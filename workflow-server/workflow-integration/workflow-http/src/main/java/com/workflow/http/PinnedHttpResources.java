package com.workflow.http;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * 出站请求的有界资源与目标故障隔离。锁只保护计数和池生命周期，不覆盖 DNS 或网络 IO。
 * 目标并发按 authority 共享；连接复用还必须匹配本次审批的 IP 集合与完整访问策略。
 */
final class PinnedHttpResources implements AutoCloseable {
    private final WorkflowHttpProperties properties;
    private final LongSupplier clock;
    private final int maxConcurrent;
    private final int perTarget;
    private final int maxPools;
    private final long idleNanos;
    private final long ttlNanos;
    private final Map<String, Target> targets = new LinkedHashMap<>(16, .75f, true);
    private final Map<PoolKey, Pool> pools = new LinkedHashMap<>(16, .75f, true);
    private int active;
    private boolean closed;

    PinnedHttpResources(WorkflowHttpProperties properties) { this(properties, System::nanoTime); }

    PinnedHttpResources(WorkflowHttpProperties properties, LongSupplier clock) {
        this.properties = properties;
        this.clock = clock;
        maxConcurrent = bound(properties.getMaxConcurrentRequests(), 1, 256);
        perTarget = Math.min(maxConcurrent, bound(properties.getMaxConcurrentPerTarget(), 1, 32));
        maxPools = bound(properties.getMaxCachedPools(), 1, 128);
        idleNanos = TimeUnit.SECONDS.toNanos(bound(properties.getPoolIdleSeconds(), 1, 600));
        ttlNanos = TimeUnit.SECONDS.toNanos(bound(properties.getConnectionTtlSeconds(), 1, 3600));
    }

    /** 满载立即拒绝；不额外积累等待线程。DNS 慢调用也占用该目标的在途配额。 */
    synchronized Admission enter(URI uri) throws UnavailableException {
        if (closed || active >= maxConcurrent) throw new UnavailableException("HTTP 全局并发已达上限或已关闭");
        String key = targetKey(uri);
        Target target = targets.get(key);
        long now = clock.getAsLong();
        if (target == null) {
            // 限制高基数目标的内存占用；仍在熔断期的状态不能被淘汰后绕过保护。
            if (targets.size() >= maxPools * 2) {
                var iterator = targets.values().iterator();
                while (iterator.hasNext()) {
                    Target candidate = iterator.next();
                    if (candidate.active == 0 && candidate.openUntil <= now) { iterator.remove(); break; }
                }
                if (targets.size() >= maxPools * 2) throw new UnavailableException("HTTP 目标状态容量已达上限");
            }
            target = new Target();
            targets.put(key, target);
        }
        if (target.active >= perTarget) throw new UnavailableException("HTTP 目标并发已达上限");
        boolean probe = target.openUntil != 0;
        if (probe && (now < target.openUntil || target.active > 0)) {
            throw new UnavailableException("HTTP 目标暂时熔断");
        }
        target.active++;
        active++;
        return new Admission(target, probe);
    }

    /** 只在每次 validateAndResolve 成功后调用，禁止直接用池缓存代替目标审批。 */
    synchronized Lease lease(ApprovedEndpoint endpoint, HttpTransportRequest request, boolean privateAllowed)
            throws UnavailableException {
        if (closed) throw new UnavailableException("HTTP 连接池已关闭");
        PoolKey key = new PoolKey(targetKey(endpoint.uri()),
                endpoint.addresses().stream().map(java.net.InetAddress::getHostAddress).distinct().sorted().toList(),
                request.allowedHosts().stream().map(host -> host.trim().toLowerCase(Locale.ROOT)).distinct().sorted().toList(),
                privateAllowed);
        cleanUp();
        Pool pool = pools.get(key);
        if (pool == null) {
            if (pools.size() >= maxPools) {
                var iterator = pools.values().iterator();
                while (iterator.hasNext()) {
                    Pool candidate = iterator.next();
                    if (candidate.active == 0) { iterator.remove(); candidate.close(); break; }
                }
                if (pools.size() >= maxPools) throw new UnavailableException("HTTP 连接池容量已达上限");
            }
            var manager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setDnsResolver(new PinnedDnsResolver(endpoint))
                    .setMaxConnTotal(perTarget).setMaxConnPerRoute(perTarget)
                    .setDefaultConnectionConfig(ConnectionConfig.custom()
                            .setConnectTimeout(Timeout.ofSeconds(bound(properties.getConnectTimeoutSeconds(), 1, 30)))
                            .setSocketTimeout(Timeout.ofSeconds(bound(properties.getMaxRequestTimeoutSeconds(), 1, 120)))
                            .setTimeToLive(TimeValue.ofNanoseconds(ttlNanos)).build())
                    .build();
            var client = HttpClients.custom().setConnectionManager(manager)
                    .disableRedirectHandling().disableAutomaticRetries()
                    .disableCookieManagement().disableAuthCaching().build();
            pool = new Pool(client, manager, clock.getAsLong());
            pools.put(key, pool);
        }
        pool.active++;
        return new Lease(pool);
    }

    /** 单一定时任务清理所有池，不为每个缓存客户端创建后台线程。活动请求持有租约，不被淘汰。 */
    synchronized void cleanUp() {
        long now = clock.getAsLong();
        var iterator = pools.values().iterator();
        while (iterator.hasNext()) {
            Pool pool = iterator.next();
            pool.manager.closeExpired();
            pool.manager.closeIdle(TimeValue.ofNanoseconds(idleNanos));
            if (pool.active == 0 && (now - pool.lastUsed >= idleNanos || now - pool.created >= ttlNanos)) {
                iterator.remove();
                pool.close();
            }
        }
        targets.values().removeIf(target -> target.active == 0 && now >= target.openUntil
                && now - target.lastUsed >= idleNanos);
    }

    @Override public synchronized void close() {
        closed = true;
        pools.values().forEach(Pool::close);
        pools.clear();
        targets.clear();
    }

    synchronized int poolCount() { return pools.size(); }

    private static String targetKey(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        int port = uri.getPort() >= 0 ? uri.getPort() : ("https".equals(scheme) ? 443 : 80);
        return scheme + "://" + String.valueOf(uri.getHost()).toLowerCase(Locale.ROOT) + ":" + port;
    }

    private static int bound(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    final class Admission implements AutoCloseable {
        private final Target target;
        private final boolean probe;
        private boolean finished;
        private Boolean failed;
        private Admission(Target target, boolean probe) { this.target = target; this.probe = probe; }
        void result(boolean failed) { this.failed = failed; }
        @Override public void close() {
            synchronized (PinnedHttpResources.this) {
                if (finished) return;
                finished = true;
                target.active--;
                active--;
                long now = clock.getAsLong();
                target.lastUsed = now;
                if (Boolean.TRUE.equals(failed)) {
                    target.failures++;
                    if (probe || target.failures >= bound(properties.getCircuitFailureThreshold(), 1, 100)) {
                        target.openUntil = now + TimeUnit.SECONDS.toNanos(bound(properties.getCircuitOpenSeconds(), 1, 300));
                    }
                } else if (Boolean.FALSE.equals(failed)) {
                    // 熔断前已经在途的成功响应不能提前解除熔断，只有冷却后的单次探测可以。
                    if (probe || target.openUntil == 0) { target.failures = 0; target.openUntil = 0; }
                }
            }
        }
    }

    final class Lease implements AutoCloseable {
        private final Pool pool;
        private boolean released;
        private Lease(Pool pool) { this.pool = pool; }
        CloseableHttpClient client() { return pool.client; }
        @Override public void close() {
            synchronized (PinnedHttpResources.this) {
                if (released) return;
                released = true;
                pool.active--;
                pool.lastUsed = clock.getAsLong();
            }
        }
    }

    /** 本地限流和熔断拒绝不得触发重试风暴，也不计入远端失败次数。 */
    static final class UnavailableException extends IOException {
        UnavailableException(String message) { super(message); }
    }
    private record PoolKey(String target, List<String> addresses, List<String> allowedHosts, boolean privateAllowed) {}
    private static final class Target { int active; int failures; long openUntil; long lastUsed; }
    private static final class Pool {
        final CloseableHttpClient client;
        final PoolingHttpClientConnectionManager manager;
        final long created;
        long lastUsed;
        int active;
        Pool(CloseableHttpClient client, PoolingHttpClientConnectionManager manager, long now) {
            this.client = client; this.manager = manager; this.created = now; this.lastUsed = now;
        }
        void close() { client.close(CloseMode.IMMEDIATE); }
    }
}
