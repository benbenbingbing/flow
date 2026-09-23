package com.workflow.openapi.application;

import java.util.Map;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 负责集成密钥{@code hasher}的业务处理；协调校验、状态变化及后续结果传递。
 */
@Component
public class IntegrationSecretHasher implements PasswordEncoder {

    private static final String ENCODING_ID = "argon2";

    private final PasswordEncoder passwordEncoder =
            new DelegatingPasswordEncoder(
                    ENCODING_ID,
                    Map.of(
                            ENCODING_ID,
                            Argon2PasswordEncoder
                                    .defaultsForSpringSecurity_v5_8()));

    /**
     * 生成哈希文本，供后续匹配或展示。
     *
     * @param secret 密钥，作为 {@code encode} 的输入影响后续处理
     * @return 处理后的哈希文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public String hash(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("凭据不能为空");
        }
        return encode(secret);
    }

    /**
     * 编码集成密钥{@code hasher}；输出作为后续校验或处理的输入。
     *
     * @param rawPassword 原始密码，供本方法编码集成密钥{@code hasher}时使用
     * @return 编码后的集成密钥{@code hasher}文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Override
    public String encode(CharSequence rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("凭据不能为空");
        }
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * 判断是否匹配集成密钥{@code hasher}；判断结果决定调用方的后续分支。
     *
     * @param rawPassword 原始密码，供本方法判断是否匹配集成密钥{@code hasher}时使用
     * @param encodedPassword 已编码密码，供本方法判断是否匹配集成密钥{@code hasher}时使用
     * @return 集成密钥{@code hasher}条件成立时为 true，否则为 false
     */
    @Override
    public boolean matches(
            CharSequence rawPassword,
            String encodedPassword) {
        return rawPassword != null
                && encodedPassword != null
                && passwordEncoder.matches(
                        rawPassword,
                        encodedPassword);
    }

    /**
     * 判断{@code upgrade}{@code encoding}条件是否成立，供调用方选择后续分支。
     *
     * @param encodedPassword 已编码密码，供本方法处理{@code upgrade}{@code encoding}时使用
     * @return {@code upgrade}{@code encoding}条件成立时为 true，否则为 false
     */
    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return passwordEncoder.upgradeEncoding(encodedPassword);
    }
}
