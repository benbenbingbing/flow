package com.workflow.admin.auth.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolves a stable login client address without trusting spoofed headers.
 */
@Component
public class ClientAddressResolver {

    private final boolean trustForwardedHeaders;

    public ClientAddressResolver(
            @Value(
                    "${workflow.security.trust-forwarded-headers:false}")
            boolean trustForwardedHeaders) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    public String resolve(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String forwarded =
                    request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                String first = forwarded.split(",", 2)[0].trim();
                String normalized = normalizeLiteral(first);
                if (normalized != null) {
                    return normalized;
                }
            }
        }
        String normalized =
                normalizeLiteral(request.getRemoteAddr());
        return normalized == null
                ? "unknown"
                : normalized;
    }

    private String normalizeLiteral(String value) {
        if (!StringUtils.hasText(value)
                || value.length() > 45
                || !value.matches("[0-9A-Fa-f:.]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(value)
                    .getHostAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }
}
