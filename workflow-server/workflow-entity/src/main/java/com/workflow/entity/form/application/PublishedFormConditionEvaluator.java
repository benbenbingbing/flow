package com.workflow.entity.form.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 已发布表单结构化条件与历史受限表达式求值器。
 */
@Component
@RequiredArgsConstructor
public class PublishedFormConditionEvaluator {

    /** 对应浏览器 readProperty 返回的 undefined，不能与显式 null 混为一谈。 */
    private static final Object MISSING = new Object();

    private static final Set<String> OPERATORS = Set.of(
            "==", "!=", ">", "<", ">=", "<=",
            "contains", "empty", "notEmpty");
    private static final Pattern EMPTY_PATTERN = Pattern.compile(
            "^(empty|notEmpty)\\(([A-Za-z_][\\w.]*)\\)$");
    private static final Pattern CONTAINS_PATTERN = Pattern.compile(
            "^([A-Za-z_][\\w.]*)\\.(?:contains|includes)\\((.*)\\)$",
            Pattern.DOTALL);
    private static final Pattern COMPARISON_PATTERN = Pattern.compile(
            "^([A-Za-z_][\\w.]*)\\s*(===|!==|>=|<=|==|!=|>|<)\\s*(.+)$",
            Pattern.DOTALL);

    private final ObjectMapper objectMapper;

    /**
     * 优先求值结构化条件；配置不存在或不完整时返回 false。
     *
     * @param configuration 配置内容，决定后续{@code structured}的处理规则
     * @param record 记录，供本方法求值{@code structured}时使用
     * @return {@code structured}条件成立时为 true，否则为 false
     */
    public boolean evaluateStructured(
            Object configuration,
            Map<String, Object> record) {
        Map<String, Object> root = runtimeRoot(configuration);
        return isRuntimeNodeComplete(root)
                && evaluateNode(root, safeRecord(record));
    }

    /**
     * 结构化条件优先，不完整或不可解析时按浏览器语义回退历史表达式。
     *
     * @param configuration 配置内容，决定后续已发布表单条件求值器的处理规则
     * @param legacyExpression 旧版表达式，供本方法求值已发布表单条件求值器时使用
     * @param record 记录，作为 {@code evaluateNode} 的输入影响后续处理
     * @param defaultValue 首选值不可用时采用的兜底值，保证后续处理有稳定输入
     * @return 已发布表单条件求值器条件成立时为 true，否则为 false
     */
    public boolean evaluate(
            Object configuration,
            String legacyExpression,
            Map<String, Object> record,
            boolean defaultValue) {
        Map<String, Object> root = runtimeRoot(configuration);
        if (isRuntimeNodeComplete(root)) {
            return evaluateNode(root, safeRecord(record));
        }
        return StringUtils.hasText(legacyExpression)
                ? evaluateLegacy(legacyExpression, record)
                : defaultValue;
    }

    /**
     * 校验结构化条件完整性及字段引用。
     *
     * @param configuration 配置内容，决定后续{@code structured}的处理规则
     * @param validProperties 有效属性集合，供本方法校验{@code structured}时使用
     * @param label 标签，后续用于校验{@code structured}时匹配或展示
     */
    public void validateStructured(
            Object configuration,
            Set<String> validProperties,
            String label) {
        Map<String, Object> config = objectMap(configuration);
        if (config.isEmpty()) {
            throw invalid(label, "条件配置不能为空");
        }
        if (integer(config.get("version")) != 1) {
            throw invalid(label, "仅支持 version=1");
        }
        if (!(config.get("root") instanceof Map<?, ?> root)) {
            throw invalid(label, "缺少根条件组");
        }
        validateNode(
                stringMap(root),
                validProperties == null ? Set.of() : validProperties,
                label,
                0);
    }

    /**
     * 校验节点；不满足约束时阻止后续处理。
     *
     * @param node 节点，作为 {@code text} 的输入影响后续处理
     * @param validProperties 有效属性集合，供本方法校验节点时使用
     * @param label 标签，后续用于校验节点时匹配或展示
     * @param depth 深度，供本方法校验节点时使用
     */
    private void validateNode(
            Map<String, Object> node,
            Set<String> validProperties,
            String label,
            int depth) {
        if (depth > 8) {
            throw invalid(label, "条件嵌套不能超过 8 层");
        }
        String type = text(node.get("type"));
        if ("GROUP".equals(type)) {
            String logic = text(node.get("logic"));
            if (!Set.of("AND", "OR").contains(logic)) {
                throw invalid(label, "条件组逻辑必须为 AND 或 OR");
            }
            if (!(node.get("children") instanceof List<?> children)
                    || children.isEmpty()) {
                throw invalid(label, "条件组至少需要一个条件");
            }
            for (Object child : children) {
                if (!(child instanceof Map<?, ?> childMap)) {
                    throw invalid(label, "条件节点必须为对象");
                }
                validateNode(
                        stringMap(childMap),
                        validProperties,
                        label,
                        depth + 1);
            }
            return;
        }
        if (!"CONDITION".equals(type)) {
            throw invalid(label, "条件节点类型不合法");
        }
        String property = text(node.get("property"));
        String operator = normalizeOperator(text(node.get("operator")));
        if (!StringUtils.hasText(property)) {
            throw invalid(label, "引用字段不能为空");
        }
        if (!validProperties.isEmpty()
                && !validProperties.contains(property)) {
            throw invalid(label, "引用字段不存在: " + property);
        }
        if (!OPERATORS.contains(operator)) {
            throw invalid(label, "操作符不合法: " + operator);
        }
        if (!Set.of("empty", "notEmpty").contains(operator)
                && isEmpty(node.get("value"))) {
            throw invalid(label, "条件值不能为空");
        }
    }

    /**
     * 求值节点，并将结果传给后续步骤。
     *
     * @param node 节点，作为 {@code equals} 的输入影响后续处理
     * @param record 记录，作为 {@code path} 的输入影响后续处理
     * @return 节点条件成立时为 true，否则为 false
     */
    private boolean evaluateNode(
            Map<String, Object> node,
            Map<String, Object> record) {
        if ("GROUP".equals(text(node.get("type")))) {
            List<?> children = node.get("children") instanceof List<?> list
                    ? list : List.of();
            if (children.isEmpty()) {
                return false;
            }
            boolean any = "OR".equals(text(node.get("logic")));
            for (Object child : children) {
                boolean result = child instanceof Map<?, ?> childMap
                        && evaluateNode(stringMap(childMap), record);
                if (any && result) {
                    return true;
                }
                if (!any && !result) {
                    return false;
                }
            }
            return !any;
        }
        String property = text(node.get("property"));
        String operator = normalizeOperator(text(node.get("operator")));
        Object actual = path(record, property);
        if ("empty".equals(operator)) {
            return isEmpty(actual);
        }
        if ("notEmpty".equals(operator)) {
            return !isEmpty(actual);
        }
        Object expected = coerceExpected(node.get("value"), actual);
        return switch (operator) {
            case "==" -> equalsValue(actual, expected);
            case "!=" -> !equalsValue(actual, expected);
            case ">" -> comparable(actual, expected)
                    && compare(actual, expected) > 0;
            case "<" -> comparable(actual, expected)
                    && compare(actual, expected) < 0;
            case ">=" -> comparable(actual, expected)
                    && compare(actual, expected) >= 0;
            case "<=" -> comparable(actual, expected)
                    && compare(actual, expected) <= 0;
            case "contains" -> containsValue(actual, expected);
            default -> false;
        };
    }

    /**
     * 求值旧版，并将结果传给后续步骤。
     *
     * @param expression 表达式，供本方法求值旧版时使用
     * @param record 记录，作为 {@code evaluateLegacyNode} 的输入影响后续处理
     * @return 旧版条件成立时为 true，否则为 false
     */
    private boolean evaluateLegacy(
            String expression,
            Map<String, Object> record) {
        String source = expression.trim()
                .replaceAll("\\$\\{([A-Za-z_][\\w.]*)}", "$1");
        if (isWrappedTemplate(source)) {
            source = source.substring(2, source.length() - 1).trim();
        }
        source = normalizeLegacyEmptyConditions(source);
        return evaluateLegacyNode(source, safeRecord(record));
    }

    /**
     * 求值旧版节点，并将结果传给后续步骤。
     *
     * @param expression 表达式，作为 {@code stripOuterParentheses} 的输入影响后续处理
     * @param record 记录，作为 {@code isEmpty} 的输入影响后续处理
     * @return 旧版节点条件成立时为 true，否则为 false
     */
    private boolean evaluateLegacyNode(
            String expression,
            Map<String, Object> record) {
        String source = stripOuterParentheses(expression.trim());
        List<String> orParts = splitTopLevel(source, "||");
        if (orParts.size() > 1) {
            return orParts.stream().anyMatch(part ->
                    evaluateLegacyNode(part, record));
        }
        List<String> andParts = splitTopLevel(source, "&&");
        if (andParts.size() > 1) {
            return andParts.stream().allMatch(part ->
                    evaluateLegacyNode(part, record));
        }
        Matcher empty = EMPTY_PATTERN.matcher(source);
        if (empty.matches()) {
            boolean value = isEmpty(path(record, empty.group(2)));
            return "empty".equals(empty.group(1)) ? value : !value;
        }
        Matcher contains = CONTAINS_PATTERN.matcher(source);
        if (contains.matches()) {
            Object actual = path(record, contains.group(1));
            Object expected = coerceExpected(
                    decodeLiteral(contains.group(2)), actual);
            return containsValue(actual, expected);
        }
        Matcher comparison = COMPARISON_PATTERN.matcher(source);
        if (!comparison.matches()) {
            return false;
        }
        Object actual = path(record, comparison.group(1));
        Object expected = coerceExpected(
                decodeLiteral(comparison.group(3)), actual);
        return evaluateComparison(
                normalizeOperator(comparison.group(2)),
                actual,
                expected);
    }

    /**
     * 求值比较，并将结果传给后续步骤。
     *
     * @param operator 操作人，供本方法求值比较时使用
     * @param actual 实际，作为 {@code equalsValue} 的输入影响后续处理
     * @param expected 预期，作为 {@code equalsValue} 的输入影响后续处理
     * @return 比较条件成立时为 true，否则为 false
     */
    private boolean evaluateComparison(
            String operator,
            Object actual,
            Object expected) {
        return switch (operator) {
            case "==" -> equalsValue(actual, expected);
            case "!=" -> !equalsValue(actual, expected);
            case ">" -> comparable(actual, expected)
                    && compare(actual, expected) > 0;
            case "<" -> comparable(actual, expected)
                    && compare(actual, expected) < 0;
            case ">=" -> comparable(actual, expected)
                    && compare(actual, expected) >= 0;
            case "<=" -> comparable(actual, expected)
                    && compare(actual, expected) <= 0;
            default -> false;
        };
    }

    /**
     * 判断{@code comparable}条件是否成立，供调用方选择后续分支。
     *
     * @param actual 实际，供本方法处理{@code comparable}时使用
     * @param expected 预期，作为 {@code Number} 的输入影响后续处理
     * @return {@code comparable}条件成立时为 true，否则为 false
     */
    private boolean comparable(Object actual, Object expected) {
        if (actual == MISSING || actual == null || expected == null) {
            return false;
        }
        // 浏览器对数字字段使用 Number(expected)；无法转为数字时得到 NaN，
        // 所有大小比较都必须为 false，不能继续退化成字符串字典序比较。
        return !(actual instanceof Number)
                || number(actual) != null && number(expected) != null;
    }

    /**
     * 处理路径，并将结果传给后续步骤。
     *
     * @param source 待处理路径的原始输入，结果供调用方继续使用
     * @param path 路径，供本方法处理路径时使用
     * @return 处理后的路径结果，供调用方继续处理
     */
    private Object path(Map<String, Object> source, String path) {
        Object current = source;
        for (String key : String.valueOf(path).split("\\.")) {
            if (!(current instanceof Map<?, ?> map)
                    || !map.containsKey(key)) {
                return MISSING;
            }
            current = map.get(key);
        }
        return current;
    }

    /**
     * 处理{@code coerce}预期，并将结果传给后续步骤。
     *
     * @param value 待处理{@code coerce}预期的原始输入，结果供调用方继续使用
     * @param actual 实际，供本方法处理{@code coerce}预期时使用
     * @return 处理后的{@code coerce}预期结果，供调用方继续处理
     */
    private Object coerceExpected(Object value, Object actual) {
        if (actual instanceof Boolean) {
            return Boolean.valueOf(String.valueOf(value));
        }
        if (actual instanceof Number) {
            BigDecimal number = number(value);
            return number == null ? value : number;
        }
        if ("null".equals(value)) {
            return null;
        }
        return value;
    }

    /**
     * 判断相等值条件是否成立，供调用方选择后续分支。
     *
     * @param actual 实际，作为 {@code number} 的输入影响后续处理
     * @param expected 预期，作为 {@code number} 的输入影响后续处理
     * @return 相等值条件成立时为 true，否则为 false
     */
    private boolean equalsValue(Object actual, Object expected) {
        if (actual instanceof Number) {
            BigDecimal left = number(actual);
            BigDecimal right = number(expected);
            return left != null && right != null
                    && left.compareTo(right) == 0;
        }
        return Objects.equals(actual, expected);
    }

    /**
     * 比较已发布表单条件求值器；结果供调用方的后续步骤使用。
     *
     * @param actual 实际，作为 {@code number} 的输入影响后续处理
     * @param expected 预期，作为 {@code number} 的输入影响后续处理
     * @return 比较后的已发布表单条件求值器结果，供调用方继续处理
     */
    private int compare(Object actual, Object expected) {
        if (actual instanceof Number) {
            BigDecimal left = number(actual);
            BigDecimal right = number(expected);
            if (left != null && right != null) {
                return left.compareTo(right);
            }
        }
        return String.valueOf(actual).compareTo(String.valueOf(expected));
    }

    /**
     * 复现浏览器 {@code String(actual ?? '').includes(String(expected ?? ''))}。
     *
     * <p>MULTI_SELECT/CHECKBOX 在浏览器中是数组；{@code String(array)} 使用逗号
     * 连接元素，且 null/undefined 元素变成空片段。Java Collection.toString() 的
     * 方括号和空格会改变 contains 结果，因此不能直接使用 String.valueOf。</p>
     *
     * @param actual 实际，作为 {@code javascriptNullishString} 的输入影响后续处理
     * @param expected 预期，供本方法判断是否包含值时使用
     * @return 值条件成立时为 true，否则为 false
     */
    private boolean containsValue(Object actual, Object expected) {
        return javascriptNullishString(actual)
                .contains(javascriptNullishString(expected));
    }

    /**
     * 生成{@code javascript}{@code nullish}字符串文本，供后续匹配或展示。
     *
     * @param value 待处理{@code javascript}{@code nullish}字符串的原始输入，结果供调用方继续使用
     * @return 处理后的{@code javascript}{@code nullish}字符串文本，供调用方比较或展示
     */
    private String javascriptNullishString(Object value) {
        if (value == MISSING || value == null) {
            return "";
        }
        return javascriptString(value);
    }

    /**
     * 生成{@code javascript}字符串文本，供后续匹配或展示。
     *
     * @param value 待处理{@code javascript}字符串的原始输入，结果供调用方继续使用
     * @return 处理后的{@code javascript}字符串文本，供调用方比较或展示
     */
    private String javascriptString(Object value) {
        if (value instanceof Collection<?> values) {
            return values.stream()
                    .map(this::javascriptArrayElementString)
                    .collect(java.util.stream.Collectors.joining(","));
        }
        if (value != null && value.getClass().isArray()) {
            java.util.ArrayList<String> values = new java.util.ArrayList<>();
            for (int index = 0; index < Array.getLength(value); index++) {
                values.add(javascriptArrayElementString(
                        Array.get(value, index)));
            }
            return String.join(",", values);
        }
        if (value instanceof Map<?, ?>) {
            return "[object Object]";
        }
        return String.valueOf(value);
    }

    /**
     * 生成{@code javascript}数组元素字符串文本，供后续匹配或展示。
     *
     * @param value 待处理{@code javascript}数组元素字符串的原始输入，结果供调用方继续使用
     * @return 处理后的{@code javascript}数组元素字符串文本，供调用方比较或展示
     */
    private String javascriptArrayElementString(Object value) {
        return value == null || value == MISSING
                ? "" : javascriptString(value);
    }

    /**
     * 处理数值，并将结果传给后续步骤。
     *
     * @param value 待处理数值的原始输入，结果供调用方继续使用
     * @return 处理后的数值结果，供调用方继续处理
     */
    private BigDecimal number(Object value) {
        if (value == null || value instanceof Boolean) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 判断是否空；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否空的原始输入，结果供调用方继续使用
     * @return 空条件成立时为 true，否则为 false
     */
    private boolean isEmpty(Object value) {
        if (value == MISSING || value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.trim().isEmpty();
        }
        if (value instanceof Collection<?> values) {
            return values.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return value.getClass().isArray() && Array.getLength(value) == 0;
    }

    /**
     * 将结构化条件规范化成浏览器 {@code parseFlowConditionConfig} 使用的运行时形状。
     *
     * <p>发布校验仍会严格拒绝未知类型、操作符和版本；这里保留浏览器的兼容归一化，
     * 让历史快照在服务端鉴权/必填校验与页面联动中得到相同结果。</p>
     *
     * @param configuration 配置内容，决定后续运行时根的处理规则
     * @return 运行时根键值结果，供调用方继续处理
     */
    private Map<String, Object> runtimeRoot(Object configuration) {
        Object root = objectMap(configuration).get("root");
        if (!(root instanceof Map<?, ?> rootMap)) {
            return Map.of();
        }
        return normalizeRuntimeNode(rootMap);
    }

    /**
     * 递归丢弃浏览器无法识别的节点，并规范化组逻辑、操作符和条件值。
     *
     * @param configured 已配置，供本方法规范化运行时节点时使用
     * @return 运行时节点键值结果，供调用方继续处理
     */
    private Map<String, Object> normalizeRuntimeNode(Object configured) {
        if (!(configured instanceof Map<?, ?> configuredMap)) {
            return Map.of();
        }
        Map<String, Object> node = stringMap(configuredMap);
        String type = text(node.get("type"));
        if ("GROUP".equals(type)) {
            java.util.ArrayList<Map<String, Object>> children =
                    new java.util.ArrayList<>();
            if (node.get("children") instanceof List<?> configuredChildren) {
                for (Object child : configuredChildren) {
                    Map<String, Object> normalized =
                            normalizeRuntimeNode(child);
                    if (!normalized.isEmpty()) {
                        children.add(normalized);
                    }
                }
            }
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("type", "GROUP");
            result.put("logic", "OR".equals(text(node.get("logic")))
                    ? "OR" : "AND");
            result.put("children", children);
            return result;
        }
        String property = text(node.get("property"));
        if (!"CONDITION".equals(type) && property.isEmpty()) {
            return Map.of();
        }
        String operator = normalizeOperator(text(node.get("operator")));
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("type", "CONDITION");
        result.put("property", property);
        result.put("operator", OPERATORS.contains(operator)
                ? operator : "==");
        Object value = node.get("value");
        result.put("value", value == null ? "" : String.valueOf(value));
        return result;
    }

    /**
     * 与浏览器 {@code isFlowConditionGroupComplete} 保持同一完整性判定。
     *
     * @param node 节点，作为 {@code text} 的输入影响后续处理
     * @return 运行时节点完成条件成立时为 true，否则为 false
     */
    private boolean isRuntimeNodeComplete(Map<String, Object> node) {
        if ("GROUP".equals(text(node.get("type")))) {
            if (!(node.get("children") instanceof List<?> children)
                    || children.isEmpty()) {
                return false;
            }
            return children.stream().allMatch(child ->
                    child instanceof Map<?, ?> childMap
                            && isRuntimeNodeComplete(stringMap(childMap)));
        }
        if (!"CONDITION".equals(text(node.get("type")))
                || text(node.get("property")).isEmpty()) {
            return false;
        }
        String operator = text(node.get("operator"));
        return Set.of("empty", "notEmpty").contains(operator)
                || !isEmpty(node.get("value"));
    }

    /**
     * 整理对象映射数据，供调用方遍历或继续处理。
     *
     * @param value 待处理对象映射的原始输入，结果供调用方继续使用
     * @return 对象映射键值结果，供调用方继续处理
     */
    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return stringMap(map);
        }
        if (!(value instanceof String text) || !StringUtils.hasText(text)) {
            return Map.of();
        }
        try {
            Object parsed = objectMapper.readValue(text, Object.class);
            return parsed instanceof Map<?, ?> map
                    ? stringMap(map) : Map.of();
        } catch (Exception exception) {
            return Map.of();
        }
    }

    /**
     * 整理安全记录数据，供调用方遍历或继续处理。
     *
     * @param record 记录，供本方法处理安全记录时使用
     * @return 安全记录键值结果，供调用方继续处理
     */
    private Map<String, Object> safeRecord(Map<String, Object> record) {
        return record == null ? Map.of() : record;
    }

    /**
     * 将输入映射的键规范为字符串，供后续序列化和字段读取。
     *
     * @param source 待处理字符串映射的原始输入，结果供调用方继续使用
     * @return 字符串映射键值结果，供调用方继续处理
     */
    private Map<String, Object> stringMap(Map<?, ?> source) {
        java.util.LinkedHashMap<String, Object> result =
                new java.util.LinkedHashMap<>();
        source.forEach((key, value) ->
                result.put(String.valueOf(key), value));
        return result;
    }

    /**
     * 整理{@code split}{@code top}层级数据，供调用方遍历或继续处理。
     *
     * @param expression 表达式，作为 {@code parts.add} 的输入影响后续处理
     * @param operator 操作人，供本方法处理{@code split}{@code top}层级时使用
     * @return 已发布表单条件求值器集合，供调用方遍历或展示
     */
    private List<String> splitTopLevel(
            String expression,
            String operator) {
        java.util.ArrayList<String> parts = new java.util.ArrayList<>();
        int start = 0;
        int depth = 0;
        char quote = 0;
        for (int index = 0; index < expression.length(); index++) {
            char character = expression.charAt(index);
            if (quote != 0) {
                if (character == '\\') {
                    index++;
                } else if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
            } else if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
            } else if (depth == 0
                    && expression.startsWith(operator, index)) {
                parts.add(expression.substring(start, index).trim());
                index += operator.length() - 1;
                start = index + 1;
            }
        }
        if (parts.isEmpty()) {
            return List.of(expression.trim());
        }
        parts.add(expression.substring(start).trim());
        return parts.stream().filter(StringUtils::hasText).toList();
    }

    /**
     * 生成{@code strip}{@code outer}{@code parentheses}文本，供后续匹配或展示。
     *
     * @param expression 表达式，供本方法处理{@code strip}{@code outer}{@code parentheses}时使用
     * @return 处理后的{@code strip}{@code outer}{@code parentheses}文本，供调用方比较或展示
     */
    private String stripOuterParentheses(String expression) {
        String result = expression;
        while (isWrappedParentheses(result)) {
            result = result.substring(1, result.length() - 1).trim();
        }
        return result;
    }

    /**
     * 判断是否{@code wrapped}{@code parentheses}；判断结果决定调用方的后续分支。
     *
     * @param expression 表达式，供本方法判断是否{@code wrapped}{@code parentheses}时使用
     * @return {@code wrapped}{@code parentheses}条件成立时为 true，否则为 false
     */
    private boolean isWrappedParentheses(String expression) {
        if (!expression.startsWith("(") || !expression.endsWith(")")) {
            return false;
        }
        int depth = 0;
        char quote = 0;
        for (int index = 0; index < expression.length(); index++) {
            char character = expression.charAt(index);
            if (quote != 0) {
                if (character == '\\') {
                    index++;
                } else if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
            } else if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
            }
            if (depth == 0 && index < expression.length() - 1) {
                return false;
            }
        }
        return depth == 0 && quote == 0;
    }

    /**
     * 判断是否{@code wrapped}模板；判断结果决定调用方的后续分支。
     *
     * @param expression 表达式，供本方法判断是否{@code wrapped}模板时使用
     * @return {@code wrapped}模板条件成立时为 true，否则为 false
     */
    private boolean isWrappedTemplate(String expression) {
        if (!expression.startsWith("${") || !expression.endsWith("}")) {
            return false;
        }
        int depth = 1;
        for (int index = 2; index < expression.length(); index++) {
            char character = expression.charAt(index);
            if (character == '{') depth++;
            if (character == '}') depth--;
            if (depth == 0) return index == expression.length() - 1;
        }
        return false;
    }

    /**
     * 规范化旧版空{@code conditions}；输出作为后续校验或处理的输入。
     *
     * @param expression 表达式，供本方法规范化旧版空{@code conditions}时使用
     * @return 规范化后的旧版空{@code conditions}文本，供调用方比较或展示
     */
    private String normalizeLegacyEmptyConditions(String expression) {
        return expression
                .replaceAll(
                        "!([A-Za-z_][\\w.]*)\\s*\\|\\|\\s*\\1\\s*==\\s*(?:''|\"\")",
                        "empty($1)")
                .replaceAll(
                        "([A-Za-z_][\\w.]*)\\s*&&\\s*\\1\\s*!=\\s*(?:''|\"\")",
                        "notEmpty($1)");
    }

    /**
     * 解码字面值；输出作为后续校验或处理的输入。
     *
     * @param value 待解码字面值的原始输入，结果供调用方继续使用
     * @return 解码后的字面值结果，供调用方继续处理
     */
    private Object decodeLiteral(String value) {
        String source = value.trim();
        if (source.length() >= 2
                && ((source.startsWith("'") && source.endsWith("'"))
                || (source.startsWith("\"") && source.endsWith("\"")))) {
            char quote = source.charAt(0);
            return source.substring(1, source.length() - 1)
                    .replace("\\" + quote, String.valueOf(quote))
                    .replace("\\\\", "\\");
        }
        if ("true".equalsIgnoreCase(source)
                || "false".equalsIgnoreCase(source)) {
            return Boolean.valueOf(source);
        }
        if ("null".equals(source)) {
            return null;
        }
        BigDecimal numeric = number(source);
        return numeric == null ? source : numeric;
    }

    /**
     * 规范化操作人；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化操作人的原始输入，结果供调用方继续使用
     * @return 规范化后的操作人文本，供调用方比较或展示
     */
    private String normalizeOperator(String value) {
        return "===".equals(value) ? "=="
                : "!==".equals(value) ? "!=" : value;
    }

    /**
     * 将输入解析为整数，供后续范围校验或计算使用。
     *
     * @param value 待处理整数的原始输入，结果供调用方继续使用
     * @return 处理后的整数结果，供调用方继续处理
     */
    private int integer(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 构造无效输入异常，阻止后续业务处理。
     *
     * @param label 标签，后续用于处理无效时匹配或展示
     * @param detail 详情，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @return 处理后的无效结果，供调用方继续处理
     */
    private IllegalArgumentException invalid(
            String label,
            String detail) {
        return new IllegalArgumentException(label + detail);
    }
}
