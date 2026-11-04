package com.workflow.biz.project.embed.demo;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 宿主签名私钥不离开后端；Flow 只配置对应的公开 JWK Set。 */
@Service
@Profile("partner-embed-demo")
class PartnerAssertionService {
    private final EmbedDemoProperties.Flow config;
    private final Clock clock;
    private final RSAKey key;

    PartnerAssertionService(EmbedDemoProperties properties, @Qualifier("partnerEmbedDemoClock") Clock clock) {
        this.config = properties.flow();
        this.clock = clock;
        try {
            String pem = Files.readString(config.privateKey(), StandardCharsets.US_ASCII);
            String encoded = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            var privateKey = (RSAPrivateCrtKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)));
            if (privateKey.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException("RSA key too small");
            }
            key = new RSAKey.Builder(Base64URL.encode(privateKey.getModulus()), Base64URL.encode(privateKey.getPublicExponent()))
                    .privateKey(privateKey).keyID(config.assertionKeyId())
                    .keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build();
        } catch (Exception exception) {
            // 启动失败不附带底层异常，避免 PEM 内容或文件内容进入诊断输出。
            throw new IllegalStateException("Configure a readable PKCS#8 RSA private key of at least 2048 bits");
        }
    }

    /** 为已认证且授权的不可回收人员标识签发 60 秒断言；每次调用生成新 jti，禁止跨 Launch 重用。 */
    String sign(String externalSubject) {
        Instant now = clock.instant();
        var claims = new JWTClaimsSet.Builder().issuer(config.assertionIssuer())
                .subject(externalSubject).audience("flow-embed-launch")
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(60)))
                .jwtID(UUID.randomUUID().toString()).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT).keyID(config.assertionKeyId()).build(), claims);
        try {
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new EmbedDemoException(500, "HOST_ASSERTION_SIGNING_FAILED", null, null);
        }
    }

    /** 只导出公钥，供 Flow STATIC_JWK_SET 配置或 REMOTE_JWKS 读取；永不序列化私钥参数。 */
    Map<String, Object> publicJwks() {
        return new JWKSet(key.toPublicJWK()).toJSONObject();
    }
}
