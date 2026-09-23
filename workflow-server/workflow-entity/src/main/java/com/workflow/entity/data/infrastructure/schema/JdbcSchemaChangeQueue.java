package com.workflow.entity.data.infrastructure.schema;

import com.workflow.integration.database.api.schema.SchemaDdlDialect;
import com.workflow.core.database.DatabaseExceptionClassifier;
import com.workflow.core.database.DatabaseSQLExceptionTranslator;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.core.database.port.*;
import com.workflow.core.database.JdbcDatabaseClock;
import com.workflow.integration.database.schema.validation.SchemaStatementScope;
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

    /**
     * 初始化JDBC结构变更队列，保存构造参数供后续方法使用。
     *
     * @param connections {@code connections}，保存在对象中供后续校验、查询或展示
     * @param dialect 方言，保存在对象中供后续校验、查询或展示
     */
    public JdbcSchemaChangeQueue(DatabaseConnections connections, SchemaDdlDialect dialect) {
        this(new JdbcTemplate(connections.application()), dialect, "workflow_schema_change");
    }

    /**
     * tableName 仅供部署适配及隔离测试使用，按同一方言校验并引用，不能传入 SQL 片段。
     *
     * @param jdbc JDBC依赖，保存到当前对象供后续业务方法调用
     * @param dialect 方言依赖，保存到当前对象供后续业务方法调用
     * @param tableName 目标物理表名，后续用于构造查询或表结构操作
     */
    public JdbcSchemaChangeQueue(JdbcTemplate jdbc, SchemaDdlDialect dialect, String tableName) {
        this.jdbc = jdbc;
        this.jdbc.setExceptionTranslator(new DatabaseSQLExceptionTranslator(
                new DatabaseExceptionClassifier(DatabaseDialects.errors(dialect.vendor()))));
        this.dialect = dialect;
        this.table = dialect.quoteIdentifier(tableName);
        this.clock = new JdbcDatabaseClock(jdbc, dialect.vendor());
    }

    /**
     * 入队JDBC结构变更队列；后续由接收方或异步任务继续处理。
     *
     * @param ddl DDL，作为 {@code SchemaStatementScope.requireBusinessStatement} 的输入影响后续处理
     * @return 入队后的JDBC结构变更队列文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 处理状态，并将结果传给后续步骤。
     *
     * @param requestId 请求ID，后续用于处理状态时定位或关联目标
     * @return 匹配的状态；未找到时为空
     */
    @Override
    public Optional<State> state(String requestId) {
        return jdbc.query("SELECT status, last_error FROM " + table + " WHERE id = ?",
                rows -> rows.next() ? Optional.of(new State(rows.getString(1), rows.getString(2))) : Optional.empty(), requestId);
    }

    /**
     * 生成活动请求文本，供后续匹配或展示。
     *
     * @param hash 哈希，供本方法处理活动请求时使用
     * @return 处理后的活动请求文本，供调用方比较或展示
     */
    private String activeRequest(String hash) {
        // active_hash 有唯一约束，最多一行，无需 LIMIT 或与事务绑定的锁查询。
        return jdbc.query("SELECT id FROM " + table + " WHERE active_hash = ? AND status IN ('PENDING', 'RUNNING')",
                rows -> rows.next() ? rows.getString(1) : null, hash);
    }

    /**
     * 生成哈希文本，供后续匹配或展示。
     *
     * @param text 待处理哈希的原始输入，结果供调用方继续使用
     * @return 处理后的哈希文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
