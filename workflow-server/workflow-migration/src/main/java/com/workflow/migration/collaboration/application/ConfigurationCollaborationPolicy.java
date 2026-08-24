package com.workflow.migration.collaboration.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** 配置内容摘要、三方合并边界和评审人分离规则。 */
@Component
@RequiredArgsConstructor
public class ConfigurationCollaborationPolicy {

    private final ObjectMapper objectMapper;

    /** 对键顺序无关的规范 JSON 生成稳定 SHA-256。 */
    public String hash(Map<String, Object> content) {
        try {
            JsonNode canonical = canonical(objectMapper.valueToTree(content == null ? Map.of() : content));
            byte[] bytes = objectMapper.writeValueAsBytes(canonical);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成配置协作摘要", exception);
        }
    }

    /**
     * 仅在目标未变化、分支未变化或调用方提交显式冲突解决结果时合并，禁止静默覆盖。
     */
    public MergeDecision merge(
            String baseHash,
            Map<String, Object> target,
            Map<String, Object> source,
            Map<String, Object> resolved) {
        String targetHash = hash(target);
        String sourceHash = hash(source);
        if (targetHash.equals(baseHash)) return new MergeDecision(false, source, "TARGET_UNCHANGED");
        if (sourceHash.equals(baseHash)) return new MergeDecision(false, target, "SOURCE_UNCHANGED");
        if (resolved != null) return new MergeDecision(false, resolved, "EXPLICIT_RESOLUTION");
        return new MergeDecision(true, Map.of(), "BOTH_CHANGED");
    }

    /** 高风险评审不允许申请人自审。 */
    public void requireIndependentReviewer(String requester, String reviewer) {
        if (!StringUtils.hasText(requester) || !StringUtils.hasText(reviewer)) {
            throw new IllegalArgumentException("评审申请人与评审人不能为空");
        }
        if (requester.equals(reviewer)) {
            throw new IllegalArgumentException("配置评审申请人不能审批自己的变更");
        }
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), entry.getValue()));
            sorted.forEach((key, value) -> result.set(key, canonical(value)));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            node.forEach(item -> result.add(canonical(item)));
            return result;
        }
        return node.deepCopy();
    }

    public record MergeDecision(boolean conflict, Map<String, Object> content, String reason) {
    }
}
