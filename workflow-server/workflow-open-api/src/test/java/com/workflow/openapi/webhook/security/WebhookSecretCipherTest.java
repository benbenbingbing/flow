package com.workflow.openapi.webhook.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class WebhookSecretCipherTest {

    private static final String MASTER_KEY =
            Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptsWithFreshAuthenticatedNoncesAndRoundTrips() {
        WebhookSecretCipher cipher = new WebhookSecretCipher(
                MASTER_KEY,
                true,
                new SecureRandom());

        String first = cipher.encrypt("signing-secret");
        String second = cipher.encrypt("signing-secret");

        assertNotEquals(first, second);
        assertFalse(first.contains("signing-secret"));
        assertEquals("signing-secret", cipher.decrypt(first));
        assertEquals("signing-secret", cipher.decrypt(second));
    }

    @Test
    void rejectsTamperedCiphertext() {
        WebhookSecretCipher cipher = new WebhookSecretCipher(
                MASTER_KEY,
                true,
                new SecureRandom());
        String encrypted = cipher.encrypt("signing-secret");
        String tampered = encrypted.substring(
                0,
                encrypted.length() - 1) + "A";

        assertThrows(
                IllegalStateException.class,
                () -> cipher.decrypt(tampered));
    }

    @Test
    void enabledWebhookRequiresAValidAes256Key() {
        WebhookSecretCipher missing = new WebhookSecretCipher(
                "",
                true,
                new SecureRandom());
        WebhookSecretCipher shortKey = new WebhookSecretCipher(
                Base64.getEncoder()
                        .encodeToString(new byte[16]),
                true,
                new SecureRandom());

        assertThrows(
                IllegalStateException.class,
                missing::validateAtStartup);
        assertThrows(
                IllegalStateException.class,
                shortKey::validateAtStartup);
    }
}
