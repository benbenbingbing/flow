package com.workflow.entity.data;

import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityPublishHistoryMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionDatasetRowMapper;
import com.workflow.embed.management.infrastructure.persistence.EmbedOperationsMapper;
import com.workflow.outbox.infrastructure.persistence.mapper.OutboxRecordMapper;
import com.workflow.process.cc.infrastructure.persistence.mapper.ProcessCcRecordMapper;
import com.workflow.process.instance.infrastructure.persistence.mapper.StartedProcessPageMapper;
import com.workflow.integration.database.api.*;
import com.workflow.core.database.*;
import com.workflow.config.database.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** 在随机测试表上执行各模块真实 Mapper，验证普通分页、首行、游标及固定批次上限。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlRuntimePaginationDatabaseTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Test
    void flowableAndCcPagesKeepIdentityTimeFiltersAndBoundOffset() {
        try (var f = new Fixture()) {
            // Wrapper 按实体元数据投影全部持久化列；未参与筛选的快照列也必须真实存在。
            String cc = f.table("process_cc_record", "id VARCHAR(64) PRIMARY KEY, cc_user_id VARCHAR(64), deleted INT, create_time DATETIME,"
                    + " unique_key VARCHAR(64), process_name VARCHAR(64), data_name VARCHAR(64), node_name VARCHAR(64),"
                    + " business_key VARCHAR(64), comment VARCHAR(64), operator_name VARCHAR(64),"
                    + " process_instance_id VARCHAR(64), process_definition_id VARCHAR(64), process_key VARCHAR(64),"
                    + " node_id VARCHAR(64), cc_user_name VARCHAR(64), cc_type VARCHAR(32), cc_timing VARCHAR(32),"
                    + " operator_id VARCHAR(64), source_task_id VARCHAR(64), source_type VARCHAR(32), recipient_rule_snapshot TEXT,"
                    + " read_status VARCHAR(16), read_time DATETIME, update_time DATETIME");
            String flow = f.table("ACT_HI_PROCINST", "PROC_INST_ID_ VARCHAR(64) PRIMARY KEY, START_TIME_ DATETIME, START_USER_ID_ VARCHAR(64), PROC_DEF_ID_ VARCHAR(64)");
            for (int i = 1; i <= 7; i++) {
                f.jdbc.update("INSERT INTO " + cc + " (id,cc_user_id,deleted,create_time,unique_key,process_name,operator_name) VALUES (?,?,?,?,?,'needle','ops')",
                        "c" + i, i == 7 ? "another" : "reader", i == 6 ? 1 : 0, START.plusSeconds(i), "key" + i);
                // Flowable 使用 Date/Timestamp 写历史时间；夹具与查询采用同一种时间绑定，
                // 避免 LocalDateTime 无时区写入与 Date 按 JDBC 时区读取之间的人为偏移。
                f.jdbc.update("INSERT INTO " + flow + " VALUES (?,?,?,?)", "p" + i, java.sql.Timestamp.valueOf(START.plusSeconds(i)),
                        i == 7 ? "another" : "reader", i == 6 ? "other-def" : "def");
            }
            try (var session = f.session(ProcessCcRecordMapper.class, StartedProcessPageMapper.class)) {
                var mapper = session.getMapper(ProcessCcRecordMapper.class);
                assertEquals(5, mapper.countByCcUserId("reader"));
                assertEquals(List.of("c2", "c1"), mapper.findByCcUserId("reader", 3, 2).stream().map(row -> row.getId()).toList());
                assertEquals(List.of("c4", "c3"), mapper.findByCcUserIdFiltered("reader", "needle", "ops", START, START.plusSeconds(6), 1, 2)
                        .stream().map(row -> row.getId()).toList());
                assertEquals(5, mapper.countByCcUserIdFiltered("reader", "needle", "ops", START, START.plusSeconds(6)));
                assertEquals("c3", mapper.findByUniqueKey("key3").getId());
                assertNull(mapper.findByUniqueKey("key6"));
                var started = session.getMapper(StartedProcessPageMapper.class);
                assertEquals(5, started.count("reader", Set.of("def"), null, null));
                assertEquals(List.of("p3", "p2"), started.page("reader", Set.of("def"), null, null, 2, 2));
                assertEquals(List.of("p4", "p3"), started.page("reader", Set.of("def"),
                        java.sql.Timestamp.valueOf(START.plusSeconds(1)), java.sql.Timestamp.valueOf(START.plusSeconds(6)), 1, 2));
                assertTrue(started.page("reader", Set.of("def"), null, null, 5, 2).isEmpty());
            }
        }
    }

    @Test
    void publishedHistoryAndRecordVersionsKeepVersionOrderAndSummaryColumns() {
        try (var f = new Fixture()) {
            // 发布历史与记录详情改用 BaseMapper 全列读取，夹具补齐生产实体字段并保留摘要断言。
            String history = f.table("entity_publish_history", "id VARCHAR(64) PRIMARY KEY, entity_id VARCHAR(64), entity_code VARCHAR(64), version INT,"
                    + " entity_name VARCHAR(64), process_definition_id VARCHAR(64), lifecycle_mode VARCHAR(32),"
                    + " team_visibility_enabled INT, team_visibility_level VARCHAR(32), version_description TEXT,"
                    + " fields_snapshot TEXT, relations_snapshot TEXT, table_ddl TEXT, publish_type VARCHAR(32),"
                    + " changes_description TEXT, published_at DATETIME, published_by VARCHAR(64), published_by_name VARCHAR(64), status VARCHAR(32)");
            List<String> textColumns = List.of("entity_code", "record_id", "version_title", "scenario_code", "scenario_name", "operation_type", "source_type",
                    "business_intent_code", "business_intent_name", "operator_id", "operator_name", "process_instance_id", "source_entity_code",
                    "source_record_id", "snapshot_hash", "data_hash", "scope_hash", "snapshot_document", "idempotency_key",
                    "source_id", "process_definition_id", "task_id", "business_trace_key", "entity_release_id",
                    "presentation_hash", "request_hash", "completeness");
            String version = f.table("entity_record_version", "id VARCHAR(64) PRIMARY KEY, version_no INT, schema_version INT, create_time DATETIME,"
                    + " entity_release_version INT, dataset_count INT, snapshot_row_count INT, snapshot_size_bytes BIGINT,"
                    + String.join(",", textColumns.stream().map(column -> column + " VARCHAR(128)").toList()));
            for (int i = 1; i <= 5; i++) {
                f.jdbc.update("INSERT INTO " + history + " (id,entity_id,entity_code,version) VALUES (?,'entity','expense',?)", "h" + i, i);
                f.jdbc.update("INSERT INTO " + version + " (id,entity_code,record_id,version_no,data_hash,snapshot_hash,snapshot_document,idempotency_key)"
                        + " VALUES (?,'expense','record',?,?,?,'large-snapshot',?)", "v" + i, i, "hash" + i, "old" + i, "request" + i);
            }
            try (var session = f.session(EntityPublishHistoryMapper.class, EntityRecordVersionMapper.class)) {
                var publish = session.getMapper(EntityPublishHistoryMapper.class);
                assertEquals(List.of(4, 3), publish.findPageByEntityId("entity", 1, 2).stream().map(row -> row.getVersion()).toList());
                assertEquals(5, publish.findLatestByEntityId("entity").getVersion());
                assertEquals(2, publish.findByEntityIdAndVersion("entity", 2).getVersion());
                var versions = session.getMapper(EntityRecordVersionMapper.class);
                var summaries = versions.findSummaryPage("expense", "record", 1, 2);
                assertEquals(List.of(4, 3), summaries.stream().map(row -> row.getVersionNo()).toList());
                assertNull(summaries.get(0).getSnapshotDocument());
                assertEquals(5, versions.countByRecord("expense", "record"));
                assertEquals("v2", versions.findVersion("expense", "record", 2).getId());
                assertEquals("hash3", versions.findDataHash("expense", "record", 3));
                assertEquals("v4", versions.findIdempotent("expense", "record", "request4").getId());
                assertTrue(versions.existsByEntityCode("expense"));
                assertFalse(versions.existsByEntityCode("missing"));
            }
        }
    }

    @Test
    void versionDatasetPagesDoNotCrossDatasetsAndReturnTheLastPartialPage() {
        try (var f = new Fixture()) {
            String table = f.table("entity_record_version_dataset_row", "id VARCHAR(64) PRIMARY KEY, dataset_id VARCHAR(64), record_id VARCHAR(64), row_order INT,"
                    + " record_title VARCHAR(128), row_hash VARCHAR(64), values_document TEXT, create_time DATETIME");
            for (int i = 1; i <= 6; i++) f.jdbc.update("INSERT INTO " + table + " (id,dataset_id,record_id,row_order) VALUES (?,?,?,?)", "d" + i, i == 6 ? "another" : "dataset", "r" + i, i);
            try (var session = f.session(EntityRecordVersionDatasetRowMapper.class)) {
                var mapper = session.getMapper(EntityRecordVersionDatasetRowMapper.class);
                assertEquals(5, mapper.countByDatasetId("dataset"));
                assertEquals(List.of("r2", "r3"), mapper.findPage("dataset", 1, 2).stream().map(row -> row.getRecordId()).toList());
                assertEquals(List.of("r5"), mapper.findPage("dataset", 4, 2).stream().map(row -> row.getRecordId()).toList());
                assertTrue(mapper.findPage("dataset", 5, 2).isEmpty());
            }
        }
    }

    @Test
    void embedSessionCursorPreservesActiveSlotFilterAndHardLimit() {
        try (var f = new Fixture()) {
            String table = f.table("embed_session", "id VARCHAR(64) PRIMARY KEY, view_id VARCHAR(64), application_id VARCHAR(64), status VARCHAR(32), slot_released INT");
            for (int i = 1; i <= 6; i++) f.jdbc.update("INSERT INTO " + table + " VALUES (?,'view',?,?,?)", "s" + i,
                    i == 6 ? "another" : "app", i == 5 ? "REVOKED" : "ACTIVE", i == 3 ? 1 : 0);
            try (var session = f.session(EmbedOperationsMapper.class)) {
                var mapper = session.getMapper(EmbedOperationsMapper.class);
                assertEquals(List.of("s1", "s2"), mapper.findActiveSessionIdsByView("view", null, 2));
                assertEquals(List.of("s2", "s4"), mapper.findActiveSessionIdsByView("view", "s1", 2));
                assertEquals(List.of("s4"), mapper.findActiveSessionIdsByApplication("app", "s2", 2));
                assertTrue(mapper.findActiveSessionIdsByApplication("app", "s4", 2).isEmpty());
            }
        }
    }

    @Test
    void outboxDiscoveryKeepsTheHundredRowLimitAndDoesNotClaimJobs() {
        try (var f = new Fixture()) {
            String table = f.table("workflow_outbox_event", "id VARCHAR(64) PRIMARY KEY, status VARCHAR(32), lease_until DATETIME(6)");
            for (int i = 1; i <= 102; i++) f.jdbc.update("INSERT INTO " + table + " VALUES (?,'PROCESSING','2000-01-01')", String.format("event-%04d", i));
            f.jdbc.update("INSERT INTO " + table + " VALUES ('pending','PENDING','2000-01-01'),('future','PROCESSING','2099-01-01')");
            try (var session = f.session(OutboxRecordMapper.class)) {
                var ids = session.getMapper(OutboxRecordMapper.class).selectExpiredLeaseIds();
                assertEquals(100, ids.size());
                assertEquals("event-0001", ids.get(0));
                assertEquals("event-0100", ids.get(99));
                assertEquals(103, f.jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE status='PROCESSING'", Integer.class));
            }
        }
    }

    @Test
    void uniqueKeyLockQueriesStillExcludeCompetingWritesWithoutLockingAnotherRow() throws Exception {
        record LockCase(String table, String column, Class<?> mapper, String method) {}
        var cases = List.of(
                new LockCase("process_task_sla", "task_id", com.workflow.process.sla.runtime.infrastructure.persistence.mapper.ProcessTaskSlaMapper.class, "findByTaskIdForUpdate"),
                new LockCase("entity_mutation_receipt", "idempotency_key", com.workflow.entity.version.infrastructure.persistence.mapper.EntityMutationReceiptMapper.class, "findByIdempotencyKeyForReplay"),
                new LockCase("process_task_add_sign_user", "generated_task_id", com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignUserMapper.class, "findByGeneratedTaskIdForUpdate"),
                new LockCase("process_task", "task_id", com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper.class, "selectByTaskIdForUpdate"),
                new LockCase("process_task_add_sign", "id", com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignMapper.class, "selectByIdForUpdate"));
        for (var c : cases) {
            try (var f = new Fixture()) {
                String table = f.table(c.table(), "id VARCHAR(64) PRIMARY KEY, deleted INT DEFAULT 0, probe INT DEFAULT 0"
                        + (c.column().equals("id") ? "" : ", " + c.column() + " VARCHAR(64) UNIQUE"));
                if (c.column().equals("id")) f.jdbc.update("INSERT INTO " + table + " (id) VALUES ('1'),('2')");
                else f.jdbc.update("INSERT INTO " + table + " (id," + c.column() + ") VALUES ('1','key1'),('2','key2')");
                try (var owner = f.session(false, c.mapper()); var contender = f.source.getConnection(); var update = contender.createStatement()) {
                    Object row = c.mapper().getMethod(c.method(), String.class).invoke(owner.getMapper(c.mapper()), c.column().equals("id") ? "1" : "key1");
                    assertNotNull(row, c.table());
                    contender.setAutoCommit(true);
                    update.execute("SET SESSION innodb_lock_wait_timeout=1");
                    assertEquals(1, update.executeUpdate("UPDATE " + table + " SET probe=probe+1 WHERE id='2'"), c.table());
                    var blocked = assertThrows(SQLException.class,
                            () -> update.executeUpdate("UPDATE " + table + " SET probe=probe+1 WHERE id='1'"), c.table());
                    assertEquals(1205, blocked.getErrorCode(), c.table());
                    // SELECT FOR UPDATE 不会设置 MyBatis 的 dirty 标记，必须强制回滚物理事务才能释放锁。
                    owner.rollback(true);
                    assertEquals(1, update.executeUpdate("UPDATE " + table + " SET probe=probe+1 WHERE id='1'"), c.table());
                }
            }
        }
    }

    static final class Fixture implements AutoCloseable {
        final InitializedDriverDataSource source;
        final JdbcTemplate jdbc;

        Fixture() { this(System.getenv("FLOW_MYSQL_TEST_URL")); }

        /** SQL mode 在驱动初始化时设置，避免运行中 SET 后驱动仍缓存旧的字符串转义规则。 */
        Fixture(boolean noBackslashEscapes) { this(sqlModeUrl(noBackslashEscapes)); }

        private Fixture(String url) {
            source = new InitializedDriverDataSource(url, System.getenv("FLOW_MYSQL_TEST_USER"),
                    System.getenv("FLOW_MYSQL_TEST_PASSWORD"), null, DatabaseJdbcProfiles.connectionInitSql(DatabaseVendor.MYSQL));
            jdbc = new JdbcTemplate(source);
        }

        private static String sqlModeUrl(boolean noBackslashEscapes) {
            String url = System.getenv("FLOW_MYSQL_TEST_URL");
            String modes = "ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION"
                    + (noBackslashEscapes ? ",NO_BACKSLASH_ESCAPES" : "");
            String setting = java.net.URLEncoder.encode("sql_mode='" + modes + "'", java.nio.charset.StandardCharsets.UTF_8);
            // 测试连接明确覆盖 sql_mode；不改环境变量、全局变量或其他连接的设置。
            if (java.util.regex.Pattern.compile("(?i)[?&]sessionVariables=").matcher(url).find())
                return url.replaceAll("(?i)([?&])sessionVariables=[^&]*", "$1sessionVariables=" + setting);
            return url + (url.contains("?") ? "&" : "?") + "sessionVariables=" + setting;
        }

        final Map<String, String> tables = new LinkedHashMap<>();
        final String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        String table(String logicalName, String columns) {
            String actual = "biz_page_" + suffix + "_" + tables.size();
            jdbc.execute("CREATE TABLE " + actual + " (" + columns + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            tables.put(logicalName, actual);
            return actual;
        }

        /** 仅替换固定表名来隔离测试数据；分页、条件与结果映射仍执行生产 Mapper。 */
        SqlSession session(Class<?>... mappers) {
            return session(true, mappers);
        }

        SqlSession session(boolean autoCommit, Class<?>... mappers) {
            var config = new com.baomidou.mybatisplus.core.MybatisConfiguration(
                    new Environment("mysql-runtime-pagination", new JdbcTransactionFactory(), isolatedDataSource()));
            config.setDatabaseId("MYSQL"); config.setMapUnderscoreToCamelCase(true);
            com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.getGlobalConfig(config)
                    .getDbConfig().setLogicDeleteField("deleted");
            config.addInterceptor(new DatabaseMybatisConfiguration()
                    .mybatisPlusInterceptor(new com.workflow.integration.database.dialect.MySqlSchemaDdlDialect()));
            for (var mapper : mappers) config.addMapper(mapper);
            return new SqlSessionFactoryBuilder().build(config).openSession(autoCommit);
        }

        /** 同一组随机表也供服务层 JDBC 查询使用，避免测试访问固定业务表名。 */
        javax.sql.DataSource isolatedDataSource() {
            return new DelegatingDataSource(source) {
                @Override public Connection getConnection() throws SQLException {
                    var actual = super.getConnection();
                    return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement") && args != null && args[0] instanceof String sql) {
                            args[0] = isolateSql(sql);
                        }
                        try {
                            Object result = method.invoke(actual, args);
                            // JdbcTemplate 无绑定参数时使用 Statement，必须和 PreparedStatement 一样隔离表名。
                            if (method.getName().equals("createStatement")) {
                                return Proxy.newProxyInstance(java.sql.Statement.class.getClassLoader(), new Class<?>[]{java.sql.Statement.class},
                                        (statementProxy, statementMethod, statementArgs) -> {
                                            if (statementArgs != null && statementArgs.length > 0 && statementArgs[0] instanceof String sql)
                                                statementArgs[0] = isolateSql(sql);
                                            try { return statementMethod.invoke(result, statementArgs); }
                                            catch (InvocationTargetException error) { throw error.getCause(); }
                                        });
                            }
                            return result;
                        }
                        catch (InvocationTargetException error) { throw error.getCause(); }
                    });
                }
            };
        }

        private String isolateSql(String sql) {
            for (var table : tables.entrySet()) sql = sql.replaceAll("(?i)\\b" + Pattern.quote(table.getKey()) + "\\b", table.getValue());
            return sql;
        }

        @Override public void close() {
            RuntimeException failure = null;
            // 父表先建、子表后建；反向清理保留外键校验，不靠关闭约束掩盖测试数据泄漏。
            var removalOrder = new ArrayList<>(tables.values());
            Collections.reverse(removalOrder);
            for (var table : removalOrder) {
                try { jdbc.execute("DROP TABLE IF EXISTS " + table); }
                catch (RuntimeException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
            }
            if (failure != null) throw failure;
        }
    }
}
