package com.workflow.embed.infrastructure.crypto;

import com.workflow.embed.application.port.EmbedSubjectDigestPort;
import com.workflow.embed.domain.SubjectDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Subject HMAC adapter that reads current and accepted key versions in deterministic order. */
public final class VersionedHmacEmbedSubjectDigest implements EmbedSubjectDigestPort {

    private final String currentVersion;
    private final Map<String, HmacEmbedSubjectDigest> digesters;

    /**
     * 初始化{@code versioned}HMAC嵌入式主体摘要，保存构造参数供后续方法使用。
     *
     * @param currentVersion 当前版本依赖，保存到当前对象供后续业务方法调用
     * @param orderedKeys {@code ordered}键集合，保存在对象中供后续校验、查询或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public VersionedHmacEmbedSubjectDigest(
            String currentVersion,
            Map<String, byte[]> orderedKeys) {
        if (currentVersion == null || currentVersion.isBlank()
                || orderedKeys == null || !orderedKeys.containsKey(currentVersion)) {
            throw new IllegalArgumentException("Current Embed Subject digest key is unavailable");
        }
        this.currentVersion = currentVersion;
        Map<String, HmacEmbedSubjectDigest> configured = new LinkedHashMap<>();
        configured.put(currentVersion,
                new HmacEmbedSubjectDigest(orderedKeys.get(currentVersion), currentVersion));
        orderedKeys.forEach((version, key) -> configured.putIfAbsent(
                version, new HmacEmbedSubjectDigest(key, version)));
        this.digesters = Collections.unmodifiableMap(new LinkedHashMap<>(configured));
    }

    /**
     * 处理摘要，并将结果传给后续步骤。
     *
     * @param applicationId 应用ID，后续用于处理摘要时定位或关联目标
     * @param identityProviderId 身份提供者ID，后续用于处理摘要时定位或关联目标
     * @param namespace 命名空间，供本方法处理摘要时使用
     * @param externalSubject 外部主体，供本方法处理摘要时使用
     * @return 处理后的摘要结果，供调用方继续处理
     */
    @Override
    public SubjectDigest digest(
            String applicationId,
            String identityProviderId,
            String namespace,
            String externalSubject) {
        return digesters.get(currentVersion).digest(
                applicationId, identityProviderId, namespace, externalSubject);
    }

    /**
     * 整理{@code accepted}数据，供调用方遍历或继续处理。
     *
     * @param applicationId 应用ID，后续用于处理{@code accepted}时定位或关联目标
     * @param identityProviderId 身份提供者ID，后续用于处理{@code accepted}时定位或关联目标
     * @param namespace 命名空间，供本方法处理{@code accepted}时使用
     * @param externalSubject 外部主体，供本方法处理{@code accepted}时使用
     * @return 主体摘要集合，供调用方遍历或展示
     */
    @Override
    public List<SubjectDigest> accepted(
            String applicationId,
            String identityProviderId,
            String namespace,
            String externalSubject) {
        List<SubjectDigest> result = new ArrayList<>();
        digesters.values().forEach(digester -> result.add(digester.digest(
                applicationId, identityProviderId, namespace, externalSubject)));
        return List.copyOf(result);
    }
}
