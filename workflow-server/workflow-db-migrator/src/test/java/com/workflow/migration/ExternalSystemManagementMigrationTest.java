package com.workflow.migration;

import com.workflow.migration.runner.BusinessMigrationPreflight;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用真实 MySQL 验证 V079 外部系统配置表、菜单资源和迁移前置阻断。
 */
@Testcontainers(disabledWithoutDocker = true)
class ExternalSystemManagementMigrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("workflow_external_system")
                    .withUsername("workflow_test")
                    .withPassword("workflow_test_password");

    @BeforeEach
    void cleanDatabase() {
        flyway().clean();
    }

    @Test
    void v079CreatesExternalSystemSchemaAndRegistersManagementMenu()
            throws Exception {
        flywayThrough78().migrate();
        try (Connection connection = MYSQL.createConnection("")) {
            assertDoesNotThrow(() -> BusinessMigrationPreflight.verify(connection));
        }

        Flyway current = flyway();
        current.migrate();

        assertEquals(0, current.info().pending().length);
        assertTrue(tableExists("sys_external_system"));
        assertTrue(tableExists("sys_external_system_parameter"));
        assertEquals("utf8mb4_unicode_ci",
                tableCollation("sys_external_system"));
        assertEquals("utf8mb4_unicode_ci",
                tableCollation("sys_external_system_parameter"));
        assertTrue(columnExtra(
                "sys_external_system_parameter",
                "active_parameter_name_en").contains("STORED GENERATED"));
        assertEquals("RESTRICT", deleteRule(
                "sys_external_system_parameter",
                "fk_sys_external_system_parameter_system"));
        assertEquals(8, countRows("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                    'sys_external_system',
                    'sys_external_system_parameter'
                  )
                  AND column_name IN (
                    'created_by', 'updated_by', 'create_time', 'update_time'
                  )
                """));

        assertEquals(3, countRows("""
                SELECT COUNT(*)
                FROM sys_menu
                WHERE (
                    id = 'external_system_menu_001'
                    AND parent_id = '400'
                    AND menu_name = '外部系统'
                    AND menu_type = 'C'
                    AND path = '/system/external-systems'
                    AND component = 'system/ExternalSystem'
                    AND perm IS NULL
                    AND status = '0' AND visible = '0' AND deleted = 0
                  ) OR (
                    id = 'external_system_view_permission_001'
                    AND parent_id = 'external_system_menu_001'
                    AND menu_type = 'F'
                    AND perm = 'system:external-system:view'
                    AND status = '0' AND deleted = 0
                  ) OR (
                    id = 'external_system_manage_permission_001'
                    AND parent_id = 'external_system_menu_001'
                    AND menu_type = 'F'
                    AND perm = 'system:external-system:manage'
                    AND status = '0' AND deleted = 0
                  )
                """));
        assertEquals(1, countRows("""
                SELECT COUNT(*)
                FROM sys_menu
                WHERE perm = 'system:external-system:view'
                  AND deleted = 0
                """));
        assertEquals(4, countRows("""
                SELECT COUNT(*)
                FROM sys_role_menu role_menu
                JOIN sys_role role_record ON role_record.id = role_menu.role_id
                WHERE role_record.role_code = 'super_admin'
                  AND role_record.deleted = 0
                  AND role_menu.menu_id IN (
                    '400',
                    'external_system_menu_001',
                    'external_system_view_permission_001',
                    'external_system_manage_permission_001'
                  )
                """));
    }

    @Test
    void v079EnforcesCodesActiveParameterNamesChecksAndRestrictedDelete()
            throws Exception {
        flyway().migrate();

        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO sys_external_system (
                  id, system_name, system_code, status, address, deleted
                ) VALUES (
                  'invalid-status', '非法状态', 'INVALID_STATUS', '2',
                  'https://invalid.example', 0
                )
                """));
        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO sys_external_system (
                  id, system_name, system_code, status, address,
                  version, deleted
                ) VALUES (
                  'invalid-version', '非法版本号', 'INVALID_VERSION', '0',
                  'https://invalid.example', -1, 0
                )
                """));
        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO sys_external_system (
                  id, system_name, system_code, status, address, deleted
                ) VALUES (
                  'invalid-deleted', '非法删除标记', 'INVALID_DELETED', '0',
                  'https://invalid.example', 2
                )
                """));

        execute("""
                INSERT INTO sys_external_system (
                  id, system_name, system_code, status, address,
                  description, created_by, updated_by, deleted
                ) VALUES (
                  'external-1', '示例外部系统', 'EXAMPLE_SYSTEM', '0',
                  'https://example.test', '约束测试', 'tester', 'tester', 0
                )
                """);
        execute("""
                INSERT INTO sys_external_system_parameter (
                  id, external_system_id, parameter_name_zh,
                  parameter_name_en, parameter_value, sort_order,
                  created_by, updated_by, deleted
                ) VALUES (
                  'parameter-1', 'external-1', '租户编码', 'tenantCode',
                  'tenant-a', 1, 'tester', 'tester', 0
                )
                """);

        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO sys_external_system_parameter (
                  id, external_system_id, parameter_name_zh,
                  parameter_name_en, parameter_value, sort_order, deleted
                ) VALUES (
                  'parameter-duplicate', 'external-1', '重复租户编码',
                  'TENANTCODE', 'tenant-b', 2, 0
                )
                """));
        execute("""
                UPDATE sys_external_system_parameter
                SET deleted = 1, updated_by = 'tester'
                WHERE id = 'parameter-1'
                """);
        execute("""
                INSERT INTO sys_external_system_parameter (
                  id, external_system_id, parameter_name_zh,
                  parameter_name_en, parameter_value, sort_order, deleted
                ) VALUES (
                  'parameter-2', 'external-1', '新租户编码', 'tenantCode',
                  'tenant-b', 2, 0
                )
                """);
        assertEquals(1, countRows("""
                SELECT COUNT(*)
                FROM sys_external_system_parameter
                WHERE external_system_id = 'external-1'
                  AND parameter_name_en = 'tenantCode'
                  AND deleted = 0
                """));

        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO sys_external_system_parameter (
                  id, external_system_id, parameter_name_zh,
                  parameter_name_en, parameter_value, sort_order, deleted
                ) VALUES (
                  'parameter-negative-sort', 'external-1', '非法排序',
                  'negativeSort', 'x', -1, 0
                )
                """));
        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO sys_external_system_parameter (
                  id, external_system_id, parameter_name_zh,
                  parameter_name_en, parameter_value, sort_order, deleted
                ) VALUES (
                  'parameter-invalid-deleted', 'external-1', '非法删除标记',
                  'invalidDeleted', 'x', 1, 2
                )
                """));
        assertThrows(SQLException.class,
                () -> execute("DELETE FROM sys_external_system "
                        + "WHERE id = 'external-1'"));

        execute("UPDATE sys_external_system SET deleted = 1 "
                + "WHERE id = 'external-1'");
        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO sys_external_system (
                  id, system_name, system_code, status, address, deleted
                ) VALUES (
                  'external-2', '编码复用系统', 'EXAMPLE_SYSTEM', '0',
                  'https://other.example', 0
                )
                """));
    }

    @Test
    void preflightRejectsPartialV079TablesAndConflictingMenuResources()
            throws Exception {
        flywayThrough78().migrate();

        execute("CREATE TABLE sys_external_system (id varchar(64) PRIMARY KEY)");
        assertTrue(verifyFails().getMessage().contains("目标表已存在"));
        execute("DROP TABLE sys_external_system");

        execute("""
                CREATE TABLE sys_external_system_parameter (
                  id varchar(64) PRIMARY KEY
                )
                """);
        assertTrue(verifyFails().getMessage().contains("目标表已存在"));
        execute("DROP TABLE sys_external_system_parameter");

        execute("UPDATE sys_menu SET status = '1' WHERE id = '400'");
        assertTrue(verifyFails().getMessage().contains("父目录 400 语义错误"));
        execute("UPDATE sys_menu SET status = '0' WHERE id = '400'");

        execute("""
                INSERT INTO sys_menu (
                  id, parent_id, menu_name, menu_type,
                  status, visible, deleted
                ) VALUES (
                  'external_system_menu_001', '0', '冲突菜单', 'C',
                  '0', '0', 0
                )
                """);
        assertTrue(verifyFails().getMessage().contains("固定 ID 已被占用"));
        execute("DELETE FROM sys_menu WHERE id = 'external_system_menu_001'");

        execute("""
                INSERT INTO sys_menu (
                  id, parent_id, menu_name, menu_type, path,
                  status, visible, deleted
                ) VALUES (
                  'foreign-external-path', '0', '冲突路径', 'C',
                  '/system/external-systems', '0', '0', 0
                )
                """);
        assertTrue(verifyFails().getMessage().contains("菜单路径已被占用"));
        execute("DELETE FROM sys_menu WHERE id = 'foreign-external-path'");

        execute("""
                INSERT INTO sys_menu (
                  id, parent_id, menu_name, menu_type, perm,
                  status, visible, deleted
                ) VALUES (
                  'foreign-external-perm', '0', '冲突权限', 'F',
                  'system:external-system:manage', '0', '0', 0
                )
                """);
        assertTrue(verifyFails().getMessage().contains("权限标识已被占用"));
    }

    private IllegalStateException verifyFails() throws Exception {
        try (Connection connection = MYSQL.createConnection("")) {
            return assertThrows(IllegalStateException.class,
                    () -> BusinessMigrationPreflight.verify(connection));
        }
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(
                        MYSQL.getJdbcUrl(),
                        MYSQL.getUsername(),
                        MYSQL.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
    }

    private Flyway flywayThrough78() {
        return Flyway.configure()
                .dataSource(
                        MYSQL.getJdbcUrl(),
                        MYSQL.getUsername(),
                        MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("78"))
                .cleanDisabled(false)
                .load();
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = MYSQL.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int countRows(String sql) throws Exception {
        try (Connection connection = MYSQL.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private boolean tableExists(String tableName) throws Exception {
        return countRows("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = '%s'
                """.formatted(tableName)) == 1;
    }

    private String tableCollation(String tableName) throws Exception {
        return queryString("""
                SELECT table_collation
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = '%s'
                """.formatted(tableName));
    }

    private String columnExtra(String tableName, String columnName)
            throws Exception {
        return queryString("""
                SELECT extra
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = '%s'
                  AND column_name = '%s'
                """.formatted(tableName, columnName));
    }

    private String deleteRule(String tableName, String constraintName)
            throws Exception {
        return queryString("""
                SELECT delete_rule
                FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name = '%s'
                  AND constraint_name = '%s'
                """.formatted(tableName, constraintName));
    }

    private String queryString(String sql) throws Exception {
        try (Connection connection = MYSQL.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }
}
