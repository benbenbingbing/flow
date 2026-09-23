package com.workflow.entity.permission.application;

import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 按钮条件内置节点的语义校验策略。
 *
 * <p>结构、深度和节点总量由 {@link EntityActionRuleStructurePolicy}
 * 统一校验；本类只保证内置节点可被运行时明确求值，避免把未完成的条件
 * 保存或发布成恒为 false 的规则。列表允许项目扩展节点，表单则只接受
 * 内置节点。</p>
 */
public final class EntityActionRuleBuiltInPolicy {

    private static final Set<String> BUILT_IN_TYPES = Set.of(
            "GROUP", "RELATION", "PROCESS_STATE", "STATUS_CODE",
            "STATUS_CATEGORY", "FIELD", "USER_FIELD");
    private static final Set<String> RELATIONS = Set.of(
            "CURRENT_USER_IS_CREATOR", "CURRENT_USER_IS_SUBMITTER",
            "CURRENT_USER_IS_ASSIGNEE", "CURRENT_USER_SAME_DEPT");
    private static final Set<String> PROCESS_STATES = Set.of(
            "NOT_STARTED", "RUNNING", "COMPLETED", "TERMINATED",
            "WITHDRAWN");
    private static final Set<String> STATUS_CATEGORIES = Set.of(
            "NEW", "PROCESSING", "COMPLETED", "TERMINATED",
            "WITHDRAWN");
    private static final Set<String> SIMPLE_OPERATORS = Set.of("EQ", "NE");
    private static final Set<String> SET_OPERATORS = Set.of(
            "EQ", "NE", "IN", "NOT_IN");
    private static final Set<String> FIELD_OPERATORS = Set.of(
            "EQ", "NE", "IN", "NOT_IN", "CONTAINS", "NOT_CONTAINS",
            "EMPTY", "NOT_EMPTY", "GT", "GTE", "LT", "LTE");
    private static final Set<String> USER_FIELDS = Set.of(
            "id", "username", "deptId", "orgId", "roleIds");
    private static final Pattern FIELD_NAME =
            Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    /**
     * 初始化实体动作规则{@code built}策略，保存构造参数供后续方法使用。
     */
    private EntityActionRuleBuiltInPolicy() {
    }

    /**
     * 校验 v2 文档中的两棵规则树。
     *
     * @param rule 已通过结构校验的 v2 文档
     * @param allowCustom 是否允许把未知类型交给列表扩展 provider 校验
     */
    public static void validate(
            Map<String, Object> rule,
            boolean allowCustom) {
        validateNode(node(rule.get("visibleWhen")), allowCustom);
        validateNode(node(rule.get("enabledWhen")), allowCustom);
    }

    /**
     * 校验节点；不满足约束时阻止后续处理。
     *
     * @param node 节点，作为 {@code normalized} 的输入影响后续处理
     * @param allowCustom 允许自定义，供本方法校验节点时使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private static void validateNode(
            Map<String, Object> node,
            boolean allowCustom) {
        if (node == null) {
            return;
        }
        String type = normalized(node.get("type"));
        if (!BUILT_IN_TYPES.contains(type)) {
            if (allowCustom) {
                return;
            }
            throw new IllegalArgumentException(
                    "不支持的按钮条件类型: " + type);
        }
        switch (type) {
            case "GROUP" -> children(node).forEach(
                    child -> validateNode(child, allowCustom));
            case "RELATION" -> requireAllowed(
                    node.get("relation"), RELATIONS,
                    "不支持的按钮用户关系: ");
            case "PROCESS_STATE" -> {
                if (node.get("lifecycleVersion") != null && !"1".equals(node.get("lifecycleVersion").toString())) {
                    throw new IllegalArgumentException("不支持的流程状态规则版本");
                }
                requireOperator(node, SIMPLE_OPERATORS);
                requireAllowed(
                        node.get("value"), node.get("lifecycleVersion") != null
                                ? Set.of("NOT_STARTED", "RUNNING", "COMPLETED") : PROCESS_STATES,
                        "不支持的流程状态: ");
            }
            case "STATUS_CODE" -> {
                requireOperator(node, SET_OPERATORS);
                requireComparisonValue(node);
            }
            case "STATUS_CATEGORY" -> {
                requireOperator(node, SET_OPERATORS);
                requireAllowedComparisonValue(
                        node, STATUS_CATEGORIES,
                        "不支持的状态分类: ");
            }
            case "FIELD" -> {
                String field = text(node.get("field"));
                if (!StringUtils.hasText(field)
                        || !FIELD_NAME.matcher(field).matches()) {
                    throw new IllegalArgumentException(
                            "按钮条件包含非法字段名: " + field);
                }
                requireOperator(node, FIELD_OPERATORS);
                requireComparisonValue(node);
            }
            case "USER_FIELD" -> {
                String field = text(node.get("field"));
                if (!USER_FIELDS.contains(field)) {
                    throw new IllegalArgumentException(
                            "不支持的当前用户字段: " + field);
                }
                requireOperator(node, FIELD_OPERATORS);
                requireComparisonValue(node);
            }
            default -> throw new IllegalStateException(
                    "未处理的按钮条件类型: " + type);
        }
    }

    /**
     * 校验并获取操作人；不满足约束时阻止后续处理。
     *
     * @param node 节点，作为 {@code normalized} 的输入影响后续处理
     * @param allowed 允许，供本方法校验并获取操作人时使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static void requireOperator(
            Map<String, Object> node,
            Set<String> allowed) {
        String operator = normalized(node.get("operator"));
        if (!allowed.contains(operator)) {
            throw new IllegalArgumentException(
                    "当前按钮条件不支持运算符: " + operator);
        }
    }

    /**
     * 校验并获取比较值；不满足约束时阻止后续处理。
     *
     * @param node 节点，作为 {@code normalized} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static void requireComparisonValue(
            Map<String, Object> node) {
        String operator = normalized(node.get("operator"));
        if (Set.of("EMPTY", "NOT_EMPTY").contains(operator)) {
            return;
        }
        Object value = node.get("value");
        if (Set.of("IN", "NOT_IN").contains(operator)) {
            if (!(value instanceof Collection<?> values)
                    || values.isEmpty()
                    || values.stream().anyMatch(
                    item -> !hasScalarValue(item))) {
                throw new IllegalArgumentException(
                        "IN/NOT_IN 按钮条件必须配置非空值集合");
            }
            return;
        }
        if (!hasScalarValue(value)) {
            throw new IllegalArgumentException(
                    "按钮条件比较值不能为空");
        }
    }

    /**
     * 校验并获取允许比较值；不满足约束时阻止后续处理。
     *
     * @param node 节点，作为 {@code requireComparisonValue} 的输入影响后续处理
     * @param allowed 允许，作为 {@code values.forEach} 的输入影响后续处理
     * @param messagePrefix 消息前缀，作为 {@code values.forEach} 的输入影响后续处理
     */
    private static void requireAllowedComparisonValue(
            Map<String, Object> node,
            Set<String> allowed,
            String messagePrefix) {
        requireComparisonValue(node);
        Object value = node.get("value");
        if (value instanceof Collection<?> values) {
            values.forEach(item -> requireAllowed(
                    item, allowed, messagePrefix));
            return;
        }
        requireAllowed(value, allowed, messagePrefix);
    }

    /**
     * 校验并获取允许；不满足约束时阻止后续处理。
     *
     * @param value 待校验并获取允许的原始输入，结果供调用方继续使用
     * @param allowed 允许，供本方法校验并获取允许时使用
     * @param messagePrefix 消息前缀，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static void requireAllowed(
            Object value,
            Set<String> allowed,
            String messagePrefix) {
        String normalized = normalized(value);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(
                    messagePrefix + text(value));
        }
    }

    /**
     * 判断是否具有标量值；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否具有标量值的原始输入，结果供调用方继续使用
     * @return 标量值条件成立时为 true，否则为 false
     */
    private static boolean hasScalarValue(Object value) {
        return value != null
                && !(value instanceof Collection<?>)
                && !(value instanceof Map<?, ?>)
                && (!(value instanceof String text)
                || StringUtils.hasText(text));
    }

    /**
     * 整理节点数据，供调用方遍历或继续处理。
     *
     * @param value 待处理节点的原始输入，结果供调用方继续使用
     * @return 节点键值结果，供调用方继续处理
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> node(Object value) {
        return value == null ? null : (Map<String, Object>) value;
    }

    /**
     * 整理子节点数据，供调用方遍历或继续处理。
     *
     * @param node 节点，供本方法处理子节点时使用
     * @return 实体动作规则{@code built}策略集合，供调用方遍历或展示
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> children(
            Map<String, Object> node) {
        return (List<Map<String, Object>>) node.get("children");
    }

    /**
     * 生成规范化文本，供后续匹配或展示。
     *
     * @param value 待处理规范化的原始输入，结果供调用方继续使用
     * @return 处理后的规范化文本，供调用方比较或展示
     */
    private static String normalized(Object value) {
        return text(value).toUpperCase(Locale.ROOT);
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
