package com.workflow.entity.ui.application.validation;

import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 数据选择器的发布契约；组件选择不改变实体属性类型，也不建立外部业务键到平台实体的关系。 */
public final class DataPickerConfigurationPolicy {
    private static final Pattern FIELD = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,99}");
    private static final Set<String> RESERVED = Set.of("__proto__", "prototype", "constructor");
    private static final Set<String> TYPES = Set.of("STRING", "TEXT", "INTEGER", "LONG", "DECIMAL", "DOUBLE", "NUMBER", "BOOLEAN", "DATE", "DATETIME");
    private DataPickerConfigurationPolicy() { }

    /** 校验字段描述、回填规则及字段自己的三个绑定；缺失描述或绑定时阻止发布。 */
    static Map<String, Map<?, ?>> validate(Map<?, ?> props, Map<?, ?> bindings, String fieldCode) {
        code(fieldCode);
        Map<?, ?> schema = object(props.get("pickerSchema"), "请先获取并保存字段描述");
        Map<String, String> fields = validateSchema(schema);
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
            if (candidates.size() != 1) invalid("必须配置一个 " + usage + " 接口");
            Map<?, ?> binding = object(candidates.get(0), "接口绑定必须为对象");
            if (!(binding.get("extensionId") instanceof String id) || id.isBlank() || Boolean.FALSE.equals(binding.get("enabled"))) invalid("接口绑定必须启用并使用 extensionId");
            Object output = binding.get("outputMapping");
            if (output != null && (!(output instanceof Map<?, ?> mapping) || !mapping.isEmpty())) invalid("选择器接口使用固定返回契约，不能配置输出映射");
            result.put(usage, binding);
        }
        return result;
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
            String type = String.valueOf(field.get("type")).toUpperCase(java.util.Locale.ROOT);
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
