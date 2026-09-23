package com.workflow.embed.infrastructure.crypto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workflow.embed.application.port.EmbedContextProtectionPort;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.ProtectedContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM context envelope with AAD and a separately keyed canonical-context HMAC. */
public final class AesGcmEmbedContextProtection implements EmbedContextProtectionPort {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BYTES = 16;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final Map<String, byte[]> encryptionKeys;
    private final String encryptionKeyVersion;
    private final byte[] hmacKey;
    private final String hmacKeyVersion;
    private final ObjectMapper canonicalMapper;
    private final SecureRandom random;

    /**
     * 初始化{@code aes}{@code gcm}嵌入式上下文{@code protection}，保存构造参数供后续方法使用。
     *
     * @param encryptionKey {@code encryption}键，后续用于授权校验、关联或幂等去重
     * @param encryptionKeyVersion {@code encryption}键版本，保存在对象中供后续校验、查询或展示
     * @param hmacKey HMAC键，后续用于授权校验、关联或幂等去重
     * @param hmacKeyVersion HMAC键版本，保存在对象中供后续校验、查询或展示
     * @param objectMapper 对象映射器，保存在对象中供后续校验、查询或展示
     * @param random {@code random}，保存在对象中供后续校验、查询或展示
     */
    public AesGcmEmbedContextProtection(
            byte[] encryptionKey,
            String encryptionKeyVersion,
            byte[] hmacKey,
            String hmacKeyVersion,
            ObjectMapper objectMapper,
            SecureRandom random) {
        this(singleKeyRing(encryptionKeyVersion, encryptionKey), encryptionKeyVersion,
                hmacKey, hmacKeyVersion, objectMapper, random);
    }

    /**
     * 构造带解密轮换窗口的 AES-GCM 适配器；只有 current key 用于新写入，其余 key 仅解密存量数据。
     *
     * @param encryptionKeys {@code encryption}键集合，保存在对象中供后续校验、查询或展示
     * @param currentEncryptionKeyVersion 当前{@code encryption}键版本，保存在对象中供后续校验、查询或展示
     * @param hmacKey HMAC键，后续用于授权校验、关联或幂等去重
     * @param hmacKeyVersion HMAC键版本，保存在对象中供后续校验、查询或展示
     * @param objectMapper 对象映射器，保存在对象中供后续校验、查询或展示
     * @param random {@code random}依赖，保存到当前对象供后续业务方法调用
     */
    public AesGcmEmbedContextProtection(
            Map<String, byte[]> encryptionKeys,
            String currentEncryptionKeyVersion,
            byte[] hmacKey,
            String hmacKeyVersion,
            ObjectMapper objectMapper,
            SecureRandom random) {
        String currentVersion = requireVersion(currentEncryptionKeyVersion);
        if (encryptionKeys == null || !encryptionKeys.containsKey(currentVersion)) {
            throw new IllegalArgumentException("Current AES-256 context key is unavailable");
        }
        LinkedHashMap<String, byte[]> safeKeys = new LinkedHashMap<>();
        encryptionKeys.forEach((version, key) -> {
            String safeVersion = requireVersion(version);
            if (key == null || key.length != 32) {
                throw new IllegalArgumentException("AES-256 context key must be 32 bytes");
            }
            safeKeys.put(safeVersion, Arrays.copyOf(key, key.length));
        });
        if (hmacKey == null || hmacKey.length < 32) {
            throw new IllegalArgumentException("Context HMAC key must be at least 32 bytes");
        }
        this.encryptionKeys = Collections.unmodifiableMap(safeKeys);
        this.encryptionKeyVersion = currentVersion;
        this.hmacKey = Arrays.copyOf(hmacKey, hmacKey.length);
        this.hmacKeyVersion = requireVersion(hmacKeyVersion);
        this.canonicalMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        this.random = random;
    }

    /**
     * 处理保护启动记录，并将结果传给后续步骤。
     *
     * @param applicationId 应用ID，后续用于处理保护启动记录时定位或关联目标
     * @param launchId 启动记录ID，后续用于处理保护启动记录时定位或关联目标
     * @param context 执行上下文，向后续保护启动记录步骤传递身份、配置或状态
     * @return 处理后的保护启动记录结果，供调用方继续处理
     */
    @Override
    public ProtectedContext protectLaunch(
            String applicationId,
            String launchId,
            Map<String, Object> context) {
        return protect("embed-launch-v1|" + applicationId + "|" + launchId, context);
    }

    /**
     * 整理解除保护启动记录数据，供调用方遍历或继续处理。
     *
     * @param applicationId 应用ID，后续用于处理解除保护启动记录时定位或关联目标
     * @param launchId 启动记录ID，后续用于处理解除保护启动记录时定位或关联目标
     * @param context 执行上下文，向后续解除保护启动记录步骤传递身份、配置或状态
     * @return 解除保护启动记录键值结果，供调用方继续处理
     */
    @Override
    public Map<String, Object> unprotectLaunch(
            String applicationId,
            String launchId,
            ProtectedContext context) {
        return unprotect(
                "embed-launch-v1|" + applicationId + "|" + launchId,
                context.ciphertext(),
                context.cipherKeyVersion());
    }

    /**
     * 处理保护会话，并将结果传给后续步骤。
     *
     * @param applicationId 应用ID，后续用于处理保护会话时定位或关联目标
     * @param sessionId 会话ID，后续用于处理保护会话时定位或关联目标
     * @param context 执行上下文，向后续保护会话步骤传递身份、配置或状态
     * @return 处理后的保护会话结果，供调用方继续处理
     */
    @Override
    public ProtectedContext protectSession(
            String applicationId,
            String sessionId,
            Map<String, Object> context) {
        return protect("embed-session-v1|" + applicationId + "|" + sessionId, context);
    }

    /**
     * 整理解除保护会话数据，供调用方遍历或继续处理。
     *
     * @param applicationId 应用ID，后续用于处理解除保护会话时定位或关联目标
     * @param sessionId 会话ID，后续用于处理解除保护会话时定位或关联目标
     * @param ciphertext {@code ciphertext}，作为 {@code unprotect} 的输入影响后续处理
     * @param keyVersion 键版本，作为 {@code unprotect} 的输入影响后续处理
     * @return 解除保护会话键值结果，供调用方继续处理
     */
    @Override
    public Map<String, Object> unprotectSession(
            String applicationId,
            String sessionId,
            String ciphertext,
            String keyVersion) {
        return unprotect(
                "embed-session-v1|" + applicationId + "|" + sessionId,
                ciphertext,
                keyVersion);
    }

    /**
     * 处理保护，并将结果传给后续步骤。
     *
     * @param aad {@code aad}，作为 {@code cipher.updateAAD} 的输入影响后续处理
     * @param context 执行上下文，向后续保护步骤传递身份、配置或状态
     * @return 处理后的保护结果，供调用方继续处理
     */
    private ProtectedContext protect(String aad, Map<String, Object> context) {
        try {
            byte[] plaintext = canonicalMapper.writeValueAsBytes(context == null ? Map.of() : context);
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(encryptionKeys.get(encryptionKeyVersion), "AES"),
                    new GCMParameterSpec(TAG_BYTES * 8, nonce));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] encryptedAndTag = cipher.doFinal(plaintext);
            int ciphertextLength = encryptedAndTag.length - TAG_BYTES;

            Map<String, String> envelope = new LinkedHashMap<>();
            envelope.put("alg", "A256GCM");
            envelope.put("kid", encryptionKeyVersion);
            envelope.put("nonce", encode(nonce));
            envelope.put("ciphertext", encode(Arrays.copyOf(encryptedAndTag, ciphertextLength)));
            envelope.put("tag", encode(Arrays.copyOfRange(
                    encryptedAndTag, ciphertextLength, encryptedAndTag.length)));
            return new ProtectedContext(
                    canonicalMapper.writeValueAsString(envelope),
                    encryptionKeyVersion,
                    hmac(aad + "|context", plaintext),
                    hmacKeyVersion);
        } catch (GeneralSecurityException | JsonProcessingException error) {
            throw unavailable(error);
        }
    }

    /**
     * 整理解除保护数据，供调用方遍历或继续处理。
     *
     * @param aad {@code aad}，作为 {@code cipher.updateAAD} 的输入影响后续处理
     * @param envelopeJson {@code envelope}JSON，作为 {@code canonicalMapper.readValue} 的输入影响后续处理
     * @param keyVersion 键版本，作为 {@code encryptionKeys.get} 的输入影响后续处理
     * @return 解除保护键值结果，供调用方继续处理
     */
    private Map<String, Object> unprotect(String aad, String envelopeJson, String keyVersion) {
        byte[] decryptionKey = encryptionKeys.get(keyVersion);
        if (decryptionKey == null) {
            throw unavailable(null);
        }
        try {
            Map<String, String> envelope = canonicalMapper.readValue(
                    envelopeJson,
                    new TypeReference<>() { });
            if (!"A256GCM".equals(envelope.get("alg"))
                    || !keyVersion.equals(envelope.get("kid"))) {
                throw unavailable(null);
            }
            byte[] nonce = decode(envelope.get("nonce"));
            byte[] ciphertext = decode(envelope.get("ciphertext"));
            byte[] tag = decode(envelope.get("tag"));
            if (nonce.length != NONCE_BYTES || tag.length != TAG_BYTES) {
                throw unavailable(null);
            }
            byte[] combined = new byte[ciphertext.length + tag.length];
            System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
            System.arraycopy(tag, 0, combined, ciphertext.length, tag.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(decryptionKey, "AES"),
                    new GCMParameterSpec(TAG_BYTES * 8, nonce));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] plaintext = cipher.doFinal(combined);
            return canonicalMapper.readValue(plaintext, MAP_TYPE);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException error) {
            throw unavailable(error);
        }
    }

    /**
     * 生成HMAC文本，供后续匹配或展示。
     *
     * @param domain {@code domain}，作为 {@code mac.update} 的输入影响后续处理
     * @param plaintext {@code plaintext}，供本方法处理HMAC时使用
     * @return 处理后的HMAC文本，供调用方比较或展示
     * @throws GeneralSecurityException 操作失败时向调用方传递
     */
    private String hmac(String domain, byte[] plaintext) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
        mac.update(domain.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) 0);
        return java.util.HexFormat.of().formatHex(mac.doFinal(plaintext));
    }

    /**
     * 编码{@code aes}{@code gcm}嵌入式上下文{@code protection}；输出作为后续校验或处理的输入。
     *
     * @param value 待编码{@code aes}{@code gcm}嵌入式上下文{@code protection}的原始输入，结果供调用方继续使用
     * @return 编码后的{@code aes}{@code gcm}嵌入式上下文{@code protection}文本，供调用方比较或展示
     */
    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    /**
     * 解码{@code aes}{@code gcm}嵌入式上下文{@code protection}；输出作为后续校验或处理的输入。
     *
     * @param value 待解码{@code aes}{@code gcm}嵌入式上下文{@code protection}的原始输入，结果供调用方继续使用
     * @return 解码后的{@code aes}{@code gcm}嵌入式上下文{@code protection}结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static byte[] decode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("missing envelope value");
        }
        return Base64.getUrlDecoder().decode(value);
    }

    /**
     * 校验并获取版本；不满足约束时阻止后续处理。
     *
     * @param value 待校验并获取版本的原始输入，结果供调用方继续使用
     * @return 校验并获取后的版本文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static String requireVersion(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 64) {
            throw new IllegalArgumentException("key version is required");
        }
        return value.trim();
    }

    /**
     * 整理{@code single}键{@code ring}数据，供调用方遍历或继续处理。
     *
     * @param version 版本，作为 {@code result.put} 的输入影响后续处理
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return {@code single}键{@code ring}键值结果，供调用方继续处理
     */
    private static Map<String, byte[]> singleKeyRing(String version, byte[] key) {
        LinkedHashMap<String, byte[]> result = new LinkedHashMap<>();
        result.put(requireVersion(version), key);
        return result;
    }

    /**
     * 构造服务不可用异常，供调用方区分失败原因。
     *
     * @param cause 原因，供本方法处理不可用时使用
     * @return 处理后的不可用结果，供调用方继续处理
     */
    private static EmbedException unavailable(Throwable cause) {
        return new EmbedException(
                503,
                EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                "Embed context protection is unavailable",
                null,
                cause);
    }
}
