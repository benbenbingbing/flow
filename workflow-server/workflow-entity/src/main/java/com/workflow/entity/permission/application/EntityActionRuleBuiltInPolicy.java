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
                requireOperator(node, SIMPLE_OPERATORS);
                requireAllowed(
                        node.get("value"), PROCESS_STATES,
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

    private static void requireOperator(
            Map<String, Object> node,
            Set<String> allowed) {
        String operator = normalized(node.get("operator"));
        if (!allowed.contains(operator)) {
            throw new IllegalArgumentException(
                    "当前按钮条件不支持运算符: " + operator);
        }
    }

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

    private static boolean hasScalarValue(Object value) {
        return value != null
                && !(value instanceof Collection<?>)
                && !(value instanceof Map<?, ?>)
                && (!(value instanceof String text)
                || StringUtils.hasText(text));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> node(Object value) {
        return value == null ? null : (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> children(
            Map<String, Object> node) {
        return (List<Map<String, Object>>) node.get("children");
    }

    private static String normalized(Object value) {
        return text(value).toUpperCase(Locale.ROOT);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
