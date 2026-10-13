package com.workflow.database;

import com.workflow.core.database.jdbc.*;
import com.workflow.core.database.schema.JdbcSchemaMetadata;
import com.workflow.config.database.DatabaseDataSourceConfiguration;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import com.workflow.integration.database.api.schema.SchemaType;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.io.StringReader;
import java.sql.Types;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** 使用真实 OB Oracle 验证保存点、数字文本和生产数据源元数据；仅创建并清理随机 ORAP_ 表。 */
@EnabledIfEnvironmentVariable(named = "FLOW_OB_ORACLE_TEST_URL", matches = ".+")
class OceanBaseOracleJdbcCompatibilityDatabaseTest {
    private static final DatabaseVendor VENDOR = DatabaseVendor.OCEANBASE_ORACLE;

    @Test void scalarPatternsKeepSignsPrecisionTrailingZerosAndNulls() {
        try (var pool = pool()) {
            var jdbc = new JdbcTemplate(pool);
            var dialect = DatabaseQueryDialects.forVendor(VENDOR);
            record Example(SchemaType type, String value, String expected) { }
            var examples = List.of(
                    new Example(SchemaType.of(SchemaType.Kind.INTEGER), "-2147483648", "-2147483648"),
                    new Example(SchemaType.of(SchemaType.Kind.LONG), "9223372036854775807", "9223372036854775807"),
                    new Example(SchemaType.of(SchemaType.Kind.LONG), "-9223372036854775808", "-9223372036854775808"),
                    new Example(SchemaType.of(SchemaType.Kind.BOOLEAN), "0", "0"),
                    new Example(SchemaType.of(SchemaType.Kind.BOOLEAN), "1", "1"),
                    new Example(SchemaType.of(SchemaType.Kind.BYTE), "-128", "-128"),
                    new Example(SchemaType.of(SchemaType.Kind.BYTE), "127", "127"),
                    new Example(SchemaType.decimal(12, 4), "-1234.5", "-1234.5000"),
                    new Example(SchemaType.decimal(5, 5), "0.001", "0.00100"),
                    new Example(SchemaType.decimal(12, 4), "0", "0.0000"),
                    new Example(SchemaType.decimal(38, 4), "1234567890123456789012345678901234.5678", "1234567890123456789012345678901234.5678"),
                    new Example(SchemaType.decimal(38, 0), "-12345678901234567890123456789012345678", "-12345678901234567890123456789012345678"));
            for (var example : examples) {
                String sql = "SELECT " + dialect.patternValueExpression("n", example.type())
                        + " FROM (SELECT CAST(? AS NUMBER) AS n FROM DUAL)";
                assertEquals(example.expected(), jdbc.queryForObject(sql, String.class, new BigDecimal(example.value())), sql);
                assertNull(jdbc.queryForObject(sql, String.class, new SqlParameterValue(Types.NUMERIC, null)));
            }
        }
    }

    @Test void allSavepointExecutorsPreservePriorWorkAndOuterRollback() {
        try (var pool = pool()) {
            var jdbc = new JdbcTemplate(pool);
            String table = "orap_tx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            jdbc.execute("CREATE TABLE " + table + " (id NUMBER(10) PRIMARY KEY, label VARCHAR2(50))");
            try {
                var tx = new TransactionTemplate(new DataSourceTransactionManager(pool));
                var dialect = DatabaseDialects.insert(VENDOR);
                tx.executeWithoutResult(status -> {
                    var insert = new JdbcIdempotentInsert(jdbc, dialect);
                    assertTrue(insert.insertIfAbsent(table, Map.of("id", 1, "label", "original")));
                    assertFalse(insert.insertIfAbsent(table, Map.of("id", 1, "label", "duplicate")));
                    var attempt = new JdbcWriteAttempt(jdbc, dialect);
                    assertEquals(1, attempt.execute(() -> jdbc.update("INSERT INTO " + table + " VALUES (2,'second')")));
                    assertThrows(DuplicateKeyException.class,
                            () -> attempt.execute(() -> jdbc.update("INSERT INTO " + table + " VALUES (1,'duplicate')")));
                    var locked = new JdbcLockedRow(jdbc, dialect);
                    locked.ensureAndLock(table, Map.of("id", 3, "label", "locked"), List.of("id"));
                    locked.ensureAndLock(table, Map.of("id", 3, "label", "replacement"), List.of("id"));
                    var nullable = new LinkedHashMap<String, Object>();
                    nullable.put("id", 4);
                    nullable.put("label", null);
                    assertTrue(insert.insertIfAbsent(table, nullable));
                    nullable.put("id", 5);
                    locked.ensureAndLock(table, nullable, List.of("id"));
                    locked.ensureAndLock(table, nullable, List.of("id"));
                    assertEquals(Arrays.asList("original", "second", "locked", null, null),
                            jdbc.queryForList("SELECT label FROM " + table + " ORDER BY id", String.class));
                    status.setRollbackOnly();
                });
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
            } finally {
                jdbc.execute("DROP TABLE " + table + " PURGE");
            }
        }
    }

    @Test void streamedClobAndGeneratedKeysWorkTogether() throws Exception {
        // 命名自增键要求 Oracle prepare-execute；该协议还必须启用 piece data 才能接收 CLOB Reader。
        // 两种能力必须在同一套生产默认属性下验证，避免只修主键回填却破坏大文本写入。
        try (var pool = pool(); var connection = pool.getConnection(); var statement = connection.createStatement()) {
            String table = "orap_lob_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            statement.execute("CREATE TABLE " + table
                    + " (id NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, doc CLOB, label VARCHAR2(50))");
            try {
                String value = "中文" + "x".repeat(16_000) + "TAIL";
                try (var insert = connection.prepareStatement("INSERT INTO " + table + " (doc,label) VALUES (?,?)", new String[]{"ID"})) {
                    insert.setCharacterStream(1, new StringReader(value), value.length());
                    insert.setNull(2, Types.NULL);
                    assertEquals(1, insert.executeUpdate());
                    try (var keys = insert.getGeneratedKeys()) {
                        assertTrue(keys.next());
                        assertTrue(keys.getLong(1) > 0);
                    }
                }
                try (var query = connection.prepareStatement("SELECT doc,label FROM " + table
                        + " WHERE DBMS_LOB.COMPARE(doc,?)=0 AND doc LIKE ? AND REGEXP_LIKE(doc,?)")) {
                    query.setCharacterStream(1, new StringReader(value), value.length());
                    query.setString(2, "%TAIL");
                    query.setString(3, "TAIL$");
                    try (var rows = query.executeQuery()) {
                        assertTrue(rows.next());
                        assertEquals(value, rows.getString(1));
                        assertNull(rows.getString(2));
                    }
                }
            } finally {
                statement.execute("DROP TABLE " + table + " PURGE");
            }
        }
    }

    @Test void applicationAndIndependentConnectionsKeepCurrentSchemaAndComments() throws Exception {
        // 不在 URL 或测试属性中手动添加 remarksReporting；验证两条生产建连路径的默认配置。
        var independent = new InitializedDriverDataSource(DatabaseVendor.OCEANBASE_ORACLE, System.getenv("FLOW_OB_ORACLE_TEST_URL"),
                System.getenv("FLOW_OB_ORACLE_TEST_USER"), System.getenv("FLOW_OB_ORACLE_TEST_PASSWORD"),
                "com.oceanbase.jdbc.Driver", null);
        var properties = new DataSourceProperties();
        properties.setUrl(System.getenv("FLOW_OB_ORACLE_TEST_URL"));
        properties.setUsername(System.getenv("FLOW_OB_ORACLE_TEST_USER"));
        properties.setPassword(System.getenv("FLOW_OB_ORACLE_TEST_PASSWORD"));
        var dialect = DatabaseDialects.forVendor(VENDOR);
        try (var connection = independent.getConnection();
             var application = new DatabaseDataSourceConfiguration().dataSource(properties, dialect)) {
            application.setMaximumPoolSize(2);
            application.setMinimumIdle(0);
            application.setConnectionTimeout(30_000);
            application.setValidationTimeout(10_000);
            var ddl = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            String table = "orap_meta_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            ddl.execute("CREATE TABLE " + table + " (id NUMBER(10) PRIMARY KEY, label VARCHAR2(50))");
            try {
                ddl.execute("COMMENT ON TABLE " + table + " IS '元数据表说明'");
                ddl.execute("COMMENT ON COLUMN " + table + ".id IS '测试主键说明'");
                ddl.execute("COMMENT ON COLUMN " + table + ".label IS '业务字段说明'");
                for (var jdbc : List.of(ddl, new JdbcTemplate(application))) {
                    String schema = jdbc.queryForObject("SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') FROM DUAL", String.class);
                    assertNotNull(schema);
                    assertFalse(schema.isBlank());
                    // 实际存在于 CURRENT_SCHEMA 的唯一随机表必须被识别，不能以 user@tenant#cluster 查询。
                    var metadata = new JdbcSchemaMetadata(jdbc, dialect);
                    assertTrue(metadata.tableExists(table), "当前 schema 的测试表未被识别: " + schema);
                    var actual = metadata.tables().stream().filter(t -> t.name().equals(table)).findFirst().orElseThrow();
                    assertEquals("元数据表说明", actual.comment());
                    var columns = metadata.columns(table);
                    assertEquals(List.of("id", "label"), columns.stream().map(c -> c.name()).toList());
                    assertEquals(List.of("测试主键说明", "业务字段说明"), columns.stream().map(c -> c.comment()).toList());
                    assertTrue(columns.get(0).primaryKey());
                }
            } finally {
                ddl.execute("DROP TABLE " + table + " PURGE");
            }
        }
    }

    private HikariDataSource pool() {
        var pool = new HikariDataSource();
        pool.setDataSource(new InitializedDriverDataSource(DatabaseVendor.OCEANBASE_ORACLE, System.getenv("FLOW_OB_ORACLE_TEST_URL"),
                System.getenv("FLOW_OB_ORACLE_TEST_USER"), System.getenv("FLOW_OB_ORACLE_TEST_PASSWORD"),
                "com.oceanbase.jdbc.Driver", null));
        pool.setMaximumPoolSize(2);
        pool.setMinimumIdle(0);
        pool.setConnectionTimeout(30_000);
        pool.setValidationTimeout(10_000);
        return pool;
    }
}
