package com.workflow.contracts.entity.code;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 防止扩展通过嵌套 Map/List 修改后续实际写入的数据；仅用于已规范化的 JSON 业务值。 */
public final class EntityCodeSnapshots {
    private EntityCodeSnapshots() { }

    /** 递归复制并冻结 JSON 对象，保留合法的 null 字段值。 */
    public static Map<String, Object> copy(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source != null) source.forEach((key, value) -> result.put(key, freeze(value)));
        return Collections.unmodifiableMap(result);
    }

    private static Object freeze(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), freeze(item)));
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List<?> list) return list.stream().map(EntityCodeSnapshots::freeze).toList();
        if (value instanceof java.util.Date date) return date.toInstant();
        return value;
    }
}
