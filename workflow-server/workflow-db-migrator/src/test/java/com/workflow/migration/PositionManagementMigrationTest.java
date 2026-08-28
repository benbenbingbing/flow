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
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用真实 MySQL 验证 V069/V070 的 DDL、负责人回填和 Flyway 前置阻断。
 */
@Testcontainers(disabledWithoutDocker = true)
class PositionManagementMigrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("workflow_position")
                    .withUsername("workflow_test")
                    .withPassword("workflow_test_password");

    @BeforeEach
    void cleanDatabase() {
        flyway().clean();
    }

    @Test
    void migrationsCreatePositionModelBackfillLeaderAndRegisterDisabledResolver()
            throws Exception {
        flywayThrough68().migrate();
        insertEnabledUser("leader-1", "leader_one");
        insertOrganization("org-1", "ORG_1", "0", "org", "leader-1");
        try (Connection connection = MYSQL.createConnection("")) {
            assertDoesNotThrow(() -> BusinessMigrationPreflight.verify(connection));
        }

        Flyway current = flyway();
        current.migrate();

        assertEquals(0, current.info().pending().length);
        assertTrue(tableExists("sys_position"));
        assertTrue(tableExists("sys_position_assignment"));
        assertTrue(tableExists("sys_position_assignment_batch"));
        assertTrue(columnExists("sys_organization", "business_level_code"));
        assertEquals(1, countRows("""
                SELECT COUNT(*)
                FROM sys_position_assignment assignment_record
                JOIN sys_position position_record
                  ON position_record.id = assignment_record.position_id
                WHERE position_record.position_code = 'UNIT_LEADER'
                  AND assignment_record.organization_unit_id = 'org-1'
                  AND assignment_record.user_id = 'leader-1'
                  AND assignment_record.is_primary = 1
                  AND assignment_record.effective_to IS NULL
                  AND assignment_record.revoked_at IS NULL
                """));
        assertEquals(6, countRows("""
                SELECT COUNT(*) FROM sys_dict_item
                WHERE dict_code = 'organization_business_level'
                  AND deleted = 0
                """));
        assertEquals(1, countRows("""
                SELECT COUNT(*)
                FROM sys_menu position_menu
                JOIN sys_menu system_parent
                  ON system_parent.id = position_menu.parent_id
                WHERE position_menu.id = 'position_management_menu_001'
                  AND position_menu.path = '/system/position'
                  AND position_menu.perm = 'system:position:view'
                  AND system_parent.id = '400'
                  AND system_parent.parent_id = '0'
                  AND system_parent.menu_name = '系统管理'
                  AND system_parent.menu_type = 'M'
                  AND system_parent.status = '0'
                  AND system_parent.visible = '0'
                  AND system_parent.deleted = 0
                """));
        assertEquals(4, countRows("""
                SELECT COUNT(*)
                FROM sys_role_menu role_menu
                JOIN sys_role role_record ON role_record.id = role_menu.role_id
                WHERE role_record.role_code = 'super_admin'
                  AND role_menu.menu_id IN (
                    '400',
                    'position_management_menu_001',
                    'position_manage_permission_001',
                    'position_assign_permission_001')
                """));
        assertEquals(1, countRows("""
                SELECT COUNT(*) FROM process_person_resolver_definition
                WHERE resolver_code = 'relativeOrgPosition'
                  AND bean_name = 'relativeOrgPositionPersonResolver'
                  AND supported_usages_document =
                    '["ASSIGNEE","CANDIDATE","MULTI_INSTANCE"]'
                  AND dynamic_extra_params = 0
                  AND enabled = 0
                """));
        assertEquals(columnCollation("sys_user", "id"),
                columnCollation("sys_position_assignment", "user_id"));
        assertEquals(columnCollation("sys_organization", "id"),
                columnCollation(
                        "sys_position_assignment", "organization_unit_id"));
    }

    @Test
    void v070PreservesValidExistingSystemParentAndGrant()
            throws Exception {
        flywayThrough69().migrate();
        execute("""
                INSERT INTO sys_menu (
                  id, parent_id, menu_name, menu_type, icon, sort, path,
                  status, visible, deleted, remark
                ) VALUES (
                  '400', '0', '系统管理', 'M', 'LegacySetting', 77,
                  '/system', '0', '0', 0, 'existing-system-parent'
                )
                """);
        execute("""
                INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
                SELECT 'existing-system-parent-grant', id, '400', CURRENT_TIMESTAMP
                FROM sys_role
                WHERE role_code = 'super_admin' AND deleted = 0
                """);
        try (Connection connection = MYSQL.createConnection("")) {
            assertDoesNotThrow(() ->
                    BusinessMigrationPreflight.verify(connection));
        }

        flyway().migrate();

        assertEquals(1, countRows("""
                SELECT COUNT(*) FROM sys_menu
                WHERE id = '400'
                  AND icon = 'LegacySetting'
                  AND sort = 77
                  AND remark = 'existing-system-parent'
                """));
        assertEquals(1, countRows("""
                SELECT COUNT(*) FROM sys_role_menu
                WHERE id = 'existing-system-parent-grant'
                  AND menu_id = '400'
                """));
        assertEquals(4, countRows("""
                SELECT COUNT(*)
                FROM sys_role_menu role_menu
                JOIN sys_role role_record ON role_record.id = role_menu.role_id
                WHERE role_record.role_code = 'super_admin'
                  AND role_menu.menu_id IN (
                    '400',
                    'position_management_menu_001',
                    'position_manage_permission_001',
                    'position_assign_permission_001')
                """));
    }

    @Test
    void preflightRejectsDanglingAndDisabledLeadersBeforeAnyPositionDdl()
            throws Exception {
        flywayThrough68().migrate();
        insertOrganization("org-1", "ORG_1", "0", "org", "missing-user");

        IllegalStateException dangling = verifyFails();
        assertTrue(dangling.getMessage().contains("悬空或已禁用"));
        assertFalse(columnExists("sys_organization", "business_level_code"));

        execute("UPDATE sys_organization SET leader_id = '1' WHERE id = 'org-1'");
        IllegalStateException disabled = verifyFails();
        assertTrue(disabled.getMessage().contains("悬空或已禁用"));
        assertFalse(tableExists("sys_position"));
    }

    @Test
    void preflightRejectsEveryReservedSeedIdentifierAndBusinessCode()
            throws Exception {
        flywayThrough68().migrate();

        execute("""
                INSERT INTO sys_dict (
                  id, dict_code, dict_name, status, sort, deleted
                ) VALUES (
                  'foreign-dict', 'organization_business_level',
                  '其他语义', '0', 0, 0
                )
                """);
        assertTrue(verifyFails().getMessage().contains("字典编码已被占用"));
        execute("DELETE FROM sys_dict WHERE id = 'foreign-dict'");

        execute("""
                INSERT INTO sys_dict (
                  id, dict_code, dict_name, status, sort, deleted
                ) VALUES (
                  'dict_org_business_level_001', 'foreign_dictionary',
                  '其他字典', '0', 0, 0
                )
                """);
        assertTrue(verifyFails().getMessage().contains("字典固定 ID 已被占用"));
        execute("DELETE FROM sys_dict WHERE id = 'dict_org_business_level_001'");

        List<String> itemIds = List.of(
                "dict_org_level_group_001",
                "dict_org_level_company_001",
                "dict_org_level_center_001",
                "dict_org_level_dept1_001",
                "dict_org_level_dept2_001",
                "dict_org_level_team_001");
        for (String itemId : itemIds) {
            insertForeignDictionaryItem(itemId, "foreign_dictionary");
            assertTrue(verifyFails().getMessage()
                    .contains("代码项固定 ID 已被占用"));
            execute("DELETE FROM sys_dict_item WHERE id = '" + itemId + "'");
        }

        insertForeignDictionaryItem(
                "foreign-business-level-item",
                "organization_business_level");
        assertTrue(verifyFails().getMessage().contains("代码项语义已被占用"));
        execute("DELETE FROM sys_dict_item WHERE id = 'foreign-business-level-item'");

        execute("""
                CREATE TABLE sys_position (
                  id varchar(64) NOT NULL PRIMARY KEY,
                  position_code varchar(100) NOT NULL
                ) ENGINE=InnoDB
                """);
        execute("""
                INSERT INTO sys_position (id, position_code)
                VALUES ('position_unit_leader_001', 'FOREIGN_POSITION')
                """);
        assertTrue(verifyFails().getMessage().contains("UNIT_LEADER"));
    }

    @Test
    void preflightRejectsEveryPartialV069TargetBeforeMigrationDdl()
            throws Exception {
        flywayThrough68().migrate();

        execute("""
                ALTER TABLE sys_organization
                ADD COLUMN business_level_code varchar(50) NULL
                """);
        assertTrue(verifyFails().getMessage().contains("部分迁移状态"));
        execute("""
                ALTER TABLE sys_organization
                DROP COLUMN business_level_code
                """);

        execute("""
                CREATE TABLE sys_position (
                  id varchar(64) NOT NULL PRIMARY KEY,
                  position_code varchar(100) NOT NULL
                ) ENGINE=InnoDB
                """);
        assertTrue(verifyFails().getMessage().contains("部分迁移状态"));
        execute("DROP TABLE sys_position");

        for (String table : List.of(
                "sys_position_assignment",
                "sys_position_assignment_batch")) {
            execute("CREATE TABLE " + table
                    + " (id varchar(64) NOT NULL PRIMARY KEY) ENGINE=InnoDB");
            assertTrue(verifyFails().getMessage().contains("部分迁移状态"));
            execute("DROP TABLE " + table);
        }
        assertFalse(tableExists("sys_position"));
    }

    @Test
    void preflightRejectsAllReservedV070MenuAndResolverResourcesBeforeV069Ddl()
            throws Exception {
        flywayThrough68().migrate();

        insertMenu("400", "错误目录", "M", "/system", null, "0");
        assertTrue(verifyFails().getMessage().contains("父目录 400"));
        execute("DELETE FROM sys_menu WHERE id = '400'");

        insertMenu("400", "系统管理", "M", "/system", null, "1");
        assertTrue(verifyFails().getMessage().contains("父目录 400"));
        execute("DELETE FROM sys_menu WHERE id = '400'");

        execute("""
                INSERT INTO sys_menu (
                  id, parent_id, menu_name, menu_type, path, perm,
                  status, visible, deleted
                ) VALUES (
                  '400', NULL, '系统管理', 'M', NULL, NULL,
                  NULL, '0', 0
                )
                """);
        assertTrue(verifyFails().getMessage().contains("父目录 400"));
        execute("DELETE FROM sys_menu WHERE id = '400'");

        for (String id : List.of(
                "position_management_menu_001",
                "position_manage_permission_001",
                "position_assign_permission_001")) {
            insertMenu(id, "其他资源", "F", "", null, "0");
            assertTrue(verifyFails().getMessage().contains("菜单固定 ID"));
            execute("DELETE FROM sys_menu WHERE id = '" + id + "'");
        }

        insertMenu(
                "foreign-position-path", "其他页面", "C",
                "/system/position", null, "0");
        assertTrue(verifyFails().getMessage().contains("菜单路径已被占用"));
        execute("DELETE FROM sys_menu WHERE id = 'foreign-position-path'");

        int permissionIndex = 0;
        for (String permission : List.of(
                "system:position:view",
                "system:position:manage",
                "system:position:assign")) {
            String id = "foreign-position-permission-" + permissionIndex++;
            insertMenu(id, "其他权限", "F", "", permission, "0");
            assertTrue(verifyFails().getMessage().contains("权限标识已被占用"));
            execute("DELETE FROM sys_menu WHERE id = '" + id + "'");
        }

        insertResolver(
                "person_resolver_relative_position_001",
                "foreignResolver");
        assertTrue(verifyFails().getMessage().contains("解析器固定 ID 或编码"));
        execute("""
                DELETE FROM process_person_resolver_definition
                WHERE id = 'person_resolver_relative_position_001'
                """);

        insertResolver("foreign-resolver-id", "relativeOrgPosition");
        assertTrue(verifyFails().getMessage().contains("解析器固定 ID 或编码"));
        assertFalse(tableExists("sys_position"));
    }

    @Test
    void preflightUsesTheSameMaximumOfThirtyTwoHierarchyNodes()
            throws Exception {
        flywayThrough68().migrate();
        for (int index = 0; index < 32; index++) {
            insertOrganization(
                    "unit-" + index,
                    "UNIT_" + index,
                    index == 0 ? "0" : "unit-" + (index - 1),
                    "org",
                    null);
        }
        try (Connection connection = MYSQL.createConnection("")) {
            assertDoesNotThrow(() -> BusinessMigrationPreflight.verify(connection));
        }

        insertOrganization("unit-32", "UNIT_32", "unit-31", "org", null);

        IllegalStateException exception = verifyFails();
        assertTrue(exception.getMessage().contains("超过 32 层"));
    }

    private IllegalStateException verifyFails() throws Exception {
        try (Connection connection = MYSQL.createConnection("")) {
            return assertThrows(
                    IllegalStateException.class,
                    () -> BusinessMigrationPreflight.verify(connection));
        }
    }

    private void insertForeignDictionaryItem(String id, String dictCode)
            throws Exception {
        execute("""
                INSERT INTO sys_dict_item (
                  id, dict_id, dict_code, parent_id, item_code,
                  item_label, item_value, sort, status, deleted
                ) VALUES (
                  '%s', 'foreign-dict', '%s', '0', 'FOREIGN',
                  '其他语义', 'FOREIGN', 0, '0', 0
                )
                """.formatted(id, dictCode));
    }

    private void insertMenu(
            String id,
            String name,
            String type,
            String path,
            String permission,
            String status) throws Exception {
        String permissionSql = permission == null
                ? "NULL" : "'" + permission + "'";
        execute("""
                INSERT INTO sys_menu (
                  id, parent_id, menu_name, menu_type, path, perm,
                  status, visible, deleted
                ) VALUES (
                  '%s', '0', '%s', '%s', '%s', %s, '%s', '0', 0
                )
                """.formatted(
                    id, name, type, path, permissionSql, status));
    }

    private void insertResolver(String id, String code) throws Exception {
        execute("""
                INSERT INTO process_person_resolver_definition (
                  id, resolver_code, display_name, bean_name,
                  implementation_version, contract_version,
                  dynamic_extra_params, enabled, revision, deleted
                ) VALUES (
                  '%s', '%s', '其他解析器', 'foreignResolverBean',
                  1, 1, 0, 0, 1, 0
                )
                """.formatted(id, code));
    }

    private void insertEnabledUser(String id, String username)
            throws Exception {
        execute("""
                INSERT INTO sys_user (
                  id, username, nickname, password, status, deleted,
                  password_reset_required
                ) VALUES (
                  '%s', '%s', '%s', 'test-password-hash', '0', 0, 0
                )
                """.formatted(id, username, username));
    }

    private void insertOrganization(
            String id,
            String code,
            String parentId,
            String type,
            String leaderId) throws Exception {
        String leader = leaderId == null ? "NULL" : "'" + leaderId + "'";
        execute("""
                INSERT INTO sys_organization (
                  id, org_code, org_name, type, parent_id, level, path,
                  leader_id, status, deleted
                ) VALUES (
                  '%s', '%s', '%s', '%s', '%s', 0, '/', %s, '0', 0
                )
                """.formatted(id, code, code, type, parentId, leader));
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

    private Flyway flywayThrough68() {
        return Flyway.configure()
                .dataSource(
                        MYSQL.getJdbcUrl(),
                        MYSQL.getUsername(),
                        MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("68"))
                .cleanDisabled(false)
                .load();
    }

    private Flyway flywayThrough69() {
        return Flyway.configure()
                .dataSource(
                        MYSQL.getJdbcUrl(),
                        MYSQL.getUsername(),
                        MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("69"))
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

    private boolean tableExists(String table) throws Exception {
        try (Connection connection = MYSQL.createConnection("");
             ResultSet result = connection.getMetaData().getTables(
                     MYSQL.getDatabaseName(), null, table,
                     new String[]{"TABLE"})) {
            return result.next();
        }
    }

    private boolean columnExists(String table, String column)
            throws Exception {
        try (Connection connection = MYSQL.createConnection("");
             ResultSet result = connection.getMetaData().getColumns(
                     MYSQL.getDatabaseName(), null, table, column)) {
            return result.next();
        }
    }

    private String columnCollation(String table, String column)
            throws Exception {
        try (Connection connection = MYSQL.createConnection("");
             var statement = connection.prepareStatement("""
                     SELECT collation_name
                     FROM information_schema.columns
                     WHERE table_schema = ? AND table_name = ? AND column_name = ?
                     """)) {
            statement.setString(1, MYSQL.getDatabaseName());
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }
}
