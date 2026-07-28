package com.workflow.http;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Validates workflow HTTP destinations before a connection is opened.
 */
@Component
@RequiredArgsConstructor
public class RestEndpointPolicy {

    private final WorkflowHttpProperties properties;

    public void validate(URI uri) {
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme)
                && !("http".equalsIgnoreCase(scheme)
                        && properties.isAllowHttp())) {
            throw new IllegalArgumentException(
                    "REST 服务任务仅允许 HTTPS");
        }
        if (uri.getUserInfo() != null
                || !StringUtils.hasText(uri.getHost())) {
            throw new IllegalArgumentException(
                    "REST 服务任务 URL 不允许用户信息且必须包含主机名");
        }
        int port = uri.getPort();
        if (port == 0 || port > 65535) {
            throw new IllegalArgumentException(
                    "REST 服务任务 URL 端口无效");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!isAllowedHost(host)) {
            throw new IllegalArgumentException(
                    "REST 服务任务目标主机不在允许列表");
        }
        if (!properties.isAllowPrivateAddresses()) {
            validatePublicAddresses(host);
        }
    }

    private boolean isAllowedHost(String host) {
        for (String configured :
                properties.getAllowedHosts()) {
            String allowed = configured == null
                    ? ""
                    : configured.trim().toLowerCase(Locale.ROOT);
            if (host.equals(allowed)) {
                return true;
            }
            if (allowed.startsWith("*.")
                    && host.endsWith(allowed.substring(1))
                    && host.length() > allowed.length() - 1) {
                return true;
            }
        }
        return false;
    }

    private void validatePublicAddresses(String host) {
        try {
            InetAddress[] addresses =
                    InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                throw new IllegalArgumentException(
                        "REST 服务任务目标主机无法解析");
            }
            for (InetAddress address : addresses) {
                if (isNonPublic(address)) {
                    throw new IllegalArgumentException(
                            "REST 服务任务禁止访问私网或保留地址");
                }
            }
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException(
                    "REST 服务任务目标主机无法解析",
                    exception);
        }
    }

    private boolean isNonPublic(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return first == 0
                    || first == 127
                    || (first == 100 && second >= 64
                            && second <= 127)
                    || (first == 192 && second == 0)
                    || (first == 198
                            && (second == 18 || second == 19))
                    || (first == 198 && second == 51)
                    || (first == 203 && second == 0)
                    || first >= 224;
        }
        if (address instanceof Inet6Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return (first & 0xfe) == 0xfc
                    || (first == 0xfe && (second & 0xc0) == 0x80)
                    || first == 0xff
                    || (first == 0x20
                            && second == 0x01
                            && Byte.toUnsignedInt(bytes[2]) == 0x0d
                            && Byte.toUnsignedInt(bytes[3]) == 0xb8);
        }
        return true;
    }
}
