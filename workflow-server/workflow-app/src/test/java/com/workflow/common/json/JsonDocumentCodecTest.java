package com.workflow.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON 文档编解码器单元测试。
 *
 * <p>被测对象为 {@link JsonDocumentCodec}，验证可移植文档的规范化排序、
 * schema 版本覆盖、原型污染键拒绝，以及超深嵌套文档拒绝。</p>
 */
class JsonDocumentCodecTest {

    /** 被测编解码器实例 */
    private final JsonDocumentCodec codec = new JsonDocumentCodec(new ObjectMapper());

    /** 规范化应按字段名排序并输出可移植 JSON 字符串 */
    @Test
    void shouldCanonicalizePortableDocument() {
        String canonical = codec.canonicalize(
                "{\"name\":\"测试\",\"enabled\":true,\"items\":[]}",
                "测试配置");

        assertEquals(
                "{\"enabled\":true,\"items\":[],\"name\":\"测试\"}",
                canonical);
    }

    /** 应强制写入 schema 版本且忽略客户端传入的值 */
    @Test
    void shouldAddSchemaVersionWithoutTrustingClientValue() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("schemaVersion", 99);
        source.put("mode", "DIALOG");

        Map<String, Object> versioned = codec.ensureSchemaVersion(source, 1);

        assertEquals(1, versioned.get("schemaVersion"));
        assertEquals("DIALOG", versioned.get("mode"));
    }

    /** 应拒绝含 __proto__ 等原型污染键的文档 */
    @Test
    void shouldRejectPrototypePollutionKeys() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> codec.read("{\"__proto__\":{\"admin\":true}}", "危险配置"));

        assertTrue(exception.getMessage().contains("禁止的配置键"));
    }

    /** 应拒绝超过深度限制的嵌套文档 */
    @Test
    void shouldRejectDocumentsBeyondDepthLimit() {
        JsonDocumentCodec shallowCodec = new JsonDocumentCodec(new ObjectMapper(), 1024, 2);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> shallowCodec.read("{\"a\":{\"b\":{\"c\":1}}}", "深层配置"));

        assertTrue(exception.getMessage().contains("嵌套层级"));
    }
}
