package com.workflow.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RestEndpointPolicyTest {

    @Test
    void resolvesExactlyOnceAndReturnsImmutableApprovedAddresses()
            throws Exception {
        WorkflowHttpProperties properties = new WorkflowHttpProperties();
        AtomicInteger resolutions = new AtomicInteger();
        InetAddress publicAddress =
                InetAddress.getByName("93.184.216.34");
        RestEndpointPolicy policy = new RestEndpointPolicy(
                properties,
                host -> {
                    resolutions.incrementAndGet();
                    return new InetAddress[]{publicAddress};
                });

        ApprovedEndpoint approved = policy.validateAndResolve(
                URI.create("https://erp.example.com/orders"),
                Set.of("erp.example.com"),
                false);

        assertEquals(1, resolutions.get());
        assertEquals(
                publicAddress,
                approved.addresses().get(0));
        assertThrows(
                UnsupportedOperationException.class,
                () -> approved.addresses().add(publicAddress));
    }

    @Test
    void rejectsMixedPublicAndPrivateDnsAnswers() throws Exception {
        WorkflowHttpProperties properties = new WorkflowHttpProperties();
        RestEndpointPolicy policy = new RestEndpointPolicy(
                properties,
                host -> new InetAddress[]{
                        InetAddress.getByName("93.184.216.34"),
                        InetAddress.getByName("10.0.0.8")
                });

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validateAndResolve(
                        URI.create("https://erp.example.com/orders"),
                        Set.of("erp.example.com"),
                        false));
    }

    @Test
    void pinnedResolverCannotBeChangedByLaterDnsAnswers()
            throws Exception {
        InetAddress approvedAddress =
                InetAddress.getByName("93.184.216.34");
        ApprovedEndpoint approved = new ApprovedEndpoint(
                URI.create("https://erp.example.com/orders"),
                "erp.example.com",
                java.util.List.of(approvedAddress));
        PinnedDnsResolver resolver = new PinnedDnsResolver(approved);

        assertEquals(
                approvedAddress,
                resolver.resolve("erp.example.com")[0]);
        InetAddress[] callerCopy =
                resolver.resolve("erp.example.com");
        callerCopy[0] = InetAddress.getByName("127.0.0.1");
        assertEquals(
                approvedAddress,
                resolver.resolve("erp.example.com")[0]);
        assertThrows(
                java.net.UnknownHostException.class,
                () -> resolver.resolve("metadata.google.internal"));
    }

    @Test
    void rejectsIpv4PrivateReservedMetadataAndDocumentationRanges()
            throws Exception {
        for (String address : new String[]{
                "0.0.0.1",
                "10.1.2.3",
                "100.64.0.1",
                "127.0.0.1",
                "169.254.169.254",
                "172.16.0.1",
                "192.0.0.1",
                "192.0.2.1",
                "192.88.99.1",
                "192.168.1.1",
                "198.18.0.1",
                "198.51.100.1",
                "203.0.113.1",
                "224.0.0.1",
                "240.0.0.1",
                "255.255.255.255"}) {
            assertRejected(address);
        }
    }

    @Test
    void rejectsIpv6LocalTransitionDocumentationAndMulticastRanges()
            throws Exception {
        for (String address : new String[]{
                "::",
                "::1",
                "::c000:201",
                "64:ff9b::a00:1",
                "100::1",
                "2001::1",
                "2001:db8::1",
                "2002:a00:1::",
                "3fff::1",
                "fc00::1",
                "fe80::1",
                "ff02::1"}) {
            assertRejected(address);
        }
    }

    @Test
    void rejectsUserInfoUnapprovedHostsAndRedirectSchemes() {
        WorkflowHttpProperties properties = new WorkflowHttpProperties();
        RestEndpointPolicy policy = new RestEndpointPolicy(
                properties,
                host -> new InetAddress[0]);

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validateAndResolve(
                        URI.create("https://user@erp.example.com/orders"),
                        Set.of("erp.example.com"),
                        false));
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validateAndResolve(
                        URI.create("https://other.example.com/orders"),
                        Set.of("erp.example.com"),
                        false));
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validateAndResolve(
                        URI.create("ftp://erp.example.com/orders"),
                        Set.of("erp.example.com"),
                        false));
    }

    private void assertRejected(String address) throws Exception {
        WorkflowHttpProperties properties = new WorkflowHttpProperties();
        InetAddress resolved = InetAddress.getByName(address);
        RestEndpointPolicy policy = new RestEndpointPolicy(
                properties,
                host -> new InetAddress[]{resolved});
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validateAndResolve(
                        URI.create("https://erp.example.com/orders"),
                        Set.of("erp.example.com"),
                        false),
                address);
    }
}
