package com.workflow.embed.infrastructure.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.ProtectedContext;
import com.workflow.embed.domain.SubjectDigest;
import com.workflow.embed.management.crypto.ConfiguredEmbedSubjectDigester;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class EmbedCryptographyTest {

    private static final byte[] AES_KEY = bytes(32, (byte) 7);
    private static final byte[] HMAC_KEY = bytes(32, (byte) 9);

    @Test
    void secretContainsAtLeastRequestedEntropyAndIsUrlSafe() {
        SecureEmbedSecretGenerator generator = new SecureEmbedSecretGenerator(new SecureRandom());

        String secret = generator.generate(32);

        assertEquals(32, Base64.getUrlDecoder().decode(secret).length);
        assertTrue(secret.matches("[A-Za-z0-9_-]+"));
        assertFalse(secret.contains("="));
    }

    @Test
    void subjectDigestIsStableButIsolatedByApplicationAndProvider() {
        HmacEmbedSubjectDigest digest = new HmacEmbedSubjectDigest(HMAC_KEY, "subject-v1");

        SubjectDigest first = digest.digest("app-a", "provider-a", "erp", "user-1");

        assertEquals(first, digest.digest("app-a", "provider-a", "erp", "user-1"));
        assertNotEquals(first.value(), digest.digest("app-b", "provider-a", "erp", "user-1").value());
        assertNotEquals(first.value(), digest.digest("app-a", "provider-b", "erp", "user-1").value());
        assertEquals(64, first.value().length());
    }

    @Test
    void runtimeSubjectDigestIsByteCompatibleWithManagementBindingWriter() {
        String encodedKey = Base64.getEncoder().encodeToString(HMAC_KEY);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("workflow.embed.crypto.hmac-key-version", "v2")
                .withProperty("workflow.embed.crypto.hmac-key-base64", encodedKey)
                .withProperty("workflow.embed.crypto.accepted-hmac-key-versions", "v1")
                .withProperty("workflow.embed.crypto.hmac-keys.v1", encodedKey);
        ConfiguredEmbedSubjectDigester management =
                new ConfiguredEmbedSubjectDigester(environment);
        Map<String, byte[]> keys = new LinkedHashMap<>();
        keys.put("v2", HMAC_KEY);
        keys.put("v1", HMAC_KEY);
        VersionedHmacEmbedSubjectDigest runtime =
                new VersionedHmacEmbedSubjectDigest("v2", keys);

        assertEquals(
                management.current("app-1", "provider-1", "erp-prod", "user-1").value(),
                runtime.digest("app-1", "provider-1", "erp-prod", "user-1").value());
        assertEquals("v2", runtime.accepted(
                "app-1", "provider-1", "erp-prod", "user-1").get(0).keyVersion());
        assertEquals(2, runtime.accepted(
                "app-1", "provider-1", "erp-prod", "user-1").size());
    }

    @Test
    void contextUsesAadAndRoundTripsWithoutPlaintextEnvelope() {
        AesGcmEmbedContextProtection protection = protection();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("supplierId", "S-10086");
        context.put("enabled", true);

        ProtectedContext launch = protection.protectLaunch("app-1", "launch-1", context);

        assertFalse(launch.ciphertext().contains("S-10086"));
        assertEquals(context, protection.unprotectLaunch("app-1", "launch-1", launch));
        assertThrows(EmbedException.class,
                () -> protection.unprotectLaunch("app-2", "launch-1", launch));
    }

    @Test
    void sessionReencryptionProducesDifferentEnvelopeAndRejectsWrongSessionAad() {
        AesGcmEmbedContextProtection protection = protection();
        Map<String, Object> context = Map.of("supplierId", "S-1");
        ProtectedContext first = protection.protectSession("app-1", "session-1", context);
        ProtectedContext second = protection.protectSession("app-1", "session-1", context);

        assertNotEquals(first.ciphertext(), second.ciphertext());
        assertEquals(first.digest(), second.digest());
        assertThrows(EmbedException.class, () -> protection.unprotectSession(
                "app-1", "session-2", first.ciphertext(), first.cipherKeyVersion()));
    }

    @Test
    void rotatedContextKeyDecryptsAcceptedOldEnvelopeAndWritesOnlyWithCurrentKey() {
        byte[] nextKey = bytes(32, (byte) 13);
        AesGcmEmbedContextProtection old = new AesGcmEmbedContextProtection(
                AES_KEY, "context-v1", HMAC_KEY, "hmac-v1",
                new ObjectMapper(), new SecureRandom());
        ProtectedContext oldEnvelope = old.protectLaunch(
                "app-1", "launch-1", Map.of("supplierId", "S-1"));
        LinkedHashMap<String, byte[]> rotationRing = new LinkedHashMap<>();
        rotationRing.put("context-v2", nextKey);
        rotationRing.put("context-v1", AES_KEY);
        AesGcmEmbedContextProtection rotated = new AesGcmEmbedContextProtection(
                rotationRing, "context-v2", HMAC_KEY, "hmac-v1",
                new ObjectMapper(), new SecureRandom());

        assertEquals(Map.of("supplierId", "S-1"),
                rotated.unprotectLaunch("app-1", "launch-1", oldEnvelope));
        assertEquals("context-v2", rotated.protectLaunch(
                "app-1", "launch-2", Map.of()).cipherKeyVersion());
    }

    private static AesGcmEmbedContextProtection protection() {
        return new AesGcmEmbedContextProtection(
                AES_KEY, "context-v1", HMAC_KEY, "hmac-v1",
                new ObjectMapper(), new SecureRandom());
    }

    private static byte[] bytes(int length, byte value) {
        byte[] bytes = new byte[length];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }
}
