package com.workflow.openapi.connector.secret;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class IntegrationSecretCipherTest {

    private static final String MASTER_KEY =
            Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void envelopeEncryptionUsesFreshKeysAndAuthenticatedContext() {
        IntegrationSecretCipher cipher = cipher();
        IntegrationSecretEnvelope first =
                cipher.encrypt("app-1", "api-token", 1, "sensitive-value");
        IntegrationSecretEnvelope second =
                cipher.encrypt("app-1", "api-token", 1, "sensitive-value");

        assertNotEquals(first.encryptedDataKey(), second.encryptedDataKey());
        assertNotEquals(first.secretCiphertext(), second.secretCiphertext());
        assertFalse(first.toString().contains("sensitive-value"));
        assertEquals(
                "sensitive-value",
                cipher.decrypt("app-1", "api-token", 1, first));
        assertThrows(
                IllegalStateException.class,
                () -> cipher.decrypt("app-2", "api-token", 1, first));
        assertThrows(
                IllegalStateException.class,
                () -> cipher.decrypt("app-1", "other-token", 1, first));
    }

    @Test
    void rejectsTamperedCiphertextAndUnavailableKeyVersions() {
        IntegrationSecretCipher cipher = cipher();
        IntegrationSecretEnvelope encrypted =
                cipher.encrypt("app-1", "api-token", 1, "sensitive-value");
        IntegrationSecretEnvelope tampered = new IntegrationSecretEnvelope(
                encrypted.keyVersion(),
                encrypted.encryptedDataKey(),
                encrypted.dataKeyNonce(),
                encrypted.secretCiphertext() + "A",
                encrypted.secretNonce());

        assertThrows(
                IllegalStateException.class,
                () -> cipher.decrypt("app-1", "api-token", 1, tampered));
        IntegrationSecretCipher rotated = new IntegrationSecretCipher(
                MASTER_KEY,
                "master-v2",
                true,
                new SecureRandom());
        assertThrows(
                IllegalStateException.class,
                () -> rotated.decrypt("app-1", "api-token", 1, encrypted));
    }

    @Test
    void enabledConnectorRequiresAes256KeyAndVersion() {
        IntegrationSecretCipher missing = new IntegrationSecretCipher(
                "",
                "",
                true,
                new SecureRandom());
        assertThrows(
                IllegalStateException.class,
                missing::validateAtStartup);
    }

    @Test
    void decryptsPreviousKeyDuringRollingMasterKeyRotation() {
        IntegrationSecretCipher previous = cipher();
        IntegrationSecretEnvelope encrypted = previous.encrypt(
                "app-1",
                "api-token",
                1,
                "sensitive-value");
        String nextKey = Base64.getEncoder()
                .encodeToString(new byte[]{
                        1, 1, 1, 1, 1, 1, 1, 1,
                        1, 1, 1, 1, 1, 1, 1, 1,
                        1, 1, 1, 1, 1, 1, 1, 1,
                        1, 1, 1, 1, 1, 1, 1, 1});
        IntegrationSecretCipher rotated =
                new IntegrationSecretCipher(
                        nextKey,
                        "master-v2",
                        "master-v1:" + MASTER_KEY,
                        true,
                        new SecureRandom());

        assertEquals(
                "sensitive-value",
                rotated.decrypt(
                        "app-1",
                        "api-token",
                        1,
                        encrypted));
        assertEquals(
                "master-v2",
                rotated.encrypt(
                        "app-1",
                        "api-token",
                        2,
                        "next-value").keyVersion());
    }

    @Test
    void rejectsMalformedHistoricalKeyConfigurationAtStartup() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IntegrationSecretCipher(
                        MASTER_KEY,
                        "master-v2",
                        "missing-separator",
                        true,
                        new SecureRandom()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IntegrationSecretCipher(
                        MASTER_KEY,
                        "master-v2",
                        "master-v2:" + MASTER_KEY,
                        true,
                        new SecureRandom()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IntegrationSecretCipher(
                        MASTER_KEY,
                        "invalid version",
                        "",
                        true,
                        new SecureRandom()));
    }

    private IntegrationSecretCipher cipher() {
        return new IntegrationSecretCipher(
                MASTER_KEY,
                "master-v1",
                true,
                new SecureRandom());
    }
}
