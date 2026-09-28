package com.workflow.dbmigrator;

import db.migration.V110__task_handover_menu;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 使用隔离内存数据库验证菜单权限边界、冲突回滚和通用 JDBC 语法，不接触开发业务库。 */
class TaskHandoverMenuMigrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL", "Oracle"})
    void addsSystemMenuAndOnlyGrantsExistingSuperAdministrators(String mode) throws Exception {
        try (Connection connection = fixture(mode)) {
            migrate(connection);
            migrate(connection);

            assertEquals(1, count(connection, """
                    SELECT COUNT(*) FROM sys_menu WHERE id = 'task_handover_menu_001'
                      AND parent_id = '400' AND menu_type = 'C' AND menu_name = '人员交接'
                      AND path = '/system/task-handover' AND component = 'system/TaskHandoverManagement'
                      AND perm = 'system:task-handover:view' AND status = '0' AND deleted = 0
                    """));
            assertEquals(1, count(connection, """
                    SELECT COUNT(*) FROM sys_menu WHERE id = 'task_handover_transfer_001'
                      AND parent_id = 'task_handover_menu_001' AND menu_type = 'F'
                      AND perm = 'system:task-handover:transfer'
                    """));
            assertEquals(3, count(connection, "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 'super'"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM sys_role_menu WHERE role_id <> 'super'"));
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void conflictingTransferPermissionRollsBackEarlierMenuInsert() throws Exception {
        try (Connection connection = fixture("MySQL")) {
            execute(connection, """
                    INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perm, deleted)
                    VALUES ('tenant-permission', '400', '自定义操作', 'F', 'system:task-handover:transfer', 0)
                    """);

            SQLException error = assertThrows(SQLException.class, () -> migrate(connection));

            assertTrue(error.getMessage().contains("其他菜单占用"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM sys_menu WHERE id = 'task_handover_menu_001'"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM sys_role_menu"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM sys_menu WHERE id = 'tenant-permission'"));
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void refusesMissingParentAndReusedFixedMenuId() throws Exception {
        try (Connection connection = fixture("MySQL")) {
            execute(connection, "UPDATE sys_menu SET deleted = 1 WHERE id = '400'");
            assertThrows(SQLException.class, () -> migrate(connection));
            execute(connection, "UPDATE sys_menu SET deleted = 0 WHERE id = '400'");
            execute(connection, """
                    INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, deleted)
                    VALUES ('task_handover_menu_001', '400', '自定义入口', 'C', '/custom', 0)
                    """);
            assertThrows(SQLException.class, () -> migrate(connection));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM sys_menu WHERE path = '/custom'"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM sys_role_menu"));
        }
    }

    @Test
    void leavesExistingTransactionOwnershipWithFlyway() throws Exception {
        try (Connection connection = fixture("MySQL")) {
            connection.setAutoCommit(false);
            migrate(connection);
            assertFalse(connection.getAutoCommit());
            connection.rollback();
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM sys_menu WHERE id = 'task_handover_menu_001'"));
        }
    }

    private void migrate(Connection connection) throws SQLException {
        new V110__task_handover_menu().migrate(new Context() {
            @Override public Connection getConnection() { return connection; }
            @Override public Configuration getConfiguration() { return null; }
        });
    }

    private Connection fixture(String mode) throws SQLException {
        Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:handover_" + UUID.randomUUID() + ";MODE=" + mode);
        execute(connection, """
                CREATE TABLE sys_menu (
                  id VARCHAR(64) PRIMARY KEY, parent_id VARCHAR(64), menu_name VARCHAR(100), menu_type CHAR(1),
                  icon VARCHAR(100), sort INTEGER, path VARCHAR(200), component VARCHAR(255), perm VARCHAR(200),
                  status CHAR(1), visible CHAR(1), is_frame CHAR(1), is_cache CHAR(1), keep_alive CHAR(1),
                  breadcrumb CHAR(1), remark VARCHAR(500), deleted INTEGER, create_by VARCHAR(64),
                  create_time TIMESTAMP, update_by VARCHAR(64), update_time TIMESTAMP)
                """);
        execute(connection, "CREATE TABLE sys_role (id VARCHAR(64) PRIMARY KEY, role_code VARCHAR(64), deleted INTEGER)");
        execute(connection, """
                CREATE TABLE sys_role_menu (id VARCHAR(64) PRIMARY KEY, role_id VARCHAR(64), menu_id VARCHAR(64),
                  create_time TIMESTAMP, UNIQUE (role_id, menu_id))
                """);
        execute(connection, """
                INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, deleted)
                VALUES ('400', '0', '系统管理', 'M', '/system', 0)
                """);
        execute(connection, "INSERT INTO sys_role VALUES ('super', 'super_admin', 0)");
        execute(connection, "INSERT INTO sys_role VALUES ('regular', 'admin', 0)");
        execute(connection, "INSERT INTO sys_role VALUES ('removed', 'super_admin', 1)");
        return connection;
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private int count(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
