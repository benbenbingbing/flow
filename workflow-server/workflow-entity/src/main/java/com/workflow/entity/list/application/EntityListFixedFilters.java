package com.workflow.entity.list.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 列表固定条件的统一语义，供默认查询、事件接口和权限范围预览共同使用。 */
public final class EntityListFixedFilters {
    private EntityListFixedFilters() {}

    /** 固定值未声明运算符时按等值匹配，不能沿用用户输入或字符串查询的 LIKE 默认值。 */
    public static Map<String, Object> normalize(Map<String, Object> fixed) {
        Map<String, Object> result = new LinkedHashMap<>(fixed == null ? Map.of() : fixed);
        for (String key : List.copyOf(result.keySet())) {
            if (key.equals(field(key))) result.putIfAbsent(key + "_op", "EQ");
        }
        return result;
    }

    /**
     * 返回独立的查询条件副本，并以可信条件替换同字段的整组用户条件。
     * 同字段的 _op/_start/_end 必须一起处理，否则客户端可通过遗留运算符放宽固定值。
     * 深拷贝避免事件 Provider 原地修改 IN 数组后污染后续步骤的可信条件。
     */
    public static Map<String, Object> apply(Map<String, Object> input, Map<String, Object> trusted) {
        Map<String, Object> result = new LinkedHashMap<>(input == null ? Map.of() : input);
        if (trusted != null && !trusted.isEmpty()) {
            Set<String> fields = trusted.keySet().stream().map(EntityListFixedFilters::field)
                    .collect(Collectors.toSet());
            result.keySet().removeIf(key -> fields.contains(field(key)));
            trusted.forEach((key, value) -> result.put(key, copy(value)));
        }
        return result;
    }

    private static Object copy(Object value) {
        if (value instanceof List<?> values) return values.stream().map(EntityListFixedFilters::copy).toList();
        if (value instanceof Map<?, ?> values) {
            Map<String, Object> result = new LinkedHashMap<>();
            values.forEach((key, item) -> result.put(String.valueOf(key), copy(item)));
            return result;
        }
        return value;
    }

    private static String field(String key) {
        for (String suffix : List.of("_start", "_end", "_op")) {
            if (key.endsWith(suffix)) return key.substring(0, key.length() - suffix.length());
        }
        return key;
    }
}
