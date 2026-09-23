package com.workflow.entity.data.infrastructure.schema;

import com.workflow.core.database.DatabaseExceptionClassifier;
import com.workflow.core.database.DatabaseSQLExceptionTranslator;
import com.workflow.integration.database.api.DatabaseDialects;

import com.workflow.integration.database.api.*;
import com.workflow.core.database.port.*;
import com.workflow.entity.data.infrastructure.schema.SchemaChangeQueuePort;
import com.workflow.core.database.JdbcDatabaseClock;
import com.workflow.integration.database.schema.SchemaStatementScope;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** 独立提交 DDL 请求，依靠 active_hash 唯一约束完成并发去重，不使用厂商 upsert。 */
public final class JdbcSchemaChangeQueue implements SchemaChangeQueuePort {
    private final JdbcTemplate jdbc;
    private final DatabaseClockPort clock;
    private final SchemaDdlDialect dialect;
    private final String table;

    public JdbcSchemaChangeQueue(DatabaseConnections connections, SchemaDdlDialect dialect) {
        this(new JdbcTemplate(connections.application()), dialect, "workflow_schema_change");
    }

    /** tableName 仅供部署适配及隔离测试使用，按同一方言校验并引用，不能传入 SQL 片段。 */
    public JdbcSchemaChangeQueue(JdbcTemplate jdbc, SchemaDdlDialect dialect, String tableName) {
        this.jdbc = jdbc;
        this.jdbc.setExceptionTranslator(new DatabaseSQLExceptionTranslator(
                new DatabaseExceptionClassifier(DatabaseDialects.errors(dialect.vendor()))));
        this.dialect = dialect;
        this.table = dialect.quoteIdentifier(tableName);
        this.clock = new JdbcDatabaseClock(jdbc, dialect.vendor());
    }

    @Override
    public String enqueue(String ddl) {
        SchemaStatementScope.requireBusinessStatement(ddl, dialect);
        String hash = hash(ddl);
        // 唯一冲突后原请求可能已经完成并清除 active_hash；重新检查并入队，不能返回空 ID。
        for (int attempt = 0; attempt < 10; attempt++) {
            String active = activeRequest(hash);
            if (active != null) return active;
            String id = UUID.randomUUID().toString();
            var now = clock.utcNow();
            try {
                jdbc.update("INSERT INTO " + table + " (id, ddl_hash, active_hash, ddl_statement, status,"
                        + " attempt, lease_token, next_attempt_at, create_time, update_time)"
                        + " VALUES (?, ?, ?, ?, 'PENDING', 0, 0, ?, ?, ?)", id, hash, hash, ddl, now, now, now);
                return id;
            } catch (DuplicateKeyException conflict) {
                if (attempt == 9) throw conflict;
            }
        }
        throw new IllegalStateException("Schema change request could not be created");
    }

    @Override
    public Optional<State> state(String requestId) {
        return jdbc.query("SELECT status, last_error FROM " + table + " WHERE id = ?",
                rows -> rows.next() ? Optional.of(new State(rows.getString(1), rows.getString(2))) : Optional.empty(), requestId);
    }

    private String activeRequest(String hash) {
        // active_hash 有唯一约束，最多一行，无需 LIMIT 或与事务绑定的锁查询。
        return jdbc.query("SELECT id FROM " + table + " WHERE active_hash = ? AND status IN ('PENDING', 'RUNNING')",
                rows -> rows.next() ? rows.getString(1) : null, hash);
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
