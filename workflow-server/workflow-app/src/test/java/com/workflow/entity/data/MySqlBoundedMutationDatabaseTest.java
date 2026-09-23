package com.workflow.entity.data;

import com.workflow.embed.infrastructure.persistence.mapper.EmbedMaintenanceMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApiRequestLeaseMapper;
import com.workflow.outbox.infrastructure.persistence.mapper.OutboxRecordMapper;
import com.workflow.process.action.infrastructure.persistence.mapper.FlowActionExecutionMapper;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.Harness;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static com.workflow.entity.data.MySqlIdempotentInsertDatabaseTest.concurrent;
import static com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.currentTable;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 Mapper 和 MySQL 锁/时钟语义；所有表使用随机名隔离，禁止消费部署环境中的队列。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlBoundedMutationDatabaseTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 22, 8, 0);

    @Test void replayCleanupUsesTheWholeCompositeKeyAndAnExactBatchLimit() {
        try (var f = new Fixture()) {
            f.table("embed_assertion_replay", "provider_id VARCHAR(64), jti_digest VARCHAR(64), expires_at DATETIME(6), "
                    + "PRIMARY KEY(provider_id,jti_digest), KEY(expires_at,provider_id,jti_digest)");
            var h = new Harness(f, EmbedMaintenanceMapper.class);
            var mapper = h.mapper(EmbedMaintenanceMapper.class);
            for (String provider : List.of("a", "b")) for (String digest : List.of("1", "2", "3")) {
                h.jdbc.update("INSERT INTO embed_assertion_replay VALUES (?,?,?)", provider, digest, digest.equals("3") ? NOW.plusSeconds(1) : NOW);
            }
            assertEquals(0, mapper.deleteExpiredAssertionReplays(NOW, 0));
            assertEquals(3, mapper.deleteExpiredAssertionReplays(NOW, 3));
            assertEquals(List.of("a3", "b2", "b3"), h.jdbc.queryForList("SELECT CONCAT(provider_id,jti_digest) FROM embed_assertion_replay ORDER BY provider_id,jti_digest", String.class));
            assertEquals(1, mapper.deleteExpiredAssertionReplays(NOW, 3));
            assertEquals(0, mapper.deleteExpiredAssertionReplays(NOW, 3));
            assertEquals(2, count(h, "embed_assertion_replay"));
        }
    }

    @Test void launchExpiryIsBoundedReentrantAndOwnedByTheCallingTransaction() {
        try (var f = new Fixture()) {
            launchTable(f);
            var h = new Harness(f, EmbedMaintenanceMapper.class);
            var mapper = h.mapper(EmbedMaintenanceMapper.class);
            for (String id : List.of("a", "b", "c", "consumed", "future")) {
                h.jdbc.update("INSERT INTO embed_launch VALUES (?,?,?,?)", id,
                        id.equals("consumed") ? "CONSUMED" : "ISSUED", id.equals("future") ? NOW.plusDays(1) : NOW, NOW.minusDays(1));
            }
            assertEquals(2, mapper.expireIssuedLaunches(NOW, 2));
            assertEquals(List.of("a", "b"), h.jdbc.queryForList("SELECT id FROM embed_launch WHERE status='EXPIRED' ORDER BY id", String.class));
            h.tx.executeWithoutResult(status -> {
                assertEquals(1, mapper.expireIssuedLaunches(NOW, 2));
                status.setRollbackOnly();
            });
            assertEquals("ISSUED", h.jdbc.queryForObject("SELECT status FROM embed_launch WHERE id='c'", String.class));
            assertEquals(1, mapper.expireIssuedLaunches(NOW, 2));
            assertEquals(0, mapper.expireIssuedLaunches(NOW, 2));
            assertEquals("CONSUMED", h.jdbc.queryForObject("SELECT status FROM embed_launch WHERE id='consumed'", String.class));
        }
    }

    @Test void contextErasureAndTerminalCleanupPreserveChecksAndReferencedLaunches() {
        try (var f = new Fixture()) {
            String launch = launchTable(f);
            f.table("embed_session", "id VARCHAR(64) PRIMARY KEY, launch_id VARCHAR(64) NOT NULL, status VARCHAR(32), slot_released INT, slot_released_at DATETIME(6), "
                    + "context_ciphertext TEXT NOT NULL, context_cipher_key_version VARCHAR(64) NOT NULL, context_digest VARCHAR(64) NOT NULL, context_digest_key_version VARCHAR(64) NOT NULL, update_time DATETIME(6), "
                    + "CHECK(JSON_VALID(context_ciphertext)), CHECK(CHAR_LENGTH(context_digest)=64), FOREIGN KEY(launch_id) REFERENCES " + launch + "(id), KEY(status,slot_released_at,id)");
            var h = new Harness(f, EmbedMaintenanceMapper.class);
            var mapper = h.mapper(EmbedMaintenanceMapper.class);
            for (String id : List.of("referenced", "free-a", "free-b")) {
                h.jdbc.update("INSERT INTO embed_launch VALUES (?,'CONSUMED',?,?)", id, NOW.minusDays(1), NOW.minusDays(1));
            }
            for (String id : List.of("a", "b", "active", "unreleased", "recent")) {
                h.jdbc.update("INSERT INTO embed_session VALUES (?,'referenced',?,?,?,?,'old-key',?,'old-digest-key',?)", id,
                        id.equals("active") ? "ACTIVE" : "EXPIRED", id.equals("unreleased") ? 0 : 1,
                        id.equals("recent") ? NOW.plusDays(1) : NOW, "{\"encrypted\":\"secret\"}", "a".repeat(64), NOW.minusDays(1));
            }
            assertEquals(1, mapper.eraseTerminalSessionContexts(NOW, NOW, 1));
            assertEquals("{}", h.jdbc.queryForObject("SELECT context_ciphertext FROM embed_session WHERE id='a'", String.class));
            assertEquals("0".repeat(64), h.jdbc.queryForObject("SELECT context_digest FROM embed_session WHERE id='a'", String.class));
            assertEquals(1, mapper.eraseTerminalSessionContexts(NOW, NOW, 10));
            assertEquals(0, mapper.eraseTerminalSessionContexts(NOW, NOW, 10));
            assertEquals(3, h.jdbc.queryForObject("SELECT COUNT(*) FROM embed_session WHERE context_cipher_key_version='old-key'", Integer.class));
            assertEquals(1, mapper.deleteUnreferencedTerminalLaunches(NOW, 1));
            assertEquals(List.of("free-b", "referenced"), h.jdbc.queryForList("SELECT id FROM embed_launch ORDER BY id", String.class));
            assertEquals(1, mapper.deleteTerminalSessions(NOW, 1));
            assertEquals(1, mapper.deleteTerminalSessions(NOW, 10));
            assertEquals(0, mapper.deleteTerminalSessions(NOW, 10));
            assertEquals(1, mapper.deleteUnreferencedTerminalLaunches(NOW, 10));
            assertEquals(1, count(h, "embed_launch"));
            assertEquals(3, count(h, "embed_session"));
        }
    }

    @Test void receiptCleanupPreservesLiveIdempotencyRecordsAndRetentionCutoff() {
        try (var f = new Fixture()) {
            f.table("integration_idempotency_record", "id VARCHAR(64) PRIMARY KEY");
            f.table("embed_operation_receipt", "id VARCHAR(64) PRIMARY KEY, idempotency_record_id VARCHAR(64), create_time DATETIME(6), KEY(create_time,id)");
            var h = new Harness(f, EmbedMaintenanceMapper.class);
            var mapper = h.mapper(EmbedMaintenanceMapper.class);
            h.jdbc.update("INSERT INTO integration_idempotency_record VALUES ('live')");
            for (String id : List.of("a", "b", "live", "future")) {
                h.jdbc.update("INSERT INTO embed_operation_receipt VALUES (?,?,?)", id, id, id.equals("future") ? NOW.plusDays(1) : NOW);
            }
            assertEquals(1, mapper.deleteOrphanOperationReceipts(NOW, 1));
            assertEquals(List.of("b", "future", "live"), h.jdbc.queryForList("SELECT id FROM embed_operation_receipt ORDER BY id", String.class));
            assertEquals(1, mapper.deleteOrphanOperationReceipts(NOW, 10));
            assertEquals(0, mapper.deleteOrphanOperationReceipts(NOW, 10));
            assertEquals(1, count(h, "integration_idempotency_record"));
        }
    }

    @Test void requestLeaseCleanupIsLimitedStableAndRollsBackWithItsTransaction() {
        try (var f = new Fixture()) {
            f.table("integration_api_request_lease", "lease_id VARCHAR(64) PRIMARY KEY, expires_at DATETIME(6), KEY(expires_at,lease_id)");
            var h = new Harness(f, IntegrationApiRequestLeaseMapper.class);
            var mapper = h.mapper(IntegrationApiRequestLeaseMapper.class);
            for (String id : List.of("a", "b", "c", "future")) {
                h.jdbc.update("INSERT INTO integration_api_request_lease VALUES (?,?)", id, id.equals("future") ? NOW.plusSeconds(1) : NOW);
            }
            h.tx.executeWithoutResult(status -> { assertEquals(2, mapper.deleteExpired(NOW, 2)); status.setRollbackOnly(); });
            assertEquals(4, count(h, "integration_api_request_lease"));
            assertEquals(2, mapper.deleteExpired(NOW, 2));
            assertEquals(List.of("c", "future"), h.jdbc.queryForList("SELECT lease_id FROM integration_api_request_lease ORDER BY lease_id", String.class));
            assertEquals(1, mapper.deleteExpired(NOW, 2));
            assertEquals(0, mapper.deleteExpired(NOW, 2));
        }
    }

    @Test void sixConcurrentOutboxClaimersOwnDisjointBoundedBatches() throws Exception {
        try (var f = new Fixture()) {
            currentTable(f, "workflow_outbox_event");
            var h = new Harness(f, OutboxRecordMapper.class);
            var mapper = h.mapper(OutboxRecordMapper.class);
            for (int i = 0; i < 70; i++) outbox(h, "event-" + String.format("%03d", i));
            h.jdbc.update("UPDATE workflow_outbox_event SET next_retry_time=TIMESTAMPADD(DAY,1,UTC_TIMESTAMP(6)) WHERE id='event-069'");
            h.jdbc.update("UPDATE workflow_outbox_event SET status='DEAD' WHERE id='event-068'");
            var barrier = new CyclicBarrier(6);
            var batches = concurrent(6, index -> {
                try { barrier.await(10, TimeUnit.SECONDS); } catch (Exception error) { throw new AssertionError(error); }
                String owner = "worker-" + index;
                assertEquals(10, mapper.claimBatch(owner, 120, 10));
                var rows = mapper.selectClaimedBatch(owner);
                assertEquals(10, rows.size());
                for (var row : rows) { assertEquals(owner, row.getOwnerId()); assertEquals(1L, row.getLeaseToken()); }
                return rows.stream().map(row -> row.getId()).toList();
            });
            var claimed = new HashSet<String>();
            for (var batch : batches) for (String id : batch) assertTrue(claimed.add(id), id);
            assertEquals(60, claimed.size());
            assertEquals(8, mapper.claimBatch("last", 120, 10));
            assertEquals(0, mapper.claimBatch("empty", 120, 10));
            assertEquals(68, h.jdbc.queryForObject("SELECT COUNT(*) FROM workflow_outbox_event WHERE status='PROCESSING'", Integer.class));
            assertFalse(claimed.contains("event-068")); assertFalse(claimed.contains("event-069"));
        }
    }

    @Test void outboxLeaseClockFencingRecoveryAndRetrySurviveTimezoneAndRollback() throws Exception {
        try (var f = new Fixture()) {
            currentTable(f, "workflow_outbox_event");
            var h = new Harness(f, OutboxRecordMapper.class);
            var mapper = h.mapper(OutboxRecordMapper.class);
            outbox(h, "event");
            h.tx.executeWithoutResult(status -> {
                h.jdbc.execute("SET time_zone = '+09:00'");
                assertEquals(1, mapper.claimBatch("rolled-back", 120, 1));
                // 历史表 update_time 为 DATETIME(0)，lease_until 为 DATETIME(6)；按原字段精度验收，
                // 不能把整秒截断造成的 119 误判为租约少加一秒。独立 UTC 比较仍检验时区没有偏移。
                assertTrue(h.jdbc.queryForObject("SELECT ABS(TIMESTAMPDIFF(MICROSECOND,update_time,lease_until)-120000000) < 1000000 FROM workflow_outbox_event", Boolean.class));
                assertTrue(h.jdbc.queryForObject("SELECT ABS(TIMESTAMPDIFF(SECOND,update_time,UTC_TIMESTAMP(6))) < 5 FROM workflow_outbox_event", Boolean.class));
                status.setRollbackOnly();
            });
            assertEquals(1, mapper.claimBatch("old'owner", 120, 1));
            assertEquals(0, mapper.markProcessed("event", "wrong-owner", 1));
            assertEquals(0, mapper.heartbeat("event", "old'owner", 0, 120));
            assertEquals(1, mapper.heartbeat("event", "old'owner", 1, 120));
            assertEquals(0, mapper.recoverExpiredLease("event"));
            h.jdbc.update("UPDATE workflow_outbox_event SET lease_until=TIMESTAMPADD(SECOND,-10,UTC_TIMESTAMP(6))");
            assertNull(mapper.selectClaimed("event", "old'owner"));
            assertEquals(0, mapper.markProcessed("event", "old'owner", 1));
            assertEquals(List.of("event"), mapper.selectExpiredLeaseIds());
            assertEquals(1, mapper.recoverExpiredLeases());
            // 历史 next_retry_time 是 DATETIME(0)，恢复时的微秒时钟可能四舍五入到下一秒。
            // 先验证恢复时间确实在当前一秒内，再等待其符合领取条件；不能假设下一条语句已跨过该边界。
            assertTrue(h.jdbc.queryForObject("SELECT ABS(TIMESTAMPDIFF(MICROSECOND,next_retry_time,UTC_TIMESTAMP(6))) < 1000000 FROM workflow_outbox_event", Boolean.class));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            int reclaimed;
            do {
                reclaimed = mapper.claimBatch("new-owner", 120, 1);
                if (reclaimed == 0) Thread.sleep(25);
            } while (reclaimed == 0 && System.nanoTime() < deadline);
            assertEquals(1, reclaimed);
            assertEquals(2L, mapper.selectClaimed("event", "new-owner").getLeaseToken());
            assertEquals(0, mapper.releaseClaim("event", "old'owner", 1));
            assertEquals(0, mapper.markFailed("event", "old'owner", 1, "DEAD", 1, 0, "old"));
            assertEquals(1, mapper.markFailed("event", "new-owner", 2, "FAILED", 1, 60, "retry"));
            assertTrue(h.jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,update_time,next_retry_time)=60 FROM workflow_outbox_event", Boolean.class));
            assertEquals(0, mapper.claimBatch("early", 120, 1));
            h.jdbc.update("UPDATE workflow_outbox_event SET next_retry_time=NULL");
            assertEquals(1, mapper.claimBatch("final-owner", 120, 1));
            assertEquals(1, mapper.markProcessed("event", "final-owner", 3));
            assertNotNull(h.jdbc.queryForObject("SELECT processed_time FROM workflow_outbox_event", LocalDateTime.class));
        }
    }

    @Test void flowActionLeaseLifecyclePreservesOwnershipAndDatabaseTime() {
        try (var f = new Fixture()) {
            // 末尾的 Wrapper 查询按实体读取全部列；补齐发布快照和执行上下文字段，不改变租约测试数据。
            f.table("process_action_execution", "id VARCHAR(64) PRIMARY KEY, process_instance_id VARCHAR(64), status VARCHAR(32), owner_id VARCHAR(100), lease_token BIGINT DEFAULT 0, lease_until DATETIME(6), "
                    + "create_time DATETIME(6), update_time DATETIME(6), next_retry_time DATETIME(6), started_at DATETIME(6), finished_at DATETIME(6), retry_count INT DEFAULT 0, "
                    + "resolved_params_json TEXT, result_json TEXT, execution_trace_json TEXT, duration_ms BIGINT, error_message TEXT, error_stack TEXT, "
                    + "action_id VARCHAR(64), action_name VARCHAR(128), handler_name VARCHAR(128), handler_display_name VARCHAR(128), version_id VARCHAR(64), "
                    + "process_definition_id VARCHAR(128), execution_id VARCHAR(64), task_id VARCHAR(64), entity_code VARCHAR(64), scope_type VARCHAR(32), "
                    + "element_id VARCHAR(128), trigger_timing VARCHAR(32), idempotency_key VARCHAR(200), payload_json TEXT, max_retries INT, KEY(status,lease_until,id)");
            var h = new Harness(f, FlowActionExecutionMapper.class);
            var mapper = h.mapper(FlowActionExecutionMapper.class);
            h.jdbc.update("INSERT INTO process_action_execution(id,status,create_time,process_instance_id) VALUES ('a','PENDING',UTC_TIMESTAMP(6),'p')");
            assertEquals("a", mapper.findReady(1).get(0).getId());
            assertEquals(1, mapper.claim("a", "old", 120));
            assertEquals(0, mapper.claim("a", "other", 120));
            assertEquals(0, mapper.heartbeat("a", "other", 1, 120));
            assertEquals(1, mapper.heartbeat("a", "old", 1, 120));
            var record = mapper.selectClaimed("a", "old");
            record.setResolvedParamsJson("{}"); record.setResultJson("{}"); record.setExecutionTraceJson("[]");
            assertEquals(1, mapper.updateRunningProgress(record));
            h.jdbc.update("UPDATE process_action_execution SET lease_until=TIMESTAMPADD(SECOND,-10,UTC_TIMESTAMP(6))");
            assertEquals(0, mapper.markLeasedSuccess(record));
            assertEquals(1, mapper.recoverExpiredLeases());
            assertEquals(1, mapper.claim("a", "new", 120));
            assertEquals(0, mapper.releaseClaim("a", "old", 1));
            assertEquals(0, mapper.updateRunningProgress(record));
            var current = mapper.selectClaimed("a", "new");
            current.setStatus("FAILED"); current.setRetryCount(1); current.setErrorMessage("retry");
            assertEquals(1, mapper.markLeasedFailure(current, 60));
            assertEquals(0, mapper.claim("a", "early", 120));
            assertTrue(h.jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,update_time,next_retry_time)=60 FROM process_action_execution", Boolean.class));
            h.jdbc.update("UPDATE process_action_execution SET next_retry_time=NULL");
            assertEquals(1, mapper.claim("a", "success", 120));
            current = mapper.selectClaimed("a", "success");
            assertEquals(1, mapper.markLeasedSuccess(current));
            assertEquals("SUCCESS", mapper.findByProcessInstanceId("p").get(0).getStatus());
            assertNotNull(h.jdbc.queryForObject("SELECT finished_at FROM process_action_execution", LocalDateTime.class));
        }
    }

    private static String launchTable(Fixture f) {
        return f.table("embed_launch", "id VARCHAR(64) PRIMARY KEY, status VARCHAR(32), expires_at DATETIME(6), update_time DATETIME(6), KEY(status,expires_at,id)");
    }

    private static int count(Harness h, String trustedTable) { return h.jdbc.queryForObject("SELECT COUNT(*) FROM " + trustedTable, Integer.class); }

    private static void outbox(Harness h, String id) {
        h.jdbc.update("INSERT INTO workflow_outbox_event(id,topic,event_key,aggregate_type,aggregate_id,payload_document,status,create_time,update_time) "
                + "VALUES (?,'test',?,'TEST','record','{}','PENDING',?,UTC_TIMESTAMP(6))", id, id, NOW);
    }
}
