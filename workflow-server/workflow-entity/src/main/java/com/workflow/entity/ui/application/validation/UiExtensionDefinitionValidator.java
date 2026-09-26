package com.workflow.entity.ui.application.validation;

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
public class UiExtensionDefinitionValidator {

    private static final Set<String> FORBIDDEN_KEYS =
            Set.of(
                    "sql", "script", "url", "jdbcUrl",
                    "command", "expression");
    private static final Set<String> SCHEMA_TYPES =
            Set.of(
                    "object", "array", "string", "number",
                    "integer", "boolean");

    private final JsonDocumentCodec codec;

    /**
     * 校验执行策略；不满足约束时阻止后续处理。
     *
     * @param policy 策略内容，决定后续执行策略的处理规则
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 校验结构定义；不满足约束时阻止后续处理。
     *
     * @param schema 结构，作为 {@code validateSchemaNode} 的输入影响后续处理
     * @param label 标签，后续用于校验结构定义时匹配或展示
     */
    public void validateSchemaDefinition(
            Map<String, Object> schema,
            String label) {
        if (schema != null && !schema.isEmpty()) {
            validateSchemaNode(schema, label, "$");
        }
    }

    /**
     * 校验结构值；不满足约束时阻止后续处理。
     *
     * @param schema 结构，作为 {@code validateSchemaValueNode} 的输入影响后续处理
     * @param value 待校验结构值的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于校验结构值时匹配或展示
     */
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

    /**
     * 校验无禁止键集合；不满足约束时阻止后续处理。
     *
     * @param value 待校验无禁止键集合的原始输入，结果供调用方继续使用
     * @param path 路径，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 校验结构节点；不满足约束时阻止后续处理。
     *
     * @param schema 结构，作为 {@code schemaType} 的输入影响后续处理
     * @param label 标签，后续用于校验结构节点时匹配或展示
     * @param path 路径，作为 {@code schemaType} 的输入影响后续处理
     */
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

    /**
     * 生成结构类型文本，供后续匹配或展示。
     *
     * @param schema 结构，供本方法处理结构类型时使用
     * @param label 标签，后续用于处理结构类型时匹配或展示
     * @param path 路径，作为 {@code schemaError} 的输入影响后续处理
     * @return 处理后的结构类型文本，供调用方比较或展示
     */
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

    /**
     * 处理JSON兼容值，并将结果传给后续步骤。
     *
     * @param value 待处理JSON兼容值的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于处理JSON兼容值时匹配或展示
     * @return 处理后的JSON兼容值结果，供调用方继续处理
     */
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

    /**
     * 校验结构值节点；不满足约束时阻止后续处理。
     *
     * @param schema 结构，作为 {@code schemaType} 的输入影响后续处理
     * @param value 待校验结构值节点的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于校验结构值节点时匹配或展示
     * @param path 路径，作为 {@code schemaType} 的输入影响后续处理
     */
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

    /**
     * 判断是否匹配结构类型；判断结果决定调用方的后续分支。
     *
     * @param type 类型标识，决定后续结构类型采用的处理分支
     * @param value 待判断是否匹配结构类型的原始输入，结果供调用方继续使用
     * @return 结构类型条件成立时为 true，否则为 false
     */
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

    /**
     * 判断是否{@code finite}数值；判断结果决定调用方的后续分支。
     *
     * @param number 数值，供本方法判断是否{@code finite}数值时使用
     * @return {@code finite}数值条件成立时为 true，否则为 false
     */
    private boolean isFiniteNumber(Number number) {
        if (number instanceof Double value) {
            return Double.isFinite(value);
        }
        if (number instanceof Float value) {
            return Float.isFinite(value);
        }
        return true;
    }

    /**
     * 判断是否整数；判断结果决定调用方的后续分支。
     *
     * @param number 数值，作为 {@code BigDecimal} 的输入影响后续处理
     * @return 整数条件成立时为 true，否则为 false
     */
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

    /**
     * 生成实际类型文本，供后续匹配或展示。
     *
     * @param value 待处理实际类型的原始输入，结果供调用方继续使用
     * @return 处理后的实际类型文本，供调用方比较或展示
     */
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

    /**
     * 构造结构错误异常，供调用方区分失败原因。
     *
     * @param label 标签，后续用于处理结构错误时匹配或展示
     * @param detail 详情，作为 {@code ValidationException} 的输入影响后续处理
     * @return 处理后的结构错误结果，供调用方继续处理
     */
    private ValidationException schemaError(
            String label,
            String detail) {
        return new ValidationException(
                label + " 校验失败: " + detail);
    }

    /**
     * 负责校验的业务处理；协调校验、状态变化及后续结果传递。
     */
    public static final class ValidationException
            extends IllegalArgumentException {

        /**
         * 初始化校验异常，保存构造参数供后续方法使用。
         *
         * @param message 消息，保存在对象中供后续校验、查询或展示
         */
        private ValidationException(String message) {
            super(message);
        }
    }
}
