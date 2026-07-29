package com.workflow.openapi.webhook.security;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts Webhook signing secrets with an environment-provided AES-256 key.
 */
@Component
public class WebhookSecretCipher {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String PREFIX = "v1";

    private final byte[] masterKey;
    private final boolean webhookEnabled;
    private final SecureRandom secureRandom;

    public WebhookSecretCipher(
            @Value("${workflow.open-api.webhook.master-key:}")
            String encodedMasterKey,
            @Value("${workflow.open-api.webhook.enabled:false}")
            boolean webhookEnabled) {
        this(
                encodedMasterKey,
                webhookEnabled,
                new SecureRandom());
    }

    WebhookSecretCipher(
            String encodedMasterKey,
            boolean webhookEnabled,
            SecureRandom secureRandom) {
        this.masterKey = decodeKey(encodedMasterKey);
        this.webhookEnabled = webhookEnabled;
        this.secureRandom = secureRandom;
    }

    @PostConstruct
    void validateAtStartup() {
        if (webhookEnabled) {
            requireKey();
        }
    }

    public String generateSecret() {
        byte[] value = new byte[32];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value);
    }

    public String encrypt(String plaintext) {
        requireKey();
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException(
                    "Webhook 签名密钥不能为空");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(
                    "AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(masterKey, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX
                    + "."
                    + encode(nonce)
                    + "."
                    + encode(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Webhook 签名密钥加密失败",
                    exception);
        }
    }

    public String decrypt(String document) {
        requireKey();
        String[] parts = document == null
                ? new String[0]
                : document.split("\\.", -1);
        if (parts.length != 3
                || !PREFIX.equals(parts[0])) {
            throw new IllegalStateException(
                    "Webhook 签名密钥密文格式无效");
        }
        try {
            byte[] nonce = decode(parts[1]);
            if (nonce.length != NONCE_BYTES) {
                throw new IllegalArgumentException(
                        "nonce length");
            }
            Cipher cipher = Cipher.getInstance(
                    "AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(masterKey, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            return new String(
                    cipher.doFinal(decode(parts[2])),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException
                 | IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Webhook 签名密钥解密失败",
                    exception);
        }
    }

    private void requireKey() {
        if (masterKey.length != 32) {
            throw new IllegalStateException(
                    "启用 Webhook 时必须配置 32 字节 Base64 主密钥");
        }
    }

    private static byte[] decodeKey(String value) {
        if (value == null || value.isBlank()) {
            return new byte[0];
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(
                    value.trim());
            return decoded.length == 32
                    ? decoded
                    : new byte[0];
        } catch (IllegalArgumentException exception) {
            return new byte[0];
        }
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value);
    }

    private byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }
}
