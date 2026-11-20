package com.workflow.entity.data.api.response;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 仅在实体字段的数字会超出浏览器精度时输出精确字符串，普通数值维持现有 JSON 类型。 */
public class EntityFieldValueSerializer extends JsonSerializer<Object> {
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    private static final BigInteger SAFE_INTEGER = BigInteger.valueOf(MAX_SAFE_INTEGER);
    private static final BigDecimal SAFE_DECIMAL = BigDecimal.valueOf(MAX_SAFE_INTEGER);

    @Override
    public void serialize(Object value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
        serializers.defaultSerializeValue(browserValue(value), generator);
    }

    /** 子表单记录也装在实体数据内，需递归处理其数值以支持重新打开后精确回显。 */
    private static Object browserValue(Object value) {
        if (value instanceof Long number && (number > MAX_SAFE_INTEGER || number < -MAX_SAFE_INTEGER)) {
            return number.toString();
        }
        if (value instanceof BigInteger number && number.abs().compareTo(SAFE_INTEGER) > 0) {
            return number.toString();
        }
        if (value instanceof BigDecimal number && (number.abs().compareTo(SAFE_DECIMAL) > 0
                || number.compareTo(BigDecimal.valueOf(number.doubleValue())) != 0)) {
            return number.toString();
        }
        if (value instanceof List<?> rows) return rows.stream().map(EntityFieldValueSerializer::browserValue).toList();
        if (value instanceof Map<?, ?> row) {
            Map<Object, Object> result = new LinkedHashMap<>();
            row.forEach((key, item) -> result.put(key, browserValue(item)));
            return result;
        }
        return value;
    }
}
