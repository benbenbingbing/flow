package com.workflow.embed.infrastructure.identity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.workflow.embed.application.port.EmbedSignedAssertionVerifierPort;
import com.workflow.embed.application.port.EmbedRemoteJwkSetPort;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedIdentityProviderSnapshot;
import com.workflow.embed.domain.EmbedIdentityProviderPolicy;
import com.workflow.embed.domain.VerifiedExternalSubject;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 使用管理员发布的静态或受控远程 JWK Set 验证短期第三方用户断言。
 *
 * <p>V1 只允许非对称签名。REMOTE_JWKS 必须经专用端口加载，由基础设施层负责 SSRF、DNS
 * 重绑定、重定向、响应大小和缓存边界；本类不会读取 JWT 的 {@code jku/x5u}。</p>
 */
public final class NimbusEmbedSignedAssertionVerifier
        implements EmbedSignedAssertionVerifierPort {

    private static final String EMBED_ASSERTION_AUDIENCE = "flow-embed-launch";
    private static final TypeReference<List<String>> STRING_LIST =
            new TypeReference<>() { };

    private final ObjectMapper objectMapper;
    private final EmbedRemoteJwkSetPort remoteJwkSetPort;

    public NimbusEmbedSignedAssertionVerifier(ObjectMapper objectMapper) {
        this(objectMapper, (provider, forceRefresh) -> {
            throw new IllegalStateException("Remote JWK Set loader is unavailable");
        });
    }

    public NimbusEmbedSignedAssertionVerifier(
            ObjectMapper objectMapper,
            EmbedRemoteJwkSetPort remoteJwkSetPort) {
        this.objectMapper = objectMapper;
        this.remoteJwkSetPort = remoteJwkSetPort;
    }

    /**
     * 完整验证签名、算法、kid、issuer、audience、时间窗、subject 和 jti 后才返回主体。
     * 任一失败均折叠为同一个 403，调用方和日志都不会拿到未经验证的 Claims。
     */
    @Override
    public VerifiedExternalSubject verify(
            EmbedIdentityProviderSnapshot provider,
            String assertion,
            Instant now) {
        try {
            validateProvider(provider);
            SignedJWT jwt = SignedJWT.parse(assertion);
            validateType(jwt);
            JWSAlgorithm algorithm = jwt.getHeader().getAlgorithm();
            Set<String> configuredAlgorithms = parseRequiredSet(
                    provider.algorithmsJson(), 64);
            Set<String> expectedAudiences = parseRequiredSet(
                    provider.audiencesJson(), EmbedIdentityProviderPolicy.MAX_AUDIENCE_LENGTH);
            if (algorithm == null
                    || !EmbedIdentityProviderPolicy
                            .supportsSigningAlgorithm(algorithm.getName())
                    || configuredAlgorithms.stream().anyMatch(configured ->
                            !EmbedIdentityProviderPolicy
                                    .supportsSigningAlgorithm(configured))
                    || !configuredAlgorithms.contains(algorithm.getName())
                    || !expectedAudiences.contains(EMBED_ASSERTION_AUDIENCE)) {
                throw invalid();
            }

            JWK key = selectVerificationKey(provider, jwt, algorithm);
            if (!jwt.verify(verifier(key, algorithm))) {
                throw invalid();
            }
            return verifiedClaims(
                    provider, jwt.getJWTClaimsSet(), expectedAudiences, now);
        } catch (EmbedException exception) {
            throw exception;
        } catch (Exception ignored) {
            // 解析、公钥转换和密码学异常统一折叠，禁止形成 key/claim 探针。
            throw invalid();
        }
    }

    private static void validateProvider(EmbedIdentityProviderSnapshot provider) {
        if (provider == null
                || !provider.isActive()
                || !"SIGNED_JWT".equals(provider.type())
                || provider.issuer() == null
                || provider.issuer().isBlank()
                || provider.subjectNamespace() == null
                || provider.subjectNamespace().isBlank()
                || provider.clockSkewSeconds() < 0
                || provider.clockSkewSeconds() > 300
                || provider.maxAssertionLifetimeSeconds() < 1
                || provider.maxAssertionLifetimeSeconds() > 300) {
            throw invalid();
        }
        boolean staticKeys = "STATIC_JWK_SET".equals(provider.jwksMode())
                && hasText(provider.jwksJson())
                && !hasText(provider.jwksUrl());
        boolean remoteKeys = "REMOTE_JWKS".equals(provider.jwksMode())
                && !hasText(provider.jwksJson())
                && hasText(provider.jwksUrl());
        if (!staticKeys && !remoteKeys) {
            throw invalid();
        }
    }

    private static void validateType(SignedJWT jwt) {
        JOSEObjectType type = jwt.getHeader().getType();
        if (type != null && !JOSEObjectType.JWT.equals(type)) {
            throw invalid();
        }
    }

    private JWK selectVerificationKey(
            EmbedIdentityProviderSnapshot provider,
            SignedJWT jwt,
            JWSAlgorithm algorithm) throws Exception {
        String keyId = jwt.getHeader().getKeyID();
        if (keyId == null || keyId.isBlank() || keyId.length() > 128) {
            throw invalid();
        }
        String jwksJson = jwksJson(provider, false);
        List<JWK> keys = parsePublicKeys(jwksJson);
        if ("REMOTE_JWKS".equals(provider.jwksMode())
                && keys.stream().noneMatch(key -> keyId.equals(key.getKeyID()))) {
            // 仅 unknown kid 触发一次受控强刷；算法或用途不匹配不能借此制造刷新风暴。
            jwksJson = jwksJson(provider, true);
            keys = parsePublicKeys(jwksJson);
        }
        List<JWK> candidates = keys.stream()
                .filter(key -> keyId.equals(key.getKeyID()))
                .filter(key -> key.getAlgorithm() == null
                        || algorithm.equals(key.getAlgorithm()))
                .filter(key -> key.getKeyUse() == null
                        || KeyUse.SIGNATURE.equals(key.getKeyUse()))
                .filter(key -> key.getKeyOperations() == null
                        || key.getKeyOperations().contains(KeyOperation.VERIFY))
                .toList();
        // kid 必须精确命中唯一公钥；重复 kid 也拒绝，避免轮换配置歧义。
        if (candidates.size() != 1 || candidates.get(0).isPrivate()) {
            throw invalid();
        }
        return candidates.get(0);
    }

    private String jwksJson(
            EmbedIdentityProviderSnapshot provider,
            boolean forceRefresh) {
        return "REMOTE_JWKS".equals(provider.jwksMode())
                ? remoteJwkSetPort.load(provider, forceRefresh)
                : provider.jwksJson();
    }

    private List<JWK> parsePublicKeys(String jwksJson) throws Exception {
        rejectPrivateJwkMaterial(jwksJson);
        List<JWK> keys = JWKSet.parse(jwksJson).getKeys();
        if (keys.stream().anyMatch(key -> key.getKeyType() == null
                || !EmbedIdentityProviderPolicy
                        .supportsPublicJwkType(key.getKeyType().getValue()))) {
            // REMOTE_JWKS adapter 会先过滤混合集；走到这里的额外类型只能来自未迁移的
            // 静态历史配置，必须整份拒绝，不能让受支持 kid 掩盖不可发布的 Provider。
            throw invalid();
        }
        return keys;
    }

    private void rejectPrivateJwkMaterial(String jwksJson) throws Exception {
        JsonNode root = objectMapper.readTree(jwksJson);
        JsonNode keys = root == null ? null : root.get("keys");
        if (root == null || !root.isObject() || keys == null || !keys.isArray()
                || keys.isEmpty() || keys.size() > 32) {
            throw invalid();
        }
        Set<String> privateParameters = Set.of(
                "d", "p", "q", "dp", "dq", "qi", "oth", "k", "seed");
        for (JsonNode key : keys) {
            if (!key.isObject()) {
                throw invalid();
            }
            for (String privateParameter : privateParameters) {
                if (key.has(privateParameter)) {
                    // Provider 配置只允许公钥。即使 Nimbus 能从私钥派生验证公钥也必须拒绝，
                    // 防止第三方签名私钥被复制进 Flow 数据库和管理接口。
                    throw invalid();
                }
            }
        }
    }

    private static JWSVerifier verifier(JWK key, JWSAlgorithm algorithm)
            throws Exception {
        if (algorithm.getName().startsWith("RS")
                || algorithm.getName().startsWith("PS")) {
            if (!(key instanceof RSAKey rsaKey) || rsaKey.size() < 2048) {
                // 管理端或远程 JWKS 即使误配了可被现实成本分解的短 RSA 模数，
                // 运行端也不能把它当成身份信任根；2048 bit 是 V1 的最低边界。
                throw invalid();
            }
            return new RSASSAVerifier(rsaKey.toRSAPublicKey());
        }
        if (algorithm.getName().startsWith("ES")) {
            if (!(key instanceof ECKey ecKey)
                    || !expectedCurve(algorithm).equals(ecKey.getCurve())) {
                throw invalid();
            }
            return new ECDSAVerifier(ecKey.toECPublicKey());
        }
        throw invalid();
    }

    private static Curve expectedCurve(JWSAlgorithm algorithm) {
        return switch (algorithm.getName()) {
            case "ES256" -> Curve.P_256;
            case "ES384" -> Curve.P_384;
            case "ES512" -> Curve.P_521;
            default -> throw invalid();
        };
    }

    private VerifiedExternalSubject verifiedClaims(
            EmbedIdentityProviderSnapshot provider,
            JWTClaimsSet claims,
            Set<String> expectedAudiences,
            Instant now) throws Exception {
        if (!provider.issuer().equals(claims.getIssuer())) {
            throw invalid();
        }
        List<String> actualAudiences = claims.getAudience();
        // Provider 可为迁移保留其他 Audience，但人员断言本身必须包含 Embed 专用值。
        // 只做任意交集会允许同一 Issuer 签发的普通业务 Token 被重放为 Launch 断言。
        if (!expectedAudiences.contains(EMBED_ASSERTION_AUDIENCE)
                || actualAudiences == null
                || !actualAudiences.contains(EMBED_ASSERTION_AUDIENCE)
                || actualAudiences.stream().noneMatch(expectedAudiences::contains)) {
            throw invalid();
        }

        Instant issuedAt = instant(claims.getIssueTime());
        Instant expiresAt = instant(claims.getExpirationTime());
        Instant notBefore = instant(claims.getNotBeforeTime());
        long skew = provider.clockSkewSeconds();
        if (issuedAt == null
                || expiresAt == null
                || !expiresAt.isAfter(issuedAt)
                || Duration.between(issuedAt, expiresAt).compareTo(
                        Duration.ofSeconds(provider.maxAssertionLifetimeSeconds())) > 0
                || issuedAt.isAfter(now.plusSeconds(skew))
                || !expiresAt.isAfter(now.minusSeconds(skew))
                || (notBefore != null && notBefore.isAfter(now.plusSeconds(skew)))) {
            throw invalid();
        }

        String subject = requiredClaim(claims.getSubject(), 128);
        String jti = requiredClaim(claims.getJWTID(), 128);
        return new VerifiedExternalSubject(
                provider.subjectNamespace(),
                subject,
                provider.issuer(),
                jti,
                expiresAt);
    }

    private Set<String> parseRequiredSet(String json, int maxValueLength) throws Exception {
        List<String> values = objectMapper.readValue(json, STRING_LIST);
        if (values == null || values.isEmpty() || values.size() > 32) {
            throw invalid();
        }
        Set<String> result = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank() || value.length() > maxValueLength
                    || !result.add(value)) {
                throw invalid();
            }
        }
        return Set.copyOf(result);
    }

    private static String requiredClaim(String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw invalid();
        }
        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Instant instant(Date value) {
        return value == null ? null : value.toInstant();
    }

    private static EmbedException invalid() {
        return new EmbedException(
                403,
                EmbedErrorCode.EMBED_IDENTITY_ASSERTION_INVALID,
                "Identity assertion is invalid");
    }
}
