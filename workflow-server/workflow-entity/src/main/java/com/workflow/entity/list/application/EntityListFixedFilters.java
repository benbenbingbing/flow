package com.workflow.entity.list.application;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 列表固定条件的统一语义，供默认查询、事件接口和权限范围预览共同使用。 */
public final class EntityListFixedFilters {
    /**
     * 初始化实体列表固定过滤条件，保存构造参数供后续方法使用。
     */
    private EntityListFixedFilters() {}

    /**
     * 固定值未声明运算符时按等值匹配，不能沿用用户输入或字符串查询的 LIKE 默认值。
     *
     * @param fixed 固定，供本方法规范化实体列表固定过滤条件时使用
     * @return 实体列表固定过滤条件键值结果，供调用方继续处理
     */
    public static Map<String, Object> normalize(Map<String, Object> fixed) {
        Map<String, Object> result = new LinkedHashMap<>(fixed == null ? Map.of() : fixed);
        for (String key : List.copyOf(result.keySet())) {
            if (key.equals(field(key))) result.putIfAbsent(key + "_op", "EQ");
        }
        return result;
    }

    /**
     * 返回独立的查询条件副本。普通值条件与范围条件可同时交给 SQL 查询器按 AND 执行，
     * 因此固定下界不能抹掉用户的等值筛选。相同方向的边界取更严格者，可信普通值及其
     * 运算符仍覆盖用户同名键，避免客户端通过修改运算符放宽固定值。
     * 深拷贝避免事件 Provider 原地修改 IN 数组后污染后续步骤的可信条件。
     *
     * @param input 待应用实体列表固定过滤条件的原始输入，结果供调用方继续使用
     * @param trusted 可信，供本方法应用实体列表固定过滤条件时使用
     * @return 实体列表固定过滤条件键值结果，供调用方继续处理
     */
    public static Map<String, Object> apply(Map<String, Object> input, Map<String, Object> trusted) {
        Map<String, Object> result = new LinkedHashMap<>(input == null ? Map.of() : input);
        if (trusted != null && !trusted.isEmpty()) {
            trusted.forEach((key, value) -> {
                String base = field(key);
                if ((key.endsWith("_start") || key.endsWith("_end")) && result.containsKey(key)) {
                    result.put(key, tighterBound(result.get(key), value, key.endsWith("_start")));
                } else if (key.endsWith("_op") && !trusted.containsKey(base) && result.containsKey(base)) {
                    // BETWEEN 等范围运算符不参与标量比较；保留用户标量的运算符。
                    // 范围上下界仍由可信值约束，无法通过此运算符修改放宽。
                    return;
                } else {
                    result.put(key, copy(value));
                }
            });
        }
        return result;
    }

    /**
     * 固定等值与用户同字段等值/集合筛选相斥时提前返回空页。扁平过滤格式不能
     * 同时携带两份标量条件，直接覆盖用户值会让页面显示与查询输入不一致。
     * 只判断可精确求交的运算符；其它运算符仍由固定条件确保访问范围不扩大。
     */
    public static boolean conflictsWithFixedEquality(Map<String, Object> input, Map<String, Object> trusted) {
        if (input == null || trusted == null) return false;
        for (Map.Entry<String, Object> entry : trusted.entrySet()) {
            String key = entry.getKey();
            if (!key.equals(field(key)) || !input.containsKey(key)
                    || !"EQ".equalsIgnoreCase(String.valueOf(trusted.getOrDefault(key + "_op", "EQ")))) {
                continue;
            }
            Object requested = input.get(key);
            String operation = input.containsKey(key + "_op")
                    ? String.valueOf(input.get(key + "_op")).toUpperCase()
                    : requested instanceof String ? "LIKE" : "EQ";
            boolean contains = requested instanceof List<?> values
                    && values.stream().anyMatch(value -> equalValue(value, entry.getValue()));
            if ("EQ".equals(operation) && !equalValue(requested, entry.getValue())
                    || "NE".equals(operation) && equalValue(requested, entry.getValue())
                    || "IN".equals(operation) && !contains
                    || "NOT_IN".equals(operation) && contains) return true;
        }
        return false;
    }

    private static boolean equalValue(Object left, Object right) {
        if (Objects.equals(left, right)) return true;
        if (!(left instanceof Number) && !(right instanceof Number)) return false;
        try {
            return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString())) == 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** 同向数值边界取交集；无法确定数据库排序语义时保留可信边界，防止放宽发布范围。 */
    private static Object tighterBound(Object user, Object trusted, boolean lower) {
        if (user == null || user instanceof String text && text.isBlank()) return copy(trusted);
        if (trusted == null || trusted instanceof String text && text.isBlank()) return copy(user);
        int comparison;
        try {
            comparison = new BigDecimal(user.toString()).compareTo(new BigDecimal(trusted.toString()));
        } catch (NumberFormatException ignored) {
            return copy(trusted);
        }
        return copy((lower ? comparison > 0 : comparison < 0) ? user : trusted);
    }

    /**
     * 复制实体列表固定过滤条件；结果供后续流程传递或持久化。
     *
     * @param value 待复制实体列表固定过滤条件的原始输入，结果供调用方继续使用
     * @return 复制后的实体列表固定过滤条件结果，供调用方继续处理
     */
    private static Object copy(Object value) {
        if (value instanceof List<?> values) return values.stream().map(EntityListFixedFilters::copy).toList();
        if (value instanceof Map<?, ?> values) {
            Map<String, Object> result = new LinkedHashMap<>();
            values.forEach((key, item) -> result.put(String.valueOf(key), copy(item)));
            return result;
        }
        return value;
    }

    /**
     * 生成字段文本，供后续匹配或展示。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 处理后的字段文本，供调用方比较或展示
     */
    private static String field(String key) {
        for (String suffix : List.of("_start", "_end", "_op")) {
            if (key.endsWith(suffix)) return key.substring(0, key.length() - suffix.length());
        }
        return key;
    }
}
