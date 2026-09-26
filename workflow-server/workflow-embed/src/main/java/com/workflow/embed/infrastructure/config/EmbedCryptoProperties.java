package com.workflow.embed.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Versioned Embed context-encryption and privacy-index key material. */
@ConfigurationProperties(prefix = "workflow.embed.crypto")
public class EmbedCryptoProperties {

    private String contextKeyBase64;
    private String contextKeyVersion = "embed-context-v1";
    private String hmacKeyBase64;
    private String hmacKeyVersion = "embed-hmac-v1";

    /**
     * 读取上下文键{@code base64}；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的上下文键{@code base64}文本，供调用方比较或展示
     */
    public String getContextKeyBase64() {
        return contextKeyBase64;
    }

    /**
     * 设置上下文键{@code base64}；后续读取或执行将使用更新后的状态。
     *
     * @param contextKeyBase64 上下文键{@code base64}，供本方法设置上下文键{@code base64}时使用
     */
    public void setContextKeyBase64(String contextKeyBase64) {
        this.contextKeyBase64 = contextKeyBase64;
    }

    /**
     * 读取上下文键版本；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的上下文键版本文本，供调用方比较或展示
     */
    public String getContextKeyVersion() {
        return contextKeyVersion;
    }

    /**
     * 设置上下文键版本；后续读取或执行将使用更新后的状态。
     *
     * @param contextKeyVersion 上下文键版本，供本方法设置上下文键版本时使用
     */
    public void setContextKeyVersion(String contextKeyVersion) {
        this.contextKeyVersion = contextKeyVersion;
    }

    /**
     * 读取HMAC键{@code base64}；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的HMAC键{@code base64}文本，供调用方比较或展示
     */
    public String getHmacKeyBase64() {
        return hmacKeyBase64;
    }

    /**
     * 设置HMAC键{@code base64}；后续读取或执行将使用更新后的状态。
     *
     * @param hmacKeyBase64 HMAC键{@code base64}，供本方法设置HMAC键{@code base64}时使用
     */
    public void setHmacKeyBase64(String hmacKeyBase64) {
        this.hmacKeyBase64 = hmacKeyBase64;
    }

    /**
     * 读取HMAC键版本；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的HMAC键版本文本，供调用方比较或展示
     */
    public String getHmacKeyVersion() {
        return hmacKeyVersion;
    }

    /**
     * 设置HMAC键版本；后续读取或执行将使用更新后的状态。
     *
     * @param hmacKeyVersion HMAC键版本，供本方法设置HMAC键版本时使用
     */
    public void setHmacKeyVersion(String hmacKeyVersion) {
        this.hmacKeyVersion = hmacKeyVersion;
    }
}
