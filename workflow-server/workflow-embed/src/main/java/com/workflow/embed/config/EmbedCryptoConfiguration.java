package com.workflow.embed.config;

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

    @Bean(name = "embedClock")
    Clock embedClock() {
        return Clock.systemUTC();
    }

    @Bean
    SecureRandom embedSecureRandom() {
        return new SecureRandom();
    }

    @Bean
    EmbedSecretGeneratorPort embedSecretGenerator(SecureRandom random) {
        return new SecureEmbedSecretGenerator(random);
    }

    @Bean
    EmbedIdGeneratorPort embedIdGenerator() {
        return new SecureEmbedIdGenerator();
    }

    @Bean
    EmbedDigestPort embedDigest() {
        return new Sha256EmbedDigest();
    }

    @Bean
    EmbedSignedAssertionVerifierPort embedSignedJwtAssertionVerifier(
            ObjectMapper objectMapper,
            EmbedRemoteJwkSetPort remoteJwkSetPort) {
        return new NimbusEmbedSignedAssertionVerifier(objectMapper, remoteJwkSetPort);
    }

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

    private static byte[] decodeContextKey(String encoded, String property) {
        byte[] key = decodeProperty(encoded, property);
        if (key.length != 32) {
            throw new IllegalStateException(property + " must be 32 bytes");
        }
        return key;
    }

    private static byte[] decodeHmacKey(String encoded) {
        byte[] key = decode(encoded, "hmacKeyBase64");
        if (key.length < 32) {
            throw new IllegalStateException("workflow.embed.crypto.hmac-key-base64 must be at least 32 bytes");
        }
        return key;
    }

    private static void addCurrentSubjectKey(
            Environment environment,
            Map<String, byte[]> keys,
            String version) {
        String encoded = environment.getProperty(
                "workflow.embed.crypto.hmac-key-base64");
        addSubjectKey(keys, version, encoded,
                "workflow.embed.crypto.hmac-key-base64");
    }

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

    private static byte[] decode(String encoded, String name) {
        return decodeProperty(encoded, "workflow.embed.crypto." + name);
    }

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

    private static String requireVersion(String value, String name) {
        if (value == null || value.isBlank() || value.trim().length() > 64) {
            throw new IllegalStateException("workflow.embed.crypto." + name + " is required");
        }
        return value.trim();
    }
}
