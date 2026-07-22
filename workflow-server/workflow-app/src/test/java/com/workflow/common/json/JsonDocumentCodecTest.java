package com.workflow.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonDocumentCodecTest {

    private final JsonDocumentCodec codec = new JsonDocumentCodec(new ObjectMapper());

    @Test
    void shouldCanonicalizePortableDocument() {
        String canonical = codec.canonicalize(
                "{\"name\":\"测试\",\"enabled\":true,\"items\":[]}",
                "测试配置");

        assertEquals(
                "{\"enabled\":true,\"items\":[],\"name\":\"测试\"}",
                canonical);
    }

    @Test
    void shouldAddSchemaVersionWithoutTrustingClientValue() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("schemaVersion", 99);
        source.put("mode", "DIALOG");

        Map<String, Object> versioned = codec.ensureSchemaVersion(source, 1);

        assertEquals(1, versioned.get("schemaVersion"));
        assertEquals("DIALOG", versioned.get("mode"));
    }

    @Test
    void shouldRejectPrototypePollutionKeys() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> codec.read("{\"__proto__\":{\"admin\":true}}", "危险配置"));

        assertTrue(exception.getMessage().contains("禁止的配置键"));
    }

    @Test
    void shouldRejectDocumentsBeyondDepthLimit() {
        JsonDocumentCodec shallowCodec = new JsonDocumentCodec(new ObjectMapper(), 1024, 2);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> shallowCodec.read("{\"a\":{\"b\":{\"c\":1}}}", "深层配置"));

        assertTrue(exception.getMessage().contains("嵌套层级"));
    }
}
