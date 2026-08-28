package com.workflow.embed.domain;

import java.util.Set;

/**
 * Embed V1 Identity Provider 的管理态与运行态共享密码学契约。
 *
 * <p>这里仅声明运行时真正具备验证实现的算法和公钥类型。扩展该集合必须同时增加实际
 * verifier、管理校验及契约测试，不能只放宽配置入口。</p>
 */
public final class EmbedIdentityProviderPolicy {

    /** 单个 audience 的最大字符数，管理态和运行态必须保持一致。 */
    public static final int MAX_AUDIENCE_LENGTH = 256;

    private static final Set<String> SIGNING_ALGORITHMS = Set.of(
            "RS256", "RS384", "RS512",
            "PS256", "PS384", "PS512",
            "ES256", "ES384", "ES512");
    private static final Set<String> PUBLIC_JWK_TYPES = Set.of("RSA", "EC");

    private EmbedIdentityProviderPolicy() {
    }

    /** 仅当 V1 runtime 存在对应 verifier 时才返回 true。 */
    public static boolean supportsSigningAlgorithm(String algorithm) {
        return SIGNING_ALGORITHMS.contains(algorithm);
    }

    /** 仅当 V1 runtime 能将该 JWK 类型用于签名验证时才返回 true。 */
    public static boolean supportsPublicJwkType(String keyType) {
        return PUBLIC_JWK_TYPES.contains(keyType);
    }
}
