package com.workflow.openapi.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IpNetworkTest {

    @Test
    void matchesIpv4AndIpv6Prefixes() {
        IpNetwork ipv4 = IpNetwork.parse("10.20.0.0/16");
        IpNetwork ipv6 = IpNetwork.parse("2001:db8::/32");

        assertTrue(ipv4.contains("10.20.255.254"));
        assertFalse(ipv4.contains("10.21.0.1"));
        assertTrue(ipv6.contains("2001:db8:abcd::1"));
        assertFalse(ipv6.contains("2001:db9::1"));
        assertFalse(ipv6.contains("10.20.0.1"));
    }

    @Test
    void rejectsHostnamesAndAmbiguousIpv4Forms() {
        assertThrows(
                IllegalArgumentException.class,
                () -> IpNetwork.parse("localhost/32"));
        assertThrows(
                IllegalArgumentException.class,
                () -> IpNetwork.parse("127.1/32"));
        assertThrows(
                IllegalArgumentException.class,
                () -> IpNetwork.parse("010.0.0.1/32"));
        assertThrows(
                IllegalArgumentException.class,
                () -> IpNetwork.parse("10.0.0.0/33"));
    }
}
