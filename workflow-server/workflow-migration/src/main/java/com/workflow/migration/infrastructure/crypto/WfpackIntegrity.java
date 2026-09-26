package com.workflow.migration.infrastructure.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** wfpack 使用的摘要与签名算法；密钥来源和轮换时机由调用方控制，不在此缓存。 */
public final class WfpackIntegrity {
    private WfpackIntegrity() {}

    /**
     * 对校验清单计算 HMAC，供跨环境导入时验证来源；不改变现有十六进制签名格式。
     *
     * @param value 原始清单字节，不能重排或重新序列化
     * @param signingKey 调用时读取的当前环境密钥
     * @return 小写十六进制 HMAC-SHA256
     * @throws IllegalStateException 签名算法或密钥初始化失败
     */
    public static String hmac(byte[] value, String signingKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value));
        } catch (Exception e) {
            throw new IllegalStateException("发布包签名失败", e);
        }
    }

    /**
     * 计算包或文件的内容摘要，供完整性校验和幂等比较使用。
     *
     * @param value 待校验的原始字节
     * @return 小写十六进制 SHA-256
     * @throws IllegalStateException 摘要算法不可用
     */
    public static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception e) {
            throw new IllegalStateException("发布包哈希计算失败", e);
        }
    }
}
