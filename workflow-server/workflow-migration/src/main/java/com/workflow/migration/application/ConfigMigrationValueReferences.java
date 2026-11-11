package com.workflow.migration.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import static com.workflow.migration.application.ConfigMigrationReferenceSupport.*;

/** 根据所属实体的字段契约转换默认值和比较常量，不能从 *Id 字段名猜测引用。 */
final class ConfigMigrationValueReferences {
    static final String FIELD_TYPES = "_referenceFields";
    static final String PROTOCOL = "_referenceProtocol";
    static final int PROTOCOL_VERSION = 1;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> IDENTITIES = Set.of("USER", "ROLE", "GROUP", "DEPT");
    private static final Map<String, String> SYSTEM_FIELDS = Map.ofEntries(
            Map.entry("submitterId", "USER"), Map.entry("submitter_id", "USER"),
            Map.entry("create_by", "USER"), Map.entry("update_by", "USER"),
            Map.entry("currentTaskAssignee", "USER"), Map.entry("current_task_assignee", "USER"),
            Map.entry("deptId", "DEPT"), Map.entry("dept_id", "DEPT"),
            Map.entry("id", "RECORD:当前实体"), Map.entry("processInstanceId", "RECORD:流程实例"),
            Map.entry("currentTaskId", "RECORD:流程任务"),
            Map.entry("name", "VALUE"), Map.entry("code", "VALUE"), Map.entry("status", "VALUE"),
            Map.entry("processStatus", "VALUE"), Map.entry("process_status", "VALUE"),
            Map.entry("processStartTime", "VALUE"), Map.entry("processEndTime", "VALUE"),
            Map.entry("currentTaskName", "VALUE"), Map.entry("submitterName", "VALUE"),
            Map.entry("submitTime", "VALUE"), Map.entry("create_time", "VALUE"),
            Map.entry("update_time", "VALUE"), Map.entry("deleted", "VALUE"));
    private static final Set<String> DOCUMENTS = Set.of("propsDocument", "legacyPropsDocument", "componentProps",
            "matchConfig", "filterConfig", "availabilityRule", "visibleWhen", "enabledWhen", "toolbarButtons",
            "rowActions", "toolbarConfig", "rowActionConfig", "viewConfig", "actionConfigDocument", "configDocument",
            "queryConfig", "fixedFilterConfig", "rulesDocument", "extensionConfig");

    private ConfigMigrationValueReferences() { }

    /**
     * 导出时在工作副本中携带字段语义，细粒度包无需读取目标草稿也能解析固定值。
     * 导入时裸 ID 由共用 identity 解析器拒绝；缺失字段契约的旧条件须重新导出。
     */
    static Map<String, Object> rewrite(Map<String, Object> snapshot,
            BiFunction<String, String, String> identity, boolean exporting) {
        Map<String, String> fields = new LinkedHashMap<>(SYSTEM_FIELDS);
        object(snapshot.get(FIELD_TYPES)).forEach((key, value) ->
                fields.put(key, SYSTEM_FIELDS.getOrDefault(key, checkedType(text(value)))));
        for (Map<String, Object> field : ConfigMigrationReferenceService.maps(snapshot.get("fields"))) {
            String name = text(field.get("fieldCode"));
            // 系统字段的数据库类型可能是 STRING，但 create_by 等运行时契约仍然是用户 ID。
            if (!name.isBlank()) fields.put(name, SYSTEM_FIELDS.getOrDefault(name, fieldType(field)));
        }
        Map<String, Object> result = object(walk(snapshot, fields, "", "$[" + text(snapshot.get("businessKey")) + "]",
                identity, exporting));
        if (exporting) {
            result.put(FIELD_TYPES, fields);
            result.put(PROTOCOL, PROTOCOL_VERSION);
        }
        return result;
    }

    private static Object walk(Object value, Map<String, String> fields, String inheritedType,
            String path, BiFunction<String, String, String> identity, boolean exporting) {
        if (value instanceof Collection<?> values) {
            List<Object> result = new ArrayList<>();
            int index = 0;
            for (Object child : values) result.add(walk(child, fields, inheritedType, path + "[" + index++ + "]", identity, exporting));
            return result;
        }
        if (!(value instanceof Map<?, ?>)) return value;
        Map<String, Object> result = object(value);
        String name = text(result.get("fieldCode"));
        if (name.isBlank() && "ENTITY_FIELD".equalsIgnoreCase(text(result.get("bindingType"))))
            name = text(result.get("bindingRef"));
        String type = fields.getOrDefault(name, inheritedType);
        // 表单节点的 FIELD 是节点种类，不是实体数据类型；旧表单也可能保留过期的 fieldType。
        // 以实体字段契约为准，只为未绑定实体字段的独立组件读取自身类型。
        String declared = text(result.get("fieldType"));
        if (!fields.containsKey(name) && (!name.isBlank() || type.isBlank())
                && !declared.isBlank() && !"FIELD".equalsIgnoreCase(declared)) type = fieldType(result);
        for (var entry : new ArrayList<>(result.entrySet())) {
            String key = entry.getKey();
            Object child = entry.getValue();
            String position = path + "." + key;
            if (Set.of(FIELD_TYPES, PROTOCOL, "paramsJson", "extraParams", "implementationConfigDocument").contains(key)) continue;
            if ("defaultValue".equals(key)) {
                if (type.isBlank() && hasValue(child) && !dynamic(child))
                    throw invalid(position, "默认值缺少所属字段的引用类型",
                            "请从更新后的源系统重新发布并导出，携带完整字段类型或表单节点的数据类型；不要直接复用旧包中的裸 ID");
                result.put(key, rewriteValue(child, type, position, identity, true));
            } else if (DOCUMENTS.contains(key) && child instanceof String document && !document.isBlank()) {
                try {
                    Object parsed = JSON.readValue(document, Object.class);
                    Object rewritten = "fixedFilterConfig".equals(key)
                            ? fixedFilters(parsed, fields, position, identity)
                            : walk(parsed, fields, type, position, identity, exporting);
                    result.put(key, JSON.writeValueAsString(rewritten));
                } catch (JsonProcessingException exception) {
                    throw invalid(position, "配置 JSON 无效", "请在设计器修正 JSON 后重新导出");
                }
            } else if ("fixedFilterConfig".equals(key)) {
                result.put(key, fixedFilters(child, fields, position, identity));
            } else {
                result.put(key, walk(child, fields, type, position, identity, exporting));
            }
        }
        String kind = text(result.get("type")).toUpperCase(Locale.ROOT);
        if (Set.of("FIELD", "CONDITION").contains(kind)) {
            String compared = text(result.get("field"));
            if (compared.isBlank()) compared = text(result.get("property"));
            String comparedType = fields.getOrDefault(compared, SYSTEM_FIELDS.getOrDefault(compared, ""));
            String operator = text(result.get("operator")).toUpperCase(Locale.ROOT);
            // EMPTY/NOT_EMPTY 没有固定比较值；节点型 FIELD 也没有 field/property，不能误当规则。
            if (!compared.isBlank() && result.containsKey("value") && !Set.of("EMPTY", "NOT_EMPTY", "NOTEMPTY").contains(operator)) {
                if (comparedType.isBlank() && hasValue(result.get("value")))
                    throw invalid(path + ".value", "缺少字段 " + compared + " 的引用类型",
                            "请从更新后的源系统重新导出，并携带该字段定义；不要在目标系统按源 ID 猜测");
                if (!comparedType.isBlank() && !"VALUE".equals(comparedType) && hasValue(result.get("value"))
                        && !Set.of("", "EQ", "NE", "==", "!=", "===", "!==", "IN", "NOT_IN").contains(operator))
                    throw invalid(path + ".value(" + compared + ")", "引用字段的范围或文本比较依赖本地 ID，无法保证跨环境语义",
                            "请改用等于、不等于或集合比较；范围或模糊规则请改为比较稳定业务编码");
                boolean set = Set.of("IN", "NOT_IN", "CONTAINS_ANY", "CONTAINS_ALL").contains(operator);
                result.put("value", rewriteValue(result.get("value"), comparedType, path + ".value(" + compared + ")", identity, set));
            }
        }
        return result;
    }

    /** 列表固定筛选的键是字段编码；_op 等操作符元数据不属于引用值。 */
    private static Object fixedFilters(Object value, Map<String, String> fields, String path,
            BiFunction<String, String, String> identity) {
        Map<String, Object> result = object(value);
        for (var entry : new ArrayList<>(result.entrySet())) {
            String key = entry.getKey();
            if (key.endsWith("_op")) continue;
            String field = key.replaceFirst("_(start|end)$", "");
            String operator = text(result.get(field + "_op")).toUpperCase(Locale.ROOT);
            // 空值操作使用 true 占位，并不参与比较；把占位值当用户 ID 会造成无意义的阻断。
            if (Set.of("IS_NULL", "IS_NOT_NULL").contains(operator)) continue;
            String type = fields.getOrDefault(field, "");
            String position = path + "." + key;
            if (type.isBlank() && hasValue(entry.getValue()) && !dynamic(entry.getValue()))
                throw invalid(position, "固定筛选缺少字段 " + field + " 的引用类型", "请从更新后的源系统重新导出并携带字段类型");
            if (!"VALUE".equals(type) && !type.isBlank()
                    && (!key.equals(field) || !Set.of("", "EQ", "NE", "IN", "NOT_IN").contains(operator)))
                throw invalid(position, "引用字段的范围或模糊比较依赖本地 ID 顺序/文本，无法保证跨环境语义",
                        "请改为等于、不等于或集合比较；需要范围或模糊搜索时改为比较稳定业务编码");
            result.put(key, rewriteValue(entry.getValue(), type, position, identity,
                    Set.of("IN", "NOT_IN").contains(operator)));
        }
        return result;
    }

    /** 保留标量、JSON 字符串及集合的外部表示；多值输入先拆分再解析，编码里的逗号由 URI 转义保护。 */
    static Object rewriteValue(Object value, String type, String path,
            BiFunction<String, String, String> identity, boolean multi) {
        if (type.isBlank() || "VALUE".equals(type) || !hasValue(value)) return value;
        if (dynamic(value)) return value;
        if (!IDENTITIES.contains(type)) throw invalid(path, "业务记录引用缺少跨环境唯一业务键（" + type + "）",
                "请移除静态记录 ID，改用按业务编码查询的动态默认值/筛选条件，或将规则改为比较稳定业务编码");
        if (value instanceof Collection<?> values) {
            return values.stream().map(item -> rewriteValue(item, type, path, identity, false)).toList();
        }
        if (value instanceof Map<?, ?>) throw invalid(path, "引用值必须是 ID 标量或 ID 数组",
                "请使用选择器保存的 ID 值，或改用动态默认值；不要保存整个对象");
        String key = text(value);
        if (key.stripLeading().startsWith("[") || key.stripLeading().startsWith("\"")) {
            try {
                return JSON.writeValueAsString(rewriteValue(JSON.readValue(key, Object.class), type, path, identity, multi));
            } catch (JsonProcessingException exception) {
                throw invalid(path, "引用默认值 JSON 无效", "请填写单个 ID 或合法的 ID 数组后重新导出");
            }
        }
        if (multi && key.contains(",")) {
            List<String> rewritten = new ArrayList<>();
            for (String item : key.split(",", -1)) {
                if (item.isBlank()) throw invalid(path, "引用集合含空值", "请删除空项后重新导出");
                rewritten.add(text(rewriteValue(item.trim(), type, path, identity, false)));
            }
            return String.join(",", rewritten);
        }
        try { return identity.apply(type, key); }
        catch (RuntimeException exception) {
            throw invalid(path, exception.getMessage(), "请补齐源/目标目录中的稳定编码，或在导入页配置编码映射；旧包裸 ID 请回源系统重新导出");
        }
    }

    private static boolean hasValue(Object value) {
        return value != null && !text(value).isBlank() && (!(value instanceof Collection<?> values) || !values.isEmpty());
    }

    private static boolean dynamic(Object value) {
        return value instanceof String expression && (expression.contains("${") || expression.contains("#{"));
    }

    /** 将实体字段压缩为迁移所需语义，供系统实体 UI 仅携带类型而不携带系统表结构。 */
    static String fieldType(Map<String, Object> field) {
        String type = text(field.get("fieldType")).toUpperCase(Locale.ROOT);
        if (Set.of("USER", "DEPT").contains(type)) return type;
        if (!Set.of("REFERENCE", "MULTI_REFERENCE").contains(type)) return "VALUE";
        String target = text(field.get("refEntityType")).toUpperCase(Locale.ROOT);
        if (IDENTITIES.contains(target)) return target;
        String code = text(field.get("refEntityCode"));
        return switch (code) {
            case "sys_user" -> "USER";
            case "sys_role" -> "ROLE";
            case "sys_group" -> "GROUP";
            case "sys_organization" -> "DEPT";
            default -> "RECORD:" + code;
        };
    }

    private static String checkedType(String type) {
        if (IDENTITIES.contains(type) || "VALUE".equals(type) || type.startsWith("RECORD:")) return type;
        throw invalid(FIELD_TYPES, "未知字段引用类型: " + type, "请更新源系统后重新导出，不要手工修改包内引用类型");
    }

    static ReferenceException invalid(String path, String reason, String suggestion) {
        return new ReferenceException(path, reason, suggestion);
    }

    /** API 错误保留完整文案，分析报告同时提供结构化位置和建议，避免页面只能展示“已阻断”。 */
    static final class ReferenceException extends IllegalArgumentException {
        private final String path;
        private final String reason;
        private final String suggestion;

        ReferenceException(String path, String reason, String suggestion) {
            super("配置位置 " + path + "：" + reason + "。建议：" + suggestion);
            this.path = path;
            this.reason = reason;
            this.suggestion = suggestion;
        }

        Map<String, Object> risk() {
            return Map.of("level", "BLOCKING", "code", "REFERENCE_INVALID", "path", path,
                    "message", reason, "suggestion", suggestion);
        }
    }
}
