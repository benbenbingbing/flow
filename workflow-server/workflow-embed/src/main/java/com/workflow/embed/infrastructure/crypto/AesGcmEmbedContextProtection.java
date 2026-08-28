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

    @Override
    public ProtectedContext protectLaunch(
            String applicationId,
            String launchId,
            Map<String, Object> context) {
        return protect("embed-launch-v1|" + applicationId + "|" + launchId, context);
    }

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

    @Override
    public ProtectedContext protectSession(
            String applicationId,
            String sessionId,
            Map<String, Object> context) {
        return protect("embed-session-v1|" + applicationId + "|" + sessionId, context);
    }

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

    private String hmac(String domain, byte[] plaintext) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
        mac.update(domain.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) 0);
        return java.util.HexFormat.of().formatHex(mac.doFinal(plaintext));
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] decode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("missing envelope value");
        }
        return Base64.getUrlDecoder().decode(value);
    }

    private static String requireVersion(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 64) {
            throw new IllegalArgumentException("key version is required");
        }
        return value.trim();
    }

    private static Map<String, byte[]> singleKeyRing(String version, byte[] key) {
        LinkedHashMap<String, byte[]> result = new LinkedHashMap<>();
        result.put(requireVersion(version), key);
        return result;
    }

    private static EmbedException unavailable(Throwable cause) {
        return new EmbedException(
                503,
                EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                "Embed context protection is unavailable",
                null,
                cause);
    }
}
