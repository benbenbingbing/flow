package com.workflow.openapi.security;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

public final class OpenIntegrationKeyMaterial {

    private static final int MINIMUM_RSA_BITS = 2048;
    private static final int MAXIMUM_KEY_FILE_BYTES = 32 * 1024;
    private static final int MAXIMUM_PREVIOUS_KEYS = 3;
    private static final Pattern KEY_ID =
            Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern AUDIENCE =
            Pattern.compile("[A-Za-z0-9._:/-]{1,128}");

    private final RSAPrivateCrtKey privateKey;
    private final RSAPublicKey publicKey;
    private final Map<String, RSAPublicKey> verificationKeys;

    public OpenIntegrationKeyMaterial(
            OpenIntegrationProperties properties,
            ResourceLoader resourceLoader) {
        validateProperties(properties);
        this.privateKey = (RSAPrivateCrtKey) parseKey(
                read(properties.getPrivateKeyLocation(), resourceLoader),
                "PRIVATE KEY",
                true);
        this.publicKey = (RSAPublicKey) parseKey(
                read(properties.getPublicKeyLocation(), resourceLoader),
                "PUBLIC KEY",
                false);
        validatePublicKey(publicKey);
        if (!privateKey.getModulus().equals(publicKey.getModulus())) {
            throw new IllegalStateException(
                    "开放接口 RSA 公钥与私钥不匹配");
        }
        this.verificationKeys = loadVerificationKeys(
                properties,
                resourceLoader);
    }

    public RSAPrivateCrtKey privateKey() {
        return privateKey;
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public Map<String, RSAPublicKey> verificationKeys() {
        return verificationKeys;
    }

    private Map<String, RSAPublicKey> loadVerificationKeys(
            OpenIntegrationProperties properties,
            ResourceLoader resourceLoader) {
        Map<String, RSAPublicKey> keys = new LinkedHashMap<>();
        keys.put(properties.getKeyId(), publicKey);
        String configured = properties.getPreviousPublicKeys();
        if (configured == null || configured.isBlank()) {
            return Map.copyOf(keys);
        }
        String[] entries = configured.split(",", -1);
        if (entries.length > MAXIMUM_PREVIOUS_KEYS) {
            throw new IllegalStateException(
                    "开放接口历史验签密钥最多配置 3 把");
        }
        for (String entry : entries) {
            int separator = entry.indexOf('=');
            if (separator <= 0
                    || separator == entry.length() - 1) {
                throw new IllegalStateException(
                        "开放接口历史验签密钥格式不正确");
            }
            String keyId = entry.substring(0, separator).trim();
            String location = entry.substring(separator + 1).trim();
            if (!KEY_ID.matcher(keyId).matches()
                    || keys.containsKey(keyId)) {
                throw new IllegalStateException(
                        "开放接口历史验签 keyId 格式或唯一性不正确");
            }
            RSAPublicKey key = (RSAPublicKey) parseKey(
                    read(location, resourceLoader),
                    "PUBLIC KEY",
                    false);
            validatePublicKey(key);
            keys.put(keyId, key);
        }
        return Map.copyOf(keys);
    }

    static void validatePublicKey(RSAPublicKey key) {
        if (key.getModulus().bitLength() < MINIMUM_RSA_BITS) {
            throw new IllegalStateException(
                    "开放接口 RSA 密钥不得低于 2048 位");
        }
    }

    private void validateProperties(OpenIntegrationProperties properties) {
        if (!isValidIssuer(properties.getIssuer())) {
            throw new IllegalStateException(
                    "启用开放接口时 issuer 必须是 HTTPS 地址");
        }
        if (properties.getAudience() == null
                || !AUDIENCE.matcher(
                        properties.getAudience()).matches()) {
            throw new IllegalStateException(
                    "开放接口 audience 格式不正确");
        }
        Duration ttl = properties.getAccessTokenTtl();
        if (ttl == null
                || ttl.compareTo(Duration.ofMinutes(1)) < 0
                || ttl.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalStateException(
                    "开放接口访问令牌有效期必须在 1 到 30 分钟之间");
        }
        if (properties.getKeyId() == null
                || !KEY_ID.matcher(properties.getKeyId()).matches()) {
            throw new IllegalStateException(
                    "开放接口 keyId 格式不正确");
        }
        if (properties.getTokenClientLimitPerMinute() < 1
                || properties.getTokenClientLimitPerMinute() > 10_000
                || properties.getTokenAddressLimitPerMinute() < 1
                || properties.getTokenAddressLimitPerMinute() > 100_000) {
            throw new IllegalStateException(
                    "开放接口令牌限流配置超出允许范围");
        }
    }

    private boolean isValidIssuer(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && uri.getPort() <= 65_535;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private String read(String location, ResourceLoader resourceLoader) {
        if (location == null
                || !(location.startsWith("classpath:")
                || location.startsWith("file:"))) {
            throw new IllegalStateException(
                    "开放接口密钥必须来自 classpath: 或 file: 资源");
        }
        Resource resource = resourceLoader.getResource(location);
        try (InputStream input = resource.getInputStream()) {
            byte[] bytes = input.readNBytes(
                    MAXIMUM_KEY_FILE_BYTES + 1);
            if (bytes.length == 0
                    || bytes.length > MAXIMUM_KEY_FILE_BYTES) {
                throw new IllegalStateException(
                        "开放接口密钥文件大小不正确");
            }
            return new String(bytes, StandardCharsets.US_ASCII);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "无法读取开放接口密钥",
                    exception);
        }
    }

    private java.security.Key parseKey(
            String pem,
            String type,
            boolean privateValue) {
        String header = "-----BEGIN " + type + "-----";
        String footer = "-----END " + type + "-----";
        if (!pem.contains(header) || !pem.contains(footer)) {
            throw new IllegalStateException(
                    "开放接口密钥必须使用 PEM " + type + " 格式");
        }
        String encoded = pem
                .replace(header, "")
                .replace(footer, "")
                .replaceAll("\\s", "");
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            if (privateValue) {
                PrivateKey key = factory.generatePrivate(
                        new PKCS8EncodedKeySpec(bytes));
                if (!(key instanceof RSAPrivateCrtKey)) {
                    throw new IllegalStateException(
                            "开放接口私钥不是 RSA CRT 私钥");
                }
                return key;
            }
            PublicKey key = factory.generatePublic(
                    new X509EncodedKeySpec(bytes));
            if (!(key instanceof RSAPublicKey)) {
                throw new IllegalStateException(
                        "开放接口公钥不是 RSA 公钥");
            }
            return key;
        } catch (IllegalArgumentException
                | java.security.GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "开放接口 RSA 密钥无法解析",
                    exception);
        }
    }
}
