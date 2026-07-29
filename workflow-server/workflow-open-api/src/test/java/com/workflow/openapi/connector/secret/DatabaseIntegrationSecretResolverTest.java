package com.workflow.openapi.connector.secret;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class DatabaseIntegrationSecretResolverTest {

    @Test
    void resolvesOnlyStrictApplicationScopedReferences() {
        IntegrationSecretMapper mapper = mock(IntegrationSecretMapper.class);
        IntegrationSecretCipher cipher = cipher();
        IntegrationSecretEnvelope envelope = cipher.encrypt(
                "app-1", "api-token", 3, "secret-value");
        IntegrationSecretRecord record = record(envelope);
        when(mapper.findResolvable("app-1", "api-token"))
                .thenReturn(record);
        DatabaseIntegrationSecretResolver resolver =
                new DatabaseIntegrationSecretResolver(mapper, cipher);

        assertEquals(
                "secret-value",
                resolver.resolve(
                        "secret://integration/app-1/api-token"));
        verify(mapper).findResolvable("app-1", "api-token");
    }

    @Test
    void rejectsAmbiguousMalformedAndMissingReferences() {
        IntegrationSecretMapper mapper = mock(IntegrationSecretMapper.class);
        DatabaseIntegrationSecretResolver resolver =
                new DatabaseIntegrationSecretResolver(mapper, cipher());

        for (String value : new String[]{
                "https://integration/app-1/api-token",
                "secret://integration/app-1/api-token?x=1",
                "secret://integration/app-1/api-token/extra",
                "secret://integration/../api-token",
                "secret://integration/app-1/%2e%2e"}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> resolver.resolve(value));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(
                        "secret://integration/app-1/missing"));
    }

    private IntegrationSecretCipher cipher() {
        return new IntegrationSecretCipher(
                Base64.getEncoder().encodeToString(new byte[32]),
                "master-v1",
                true,
                new SecureRandom());
    }

    private IntegrationSecretRecord record(
            IntegrationSecretEnvelope envelope) {
        IntegrationSecretRecord value = new IntegrationSecretRecord();
        value.setApplicationId("app-1");
        value.setSecretName("api-token");
        value.setSecretVersion(3L);
        value.setKeyVersion(envelope.keyVersion());
        value.setEncryptedDataKey(envelope.encryptedDataKey());
        value.setDataKeyNonce(envelope.dataKeyNonce());
        value.setSecretCiphertext(envelope.secretCiphertext());
        value.setSecretNonce(envelope.secretNonce());
        return value;
    }
}
