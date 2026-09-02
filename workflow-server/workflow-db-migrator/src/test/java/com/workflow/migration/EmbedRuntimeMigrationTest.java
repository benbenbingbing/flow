package com.workflow.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Embed Runtime schema against real MySQL semantics that H2 cannot
 * faithfully model, especially JSON checks, string FK collations, and cleanup order.
 */
abstract class AbstractEmbedRuntimeMigrationTest {

    private static final int MYSQL_LOCK_WAIT_TIMEOUT = 1205;
    private static final int STATEMENT_TIMEOUT_SECONDS = 3;
    private static final int CONCURRENCY_TIMEOUT_SECONDS = 8;
    private static final String FORM_UNIQUE_FIELD_SENTINEL_HASH =
            sha256("FORM_UNIQUE_FIELD_SENTINEL_V1");

    @BeforeEach
    void cleanDatabase() {
        flyway().clean();
    }

    @Test
    void freshDatabaseCreatesTheEmbedRuntimeSchema() throws Exception {
        Flyway flyway = migrateEmbedSchema();

        assertSchemaIsCurrent(flyway);
        assertEquals(10, countRows("""
                SELECT COUNT(*)
                  FROM flyway_schema_history
                 WHERE success = 1
                   AND version IN (
                       '62', '63', '64', '65', '66', '67', '68', '69', '70', '71')
                """));
        assertEquals(1, countRows("""
                SELECT COUNT(*)
                  FROM sys_menu
                 WHERE id = 'user_manual_embed_integration_001'
                   AND parent_id = 'user_manual_dir_001'
                   AND path = '/manual/embed-integration'
                   AND component = 'manual/EmbedIntegrationManual'
                   AND perm = 'user-manual:embed-integration:view'
                   AND sort = 4
                """));
        assertEquals(1, countRows("""
                SELECT COUNT(*)
                  FROM sys_menu
                 WHERE id = 'user_manual_interface_service_001'
                   AND sort = 5
                """));
        assertEquals(0, countRows("""
                SELECT COUNT(*)
                  FROM sys_role_menu parent_grant
                  LEFT JOIN sys_role_menu manual_grant
                    ON manual_grant.role_id = parent_grant.role_id
                   AND manual_grant.menu_id = 'user_manual_embed_integration_001'
                 WHERE parent_grant.menu_id = 'user_manual_dir_001'
                   AND manual_grant.id IS NULL
                """));
        for (String table : Set.of(
                "embed_view",
                "embed_view_release",
                "embed_identity_provider",
                "embed_application_grant",
                "embed_allowed_origin",
                "embed_external_identity_binding",
                "embed_assertion_replay",
                "embed_launch",
                "embed_session",
                "embed_session_counter",
                "embed_operation_receipt")) {
            assertTrue(tableExists(table), "missing Embed table: " + table);
        }

        assertTrue(indexExists("embed_view", "uk_embed_view_key"));
        assertTrue(indexExists("embed_view_release", "uk_embed_view_release_revision"));
        assertTrue(indexExists("embed_application_grant", "uk_embed_grant_application_view"));
        assertTrue(indexExists("embed_identity_provider", "uk_embed_identity_provider_issuer_ns"));
        assertTrue(indexExists("embed_external_identity_binding", "uk_embed_binding_subject"));
        assertTrue(indexExists("embed_launch", "uk_embed_launch_code_digest"));
        assertTrue(indexExists("embed_launch", "uk_embed_launch_consumed_session"));
        assertTrue(indexExists("embed_launch", "idx_embed_launch_cleanup"));
        assertTrue(indexExists("embed_session", "uk_embed_session_token_digest"));
        assertTrue(indexExists("embed_session", "uk_embed_session_launch"));
        assertTrue(indexExists("embed_session", "idx_embed_session_counter_reconcile"));
        assertTrue(indexExists("embed_session", "idx_embed_session_terminal_cleanup"));
        assertTrue(indexExists("embed_operation_receipt", "uk_embed_receipt_idempotency"));
        assertTrue(indexExists("embed_operation_receipt", "idx_embed_receipt_cleanup"));
        assertTrue(indexExists(
                "integration_api_request_lease", "idx_integration_api_lease_scope"));
        assertEquals(
                "utf8mb4_unicode_ci",
                columnCollation("integration_api_request_lease", "scope_key"));
        assertEquals(
                "",
                columnDefault("integration_api_request_lease", "scope_key"));

        // V074 统一排序规则后，父列及所有实体外键子列必须继续严格匹配。
        String applicationIdCollation = columnCollation("integration_application", "id");
        assertEquals("utf8mb4_unicode_ci", applicationIdCollation);
        for (String table : Set.of(
                "embed_application_grant",
                "embed_external_identity_binding",
                "embed_launch",
                "embed_session",
                "embed_operation_receipt")) {
            assertEquals(applicationIdCollation, columnCollation(table, "application_id"));
        }

        // 旧库中继承 0900 的 sys_user.id 也必须被 V074 收敛到统一规则。
        String flowUserIdCollation = columnCollation("sys_user", "id");
        assertEquals("utf8mb4_unicode_ci", flowUserIdCollation);
        for (String table : Set.of(
                "embed_external_identity_binding",
                "embed_launch",
                "embed_session",
                "embed_session_counter")) {
            assertEquals(flowUserIdCollation, columnCollation(table, "flow_user_id"));
        }

        assertForeignKey("embed_view_release", "view_id", "embed_view", "id");
        assertForeignKey("embed_application_grant", "application_id", "integration_application", "id");
        assertForeignKey("embed_application_grant", "view_id", "embed_view", "id");
        assertForeignKey("embed_application_grant", "identity_provider_id", "embed_identity_provider", "id");
        assertForeignKey("embed_external_identity_binding", "flow_user_id", "sys_user", "id");
        assertForeignKey("embed_launch", "identity_binding_id", "embed_external_identity_binding", "id");
        assertForeignKey("embed_session", "launch_id", "embed_launch", "id");
        assertForeignKey("embed_session_counter", "grant_id", "embed_application_grant", "id");
        assertForeignKey("embed_session_counter", "flow_user_id", "sys_user", "id");
        assertForeignKey("embed_operation_receipt", "application_id", "integration_application", "id");

        // This reverse pointer must remain FK-free so the Launch can be claimed before Session insert.
        assertFalse(hasForeignKey("embed_launch", "consumed_session_id"));
        assertFalse(hasForeignKey("embed_operation_receipt", "idempotency_record_id"));
        assertEquals(5, countRows("""
                SELECT COUNT(*)
                  FROM sys_menu
                 WHERE id LIKE 'embed_perm_%'
                   AND perm LIKE 'system:embed:%'
                   AND parent_id = 'embed_management_menu_001'
                """));
        assertEquals(1, countRows("""
                SELECT COUNT(*)
                  FROM sys_menu
                 WHERE id = 'embed_management_menu_001'
                   AND path = '/system/embed-management'
                   AND component = 'system/EmbedManagement'
                   AND perm = 'system:embed:view'
                """));
        assertEquals(5, countRows("""
                SELECT COUNT(*)
                  FROM sys_role_menu
                 WHERE role_id = '1'
                   AND menu_id LIKE 'embed_perm_%'
                """));
        assertEquals(1, countRows("""
                SELECT COUNT(*)
                  FROM sys_role_menu
                 WHERE role_id = '1'
                   AND menu_id = 'embed_management_menu_001'
                """));
    }

    @Test
    void databaseEnforcesEmbedChecksAndCleanupDirection() throws Exception {
        migrateEmbedSchema();
        seedParents();

        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO embed_view (
                  id, view_key, name, surface_type, status, draft_config_json,
                  draft_revision, lock_version, security_version, create_by, update_by
                ) VALUES (
                  'invalid-view', 'invalid-view', 'Invalid', 'LIST', 'DRAFT',
                  'not-json', 1, 1, 1, 'embed-flow-user', 'embed-flow-user'
                )
                """));

        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO embed_session_counter (
                  grant_id, flow_user_id, active_count, lock_version
                ) VALUES ('embed-grant', 'embed-flow-user', -1, 0)
                """));

        // issuer=NULL 也必须保持 namespace 唯一，不能依赖 SELECT 后 INSERT 的竞态检查。
        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO embed_identity_provider (
                  id, name, type, status, subject_namespace,
                  audiences_json, algorithms_json,
                  clock_skew_seconds, max_assertion_lifetime_seconds,
                  key_version, lock_version, security_version, create_by, update_by
                ) VALUES (
                  'duplicate-trusted-provider', 'Duplicate trusted provider',
                  'TRUSTED_EXTERNAL_ID', 'ACTIVE', 'embed-test',
                  JSON_ARRAY(), JSON_ARRAY(), 30, 60,
                  1, 1, 1, 'embed-flow-user', 'embed-flow-user'
                )
                """));

        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO embed_application_grant (
                  id, application_id, view_id, identity_provider_id, status,
                  trusted_subject_assertion, revision_mode, capability_ceiling_json,
                  max_active_sessions_per_user, max_session_seconds,
                  launch_limit_per_minute, runtime_limit_per_minute, max_concurrency,
                  lock_version, security_version, create_by, update_by
                ) VALUES (
                  'orphan-grant', 'missing-application', 'embed-view', 'embed-provider',
                  'ACTIVE', 0, 'FOLLOW_ACTIVE', JSON_ARRAY(), 1, 60, 1, 1, 1,
                  1, 1, 'embed-flow-user', 'embed-flow-user'
                )
                """));

        insertConsumedLaunch("embed-launch", "embed-session");
        assertThrows(SQLException.class, () -> insertSession(
                "embed-session", "embed-launch", "ACTIVE", 1, null));

        insertSession("embed-session", "embed-launch", "ACTIVE", 0, null);
        execute("""
                INSERT INTO embed_session_counter (
                  grant_id, flow_user_id, active_count, lock_version
                ) VALUES ('embed-grant', 'embed-flow-user', 1, 0)
                """);
        insertReceipt("embed-receipt-1", "idempotency-1");
        assertThrows(SQLException.class,
                () -> insertReceipt("embed-receipt-2", "idempotency-1"));

        // Session owns the real FK. The terminal session must be removed before its Launch.
        assertThrows(SQLException.class,
                () -> execute("DELETE FROM embed_launch WHERE id = 'embed-launch'"));
        execute("DELETE FROM embed_session WHERE id = 'embed-session'");
        execute("DELETE FROM embed_launch WHERE id = 'embed-launch'");

        // 旧 Open API insert 不提供 scope_key 时仍落入空 scope，Embed 租约则可按 Grant 隔离。
        execute("""
                INSERT INTO integration_api_request_lease (
                  lease_id, application_id, expires_at
                ) VALUES (
                  'legacy-open-lease', 'embed-application',
                  TIMESTAMPADD(MINUTE, 1, CURRENT_TIMESTAMP(6))
                )
                """);
        execute("""
                INSERT INTO integration_api_request_lease (
                  lease_id, application_id, scope_key, expires_at
                ) VALUES (
                  'embed-runtime-lease', 'embed-application',
                  'embed-runtime-grant-v1:embed-grant',
                  TIMESTAMPADD(MINUTE, 1, CURRENT_TIMESTAMP(6))
                )
                """);
        assertEquals(1, countRows("""
                SELECT COUNT(*)
                  FROM integration_api_request_lease
                 WHERE application_id = 'embed-application'
                   AND scope_key = ''
                """));
        assertEquals(1, countRows("""
                SELECT COUNT(*)
                  FROM integration_api_request_lease
                 WHERE application_id = 'embed-application'
                   AND scope_key = 'embed-runtime-grant-v1:embed-grant'
                """));
    }

    /**
     * 验证生产 gate 算法在 RC/RR 下把同一实体字段的条件唯一写串行化。
     *
     * <p>测试使用与 {@code EntityFormUniqueValueGateRepository} 相同的哈希、排序、
     * {@code INSERT IGNORE + SELECT FOR UPDATE} 顺序，并分别覆盖 gate 首次建立和
     * 已存在两种路径。第二连接必须获得 MySQL 1205 锁等待超时，才能证明
     * 它确实进入了数据库等待，而不是因线程调度造成假阳性。</p>
     */
    @Test
    void formUniqueFieldSentinelSerializesConditionalSwapAtRcAndRr()
            throws Exception {
        migrateEmbedSchema();
        for (int isolation : List.of(
                Connection.TRANSACTION_READ_COMMITTED,
                Connection.TRANSACTION_REPEATABLE_READ)) {
            execute("DELETE FROM entity_form_unique_value_gate");
            // cold path：第一个事务会首次创建 sentinel/value gate。
            assertFieldSentinelBarrier(isolation, sha256("A"), sha256("B"));
            // warm path：反向值顺序仍必须在权威扫描前经过共享 sentinel。
            assertFieldSentinelBarrier(isolation, sha256("B"), sha256("A"));
        }
    }

    /**
     * 验证 Launch 的条件消费在 RC/RR 下都只允许一个 Session 获胜。
     *
     * <p>首事务执行与 {@code EmbedSessionExchangeMapper.consumeLaunch} 相同的条件更新，
     * 并在提交前插入 Session。并发事务必须在同一 Launch 行上获得 MySQL 1205，首事务
     * 提交后的重试则只能更新 0 行。这样同时证明了真实锁等待、一次性状态条件和
     * Session/Launch 唯一约束，而不是依赖线程先后顺序推断结果。</p>
     */
    @Test
    void oneTimeLaunchExchangeIsLinearizedAtRcAndRr() throws Exception {
        migrateEmbedSchema();
        seedParents();

        for (int isolation : mysqlIsolationLevels()) {
            deleteRuntimeRows();
            String suffix = isolationSuffix(isolation);
            String launchId = "exchange-launch-" + suffix;
            String sessionId = "exchange-session-" + suffix;
            insertIssuedLaunch(launchId, false);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            CountDownLatch contenderReady = new CountDownLatch(1);
            Future<Integer> contender = null;
            try (Connection winner = transactionalConnection(isolation)) {
                assertEquals(1, consumeIssuedLaunch(winner, launchId, sessionId));
                insertRuntimeSession(
                        winner, sessionId, launchId, "ACTIVE", 0, null);

                contender = submitLockProbe(executor, contenderReady, isolation,
                        connection -> assertEquals(
                                1,
                                consumeIssuedLaunch(
                                        connection, launchId, "loser-session-" + suffix)));
                assertLockWaitTimeout(contenderReady, contender,
                        "concurrent exchange did not wait on the consumed Launch row");

                winner.commit();
            } finally {
                stopExecutor(executor, contender);
            }

            try (Connection retry = transactionalConnection(isolation)) {
                assertEquals(0, consumeIssuedLaunch(
                        retry, launchId, "retry-session-" + suffix));
                retry.commit();
            }
            assertEquals(1, countRows("""
                    SELECT COUNT(*)
                      FROM embed_launch
                     WHERE id = '%s'
                       AND status = 'CONSUMED'
                       AND consumed_session_id = '%s'
                    """.formatted(launchId, sessionId)));
            assertEquals(1, countRows("""
                    SELECT COUNT(*)
                      FROM embed_session
                     WHERE launch_id = '%s'
                    """.formatted(launchId)));
        }
    }

    /**
     * 验证同一 Grant/用户的上限判断和终态 slot 释放在 RC/RR 下均为 exactly-once。
     *
     * <p>配额竞争复用生产的 guarded counter update；终止竞争复用
     * Counter -&gt; Session 全局锁序、ACTIVE/slot_released 条件更新和 Counter 扣减。
     * 每个竞争点都要求出现真实 1205，提交后再以新事务验证幂等结果。</p>
     */
    @Test
    void sessionCounterLimitAndTerminationReleaseAreExactlyOnceAtRcAndRr()
            throws Exception {
        migrateEmbedSchema();
        seedParents();

        for (int isolation : mysqlIsolationLevels()) {
            deleteRuntimeRows();
            String suffix = isolationSuffix(isolation);
            execute("""
                    INSERT INTO embed_session_counter (
                      grant_id, flow_user_id, active_count, lock_version
                    ) VALUES ('embed-grant', 'embed-flow-user', 0, 0)
                    """);

            ExecutorService quotaExecutor = Executors.newSingleThreadExecutor();
            CountDownLatch quotaContenderReady = new CountDownLatch(1);
            Future<Integer> quotaContender = null;
            try (Connection winner = transactionalConnection(isolation)) {
                assertEquals(1, incrementSessionCounter(winner, 1));
                quotaContender = submitLockProbe(
                        quotaExecutor, quotaContenderReady, isolation,
                        connection -> assertEquals(1, incrementSessionCounter(connection, 1)));
                assertLockWaitTimeout(quotaContenderReady, quotaContender,
                        "concurrent quota acquisition did not wait on the Counter row");
                winner.commit();
            } finally {
                stopExecutor(quotaExecutor, quotaContender);
            }
            try (Connection retry = transactionalConnection(isolation)) {
                assertEquals(0, incrementSessionCounter(retry, 1));
                retry.commit();
            }
            assertEquals(1, scalar("""
                    SELECT active_count
                      FROM embed_session_counter
                     WHERE grant_id = 'embed-grant'
                       AND flow_user_id = 'embed-flow-user'
                    """));

            String launchId = "terminate-launch-" + suffix;
            String sessionId = "terminate-session-" + suffix;
            insertConsumedLaunchUnique(launchId, sessionId);
            try (Connection connection = connection()) {
                insertRuntimeSession(
                        connection, sessionId, launchId, "ACTIVE", 0, null);
            }

            ExecutorService terminationExecutor = Executors.newSingleThreadExecutor();
            CountDownLatch terminationContenderReady = new CountDownLatch(1);
            Future<Integer> terminationContender = null;
            try (Connection winner = transactionalConnection(isolation)) {
                assertTrue(terminateSessionLikeProduction(winner, sessionId));
                terminationContender = submitLockProbe(
                        terminationExecutor, terminationContenderReady, isolation,
                        connection -> assertTrue(
                                terminateSessionLikeProduction(connection, sessionId)));
                assertLockWaitTimeout(terminationContenderReady, terminationContender,
                        "concurrent termination did not wait in Counter -> Session order");
                winner.commit();
            } finally {
                stopExecutor(terminationExecutor, terminationContender);
            }

            try (Connection retry = transactionalConnection(isolation)) {
                assertFalse(terminateSessionLikeProduction(retry, sessionId));
                retry.commit();
            }
            assertEquals(1, countRows("""
                    SELECT COUNT(*)
                      FROM embed_session
                     WHERE id = '%s'
                       AND status = 'LOGGED_OUT'
                       AND slot_released = 1
                       AND slot_released_at IS NOT NULL
                    """.formatted(sessionId)));
            assertEquals(0, scalar("""
                    SELECT active_count
                      FROM embed_session_counter
                     WHERE grant_id = 'embed-grant'
                       AND flow_user_id = 'embed-flow-user'
                    """));
            assertEquals(2, scalar("""
                    SELECT lock_version
                      FROM embed_session_counter
                     WHERE grant_id = 'embed-grant'
                       AND flow_user_id = 'embed-flow-user'
                    """));
        }
    }

    /**
     * 验证共享幂等表的唯一 claim、接管 fencing 和业务回执唯一性。
     *
     * <p>并发 {@code INSERT IGNORE} 必须在唯一键上发生真实锁等待；旧 Worker 的 token=1
     * 在 token=2 接管后不能完成。新 Worker 将 Receipt 插入和 SUCCEEDED 完成放在同一
     * 事务中，随后数据库唯一键继续拒绝第二张回执。</p>
     */
    @Test
    void idempotencyClaimFencingAndReceiptAreLinearizedAtRcAndRr()
            throws Exception {
        migrateEmbedSchema();
        seedParents();

        for (int isolation : mysqlIsolationLevels()) {
            deleteRuntimeRows();
            String suffix = isolationSuffix(isolation);
            String recordId = "idem-record-" + suffix;
            String idempotencyKey = "create-key-" + suffix;
            String requestHash = sha256("request-" + suffix);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            CountDownLatch contenderReady = new CountDownLatch(1);
            Future<Integer> contender = null;
            try (Connection winner = transactionalConnection(isolation)) {
                assertEquals(1, insertIdempotencyClaim(
                        winner, recordId, idempotencyKey, requestHash));
                contender = submitLockProbe(executor, contenderReady, isolation,
                        connection -> assertEquals(
                                0,
                                insertIdempotencyClaim(
                                        connection,
                                        "loser-record-" + suffix,
                                        idempotencyKey,
                                        requestHash)));
                assertLockWaitTimeout(contenderReady, contender,
                        "concurrent idempotency claim did not wait on the unique operation key");
                winner.commit();
            } finally {
                stopExecutor(executor, contender);
            }

            try (Connection retry = transactionalConnection(isolation)) {
                assertEquals(0, insertIdempotencyClaim(
                        retry, "retry-record-" + suffix, idempotencyKey, requestHash));
                retry.commit();
            }
            assertEquals(1, failIdempotencyRetryable(recordId, 1));
            assertEquals(1, reacquireIdempotency(recordId, 1));
            assertEquals(0, completeIdempotency(recordId, 1));

            String receiptId = "receipt-" + suffix;
            try (Connection business = transactionalConnection(isolation)) {
                insertOperationReceipt(business, receiptId, recordId, "record-" + suffix);
                assertEquals(1, completeIdempotency(business, recordId, 2));
                business.commit();
            }
            assertThrows(SQLException.class, () -> {
                try (Connection duplicate = connection()) {
                    insertOperationReceipt(
                            duplicate, "duplicate-receipt-" + suffix,
                            recordId, "record-" + suffix);
                }
            });
            assertEquals(1, countRows("""
                    SELECT COUNT(*)
                      FROM integration_idempotency_record
                     WHERE id = '%s'
                       AND status = 'SUCCEEDED'
                       AND fencing_token = 2
                    """.formatted(recordId)));
            assertEquals(1, countRows("""
                    SELECT COUNT(*)
                      FROM embed_operation_receipt
                     WHERE idempotency_record_id = '%s'
                    """.formatted(recordId)));
        }
    }

    /**
     * 直接执行生产 Maintenance Mapper 的有界 SQL，验证批次、保留期和 FK 清理顺序。
     *
     * <p>该测试覆盖防重放清理、ISSUED 过期、终态 Context 擦除、Receipt 在幂等记录
     * 消失前不可删除，以及 Session 必须先于 Launch 删除。每条写 SQL 都保留生产的
     * 状态条件、索引顺序、NOT EXISTS 和 LIMIT。</p>
     */
    @Test
    void maintenanceSqlIsBoundedAndHonorsReceiptAndForeignKeyOrder()
            throws Exception {
        migrateEmbedSchema();
        seedParents();

        insertAssertionReplay("expired-replay-a", true);
        insertAssertionReplay("expired-replay-b", true);
        insertAssertionReplay("expired-replay-c", true);
        insertAssertionReplay("live-replay", false);
        assertEquals(2, deleteExpiredAssertionReplays(2));
        assertEquals(1, countRows("""
                SELECT COUNT(*) FROM embed_assertion_replay
                 WHERE expires_at <= CURRENT_TIMESTAMP(6)
                """));
        assertEquals(1, deleteExpiredAssertionReplays(2));
        assertEquals(1, countRows("SELECT COUNT(*) FROM embed_assertion_replay"));

        insertIssuedLaunch("expired-launch-a", true);
        insertIssuedLaunch("expired-launch-b", true);
        insertIssuedLaunch("expired-launch-c", true);
        insertIssuedLaunch("live-launch", false);
        assertEquals(2, expireIssuedLaunches(2));
        assertEquals(1, countRows("""
                SELECT COUNT(*) FROM embed_launch
                 WHERE status = 'ISSUED' AND expires_at <= CURRENT_TIMESTAMP(6)
                """));
        assertEquals(1, expireIssuedLaunches(2));
        assertEquals(3, countRows("""
                SELECT COUNT(*) FROM embed_launch WHERE status = 'EXPIRED'
                """));

        insertTerminalSessionForMaintenance(
                "context-launch-a", "context-session-a",
                "TIMESTAMPADD(HOUR, -2, CURRENT_TIMESTAMP(6))", false);
        insertTerminalSessionForMaintenance(
                "context-launch-b", "context-session-b",
                "TIMESTAMPADD(HOUR, -2, CURRENT_TIMESTAMP(6))", false);
        insertTerminalSessionForMaintenance(
                "context-launch-c", "context-session-c",
                "TIMESTAMPADD(HOUR, -2, CURRENT_TIMESTAMP(6))", false);
        assertEquals(2, eraseTerminalSessionContexts(2));
        assertEquals(2, countRows("""
                SELECT COUNT(*) FROM embed_session
                 WHERE context_cipher_key_version = 'embed-erased-v1'
                """));
        assertEquals(1, eraseTerminalSessionContexts(2));

        insertIdempotencyRecord("retained-idempotency", "retained-key");
        try (Connection connection = connection()) {
            insertOperationReceipt(
                    connection, "retained-receipt", "retained-idempotency", "record-a");
            insertOperationReceipt(
                    connection, "orphan-receipt", "missing-idempotency", "record-b");
        }
        execute("""
                UPDATE embed_operation_receipt
                   SET create_time = TIMESTAMPADD(DAY, -3, CURRENT_TIMESTAMP(6))
                 WHERE id IN ('retained-receipt', 'orphan-receipt')
                """);
        assertEquals(1, deleteOrphanOperationReceipts(10));
        assertEquals(1, countRows("""
                SELECT COUNT(*) FROM embed_operation_receipt
                 WHERE id = 'retained-receipt'
                """));
        execute("DELETE FROM integration_idempotency_record WHERE id = 'retained-idempotency'");
        assertEquals(1, deleteOrphanOperationReceipts(10));

        insertTerminalSessionForMaintenance(
                "cleanup-launch", "cleanup-session",
                "TIMESTAMPADD(DAY, -4, CURRENT_TIMESTAMP(6))", true);
        assertEquals(0, deleteUnreferencedTerminalLaunches(10));
        assertEquals(1, deleteTerminalSessions(1));
        assertEquals(1, deleteUnreferencedTerminalLaunches(10));
        assertEquals(0, countRows("""
                SELECT COUNT(*) FROM embed_session WHERE id = 'cleanup-session'
                """));
        assertEquals(0, countRows("""
                SELECT COUNT(*) FROM embed_launch WHERE id = 'cleanup-launch'
                """));
    }

    private void assertFieldSentinelBarrier(
            int isolation,
            String firstValueHash,
            String secondValueHash) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch secondReady = new CountDownLatch(1);
        Future<Integer> second = null;
        try (Connection first = connection()) {
            first.setTransactionIsolation(isolation);
            first.setAutoCommit(false);
            insertAndLockUniqueGates(first, firstValueHash);

            second = executor.submit(() -> {
                try (Connection connection = connection()) {
                    connection.setTransactionIsolation(isolation);
                    connection.setAutoCommit(false);
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("SET SESSION innodb_lock_wait_timeout = 1");
                    }
                    secondReady.countDown();
                    try {
                        insertAndLockUniqueGates(connection, secondValueHash);
                        return 0;
                    } catch (SQLException error) {
                        connection.rollback();
                        return error.getErrorCode();
                    }
                }
            });

            assertTrue(secondReady.await(5, TimeUnit.SECONDS));
            assertEquals(
                    1205,
                    second.get(5, TimeUnit.SECONDS),
                    "second transaction did not wait on the shared field gate");

            first.commit();
            // 首事务释放后，同一生产锁序必须可立即完成，避免把死锁误判为串行化。
            try (Connection retry = connection()) {
                retry.setTransactionIsolation(isolation);
                retry.setAutoCommit(false);
                insertAndLockUniqueGates(retry, secondValueHash);
                retry.commit();
            }
        } finally {
            if (second != null && !second.isDone()) {
                second.cancel(true);
            }
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private void insertAndLockUniqueGates(Connection connection, String valueHash)
            throws Exception {
        for (String hash : List.of(FORM_UNIQUE_FIELD_SENTINEL_HASH, valueHash)
                .stream().distinct().sorted().toList()) {
            try (var insert = connection.prepareStatement("""
                    INSERT IGNORE INTO entity_form_unique_value_gate (
                      scope_key, value_hash
                    ) VALUES ('ENTITY:project:name', ?)
                    """)) {
                insert.setQueryTimeout(3);
                insert.setString(1, hash);
                insert.executeUpdate();
            }
            try (var lock = connection.prepareStatement("""
                    SELECT value_hash
                      FROM entity_form_unique_value_gate
                     WHERE scope_key = 'ENTITY:project:name'
                       AND value_hash = ?
                     FOR UPDATE
                    """)) {
                lock.setQueryTimeout(3);
                lock.setString(1, hash);
                try (ResultSet result = lock.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(hash, result.getString(1));
                }
            }
        }
    }

    private List<Integer> mysqlIsolationLevels() {
        return List.of(
                Connection.TRANSACTION_READ_COMMITTED,
                Connection.TRANSACTION_REPEATABLE_READ);
    }

    private String isolationSuffix(int isolation) {
        return isolation == Connection.TRANSACTION_READ_COMMITTED ? "rc" : "rr";
    }

    /**
     * 新建带 statement/lock timeout 的事务连接。网络超时由本机测试 JDBC URL 的
     * {@code socketTimeout} 兜底，避免 MySQL 故障时测试线程永久阻塞。
     */
    private Connection transactionalConnection(int isolation) throws Exception {
        Connection connection = connection();
        try {
            connection.setTransactionIsolation(isolation);
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
                statement.execute("SET SESSION innodb_lock_wait_timeout = 1");
            }
            return connection;
        } catch (Exception error) {
            connection.close();
            throw error;
        }
    }

    private Future<Integer> submitLockProbe(
            ExecutorService executor,
            CountDownLatch ready,
            int isolation,
            SqlTransactionOperation operation) {
        return executor.submit(() -> {
            try (Connection contender = transactionalConnection(isolation)) {
                ready.countDown();
                try {
                    operation.execute(contender);
                    contender.commit();
                    return 0;
                } catch (SQLException error) {
                    rollbackQuietly(contender);
                    return error.getErrorCode();
                }
            }
        });
    }

    private void assertLockWaitTimeout(
            CountDownLatch ready,
            Future<Integer> contender,
            String message) throws Exception {
        assertTrue(ready.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "concurrent transaction did not start");
        assertEquals(
                MYSQL_LOCK_WAIT_TIMEOUT,
                contender.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                message);
    }

    private void stopExecutor(ExecutorService executor, Future<?> future)
            throws Exception {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(
                CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "concurrency test executor leaked a worker thread");
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // 原始锁等待异常才是验收证据，rollback 的次生异常不能覆盖它。
        }
    }

    private int consumeIssuedLaunch(
            Connection connection,
            String launchId,
            String sessionId) throws Exception {
        try (PreparedStatement statement = prepared(connection, """
                UPDATE embed_launch
                   SET status = 'CONSUMED',
                       consumed_at = CURRENT_TIMESTAMP(6),
                       consumed_session_id = ?,
                       update_time = CURRENT_TIMESTAMP(6)
                 WHERE id = ?
                   AND status = 'ISSUED'
                   AND expires_at > CURRENT_TIMESTAMP(6)
                   AND launch_code_digest = ?
                   AND channel_id = 'channel-1'
                   AND parent_origin = 'https://portal.example.test'
                """)) {
            statement.setString(1, sessionId);
            statement.setString(2, launchId);
            statement.setString(3, sha256("launch-code:" + launchId));
            return statement.executeUpdate();
        }
    }

    private int incrementSessionCounter(Connection connection, int limit)
            throws Exception {
        try (PreparedStatement statement = prepared(connection, """
                UPDATE embed_session_counter
                   SET active_count = active_count + 1,
                       lock_version = lock_version + 1,
                       update_time = CURRENT_TIMESTAMP(6)
                 WHERE grant_id = 'embed-grant'
                   AND flow_user_id = 'embed-flow-user'
                   AND active_count < ?
                """)) {
            statement.setInt(1, limit);
            return statement.executeUpdate();
        }
    }

    /** 复刻 Session Persistence Adapter 的 Counter -&gt; Session 终止锁序。 */
    private boolean terminateSessionLikeProduction(
            Connection connection,
            String sessionId) throws Exception {
        String candidateStatus;
        try (PreparedStatement statement = prepared(connection, """
                SELECT status
                  FROM embed_session
                 WHERE id = ?
                 LIMIT 1
                """)) {
            statement.setString(1, sessionId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return false;
                }
                candidateStatus = result.getString(1);
            }
        }
        if (!"ACTIVE".equals(candidateStatus)) {
            return false;
        }

        try (PreparedStatement counter = prepared(connection, """
                SELECT active_count
                  FROM embed_session_counter
                 WHERE grant_id = 'embed-grant'
                   AND flow_user_id = 'embed-flow-user'
                 FOR UPDATE
                """)) {
            try (ResultSet result = counter.executeQuery()) {
                assertTrue(result.next(), "session counter disappeared during termination");
            }
        }
        try (PreparedStatement session = prepared(connection, """
                SELECT status, slot_released
                  FROM embed_session
                 WHERE id = ?
                 FOR UPDATE
                """)) {
            session.setString(1, sessionId);
            try (ResultSet result = session.executeQuery()) {
                assertTrue(result.next(), "session disappeared during termination");
                if (!"ACTIVE".equals(result.getString("status"))
                        || result.getInt("slot_released") != 0) {
                    return false;
                }
            }
        }
        try (PreparedStatement terminate = prepared(connection, """
                UPDATE embed_session
                   SET status = 'LOGGED_OUT',
                       slot_released = 1,
                       slot_released_at = CURRENT_TIMESTAMP(6),
                       revoked_at = NULL,
                       revoke_reason = NULL,
                       update_time = CURRENT_TIMESTAMP(6)
                 WHERE id = ?
                   AND status = 'ACTIVE'
                   AND slot_released = 0
                """)) {
            terminate.setString(1, sessionId);
            if (terminate.executeUpdate() != 1) {
                return false;
            }
        }
        try (PreparedStatement decrement = prepared(connection, """
                UPDATE embed_session_counter
                   SET active_count = CASE
                         WHEN active_count > 0 THEN active_count - 1 ELSE 0 END,
                       lock_version = lock_version + 1,
                       update_time = CURRENT_TIMESTAMP(6)
                 WHERE grant_id = 'embed-grant'
                   AND flow_user_id = 'embed-flow-user'
                """)) {
            assertEquals(1, decrement.executeUpdate());
        }
        return true;
    }

    private int insertIdempotencyClaim(
            Connection connection,
            String id,
            String idempotencyKey,
            String requestHash) throws Exception {
        try (PreparedStatement statement = prepared(connection, """
                INSERT IGNORE INTO integration_idempotency_record (
                  id, application_id, operation, idempotency_key,
                  request_hash, status, fencing_token,
                  processing_started_at, expires_at, create_time, update_time
                ) VALUES (
                  ?, 'embed-application', 'EMBED_RECORD_CREATE', ?,
                  ?, 'PROCESSING', 1,
                  CURRENT_TIMESTAMP(6), TIMESTAMPADD(DAY, 7, CURRENT_TIMESTAMP(6)),
                  CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                )
                """)) {
            statement.setString(1, id);
            statement.setString(2, idempotencyKey);
            statement.setString(3, requestHash);
            return statement.executeUpdate();
        }
    }

    private int failIdempotencyRetryable(String id, long fencingToken)
            throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = prepared(connection, """
                     UPDATE integration_idempotency_record
                        SET status = 'FAILED_RETRYABLE',
                            resource_type = NULL,
                            resource_id = NULL,
                            response_status = NULL,
                            response_body = NULL,
                            update_time = CURRENT_TIMESTAMP(6)
                      WHERE id = ?
                        AND status = 'PROCESSING'
                        AND fencing_token = ?
                     """)) {
            statement.setString(1, id);
            statement.setLong(2, fencingToken);
            return statement.executeUpdate();
        }
    }

    private int reacquireIdempotency(String id, long expectedFencingToken)
            throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = prepared(connection, """
                     UPDATE integration_idempotency_record
                        SET status = 'PROCESSING',
                            fencing_token = fencing_token + 1,
                            processing_started_at = CURRENT_TIMESTAMP(6),
                            expires_at = TIMESTAMPADD(DAY, 7, CURRENT_TIMESTAMP(6)),
                            resource_type = NULL,
                            resource_id = NULL,
                            response_status = NULL,
                            response_body = NULL,
                            update_time = CURRENT_TIMESTAMP(6)
                      WHERE id = ?
                        AND fencing_token = ?
                        AND (
                          status = 'FAILED_RETRYABLE'
                          OR (status = 'PROCESSING'
                              AND processing_started_at
                                  < TIMESTAMPADD(SECOND, -120, CURRENT_TIMESTAMP(6)))
                        )
                     """)) {
            statement.setString(1, id);
            statement.setLong(2, expectedFencingToken);
            return statement.executeUpdate();
        }
    }

    private int completeIdempotency(String id, long fencingToken)
            throws Exception {
        try (Connection connection = connection()) {
            return completeIdempotency(connection, id, fencingToken);
        }
    }

    private int completeIdempotency(
            Connection connection,
            String id,
            long fencingToken) throws Exception {
        try (PreparedStatement statement = prepared(connection, """
                UPDATE integration_idempotency_record
                   SET status = 'SUCCEEDED',
                       resource_type = 'RECORD',
                       resource_id = 'record-result',
                       response_status = 201,
                       response_body = JSON_OBJECT('recordId', 'record-result'),
                       update_time = CURRENT_TIMESTAMP(6)
                 WHERE id = ?
                   AND status = 'PROCESSING'
                   AND fencing_token = ?
                """)) {
            statement.setString(1, id);
            statement.setLong(2, fencingToken);
            return statement.executeUpdate();
        }
    }

    private void insertOperationReceipt(
            Connection connection,
            String receiptId,
            String idempotencyRecordId,
            String targetId) throws Exception {
        try (PreparedStatement statement = prepared(connection, """
                INSERT INTO embed_operation_receipt (
                  id, idempotency_record_id, application_id, operation,
                  actor_scope_digest, view_key, target_type, target_id,
                  outcome_code, record_version, result_summary_json
                ) VALUES (
                  ?, ?, 'embed-application', 'EMBED_RECORD_CREATE',
                  ?, 'embed-test-view', 'RECORD', ?,
                  'RECORD_CREATED', 0,
                  JSON_OBJECT('recordId', ?, 'recordVersion', 0)
                )
                """)) {
            statement.setString(1, receiptId);
            statement.setString(2, idempotencyRecordId);
            statement.setString(3, sha256("actor-scope"));
            statement.setString(4, targetId);
            statement.setString(5, targetId);
            statement.executeUpdate();
        }
    }

    private void insertIssuedLaunch(String launchId, boolean expired)
            throws Exception {
        String createTime = expired
                ? "TIMESTAMPADD(DAY, -2, CURRENT_TIMESTAMP(6))"
                : "CURRENT_TIMESTAMP(6)";
        String expiresAt = expired
                ? "TIMESTAMPADD(DAY, -1, CURRENT_TIMESTAMP(6))"
                : "TIMESTAMPADD(MINUTE, 5, CURRENT_TIMESTAMP(6))";
        execute("""
                INSERT INTO embed_launch (
                  id, application_id, grant_id, view_id, view_release_id,
                  identity_provider_id, provider_security_version, application_version,
                  grant_security_version, view_security_version, flow_user_id,
                  identity_binding_id, binding_version, subject_digest,
                  subject_digest_key_version, parent_origin, channel_id, entry_mode,
                  context_ciphertext, context_cipher_key_version, context_digest,
                  context_digest_key_version, ui_locale, ui_theme, launch_code_digest,
                  status, expires_at, create_time, update_time
                ) VALUES (
                  '%s', 'embed-application', 'embed-grant', 'embed-view', 'embed-release',
                  'embed-provider', 1, 0, 1, 1, 'embed-flow-user',
                  'embed-binding', 1, '%s',
                  'subject-v1', 'https://portal.example.test', 'channel-1', 'LIST',
                  JSON_OBJECT('alg', 'A256GCM', 'kid', 'context-v1',
                    'nonce', 'nonce', 'ciphertext', 'ciphertext', 'tag', 'tag'),
                  'context-v1', '%s', 'context-hmac-v1', 'zh-CN', 'light', '%s',
                  'ISSUED', %s, %s, %s
                )
                """.formatted(
                launchId,
                sha256("subject:" + launchId),
                sha256("context:" + launchId),
                sha256("launch-code:" + launchId),
                expiresAt,
                createTime,
                createTime));
    }

    private void insertConsumedLaunchUnique(String launchId, String sessionId)
            throws Exception {
        insertIssuedLaunch(launchId, false);
        try (Connection connection = connection()) {
            assertEquals(1, consumeIssuedLaunch(connection, launchId, sessionId));
        }
    }

    private void insertRuntimeSession(
            Connection connection,
            String sessionId,
            String launchId,
            String status,
            int slotReleased,
            String slotReleasedAtExpression) throws Exception {
        String releasedAt = slotReleasedAtExpression == null
                ? "NULL"
                : slotReleasedAtExpression;
        try (PreparedStatement statement = prepared(connection, """
                INSERT INTO embed_session (
                  id, session_token_digest, launch_id, application_id, grant_id,
                  view_id, view_release_id, identity_provider_id, provider_security_version,
                  flow_user_id, identity_binding_id, binding_version, parent_origin,
                  channel_id, entry_mode, parent_nonce_digest, child_nonce_digest,
                  context_ciphertext, context_cipher_key_version, context_digest,
                  context_digest_key_version, ui_locale, ui_theme, capability_snapshot_json,
                  application_version, grant_security_version, view_security_version,
                  status, slot_released, slot_released_at, issued_at, last_seen_at,
                  idle_expires_at, absolute_expires_at, create_time, update_time
                ) VALUES (
                  ?, ?, ?, 'embed-application', 'embed-grant',
                  'embed-view', 'embed-release', 'embed-provider', 1,
                  'embed-flow-user', 'embed-binding', 1, 'https://portal.example.test',
                  'channel-1', 'LIST', ?, ?,
                  JSON_OBJECT('alg', 'A256GCM', 'kid', 'context-v1',
                    'nonce', 'nonce', 'ciphertext', 'ciphertext', 'tag', 'tag'),
                  'context-v1', ?, 'context-hmac-v1', 'zh-CN', 'light', JSON_ARRAY(),
                  0, 1, 1, ?, %d, %s,
                  CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6),
                  TIMESTAMPADD(MINUTE, 1, CURRENT_TIMESTAMP(6)),
                  TIMESTAMPADD(MINUTE, 5, CURRENT_TIMESTAMP(6)),
                  CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                )
                """.formatted(slotReleased, releasedAt))) {
            statement.setString(1, sessionId);
            statement.setString(2, sha256("token:" + sessionId));
            statement.setString(3, launchId);
            statement.setString(4, sha256("parent-nonce:" + sessionId));
            statement.setString(5, sha256("child-nonce:" + sessionId));
            statement.setString(6, sha256("session-context:" + sessionId));
            statement.setString(7, status);
            statement.executeUpdate();
        }
    }

    private void insertAssertionReplay(String value, boolean expired)
            throws Exception {
        String expiresAt = expired
                ? "TIMESTAMPADD(DAY, -1, CURRENT_TIMESTAMP(6))"
                : "TIMESTAMPADD(DAY, 1, CURRENT_TIMESTAMP(6))";
        String createTime = expired
                ? "TIMESTAMPADD(DAY, -2, CURRENT_TIMESTAMP(6))"
                : "CURRENT_TIMESTAMP(6)";
        execute("""
                INSERT INTO embed_assertion_replay (
                  provider_id, jti_digest, expires_at, create_time, update_time
                ) VALUES (
                  'embed-provider', '%s', %s, %s, %s
                )
                """.formatted(sha256(value), expiresAt, createTime, createTime));
    }

    private int deleteExpiredAssertionReplays(int limit) throws Exception {
        return executeUpdate("""
                DELETE FROM embed_assertion_replay
                 WHERE expires_at <= CURRENT_TIMESTAMP(6)
                 ORDER BY expires_at, provider_id, jti_digest
                 LIMIT %d
                """.formatted(limit));
    }

    private int expireIssuedLaunches(int limit) throws Exception {
        return executeUpdate("""
                UPDATE embed_launch
                   SET status = 'EXPIRED',
                       update_time = CURRENT_TIMESTAMP(6)
                 WHERE status = 'ISSUED'
                   AND expires_at <= CURRENT_TIMESTAMP(6)
                 ORDER BY expires_at, id
                 LIMIT %d
                """.formatted(limit));
    }

    private int eraseTerminalSessionContexts(int limit) throws Exception {
        return executeUpdate("""
                UPDATE embed_session
                   SET context_ciphertext = '{}',
                       context_cipher_key_version = 'embed-erased-v1',
                       context_digest = REPEAT('0', 64),
                       context_digest_key_version = 'embed-erased-v1',
                       update_time = CURRENT_TIMESTAMP(6)
                 WHERE status IN ('LOGGED_OUT', 'EXPIRED', 'REVOKED')
                   AND slot_released = 1
                   AND slot_released_at
                       <= TIMESTAMPADD(HOUR, -1, CURRENT_TIMESTAMP(6))
                   AND context_cipher_key_version <> 'embed-erased-v1'
                 ORDER BY status, slot_released_at, id
                 LIMIT %d
                """.formatted(limit));
    }

    private int deleteOrphanOperationReceipts(int limit) throws Exception {
        return executeUpdate("""
                DELETE FROM embed_operation_receipt
                 WHERE create_time <= TIMESTAMPADD(DAY, -1, CURRENT_TIMESTAMP(6))
                   AND NOT EXISTS (
                        SELECT 1
                          FROM integration_idempotency_record i
                         WHERE i.id = embed_operation_receipt.idempotency_record_id
                   )
                 ORDER BY create_time, id
                 LIMIT %d
                """.formatted(limit));
    }

    private int deleteTerminalSessions(int limit) throws Exception {
        return executeUpdate("""
                DELETE FROM embed_session
                 WHERE status IN ('LOGGED_OUT', 'EXPIRED', 'REVOKED')
                   AND slot_released = 1
                   AND slot_released_at
                       <= TIMESTAMPADD(DAY, -1, CURRENT_TIMESTAMP(6))
                 ORDER BY status, slot_released_at, id
                 LIMIT %d
                """.formatted(limit));
    }

    private int deleteUnreferencedTerminalLaunches(int limit) throws Exception {
        return executeUpdate("""
                DELETE FROM embed_launch
                 WHERE status IN ('CONSUMED', 'EXPIRED', 'REVOKED')
                   AND update_time <= TIMESTAMPADD(DAY, -1, CURRENT_TIMESTAMP(6))
                   AND NOT EXISTS (
                        SELECT 1
                          FROM embed_session s
                         WHERE s.launch_id = embed_launch.id
                   )
                 ORDER BY status, update_time, id
                 LIMIT %d
                """.formatted(limit));
    }

    private void insertIdempotencyRecord(String id, String key) throws Exception {
        try (Connection connection = connection()) {
            assertEquals(1, insertIdempotencyClaim(
                    connection, id, key, sha256("request:" + key)));
        }
    }

    private void insertTerminalSessionForMaintenance(
            String launchId,
            String sessionId,
            String slotReleasedAtExpression,
            boolean oldLaunch) throws Exception {
        insertConsumedLaunchUnique(launchId, sessionId);
        try (Connection connection = connection()) {
            insertRuntimeSession(
                    connection, sessionId, launchId,
                    "LOGGED_OUT", 1, slotReleasedAtExpression);
        }
        if (oldLaunch) {
            execute("""
                    UPDATE embed_launch
                       SET update_time = TIMESTAMPADD(DAY, -4, CURRENT_TIMESTAMP(6))
                     WHERE id = '%s'
                    """.formatted(launchId));
        }
    }

    private void deleteRuntimeRows() throws Exception {
        execute("DELETE FROM embed_operation_receipt");
        execute("DELETE FROM integration_idempotency_record");
        execute("DELETE FROM embed_session");
        execute("DELETE FROM embed_launch");
        execute("DELETE FROM embed_session_counter");
    }

    private long scalar(String sql) throws Exception {
        return countRows(sql);
    }

    private int executeUpdate(String sql) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = prepared(connection, sql)) {
            return statement.executeUpdate();
        }
    }

    private PreparedStatement prepared(Connection connection, String sql)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
        return statement;
    }

    @FunctionalInterface
    private interface SqlTransactionOperation {
        void execute(Connection connection) throws Exception;
    }

    /**
     * Builds the minimum valid management graph used by constraint tests. The values are
     * deliberately synthetic; behavior and authorization are covered by runtime tests.
     */
    private void seedParents() throws Exception {
        execute("""
                INSERT INTO sys_user (id, username, password)
                VALUES ('embed-flow-user', 'embed-flow-user', 'test-password')
                """);
        execute("""
                INSERT INTO integration_application (
                  id, client_id, application_name, status,
                  rate_limit_per_minute, max_concurrency, allowed_source_cidrs,
                  version, created_by, updated_by
                ) VALUES (
                  'embed-application', 'embed-client', 'Embed migration test', 'ACTIVE',
                  60, 10, JSON_ARRAY(), 0, 'embed-flow-user', 'embed-flow-user'
                )
                """);
        execute("""
                INSERT INTO embed_view (
                  id, view_key, name, surface_type, status, draft_config_json,
                  draft_revision, lock_version, security_version, create_by, update_by
                ) VALUES (
                  'embed-view', 'embed-test-view', 'Embed test view', 'FORM', 'ACTIVE',
                  JSON_OBJECT(), 1, 1, 1, 'embed-flow-user', 'embed-flow-user'
                )
                """);
        execute("""
                INSERT INTO embed_view_release (
                  id, view_id, revision, surface_type, entity_code,
                  form_release_id, form_release_version,
                  entry_modes_json, capabilities_json, field_policy_json,
                  action_policy_json, context_schema_json, context_bindings_json,
                  ui_config_json, config_json, config_hash, published_by, published_at
                ) VALUES (
                  'embed-release', 'embed-view', 1, 'FORM', 'embed_entity',
                  'form-release-1', 1,
                  JSON_ARRAY('CREATE'), JSON_ARRAY(), JSON_OBJECT(),
                  JSON_OBJECT(), JSON_OBJECT(), JSON_OBJECT(),
                  JSON_OBJECT(), JSON_OBJECT(), '%s', 'embed-flow-user', CURRENT_TIMESTAMP(6)
                )
                """.formatted(hex('a')));
        execute("""
                INSERT INTO embed_identity_provider (
                  id, name, type, status, subject_namespace,
                  audiences_json, algorithms_json,
                  clock_skew_seconds, max_assertion_lifetime_seconds,
                  key_version, lock_version, security_version, create_by, update_by
                ) VALUES (
                  'embed-provider', 'Embed trusted identity', 'TRUSTED_EXTERNAL_ID', 'ACTIVE',
                  'embed-test', JSON_ARRAY(), JSON_ARRAY(),
                  30, 60, 1, 1, 1, 'embed-flow-user', 'embed-flow-user'
                )
                """);
        execute("""
                INSERT INTO embed_application_grant (
                  id, application_id, view_id, identity_provider_id, status,
                  trusted_subject_assertion, revision_mode, capability_ceiling_json,
                  max_active_sessions_per_user, max_session_seconds,
                  launch_limit_per_minute, runtime_limit_per_minute, max_concurrency,
                  lock_version, security_version, create_by, update_by
                ) VALUES (
                  'embed-grant', 'embed-application', 'embed-view', 'embed-provider', 'ACTIVE',
                  1, 'FOLLOW_ACTIVE', JSON_ARRAY(), 1, 300, 60, 600, 10,
                  1, 1, 'embed-flow-user', 'embed-flow-user'
                )
                """);
        execute("""
                INSERT INTO embed_allowed_origin (grant_id, origin)
                VALUES ('embed-grant', 'https://portal.example.test')
                """);
        execute("""
                INSERT INTO embed_external_identity_binding (
                  id, application_id, identity_provider_id, subject_digest,
                  subject_digest_key_version, subject_hint, flow_user_id, status,
                  binding_version, create_by, update_by
                ) VALUES (
                  'embed-binding', 'embed-application', 'embed-provider', '%s',
                  'subject-v1', 'us***01', 'embed-flow-user', 'ACTIVE',
                  1, 'embed-flow-user', 'embed-flow-user'
                )
                """.formatted(hex('b')));
    }

    /**
     * Launch is deliberately inserted as consumed before the Session to prove that the
     * audit pointer has no reverse FK and that only Session owns the launch FK.
     */
    private void insertConsumedLaunch(String launchId, String sessionId)
            throws Exception {
        execute("""
                INSERT INTO embed_launch (
                  id, application_id, grant_id, view_id, view_release_id,
                  identity_provider_id, provider_security_version, application_version,
                  grant_security_version, view_security_version, flow_user_id,
                  identity_binding_id, binding_version, subject_digest,
                  subject_digest_key_version, parent_origin, channel_id, entry_mode,
                  context_ciphertext, context_cipher_key_version, context_digest,
                  context_digest_key_version, ui_locale, ui_theme, launch_code_digest,
                  status, expires_at, consumed_at, consumed_session_id
                ) VALUES (
                  '%s', 'embed-application', 'embed-grant', 'embed-view', 'embed-release',
                  'embed-provider', 1, 0, 1, 1, 'embed-flow-user',
                  'embed-binding', 1, '%s',
                  'subject-v1', 'https://portal.example.test', 'channel-1', 'LIST',
                  JSON_OBJECT('alg', 'A256GCM', 'kid', 'context-v1',
                    'nonce', 'nonce', 'ciphertext', 'ciphertext', 'tag', 'tag'),
                  'context-v1', '%s', 'context-hmac-v1', 'zh-CN', 'light', '%s',
                  'CONSUMED', TIMESTAMPADD(MINUTE, 1, CURRENT_TIMESTAMP(6)),
                  CURRENT_TIMESTAMP(6), '%s'
                )
                """.formatted(
                launchId,
                hex('b'),
                hex('c'),
                hex('d'),
                sessionId));
    }

    /**
     * Inserts a Session in a requested lifecycle state so the slot-release check can be
     * exercised independently of the exchange service implementation.
     */
    private void insertSession(
            String sessionId,
            String launchId,
            String status,
            int slotReleased,
            String slotReleasedAt) throws Exception {
        String releasedAt = slotReleasedAt == null ? "NULL" : slotReleasedAt;
        execute("""
                INSERT INTO embed_session (
                  id, session_token_digest, launch_id, application_id, grant_id,
                  view_id, view_release_id, identity_provider_id, provider_security_version,
                  flow_user_id, identity_binding_id, binding_version, parent_origin,
                  channel_id, entry_mode, parent_nonce_digest, child_nonce_digest,
                  context_ciphertext, context_cipher_key_version, context_digest,
                  context_digest_key_version, ui_locale, ui_theme, capability_snapshot_json,
                  application_version, grant_security_version, view_security_version,
                  status, slot_released, slot_released_at, issued_at, last_seen_at,
                  idle_expires_at, absolute_expires_at
                ) VALUES (
                  '%s', '%s', '%s', 'embed-application', 'embed-grant',
                  'embed-view', 'embed-release', 'embed-provider', 1,
                  'embed-flow-user', 'embed-binding', 1, 'https://portal.example.test',
                  'channel-1', 'LIST', '%s', '%s',
                  JSON_OBJECT('alg', 'A256GCM', 'kid', 'context-v1',
                    'nonce', 'nonce', 'ciphertext', 'ciphertext', 'tag', 'tag'),
                  'context-v1', '%s', 'context-hmac-v1', 'zh-CN', 'light', JSON_ARRAY(),
                  0, 1, 1, '%s', %d, %s, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6),
                  TIMESTAMPADD(MINUTE, 1, CURRENT_TIMESTAMP(6)),
                  TIMESTAMPADD(MINUTE, 5, CURRENT_TIMESTAMP(6))
                )
                """.formatted(
                sessionId,
                hex('e'),
                launchId,
                hex('f'),
                hex('0'),
                hex('1'),
                hex('c'),
                status,
                slotReleased,
                releasedAt));
    }

    private void insertReceipt(String receiptId, String idempotencyRecordId)
            throws Exception {
        execute("""
                INSERT INTO embed_operation_receipt (
                  id, idempotency_record_id, application_id, operation,
                  actor_scope_digest, view_key, target_type, target_id, outcome_code,
                  record_version, result_summary_json
                ) VALUES (
                  '%s', '%s', 'embed-application', 'EMBED_RECORD_CREATE',
                  '%s', 'embed-test-view', 'RECORD', 'record-1', 'RECORD_CREATED',
                  0, JSON_OBJECT('recordId', 'record-1', 'recordVersion', 0)
                )
                """.formatted(receiptId, idempotencyRecordId, hex('2')));
    }

    /**
     * Builds the exact Flyway configuration used by both cleanup and migration. The
     * Embed contract deliberately executes every immutable migration from V001 through
     * the current version so an isolated higher-version test cannot hide history gaps.
     */
    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(
                        jdbcUrl(),
                        databaseUsername(),
                        databasePassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
    }

    /**
     * Executes the production classpath migration chain without ignore or repair rules.
     */
    private Flyway migrateEmbedSchema() {
        Flyway flyway = flyway();
        flyway.migrate();
        return flyway;
    }

    private void assertSchemaIsCurrent(Flyway flyway) throws Exception {
        assertEquals(0, flyway.info().pending().length);
        // 该契约执行完整生产迁移链；并行功能占用的新版本也必须进入历史，
        // 否则把最高版本固定在 Embed 自身的 V068 会掩盖真实 classpath 漂移。
        assertEquals("74", flyway.info().current().getVersion().getVersion());
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT version
                       FROM flyway_schema_history
                      WHERE success = 1
                      ORDER BY installed_rank DESC
                      LIMIT 1
                     """)) {
            assertTrue(result.next());
            assertEquals(flyway.info().current().getVersion().getVersion(), result.getString(1));
        }
    }

    private boolean tableExists(String table) throws Exception {
        try (Connection connection = connection();
             ResultSet result = connection.getMetaData().getTables(
                     databaseName(), null, table, new String[] {"TABLE"})) {
            return result.next();
        }
    }

    private boolean indexExists(String table, String index) throws Exception {
        try (Connection connection = connection();
             ResultSet result = connection.getMetaData().getIndexInfo(
                     databaseName(), null, table, false, false)) {
            while (result.next()) {
                if (index.equals(result.getString("INDEX_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private String columnCollation(String table, String column) throws Exception {
        try (Connection connection = connection();
             var statement = connection.prepareStatement("""
                     SELECT collation_name
                       FROM information_schema.columns
                      WHERE table_schema = ?
                        AND table_name = ?
                        AND column_name = ?
                     """)) {
            statement.setString(1, databaseName());
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "missing column: " + table + "." + column);
                return result.getString(1);
            }
        }
    }

    private String columnDefault(String table, String column) throws Exception {
        try (Connection connection = connection();
             var statement = connection.prepareStatement("""
                     SELECT column_default
                       FROM information_schema.columns
                      WHERE table_schema = ?
                        AND table_name = ?
                        AND column_name = ?
                     """)) {
            statement.setString(1, databaseName());
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "missing column: " + table + "." + column);
                return result.getString(1);
            }
        }
    }

    private void assertForeignKey(
            String table,
            String column,
            String referencedTable,
            String referencedColumn) throws Exception {
        try (Connection connection = connection();
             var statement = connection.prepareStatement("""
                     SELECT referenced_table_name, referenced_column_name
                       FROM information_schema.key_column_usage
                      WHERE table_schema = ?
                        AND table_name = ?
                        AND column_name = ?
                        AND referenced_table_name IS NOT NULL
                     """)) {
            statement.setString(1, databaseName());
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "missing FK: " + table + "." + column);
                assertEquals(referencedTable, result.getString("referenced_table_name"));
                assertEquals(referencedColumn, result.getString("referenced_column_name"));
            }
        }
    }

    private boolean hasForeignKey(String table, String column) throws Exception {
        try (Connection connection = connection();
             var statement = connection.prepareStatement("""
                     SELECT 1
                       FROM information_schema.key_column_usage
                      WHERE table_schema = ?
                        AND table_name = ?
                        AND column_name = ?
                        AND referenced_table_name IS NOT NULL
                      LIMIT 1
                     """)) {
            statement.setString(1, databaseName());
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private long countRows(String sql) throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl(), databaseUsername(), databasePassword());
    }

    protected abstract String jdbcUrl();

    protected abstract String databaseUsername();

    protected abstract String databasePassword();

    protected abstract String databaseName();

    private static String hex(char digit) {
        return String.valueOf(digit).repeat(64);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
