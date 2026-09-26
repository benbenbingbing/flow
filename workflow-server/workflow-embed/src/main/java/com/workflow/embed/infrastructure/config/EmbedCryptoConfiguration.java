package com.workflow.embed.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.embed.application.port.EmbedContextProtectionPort;
import com.workflow.embed.application.port.EmbedDigestPort;
import com.workflow.embed.application.port.EmbedIdGeneratorPort;
import com.workflow.embed.application.port.EmbedRemoteJwkSetPort;
import com.workflow.embed.application.port.EmbedSecretGeneratorPort;
import com.workflow.embed.application.port.EmbedSignedAssertionVerifierPort;
import com.workflow.embed.application.port.EmbedSubjectDigestPort;
import com.workflow.embed.infrastructure.identity.NimbusEmbedSignedAssertionVerifier;
import com.workflow.embed.infrastructure.crypto.AesGcmEmbedContextProtection;
import com.workflow.embed.infrastructure.crypto.SecureEmbedIdGenerator;
import com.workflow.embed.infrastructure.crypto.SecureEmbedSecretGenerator;
import com.workflow.embed.infrastructure.crypto.Sha256EmbedDigest;
import com.workflow.embed.infrastructure.crypto.VersionedHmacEmbedSubjectDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Constructs cryptographic adapters only when Embed is explicitly enabled. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(EmbedCryptoProperties.class)
public class EmbedCryptoConfiguration {

    /**
     * 处理嵌入式时钟，并将结果传给后续步骤。
     *
     * @return 处理后的嵌入式时钟结果，供调用方继续处理
     */
    @Bean(name = "embedClock")
    Clock embedClock() {
        return Clock.systemUTC();
    }

    /**
     * 处理嵌入式{@code secure}{@code random}，并将结果传给后续步骤。
     *
     * @return 处理后的嵌入式{@code secure}{@code random}结果，供调用方继续处理
     */
    @Bean
    SecureRandom embedSecureRandom() {
        return new SecureRandom();
    }

    /**
     * 处理嵌入式密钥生成器，并将结果传给后续步骤。
     *
     * @param random {@code random}，作为 {@code SecureEmbedSecretGenerator} 的输入影响后续处理
     * @return 处理后的嵌入式密钥生成器结果，供调用方继续处理
     */
    @Bean
    EmbedSecretGeneratorPort embedSecretGenerator(SecureRandom random) {
        return new SecureEmbedSecretGenerator(random);
    }

    /**
     * 处理嵌入式ID生成器，并将结果传给后续步骤。
     *
     * @return 处理后的嵌入式ID生成器结果，供调用方继续处理
     */
    @Bean
    EmbedIdGeneratorPort embedIdGenerator() {
        return new SecureEmbedIdGenerator();
    }

    /**
     * 处理嵌入式摘要，并将结果传给后续步骤。
     *
     * @return 处理后的嵌入式摘要结果，供调用方继续处理
     */
    @Bean
    EmbedDigestPort embedDigest() {
        return new Sha256EmbedDigest();
    }

    /**
     * 处理嵌入式已签名{@code jwt}断言验证器，并将结果传给后续步骤。
     *
     * @param objectMapper 对象映射器，作为 {@code NimbusEmbedSignedAssertionVerifier} 的输入影响后续处理
     * @param remoteJwkSetPort {@code remote}{@code jwk}设置端口，作为 {@code NimbusEmbedSignedAssertionVerifier} 的输入影响后续处理
     * @return 处理后的嵌入式已签名{@code jwt}断言验证器结果，供调用方继续处理
     */
    @Bean
    EmbedSignedAssertionVerifierPort embedSignedJwtAssertionVerifier(
            ObjectMapper objectMapper,
            EmbedRemoteJwkSetPort remoteJwkSetPort) {
        return new NimbusEmbedSignedAssertionVerifier(objectMapper, remoteJwkSetPort);
    }

    /**
     * 处理嵌入式主体摘要，并将结果传给后续步骤。
     *
     * @param environment 环境，作为 {@code addCurrentSubjectKey} 的输入影响后续处理
     * @return 处理后的嵌入式主体摘要结果，供调用方继续处理
     */
    @Bean
    EmbedSubjectDigestPort embedSubjectDigest(Environment environment) {
        String current = environment.getProperty(
                "workflow.embed.crypto.hmac-key-version", "embed-hmac-v1");
        LinkedHashMap<String, byte[]> keys = new LinkedHashMap<>();
        addCurrentSubjectKey(environment, keys, current.trim());
        String accepted = environment.getProperty(
                "workflow.embed.crypto.accepted-hmac-key-versions", "");
        for (String version : accepted.split(",")) {
            if (!version.isBlank()) {
                addAcceptedSubjectKey(environment, keys, version.trim());
            }
        }
        return new VersionedHmacEmbedSubjectDigest(current.trim(), keys);
    }

    /**
     * 处理嵌入式上下文{@code protection}，并将结果传给后续步骤。
     *
     * @param properties 属性集合，作为 {@code requireVersion} 的输入影响后续处理
     * @param objectMapper 对象映射器，供本方法处理嵌入式上下文{@code protection}时使用
     * @param random {@code random}，供本方法处理嵌入式上下文{@code protection}时使用
     * @param environment 环境，作为 {@code contextKeys.put} 的输入影响后续处理
     * @return 处理后的嵌入式上下文{@code protection}结果，供调用方继续处理
     */
    @Bean
    EmbedContextProtectionPort embedContextProtection(
            EmbedCryptoProperties properties,
            ObjectMapper objectMapper,
            SecureRandom random,
            Environment environment) {
        String currentContextVersion = requireVersion(
                properties.getContextKeyVersion(), "contextKeyVersion");
        LinkedHashMap<String, byte[]> contextKeys = new LinkedHashMap<>();
        contextKeys.put(currentContextVersion,
                decodeContextKey(properties.getContextKeyBase64(),
                        "workflow.embed.crypto.context-key-base64"));
        String accepted = environment.getProperty(
                "workflow.embed.crypto.accepted-context-key-versions", "");
        for (String version : accepted.split(",")) {
            String normalized = version.trim();
            if (!normalized.isEmpty() && !contextKeys.containsKey(normalized)) {
                String property = "workflow.embed.crypto.context-keys." + normalized;
                contextKeys.put(normalized,
                        decodeContextKey(environment.getProperty(property), property));
            }
        }
        byte[] hmacKey = decodeHmacKey(properties.getHmacKeyBase64());
        return new AesGcmEmbedContextProtection(
                contextKeys,
                currentContextVersion,
                hmacKey,
                requireVersion(properties.getHmacKeyVersion(), "hmacKeyVersion"),
                objectMapper,
                random);
    }

    /**
     * 解码上下文键；输出作为后续校验或处理的输入。
     *
     * @param encoded 已编码，作为 {@code decodeProperty} 的输入影响后续处理
     * @param property 属性，作为 {@code decodeProperty} 的输入影响后续处理
     * @return 解码后的上下文键结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private static byte[] decodeContextKey(String encoded, String property) {
        byte[] key = decodeProperty(encoded, property);
        if (key.length != 32) {
            throw new IllegalStateException(property + " must be 32 bytes");
        }
        return key;
    }

    /**
     * 解码HMAC键；输出作为后续校验或处理的输入。
     *
     * @param encoded 已编码，作为 {@code decode} 的输入影响后续处理
     * @return 解码后的HMAC键结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private static byte[] decodeHmacKey(String encoded) {
        byte[] key = decode(encoded, "hmacKeyBase64");
        if (key.length < 32) {
            throw new IllegalStateException("workflow.embed.crypto.hmac-key-base64 must be at least 32 bytes");
        }
        return key;
    }

    /**
     * 添加当前主体键；结果供后续流程传递或持久化。
     *
     * @param environment 环境，供本方法添加当前主体键时使用
     * @param keys 键集合，作为 {@code addSubjectKey} 的输入影响后续处理
     * @param version 版本，作为 {@code addSubjectKey} 的输入影响后续处理
     */
    private static void addCurrentSubjectKey(
            Environment environment,
            Map<String, byte[]> keys,
            String version) {
        String encoded = environment.getProperty(
                "workflow.embed.crypto.hmac-key-base64");
        addSubjectKey(keys, version, encoded,
                "workflow.embed.crypto.hmac-key-base64");
    }

    /**
     * 添加{@code accepted}主体键；结果供后续流程传递或持久化。
     *
     * @param environment 环境，作为 {@code addSubjectKey} 的输入影响后续处理
     * @param keys 键集合，作为 {@code addSubjectKey} 的输入影响后续处理
     * @param version 版本，作为 {@code addSubjectKey} 的输入影响后续处理
     */
    private static void addAcceptedSubjectKey(
            Environment environment,
            Map<String, byte[]> keys,
            String version) {
        if (keys.containsKey(version)) {
            // The dedicated current-key property is authoritative when an operator accidentally
            // repeats the current version in the accepted rotation list.
            return;
        }
        String property = "workflow.embed.crypto.hmac-keys." + version;
        addSubjectKey(keys, version, environment.getProperty(property), property);
    }

    /**
     * 添加主体键；结果供后续流程传递或持久化。
     *
     * @param keys 键集合，供本方法添加主体键时使用
     * @param version 版本，作为 {@code keys.put} 的输入影响后续处理
     * @param encoded 已编码，供本方法添加主体键时使用
     * @param property 属性，作为 {@code IllegalStateException} 的输入影响后续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private static void addSubjectKey(
            Map<String, byte[]> keys,
            String version,
            String encoded,
            String property) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(property + " is required");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException(property + " is not valid Base64", error);
        }
        if (key.length < 32) {
            throw new IllegalStateException(property + " must be at least 32 bytes");
        }
        keys.put(version, key);
    }

    /**
     * 解码嵌入式{@code crypto}配置；输出作为后续校验或处理的输入。
     *
     * @param encoded 已编码，作为 {@code decodeProperty} 的输入影响后续处理
     * @param name 名称，后续用于解码嵌入式{@code crypto}配置时匹配或展示
     * @return 解码后的嵌入式{@code crypto}配置结果，供调用方继续处理
     */
    private static byte[] decode(String encoded, String name) {
        return decodeProperty(encoded, "workflow.embed.crypto." + name);
    }

    /**
     * 解码属性；输出作为后续校验或处理的输入。
     *
     * @param encoded 已编码，供本方法解码属性时使用
     * @param property 属性，作为 {@code IllegalStateException} 的输入影响后续处理
     * @return 解码后的属性结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private static byte[] decodeProperty(String encoded, String property) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(property + " is required");
        }
        try {
            return Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException(property + " is not valid Base64", error);
        }
    }

    /**
     * 校验并获取版本；不满足约束时阻止后续处理。
     *
     * @param value 待校验并获取版本的原始输入，结果供调用方继续使用
     * @param name 名称，后续用于校验并获取版本时匹配或展示
     * @return 校验并获取后的版本文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private static String requireVersion(String value, String name) {
        if (value == null || value.isBlank() || value.trim().length() > 64) {
            throw new IllegalStateException("workflow.embed.crypto." + name + " is required");
        }
        return value.trim();
    }
}
