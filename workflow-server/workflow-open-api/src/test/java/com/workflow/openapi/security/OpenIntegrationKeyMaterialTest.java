package com.workflow.openapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

class OpenIntegrationKeyMaterialTest {

    @TempDir
    private Path directory;

    @Test
    void loadsCurrentSigningPairAndBoundedPreviousVerificationKey()
            throws Exception {
        KeyPair current = keyPair(2048);
        KeyPair previous = keyPair(2048);
        OpenIntegrationProperties properties =
                properties(current, "current-key");
        Path previousPublic = writePublicKey(
                "previous-public.pem",
                previous);
        properties.setPreviousPublicKeys(
                "previous-key=" + previousPublic.toUri());

        OpenIntegrationKeyMaterial material =
                new OpenIntegrationKeyMaterial(
                        properties,
                        new DefaultResourceLoader());

        assertEquals(
                current.getPublic(),
                material.publicKey());
        assertEquals(
                java.util.Set.of(
                        "current-key",
                        "previous-key"),
                material.verificationKeys().keySet());
    }

    @Test
    void rejectsMismatchedAndWeakRsaKeys() throws Exception {
        KeyPair first = keyPair(2048);
        KeyPair second = keyPair(2048);
        OpenIntegrationProperties mismatched =
                properties(first, "current-key");
        mismatched.setPublicKeyLocation(
                writePublicKey(
                        "mismatched-public.pem",
                        second)
                        .toUri()
                        .toString());

        assertThrows(
                IllegalStateException.class,
                () -> new OpenIntegrationKeyMaterial(
                        mismatched,
                        new DefaultResourceLoader()));

        OpenIntegrationProperties weak =
                properties(keyPair(1024), "weak-key");
        assertThrows(
                IllegalStateException.class,
                () -> new OpenIntegrationKeyMaterial(
                        weak,
                        new DefaultResourceLoader()));
    }

    @Test
    void rejectsUnsafeLocationsInvalidTtlAndUnboundedHistory()
            throws Exception {
        OpenIntegrationProperties unsafe =
                properties(keyPair(2048), "current-key");
        unsafe.setPrivateKeyLocation("/tmp/private.pem");
        assertThrows(
                IllegalStateException.class,
                () -> new OpenIntegrationKeyMaterial(
                        unsafe,
                        new DefaultResourceLoader()));

        OpenIntegrationProperties invalidTtl =
                properties(keyPair(2048), "current-key");
        invalidTtl.setAccessTokenTtl(Duration.ofMinutes(31));
        assertThrows(
                IllegalStateException.class,
                () -> new OpenIntegrationKeyMaterial(
                        invalidTtl,
                        new DefaultResourceLoader()));

        OpenIntegrationProperties history =
                properties(keyPair(2048), "current-key");
        Path publicKey = writePublicKey(
                "history-public.pem",
                keyPair(2048));
        history.setPreviousPublicKeys(
                "one=" + publicKey.toUri()
                        + ",two=" + publicKey.toUri()
                        + ",three=" + publicKey.toUri()
                        + ",four=" + publicKey.toUri());
        assertThrows(
                IllegalStateException.class,
                () -> new OpenIntegrationKeyMaterial(
                        history,
                        new DefaultResourceLoader()));

        OpenIntegrationProperties invalidIssuer =
                properties(keyPair(2048), "issuer-key");
        invalidIssuer.setIssuer(
                "https://user@example.test?unexpected=true");
        assertThrows(
                IllegalStateException.class,
                () -> new OpenIntegrationKeyMaterial(
                        invalidIssuer,
                        new DefaultResourceLoader()));

        OpenIntegrationProperties invalidAudience =
                properties(keyPair(2048), "audience-key");
        invalidAudience.setAudience("flow open api");
        assertThrows(
                IllegalStateException.class,
                () -> new OpenIntegrationKeyMaterial(
                        invalidAudience,
                        new DefaultResourceLoader()));

        OpenIntegrationProperties oversized =
                properties(keyPair(2048), "oversized-key");
        Path oversizedPrivate = directory.resolve(
                "oversized-private.pem");
        Files.writeString(
                oversizedPrivate,
                "x".repeat(32 * 1024 + 1),
                StandardCharsets.US_ASCII);
        oversized.setPrivateKeyLocation(
                oversizedPrivate.toUri().toString());
        assertThrows(
                IllegalStateException.class,
                () -> new OpenIntegrationKeyMaterial(
                        oversized,
                        new DefaultResourceLoader()));
    }

    private OpenIntegrationProperties properties(
            KeyPair pair,
            String keyId) throws Exception {
        Path privateKey = directory.resolve(
                keyId + "-private.pem");
        Files.writeString(
                privateKey,
                pem(
                        "PRIVATE KEY",
                        ((RSAPrivateKey) pair.getPrivate())
                                .getEncoded()),
                StandardCharsets.US_ASCII);
        Path publicKey = writePublicKey(
                keyId + "-public.pem",
                pair);
        OpenIntegrationProperties properties =
                new OpenIntegrationProperties();
        properties.setEnabled(true);
        properties.setIssuer("https://flow.test");
        properties.setAudience("flow-open-api");
        properties.setAccessTokenTtl(Duration.ofMinutes(10));
        properties.setKeyId(keyId);
        properties.setPrivateKeyLocation(
                privateKey.toUri().toString());
        properties.setPublicKeyLocation(
                publicKey.toUri().toString());
        return properties;
    }

    private Path writePublicKey(
            String fileName,
            KeyPair pair) throws Exception {
        Path file = directory.resolve(fileName);
        Files.writeString(
                file,
                pem(
                        "PUBLIC KEY",
                        ((RSAPublicKey) pair.getPublic())
                                .getEncoded()),
                StandardCharsets.US_ASCII);
        return file;
    }

    private KeyPair keyPair(int bits) throws Exception {
        KeyPairGenerator generator =
                KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    private String pem(String type, byte[] value) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(
                                64,
                                new byte[]{'\n'})
                .encodeToString(value)
                + "\n-----END " + type + "-----\n";
    }
}
