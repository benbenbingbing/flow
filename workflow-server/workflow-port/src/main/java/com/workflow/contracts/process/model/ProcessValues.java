package com.workflow.contracts.process.model;

import java.time.Instant;
import java.util.*;

/** 流程契约的纯值校验与快照转换；不读取身份、不调用平台服务。 */
public final class ProcessValues {
    private ProcessValues() {}

    /** 校验稳定标识，拒绝空白，避免意外查询整个作用域。 */
    public static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return value;
    }

    /** 时间筛选采用包含下界、不含上界的区间。 */
    public static void range(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) throw new IllegalArgumentException("时间区间必须满足 from < to");
    }

    /** 深拷贝动态业务值，保留 null；任何嵌套集合都不能继续修改请求或引擎状态。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> snapshot(Map<String, ?> source) {
        return source == null ? Map.of() : (Map<String, Object>) copy(source, 0);
    }

    /** 单个变量的独立快照；循环对象和不支持的引擎类型明确拒绝。 */
    public static Object snapshotValue(Object value) { return copy(value, 0); }

    private static Object copy(Object value, int depth) {
        // 限制嵌套深度也阻止循环引用耗尽线程栈；标量保留精度及 null 语义。
        if (depth > 32) throw new IllegalArgumentException("流程数据嵌套超过 32 层");
        if (value instanceof Double number && !Double.isFinite(number)
                || value instanceof Float floatValue && !Float.isFinite(floatValue)) {
            throw new IllegalArgumentException("流程数据不支持 NaN 或无穷大");
        }
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof Float || value instanceof Double
                || value instanceof java.math.BigInteger || value instanceof java.math.BigDecimal
                || value instanceof Instant) return value;
        if (value instanceof Date date) return date.toInstant();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                if (!(key instanceof String name)) throw new IllegalArgumentException("流程数据对象键必须为字符串");
                result.put(name, copy(item, depth + 1));
            });
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof Collection<?> list) {
            List<Object> result = new ArrayList<>();
            list.forEach(item -> result.add(copy(item, depth + 1)));
            return Collections.unmodifiableList(result);
        }
        throw new IllegalArgumentException("不支持的流程数据类型: " + value.getClass().getName());
    }
}
