package com.workflow.dbmigrator;

import db.migration.V130__simplify_position_membership;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 直接执行实际 V130，验证有损清理边界、当前关系约束和重复升级。 */
class PositionMembershipSimplificationMigrationTest {
    @TempDir Path migrations;
    private static final String SCHEMA = """
            CREATE TABLE sys_position(id VARCHAR(64) PRIMARY KEY, position_code VARCHAR(100), holder_mode VARCHAR(16));
            CREATE TABLE sys_organization(id VARCHAR(64) PRIMARY KEY, leader_id VARCHAR(64), leader_name VARCHAR(100));
            CREATE TABLE sys_user(id VARCHAR(64) PRIMARY KEY, nickname VARCHAR(100), username VARCHAR(100));
            CREATE TABLE sys_position_assignment(id VARCHAR(64) PRIMARY KEY, position_id VARCHAR(64),
              organization_unit_id VARCHAR(64), user_id VARCHAR(64), revision INTEGER, created_by VARCHAR(64),
              updated_by VARCHAR(64), create_time TIMESTAMP, update_time TIMESTAMP, effective_from TIMESTAMP,
              effective_to TIMESTAMP, revoked_at TIMESTAMP, is_primary SMALLINT, sort_order INTEGER,
              revoked_by VARCHAR(64), revoke_reason VARCHAR(500));
            CREATE TABLE sys_position_assignment_batch(id VARCHAR(64));
            INSERT INTO sys_position VALUES('p','UNIT_LEADER','SINGLE');
            INSERT INTO sys_organization VALUES('org','old','旧负责人');
            INSERT INTO sys_user VALUES('u','当前人员','current'),('other','其他人','other');
            """;

    @Test void retainsOnlyCurrentRelationsAndRemovesRetiredObjects() throws Exception {
        try (Connection connection = database()) {
            schema(connection);
            insert(connection, "current", "u", "2000-01-01", null, null);
            insert(connection, "expired", "other", "2000-01-01", "2001-01-01", null);
            insert(connection, "future", "other", "2099-01-01", null, null);
            insert(connection, "revoked", "other", "2000-01-01", null, "2001-01-01");
            migrate(connection); migrate(connection);
            try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT id, user_id FROM sys_position_assignment")) {
                assertTrue(rows.next()); assertEquals("current", rows.getString(1)); assertEquals("u", rows.getString(2));
                assertFalse(rows.next());
            }
            try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT * FROM sys_position_assignment")) {
                assertEquals(9, rows.getMetaData().getColumnCount());
            }
            try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT leader_id FROM sys_organization WHERE id='org'")) {
                assertTrue(rows.next()); assertEquals("u", rows.getString(1));
            }
            assertThrows(SQLException.class, () -> connection.createStatement().executeQuery("SELECT * FROM sys_position_assignment_batch"));
            assertThrows(SQLException.class, () -> connection.createStatement().executeUpdate("INSERT INTO sys_position_assignment "
                    + "(id,position_id,organization_unit_id,user_id) VALUES ('duplicate','p','org','u')"));
        }
    }

    @Test void rejectsAmbiguousCurrentSingleHolderBeforeDroppingHistory() throws Exception {
        try (Connection connection = database()) {
            schema(connection);
            insert(connection, "a", "u", "2000-01-01", null, null);
            insert(connection, "b", "other", "2000-01-01", null, null);
            assertThrows(SQLException.class, () -> migrate(connection));
            try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT COUNT(*) FROM sys_position_assignment")) {
                assertTrue(rows.next()); assertEquals(2, rows.getInt(1));
            }
        }
    }

    @Test void resumesAfterOldTableWasDroppedBeforeRename() throws Exception {
        try (Connection connection = database()) {
            schema(connection);
            insert(connection, "current", "u", "2000-01-01", null, null);
            migrate(connection);
            connection.createStatement().execute("ALTER TABLE sys_position_assignment RENAME TO sys_position_member_stage");
            migrate(connection);
            try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT id FROM sys_position_assignment")) {
                assertTrue(rows.next()); assertEquals("current", rows.getString(1)); assertFalse(rows.next());
            }
        }
    }

    @Test void flywayAppliesUpgradeAndValidatesWithoutChangingHistoricalMigration() throws Exception {
        try (Connection connection = database()) {
            Files.writeString(migrations.resolve("V129__position_fixture.sql"), SCHEMA);
            var flyway = Flyway.configure().dataSource(connection.getMetaData().getURL(), "sa", "")
                    .locations("filesystem:" + migrations).javaMigrations(new V130__simplify_position_membership()).load();
            assertEquals(2, flyway.migrate().migrationsExecuted);
            flyway.validate();
            assertEquals(0, flyway.migrate().migrationsExecuted);
        }
    }

    /** 可选隔离 MySQL 实测，URL 由测试调用显式提供，不读取或连接应用业务数据库。 */
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "POSITION_MYSQL_URL", matches = ".+")
    void upgradesIsolatedMysqlAndKeepsForeignKeyCollationsCompatible() throws Exception {
        try (Connection connection = DriverManager.getConnection(System.getenv("POSITION_MYSQL_URL"), "root", "")) {
            String testSchema = "position_upgrade_" + UUID.randomUUID().toString().replace("-", "");
            connection.createStatement().execute("CREATE DATABASE " + testSchema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            connection.setCatalog(testSchema);
            try {
            schema(connection);
            insert(connection, "current", "u", "2000-01-01", null, null);
            insert(connection, "expired", "other", "2000-01-01", "2001-01-01", null);
            insert(connection, "future", "other", "2099-01-01", null, null);
            insert(connection, "revoked", "other", "2000-01-01", null, "2001-01-01");
            migrate(connection); migrate(connection);
            try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT * FROM sys_position_assignment")) {
                assertEquals(9, rows.getMetaData().getColumnCount());
                assertTrue(rows.next()); assertEquals("current", rows.getString("id")); assertFalse(rows.next());
            }
            assertThrows(SQLException.class, () -> connection.createStatement().executeUpdate("INSERT INTO sys_position_assignment "
                    + "(id,position_id,organization_unit_id,user_id) VALUES ('dangling','p','org','missing')"));
            assertThrows(SQLException.class, () -> connection.createStatement().executeUpdate("INSERT INTO sys_position_assignment "
                    + "(id,position_id,organization_unit_id,user_id) VALUES ('duplicate','p','org','u')"));
            } finally {
                connection.createStatement().execute("DROP DATABASE " + testSchema);
            }
        }
    }

    private void schema(Connection connection) throws Exception {
        String schema = connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL")
                ? SCHEMA.replace("TIMESTAMP", "DATETIME(6)") : SCHEMA;
        for (String sql : schema.split(";")) if (!sql.isBlank()) connection.createStatement().execute(sql);
    }
    private void insert(Connection connection, String id, String user, String from, String to, String revoked) throws Exception {
        try (var sql = connection.prepareStatement("INSERT INTO sys_position_assignment "
                + "(id,position_id,organization_unit_id,user_id,revision,create_time,update_time,effective_from,effective_to,revoked_at) "
                + "VALUES (?,'p','org',?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,?,?)")) {
            sql.setString(1,id); sql.setString(2,user);
            var utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
            sql.setTimestamp(3, timestamp(from), utc);
            sql.setTimestamp(4, to == null ? null : timestamp(to), utc);
            sql.setTimestamp(5, revoked == null ? null : timestamp(revoked), utc);
            sql.executeUpdate();
        }
    }
    private java.sql.Timestamp timestamp(String date) {
        return java.sql.Timestamp.from(java.time.LocalDate.parse(date).atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
    }
    private void migrate(Connection connection) throws Exception {
        Context context = mock(Context.class); when(context.getConnection()).thenReturn(connection);
        new V130__simplify_position_membership().migrate(context);
    }
    private Connection database() throws Exception {
        return DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }
}
