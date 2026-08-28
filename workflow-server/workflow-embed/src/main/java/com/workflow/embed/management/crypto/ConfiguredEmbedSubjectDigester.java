package com.workflow.embed.management.crypto;

import com.workflow.core.error.BusinessConflictException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 从受管配置读取版本化 HMAC 密钥的 Subject 摘要实现。
 *
 * <p>当前密钥复用运行态的 {@code workflow.embed.crypto.hmac-key-base64/version}，从而保证管理端
 * 创建的 Binding 与 Launch 验证使用完全相同的 domain separation。轮换窗口中的旧密钥可通过
 * {@code workflow.embed.crypto.accepted-hmac-key-versions} 和
 * {@code workflow.embed.crypto.hmac-keys.<version>} 配置。没有配置时仅相关管理操作失败，不用
 * 不安全的进程随机密钥污染持久化绑定。</p>
 */
@Component
public class ConfiguredEmbedSubjectDigester implements EmbedSubjectDigester {

    private final String currentVersion;
    private final Map<String, byte[]> keys;

    public ConfiguredEmbedSubjectDigester(Environment environment) {
        String configuredVersion = environment.getProperty(
                "workflow.embed.crypto.hmac-key-version", "embed-hmac-v1");
        this.currentVersion = configuredVersion == null ? null : configuredVersion.trim();
        this.keys = loadKeys(environment);
    }

    @Override
    public Digest current(
            String applicationId,
            String providerId,
            String subjectNamespace,
            String externalSubject) {
        requireConfigured();
        byte[] key = keys.get(currentVersion);
        if (key == null) {
            throw unavailable();
        }
        return new Digest(digest(key, applicationId, providerId,
                subjectNamespace, externalSubject), currentVersion);
    }

    @Override
    public List<Digest> accepted(
            String applicationId,
            String providerId,
            String subjectNamespace,
            String externalSubject) {
        requireConfigured();
        List<Digest> result = new ArrayList<>();
        keys.forEach((version, key) -> result.add(
                new Digest(digest(key, applicationId, providerId,
                        subjectNamespace, externalSubject), version)));
        return List.copyOf(result);
    }

    private void requireConfigured() {
        if (!StringUtils.hasText(currentVersion) || keys.isEmpty()) {
            throw unavailable();
        }
    }

    private static Map<String, byte[]> loadKeys(Environment environment) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        String versions = environment.getProperty(
                "workflow.embed.crypto.accepted-hmac-key-versions", "");
        for (String version : versions.split(",")) {
            String normalized = version.trim();
            if (!normalized.isEmpty()) {
                addKey(environment, result, normalized);
            }
        }
        String current = environment.getProperty(
                "workflow.embed.crypto.hmac-key-version", "embed-hmac-v1");
        if (StringUtils.hasText(current)) {
            String encoded = environment.getProperty(
                    "workflow.embed.crypto.hmac-key-base64");
            addEncodedKey(result, current.trim(), encoded);
        }
        return Map.copyOf(result);
    }

    private static void addKey(Environment environment, Map<String, byte[]> target,
                               String version) {
        String encoded = environment.getProperty(
                "workflow.embed.crypto.hmac-keys." + version);
        addEncodedKey(target, version, encoded);
    }

    private static void addEncodedKey(
            Map<String, byte[]> target, String version, String encoded) {
        if (!StringUtils.hasText(encoded)) {
            return;
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Embed Subject 摘要密钥不是合法 Base64", exception);
        }
        if (key.length < 32) {
            throw new IllegalStateException("Embed Subject 摘要密钥至少需要 256 bit");
        }
        target.put(version, key);
    }

    private static String digest(
            byte[] key,
            String applicationId,
            String providerId,
            String subjectNamespace,
            String externalSubject) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            String scoped = "embed-subject-v1\0" + applicationId + "\0" + providerId
                    + "\0" + subjectNamespace + "\0" + externalSubject;
            return java.util.HexFormat.of().formatHex(
                    mac.doFinal(scoped.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法计算 Embed Subject 摘要", exception);
        }
    }

    private static BusinessConflictException unavailable() {
        return new BusinessConflictException(
                "EMBED_SUBJECT_DIGEST_KEY_UNAVAILABLE",
                "Embed Subject 摘要密钥尚未配置");
    }
}
