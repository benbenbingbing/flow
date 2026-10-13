package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.migration.port.EntityExcelMigrationPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Excel 可迁移配置的依赖闭包；不读取运行任务、文件、业务行或执行令牌。 */
final class ConfigMigrationExcelSupport {
    private ConfigMigrationExcelSupport() { }

    /** 只保留 Excel 动作的设计参数，兼容旧接口和新绑定；源环境身份和运行选择不能传播。 */
    static void sanitizeInterfaces(Map<String, Object> snapshot, ObjectMapper json) {
        // 内置动作直接存在表单/列表事件中，即使没有 interfaceExtensions 分区也要处理。
        snapshot.replaceAll((key, value) -> sanitizeBuiltinActions(value));
        if (!snapshot.containsKey("interfaceExtensions")) return;
        List<Map<String, Object>> extensions = new ArrayList<>();
        for (Map<String, Object> extension : objects(snapshot.get("interfaceExtensions"))) {
            if ("entity_excel_action".equals(extension.get("providerCode"))) {
                Object raw = extension.get("implementationConfigDocument");
                Map<String, Object> config;
                try { config = raw instanceof String value ? json.readValue(value, Map.class) : object(raw); }
                catch (Exception error) { throw new IllegalArgumentException("Excel 按钮接口配置损坏", error); }
                Map<String, Object> portable = portableConfiguration(config, true);
                // 保持原协议的字符串/对象类型，供既有接口扩展导入器直接重建目标 scopeId。
                try { extension.put("implementationConfigDocument", raw instanceof String ? json.writeValueAsString(portable) : portable); }
                catch (Exception error) { throw new IllegalArgumentException("Excel 按钮接口配置无法序列化", error); }
            }
            extensions.add(extension);
        }
        snapshot.put("interfaceExtensions", extensions);
    }

    /** 页面细粒度导出也必须携带默认方案及写入表单，否则默认按钮在目标环境会失去同一语义。 */
    static void includeSelected(Map<String, Object> source, Map<String, Object> selected, Set<String> sections, ObjectMapper json) {
        if (!source.containsKey(EntityExcelMigrationPort.SECTION)) return;
        if (!selected.containsKey("forms") && !selected.containsKey("lists")
                && !sections.contains(EntityExcelMigrationPort.SECTION) && !sections.contains("interfaceExtensions")) return;
        selected.put(EntityExcelMigrationPort.SECTION, source.get(EntityExcelMigrationPort.SECTION));
        Set<String> formKeys = new LinkedHashSet<>();
        for (Map<String, Object> scheme : schemes(source)) {
            if (!"ACTIVE".equals(scheme.get("status"))) continue;
            Map<String, Object> config = object(object(scheme.get("config")).get("importConfig"));
            if (Boolean.TRUE.equals(config.get("enabled")) && config.get("writeFormKey") instanceof String key) formKeys.add(key);
        }
        mergeOwners(source, selected, "forms", "formKey", formKeys);
        // FORM 的批量导出依赖列表选择器，按稳定 listKey 带入来源列表配置。
        Set<String> listKeys = new LinkedHashSet<>();
        for (Map<String, Object> extension : objects(source.get("interfaceExtensions"))) {
            if (!"entity_excel_action".equals(extension.get("providerCode"))) continue;
            try {
                Object raw = extension.get("implementationConfigDocument");
                Map<String, Object> config = raw instanceof String text ? json.readValue(text, Map.class) : object(raw);
                if (config.get("listKey") instanceof String key && !key.isBlank()) listKeys.add(key);
            } catch (Exception error) { throw new IllegalArgumentException("Excel 按钮接口配置损坏", error); }
        }
        for (Map<String, Object> config : builtinConfigurations(source)) {
            if (config.get("listKey") instanceof String key && !key.isBlank()) listKeys.add(key);
        }
        mergeOwners(source, selected, "lists", "listKey", listKeys);
        // 支持表单的事件也要随表单一起迁移，供后续接口扩展闭包计算使用。
        if ((!formKeys.isEmpty() || !listKeys.isEmpty()) && source.containsKey("eventBindings")) selected.put("eventBindings", source.get("eventBindings"));
    }

    static List<Map<String, Object>> dependencies(Map<String, Object> snapshot) { return dependencies(snapshot, snapshot); }

    /** DICT 的字典身份来自实体字段；细粒度导出没有 fields 分区时也不能丢失该依赖。 */
    static List<Map<String, Object>> dependencies(Map<String, Object> source, Map<String, Object> snapshot) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> scheme : schemes(snapshot)) {
            if (!"ACTIVE".equals(scheme.get("status"))) continue;
            Map<String, Object> config = object(object(scheme.get("config")).get("importConfig"));
            if (Boolean.TRUE.equals(config.get("enabled")) && config.get("writeFormKey") instanceof String key)
                result.add(Map.of("type", "FORM", "key", "wf-form://" + snapshot.get("businessKey") + "/" + key, "required", true, "source", "Excel 导入写入表单"));
            for (var column : objects(object(scheme.get("config")).get("columns"))) {
                if (!"DICT".equals(object(column.get("codec")).get("type"))) continue;
                for (var field : objects(source.get("fields"))) {
                    if (java.util.Objects.equals(field.get("fieldCode"), column.get("fieldCode"))
                            && field.get("dictType") instanceof String code && !code.isBlank())
                        result.add(Map.of("type", "DICTIONARY", "key", code, "required", true, "source", "Excel 字典转换"));
                }
            }
        }
        return result;
    }

    /** 历史快照尚无 Excel 分区，回滚时显式清空新引入的默认指针。 */
    static void prepareRollback(Map<String, Object> previous, Map<String, Object> imported) {
        if (imported.containsKey(EntityExcelMigrationPort.SECTION) && !previous.containsKey(EntityExcelMigrationPort.SECTION))
            previous.put(EntityExcelMigrationPort.SECTION, Map.of("schemaVersion", 1, "schemes", List.of(), "defaults", Map.of("schemaVersion", 1)));
    }

    private static void mergeOwners(Map<String, Object> source, Map<String, Object> selected, String section, String keyField, Set<String> keys) {
        if (keys.isEmpty()) return;
        Map<String, Map<String, Object>> values = new LinkedHashMap<>();
        for (var value : objects(selected.get(section))) values.put(String.valueOf(value.get(keyField)), value);
        for (var value : objects(source.get(section))) if (keys.contains(String.valueOf(value.get(keyField)))) values.putIfAbsent(String.valueOf(value.get(keyField)), value);
        if (!values.isEmpty()) selected.put(section, new ArrayList<>(values.values()));
    }

    /** 复制嵌套事件文档，避免细粒度导出清理参数时改动源发布快照。 */
    private static Object sanitizeBuiltinActions(Object value) {
        if (value instanceof List<?> list) return list.stream().map(ConfigMigrationExcelSupport::sanitizeBuiltinActions).toList();
        if (!(value instanceof Map<?, ?>)) return value;
        Map<String, Object> copy = object(value);
        copy.replaceAll((key, child) -> sanitizeBuiltinActions(child));
        if ("ENTITY_EXCEL".equals(copy.get("actionType"))) {
            copy.put("actionConfig", portableConfiguration(object(copy.get("actionConfig")), false));
        }
        return copy;
    }

    /** 内置动作不再产生接口依赖，来源列表仍须作为页面配置依赖一起导出。 */
    private static List<Map<String, Object>> builtinConfigurations(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof Map<?, ?> map) {
            if ("ENTITY_EXCEL".equals(map.get("actionType"))) result.add(object(map.get("actionConfig")));
            else for (Object child : map.values()) result.addAll(builtinConfigurations(child));
        } else if (value instanceof List<?> list) {
            for (Object child : list) result.addAll(builtinConfigurations(child));
        }
        return result;
    }

    private static Map<String, Object> portableConfiguration(Map<String, Object> config, boolean legacy) {
        Map<String, Object> portable = keep(config, legacy
                ? Set.of("entityCode", "operation", "schemeRef", "dataScope", "listKey", "selectionSource")
                : Set.of("operation", "schemeRef", "dataScope", "listKey", "selectionSource"));
        if (portable.containsKey("schemeRef")) portable.put("schemeRef", keep(object(portable.get("schemeRef")), Set.of("mode", "schemeCode")));
        if (portable.containsKey("selectionSource")) portable.put("selectionSource", keep(object(portable.get("selectionSource")), Set.of("type")));
        return portable;
    }
    private static List<Map<String, Object>> schemes(Map<String, Object> snapshot) { return objects(object(snapshot.get(EntityExcelMigrationPort.SECTION)).get("schemes")); }
    private static Map<String, Object> keep(Map<String, Object> value, Set<String> keys) { Map<String, Object> copy = new LinkedHashMap<>(value); copy.keySet().retainAll(keys); return copy; }
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value) { return value instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>(); }
    private static List<Map<String, Object>> objects(Object value) { return value instanceof List<?> list ? list.stream().map(ConfigMigrationExcelSupport::object).toList() : List.of(); }
}
