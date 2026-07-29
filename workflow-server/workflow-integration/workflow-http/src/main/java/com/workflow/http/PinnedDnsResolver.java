package com.workflow.http;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import org.apache.hc.client5.http.DnsResolver;

final class PinnedDnsResolver implements DnsResolver {

    private final String host;
    private final InetAddress[] addresses;

    PinnedDnsResolver(ApprovedEndpoint approved) {
        this.host = approved.host().toLowerCase(Locale.ROOT);
        this.addresses = approved.addresses()
                .toArray(InetAddress[]::new);
    }

    @Override
    public InetAddress[] resolve(String requestedHost)
            throws UnknownHostException {
        requireApprovedHost(requestedHost);
        return addresses.clone();
    }

    @Override
    public String resolveCanonicalHostname(String requestedHost)
            throws UnknownHostException {
        requireApprovedHost(requestedHost);
        return host;
    }

    private void requireApprovedHost(String requestedHost)
            throws UnknownHostException {
        if (requestedHost == null
                || !host.equals(
                        requestedHost.toLowerCase(Locale.ROOT))) {
            throw new UnknownHostException(
                    "HTTP Connector 拒绝解析未审批主机");
        }
    }
}
