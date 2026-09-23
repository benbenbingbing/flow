package com.workflow.embed.application.record;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 为 Embed 幂等范围生成稳定、无密钥的 canonical SHA-256 摘要。 */
@Component
public class EmbedCanonicalRequestHasher {

    private static final String ACTOR_DOMAIN = "embed-actor-v1";
    private static final String REQUEST_DOMAIN = "embed-request-v1";

    private final ObjectMapper objectMapper;

    /**
     * 初始化嵌入式规范请求{@code hasher}，保存构造参数供后续方法使用。
     *
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     */
    public EmbedCanonicalRequestHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Actor 摘要跨 Session/Launch 稳定，同时绑定 Application、Provider、Binding 和 Flow 用户。
     *
     * @param session 会话，作为 {@code material.put} 的输入影响后续处理
     * @return 处理后的操作人作用域摘要文本，供调用方比较或展示
     */
    public String actorScopeDigest(AuthenticatedEmbedSession session) {
        if (session == null
                || !StringUtils.hasText(session.applicationId())
                || !StringUtils.hasText(session.identityProviderId())
                || !StringUtils.hasText(session.identityBindingId())
                || !StringUtils.hasText(session.flowUserId())) {
            throw new IllegalStateException("Embed 会话缺少稳定 Actor 身份");
        }
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("domain", ACTOR_DOMAIN);
        material.put("applicationId", session.applicationId());
        material.put("identityProviderId", session.identityProviderId());
        material.put("identityBindingId", session.identityBindingId());
        material.put("flowUserId", session.flowUserId());
        return digest(material);
    }

    /**
     * 计算写请求摘要。Session/Launch/Release/Trace/Locale 均不进入材料，允许安全跨启动重放。
     *
     * @param applicationId 应用ID，后续用于处理请求哈希时定位或关联目标
     * @param actorScopeDigest 操作人作用域摘要，作为 {@code material.put} 的输入影响后续处理
     * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
     * @param operation 操作标识，决定后续请求哈希采用的处理分支
     * @param targetType 目标类型标识，决定后续请求哈希采用的处理分支
     * @param target 目标，作为 {@code material.put} 的输入影响后续处理
     * @param requestBody 请求请求体，作为 {@code material.put} 的输入影响后续处理
     * @return 处理后的请求哈希文本，供调用方比较或展示
     */
    public String requestHash(
            String applicationId,
            String actorScopeDigest,
            String viewKey,
            String operation,
            String targetType,
            Object target,
            Object requestBody) {
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("domain", REQUEST_DOMAIN);
        material.put("applicationId", applicationId);
        material.put("actorScopeDigest", actorScopeDigest);
        material.put("viewKey", viewKey);
        material.put("operation", operation);
        material.put("targetType", targetType);
        material.put("target", target);
        material.put("body", requestBody);
        return digest(material);
    }

    /**
     * 生成摘要文本，供后续匹配或展示。
     *
     * @param value 待处理摘要的原始输入，结果供调用方继续使用
     * @return 处理后的摘要文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private String digest(Object value) {
        try {
            JsonNode canonical = canonicalize(objectMapper.valueToTree(value));
            byte[] encoded = objectMapper.writeValueAsBytes(canonical);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(encoded));
        } catch (JsonProcessingException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("无法计算 Embed canonical 摘要", error);
        }
    }

    /**
     * 规范化嵌入式规范请求{@code hasher}；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化嵌入式规范请求{@code hasher}的原始输入，结果供调用方继续使用
     * @return 规范化后的嵌入式规范请求{@code hasher}结果，供调用方继续处理
     */
    private JsonNode canonicalize(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            java.util.stream.StreamSupport.stream(
                            ((Iterable<Map.Entry<String, JsonNode>>) () ->
                                    value.fields()).spliterator(), false)
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .forEach(entry -> result.set(
                            entry.getKey(), canonicalize(entry.getValue())));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            value.forEach(item -> result.add(canonicalize(item)));
            return result;
        }
        return value;
    }
}
