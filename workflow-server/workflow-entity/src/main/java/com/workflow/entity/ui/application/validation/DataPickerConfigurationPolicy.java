package com.workflow.entity.ui.application.validation;

import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;
import com.workflow.entity.ui.application.PageParameterPolicy;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 数据选择器的发布契约；组件选择不改变实体属性类型，也不建立外部业务键到平台实体的关系。 */
public final class DataPickerConfigurationPolicy {
    public static final Set<String> SUPPORTED_FIELD_TYPES = Set.of(
            "STRING", "SELECT", "TEXT", "INTEGER", "LONG", "DECIMAL", "DATE", "DATETIME", "BOOLEAN", "RICH_TEXT");
    private static final Pattern FIELD = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,99}");
    private static final Set<String> RESERVED = Set.of("__proto__", "prototype", "constructor");
    private static final Set<String> TYPES = Set.of("STRING", "TEXT", "INTEGER", "LONG", "DECIMAL", "DOUBLE", "NUMBER", "BOOLEAN", "DATE", "DATETIME", "RICH_TEXT");
    private static final Map<String, Object> DEFAULT_SCHEMA = Map.of(
            "schemaVersion", "id-name-v1", "valueField", "id", "labelField", "name",
            "fields", List.of(Map.of("code", "id", "type", "STRING"), Map.of("code", "name", "type", "STRING")),
            "columns", List.of(Map.of("field", "name")));
    private DataPickerConfigurationPolicy() { }

    /** 按来源校验字段契约和回填。只有动态描述模式要求绑定描述接口，历史配置沿用该模式。 */
    static Map<String, Map<?, ?>> validate(Map<?, ?> props, Map<?, ?> bindings, String fieldCode, String fieldType) {
        code(fieldCode);
        String sourceMode = String.valueOf(props.containsKey("pickerSource") ? props.get("pickerSource") : "INTERFACE");
        if (!Set.of("INTERFACE", "ENTITY_LIST").contains(sourceMode)) invalid("数据来源无效");
        boolean listSource = "ENTITY_LIST".equals(sourceMode);
        String mode = schemaMode(props, bindings);
        Map<?, ?> schema = schema(props, bindings);
        Map<String, String> fields = validateSchema(schema);
        validateWriteField(props, fields, fieldType);
        if (listSource) {
            if (!"id".equals(schema.get("valueField")) || !"name".equals(schema.get("labelField"))
                    || !"STRING".equals(fields.get("name"))) invalid("实体列表使用 ID 定位记录、名称作为默认显示字段");
            Map<?, ?> target = object(props.get("pickerList"), "请配置已发布实体列表");
            for (String key : List.of("entityId", "entityCode", "listKey")) {
                if (!(target.get(key) instanceof String value) || value.isBlank()) invalid("请选择目标实体和已发布列表");
            }
        }
        if (props.containsKey("pickerPageSize")) boundedInteger(props.get("pickerPageSize"), 1, 200, "每页条数");
        if (props.containsKey("pickerClearOnClear") && !(props.get("pickerClearOnClear") instanceof Boolean)) invalid("清空处理必须为布尔值");
        Object rawMappings = props.get("pickerMappings");
        List<?> mappings = rawMappings == null ? List.of() : array(rawMappings, 200, "回填规则");
        Set<String> targets = new HashSet<>();
        for (Object item : mappings) {
            Map<?, ?> mapping = object(item, "回填规则必须为对象");
            String source = path(mapping.get("sourcePath"), "selection.data.");
            String target = path(mapping.get("targetPath"), "form.");
            if (!fields.containsKey(source) || target.equals(fieldCode) || !targets.add(target)) invalid("回填来源不存在，或目标重复/指向当前属性");
            if (!Set.of("ALWAYS", "IF_EMPTY", "CONFIRM").contains(String.valueOf(mapping.get("overwrite")))) invalid("回填覆盖策略无效");
            if (!(mapping.get("clearOnEmpty") instanceof Boolean)) invalid("回填空值策略必须为布尔值");
        }
        Map<String, Map<?, ?>> result = new LinkedHashMap<>();
        for (String usage : UiDataSourceUsages.FIELD_PICKER_USAGES) {
            Object raw = bindings.get(usage);
            List<?> candidates = raw instanceof List<?> list ? list : raw == null ? List.of() : List.of(raw);
            if (listSource || UiDataSourceUsages.FIELD_PICKER_SCHEMA.equals(usage) && !"INTERFACE".equals(mode)) {
                if (!candidates.isEmpty()) invalid("当前数据来源无需配置 " + usage + " 接口");
                continue;
            }
            if (candidates.size() != 1) invalid("必须配置一个 " + usage + " 接口");
            Map<?, ?> binding = object(candidates.get(0), "接口绑定必须为对象");
            if (!(binding.get("extensionId") instanceof String id) || id.isBlank() || Boolean.FALSE.equals(binding.get("enabled"))) invalid("接口绑定必须启用并使用 extensionId");
            Object output = binding.get("outputMapping");
            if (output != null && (!(output instanceof Map<?, ?> mapping) || !mapping.isEmpty())) invalid("选择器接口使用固定返回契约，不能配置输出映射");
            result.put(usage, binding);
        }
        return result;
    }

    /** 发布时校验目标属性与行内取值类型。记录键仍用于按键解析，取值字段可随业务独立配置。 */
    private static void validateWriteField(Map<?, ?> props, Map<String, String> fields, String fieldType) {
        if (fieldType == null || !SUPPORTED_FIELD_TYPES.contains(fieldType)) invalid("当前属性类型不支持数据选择器");
        boolean supportsKey = "STRING".equals(fieldType) || "SELECT".equals(fieldType);
        Object mode = props.containsKey("pickerValueMode") ? props.get("pickerValueMode") : supportsKey ? "KEY" : "FIELD";
        if ("KEY".equals(mode) && supportsKey) return;
        if (!"FIELD".equals(mode) || "SELECT".equals(fieldType)) invalid("写入方式与当前属性类型不兼容");
        String source = fields.get(props.get("pickerWriteField"));
        if (source == null || !switch (fieldType) {
            case "STRING", "TEXT" -> Set.of("STRING", "TEXT").contains(source);
            case "INTEGER", "LONG" -> Set.of("INTEGER", "LONG").contains(source);
            case "DECIMAL" -> Set.of("INTEGER", "LONG", "DECIMAL", "DOUBLE", "NUMBER").contains(source);
            default -> fieldType.equals(source);
        }) invalid("请配置与当前属性类型兼容的取值来源字段");
    }

    static String schemaMode(Map<?, ?> props, Map<?, ?> bindings) {
        if ("ENTITY_LIST".equals(props.get("pickerSource"))) return "ENTITY_LIST";
        if (props.containsKey("pickerSchemaSource")) {
            String mode = String.valueOf(props.get("pickerSchemaSource"));
            if (!Set.of("DEFAULT", "MANUAL", "QUERY_SCHEMA", "INTERFACE").contains(mode)) invalid("字段描述方式无效");
            return mode;
        }
        return props.get("pickerSchema") != null || bindings.containsKey(UiDataSourceUsages.FIELD_PICKER_SCHEMA)
                ? "INTERFACE" : "DEFAULT";
    }

    static Map<?, ?> schema(Map<?, ?> props, Map<?, ?> bindings) {
        return "DEFAULT".equals(schemaMode(props, bindings))
                ? DEFAULT_SCHEMA : object(props.get("pickerSchema"), "请先配置并保存字段描述");
    }

    /** 从分页接口声明中选择的字段必须仍存在且类型相同，更新接口后需要重新选择失效的字段。 */
    static void validateQuerySchema(Map<?, ?> config, Map<?, ?> output) {
        Map<?, ?> properties = object(output.get("properties"), "分页接口未声明返回字段");
        Map<?, ?> records = object(properties.get("records"), "分页接口未声明记录");
        Map<?, ?> items = object(records.get("items"), "分页接口未声明记录字段");
        Map<?, ?> row = object(items.get("properties"), "分页接口未声明记录字段");
        validateSchema(config).forEach((code, expected) -> {
            Map<?, ?> property = object(row.get(code), "分页接口未声明字段 " + code);
            Object declaredType = property.get("type");
            if (declaredType instanceof List<?> types) {
                List<?> nonNull = types.stream().filter(type -> !"null".equals(type)).toList();
                declaredType = nonNull.size() == 1 ? nonNull.get(0) : null;
            }
            String actual = switch (String.valueOf(declaredType)) {
                case "string" -> switch (String.valueOf(property.get("format"))) {
                    case "date" -> "DATE";
                    case "date-time" -> "DATETIME";
                    case "html" -> "RICH_TEXT";
                    case "int64" -> "LONG";
                    case "decimal" -> "DECIMAL";
                    default -> "STRING";
                };
                case "integer" -> "int32".equals(property.get("format")) ? "INTEGER" : "LONG";
                case "number" -> "NUMBER";
                case "boolean" -> "BOOLEAN";
                default -> "";
            };
            // 旧声明把 int32 归为 LONG、普通字符串格式归为 STRING，发布后的历史契约仍可解析。
            boolean legacy = "LONG".equals(expected) && "INTEGER".equals(actual)
                    || "STRING".equals(expected) && "string".equals(declaredType) && Set.of("RICH_TEXT", "LONG", "DECIMAL").contains(actual);
            if (!expected.equals(actual) && !legacy) invalid("分页接口字段类型已调整: " + code);
        });
    }

    /**
     * 检查选择器依赖的发布列和参数用途。发布列表独立调整后，表单重新发布需更新失效的依赖。
     * @param props 当前选择器配置，包含根据目标列表生成的回填契约
     * @param viewConfig 目标列表当前激活版本的视图配置
     * @param entityTypes 目标列表中可回填的实体字段类型，含系统 ID 和名称
     */
    public static void validatePublishedList(Map<?, ?> props, Map<String, Object> viewConfig, Map<String, String> entityTypes) {
        validateSchema(schema(props, Map.of())).forEach((code, expected) -> {
            String actual = entityTypes.get(code);
            if (actual != null && Set.of("SELECT", "RADIO", "REFERENCE", "USER", "DEPT", "ROLE", "GROUP").contains(actual)) {
                actual = "STRING";
            }
            // 历史列表的富文本声明为 STRING，此快照可继续发布；新配置会按 RICH_TEXT 提供取值。
            if (!expected.equals(actual) && !("STRING".equals(expected) && "RICH_TEXT".equals(actual))) {
                invalid("列表回填字段已失效或类型已变化: " + code);
            }
        });

        Map<?, ?> target = object(props.get("pickerList"), "请配置已发布实体列表");
        if (target.get("parameterMappings") == null) return;
        Map<String, Object> view = viewConfig == null ? Map.of() : viewConfig;
        Set<Object> filters = new HashSet<>();
        if (view.get("inputParameterBindings") instanceof List<?> bindings) {
            for (Object item : bindings) {
                Map<String, Object> binding = PageParameterPolicy.map(item);
                if ("FILTER".equals(binding.get("usage"))) filters.add(binding.get("parameter"));
            }
        }
        Map<String, Object> definitions = PageParameterPolicy.map(PageParameterPolicy.map(view.get("inputParameterSchema")).get("properties"));
        Set<String> seen = new HashSet<>();
        for (Object item : array(target.get("parameterMappings"), 50, "列表参数映射")) {
            Map<?, ?> mapping = object(item, "参数映射必须为对象");
            String parameter = code(mapping.get("parameter"));
            if (!seen.add(parameter) || !filters.contains(parameter) || !definitions.containsKey(parameter)) {
                invalid("列表筛选参数未发布或重复配置: " + parameter);
            }
            String source = String.valueOf(mapping.get("sourceType"));
            if ("LITERAL".equals(source)) {
                Object value = mapping.get("value");
                if (value == null) invalid("请配置列表参数固定值: " + parameter);
                PageParameterPolicy.resolve(Map.of("inputParameterSchema", Map.of(
                        "properties", Map.of(parameter, definitions.get(parameter)), "required", List.of(parameter))), Map.of(parameter, value));
            } else if ("FIELD".equals(source) || "ENTITY_FIELD".equals(source)) {
                code(mapping.get("sourceField"));
            } else {
                invalid("列表参数来源仅支持表单属性、实体属性或固定值");
            }
        }
    }

    /** 校验已固定的字段契约；返回类型目录，供发布时检查回填到真实表单属性的类型兼容性。 */
    static Map<String, String> validateSchema(Map<?, ?> schema) {
        if (!(schema.get("schemaVersion") instanceof String version) || version.isBlank()) invalid("缺少 schemaVersion");
        List<?> fields = array(schema.get("fields"), 200, "字段描述");
        if (fields.isEmpty()) invalid("字段描述不能为空");
        Map<String, String> types = new LinkedHashMap<>();
        for (Object item : fields) {
            Map<?, ?> field = object(item, "字段必须为对象");
            String code = code(field.get("code"));
            String type = String.valueOf(field.get("type")).toUpperCase(Locale.ROOT);
            if (!TYPES.contains(type) || types.putIfAbsent(code, type) != null) invalid("字段类型不受支持或字段重复");
        }
        if (!"STRING".equals(types.get(schema.get("valueField"))) || !types.containsKey(schema.get("labelField"))) invalid("主值必须为 STRING 字段，显示字段必须已声明");
        List<?> columns = array(schema.get("columns"), 50, "展示列");
        if (columns.isEmpty()) invalid("展示列不能为空");
        Set<Object> seen = new HashSet<>();
        for (Object item : columns) {
            Map<?, ?> column = object(item, "展示列必须为对象");
            if (!types.containsKey(column.get("field")) || !seen.add(column.get("field"))) invalid("展示列引用不存在或重复");
        }
        return types;
    }

    /**
     * 服务端执行前限制查询规模；业务筛选参数不能替代 picker 协议中的页码或所选键。
     * @param usage 当前已授权的选择器绑定用途，决定分页或按键解析的协议检查
     * @param input 映射后的业务输入，必须保留组件生成的 picker 对象
     * @throws IllegalArgumentException 协议缺失、页码越界或业务键类型错误时阻止 Provider 执行
     */
    public static void validateRequest(String usage, Map<String, Object> input) {
        Map<?, ?> picker = object(input == null ? null : input.get("picker"), "缺少 input.picker");
        if (UiDataSourceUsages.FIELD_PICKER_QUERY.equals(usage)) {
            boundedInteger(picker.get("pageNum"), 1, Integer.MAX_VALUE, "页码");
            boundedInteger(picker.get("pageSize"), 1, 200, "每页条数");
            if (picker.containsKey("keyword") && (!(picker.get("keyword") instanceof String keyword) || keyword.length() > 500)) invalid("关键词不能超过 500 字符");
        }
        if (UiDataSourceUsages.FIELD_PICKER_RESOLVE.equals(usage)) {
            List<?> values = array(picker.get("values"), 200, "所选键");
            if (values.isEmpty()) invalid("所选键不能为空");
            Set<Object> seen = new HashSet<>();
            for (Object value : values) if (!(value instanceof String key) || key.isBlank() || key.length() > 1000 || !seen.add(key)) invalid("所选键必须为不重复的非空字符串");
        }
    }

    /**
     * 接口目录和发布校验共用同一输出 Schema 判断，设计器不会展示无法发布的候选接口。
     * @param usage 选择器的描述、查询或解析用途
     * @param schema 接口声明的输出 Schema，实际记录仍由组件按已发布字段契约检查
     * @return 输出根类型和必要属性类型满足该用途的固定契约时为 true
     */
    public static boolean supportsOutputSchema(String usage, Map<?, ?> schema) {
        if (UiDataSourceUsages.FIELD_PICKER_RESOLVE.equals(usage)) return type(schema, "array");
        if (!type(schema, "object") || !(schema.get("properties") instanceof Map<?, ?> properties)) return false;
        if (UiDataSourceUsages.FIELD_PICKER_QUERY.equals(usage)) return property(properties, "records", "array") && property(properties, "total", "integer");
        return property(properties, "fields", "array") && property(properties, "columns", "array")
                && property(properties, "valueField", "string") && property(properties, "labelField", "string") && property(properties, "schemaVersion", "string");
    }

    static boolean compatibleTypes(String source, String target) {
        if (source == null || target == null) return false;
        if (source.equals(target)) return true;
        Set<String> text = Set.of("STRING", "TEXT", "RICH_TEXT", "SELECT", "RADIO");
        Set<String> numbers = Set.of("INTEGER", "LONG", "DECIMAL", "DOUBLE", "NUMBER");
        return text.contains(source) && text.contains(target) || numbers.contains(source) && numbers.contains(target);
    }
    private static boolean property(Map<?, ?> properties, String key, String expected) {
        return properties.get(key) instanceof Map<?, ?> schema && type(schema, expected);
    }
    private static boolean type(Map<?, ?> schema, String expected) { return expected.equalsIgnoreCase(String.valueOf(schema.get("type"))); }

    private static String path(Object value, String prefix) {
        if (!(value instanceof String text) || !text.startsWith(prefix)) { invalid("回填路径无效"); return ""; }
        return code(((String) value).substring(prefix.length()));
    }
    private static String code(Object value) {
        if (!(value instanceof String text) || !FIELD.matcher(text).matches() || RESERVED.contains(text)) { invalid("字段编码无效"); return ""; }
        return (String) value;
    }
    private static List<?> array(Object value, int max, String label) {
        if (!(value instanceof List<?> list) || list.size() > max) { invalid(label + "必须为不超过 " + max + " 项的数组"); return List.of(); }
        return (List<?>) value;
    }
    private static Map<?, ?> object(Object value, String message) {
        if (!(value instanceof Map<?, ?> map)) { invalid(message); return Map.of(); }
        return (Map<?, ?>) value;
    }
    private static void boundedInteger(Object value, int min, int max, String label) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()) || number.doubleValue() != number.longValue() || number.longValue() < min || number.longValue() > max) invalid(label + "超出允许范围");
    }
    private static void invalid(String message) { throw new IllegalArgumentException("数据选择器：" + message); }
}
