package com.workflow.process.configintelligence.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 声明式蓝图参数引擎，只处理 ${{parameter}} 占位符，不解析表达式或执行脚本。
 */
@Component
@RequiredArgsConstructor
public class BlueprintTemplateEngine {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{\\{([A-Za-z][A-Za-z0-9_.-]{0,63})}}" );
    private final ObjectMapper objectMapper;

    public RenderResult render(
            Map<String, Object> schema,
            Map<String, Object> bundle,
            Map<String, Object> parameters) {
        Map<String, Object> effective = validateParameters(schema, parameters);
        JsonNode rendered = renderNode(objectMapper.valueToTree(bundle == null ? Map.of() : bundle), effective, "$");
        ensureNoPlaceholders(rendered, "$");
        return new RenderResult(
                Map.copyOf(effective),
                objectMapper.convertValue(rendered, new TypeReference<Map<String, Object>>() { }));
    }

    private Map<String, Object> validateParameters(
            Map<String, Object> schema,
            Map<String, Object> supplied) {
        JsonNode definitions = objectMapper.valueToTree(schema == null ? Map.of() : schema).path("parameters");
        Map<String, Object> effective = new LinkedHashMap<>();
        Map<String, Object> input = supplied == null ? Map.of() : supplied;
        Set<String> declared = new java.util.LinkedHashSet<>();
        definitions.fields().forEachRemaining(entry -> {
            String name = entry.getKey();
            declared.add(name);
            JsonNode definition = entry.getValue();
            Object value = input.get(name);
            if (value == null && definition.has("default")) {
                value = objectMapper.convertValue(definition.get("default"), Object.class);
            }
            if (value == null && definition.path("required").asBoolean(false)) {
                throw new IllegalArgumentException("缺少蓝图必填参数: " + name);
            }
            if (value != null) {
                validateType(name, value, definition.path("type").asText("string"));
                if (definition.path("enum").isArray()) {
                    JsonNode valueNode = objectMapper.valueToTree(value);
                    boolean matched = false;
                    for (JsonNode option : definition.path("enum")) {
                        matched |= option.equals(valueNode);
                    }
                    if (!matched) {
                        throw new IllegalArgumentException("蓝图参数 " + name + " 不在允许选项中");
                    }
                }
                String pattern = definition.path("pattern").asText();
                if (StringUtils.hasText(pattern)
                        && value instanceof String text
                        && !Pattern.compile(pattern).matcher(text).matches()) {
                    throw new IllegalArgumentException("蓝图参数 " + name + " 格式不符合约束");
                }
                effective.put(name, value);
            }
        });
        java.util.LinkedHashSet<String> unknown = new java.util.LinkedHashSet<>(input.keySet());
        unknown.removeAll(declared);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("包含未声明的蓝图参数: " + String.join(", ", unknown));
        }
        return effective;
    }

    private JsonNode renderNode(JsonNode node, Map<String, Object> parameters, String path) {
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry ->
                    result.set(entry.getKey(), renderNode(entry.getValue(), parameters, path + "/" + entry.getKey())));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            for (int index = 0; index < node.size(); index++) {
                result.add(renderNode(node.get(index), parameters, path + "/" + index));
            }
            return result;
        }
        if (!node.isTextual()) {
            return node.deepCopy();
        }
        String text = node.asText();
        Matcher exact = PLACEHOLDER.matcher(text);
        if (exact.matches()) {
            Object value = requireParameter(parameters, exact.group(1), path);
            return objectMapper.valueToTree(value);
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            Object value = requireParameter(parameters, matcher.group(1), path);
            if (value instanceof Map<?, ?> || value instanceof Iterable<?>) {
                throw new IllegalArgumentException("复合参数只能作为完整字段值使用: " + matcher.group(1));
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(rendered);
        return objectMapper.getNodeFactory().textNode(rendered.toString());
    }

    private Object requireParameter(Map<String, Object> parameters, String name, String path) {
        if (!parameters.containsKey(name)) {
            throw new IllegalArgumentException("蓝图路径 " + path + " 引用了未提供参数: " + name);
        }
        return parameters.get(name);
    }

    private void ensureNoPlaceholders(JsonNode node, String path) {
        if (node.isTextual() && PLACEHOLDER.matcher(node.asText()).find()) {
            throw new IllegalArgumentException("蓝图实例化后仍有未解析参数: " + path);
        }
        if (node.isContainerNode()) {
            java.util.Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                ensureNoPlaceholders(entry.getValue(), path + "/" + entry.getKey());
            }
            if (node.isArray()) {
                for (int index = 0; index < node.size(); index++) {
                    ensureNoPlaceholders(node.get(index), path + "/" + index);
                }
            }
        }
    }

    private void validateType(String name, Object value, String type) {
        boolean valid = switch (type.toLowerCase()) {
            case "string" -> value instanceof String;
            case "number", "integer" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof Iterable<?> || value.getClass().isArray();
            case "object" -> value instanceof Map<?, ?>;
            default -> throw new IllegalArgumentException("蓝图参数 " + name + " 使用未知类型: " + type);
        };
        if (!valid) {
            throw new IllegalArgumentException("蓝图参数 " + name + " 类型应为 " + type);
        }
    }

    public record RenderResult(
            Map<String, Object> effectiveParameters,
            Map<String, Object> renderedBundle) {
    }
}
