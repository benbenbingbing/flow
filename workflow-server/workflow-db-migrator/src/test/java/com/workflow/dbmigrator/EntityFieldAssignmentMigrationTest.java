package com.workflow.dbmigrator;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 执行实际 SQL 文件，验证手动升级与 Flyway 兼容、重复执行保留配置及七方言种子等价。 */
class EntityFieldAssignmentMigrationTest {
    private static final String MIGRATION = "V132__entity_field_assignment_action.sql";
    private static final String ID = "flow_action_definition_field_assignment";
    private static final String HANDLER = "entityFieldAssignmentFlowActionHandler";
    private static final String DESCRIPTION = "实际经过连线时给当前实体的已发布普通字段赋固定值或清空；事务内执行，失败回滚。";
    // 使用与存量表相同的非空及稳定标识唯一约束，避免 H2 掩盖目录碰撞。
    private static final String SCHEMA = """
            CREATE TABLE process_action_definition (
                id VARCHAR(64) PRIMARY KEY, action_code VARCHAR(200) NOT NULL UNIQUE,
                display_name VARCHAR(200) NOT NULL, description VARCHAR(1000),
                handler_name VARCHAR(200) NOT NULL UNIQUE, visibility_scope VARCHAR(20) NOT NULL,
                enabled SMALLINT NOT NULL, created_by VARCHAR(64),
                create_time TIMESTAMP NOT NULL, update_time TIMESTAMP NOT NULL, deleted SMALLINT NOT NULL
            );
            """;

    @TempDir Path migrations;

    @Test void flywaySeedsOnceAndRepeatNeverReenablesOrOverwritesAdministratorChanges() throws Exception {
        String url = databaseUrl();
        var flyway = flyway(url);
        assertEquals(2, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM process_action_definition")) {
                assertTrue(rows.next());
                assertEquals(ID, rows.getString("id"));
                assertEquals("实体字段赋值", rows.getString("display_name"));
                assertEquals(DESCRIPTION, rows.getString("description"));
                assertEquals("GLOBAL", rows.getString("visibility_scope"));
                assertEquals(1, rows.getInt("enabled"));
                assertFalse(rows.next());
            }
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE process_action_definition SET enabled=0, display_name='业务字段', "
                        + "visibility_scope='ENTITY', deleted=1, update_time='2000-01-01 00:00:00'");
            }
            executeMigration(connection);
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM process_action_definition")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt("enabled"));
                assertEquals(1, rows.getInt("deleted"));
                assertEquals("业务字段", rows.getString("display_name"));
                assertEquals("ENTITY", rows.getString("visibility_scope"));
                assertEquals("2000-01-01 00:00:00.0", rows.getTimestamp("update_time").toString());
                assertFalse(rows.next());
            }
        }
    }

    @Test void manuallyExecutedSqlCanLaterBeAppliedAndRecordedByFlyway() throws Exception {
        String url = databaseUrl();
        var flyway = flyway(url);
        // 先登记测试前置版本，再模拟 DBA 直接执行 V132；不篡改 Flyway 历史。
        assertEquals(1, Flyway.configure().dataSource(url, "sa", "")
                .locations("filesystem:" + migrations).target("1").load().migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            executeMigration(connection);
            executeMigration(connection);
        }
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM process_action_definition")) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1));
        }
    }

    @Test void preservesAdministratorCreatedDefinitionWithDifferentId() throws Exception {
        try (var connection = DriverManager.getConnection(databaseUrl(), "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute(SCHEMA);
            }
            insert(connection, "administrator-created-id", HANDLER, HANDLER);
            executeMigration(connection);
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT id, display_name FROM process_action_definition")) {
                assertTrue(rows.next());
                assertEquals("administrator-created-id", rows.getString("id"));
                assertEquals("管理员动作", rows.getString("display_name"));
                assertFalse(rows.next());
            }
        }
    }

    @Test void rejectsDifferentBeanNameCasingDespiteMysqlCaseInsensitiveCollation() throws Exception {
        try (var connection = DriverManager.getConnection(databaseUrl(), "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute(SCHEMA);
                // 让 H2 也模拟实际 MySQL 目录的不区分大小写唯一键。
                statement.execute("ALTER TABLE process_action_definition ALTER COLUMN handler_name VARCHAR_IGNORECASE(200) NOT NULL");
            }
            insert(connection, "administrator-created-id", HANDLER, HANDLER.toLowerCase(Locale.ROOT));
            assertThrows(SQLException.class, () -> executeMigration(connection));
        }
    }

    @Test void sevenScriptsHaveEquivalentSingleSeedWithoutStructureChanges() throws Exception {
        assertTrue(migrationSql().contains("'" + DESCRIPTION + "'"));
        String expected = null;
        for (String dialect : List.of("mysql", "pg", "oracle", "dameng", "kingbase", "obmysql", "oboracle")) {
            String script = Files.readString(Path.of("../../docs/sql/" + dialect + ".sql"));
            var seeds = script.lines().filter(line -> line.startsWith("INSERT INTO ")
                    && line.contains("'" + ID + "'")).toList();
            assertEquals(1, seeds.size(), dialect);
            String normalized = seeds.get(0).replace("`", "").replace("\"", "").toLowerCase(Locale.ROOT);
            assertTrue(normalized.contains(DESCRIPTION));
            assertTrue(normalized.contains("'" + HANDLER.toLowerCase(Locale.ROOT) + "'"));
            assertTrue(normalized.contains("'global',1,'system',current_timestamp,current_timestamp,0"));
            if (expected == null) expected = normalized; else assertEquals(expected, normalized, dialect);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ID", "ACTION_CODE", "HANDLER_NAME", "ID_WITH_VALID_BINDING"})
    void stableIdentityCollisionFailsWithoutChangingExistingCatalog(String collision) throws Exception {
        try (var connection = DriverManager.getConnection(databaseUrl(), "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute(SCHEMA);
            }
            String id = collision.startsWith("ID") ? ID : "another-action";
            insert(connection, id, collision.equals("ACTION_CODE") ? HANDLER : "custom",
                    collision.equals("HANDLER_NAME") ? HANDLER : "customHandler");
            // 正确处理器已配置、但稳定 ID 被另一动作占用时也不能静默登记成功。
            if (collision.equals("ID_WITH_VALID_BINDING")) {
                insert(connection, "administrator-created-id", HANDLER, HANDLER);
            }
            assertThrows(SQLException.class, () -> executeMigration(connection));
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM process_action_definition WHERE id='" + id + "'")) {
                assertTrue(rows.next());
                assertEquals(collision.equals("HANDLER_NAME") ? HANDLER : "customHandler", rows.getString("handler_name"));
                assertEquals("管理员动作", rows.getString("display_name"));
                assertFalse(rows.next());
            }
        }
    }

    /** 仅复制实际 SQL 与最小前置表，避免把其他历史迁移引入本次回归。 */
    private Flyway flyway(String url) throws Exception {
        Files.writeString(migrations.resolve("V001__test_catalog.sql"), SCHEMA);
        Files.writeString(migrations.resolve(MIGRATION), migrationSql());
        return Flyway.configure().dataSource(url, "sa", "").locations("filesystem:" + migrations).load();
    }

    private static String databaseUrl() {
        return "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    }

    /** 直接读取发布资源，让手动执行与 Flyway 路径始终测试同一份脚本。 */
    private static String migrationSql() throws Exception {
        try (var stream = EntityFieldAssignmentMigrationTest.class.getResourceAsStream("/db/migration/" + MIGRATION)) {
            assertNotNull(stream, "发布包必须包含 V132 SQL");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void executeMigration(Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute(migrationSql());
        }
    }

    private static void insert(Connection connection, String id, String actionCode, String handlerName) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO process_action_definition "
                + "(id, action_code, display_name, handler_name, visibility_scope, enabled, created_by, create_time, update_time, deleted) "
                + "VALUES (?, ?, '管理员动作', ?, 'ENTITY', 0, 'administrator', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)")) {
            statement.setString(1, id);
            statement.setString(2, actionCode);
            statement.setString(3, handlerName);
            statement.executeUpdate();
        }
    }
}
