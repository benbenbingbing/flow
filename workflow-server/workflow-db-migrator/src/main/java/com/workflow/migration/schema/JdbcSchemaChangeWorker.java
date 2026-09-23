package com.workflow.migration.schema;

import com.workflow.core.database.DatabaseExceptionClassifier;
import com.workflow.core.database.DatabaseSQLExceptionTranslator;
import com.workflow.integration.database.api.DatabaseDialects;

import com.workflow.integration.database.api.*;
import com.workflow.core.database.port.*;
import com.workflow.core.database.JdbcDatabaseClock;
import com.workflow.core.database.lock.JdbcDatabaseLock;
import com.workflow.integration.database.schema.SchemaStatementScope;
import com.workflow.migration.schema.SchemaDdlReplayVerifier;
import com.workflow.integration.database.schema.AuditTimestampDdl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 独立身份执行动态 DDL。领取依靠带版本号的原子 UPDATE，避免 LIMIT/FOR UPDATE 的厂商差异。
 * 每个请求另持会话锁，防止租约超时但旧 DDL 尚在执行时被新 worker 并发重放。
 */
public final class JdbcSchemaChangeWorker {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcSchemaChangeWorker.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final long LEASE_SECONDS = 120;
    private final JdbcTemplate jdbc;
    private final DatabaseClockPort clock;
    private final SchemaDdlDialect dialect;
    private final DatabaseLockPort locks;
    private final SchemaDdlReplayVerifier replay;
    private final String owner;
    private final String table;

    /** source 必须是关闭即结束物理会话的独立数据源；不得传入业务连接池。 */
    public JdbcSchemaChangeWorker(DataSource source, SchemaDdlDialect dialect, String owner) {
        this(source, dialect, owner, "workflow_schema_change");
    }

    /** tableName 用于隔离队列，不改变任务 DDL 仅可操作 biz_ 表的边界。 */
    public JdbcSchemaChangeWorker(DataSource source, SchemaDdlDialect dialect, String owner, String tableName) {
        if (owner == null || owner.isBlank() || owner.length() > 128) throw new IllegalArgumentException("Invalid schema worker ID");
        this.jdbc = new JdbcTemplate(source);
        this.jdbc.setExceptionTranslator(new DatabaseSQLExceptionTranslator(
                new DatabaseExceptionClassifier(DatabaseDialects.errors(dialect.vendor()))));
        this.clock = new JdbcDatabaseClock(jdbc, dialect.vendor());
        this.dialect = dialect;
        this.locks = new JdbcDatabaseLock(source, dialect.vendor());
        this.replay = new SchemaDdlReplayVerifier(jdbc, dialect);
        this.owner = owner;
        this.table = dialect.quoteIdentifier(tableName);
    }

    /** 尝试处理一个到期请求；无任务或候选正在被其他会话处理时返回 false，调用方负责轮询。 */
    public boolean processNext() {
        var now = clock.utcNow();
        Candidate cursor = null;
        while (true) {
            List<Candidate> candidates = candidates(now, cursor);
            for (var candidate : candidates) {
                var handle = locks.tryAcquire("flow:schema-job", table + ":" + candidate.id());
                if (handle.isEmpty()) continue;
                try (var held = handle.orElseThrow()) {
                    Claim claim = claim(candidate);
                    if (claim == null) continue;
                    if (candidate.attempt() >= MAX_ATTEMPTS) {
                        // 最后一次执行崩溃也要收口；先检查是否只是 ACK 丢失，但不再执行第六次 DDL。
                        finishExhausted(claim);
                    } else {
                        apply(claim);
                    }
                    return true;
                }
            }
            if (candidates.size() < 20) return false;
            // 前一批可能都被执行中的会话锁占用。按稳定游标继续扫描，避免后续实体发布饥饿。
            cursor = candidates.get(candidates.size() - 1);
        }
    }

    private void finishExhausted(Claim claim) {
        try {
            SchemaStatementScope.requireBusinessStatement(claim.ddl(), dialect);
            if (replay.isApplied(claim.ddl())) complete(claim);
            else fail(claim, "Schema worker exhausted attempts before acknowledging DDL", true);
        } catch (RuntimeException failure) {
            fail(claim, safeMessage(failure), true);
        }
    }

    private List<Candidate> candidates(LocalDateTime now, Candidate cursor) {
        return jdbc.query(connection -> {
            var select = connection.prepareStatement("SELECT id, ddl_statement, lease_token, attempt, create_time FROM " + table
                    + " WHERE status IN ('PENDING', 'RUNNING') AND next_attempt_at <= ?"
                    + " AND (status = 'PENDING' OR lease_until < ?)"
                    + (cursor == null ? "" : " AND (create_time > ? OR (create_time = ? AND id > ?))")
                    + " ORDER BY create_time, id");
            select.setObject(1, now);
            select.setObject(2, now);
            if (cursor != null) {
                select.setObject(3, cursor.created());
                select.setObject(4, cursor.created());
                select.setString(5, cursor.id());
            }
            // JDBC 限制返回候选数量，各产品由驱动完成截断；SQL 不含厂商分页或行锁组合。
            select.setMaxRows(20);
            return select;
        }, (row, index) -> new Candidate(row.getString(1), row.getString(2), row.getLong(3), row.getInt(4),
                row.getObject(5, LocalDateTime.class)));
    }

    /** SELECT 不是领取凭据；只有匹配旧 token、状态和到期条件的 UPDATE 成功才拥有租约。 */
    private Claim claim(Candidate candidate) {
        var now = clock.utcNow();
        int attempt = Math.min(MAX_ATTEMPTS, candidate.attempt() + 1);
        long token = Math.addExact(candidate.token(), 1);
        int updated = jdbc.update("UPDATE " + table + " SET status='RUNNING', owner_id=?, lease_token=?,"
                + " lease_until=?, attempt=?, last_error=NULL, update_time=? WHERE id=? AND lease_token=?"
                + " AND status IN ('PENDING','RUNNING') AND next_attempt_at<=?"
                + " AND (status='PENDING' OR lease_until<?)", owner, token, now.plusSeconds(LEASE_SECONDS),
                attempt, now, candidate.id(), candidate.token(), now, now);
        return updated == 1 ? new Claim(candidate.id(), candidate.ddl(), token, attempt) : null;
    }

    private void apply(Claim claim) {
        try (var heartbeat = new LeaseHeartbeat(claim)) {
            SchemaStatementScope.requireBusinessStatement(claim.ddl(), dialect);
            if (!heartbeat.renew()) throw new IllegalStateException("Schema worker lease expired before DDL");
            try {
                for (String statement : AuditTimestampDdl.retryableExecution(claim.ddl())) {
                    SchemaStatementScope.requireBusinessStatement(statement, dialect);
                    jdbc.execute(statement);
                }
            } catch (RuntimeException failure) {
                // 不能根据“已存在”错误码认定成功。只在实际结构能证明目标已达成时接受重放。
                if (!replay.isApplied(claim.ddl())) throw failure;
            }
            // MySQL 的 IF NOT EXISTS 会静默跳过已有的错误结构，因此即便执行未抛错也要验证建表结果。
            if (replay.requiresCreationVerification(claim.ddl()) && !replay.isApplied(claim.ddl())) {
                throw new IllegalStateException("Existing schema object does not match the requested DDL");
            }
            if (heartbeat.lost.get()) throw new IllegalStateException("Schema worker lost lease during DDL");
            complete(claim);
        } catch (RuntimeException failure) {
            fail(claim, safeMessage(failure), claim.attempt() >= MAX_ATTEMPTS);
        }
    }

    private void complete(Claim claim) {
        var now = clock.utcNow();
        acknowledge(jdbc.update("UPDATE " + table + " SET status='APPLIED', active_hash=NULL, lease_until=NULL,"
                + " last_error=NULL, completed_time=?, update_time=?" + fence(),
                now, now, claim.id(), owner, claim.token(), now), claim);
    }

    private void fail(Claim claim, String message, boolean terminal) {
        var now = clock.utcNow();
        String status = terminal ? "FAILED" : "PENDING";
        int delay = Math.min(300, 5 * (1 << Math.min(Math.max(0, claim.attempt() - 1), 6)));
        acknowledge(jdbc.update("UPDATE " + table + " SET status=?, active_hash=CASE WHEN ?='FAILED'"
                + " THEN NULL ELSE active_hash END, lease_until=NULL, last_error=?, next_attempt_at=?, update_time=?"
                + fence(), status, status, message, now.plusSeconds(delay), now,
                claim.id(), owner, claim.token(), now), claim);
    }

    private String fence() {
        return " WHERE id=? AND owner_id=? AND lease_token=? AND status='RUNNING' AND lease_until>?";
    }

    private void acknowledge(int count, Claim claim) {
        if (count != 1) LOG.warn("Schema worker lost lease before ACK: id={}", claim.id());
    }

    private final class LeaseHeartbeat implements AutoCloseable {
        private final Claim claim;
        private final AtomicBoolean lost = new AtomicBoolean();
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "schema-worker-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        });

        private LeaseHeartbeat(Claim claim) {
            this.claim = claim;
            scheduler.scheduleAtFixedRate(this::renew, 30, 30, TimeUnit.SECONDS);
        }

        private boolean renew() {
            if (lost.get()) return false;
            try {
                LocalDateTime now = clock.utcNow();
                boolean renewed = jdbc.update("UPDATE " + table + " SET lease_until=?, update_time=?" + fence(),
                        now.plusSeconds(LEASE_SECONDS), now, claim.id(), owner, claim.token(), now) == 1;
                if (!renewed) lost.set(true);
                return renewed;
            } catch (RuntimeException failure) {
                lost.set(true);
                LOG.warn("Schema worker could not renew lease: id={}", claim.id(), failure);
                return false;
            }
        }

        @Override public void close() { scheduler.shutdownNow(); }
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return message.substring(0, Math.min(message.length(), 1000));
    }

    private record Candidate(String id, String ddl, long token, int attempt, LocalDateTime created) {}
    private record Claim(String id, String ddl, long token, int attempt) {}
}
