package com.workflow.entity.excel.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按钮事件内置 Excel 动作协议。参数直接保存在事件步骤并随所属表单/列表发布，
 * 不需要生成接口定义，也不能由输入映射或客户端请求替换操作和方案。
 */
public final class ExcelEventAction {
    public static final String TYPE = "ENTITY_EXCEL";
    private static final Set<String> OPERATIONS = Set.of("IMPORT", "EXPORT", "TEMPLATE");
    private static final Set<String> SCOPES = Set.of("CURRENT_RECORD", "SELECTED", "FILTERED");

    private ExcelEventAction() { }

    public static boolean isExcelStep(Map<String, Object> step) {
        return step != null && TYPE.equals(step.get("actionType"));
    }

    /** 读取静态动作参数的独立浅副本；缺少对象配置属于无效绑定，不能默认为其他操作。 */
    public static Map<String, Object> configuration(Map<String, Object> step) {
        if (!isExcelStep(step) || !(step.get("actionConfig") instanceof Map<?, ?> configuration))
            throw new IllegalArgumentException("Excel 动作必须配置 actionConfig 对象");
        return stringMap(configuration);
    }

    /**
     * 保存、发布及执行共用校验。仅允许真实表单/列表按钮的无条件主步骤；
     * 方案和范围必须是静态配置，避免通用事件映射在运行时扩大数据范围。
     */
    public static void validateStep(Map<String, Object> step, String ownerType, String targetType, String eventCode) {
        boolean form = "FORM".equals(ownerType);
        boolean row = "ROW_BUTTON_CLICK".equals(eventCode);
        if (!"BUTTON".equals(targetType) || !(form && "FORM_BUTTON_CLICK".equals(eventCode)
                || "LIST".equals(ownerType) && Set.of("TOOLBAR_BUTTON_CLICK", "ROW_BUTTON_CLICK").contains(eventCode)))
            throw new IllegalArgumentException("Excel 动作仅支持表单或列表的按钮事件");
        if (!"REPLACE".equals(step.get("strategy")) || populated(step.get("condition")))
            throw new IllegalArgumentException("Excel 动作必须作为无条件主处理步骤");
        if (step.get("failurePolicy") != null && !"STOP".equals(step.get("failurePolicy")))
            throw new IllegalArgumentException("Excel 动作失败后必须停止执行");
        for (String key : List.of("extensionId", "serviceId", "operationCode", "executableSnapshot", "inputMapping", "outputMapping")) {
            if (populated(step.get(key))) throw new IllegalArgumentException("Excel 内置动作不能同时配置接口或参数映射：" + key);
        }
        Map<String, Object> config = configuration(step);
        if (!Set.of("operation", "schemeRef", "dataScope", "listKey", "selectionSource").containsAll(config.keySet()))
            throw new IllegalArgumentException("Excel 动作包含不支持的配置项");
        String operation = text(config.get("operation"));
        if (!OPERATIONS.contains(operation)) throw new IllegalArgumentException("Excel操作无效");
        if (!(config.get("schemeRef") instanceof Map<?, ?> reference)) throw new IllegalArgumentException("方案引用方式无效");
        String mode = text(reference.get("mode")), code = text(reference.get("schemeCode"));
        if (!Set.of("mode", "schemeCode").containsAll(reference.keySet()) || !Set.of("DEFAULT", "EXPLICIT").contains(mode))
            throw new IllegalArgumentException("方案引用方式无效");
        if ("EXPLICIT".equals(mode) && !code.matches("[A-Za-z][A-Za-z0-9_]{0,99}"))
            throw new IllegalArgumentException("指定 Excel 方案编码无效");
        if (reference.get("schemeCode") != null && !(reference.get("schemeCode") instanceof String))
            throw new IllegalArgumentException("指定 Excel 方案编码必须是字符串");
        if ("DEFAULT".equals(mode) && !code.isBlank()) throw new IllegalArgumentException("跟随实体默认时不能指定方案编码");
        if (config.get("dataScope") != null && !(config.get("dataScope") instanceof String))
            throw new IllegalArgumentException("导出范围无效");
        if (config.get("listKey") != null && !(config.get("listKey") instanceof String))
            throw new IllegalArgumentException("来源列表标识必须是字符串");
        String scope = text(config.get("dataScope"));
        if (!scope.isBlank() && !SCOPES.contains(scope)) throw new IllegalArgumentException("导出范围无效");
        if (row && "IMPORT".equals(operation)) throw new IllegalArgumentException("行按钮不支持批量导入，请使用工具栏按钮");
        if (row && "EXPORT".equals(operation) && !scope.isBlank() && !"CURRENT_RECORD".equals(scope))
            throw new IllegalArgumentException("行按钮仅支持导出当前记录");
        Object selection = config.get("selectionSource");
        if (populated(selection) && (!(selection instanceof Map<?, ?> source)
                || !Set.of("type").containsAll(source.keySet()) || !"LIST_DIALOG".equals(source.get("type"))))
            throw new IllegalArgumentException("导出选择来源仅支持 LIST_DIALOG");
        if (form && "EXPORT".equals(operation) && !scope.isBlank() && !"CURRENT_RECORD".equals(scope)
                && (text(config.get("listKey")).isBlank() || !(selection instanceof Map<?, ?> source)
                || !"LIST_DIALOG".equals(source.get("type"))))
            throw new IllegalArgumentException("表单批量导出需要配置已发布列表及LIST_DIALOG选择来源");
    }

    private static boolean populated(Object value) {
        if (value == null) return false;
        if (value instanceof Map<?, ?> map) return !map.isEmpty();
        if (value instanceof List<?> list) return !list.isEmpty();
        return !(value instanceof String text) || !text.isBlank();
    }

    private static Map<String, Object> stringMap(Map<?, ?> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            if (!(key instanceof String)) throw new IllegalArgumentException("Excel 动作配置键必须是字符串");
            result.put((String) key, value);
        });
        return result;
    }

    private static String text(Object value) { return value instanceof String text ? text : ""; }
}
