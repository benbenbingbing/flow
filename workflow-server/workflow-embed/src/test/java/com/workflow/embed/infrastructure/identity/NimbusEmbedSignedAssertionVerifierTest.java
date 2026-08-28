package com.workflow.embed.infrastructure.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedIdentityProviderSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NimbusEmbedSignedAssertionVerifierTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private NimbusEmbedSignedAssertionVerifier verifier;
    private RSAKey signingKey;
    private Instant now;

    @BeforeEach
    void setUp() throws Exception {
        verifier = new NimbusEmbedSignedAssertionVerifier(objectMapper);
        signingKey = new RSAKeyGenerator(2048)
                .keyID("partner-key-1")
                .algorithm(JWSAlgorithm.RS256)
                .generate();
        now = Instant.parse("2026-08-27T04:00:00Z");
    }

    @Test
    void verifiesPublishedIssuerAudienceKeyAndShortLifetime() throws Exception {
        var result = verifier.verify(provider(), assertion(
                "https://id.partner.example",
                List.of("flow-embed-launch"),
                now.minusSeconds(5),
                now.plusSeconds(55),
                "subject-001",
                "jti-001"), now);

        assertEquals("partner-users", result.namespace());
        assertEquals("subject-001", result.externalSubject());
        assertEquals("jti-001", result.jti());
        assertEquals(now.plusSeconds(55), result.replayExpiresAt());
    }

    @Test
    void rejectsWrongAudienceAndIssuerWithoutReturningUnverifiedClaims() throws Exception {
        assertInvalid(assertion(
                "https://id.partner.example",
                List.of("another-api"),
                now,
                now.plusSeconds(60),
                "subject-001",
                "jti-001"));
        assertInvalid(assertion(
                "https://evil.example",
                List.of("flow-embed-launch"),
                now,
                now.plusSeconds(60),
                "subject-001",
                "jti-002"));
    }

    @Test
    void rejectsOrdinaryTokenEvenWhenProviderKeepsMigrationAudience() throws Exception {
        EmbedIdentityProviderSnapshot provider = providerWithAudiences(
                "[\"flow-embed-launch\",\"ordinary-api\"]");
        String ordinaryToken = assertion(
                "https://id.partner.example",
                List.of("ordinary-api"),
                now,
                now.plusSeconds(60),
                "subject-001",
                "jti-ordinary");

        EmbedException failure = assertThrows(
                EmbedException.class,
                () -> verifier.verify(provider, ordinaryToken, now));

        assertEquals(403, failure.status());
    }

    @Test
    void rejectsPersistedAudienceBeyondTheManagementContract() throws Exception {
        EmbedIdentityProviderSnapshot historical = providerWithAudiences(
                objectMapper.writeValueAsString(
                        List.of("flow-embed-launch", "a".repeat(257))));
        String valid = assertion(
                "https://id.partner.example",
                List.of("flow-embed-launch"),
                now,
                now.plusSeconds(60),
                "subject-001",
                "jti-long-audience");

        EmbedException failure = assertThrows(
                EmbedException.class,
                () -> verifier.verify(historical, valid, now));

        assertEquals(403, failure.status());
    }

    @Test
    void rejectsEdDsaBeforeLoadingAnyRemoteKeys() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        NimbusEmbedSignedAssertionVerifier remoteVerifier =
                new NimbusEmbedSignedAssertionVerifier(objectMapper, (provider, forceRefresh) -> {
                    loads.incrementAndGet();
                    return "{\"keys\":[]}";
                });
        EmbedIdentityProviderSnapshot historical = remoteProvider("[\"EdDSA\"]");

        EmbedException failure = assertThrows(
                EmbedException.class,
                () -> remoteVerifier.verify(
                        historical, compactAssertion("EdDSA", "okp-key"), now));

        assertEquals(403, failure.status());
        assertEquals(0, loads.get());
    }

    @Test
    void rejectsMixedHistoricalAlgorithmsAndStaticOkpKeys() throws Exception {
        String valid = assertion(
                "https://id.partner.example",
                List.of("flow-embed-launch"),
                now,
                now.plusSeconds(60),
                "subject-001",
                "jti-mixed-history");
        EmbedIdentityProviderSnapshot mixedAlgorithms = new EmbedIdentityProviderSnapshot(
                "provider-1", "SIGNED_JWT", "ACTIVE",
                "https://id.partner.example", "partner-users",
                "[\"flow-embed-launch\"]", "[\"RS256\",\"EdDSA\"]",
                "STATIC_JWK_SET", new JWKSet(signingKey.toPublicJWK()).toString(),
                null, 0, 60, 1, 1);
        assertThrows(EmbedException.class,
                () -> verifier.verify(mixedAlgorithms, valid, now));

        String mixedKeys = "{\"keys\":["
                + signingKey.toPublicJWK().toJSONString()
                + ","
                + "{\"kty\":\"OKP\",\"kid\":\"okp\",\"use\":\"sig\","
                + "\"crv\":\"Ed25519\","
                + "\"x\":\"11qYAYKxCrfVS_7TyWfy3G4EMRdcQHoTSgLZZKhFSqA\"}]}";
        EmbedIdentityProviderSnapshot mixedStaticKeys = providerWithJwks(mixedKeys);
        assertThrows(EmbedException.class,
                () -> verifier.verify(mixedStaticKeys, valid, now));
    }

    @Test
    void rejectsExpiredOrOverlongAssertions() throws Exception {
        assertInvalid(assertion(
                "https://id.partner.example",
                List.of("flow-embed-launch"),
                now.minusSeconds(120),
                now.minusSeconds(60),
                "subject-001",
                "jti-001"));
        assertInvalid(assertion(
                "https://id.partner.example",
                List.of("flow-embed-launch"),
                now,
                now.plusSeconds(61),
                "subject-001",
                "jti-002"));
    }

    @Test
    void rejectsRemoteJwksWhenNoHardenedFetcherIsConfigured() throws Exception {
        EmbedIdentityProviderSnapshot remote = remoteProvider();

        assertThrows(EmbedException.class, () -> verifier.verify(
                remote,
                assertion("https://id.partner.example", List.of("flow-embed-launch"),
                        now, now.plusSeconds(60), "subject-001", "jti-001"),
                now));
    }

    @Test
    void verifiesRemoteJwksAndForceRefreshesOnlyForUnknownKid() throws Exception {
        RSAKey staleKey = new RSAKeyGenerator(2048)
                .keyID("stale-key")
                .algorithm(JWSAlgorithm.RS256)
                .generate();
        AtomicInteger ordinaryLoads = new AtomicInteger();
        AtomicInteger forcedLoads = new AtomicInteger();
        NimbusEmbedSignedAssertionVerifier remoteVerifier =
                new NimbusEmbedSignedAssertionVerifier(objectMapper, (provider, forceRefresh) -> {
                    if (forceRefresh) {
                        forcedLoads.incrementAndGet();
                        return new JWKSet(signingKey.toPublicJWK()).toString();
                    }
                    ordinaryLoads.incrementAndGet();
                    return new JWKSet(staleKey.toPublicJWK()).toString();
                });

        var result = remoteVerifier.verify(
                remoteProvider(),
                assertion("https://id.partner.example", List.of("flow-embed-launch"),
                        now, now.plusSeconds(60), "subject-001", "jti-remote"),
                now);

        assertEquals("subject-001", result.externalSubject());
        assertEquals(1, ordinaryLoads.get());
        assertEquals(1, forcedLoads.get());
    }

    @Test
    void rejectsUnknownKidAndPrivateJwkMaterial() throws Exception {
        String valid = assertion(
                "https://id.partner.example",
                List.of("flow-embed-launch"),
                now,
                now.plusSeconds(60),
                "subject-001",
                "jti-001");
        EmbedIdentityProviderSnapshot wrongKey = providerWithJwks(
                new JWKSet(new RSAKeyGenerator(2048)
                        .keyID("other-key")
                        .algorithm(JWSAlgorithm.RS256)
                        .generate().toPublicJWK()).toString());
        assertThrows(EmbedException.class, () -> verifier.verify(wrongKey, valid, now));

        EmbedIdentityProviderSnapshot privateKey = providerWithJwks(
                objectMapper.writeValueAsString(
                        new JWKSet(signingKey).toJSONObject(false)));
        assertThrows(EmbedException.class, () -> verifier.verify(privateKey, valid, now));
    }

    @Test
    void rejectsRsaKeysBelowTheEmbedSecurityMinimum() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        var pair = generator.generateKeyPair();
        RSAKey weakKey = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID("weak-partner-key")
                .algorithm(JWSAlgorithm.RS256)
                .build();
        String weakAssertion = weakRsaAssertion(weakKey);
        EmbedIdentityProviderSnapshot weakProvider = providerWithJwks(
                new JWKSet(weakKey.toPublicJWK()).toString());

        EmbedException failure = assertThrows(
                EmbedException.class,
                () -> verifier.verify(weakProvider, weakAssertion, now));

        assertEquals(403, failure.status());
    }

    /** Nimbus 的签名器主动拒绝短 RSA；这里用 JCA 构造旧/外部系统仍可能发来的弱 JWT。 */
    private String weakRsaAssertion(RSAKey weakKey) throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString(objectMapper.writeValueAsBytes(Map.of(
                "alg", "RS256", "typ", "JWT", "kid", weakKey.getKeyID())));
        String payload = encoder.encodeToString(objectMapper.writeValueAsBytes(Map.of(
                "iss", "https://id.partner.example",
                "aud", "flow-embed-launch",
                "sub", "subject-001",
                "jti", "jti-weak-key",
                "iat", now.getEpochSecond(),
                "exp", now.plusSeconds(60).getEpochSecond())));
        String signingInput = header + "." + payload;
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(weakKey.toRSAPrivateKey());
        signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + encoder.encodeToString(signer.sign());
    }

    private void assertInvalid(String assertion) {
        EmbedException failure = assertThrows(
                EmbedException.class,
                () -> verifier.verify(provider(), assertion, now));
        assertEquals(403, failure.status());
    }

    private EmbedIdentityProviderSnapshot provider() {
        return providerWithJwks(new JWKSet(signingKey.toPublicJWK()).toString());
    }

    private EmbedIdentityProviderSnapshot providerWithAudiences(String audiencesJson) {
        return new EmbedIdentityProviderSnapshot(
                "provider-1", "SIGNED_JWT", "ACTIVE",
                "https://id.partner.example", "partner-users",
                audiencesJson, "[\"RS256\"]", "STATIC_JWK_SET",
                new JWKSet(signingKey.toPublicJWK()).toString(),
                null, 0, 60, 1, 1);
    }

    private EmbedIdentityProviderSnapshot providerWithJwks(String jwks) {
        return new EmbedIdentityProviderSnapshot(
                "provider-1", "SIGNED_JWT", "ACTIVE",
                "https://id.partner.example", "partner-users",
                "[\"flow-embed-launch\"]", "[\"RS256\"]", "STATIC_JWK_SET",
                jwks, null, 0, 60, 1, 1);
    }

    private EmbedIdentityProviderSnapshot remoteProvider() {
        return remoteProvider("[\"RS256\"]");
    }

    private EmbedIdentityProviderSnapshot remoteProvider(String algorithmsJson) {
        return new EmbedIdentityProviderSnapshot(
                "provider-1", "SIGNED_JWT", "ACTIVE",
                "https://id.partner.example", "partner-users",
                "[\"flow-embed-launch\"]", algorithmsJson, "REMOTE_JWKS",
                null, "https://id.partner.example/.well-known/jwks.json",
                0, 60, 1, 1);
    }

    /** 构造只需通过 JOSE 解析的断言，验证不支持算法会在远程取 key 前被拒绝。 */
    private String compactAssertion(String algorithm, String keyId) throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString(objectMapper.writeValueAsBytes(Map.of(
                "alg", algorithm, "typ", "JWT", "kid", keyId)));
        String payload = encoder.encodeToString(objectMapper.writeValueAsBytes(Map.of(
                "iss", "https://id.partner.example",
                "aud", "flow-embed-launch",
                "sub", "subject-001",
                "jti", "jti-unsupported-algorithm",
                "iat", now.getEpochSecond(),
                "exp", now.plusSeconds(60).getEpochSecond())));
        return header + "." + payload + ".AA";
    }

    private String assertion(
            String issuer,
            List<String> audiences,
            Instant issuedAt,
            Instant expiresAt,
            String subject,
            String jti) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(JOSEObjectType.JWT)
                        .keyID(signingKey.getKeyID())
                        .build(),
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .audience(audiences)
                        .subject(subject)
                        .jwtID(jti)
                        .issueTime(Date.from(issuedAt))
                        .expirationTime(Date.from(expiresAt))
                        .build());
        jwt.sign(new RSASSASigner(signingKey.toPrivateKey()));
        return jwt.serialize();
    }
}
