package com.workflow.openapi.connector.secret;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class IntegrationSecretCipher {

    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String AAD_PREFIX = "flow-integration-secret-v1";

    private final Map<String, byte[]> masterKeys;
    private final String keyVersion;
    private final boolean connectorEnabled;
    private final SecureRandom secureRandom;

    @Autowired
    IntegrationSecretCipher(
            @Value("${workflow.integration.connector.http.master-key:}")
            String encodedMasterKey,
            @Value("${workflow.integration.connector.http.master-key-version:}")
            String keyVersion,
            @Value("${workflow.integration.connector.http.previous-master-keys:}")
            String previousMasterKeys,
            @Value("${workflow.integration.connector.http.enabled:false}")
            boolean connectorEnabled) {
        this(
                encodedMasterKey,
                keyVersion,
                previousMasterKeys,
                connectorEnabled,
                new SecureRandom());
    }

    IntegrationSecretCipher(
            String encodedMasterKey,
            String keyVersion,
            boolean connectorEnabled,
            SecureRandom secureRandom) {
        this(
                encodedMasterKey,
                keyVersion,
                "",
                connectorEnabled,
                secureRandom);
    }

    IntegrationSecretCipher(
            String encodedMasterKey,
            String keyVersion,
            String previousMasterKeys,
            boolean connectorEnabled,
            SecureRandom secureRandom) {
        this.keyVersion = keyVersion == null ? "" : keyVersion.trim();
        this.masterKeys = readMasterKeys(
                this.keyVersion,
                encodedMasterKey,
                previousMasterKeys);
        this.connectorEnabled = connectorEnabled;
        this.secureRandom = secureRandom;
    }

    @PostConstruct
    void validateAtStartup() {
        if (connectorEnabled) {
            requireMasterKey();
        }
    }

    IntegrationSecretEnvelope encrypt(
            String applicationId,
            String secretName,
            long secretVersion,
            String plaintext) {
        requireMasterKey();
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("集成 Secret 不能为空");
        }
        byte[] dataKeyBytes = randomBytes(KEY_BYTES);
        byte[] plaintextBytes =
                plaintext.getBytes(StandardCharsets.UTF_8);
        SecretKey dataKey = new SecretKeySpec(dataKeyBytes, "AES");
        byte[] dataKeyNonce = randomBytes(NONCE_BYTES);
        byte[] secretNonce = randomBytes(NONCE_BYTES);
        byte[] context = context(applicationId, secretName, secretVersion);
        try {
            byte[] encryptedDataKey = encrypt(
                    new SecretKeySpec(
                            masterKey(keyVersion),
                            "AES"),
                    dataKeyNonce,
                    context,
                    dataKeyBytes);
            byte[] ciphertext = encrypt(
                    dataKey,
                    secretNonce,
                    context,
                    plaintextBytes);
            return new IntegrationSecretEnvelope(
                    keyVersion,
                    encode(encryptedDataKey),
                    encode(dataKeyNonce),
                    encode(ciphertext),
                    encode(secretNonce));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("集成 Secret 加密失败", exception);
        } finally {
            java.util.Arrays.fill(dataKeyBytes, (byte) 0);
            java.util.Arrays.fill(plaintextBytes, (byte) 0);
        }
    }

    String decrypt(
            String applicationId,
            String secretName,
            long secretVersion,
            IntegrationSecretEnvelope envelope) {
        requireMasterKey();
        if (envelope == null
                || !masterKeys.containsKey(envelope.keyVersion())) {
            throw new IllegalStateException("集成 Secret 主密钥版本不可用");
        }
        byte[] dataKeyBytes = null;
        byte[] context = context(applicationId, secretName, secretVersion);
        try {
            dataKeyBytes = decrypt(
                    new SecretKeySpec(
                            masterKey(envelope.keyVersion()),
                            "AES"),
                    decodeNonce(envelope.dataKeyNonce()),
                    context,
                    decode(envelope.encryptedDataKey()));
            if (dataKeyBytes.length != KEY_BYTES) {
                throw new GeneralSecurityException("invalid data key");
            }
            byte[] plaintext = decrypt(
                    new SecretKeySpec(dataKeyBytes, "AES"),
                    decodeNonce(envelope.secretNonce()),
                    context,
                    decode(envelope.secretCiphertext()));
            String value = new String(
                    plaintext,
                    StandardCharsets.UTF_8);
            java.util.Arrays.fill(plaintext, (byte) 0);
            return value;
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("集成 Secret 解密失败", exception);
        } finally {
            if (dataKeyBytes != null) {
                java.util.Arrays.fill(dataKeyBytes, (byte) 0);
            }
        }
    }

    private byte[] context(
            String applicationId,
            String secretName,
            long secretVersion) {
        if (applicationId == null || applicationId.isBlank()
                || secretName == null || secretName.isBlank()
                || secretVersion <= 0) {
            throw new IllegalArgumentException("集成 Secret 加密上下文无效");
        }
        return (AAD_PREFIX + "\n"
                + applicationId + "\n"
                + secretName + "\n"
                + secretVersion).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] encrypt(
            SecretKey key,
            byte[] nonce,
            byte[] aad,
            byte[] plaintext) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.ENCRYPT_MODE,
                key,
                new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(plaintext);
    }

    private byte[] decrypt(
            SecretKey key,
            byte[] nonce,
            byte[] aad,
            byte[] ciphertext) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(ciphertext);
    }

    private byte[] randomBytes(int length) {
        byte[] value = new byte[length];
        secureRandom.nextBytes(value);
        return value;
    }

    private void requireMasterKey() {
        if (keyVersion.isBlank()
                || !masterKeys.containsKey(keyVersion)) {
            throw new IllegalStateException(
                    "启用 HTTP Connector 时必须配置 32 字节 Base64 主密钥及版本");
        }
    }

    @PreDestroy
    void clearMasterKeys() {
        masterKeys.values().forEach(
                value -> java.util.Arrays.fill(value, (byte) 0));
    }

    private byte[] masterKey(String version) {
        byte[] key = masterKeys.get(version);
        if (key == null || key.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "集成 Secret 主密钥版本不可用");
        }
        return key;
    }

    private byte[] decodeNonce(String value) {
        byte[] nonce = decode(value);
        if (nonce.length != NONCE_BYTES) {
            throw new IllegalArgumentException("invalid nonce");
        }
        return nonce;
    }

    private static byte[] decodeMasterKey(String value) {
        if (value == null || value.isBlank()) {
            return new byte[0];
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value.trim());
            return decoded.length == KEY_BYTES ? decoded : new byte[0];
        } catch (IllegalArgumentException exception) {
            return new byte[0];
        }
    }

    private static Map<String, byte[]> readMasterKeys(
            String currentVersion,
            String currentValue,
            String previousValues) {
        Map<String, byte[]> values = new LinkedHashMap<>();
        if (!currentVersion.isBlank()) {
            if (!currentVersion.matches("[A-Za-z0-9._-]{1,64}")) {
                throw new IllegalArgumentException(
                        "HTTP Connector 主密钥版本无效");
            }
            byte[] current = decodeMasterKey(currentValue);
            if (current.length == KEY_BYTES) {
                values.put(currentVersion, current);
            }
        }
        if (previousValues != null && !previousValues.isBlank()) {
            for (String item : previousValues.split(",")) {
                int separator = item.indexOf(':');
                if (separator <= 0) {
                    throw invalidPreviousKeyConfiguration();
                }
                String version = item.substring(0, separator).trim();
                byte[] key = decodeMasterKey(
                        item.substring(separator + 1).trim());
                if (!version.matches("[A-Za-z0-9._-]{1,64}")
                        || key.length != KEY_BYTES
                        || version.equals(currentVersion)
                        || values.putIfAbsent(version, key) != null) {
                    throw invalidPreviousKeyConfiguration();
                }
            }
        }
        return Map.copyOf(values);
    }

    private static IllegalArgumentException
            invalidPreviousKeyConfiguration() {
        return new IllegalArgumentException(
                "HTTP Connector 历史主密钥配置无效");
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }
}
