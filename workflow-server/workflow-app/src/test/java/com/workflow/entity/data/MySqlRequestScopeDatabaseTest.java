package com.workflow.entity.data;

import com.workflow.embed.infrastructure.persistence.mapper.EmbedTrafficControlMapper;
import com.workflow.integration.database.api.DatabaseScalarValues;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApiRequestLeaseMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.security.OpenApiConcurrencyLeaseService;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.Harness;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
import static com.workflow.entity.data.MySqlIdempotentInsertDatabaseTest.concurrent;

/** MySQL 空范围继续兼容旧节点；固定占位范围与 Embed 范围的计数、释放和竞争在随机表验证。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlRequestScopeDatabaseTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 22, 12, 0);
    private static final String APPLICATION_SCOPE = "open-api-application-v1";
    private static final String EMBED_SCOPE = EmbedTrafficControlMapper.RUNTIME_SCOPE_PREFIX + "grant";

    @Test void countsAndExpiryKeepApplicationScopesSeparateAndMysqlNewRowsVisibleToOldQueries() {
        try (var f = new Fixture()) {
            leaseTable(f); var h = new Harness(f, IntegrationApiRequestLeaseMapper.class, EmbedTrafficControlMapper.class);
            var mapper = h.mapper(IntegrationApiRequestLeaseMapper.class);
            assertEquals(1, mapper.insert("new", "app", NOW.plusMinutes(1), NOW));
            seed(h, "old", "app", "", NOW.plusMinutes(1));
            seed(h, "encoded", "app", APPLICATION_SCOPE, NOW.plusMinutes(1));
            seed(h, "expired-old", "app", "", NOW);
            seed(h, "expired-encoded", "app", APPLICATION_SCOPE, NOW);
            seed(h, "embed", "app", EMBED_SCOPE, NOW.plusMinutes(1));
            seed(h, "expired-embed", "app", EMBED_SCOPE, NOW);
            seed(h, "other-app", "other", "", NOW.plusMinutes(1));
            assertEquals("", h.jdbc.queryForObject("SELECT scope_key FROM integration_api_request_lease WHERE lease_id='new'", String.class));
            assertEquals(2, h.jdbc.queryForObject("SELECT COUNT(*) FROM integration_api_request_lease WHERE application_id='app' AND scope_key='' AND expires_at>?", Integer.class, NOW));
            assertEquals(3, mapper.countActive("app", NOW));
            assertEquals(0, mapper.countActive("app' OR 1=1 --", NOW));
            assertEquals(1, h.mapper(EmbedTrafficControlMapper.class).countActiveRuntimeLeases("app", EMBED_SCOPE, NOW));
            assertEquals(2, mapper.deleteExpiredForApplication("app", NOW));
            assertEquals(0, mapper.deleteExpiredForApplication("app", NOW));
            assertEquals(List.of("expired-embed"), h.jdbc.queryForList("SELECT lease_id FROM integration_api_request_lease WHERE expires_at<=?", String.class, NOW));
            assertEquals(1, mapper.countActive("other", NOW));
        }
    }

    @Test void releasesCannotCrossScopeAndBothEncodingsJoinTheCallingTransaction() {
        try (var f = new Fixture()) {
            leaseTable(f); var h = new Harness(f, IntegrationApiRequestLeaseMapper.class, EmbedTrafficControlMapper.class);
            var open = h.mapper(IntegrationApiRequestLeaseMapper.class); var embed = h.mapper(EmbedTrafficControlMapper.class);
            seed(h, "old", "app", "", NOW); seed(h, "encoded", "app", APPLICATION_SCOPE, NOW);
            seed(h, "embed", "app", EMBED_SCOPE, NOW); seed(h, "foreign", "app", "another-runtime-scope", NOW);
            assertEquals(0, embed.releaseRuntimeLease("old")); assertEquals(0, embed.releaseRuntimeLease("encoded"));
            assertEquals(0, open.release("embed")); assertEquals(0, open.release("foreign"));
            assertEquals(0, embed.releaseRuntimeLease("foreign"));
            assertEquals(0, open.release("old' OR 1=1 --"));
            h.tx.executeWithoutResult(status -> {
                assertEquals(1, open.release("old")); assertEquals(1, open.release("encoded"));
                assertEquals(1, embed.releaseRuntimeLease("embed"));
                assertEquals(1, open.insert("rolled-back", "app", NOW.plusMinutes(1), NOW));
                status.setRollbackOnly();
            });
            assertEquals(4, h.jdbc.queryForObject("SELECT COUNT(*) FROM integration_api_request_lease", Integer.class));
            assertEquals(1, open.release("old")); assertEquals(1, open.release("encoded"));
            assertEquals(1, embed.releaseRuntimeLease("embed"));
            assertEquals(List.of("foreign"), h.jdbc.queryForList("SELECT lease_id FROM integration_api_request_lease", String.class));
        }
    }

    @Test void sixConcurrentRequestsCountLegacyAndEncodedLeasesUnderTheApplicationLock() throws Exception {
        try (var f = new Fixture()) {
            leaseTable(f); f.table("integration_application", "id VARCHAR(64) PRIMARY KEY");
            var h = new Harness(f, IntegrationApiRequestLeaseMapper.class, IntegrationApplicationMapper.class);
            h.jdbc.update("INSERT INTO integration_application VALUES ('app')");
            seed(h, "legacy", "app", "", NOW.plusMinutes(1)); seed(h, "encoded", "app", APPLICATION_SCOPE, NOW.plusMinutes(1));
            seed(h, "embed", "app", EMBED_SCOPE, NOW.plusMinutes(1));
            var constructor = OpenApiConcurrencyLeaseService.class.getDeclaredConstructor(
                    IntegrationApplicationMapper.class, IntegrationApiRequestLeaseMapper.class, Clock.class);
            constructor.setAccessible(true);
            var target = constructor.newInstance(h.mapper(IntegrationApplicationMapper.class), h.mapper(IntegrationApiRequestLeaseMapper.class),
                    Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
            var service = h.transactional(target);
            var admitted = concurrent(6, ignored -> {
                try { return service.acquire("app", 3); }
                catch (OpenApiConcurrencyLeaseService.ConcurrencyRejectedException denied) { return null; }
            });
            assertEquals(1, admitted.stream().filter(Objects::nonNull).count());
            assertEquals(3, h.mapper(IntegrationApiRequestLeaseMapper.class).countActive("app", NOW));
            var lease = admitted.stream().filter(Objects::nonNull).findFirst().orElseThrow();
            service.release(lease); assertEquals(2, h.mapper(IntegrationApiRequestLeaseMapper.class).countActive("app", NOW));
            assertNotNull(service.acquire("app", 3));
            assertThrows(OpenApiConcurrencyLeaseService.ConcurrencyRejectedException.class, () -> service.acquire("missing", 3));
            assertEquals(4, h.jdbc.queryForObject("SELECT COUNT(*) FROM integration_api_request_lease", Integer.class));
            assertEquals("", DatabaseScalarValues.nonNullEmptyText("MYSQL", APPLICATION_SCOPE));
            assertThrows(IllegalArgumentException.class, () -> DatabaseScalarValues.nonNullEmptyText("MYSQL", " "));
            assertThrows(IllegalStateException.class, () -> DatabaseScalarValues.nonNullEmptyText(null, APPLICATION_SCOPE));
        }
    }

    private static void leaseTable(Fixture f) {
        f.table("integration_api_request_lease", "lease_id VARCHAR(64) PRIMARY KEY,application_id VARCHAR(64) NOT NULL,scope_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,expires_at DATETIME(6),create_time DATETIME(6),update_time DATETIME(6),KEY(application_id,scope_key,expires_at)");
    }

    private static void seed(Harness h, String id, String app, String scope, LocalDateTime expires) {
        h.jdbc.update("INSERT INTO integration_api_request_lease VALUES (?,?,?,?,?,?)", id, app, scope, expires, NOW, NOW);
    }
}
