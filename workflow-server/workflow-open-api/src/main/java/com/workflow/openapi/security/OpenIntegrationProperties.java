package com.workflow.openapi.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 封装打开集成属性集合相关能力和状态；供同一业务流程的后续处理使用。
 */
@ConfigurationProperties(prefix = "workflow.open-api")
public class OpenIntegrationProperties {

    private boolean enabled;
    private String issuer = "https://flow.invalid";
    private String audience = "flow-open-api";
    private Duration accessTokenTtl = Duration.ofMinutes(10);
    private String keyId;
    private String privateKeyLocation;
    private String publicKeyLocation;
    private String previousPublicKeys = "";
    private int tokenClientLimitPerMinute = 30;
    private int tokenAddressLimitPerMinute = 300;
    private boolean trustForwardedHeaders;
    private List<String> trustedProxyCidrs = List.of();

    /**
     * 判断是否启用；判断结果决定调用方的后续分支。
     *
     * @return 启用条件成立时为 true，否则为 false
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置启用；后续读取或执行将使用更新后的状态。
     *
     * @param enabled 启用，供本方法设置启用时使用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 读取签发方；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的签发方文本，供调用方比较或展示
     */
    public String getIssuer() {
        return issuer;
    }

    /**
     * 设置签发方；后续读取或执行将使用更新后的状态。
     *
     * @param issuer 签发方，供本方法设置签发方时使用
     */
    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    /**
     * 读取{@code audience}；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的{@code audience}文本，供调用方比较或展示
     */
    public String getAudience() {
        return audience;
    }

    /**
     * 设置{@code audience}；后续读取或执行将使用更新后的状态。
     *
     * @param audience {@code audience}，供本方法设置{@code audience}时使用
     */
    public void setAudience(String audience) {
        this.audience = audience;
    }

    /**
     * 读取访问令牌{@code ttl}；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的时长结果，供调用方继续处理
     */
    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    /**
     * 设置访问令牌{@code ttl}；后续读取或执行将使用更新后的状态。
     *
     * @param accessTokenTtl 访问令牌{@code ttl}，供本方法设置访问令牌{@code ttl}时使用
     */
    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    /**
     * 读取键ID；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的键ID文本，供调用方比较或展示
     */
    public String getKeyId() {
        return keyId;
    }

    /**
     * 设置键ID；后续读取或执行将使用更新后的状态。
     *
     * @param keyId 键ID，后续用于设置键ID时定位或关联目标
     */
    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    /**
     * 读取{@code private}键{@code location}；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的{@code private}键{@code location}文本，供调用方比较或展示
     */
    public String getPrivateKeyLocation() {
        return privateKeyLocation;
    }

    /**
     * 设置{@code private}键{@code location}；后续读取或执行将使用更新后的状态。
     *
     * @param privateKeyLocation {@code private}键{@code location}，供本方法设置{@code private}键{@code location}时使用
     */
    public void setPrivateKeyLocation(String privateKeyLocation) {
        this.privateKeyLocation = privateKeyLocation;
    }

    /**
     * 读取公开键{@code location}；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的公开键{@code location}文本，供调用方比较或展示
     */
    public String getPublicKeyLocation() {
        return publicKeyLocation;
    }

    /**
     * 设置公开键{@code location}；后续读取或执行将使用更新后的状态。
     *
     * @param publicKeyLocation 公开键{@code location}，供本方法设置公开键{@code location}时使用
     */
    public void setPublicKeyLocation(String publicKeyLocation) {
        this.publicKeyLocation = publicKeyLocation;
    }

    /**
     * 读取上一项公开键集合；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的上一项公开键集合文本，供调用方比较或展示
     */
    public String getPreviousPublicKeys() {
        return previousPublicKeys;
    }

    /**
     * 设置上一项公开键集合；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置上一项公开键集合的原始输入，结果供调用方继续使用
     */
    public void setPreviousPublicKeys(String value) {
        this.previousPublicKeys = value;
    }

    /**
     * 读取令牌客户端上限每分钟；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的打开集成属性集合结果，供调用方继续处理
     */
    public int getTokenClientLimitPerMinute() {
        return tokenClientLimitPerMinute;
    }

    /**
     * 设置令牌客户端上限每分钟；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置令牌客户端上限每分钟的原始输入，结果供调用方继续使用
     */
    public void setTokenClientLimitPerMinute(int value) {
        this.tokenClientLimitPerMinute = value;
    }

    /**
     * 读取令牌地址上限每分钟；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的打开集成属性集合结果，供调用方继续处理
     */
    public int getTokenAddressLimitPerMinute() {
        return tokenAddressLimitPerMinute;
    }

    /**
     * 设置令牌地址上限每分钟；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置令牌地址上限每分钟的原始输入，结果供调用方继续使用
     */
    public void setTokenAddressLimitPerMinute(int value) {
        this.tokenAddressLimitPerMinute = value;
    }

    /**
     * 判断是否{@code trust}{@code forwarded}{@code headers}；判断结果决定调用方的后续分支。
     *
     * @return {@code trust}{@code forwarded}{@code headers}条件成立时为 true，否则为 false
     */
    public boolean isTrustForwardedHeaders() {
        return trustForwardedHeaders;
    }

    /**
     * 设置{@code trust}{@code forwarded}{@code headers}；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置{@code trust}{@code forwarded}{@code headers}的原始输入，结果供调用方继续使用
     */
    public void setTrustForwardedHeaders(boolean value) {
        this.trustForwardedHeaders = value;
    }

    /**
     * 读取可信{@code proxy}{@code cidrs}；查询结果供调用方展示或继续处理。
     *
     * @return 打开集成属性集合，供调用方遍历或展示
     */
    public List<String> getTrustedProxyCidrs() {
        return trustedProxyCidrs;
    }

    /**
     * 设置可信{@code proxy}{@code cidrs}；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置可信{@code proxy}{@code cidrs}的原始输入，结果供调用方继续使用
     */
    public void setTrustedProxyCidrs(List<String> value) {
        this.trustedProxyCidrs = value == null
                ? List.of()
                : List.copyOf(value);
    }
}
