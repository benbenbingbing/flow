package com.workflow.http.transport;

import com.workflow.http.policy.ApprovedEndpoint;
import java.net.InetAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PinnedDnsResolverTest {
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

}
