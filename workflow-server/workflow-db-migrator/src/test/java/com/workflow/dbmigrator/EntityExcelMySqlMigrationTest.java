package com.workflow.dbmigrator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 在隔离 MySQL 8.4 中直接执行实际 V115，验证 MySQL 的字符集、索引长度与唯一约束。
 * 初始化脚本只指定 V115，不扫描或执行历史迁移链；无 Docker 时由 Testcontainers 跳过。
 */
@Testcontainers(disabledWithoutDocker = true)
class EntityExcelMySqlMigrationTest {
    private static final Set<String> TABLES = Set.of(
            "entity_excel_settings", "entity_excel_scheme", "entity_excel_scheme_published",
            "entity_excel_job", "entity_excel_job_row");

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("workflow_excel_v115")
            .withUsername("workflow_test")
            .withPassword("workflow_test_password")
            .withInitScript("db/migration/V115__entity_excel_configuration_and_jobs.sql");

    /** 只清理本测试容器内的数据，各约束用例独立，建表脚本保持原样。 */
    @BeforeEach
    void clearRows() throws SQLException {
        try (Connection connection = MYSQL.createConnection(""); var statement = connection.createStatement()) {
            for (String table : TABLES) statement.executeUpdate("DELETE FROM " + table);
        }
    }

    @Test
    void v115CreatesExactlyFiveInnoDbUtf8mb4TablesAndFullRequestIndex() throws SQLException {
        try (Connection connection = MYSQL.createConnection(""); var statement = connection.createStatement()) {
            Set<String> actual = new TreeSet<>();
            try (var rows = statement.executeQuery("""
                    SELECT table_name, engine, table_collation
                      FROM information_schema.tables
                     WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
                    """)) {
                while (rows.next()) {
                    actual.add(rows.getString("table_name"));
                    assertEquals("InnoDB", rows.getString("engine"));
                    assertEquals("utf8mb4_unicode_ci", rows.getString("table_collation"));
                }
            }
            // 没有 Flyway 历史表或旧业务表，保证本用例不会被历史迁移故障掩盖。
            assertEquals(TABLES, actual);
            try (var rows = statement.executeQuery("""
                    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) AS indexed_columns,
                           MAX(non_unique) AS non_unique, COUNT(sub_part) AS prefix_columns
                      FROM information_schema.statistics
                     WHERE table_schema = DATABASE() AND table_name = 'entity_excel_job'
                       AND index_name = 'uk_excel_job_request'
                    """)) {
                rows.next();
                assertEquals("user_id,origin_key,request_id", rows.getString("indexed_columns"));
                assertEquals(0, rows.getInt("non_unique"));
                assertEquals(0, rows.getInt("prefix_columns"), "幂等键不能用前缀索引代替完整入口身份");
            }
        }
    }

    @Test
    void requestIdempotencyUsesCompleteUserOriginAndRequestEvenAtUtf8mb4Limits() throws SQLException {
        try (Connection connection = MYSQL.createConnection("")) {
            // 使用四字节字符填满各列，实际验证 InnoDB 的组合索引长度而非只插入短 ASCII。
            String user = "😀".repeat(64);
            String origin = "🧾".repeat(500);
            String request = "📄".repeat(128);
            insertJob(connection, "job1", user, origin, request);
            SQLException duplicate = assertThrows(SQLException.class,
                    () -> insertJob(connection, "duplicate", user, origin, request));
            assertEquals("23000", duplicate.getSQLState());
            assertEquals(1062, duplicate.getErrorCode(), "必须因唯一键冲突而失败");

            // 仅最后一个字符不同也必须可插入，证明三个维度均未被截断或遗漏。
            insertJob(connection, "job2", "😀".repeat(63) + "界", origin, request);
            insertJob(connection, "job3", user, "🧾".repeat(499) + "界", request);
            insertJob(connection, "job4", user, origin, "📄".repeat(127) + "界");
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM entity_excel_job")) {
                rows.next();
                assertEquals(4, rows.getInt(1));
            }
        }
    }

    @Test
    void settingsSchemePublicationAndOriginalCoordinatesRetainUniqueIdentity() throws SQLException {
        try (Connection connection = MYSQL.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO entity_excel_settings(entity_id,draft_json,updated_at) VALUES('e1','{}',CURRENT_TIMESTAMP)");
            assertDuplicate(() -> statement.executeUpdate("INSERT INTO entity_excel_settings(entity_id,draft_json,updated_at) VALUES('e1','{}',CURRENT_TIMESTAMP)"));
            statement.executeUpdate("INSERT INTO entity_excel_scheme(id,entity_id,scheme_code,name,draft_json,created_at,updated_at) VALUES('s1','e1','basic','基础','{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            assertDuplicate(() -> statement.executeUpdate("INSERT INTO entity_excel_scheme(id,entity_id,scheme_code,name,draft_json,created_at,updated_at) VALUES('s2','e1','basic','另一套','{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"));
            statement.executeUpdate("INSERT INTO entity_excel_scheme(id,entity_id,scheme_code,name,draft_json,created_at,updated_at) VALUES('s2','e2','basic','其他实体','{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO entity_excel_scheme_published(scheme_id,document_json,content_hash,published_at) VALUES('s1','{}','hash',CURRENT_TIMESTAMP)");
            assertDuplicate(() -> statement.executeUpdate("INSERT INTO entity_excel_scheme_published(scheme_id,document_json,content_hash,published_at) VALUES('s1','{}','changed',CURRENT_TIMESTAMP)"));
            statement.executeUpdate("INSERT INTO entity_excel_job_row(id,job_id,sheet_no,excel_row_number) VALUES('r1','job1',0,3)");
            assertDuplicate(() -> statement.executeUpdate("INSERT INTO entity_excel_job_row(id,job_id,sheet_no,excel_row_number) VALUES('r2','job1',0,3)"));
            statement.executeUpdate("INSERT INTO entity_excel_job_row(id,job_id,sheet_no,excel_row_number) VALUES('r2','job1',1,3)");
        }
    }

    private static void assertDuplicate(org.junit.jupiter.api.function.Executable action) {
        SQLException duplicate = assertThrows(SQLException.class, action);
        assertEquals("23000", duplicate.getSQLState());
        assertEquals(1062, duplicate.getErrorCode());
    }

    private static void insertJob(Connection connection, String id, String user, String origin, String request)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO entity_excel_job(id,entity_id,entity_code,operation,status,user_id,origin_key,
                  request_id,payload_hash,config_json,created_at,updated_at)
                VALUES(?,'e1','employee','IMPORT','RUNNING',?,?,?,'hash','{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """)) {
            statement.setString(1, id);
            statement.setString(2, user);
            statement.setString(3, origin);
            statement.setString(4, request);
            statement.executeUpdate();
        }
    }
}
