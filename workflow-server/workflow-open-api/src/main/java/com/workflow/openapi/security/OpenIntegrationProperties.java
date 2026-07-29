package com.workflow.openapi.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getPrivateKeyLocation() {
        return privateKeyLocation;
    }

    public void setPrivateKeyLocation(String privateKeyLocation) {
        this.privateKeyLocation = privateKeyLocation;
    }

    public String getPublicKeyLocation() {
        return publicKeyLocation;
    }

    public void setPublicKeyLocation(String publicKeyLocation) {
        this.publicKeyLocation = publicKeyLocation;
    }

    public String getPreviousPublicKeys() {
        return previousPublicKeys;
    }

    public void setPreviousPublicKeys(String value) {
        this.previousPublicKeys = value;
    }

    public int getTokenClientLimitPerMinute() {
        return tokenClientLimitPerMinute;
    }

    public void setTokenClientLimitPerMinute(int value) {
        this.tokenClientLimitPerMinute = value;
    }

    public int getTokenAddressLimitPerMinute() {
        return tokenAddressLimitPerMinute;
    }

    public void setTokenAddressLimitPerMinute(int value) {
        this.tokenAddressLimitPerMinute = value;
    }

    public boolean isTrustForwardedHeaders() {
        return trustForwardedHeaders;
    }

    public void setTrustForwardedHeaders(boolean value) {
        this.trustForwardedHeaders = value;
    }

    public List<String> getTrustedProxyCidrs() {
        return trustedProxyCidrs;
    }

    public void setTrustedProxyCidrs(List<String> value) {
        this.trustedProxyCidrs = value == null
                ? List.of()
                : List.copyOf(value);
    }
}
