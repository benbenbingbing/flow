package com.workflow.openapi.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class IntegrationVariableSchemaService {

    private static final int MAX_SCHEMA_BYTES = 65_535;
    private static final int MAX_SCHEMA_DEPTH = 32;
    private static final int MAX_SCHEMA_NODES = 2_048;
    private static final int MAX_PROPERTIES = 100;
    private static final int MAX_ARRAY_ITEMS = 1_000;
    private static final Set<String> RESERVED_ROOT_PROPERTIES = Set.of(
            "initiator",
            "submitterId",
            "submitterName",
            "entityCode",
            "entityDataId",
            "dataNo",
            "skipNodeEnabled",
            "integrationApplicationId",
            "integrationTraceId",
            "integrationBusinessSystem",
            "integrationBusinessType",
            "integrationBusinessId",
            "integrationExternalInitiatorId");

    private final ObjectMapper objectMapper;
    private final SchemaRegistry schemaRegistry =
            SchemaRegistry.withDefaultDialect(
                    SpecificationVersion.DRAFT_2020_12);

    public IntegrationVariableSchemaService(
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String validateConfiguration(JsonNode schema) {
        if (schema == null || !schema.isObject()) {
            throw new IllegalArgumentException(
                    "输入 Schema 必须是 JSON 对象");
        }
        if (!"object".equals(schema.path("type").asText())) {
            throw new IllegalArgumentException(
                    "输入 Schema 根类型必须是 object");
        }
        validateSchemaNode(schema, 0, new Counter());
        JsonNode properties = schema.path("properties");
        if (properties.isObject()) {
            properties.fieldNames().forEachRemaining(name -> {
                if (RESERVED_ROOT_PROPERTIES.contains(name)) {
                    throw new IllegalArgumentException(
                            "输入 Schema 不能声明引擎保留变量: " + name);
                }
            });
        }
        String serialized = write(schema);
        if (serialized.getBytes(StandardCharsets.UTF_8).length
                > MAX_SCHEMA_BYTES) {
            throw new IllegalArgumentException(
                    "输入 Schema 超过 65535 字节");
        }
        try {
            schemaRegistry.getSchema(serialized, InputFormat.JSON);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "输入 Schema 不是有效的 JSON Schema 2020-12",
                    exception);
        }
        return serialized;
    }

    public List<Violation> validateVariables(
            String schemaJson,
            Map<String, Object> variables) {
        Schema schema;
        try {
            schema = schemaRegistry.getSchema(
                    schemaJson,
                    InputFormat.JSON);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "已发布的输入 Schema 无法加载",
                    exception);
        }
        List<com.networknt.schema.Error> errors = schema.validate(
                write(variables),
                InputFormat.JSON);
        if (errors.isEmpty()) {
            return List.of();
        }
        List<Violation> violations = new ArrayList<>();
        errors.stream()
                .limit(20)
                .forEach(error -> violations.add(new Violation(
                        error.getInstanceLocation().toString(),
                        error.getMessage())));
        return List.copyOf(violations);
    }

    private void validateSchemaNode(
            JsonNode node,
            int depth,
            Counter counter) {
        if (depth > MAX_SCHEMA_DEPTH) {
            throw new IllegalArgumentException(
                    "输入 Schema 嵌套层级超过 32");
        }
        counter.increment();
        if (counter.value > MAX_SCHEMA_NODES) {
            throw new IllegalArgumentException(
                    "输入 Schema 复杂度超过限制");
        }
        if (node.isObject()) {
            JsonNode reference = node.get("$ref");
            if (reference != null
                    && (!reference.isTextual()
                    || !reference.asText().startsWith("#/"))) {
                throw new IllegalArgumentException(
                        "输入 Schema 只允许本地 $ref");
            }
            if (node.has("pattern") || node.has("patternProperties")) {
                throw new IllegalArgumentException(
                        "V1 输入 Schema 不支持正则约束");
            }
            if ("object".equals(node.path("type").asText())) {
                if (!node.has("additionalProperties")
                        || !node.get("additionalProperties").isBoolean()
                        || node.get("additionalProperties").asBoolean()) {
                    throw new IllegalArgumentException(
                            "对象 Schema 必须设置 additionalProperties=false");
                }
                if (!node.has("maxProperties")
                        || !node.get("maxProperties").canConvertToInt()
                        || node.get("maxProperties").intValue() < 0
                        || node.get("maxProperties").intValue()
                        > MAX_PROPERTIES) {
                    throw new IllegalArgumentException(
                            "对象 Schema 的 maxProperties 必须在 0 到 100 之间");
                }
            }
            if ("array".equals(node.path("type").asText())) {
                if (!node.has("maxItems")
                        || !node.get("maxItems").canConvertToInt()
                        || node.get("maxItems").intValue() < 0
                        || node.get("maxItems").intValue()
                        > MAX_ARRAY_ITEMS) {
                    throw new IllegalArgumentException(
                            "数组 Schema 的 maxItems 必须在 0 到 1000 之间");
                }
            }
            Iterator<Map.Entry<String, JsonNode>> fields =
                    node.fields();
            while (fields.hasNext()) {
                validateSchemaNode(
                        fields.next().getValue(),
                        depth + 1,
                        counter);
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                validateSchemaNode(item, depth + 1, counter);
            }
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "JSON 内容无法序列化",
                    exception);
        }
    }

    public record Violation(String path, String reason) {
    }

    private static final class Counter {
        private int value;

        void increment() {
            value++;
        }
    }
}
