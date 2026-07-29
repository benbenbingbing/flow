package com.workflow.openapi.security;

import com.workflow.openapi.network.IpNetwork;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "workflow.open-api.enabled",
        havingValue = "true")
public class OpenIntegrationClientAddressResolver {

    private static final int MAXIMUM_FORWARDED_HEADER_LENGTH = 512;
    private static final int MAXIMUM_FORWARDED_ADDRESSES = 16;

    private final OpenIntegrationProperties properties;
    private final List<IpNetwork> trustedProxies;

    public OpenIntegrationClientAddressResolver(
            OpenIntegrationProperties properties) {
        this.properties = properties;
        if (!properties.isTrustForwardedHeaders()) {
            this.trustedProxies = List.of();
            return;
        }
        if (properties.getTrustedProxyCidrs().isEmpty()) {
            throw new IllegalStateException(
                    "信任转发头时必须配置可信代理 CIDR");
        }
        try {
            this.trustedProxies = properties.getTrustedProxyCidrs()
                    .stream()
                    .map(IpNetwork::parse)
                    .toList();
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "可信代理 CIDR 格式不正确",
                    exception);
        }
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = normalize(request.getRemoteAddr());
        if (!properties.isTrustForwardedHeaders()
                || remoteAddress == null
                || !isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remoteAddress;
        }
        if (forwarded.length() > MAXIMUM_FORWARDED_HEADER_LENGTH) {
            return null;
        }
        String[] values = forwarded.split(",", -1);
        if (values.length > MAXIMUM_FORWARDED_ADDRESSES) {
            return null;
        }
        List<String> addresses = new ArrayList<>(values.length);
        for (String value : values) {
            String normalized = normalize(value.trim());
            if (normalized == null) {
                return null;
            }
            addresses.add(normalized);
        }
        for (int index = addresses.size() - 1; index >= 0; index--) {
            String address = addresses.get(index);
            if (!isTrustedProxy(address)) {
                return address;
            }
        }
        return addresses.get(0);
    }

    private String normalize(String value) {
        try {
            return IpNetwork.parseAddress(value).getHostAddress();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isTrustedProxy(String address) {
        return trustedProxies.stream()
                .anyMatch(network -> network.contains(address));
    }
}
