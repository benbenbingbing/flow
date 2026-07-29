package com.workflow.http;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Validates workflow HTTP destinations before a connection is opened.
 */
@Component
public class RestEndpointPolicy {

    private final WorkflowHttpProperties properties;
    private final HostAddressResolver resolver;

    public RestEndpointPolicy(WorkflowHttpProperties properties) {
        this(properties, InetAddress::getAllByName);
    }

    RestEndpointPolicy(
            WorkflowHttpProperties properties,
            HostAddressResolver resolver) {
        this.properties = properties;
        this.resolver = resolver;
    }

    public void validate(URI uri) {
        validateAndResolve(
                uri,
                Set.copyOf(properties.getAllowedHosts()),
                properties.isAllowPrivateAddresses());
    }

    public ApprovedEndpoint validateAndResolve(
            URI uri,
            Set<String> allowedHosts,
            boolean allowPrivateAddresses) {
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
        if (!isAllowedHost(host, allowedHosts)) {
            throw new IllegalArgumentException(
                    "REST 服务任务目标主机不在允许列表");
        }
        List<InetAddress> addresses = resolve(host);
        if (!allowPrivateAddresses) {
            validatePublicAddresses(addresses);
        }
        return new ApprovedEndpoint(uri, host, List.copyOf(addresses));
    }

    private boolean isAllowedHost(
            String host,
            Set<String> allowedHosts) {
        for (String configured : allowedHosts) {
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

    private List<InetAddress> resolve(String host) {
        try {
            InetAddress[] addresses = resolver.resolve(host);
            if (addresses.length == 0) {
                throw new IllegalArgumentException(
                        "REST 服务任务目标主机无法解析");
            }
            return Arrays.asList(addresses.clone());
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException(
                    "REST 服务任务目标主机无法解析",
                    exception);
        }
    }

    private void validatePublicAddresses(
            List<InetAddress> addresses) {
        for (InetAddress address : addresses) {
            if (isNonPublic(address)) {
                throw new IllegalArgumentException(
                        "REST 服务任务禁止访问私网或保留地址");
            }
        }
    }

    boolean isNonPublic(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            long value = ((long) Byte.toUnsignedInt(bytes[0]) << 24)
                    | ((long) Byte.toUnsignedInt(bytes[1]) << 16)
                    | ((long) Byte.toUnsignedInt(bytes[2]) << 8)
                    | Byte.toUnsignedInt(bytes[3]);
            return inIpv4(value, "0.0.0.0", 8)
                    || inIpv4(value, "10.0.0.0", 8)
                    || inIpv4(value, "100.64.0.0", 10)
                    || inIpv4(value, "127.0.0.0", 8)
                    || inIpv4(value, "169.254.0.0", 16)
                    || inIpv4(value, "172.16.0.0", 12)
                    || inIpv4(value, "192.0.0.0", 24)
                    || inIpv4(value, "192.0.2.0", 24)
                    || inIpv4(value, "192.88.99.0", 24)
                    || inIpv4(value, "192.168.0.0", 16)
                    || inIpv4(value, "198.18.0.0", 15)
                    || inIpv4(value, "198.51.100.0", 24)
                    || inIpv4(value, "203.0.113.0", 24)
                    || inIpv4(value, "224.0.0.0", 4)
                    || inIpv4(value, "240.0.0.0", 4);
        }
        if (address instanceof Inet6Address) {
            return matches(bytes, new byte[]{0x00}, 96)
                    || matches(bytes, new byte[]{0x00, 0x64, (byte) 0xff,
                            (byte) 0x9b}, 96)
                    || matches(bytes, new byte[]{0x01, 0x00}, 64)
                    || matches(bytes, new byte[]{0x20, 0x01}, 23)
                    || matches(bytes, new byte[]{0x20, 0x01,
                            0x0d, (byte) 0xb8}, 32)
                    || matches(bytes, new byte[]{0x20, 0x02}, 16)
                    || matches(bytes, new byte[]{0x3f, (byte) 0xff}, 20)
                    || matches(bytes, new byte[]{(byte) 0xfc}, 7)
                    || matches(bytes, new byte[]{(byte) 0xfe, (byte) 0x80}, 10)
                    || matches(bytes, new byte[]{(byte) 0xff}, 8);
        }
        return true;
    }

    private boolean inIpv4(long value, String network, int prefix) {
        String[] parts = network.split("\\.");
        long base = 0;
        for (String part : parts) {
            base = (base << 8) | Integer.parseInt(part);
        }
        long mask = prefix == 0
                ? 0
                : 0xffffffffL << (32 - prefix) & 0xffffffffL;
        return (value & mask) == (base & mask);
    }

    private boolean matches(
            byte[] address,
            byte[] prefix,
            int prefixBits) {
        int wholeBytes = prefixBits / 8;
        int remainingBits = prefixBits % 8;
        for (int index = 0; index < wholeBytes; index++) {
            byte expected = index < prefix.length
                    ? prefix[index]
                    : 0;
            if (address[index] != expected) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xff << (8 - remainingBits);
        int expected = wholeBytes < prefix.length
                ? Byte.toUnsignedInt(prefix[wholeBytes])
                : 0;
        return (Byte.toUnsignedInt(address[wholeBytes]) & mask)
                == (expected & mask);
    }
}
