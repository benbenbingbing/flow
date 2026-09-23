package com.workflow.embed.infrastructure.crypto;

import com.workflow.embed.application.port.EmbedSubjectDigestPort;
import com.workflow.embed.domain.SubjectDigest;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA-256 privacy index with explicit application/provider/domain separation. */
public final class HmacEmbedSubjectDigest implements EmbedSubjectDigestPort {

    private final byte[] key;
    private final String keyVersion;

    /**
     * 初始化HMAC嵌入式主体摘要，保存构造参数供后续方法使用。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param keyVersion 键版本依赖，保存到当前对象供后续业务方法调用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public HmacEmbedSubjectDigest(byte[] key, String keyVersion) {
        if (key == null || key.length < 32) {
            throw new IllegalArgumentException("HMAC key must be at least 32 bytes");
        }
        if (keyVersion == null || keyVersion.isBlank()) {
            throw new IllegalArgumentException("HMAC key version is required");
        }
        this.key = Arrays.copyOf(key, key.length);
        this.keyVersion = keyVersion;
    }

    /**
     * 处理摘要，并将结果传给后续步骤。
     *
     * @param applicationId 应用ID，后续用于处理摘要时定位或关联目标
     * @param identityProviderId 身份提供者ID，后续用于处理摘要时定位或关联目标
     * @param namespace 命名空间，供本方法处理摘要时使用
     * @param externalSubject 外部主体，供本方法处理摘要时使用
     * @return 处理后的摘要结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    @Override
    public SubjectDigest digest(
            String applicationId,
            String identityProviderId,
            String namespace,
            String externalSubject) {
        // Must remain byte-for-byte compatible with the management Binding writer. Explicit
        // domain and Namespace separation prevent the same low-entropy Subject from correlating
        // across applications or providers.
        String input = "embed-subject-v1\0" + applicationId + "\0" + identityProviderId
                + "\0" + namespace + "\0" + externalSubject;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return new SubjectDigest(
                    HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8))),
                    keyVersion);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HmacSHA256 is unavailable", error);
        }
    }
}
