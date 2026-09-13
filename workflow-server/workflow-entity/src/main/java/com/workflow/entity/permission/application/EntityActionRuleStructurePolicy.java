package com.workflow.entity.permission.application;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 按钮显示/启用条件的 v2 结构边界与通用规范化。
 *
 * <p>该类只处理表单与列表共享的协议约束；节点类型、字段和扩展条件的
 * 业务校验仍由各自配置策略完成。返回值是独立副本，并会将两棵树中
 * IN/NOT_IN 的逗号字符串统一为数组。</p>
 */
public final class EntityActionRuleStructurePolicy {

    private static final int MAX_RULE_DEPTH = 6;
    private static final int MAX_RULE_NODES = 100;
    private static final Set<String> ROOT_KEYS = Set.of(
            "version", "visibleWhen", "enabledWhen", "disabledMessage");

    private EntityActionRuleStructurePolicy() {
    }

    /**
     * 验证并规范化唯一支持的 availabilityRule v2 文档。
     *
     * @param value 条件文档，必须是 Map
     * @return 规范化后的可变 Map 副本
     * @throws IllegalArgumentException 版本、结构或复杂度超限时抛出
     */
    public static Map<String, Object> normalizeAndValidate(Object value) {
        Map<String, Object> source = map(value, "按钮适用条件");
        if (exactInteger(source.get("version"), "按钮条件版本") != 2) {
            throw new IllegalArgumentException("不支持的按钮条件版本");
        }
        Set<String> unknownKeys = new LinkedHashSet<>(source.keySet());
        unknownKeys.removeAll(ROOT_KEYS);
        if (!unknownKeys.isEmpty()) {
            throw new IllegalArgumentException(
                    "按钮适用条件包含不支持的字段: " + unknownKeys);
        }
        Object disabledMessage = source.get("disabledMessage");
        if (disabledMessage != null
                && (!(disabledMessage instanceof String text)
                || text.length() > 300)) {
            throw new IllegalArgumentException(
                    "按钮禁用提示必须是字符串且最多 300 个字符");
        }

        int[] count = {0};
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", 2);
        Map<String, Object> visibleWhen = normalizeNode(
                source.get("visibleWhen"), 1, count);
        Map<String, Object> enabledWhen = normalizeNode(
                source.get("enabledWhen"), 1, count);
        String normalizedMessage = disabledMessage instanceof String text
                        && StringUtils.hasText(text)
                ? text : "";
        if (enabledWhen != null
                && !StringUtils.hasText(normalizedMessage)) {
            throw new IllegalArgumentException(
                    "配置按钮启用条件时必须填写禁用提示");
        }
        result.put("visibleWhen", visibleWhen);
        result.put("enabledWhen", enabledWhen);
        result.put("disabledMessage", normalizedMessage);
        return result;
    }

    private static Map<String, Object> normalizeNode(
            Object value,
            int depth,
            int[] count) {
        if (value == null) {
            return null;
        }
        if (depth > MAX_RULE_DEPTH || ++count[0] > MAX_RULE_NODES) {
            throw new IllegalArgumentException("按钮条件规则过于复杂");
        }
        Map<String, Object> node = map(value, "按钮条件节点");
        String type = text(node.get("type"));
        if (!StringUtils.hasText(type)) {
            throw new IllegalArgumentException("按钮条件缺少类型");
        }
        type = type.toUpperCase(Locale.ROOT);
        Map<String, Object> result = new LinkedHashMap<>(node);
        result.put("type", type);
        if ("GROUP".equals(type)) {
            String logic = text(node.get("logic"));
            if (!"AND".equalsIgnoreCase(logic)
                    && !"OR".equalsIgnoreCase(logic)) {
                throw new IllegalArgumentException(
                        "按钮条件组逻辑只能是 AND 或 OR");
            }
            result.put("logic", logic.toUpperCase(Locale.ROOT));
            if (!(node.get("children") instanceof List<?> children)
                    || children.isEmpty()) {
                throw new IllegalArgumentException("按钮条件组不能为空");
            }
            List<Map<String, Object>> normalizedChildren = new ArrayList<>();
            for (Object child : children) {
                Map<String, Object> normalizedChild = normalizeNode(
                        child, depth + 1, count);
                if (normalizedChild == null) {
                    throw new IllegalArgumentException(
                            "按钮条件子节点不能为空");
                }
                normalizedChildren.add(normalizedChild);
            }
            result.put("children", normalizedChildren);
            return result;
        }
        String operator = text(node.get("operator"));
        if (StringUtils.hasText(operator)) {
            operator = operator.toUpperCase(Locale.ROOT);
            result.put("operator", operator);
        }
        if (node.containsKey("relation")) {
            result.put("relation", text(node.get("relation"))
                    .toUpperCase(Locale.ROOT));
        }
        if (node.containsKey("field")) {
            // USER_FIELD 的 camelCase 字段是协议的一部分，只裁剪空白。
            result.put("field", text(node.get("field")));
        }
        if (("IN".equalsIgnoreCase(operator)
                || "NOT_IN".equalsIgnoreCase(operator))
                && node.get("value") instanceof String text) {
            result.put("value", Arrays.stream(text.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList());
        }
        if ("PROCESS_STATE".equals(type)
                || "STATUS_CATEGORY".equals(type)) {
            result.put("value", normalizeEnumValue(result.get("value")));
        }
        return result;
    }

    /** 内置枚举在持久化前统一为大写，避免校验与执行读取不同形态。 */
    private static Object normalizeEnumValue(Object value) {
        if (value instanceof List<?> values) {
            return values.stream()
                    .map(item -> text(item).toUpperCase(Locale.ROOT))
                    .toList();
        }
        return value instanceof String text
                ? text.trim().toUpperCase(Locale.ROOT)
                : value;
    }

    private static int exactInteger(Object value, String label) {
        if (value instanceof Number number) {
            try {
                return new BigDecimal(String.valueOf(number)).intValueExact();
            } catch (ArithmeticException | NumberFormatException exception) {
                throw new IllegalArgumentException(label + "必须是整数");
            }
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + "必须是整数");
        }
    }

    private static Map<String, Object> map(Object value, String label) {
        if (!(value instanceof Map<?, ?> source)) {
            throw new IllegalArgumentException(label + "必须是对象");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
