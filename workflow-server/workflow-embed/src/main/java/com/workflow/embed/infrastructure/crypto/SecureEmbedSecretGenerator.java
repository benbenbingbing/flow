package com.workflow.embed.infrastructure.crypto;

import com.workflow.embed.application.port.EmbedSecretGeneratorPort;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/** CSPRNG-backed base64url secret generator. */
public final class SecureEmbedSecretGenerator implements EmbedSecretGeneratorPort {

    private final SecureRandom random;

    /**
     * 初始化{@code secure}嵌入式密钥生成器，保存构造参数供后续方法使用。
     *
     * @param random {@code random}，保存在对象中供后续校验、查询或展示
     */
    public SecureEmbedSecretGenerator(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * 生成{@code secure}嵌入式密钥生成器；结果供调用方的后续步骤使用。
     *
     * @param bytes 字节，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @return 生成后的{@code secure}嵌入式密钥生成器文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Override
    public String generate(int bytes) {
        if (bytes < 32 || bytes > 64) {
            throw new IllegalArgumentException("Embed secrets must contain 32 to 64 random bytes");
        }
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
