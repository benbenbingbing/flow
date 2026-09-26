package com.workflow.http.transport;

import com.workflow.http.api.HttpTransportUnavailableException;

import com.workflow.http.api.HttpTransportRequest;
import com.workflow.http.config.WorkflowHttpProperties;
import com.workflow.http.policy.ApprovedEndpoint;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 使用可控时钟验证容量、策略隔离与冷却，不依赖网络抖动或长时间等待。 */
class PinnedHttpResourcesTest {
    private final URI uri = URI.create("https://api.example.test/data");

    @Test
    void globalAndTargetConcurrencyAreBoundedAndRecoverAfterRelease() throws Exception {
        var properties = new WorkflowHttpProperties();
        properties.setMaxConcurrentRequests(2);
        properties.setMaxConcurrentPerTarget(1);
        try (var resources = new PinnedHttpResources(properties)) {
            try (var first = resources.enter(uri)) {
                assertThrows(HttpTransportUnavailableException.class, () -> resources.enter(uri));
                try (var second = resources.enter(URI.create("https://other.example.test/data"))) {
                    assertThrows(HttpTransportUnavailableException.class,
                            () -> resources.enter(URI.create("https://third.example.test/data")));
                }
            }
            try (var recovered = resources.enter(uri)) { recovered.result(false); }
        }
    }

    @Test
    void failingTargetOpensCircuitAndAllowsOnlyOneRecoveryProbe() throws Exception {
        var now = new AtomicLong(1);
        var properties = new WorkflowHttpProperties();
        properties.setCircuitFailureThreshold(2);
        properties.setCircuitOpenSeconds(1);
        try (var resources = new PinnedHttpResources(properties, now::get)) {
            for (int i = 0; i < 2; i++) {
                try (var failed = resources.enter(uri)) { failed.result(true); }
            }
            assertThrows(HttpTransportUnavailableException.class, () -> resources.enter(uri));
            // 一个失败目标不会阻止其他目标继续执行。
            try (var healthy = resources.enter(URI.create("https://other.example.test/data"))) { healthy.result(false); }
            now.addAndGet(TimeUnit.SECONDS.toNanos(2));
            try (var probe = resources.enter(uri)) {
                assertThrows(HttpTransportUnavailableException.class, () -> resources.enter(uri));
                probe.result(false);
            }
            try (var recovered = resources.enter(uri)) { recovered.result(false); }
        }
    }

    @Test
    void reuseRequiresMatchingAddressesAndPolicyAndIdlePoolsAreReclaimed() throws Exception {
        var now = new AtomicLong(1);
        var properties = new WorkflowHttpProperties();
        properties.setMaxCachedPools(4);
        properties.setPoolIdleSeconds(1);
        var request = request(Set.of("api.example.test"));
        var endpoint = endpoint("127.0.0.1");
        try (var resources = new PinnedHttpResources(properties, now::get)) {
            Object original;
            try (var first = resources.lease(endpoint, request, true)) { original = first.client(); }
            try (var same = resources.lease(endpoint, request, true)) { assertSame(original, same.client()); }
            try (var addressChanged = resources.lease(endpoint("127.0.0.2"), request, true)) {
                assertNotSame(original, addressChanged.client());
            }
            try (var policyChanged = resources.lease(endpoint, request(Set.of("*.example.test")), true)) {
                assertNotSame(original, policyChanged.client());
            }
            try (var strictPolicy = resources.lease(endpoint, request, false)) {
                assertNotSame(original, strictPolicy.client());
            }
            assertEquals(4, resources.poolCount());
            now.addAndGet(TimeUnit.SECONDS.toNanos(2));
            resources.cleanUp();
            assertEquals(0, resources.poolCount());
            try (var refreshed = resources.lease(endpoint, request, true)) { assertNotSame(original, refreshed.client()); }
        }
    }

    @Test
    void activePoolIsNotEvictedAndIdlePoolCanBeReplacedAtCapacity() throws Exception {
        var properties = new WorkflowHttpProperties();
        properties.setMaxCachedPools(1);
        try (var resources = new PinnedHttpResources(properties)) {
            try (var first = resources.lease(endpoint("127.0.0.1"), request(Set.of("api.example.test")), true)) {
                assertThrows(HttpTransportUnavailableException.class,
                        () -> resources.lease(endpoint("127.0.0.2"), request(Set.of("api.example.test")), true));
                assertEquals(1, resources.poolCount());
            }
            try (var replacement = resources.lease(endpoint("127.0.0.2"), request(Set.of("api.example.test")), true)) {
                assertEquals(1, resources.poolCount());
            }
        }
    }

    private ApprovedEndpoint endpoint(String ip) throws Exception {
        return new ApprovedEndpoint(uri, uri.getHost(), List.of(InetAddress.getByName(ip)));
    }
    private HttpTransportRequest request(Set<String> hosts) {
        return new HttpTransportRequest("GET", uri, Map.of(), null, 2000, hosts, 1024, false);
    }
}
