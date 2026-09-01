package com.workflow.embed.application.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.contracts.embed.EmbedNativeListDependencyClosure;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import org.springframework.util.StringUtils;

/** LIST 依赖闭包的 canonical 编解码与摘要规则。 */
public final class EmbedNativeListDependencyClosureCodec {

    public static final String CONFIG_FIELD = "nativeListDependencyClosure";
    public static final String HASH_FIELD = "nativeListDependencyClosureHash";

    private EmbedNativeListDependencyClosureCodec() {
    }

    /** 从完整 Runtime config 读取闭包，并验证 config 中固定的 closure hash。 */
    public static Decoded decode(ObjectMapper objectMapper, String configJson) {
        try {
            JsonNode config = objectMapper.readTree(configJson);
            if (config == null || !config.isObject()) {
                throw new IllegalArgumentException("Embed Runtime config 不是对象");
            }
            JsonNode closureNode = config.get(CONFIG_FIELD);
            String expectedHash = config.path(HASH_FIELD).asText(null);
            if (closureNode == null || !closureNode.isObject()
                    || !StringUtils.hasText(expectedHash)) {
                throw new IllegalArgumentException("Embed LIST 依赖闭包缺失");
            }
            String actualHash = canonicalHash(objectMapper, closureNode);
            if (!MessageDigest.isEqual(
                    expectedHash.getBytes(StandardCharsets.US_ASCII),
                    actualHash.getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("Embed LIST 依赖闭包摘要不一致");
            }
            EmbedNativeListDependencyClosure closure =
                    objectMapper.treeToValue(
                            closureNode,
                            EmbedNativeListDependencyClosure.class);
            return new Decoded(closure, actualHash);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Embed LIST 依赖闭包无法解析", error);
        }
    }

    /** 对 closure 节点本身规范化后计算 SHA-256，不受外层 config 其它字段影响。 */
    public static String canonicalHash(
            ObjectMapper objectMapper,
            JsonNode closureNode) {
        try {
            String canonical = objectMapper.writeValueAsString(
                    canonicalize(objectMapper, closureNode));
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("无法计算 Embed LIST 依赖闭包摘要", error);
        }
    }

    private static JsonNode canonicalize(
            ObjectMapper objectMapper,
            JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            ArrayList<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
            node.fields().forEachRemaining(fields::add);
            fields.sort(Comparator.comparing(Map.Entry::getKey));
            fields.forEach(field -> result.set(
                    field.getKey(),
                    canonicalize(objectMapper, field.getValue())));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            node.forEach(value -> result.add(canonicalize(objectMapper, value)));
            return result;
        }
        return node.deepCopy();
    }

    public record Decoded(
            EmbedNativeListDependencyClosure closure,
            String hash) {
    }
}
