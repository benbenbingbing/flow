package com.workflow.entity.list.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
     * 返回独立的查询条件副本，并以可信条件替换同字段的整组用户条件。
     * 同字段的 _op/_start/_end 必须一起处理，否则客户端可通过遗留运算符放宽固定值。
     * 深拷贝避免事件 Provider 原地修改 IN 数组后污染后续步骤的可信条件。
     *
     * @param input 待应用实体列表固定过滤条件的原始输入，结果供调用方继续使用
     * @param trusted 可信，供本方法应用实体列表固定过滤条件时使用
     * @return 实体列表固定过滤条件键值结果，供调用方继续处理
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
