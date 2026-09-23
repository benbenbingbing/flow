package com.workflow.openapi.application;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 负责集成密钥生成器的业务处理；协调校验、状态变化及后续结果传递。
 */
@Component
public class IntegrationSecretGenerator {

    private static final int CLIENT_ID_RANDOM_BYTES = 18;
    private static final int CLIENT_SECRET_RANDOM_BYTES = 32;

    private final SecureRandom secureRandom;

    /**
     * 初始化集成密钥生成器，保存构造参数供后续方法使用。
     */
    public IntegrationSecretGenerator() {
        this(new SecureRandom());
    }

    /**
     * 初始化集成密钥生成器，保存构造参数供后续方法使用。
     *
     * @param secureRandom {@code secure}{@code random}依赖，保存到当前对象供后续业务方法调用
     */
    IntegrationSecretGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    /**
     * 生成新客户端ID文本，供后续匹配或展示。
     *
     * @return 处理后的新客户端ID文本，供调用方比较或展示
     */
    public String newClientId() {
        return "flow_" + randomUrlSafe(CLIENT_ID_RANDOM_BYTES);
    }

    /**
     * 生成新客户端密钥文本，供后续匹配或展示。
     *
     * @return 处理后的新客户端密钥文本，供调用方比较或展示
     */
    public String newClientSecret() {
        return randomUrlSafe(CLIENT_SECRET_RANDOM_BYTES);
    }

    /**
     * 生成{@code random}URL安全文本，供后续匹配或展示。
     *
     * @param byteCount {@code byte}数量，供本方法处理{@code random}URL安全时使用
     * @return 处理后的{@code random}URL安全文本，供调用方比较或展示
     */
    private String randomUrlSafe(int byteCount) {
        byte[] value = new byte[byteCount];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
