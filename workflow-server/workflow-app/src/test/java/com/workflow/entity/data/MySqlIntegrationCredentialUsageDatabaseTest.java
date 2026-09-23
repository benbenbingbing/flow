package com.workflow.entity.data;

import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.Harness;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationCredentialMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/** 真实单语句更新验收：只使用随机隔离表，验证应用/凭据状态边界与调用方事务归属。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlIntegrationCredentialUsageDatabaseTest {
    private static final LocalDateTime BEFORE = LocalDateTime.of(2026, 9, 22, 8, 0, 0, 123456000);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 23, 9, 30, 0, 654321000);
    private static final String SPECIAL_CLIENT = "client' OR 1=1 --\\中文%_";

    @Test
    void usageUpdatesAllAndOnlyActiveCredentialsOfTheMatchingActiveApplication() {
        try (var fixture = new Fixture()) {
            tables(fixture);
            var harness = new Harness(fixture, IntegrationCredentialMapper.class);
            seed(harness.jdbc);
            var mapper = harness.mapper(IntegrationCredentialMapper.class);
            Map<String, LocalDateTime> expected = usageTimes(harness.jdbc);

            assertEquals(2, mapper.markActiveUsedByClientId("target-client", NOW));
            expected.put("target-a", NOW);
            expected.put("target-b", NOW);
            assertEquals(expected, usageTimes(harness.jdbc));

            assertEquals(0, mapper.markActiveUsedByClientId("disabled-client", NOW));
            assertEquals(0, mapper.markActiveUsedByClientId("null-status-client", NOW));
            assertEquals(0, mapper.markActiveUsedByClientId("missing-client", NOW));
            assertEquals(0, mapper.markActiveUsedByClientId("missing' OR 1=1 --", NOW));
            // 数据库中存在 client_id 为 NULL 的启用应用，仍不能被 SQL 的等值 NULL 绑定命中。
            assertEquals(0, mapper.markActiveUsedByClientId(null, NOW));
            assertEquals(expected, usageTimes(harness.jdbc));

            assertEquals(1, mapper.markActiveUsedByClientId(SPECIAL_CLIENT, NOW));
            expected.put("special", NOW);
            assertEquals(expected, usageTimes(harness.jdbc));
        }
    }

    @Test
    void nullTimestampIsAssignedAndUsageChangesStayInsideTheCallingTransaction() {
        try (var fixture = new Fixture()) {
            tables(fixture);
            var harness = new Harness(fixture, IntegrationCredentialMapper.class);
            seed(harness.jdbc);
            var mapper = harness.mapper(IntegrationCredentialMapper.class);
            Map<String, LocalDateTime> before = usageTimes(harness.jdbc);
            Map<String, LocalDateTime> cleared = new LinkedHashMap<>(before);
            cleared.put("target-a", null);
            cleared.put("target-b", null);
            // 使用另一 DataSource 身份取得独立连接，检查未提交更新不会泄漏给其他调用方。
            var observer = new JdbcTemplate(fixture.isolatedDataSource());

            harness.tx.executeWithoutResult(status -> {
                assertEquals(2, mapper.markActiveUsedByClientId("target-client", null));
                assertEquals(cleared, usageTimes(harness.jdbc));
                assertEquals(before, usageTimes(observer));
                status.setRollbackOnly();
            });
            assertEquals(before, usageTimes(harness.jdbc));

            assertEquals(2, mapper.markActiveUsedByClientId("target-client", null));
            assertEquals(cleared, usageTimes(harness.jdbc));
            assertEquals(cleared, usageTimes(observer));
        }
    }

    /** 本测试仅执行标注 UPDATE 和显式 JDBC 投影，因此夹具只需该语句涉及的列。 */
    private static void tables(Fixture fixture) {
        fixture.table("integration_application", "id VARCHAR(64) PRIMARY KEY, client_id VARCHAR(255), status VARCHAR(32)");
        fixture.table("integration_application_credential", "id VARCHAR(64) PRIMARY KEY, application_id VARCHAR(64), "
                + "status VARCHAR(32), last_used_at DATETIME(6)");
    }

    private static void seed(JdbcTemplate jdbc) {
        application(jdbc, "target", "target-client", "ACTIVE");
        application(jdbc, "disabled", "disabled-client", "DISABLED");
        application(jdbc, "other", "other-client", "ACTIVE");
        application(jdbc, "special", SPECIAL_CLIENT, "ACTIVE");
        application(jdbc, "null-client", null, "ACTIVE");
        application(jdbc, "null-status", "null-status-client", null);
        credential(jdbc, "target-a", "target", "ACTIVE");
        credential(jdbc, "target-b", "target", "ACTIVE");
        credential(jdbc, "revoked", "target", "REVOKED");
        credential(jdbc, "null-credential-status", "target", null);
        credential(jdbc, "disabled", "disabled", "ACTIVE");
        credential(jdbc, "other", "other", "ACTIVE");
        credential(jdbc, "special", "special", "ACTIVE");
        credential(jdbc, "null-client", "null-client", "ACTIVE");
        credential(jdbc, "null-application-status", "null-status", "ACTIVE");
        credential(jdbc, "orphan", "missing-application", "ACTIVE");
        credential(jdbc, "null-application", null, "ACTIVE");
    }

    private static void application(JdbcTemplate jdbc, String id, String clientId, String status) {
        jdbc.update("INSERT INTO integration_application(id,client_id,status) VALUES (?,?,?)", id, clientId, status);
    }

    private static void credential(JdbcTemplate jdbc, String id, String applicationId, String status) {
        jdbc.update("INSERT INTO integration_application_credential(id,application_id,status,last_used_at) VALUES (?,?,?,?)",
                id, applicationId, status, BEFORE);
    }

    private static Map<String, LocalDateTime> usageTimes(JdbcTemplate jdbc) {
        return jdbc.query("SELECT id,last_used_at FROM integration_application_credential ORDER BY id", rows -> {
            Map<String, LocalDateTime> result = new LinkedHashMap<>();
            while (rows.next()) result.put(rows.getString("id"), rows.getObject("last_used_at", LocalDateTime.class));
            return result;
        });
    }
}
