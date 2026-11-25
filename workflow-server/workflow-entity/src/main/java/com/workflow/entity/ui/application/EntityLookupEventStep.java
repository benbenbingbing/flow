package com.workflow.entity.ui.application;

import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 已发布的按主键查询步骤。目标实体和结果字段来自配置，只有 ID 来自事件输入。 */
final class EntityLookupEventStep {

    static final String TYPE = "ENTITY_LOOKUP";
    private static final String FIELD = "[A-Za-z_][A-Za-z0-9_]{0,99}";
    private static final String RESULT_PREFIX = "data.data.";
    private static final Set<String> FORM_EVENTS = Set.of(UiDataSourceUsages.FORM_OPEN,
            UiDataSourceUsages.FORM_BUTTON_CLICK, UiDataSourceUsages.FIELD_CHANGE,
            UiDataSourceUsages.ENTITY_SELECTED, UiDataSourceUsages.FIELD_BUTTON_CLICK);
    private static final Set<String> PROTECTED_TARGETS = Set.of("id", "processInstanceId", "process_instance_id",
            "processStatus", "process_status", "deleted", "create_by", "update_by", "create_time", "update_time");

    private EntityLookupEventStep() {
    }

    static boolean matches(Map<?, ?> step) {
        return TYPE.equals(step.get("actionType"));
    }

    /**
     * 保存、发布和执行共用同一结构校验。列表步骤以查询结果作为主结果，
     * 表单步骤则要求显式目标字段，以免宿主把整条详情当成草稿合并。
     */
    static Config validate(Map<?, ?> step, String ownerType, String targetType, String eventCode) {
        boolean form = "FORM".equals(ownerType) && FORM_EVENTS.contains(eventCode);
        boolean list = "LIST".equals(ownerType) && "BUTTON".equals(targetType)
                && Set.of(UiDataSourceUsages.ROW_BUTTON_CLICK, UiDataSourceUsages.TOOLBAR_BUTTON_CLICK)
                        .contains(eventCode);
        if (!form && !list || list && !"REPLACE".equalsIgnoreCase(text(step.get("strategy")))) {
            throw new IllegalArgumentException("按 ID 查询仅支持表单打开、字段事件和按钮；列表须配置为主处理");
        }
        for (String key : List.of("extensionId", "serviceId", "operationCode", "inputMapping",
                "replaceResult", "executableSnapshot", "operationSnapshotVersion")) {
            if (step.containsKey(key)) throw new IllegalArgumentException("实体查询步骤包含不适用的配置项：" + key);
        }

        Map<String, Object> config = PageParameterPolicy.map(step.get("actionConfig"));
        String entityCode = text(config.get("entityCode"));
        String idPath = text(config.get("idPath"));
        if (!entityCode.matches(FIELD) || !validIdPath(idPath, form, eventCode)) {
            throw new IllegalArgumentException("请选择目标实体并指定有效的记录 ID 来源");
        }
        Set<String> fields = new LinkedHashSet<>();
        Object output = step.get("outputMapping");
        if (form) {
            if (!(output instanceof List<?> rows) || rows.isEmpty() || rows.size() > 50) {
                throw new IllegalArgumentException("请配置 1 到 50 个表单回填字段");
            }
            Set<String> targets = new LinkedHashSet<>();
            for (Object item : rows) {
                Map<String, Object> row = PageParameterPolicy.map(item);
                String source = text(row.get("sourcePath"));
                String target = text(row.get("targetPath"));
                if (!source.startsWith(RESULT_PREFIX) || !target.startsWith("form.")
                        || !fieldCode(source.substring(RESULT_PREFIX.length())) || !fieldCode(target.substring(5))
                        || PROTECTED_TARGETS.contains(target.substring(5)) || !targets.add(target)
                        || row.containsKey("literal")) {
                    throw new IllegalArgumentException("实体查询回填必须选择有效且不重复的表单目标字段");
                }
                fields.add(source.substring(RESULT_PREFIX.length()));
            }
        } else {
            if (output != null || !(config.get("fields") instanceof Collection<?> selected)
                    || selected.isEmpty() || selected.size() > 50) {
                throw new IllegalArgumentException("请选择 1 到 50 个列表查询结果字段");
            }
            for (Object field : selected) {
                if (!fieldCode(text(field))) throw new IllegalArgumentException("实体查询结果字段无效");
                fields.add(text(field));
            }
        }
        return new Config(entityCode, idPath, fields);
    }

    /** 缺少可读字段时不产生映射，保留实际返回 null 与字段不可读的区别。 */
    static boolean hasSource(Map<?, ?> mapping, Map<?, ?> data) {
        return data.containsKey(text(mapping.get("sourcePath")).substring(RESULT_PREFIX.length()));
    }

    private static boolean validIdPath(String path, boolean form, String eventCode) {
        if (path.matches("input\\.params\\." + FIELD)) return true;
        if (form) {
            return path.matches("input\\.form\\." + FIELD) || "recordId".equals(path)
                    || Set.of(UiDataSourceUsages.FIELD_CHANGE, UiDataSourceUsages.ENTITY_SELECTED,
                            UiDataSourceUsages.FIELD_BUTTON_CLICK).contains(eventCode) && "input.value".equals(path)
                    || UiDataSourceUsages.ENTITY_SELECTED.equals(eventCode) && "selection.id".equals(path);
        }
        return UiDataSourceUsages.ROW_BUTTON_CLICK.equals(eventCode)
                ? path.matches("input\\.row\\.(id|data\\." + FIELD + ")")
                : path.matches("input\\.selectedRows\\.0\\.(id|data\\." + FIELD + ")");
    }

    private static boolean fieldCode(String value) {
        return value.matches(FIELD) && !Set.of("__proto__", "constructor", "prototype").contains(value);
    }

    private static String text(Object value) {
        return value instanceof String string ? string : "";
    }

    record Config(String entityCode, String idPath, Set<String> fields) {
    }
}
