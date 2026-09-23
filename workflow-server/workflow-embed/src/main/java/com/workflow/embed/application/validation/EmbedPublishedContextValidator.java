package com.workflow.embed.application.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Validates Launch context against the local, published JSON-Schema subset used by Embed V1.
 * Remote references and unbounded/deep documents are rejected so validation cannot perform I/O.
 */
@Component
public class EmbedPublishedContextValidator {

    private static final int MAX_CONTEXT_BYTES = 16 * 1024;
    private static final int MAX_PROPERTIES = 32;
    private static final int MAX_DEPTH = 8;

    private final ObjectMapper objectMapper;

    /**
     * 初始化嵌入式已发布上下文校验器，保存构造参数供后续方法使用。
     *
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     */
    public EmbedPublishedContextValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Validates and returns an immutable-safe copy supplied by the caller's record constructor.
     *
     * @param context 执行上下文，向后续嵌入式已发布上下文步骤传递身份、配置或状态
     * @param schemaJson 结构JSON，作为 {@code parseSchema} 的输入影响后续处理
     */
    public void validate(Map<String, Object> context, String schemaJson) {
        Map<String, Object> safeContext = context == null ? Map.of() : context;
        if (safeContext.size() > MAX_PROPERTIES || serializedSize(safeContext) > MAX_CONTEXT_BYTES) {
            throw invalid("Embed context exceeds the published limits");
        }
        JsonNode schema = parseSchema(schemaJson);
        rejectReferencesAndDepth(schema, 0);
        validateNode(objectMapper.valueToTree(safeContext), schema, "$", 0);
    }

    /**
     * 解析结构；输出作为后续校验或处理的输入。
     *
     * @param schemaJson 结构JSON，作为 {@code objectMapper.readTree} 的输入影响后续处理
     * @return 解析后的结构结果，供调用方继续处理
     */
    private JsonNode parseSchema(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()
                || schemaJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > MAX_CONTEXT_BYTES) {
            throw invalid("Published context schema is invalid");
        }
        try {
            JsonNode schema = objectMapper.readTree(schemaJson);
            if (schema == null || !schema.isObject()) {
                throw invalid("Published context schema is invalid");
            }
            return schema;
        } catch (JsonProcessingException error) {
            throw invalid("Published context schema is invalid");
        }
    }

    /**
     * 处理{@code serialized}大小，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续{@code serialized}大小步骤传递身份、配置或状态
     * @return 处理后的{@code serialized}大小结果，供调用方继续处理
     */
    private int serializedSize(Map<String, Object> context) {
        try {
            return objectMapper.writeValueAsBytes(context).length;
        } catch (JsonProcessingException error) {
            throw invalid("Embed context is not JSON serializable");
        }
    }

    /**
     * 处理驳回引用与深度，并将结果传给后续步骤。
     *
     * @param node 节点，供本方法处理驳回引用与深度时使用
     * @param depth 深度，作为 {@code invalid} 的输入影响后续处理
     */
    private void rejectReferencesAndDepth(JsonNode node, int depth) {
        if (depth > MAX_DEPTH || (node.isObject() && node.has("$ref"))) {
            throw invalid("Published context schema contains unsupported references or depth");
        }
        for (JsonNode child : node) {
            rejectReferencesAndDepth(child, depth + 1);
        }
    }

    /**
     * 校验节点；不满足约束时阻止后续处理。
     *
     * @param value 待校验节点的原始输入，结果供调用方继续使用
     * @param schema 结构，作为 {@code validateType} 的输入影响后续处理
     * @param path 路径，作为 {@code validateType} 的输入影响后续处理
     * @param depth 深度，作为 {@code validateObject} 的输入影响后续处理
     */
    private void validateNode(JsonNode value, JsonNode schema, String path, int depth) {
        if (depth > MAX_DEPTH) {
            throw invalid("Embed context is too deeply nested");
        }
        validateType(value, schema.path("type").asText(null), path);
        validateEnum(value, schema.path("enum"), path);
        if (value.isObject()) {
            validateObject(value, schema, path, depth);
        } else if (value.isArray()) {
            validateArray(value, schema, path, depth);
        } else if (value.isTextual()) {
            validateString(value.asText(), schema, path);
        } else if (value.isNumber()) {
            validateNumber(value.decimalValue(), schema, path);
        }
    }

    /**
     * 校验类型；不满足约束时阻止后续处理。
     *
     * @param value 待校验类型的原始输入，结果供调用方继续使用
     * @param type 类型标识，决定后续类型采用的处理分支
     * @param path 路径，作为 {@code invalid} 的输入影响后续处理
     */
    private void validateType(JsonNode value, String type, String path) {
        if (type == null || type.isBlank()) {
            return;
        }
        boolean valid = switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> false;
        };
        if (!valid) {
            throw invalid("Embed context does not match published schema at " + path);
        }
    }

    /**
     * 校验枚举；不满足约束时阻止后续处理。
     *
     * @param value 待校验枚举的原始输入，结果供调用方继续使用
     * @param enumValues 枚举值集合，供本方法校验枚举时使用
     * @param path 路径，作为 {@code invalid} 的输入影响后续处理
     */
    private void validateEnum(JsonNode value, JsonNode enumValues, String path) {
        if (!enumValues.isArray()) {
            return;
        }
        for (JsonNode allowed : enumValues) {
            if (allowed.equals(value)) {
                return;
            }
        }
        throw invalid("Embed context value is not allowed at " + path);
    }

    /**
     * 校验对象；不满足约束时阻止后续处理。
     *
     * @param value 待校验对象的原始输入，结果供调用方继续使用
     * @param schema 结构，供本方法校验对象时使用
     * @param path 路径，作为 {@code invalid} 的输入影响后续处理
     * @param depth 深度，供本方法校验对象时使用
     */
    private void validateObject(JsonNode value, JsonNode schema, String path, int depth) {
        JsonNode properties = schema.path("properties");
        Set<String> required = new HashSet<>();
        schema.path("required").forEach(item -> required.add(item.asText()));
        for (String field : required) {
            if (!value.has(field)) {
                throw invalid("Required Embed context value is missing at " + path + "." + field);
            }
        }

        boolean additionalAllowed = !schema.has("additionalProperties")
                || schema.path("additionalProperties").asBoolean(true);
        Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode propertySchema = properties.path(field.getKey());
            if (propertySchema.isMissingNode()) {
                if (!additionalAllowed) {
                    throw invalid("Embed context property is not published at "
                            + path + "." + field.getKey());
                }
            } else {
                validateNode(field.getValue(), propertySchema, path + "." + field.getKey(), depth + 1);
            }
        }
    }

    /**
     * 校验数组；不满足约束时阻止后续处理。
     *
     * @param value 待校验数组的原始输入，结果供调用方继续使用
     * @param schema 结构，供本方法校验数组时使用
     * @param path 路径，作为 {@code invalid} 的输入影响后续处理
     * @param depth 深度，供本方法校验数组时使用
     */
    private void validateArray(JsonNode value, JsonNode schema, String path, int depth) {
        if (schema.has("maxItems") && value.size() > schema.path("maxItems").asInt()) {
            throw invalid("Embed context array is too large at " + path);
        }
        if (schema.has("minItems") && value.size() < schema.path("minItems").asInt()) {
            throw invalid("Embed context array is too small at " + path);
        }
        JsonNode items = schema.path("items");
        if (!items.isMissingNode()) {
            for (int index = 0; index < value.size(); index++) {
                validateNode(value.get(index), items, path + "[" + index + "]", depth + 1);
            }
        }
    }

    /**
     * 校验字符串；不满足约束时阻止后续处理。
     *
     * @param value 待校验字符串的原始输入，结果供调用方继续使用
     * @param schema 结构，作为 {@code invalid} 的输入影响后续处理
     * @param path 路径，作为 {@code invalid} 的输入影响后续处理
     */
    private void validateString(String value, JsonNode schema, String path) {
        if (schema.has("minLength") && value.length() < schema.path("minLength").asInt()) {
            throw invalid("Embed context string is too short at " + path);
        }
        if (schema.has("maxLength") && value.length() > schema.path("maxLength").asInt()) {
            throw invalid("Embed context string is too long at " + path);
        }
        if (schema.hasNonNull("pattern")) {
            // 历史或被篡改的 Release 也必须在运行端 fail closed，不能只依赖发布校验。
            // java.util.regex 的回溯时间不受输入字节上限可靠约束，V1 不执行外部配置正则。
            throw invalid("Published context schema pattern is unsupported at " + path);
        }
    }

    /**
     * 校验数值；不满足约束时阻止后续处理。
     *
     * @param value 待校验数值的原始输入，结果供调用方继续使用
     * @param schema 结构，供本方法校验数值时使用
     * @param path 路径，作为 {@code invalid} 的输入影响后续处理
     */
    private void validateNumber(BigDecimal value, JsonNode schema, String path) {
        if (schema.has("minimum")
                && value.compareTo(schema.path("minimum").decimalValue()) < 0) {
            throw invalid("Embed context number is below its minimum at " + path);
        }
        if (schema.has("maximum")
                && value.compareTo(schema.path("maximum").decimalValue()) > 0) {
            throw invalid("Embed context number exceeds its maximum at " + path);
        }
    }

    /**
     * 构造无效输入异常，阻止后续业务处理。
     *
     * @param message 消息，作为 {@code EmbedException} 的输入影响后续处理
     * @return 处理后的无效结果，供调用方继续处理
     */
    private static EmbedException invalid(String message) {
        return new EmbedException(400, EmbedErrorCode.EMBED_CONTEXT_INVALID, message);
    }
}
