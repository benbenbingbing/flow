package com.workflow.embed.management.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.workflow.embed.infrastructure.crypto.HmacEmbedSubjectDigest;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ConfiguredEmbedSubjectDigesterTest {

    @Test
    void isolatesDigestByApplicationAndProviderAndAcceptsRotationWindow() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("workflow.embed.crypto.hmac-key-version", "v2")
                .withProperty("workflow.embed.crypto.hmac-key-base64", key(2))
                .withProperty("workflow.embed.crypto.accepted-hmac-key-versions", "v1")
                .withProperty("workflow.embed.crypto.hmac-keys.v1", key(1));
        ConfiguredEmbedSubjectDigester digester =
                new ConfiguredEmbedSubjectDigester(environment);

        var current = digester.current("app-1", "provider-1", "partner", "subject-1");

        assertEquals("v2", current.keyVersion());
        assertEquals(64, current.value().length());
        assertEquals(new HmacEmbedSubjectDigest(keyBytes(2), "v2")
                        .digest("app-1", "provider-1", "partner", "subject-1").value(),
                current.value());
        assertEquals(2, digester.accepted(
                "app-1", "provider-1", "partner", "subject-1").size());
        assertNotEquals(current.value(),
                digester.current("app-2", "provider-1", "partner", "subject-1").value());
        assertNotEquals(current.value(),
                digester.current("app-1", "provider-2", "partner", "subject-1").value());
    }

    @Test
    void failsClosedWhenKeyIsMissing() {
        ConfiguredEmbedSubjectDigester digester =
                new ConfiguredEmbedSubjectDigester(new MockEnvironment());

        assertThrows(com.workflow.core.error.BusinessConflictException.class,
                () -> digester.current("app", "provider", "ns", "subject"));
    }

    @Test
    void normalizesConfiguredCurrentKeyVersionLikeRuntimeAdapter() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("workflow.embed.crypto.hmac-key-version", " v2 ")
                .withProperty("workflow.embed.crypto.hmac-key-base64", key(2));
        ConfiguredEmbedSubjectDigester digester =
                new ConfiguredEmbedSubjectDigester(environment);

        assertEquals("v2", digester.current(
                "app", "provider", "ns", "subject").keyVersion());
    }

    private static String key(int seed) {
        return Base64.getEncoder().encodeToString(keyBytes(seed));
    }

    private static byte[] keyBytes(int seed) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) seed);
        return bytes;
    }
}
