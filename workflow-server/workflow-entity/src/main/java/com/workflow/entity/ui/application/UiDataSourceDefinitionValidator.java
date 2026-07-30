package com.workflow.entity.ui.application;

import com.workflow.core.serialization.JsonDocumentCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates interface-service configuration and runtime payload schemas.
 */
@Component
@RequiredArgsConstructor
public class UiDataSourceDefinitionValidator {

    private static final Set<String> FORBIDDEN_KEYS =
            Set.of(
                    "sql", "script", "url", "jdbcUrl",
                    "command", "expression");
    private static final Set<String> SCHEMA_TYPES =
            Set.of(
                    "object", "array", "string", "number",
                    "integer", "boolean");

    private final JsonDocumentCodec codec;

    public void validateExecutionPolicy(
            Map<String, Object> policy) {
        if (policy == null) {
            return;
        }
        int timeout =
                policy.get("timeoutMs") instanceof Number number
                        ? number.intValue() : 3000;
        if (timeout < 100 || timeout > 30000) {
            throw new IllegalArgumentException(
                    "数据源超时必须在 100 到 30000 毫秒之间");
        }
        int cacheSeconds =
                policy.get("cacheSeconds") instanceof Number number
                        ? number.intValue() : 0;
        if (cacheSeconds < 0 || cacheSeconds > 86400) {
            throw new IllegalArgumentException(
                    "数据源缓存时间必须在 0 到 86400 秒之间");
        }
        String failure = String.valueOf(
                        policy.getOrDefault(
                                "failurePolicy",
                                "FAIL"))
                .trim()
                .toUpperCase(Locale.ROOT);
        if (!Set.of("FAIL", "EMPTY", "NULL")
                .contains(failure)) {
            throw new IllegalArgumentException(
                    "不支持的数据源失败策略: " + failure);
        }
    }

    public void validateSchemaDefinition(
            Map<String, Object> schema,
            String label) {
        if (schema != null && !schema.isEmpty()) {
            validateSchemaNode(schema, label, "$");
        }
    }

    public void validateSchemaValue(
            Map<String, Object> schema,
            Object value,
            String label) {
        if (schema == null || schema.isEmpty()) {
            return;
        }
        validateSchemaValueNode(
                schema,
                jsonCompatibleValue(value, label),
                label,
                "$");
    }

    public void validateNoForbiddenKeys(
            Object value,
            String path) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (FORBIDDEN_KEYS.contains(key)) {
                    throw new IllegalArgumentException(
                            "数据源配置禁止使用键: "
                                    + path + "." + key);
                }
                validateNoForbiddenKeys(
                        entry.getValue(),
                        path + "." + key);
            }
        } else if (value instanceof List<?> list) {
            for (int index = 0;
                    index < list.size();
                    index++) {
                validateNoForbiddenKeys(
                        list.get(index),
                        path + "[" + index + "]");
            }
        }
    }

    private void validateSchemaNode(
            Map<?, ?> schema,
            String label,
            String path) {
        String type = schemaType(schema, label, path);
        Object required = schema.get("required");
        if (required != null) {
            if (!(required instanceof List<?> requiredFields)) {
                throw schemaError(
                        label,
                        path
                                + ".required 必须为字符串数组");
            }
            for (int index = 0;
                    index < requiredFields.size();
                    index++) {
                Object field = requiredFields.get(index);
                if (!(field instanceof String text)
                        || !StringUtils.hasText(text)) {
                    throw schemaError(
                            label,
                            path
                                    + ".required["
                                    + index
                                    + "] 必须为非空字符串");
                }
            }
            if (StringUtils.hasText(type)
                    && !"object".equals(type)) {
                throw schemaError(
                        label,
                        path
                                + ".required 只能用于 object");
            }
        }
        Object properties = schema.get("properties");
        if (properties != null) {
            if (!(properties
                    instanceof Map<?, ?> propertySchemas)) {
                throw schemaError(
                        label,
                        path
                                + ".properties 必须为对象");
            }
            if (StringUtils.hasText(type)
                    && !"object".equals(type)) {
                throw schemaError(
                        label,
                        path
                                + ".properties 只能用于 object");
            }
            for (Map.Entry<?, ?> entry
                    : propertySchemas.entrySet()) {
                if (!(entry.getValue()
                        instanceof Map<?, ?> childSchema)) {
                    throw schemaError(
                            label,
                            path
                                    + ".properties."
                                    + entry.getKey()
                                    + " 必须为 Schema 对象");
                }
                validateSchemaNode(
                        childSchema,
                        label,
                        path
                                + ".properties."
                                + entry.getKey());
            }
        }
        Object items = schema.get("items");
        if (items != null) {
            if (!(items instanceof Map<?, ?> itemSchema)) {
                throw schemaError(
                        label,
                        path + ".items 必须为 Schema 对象");
            }
            if (StringUtils.hasText(type)
                    && !"array".equals(type)) {
                throw schemaError(
                        label,
                        path + ".items 只能用于 array");
            }
            validateSchemaNode(
                    itemSchema,
                    label,
                    path + ".items");
        }
    }

    private String schemaType(
            Map<?, ?> schema,
            String label,
            String path) {
        Object configured = schema.get("type");
        if (configured == null) {
            return "";
        }
        if (!(configured instanceof String text)
                || !StringUtils.hasText(text)) {
            throw schemaError(
                    label,
                    path + ".type 必须为非空字符串");
        }
        String type = text.trim()
                .toLowerCase(Locale.ROOT);
        if (!SCHEMA_TYPES.contains(type)) {
            throw schemaError(
                    label,
                    path + ".type 不支持: " + text);
        }
        return type;
    }

    private Object jsonCompatibleValue(
            Object value,
            String label) {
        if (value == null
                || value instanceof Map<?, ?>
                || value instanceof List<?>
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean) {
            return value;
        }
        return codec.read(
                codec.write(
                        value,
                        label + " JSON转换"),
                label + " JSON转换");
    }

    private void validateSchemaValueNode(
            Map<?, ?> schema,
            Object value,
            String label,
            String path) {
        String type = schemaType(schema, label, path);
        if (!StringUtils.hasText(type)) {
            if (schema.containsKey("properties")
                    || schema.containsKey("required")) {
                type = "object";
            } else if (schema.containsKey("items")) {
                type = "array";
            }
        }
        if (StringUtils.hasText(type)
                && !matchesSchemaType(type, value)) {
            throw schemaError(
                    label,
                    path + " 类型应为 " + type
                            + "，实际为 "
                            + actualType(value));
        }
        if ("object".equals(type)) {
            Map<?, ?> object = (Map<?, ?>) value;
            Object required = schema.get("required");
            if (required instanceof List<?> fields) {
                for (Object field : fields) {
                    if (!object.containsKey(
                            String.valueOf(field))) {
                        throw schemaError(
                                label,
                                path + "." + field
                                        + " 为必填字段");
                    }
                }
            }
            if (schema.get("properties")
                    instanceof Map<?, ?> properties) {
                for (Map.Entry<?, ?> entry
                        : properties.entrySet()) {
                    String property =
                            String.valueOf(entry.getKey());
                    if (object.containsKey(property)) {
                        validateSchemaValueNode(
                                (Map<?, ?>)
                                        entry.getValue(),
                                object.get(property),
                                label,
                                path + "." + property);
                    }
                }
            }
        } else if ("array".equals(type)
                && schema.get("items")
                        instanceof Map<?, ?> itemSchema) {
            List<?> values = (List<?>) value;
            for (int index = 0;
                    index < values.size();
                    index++) {
                validateSchemaValueNode(
                        itemSchema,
                        values.get(index),
                        label,
                        path + "[" + index + "]");
            }
        }
    }

    private boolean matchesSchemaType(
            String type,
            Object value) {
        return switch (type) {
            case "object" -> value instanceof Map<?, ?>;
            case "array" -> value instanceof List<?>;
            case "string" -> value instanceof String;
            case "number" -> value instanceof Number number
                    && isFiniteNumber(number);
            case "integer" -> value instanceof Number number
                    && isInteger(number);
            case "boolean" -> value instanceof Boolean;
            default -> false;
        };
    }

    private boolean isFiniteNumber(Number number) {
        if (number instanceof Double value) {
            return Double.isFinite(value);
        }
        if (number instanceof Float value) {
            return Float.isFinite(value);
        }
        return true;
    }

    private boolean isInteger(Number number) {
        if (!isFiniteNumber(number)) {
            return false;
        }
        if (number instanceof Byte
                || number instanceof Short
                || number instanceof Integer
                || number instanceof Long
                || number instanceof BigInteger) {
            return true;
        }
        if (number instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().scale()
                    <= 0;
        }
        if (number instanceof Double value) {
            return value == Math.rint(value);
        }
        if (number instanceof Float value) {
            return value == Math.rint(value);
        }
        try {
            return new BigDecimal(number.toString())
                    .stripTrailingZeros()
                    .scale() <= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String actualType(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        if (value instanceof List<?>) {
            return "array";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Number) {
            return "number";
        }
        return value.getClass().getSimpleName();
    }

    private ValidationException schemaError(
            String label,
            String detail) {
        return new ValidationException(
                label + " 校验失败: " + detail);
    }

    public static final class ValidationException
            extends IllegalArgumentException {

        private ValidationException(String message) {
            super(message);
        }
    }
}
