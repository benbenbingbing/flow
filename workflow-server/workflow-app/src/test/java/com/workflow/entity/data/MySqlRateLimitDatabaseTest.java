package com.workflow.entity.data;

import com.workflow.admin.auth.infrastructure.config.LoginThrottleProperties;
import com.workflow.admin.auth.application.LoginThrottleService;
import com.workflow.admin.auth.infrastructure.persistence.mapper.LoginThrottleMapper;
import com.workflow.core.database.jdbc.JdbcLockedRow;
import com.workflow.core.error.RateLimitExceededException;
import com.workflow.embed.application.port.EmbedTrafficControlPort.RuntimeRequestClass;
import com.workflow.embed.infrastructure.config.EmbedProperties;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.infrastructure.persistence.adapter.MyBatisEmbedTrafficControlAdapter;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedTrafficControlMapper;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationRateLimitMapper;
import com.workflow.openapi.application.security.IntegrationRateLimitService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static com.workflow.entity.data.MySqlCoordinatedOperationDatabaseTest.transactional;
import static com.workflow.entity.data.MySqlIdempotentInsertDatabaseTest.concurrent;
import com.workflow.entity.data.MySqlIdempotentInsertDatabaseTest.Harness;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import static org.junit.jupiter.api.Assertions.*;

/** MySQL 真实并发与 Spring 事务，保持登录、开放接口和 Embed 原有的不同拒绝计费规则。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlRateLimitDatabaseTest {
    private static final Instant NOW = Instant.parse("2026-09-22T03:00:42Z");

    @Test
    void openApiAcceptsOnlyTheLimitAndRollsBackRejectedIncrements() throws Exception {
        try (var f = new Fixture()) {
            rateTable(f); var h = new Harness(f, IntegrationRateLimitMapper.class); var clock = new TestClock();
            var service = openApi(h, clock);
            var admitted = concurrent(6, index -> {
                try { service.acquire("client", "same", 3); return true; }
                catch (RateLimitExceededException error) { assertEquals(18L, error.getRetryAfterSeconds()); return false; }
            });
            assertEquals(3, admitted.stream().filter(Boolean.TRUE::equals).count());
            assertEquals(3, h.jdbc.queryForObject("SELECT request_count FROM integration_rate_limit_bucket", Integer.class));
            service.acquire("address", "same", 1);
            clock.now = NOW.plusSeconds(18);
            service.acquire("client", "same", 3);
            assertEquals(3, h.jdbc.queryForObject("SELECT COUNT(*) FROM integration_rate_limit_bucket", Integer.class));
            assertThrows(RateLimitExceededException.class, () -> service.acquire("zero", "same", 0));
            assertEquals(3, h.jdbc.queryForObject("SELECT COUNT(*) FROM integration_rate_limit_bucket", Integer.class));
        }
    }

    @Test
    void loginThresholdAndWindowBoundaryUseTheNewFailureCount() throws Exception {
        try (var f = new Fixture()) {
            loginTable(f); var h = new Harness(f, LoginThrottleMapper.class); var clock = new TestClock();
            var properties = new LoginThrottleProperties(); properties.setAccountMaxFailures(3);
            properties.setClientMaxFailures(30); properties.setWindowSeconds(60); properties.setBlockSeconds(60);
            var service = login(h, clock, properties);
            service.recordFailure(" Admin ", "203.0.113.8");
            service.recordFailure("admin", "203.0.113.8");
            assertDoesNotThrow(() -> service.assertAllowed("ADMIN", "203.0.113.8"));
            service.recordFailure("admin", "203.0.113.8");
            assertEquals(60, assertThrows(RateLimitExceededException.class,
                    () -> service.assertAllowed("admin", "203.0.113.8")).getRetryAfterSeconds());
            clock.now = NOW.plusSeconds(60);
            assertDoesNotThrow(() -> service.assertAllowed("admin", "203.0.113.8"));
            service.recordFailure("admin", "203.0.113.8");
            assertEquals(4, h.jdbc.queryForObject("SELECT failure_count FROM auth_login_throttle WHERE throttle_key LIKE 'a:%'", Integer.class));
            clock.now = NOW.plusSeconds(61);
            service.recordFailure("admin", "203.0.113.8");
            assertEquals(1, h.jdbc.queryForObject("SELECT failure_count FROM auth_login_throttle WHERE throttle_key LIKE 'a:%'", Integer.class));
            // 旧窗口过期会重置计数，但不提前解除仍然有效的封禁。
            assertEquals(59, assertThrows(RateLimitExceededException.class,
                    () -> service.assertAllowed("admin", "203.0.113.8")).getRetryAfterSeconds());
            service.recordSuccess(" ADMIN ");
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM auth_login_throttle", Integer.class));
            assertDoesNotThrow(() -> service.assertAllowed("admin", "203.0.113.8"));
        }
    }

    @Test
    void concurrentLoginFailuresDoNotLoseEitherDimensionAndRollbackTogether() throws Exception {
        try (var f = new Fixture()) {
            loginTable(f); var h = new Harness(f, LoginThrottleMapper.class);
            var service = login(h, new TestClock(), new LoginThrottleProperties());
            concurrent(6, index -> { service.recordFailure("admin", "203.0.113.8"); return true; });
            assertEquals(List.of(6, 6), h.jdbc.queryForList("SELECT failure_count FROM auth_login_throttle ORDER BY throttle_key", Integer.class));
            h.tx.executeWithoutResult(status -> { service.recordFailure("admin", "203.0.113.8"); status.setRollbackOnly(); });
            assertEquals(List.of(6, 6), h.jdbc.queryForList("SELECT failure_count FROM auth_login_throttle ORDER BY throttle_key", Integer.class));
        }
    }

    @Test
    void embedLaunchKeepsRejectedAttemptCountsAndDoesNotResetAnExistingBucket() throws Exception {
        try (var f = new Fixture()) {
            embedTables(f); var h = new Harness(f, EmbedTrafficControlMapper.class); seedGrant(h);
            var adapter = embed(h, new EmbedProperties());
            var admitted = concurrent(6, index -> {
                try { adapter.consumeLaunch("app", "grant"); return true; }
                catch (EmbedException error) { assertEquals(429, error.getStatus()); assertEquals(18L, error.getRetryAfterSeconds()); return false; }
            });
            assertEquals(3, admitted.stream().filter(Boolean.TRUE::equals).count());
            assertEquals(6, h.jdbc.queryForObject("SELECT request_count FROM integration_rate_limit_bucket", Integer.class));
        }
    }

    @Test
    void embedExchangeStandaloneBucketsSerializeAndKeepRejections() throws Exception {
        try (var f = new Fixture()) {
            rateTable(f); var h = new Harness(f, EmbedTrafficControlMapper.class);
            var properties = new EmbedProperties(); properties.setExchangeLaunchLimitPerMinute(3); properties.setExchangeAddressLimitPerMinute(100);
            var adapter = embed(h, properties);
            var admitted = concurrent(6, index -> {
                try { adapter.consumeExchange("launch", "203.0.113.8"); return true; }
                catch (EmbedException error) { assertEquals(429, error.getStatus()); return false; }
            });
            assertEquals(3, admitted.stream().filter(Boolean.TRUE::equals).count());
            assertEquals(List.of(3, 6), h.jdbc.queryForList("SELECT request_count FROM integration_rate_limit_bucket ORDER BY request_count", Integer.class));
        }
    }

    @Test
    void embedLeaseLimitRetainsQuotaRejectionsButPersistenceFailureRollsBackAllCounters() throws Exception {
        try (var f = new Fixture()) {
            embedTables(f); var h = new Harness(f, EmbedTrafficControlMapper.class); seedGrant(h);
            var properties = new EmbedProperties(); properties.setRuntimeSessionLimitPerMinute(100);
            var adapter = embed(h, properties);
            var leases = concurrent(6, index -> {
                try { return adapter.acquireRuntime("app", "grant", "session", RuntimeRequestClass.READ); }
                catch (EmbedException error) { assertEquals(429, error.getStatus()); return null; }
            });
            assertEquals(2, leases.stream().filter(Objects::nonNull).count());
            assertEquals(List.of(6, 6), h.jdbc.queryForList("SELECT request_count FROM integration_rate_limit_bucket ORDER BY bucket_key", Integer.class));
            adapter.releaseRuntime(leases.stream().filter(Objects::nonNull).findFirst().orElseThrow());
            properties.setRuntimeRequestLeaseSeconds(-1);
            var error = assertThrows(EmbedException.class, () -> adapter.acquireRuntime("app", "grant", "session", RuntimeRequestClass.READ));
            assertEquals(503, error.getStatus());
            assertEquals(List.of(6, 6), h.jdbc.queryForList("SELECT request_count FROM integration_rate_limit_bucket ORDER BY bucket_key", Integer.class));
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM integration_api_request_lease", Integer.class));
            properties.setRuntimeRequestLeaseSeconds(60);
            assertNotNull(adapter.acquireRuntime("app", "grant", "session", RuntimeRequestClass.READ));
            assertEquals(List.of(7, 7), h.jdbc.queryForList("SELECT request_count FROM integration_rate_limit_bucket ORDER BY bucket_key", Integer.class));
        }
    }

    private static JdbcLockedRow locks(Harness h) { return new JdbcLockedRow(h.jdbc, DatabaseDialects.insert(DatabaseVendor.MYSQL)); }

    /** 注入现有包内测试时钟构造器，保持生产入口的时钟来源不变。 */
    private static IntegrationRateLimitService openApi(Harness h, Clock clock) throws Exception {
        var constructor = IntegrationRateLimitService.class.getDeclaredConstructor(IntegrationRateLimitMapper.class, Clock.class, JdbcLockedRow.class);
        constructor.setAccessible(true);
        return transactional(constructor.newInstance(h.mapper(IntegrationRateLimitMapper.class), clock, locks(h)), h);
    }

    private static LoginThrottleService login(Harness h, Clock clock, LoginThrottleProperties properties) throws Exception {
        var constructor = LoginThrottleService.class.getDeclaredConstructor(LoginThrottleMapper.class, LoginThrottleProperties.class, Clock.class, JdbcLockedRow.class);
        constructor.setAccessible(true);
        return transactional(constructor.newInstance(h.mapper(LoginThrottleMapper.class), properties, clock, locks(h)), h);
    }

    private static MyBatisEmbedTrafficControlAdapter embed(Harness h, EmbedProperties properties) {
        return transactional(new MyBatisEmbedTrafficControlAdapter(h.mapper(EmbedTrafficControlMapper.class), value -> {
            try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
            catch (Exception error) { throw new IllegalStateException(error); }
        }, properties, new TestClock(), locks(h)), h);
    }

    private static void rateTable(Fixture f) {
        f.table("integration_rate_limit_bucket", "bucket_key CHAR(64), window_epoch BIGINT, request_count INT NOT NULL, "
                + "create_time DATETIME(6), update_time DATETIME(6), PRIMARY KEY(bucket_key,window_epoch)");
    }

    private static void loginTable(Fixture f) {
        f.table("auth_login_throttle", "throttle_key VARCHAR(80) PRIMARY KEY, failure_count INT NOT NULL, window_started_at DATETIME(6) NOT NULL, "
                + "blocked_until DATETIME(6), update_time DATETIME(6) NOT NULL");
    }

    private static void embedTables(Fixture f) {
        rateTable(f);
        f.table("integration_application", "id VARCHAR(64) PRIMARY KEY, status VARCHAR(30), expires_at DATETIME(6), version BIGINT");
        f.table("embed_application_grant", "id VARCHAR(64) PRIMARY KEY, application_id VARCHAR(64), status VARCHAR(30), expires_at DATETIME(6), "
                + "launch_limit_per_minute INT, runtime_limit_per_minute INT, max_concurrency INT");
        f.table("integration_api_request_lease", "lease_id VARCHAR(64) PRIMARY KEY, application_id VARCHAR(64), scope_key VARCHAR(255), "
                + "expires_at DATETIME(6), create_time DATETIME(6), update_time DATETIME(6), CHECK(expires_at > create_time)");
    }

    private static void seedGrant(Harness h) {
        h.jdbc.update("INSERT INTO integration_application VALUES ('app','ACTIVE',NULL,1)");
        h.jdbc.update("INSERT INTO embed_application_grant VALUES ('grant','app','ACTIVE',NULL,3,100,2)");
    }

    private static final class TestClock extends Clock {
        volatile Instant now = NOW;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
