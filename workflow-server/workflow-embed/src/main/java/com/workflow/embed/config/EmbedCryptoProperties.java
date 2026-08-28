package com.workflow.embed.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Versioned Embed context-encryption and privacy-index key material. */
@ConfigurationProperties(prefix = "workflow.embed.crypto")
public class EmbedCryptoProperties {

    private String contextKeyBase64;
    private String contextKeyVersion = "embed-context-v1";
    private String hmacKeyBase64;
    private String hmacKeyVersion = "embed-hmac-v1";

    public String getContextKeyBase64() {
        return contextKeyBase64;
    }

    public void setContextKeyBase64(String contextKeyBase64) {
        this.contextKeyBase64 = contextKeyBase64;
    }

    public String getContextKeyVersion() {
        return contextKeyVersion;
    }

    public void setContextKeyVersion(String contextKeyVersion) {
        this.contextKeyVersion = contextKeyVersion;
    }

    public String getHmacKeyBase64() {
        return hmacKeyBase64;
    }

    public void setHmacKeyBase64(String hmacKeyBase64) {
        this.hmacKeyBase64 = hmacKeyBase64;
    }

    public String getHmacKeyVersion() {
        return hmacKeyVersion;
    }

    public void setHmacKeyVersion(String hmacKeyVersion) {
        this.hmacKeyVersion = hmacKeyVersion;
    }
}
