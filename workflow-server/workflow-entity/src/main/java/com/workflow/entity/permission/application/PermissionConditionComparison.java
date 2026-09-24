package com.workflow.entity.permission.application;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 按钮和 USER_FIELD 列表权限共享的内存比较语义。
 * 缺失值仅允许 EMPTY 命中；角色集合按交集处理。字符串标识保持字典序，
 * 只有实际输入含 Number 时才数值化，避免用户编码被隐式当作数字。
 * 数据库字段比较仍由 SQL 编译器按字段类型和方言完成。
 */
final class PermissionConditionComparison {
    private PermissionConditionComparison() {}

    /** 比较按钮或用户字段条件。非空操作在任一输入缺失时返回 false，防止取反条件放大访问范围。 */
    static boolean compare(Object actual, String operator, Object expected) {
        String op = operator == null ? "EQ" : operator.toUpperCase(Locale.ROOT);
        if ("EMPTY".equals(op)) {
            return isEmpty(actual);
        }
        if ("NOT_EMPTY".equals(op)) {
            return !isEmpty(actual);
        }
        // 缺少实际字段或比较值时必须失败关闭，尤其不能让 NE/NOT_IN/LT
        // 这类取反或有序比较把“不存在”误判成满足条件。
        if (actual == null || expected == null) {
            return false;
        }
        return switch (op) {
            case "EQ" -> equalsValue(actual, expected);
            case "NE" -> !equalsValue(actual, expected);
            case "IN" -> intersects(actual, expected);
            case "NOT_IN" -> !intersects(actual, expected);
            case "CONTAINS" -> contains(actual, expected);
            case "NOT_CONTAINS" -> !contains(actual, expected);
            case "GT" -> compareOrdered(actual, expected) > 0;
            case "GTE" -> compareOrdered(actual, expected) >= 0;
            case "LT" -> compareOrdered(actual, expected) < 0;
            case "LTE" -> compareOrdered(actual, expected) <= 0;
            default -> false;
        };
    }

    /** 显式数值允许跨数值类型比较；无法数值化时沿用字符串比较。 */
    private static boolean equalsValue(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        if (actual instanceof Number || expected instanceof Number) {
            try {
                return new BigDecimal(String.valueOf(actual))
                        .compareTo(new BigDecimal(String.valueOf(expected))) == 0;
            } catch (NumberFormatException ignored) {
            }
        }
        return String.valueOf(actual).equals(String.valueOf(expected));
    }

    /** 集合按元素相等判断，普通文本按子串判断；不将角色集合整体转成字符串。 */
    private static boolean contains(Object actual, Object expected) {
        if (actual instanceof Collection<?> collection) {
            return collection.stream().anyMatch(value -> equalsValue(value, expected));
        }
        return actual != null && expected != null
                && String.valueOf(actual).contains(String.valueOf(expected));
    }

    /** 角色等集合型实际值按任一交集解释 IN，标量视为单元素集合。 */
    private static boolean intersects(Object actual, Object expected) {
        Collection<?> expectedValues = toCollection(expected);
        Collection<?> actualValues = actual instanceof Collection<?> values
                ? values : List.of(actual);
        return actualValues.stream().anyMatch(left ->
                expectedValues.stream().anyMatch(right ->
                        equalsValue(left, right)));
    }

    /** 字符串身份保留字典序，仅一方明确为 Number 时尝试数值排序。 */
    private static int compareOrdered(Object actual, Object expected) {
        if (actual instanceof Number || expected instanceof Number) {
            try {
                return new BigDecimal(String.valueOf(actual))
                        .compareTo(new BigDecimal(String.valueOf(expected)));
            } catch (NumberFormatException ignored) {
            }
        }
        return String.valueOf(actual).compareTo(String.valueOf(expected));
    }

    /** 沿用规则编辑器的空值定义：null、空白文本、空集合及空映射。 */
    private static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    /** 兼容规则中的列表、对象数组及标量输入。 */
    private static Collection<?> toCollection(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        if (value != null && value.getClass().isArray()) {
            return List.of((Object[]) value);
        }
        return value == null ? List.of() : List.of(value);
    }
}
