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

    /**
     * 初始化已配置嵌入式主体{@code digester}，保存构造参数供后续方法使用。
     *
     * @param environment 环境，保存在对象中供后续校验、查询或展示
     */
    public ConfiguredEmbedSubjectDigester(Environment environment) {
        String configuredVersion = environment.getProperty(
                "workflow.embed.crypto.hmac-key-version", "embed-hmac-v1");
        this.currentVersion = configuredVersion == null ? null : configuredVersion.trim();
        this.keys = loadKeys(environment);
    }

    /**
     * 处理当前，并将结果传给后续步骤。
     *
     * @param applicationId 应用ID，后续用于处理当前时定位或关联目标
     * @param providerId 提供者ID，后续用于处理当前时定位或关联目标
     * @param subjectNamespace 主体命名空间，作为 {@code Digest} 的输入影响后续处理
     * @param externalSubject 外部主体，作为 {@code Digest} 的输入影响后续处理
     * @return 处理后的当前结果，供调用方继续处理
     */
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

    /**
     * 整理{@code accepted}数据，供调用方遍历或继续处理。
     *
     * @param applicationId 应用ID，后续用于处理{@code accepted}时定位或关联目标
     * @param providerId 提供者ID，后续用于处理{@code accepted}时定位或关联目标
     * @param subjectNamespace 主体命名空间，供本方法处理{@code accepted}时使用
     * @param externalSubject 外部主体，供本方法处理{@code accepted}时使用
     * @return 摘要集合，供调用方遍历或展示
     */
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

    /**
     * 校验并获取已配置；不满足约束时阻止后续处理。
     */
    private void requireConfigured() {
        if (!StringUtils.hasText(currentVersion) || keys.isEmpty()) {
            throw unavailable();
        }
    }

    /**
     * 加载键集合；查询结果供调用方展示或继续处理。
     *
     * @param environment 环境，作为 {@code addKey} 的输入影响后续处理
     * @return 键集合键值结果，供调用方继续处理
     */
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

    /**
     * 添加键；结果供后续流程传递或持久化。
     *
     * @param environment 环境，供本方法添加键时使用
     * @param target 目标，作为 {@code addEncodedKey} 的输入影响后续处理
     * @param version 版本，作为 {@code environment.getProperty} 的输入影响后续处理
     */
    private static void addKey(Environment environment, Map<String, byte[]> target,
                               String version) {
        String encoded = environment.getProperty(
                "workflow.embed.crypto.hmac-keys." + version);
        addEncodedKey(target, version, encoded);
    }

    /**
     * 添加已编码键；结果供后续流程传递或持久化。
     *
     * @param target 目标，供本方法添加已编码键时使用
     * @param version 版本，作为 {@code target.put} 的输入影响后续处理
     * @param encoded 已编码，供本方法添加已编码键时使用
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 生成摘要文本，供后续匹配或展示。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param applicationId 应用ID，后续用于处理摘要时定位或关联目标
     * @param providerId 提供者ID，后续用于处理摘要时定位或关联目标
     * @param subjectNamespace 主体命名空间，供本方法处理摘要时使用
     * @param externalSubject 外部主体，供本方法处理摘要时使用
     * @return 处理后的摘要文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 构造服务不可用异常，供调用方区分失败原因。
     *
     * @return 处理后的不可用结果，供调用方继续处理
     */
    private static BusinessConflictException unavailable() {
        return new BusinessConflictException(
                "EMBED_SUBJECT_DIGEST_KEY_UNAVAILABLE",
                "Embed Subject 摘要密钥尚未配置");
    }
}
