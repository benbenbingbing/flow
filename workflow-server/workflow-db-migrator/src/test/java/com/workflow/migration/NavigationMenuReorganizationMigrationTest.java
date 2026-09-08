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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用真实 MySQL 验证 V075 菜单重组及 V076 路由对齐。 */
@Testcontainers(disabledWithoutDocker = true)
class NavigationMenuReorganizationMigrationTest {

    private static final String TARGET_MENU_IDS = """
            '300',
            '403',
            'list_column_template_menu_001',
            'dev_guide_dir',
            'flow_setting_menu_001',
            'dev_guide_list',
            'list_field_guide_v2_001',
            'custom_list_guide',
            'custom_form_guide',
            'flow_action_guide_menu_001',
            'extension_management_menu_001',
            '400',
            'work_calendar_menu_001',
            'sla_management_dir_001',
            'task_sla_policy_menu_001',
            'task_sla_monitor_menu_001'
            """;

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("workflow_navigation_menu")
                    .withUsername("workflow_test")
                    .withPassword("workflow_test_password");

    @BeforeEach
    void cleanDatabase() {
        flyway().clean();
    }

    @Test
    void freshDatabaseBuildsRequestedHierarchyAndSuperAdminGrants()
            throws Exception {
        Flyway current = flyway();
        current.migrate();

        assertEquals("79", current.info().current().getVersion().getVersion());
        assertRequestedHierarchy();
        assertSuperAdminOwnsTargetMenus();
        assertEquals(5, countRows("""
                SELECT COUNT(*)
                FROM sys_menu
                WHERE parent_id = 'flow_setting_menu_001'
                  AND menu_type IN ('M', 'C')
                  AND deleted = 0
                """));
    }

    @Test
    void migrationsReorganizeExistingLegacyRowsMigratedThroughV074()
            throws Exception {
        flywayThrough74().migrate();
        seedLegacyNavigation();

        assertMenu("403", "400", "用户组管理", "C", 41);
        assertMenu(
                "flow_setting_menu_001",
                "dev_guide_dir",
                "流程配置",
                "M",
                1);
        assertMenu(
                "list_column_template_menu_001",
                "400",
                "列表列模板",
                "C",
                73);

        try (Connection connection = MYSQL.createConnection("")) {
            assertDoesNotThrow(
                    () -> BusinessMigrationPreflight.verify(connection));
        }

        Flyway current = flyway();
        current.migrate();

        assertEquals("79", current.info().current().getVersion().getVersion());
        assertRequestedHierarchy();
        assertSuperAdminOwnsTargetMenus();
        assertMenu(
                "tenant_custom_dev_tool_001",
                "dev_guide_dir",
                "租户自定义工具",
                "C",
                99);
        assertEquals(4, countRows("""
                SELECT COUNT(*)
                FROM sys_menu
                WHERE id IN (
                    '403',
                    'dev_guide_list',
                    'custom_list_guide',
                    'custom_form_guide'
                  )
                  AND remark LIKE 'legacy-navigation-row:%'
                """));
    }

    @Test
    void preflightAllowsUpgradeWhenV070HasNotCreatedSystemMenuYet()
            throws Exception {
        flywayThrough69().migrate();

        try (Connection connection = MYSQL.createConnection("")) {
            assertDoesNotThrow(
                    () -> BusinessMigrationPreflight.verify(connection));
        }

        Flyway current = flyway();
        current.migrate();
        assertEquals("79", current.info().current().getVersion().getVersion());
        assertRequestedHierarchy();
    }

    @Test
    void databaseAlreadyAtV075ValidatesAndMigratesRoutesThroughV076()
            throws Exception {
        Flyway through75 = flywayThrough75();
        through75.migrate();

        assertEquals("75", through75.info().current().getVersion().getVersion());
        assertLegacyRoutesAfterV075();
        try (Connection connection = MYSQL.createConnection("")) {
            assertDoesNotThrow(
                    () -> BusinessMigrationPreflight.verify(connection));
        }

        Flyway current = flyway();
        current.migrate();
        assertEquals("79", current.info().current().getVersion().getVersion());
        assertRequestedHierarchy();
    }

    @Test
    void preflightRejectsSoftDeletedNavigationTarget() throws Exception {
        flywayThrough74().migrate();
        execute("""
                UPDATE sys_menu
                SET deleted = 1
                WHERE id = 'work_calendar_menu_001'
                """);

        try (Connection connection = MYSQL.createConnection("")) {
            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> BusinessMigrationPreflight.verify(connection));
            assertTrue(error.getMessage().contains("V075 待重组功能菜单"));
        }
    }

    @Test
    void preflightRejectsMissingHistoricalNavigationTarget() throws Exception {
        flywayThrough74().migrate();
        execute("""
                DELETE FROM sys_role_menu
                WHERE menu_id = 'list_column_template_menu_001'
                """);
        execute("""
                DELETE FROM sys_menu
                WHERE id = 'list_column_template_menu_001'
                """);

        try (Connection connection = MYSQL.createConnection("")) {
            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> BusinessMigrationPreflight.verify(connection));
            assertTrue(error.getMessage().contains("V075 缺少 V030"));
        }
    }

    @Test
    void preflightRejectsCanonicalRouteOwnedByAnotherMenu() throws Exception {
        flywayThrough74().migrate();
        seedLegacyNavigation();
        execute("""
                UPDATE sys_menu
                SET path = '/dev/manual/flow-actions'
                WHERE id = 'tenant_custom_dev_tool_001'
                """);

        try (Connection connection = MYSQL.createConnection("")) {
            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> BusinessMigrationPreflight.verify(connection));
            assertTrue(error.getMessage().contains("V076 开发手册路径"));
        }
    }

    @Test
    void preflightRejectsUserGroupWithOnlyCanonicalRouteApplied()
            throws Exception {
        flywayThrough74().migrate();
        seedLegacyNavigation();
        execute("""
                UPDATE sys_menu
                SET path = '/config/process-user-groups'
                WHERE id = '403'
                """);

        try (Connection connection = MYSQL.createConnection("")) {
            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> BusinessMigrationPreflight.verify(connection));
            assertTrue(error.getMessage().contains(
                    "V075 流程用户组固定 ID"));
        }
    }

    /**
     * 校验页面仍使用稳定 ID，层级由 V075、页面地址由 V076 调整。
     */
    private void assertRequestedHierarchy() throws Exception {
        assertMenu("300", "0", "配置管理", "M", 30);
        assertMenuPath("300", "/config");
        assertMenu("403", "300", "流程用户组", "C", 3);
        assertMenuPath("403", "/config/process-user-groups");
        assertMenu(
                "list_column_template_menu_001",
                "300",
                "列表列模板",
                "C",
                4);
        assertMenuPath(
                "list_column_template_menu_001",
                "/config/list-column-templates");

        assertMenu(
                "flow_setting_menu_001",
                "dev_guide_dir",
                "开发手册",
                "M",
                1);
        assertMenuPath("flow_setting_menu_001", "/dev/manual");
        assertMenu(
                "dev_guide_list",
                "flow_setting_menu_001",
                "列表字段扩展",
                "C",
                1);
        assertMenuPath("dev_guide_list", "/dev/manual/list-field-extension");
        assertMenu(
                "list_field_guide_v2_001",
                "flow_setting_menu_001",
                "列表字段扩展2",
                "C",
                2);
        assertMenuPath(
                "list_field_guide_v2_001",
                "/dev/manual/list-field-extension-v2");
        assertMenu(
                "custom_list_guide",
                "flow_setting_menu_001",
                "自定义列表组件",
                "C",
                3);
        assertMenuPath("custom_list_guide", "/dev/manual/custom-list");
        assertMenu(
                "custom_form_guide",
                "flow_setting_menu_001",
                "自定义表单组件",
                "C",
                4);
        assertMenuPath("custom_form_guide", "/dev/manual/custom-form");
        assertMenu(
                "flow_action_guide_menu_001",
                "flow_setting_menu_001",
                "流程动作",
                "C",
                5);
        assertMenuPath("flow_action_guide_menu_001", "/dev/manual/flow-actions");
        assertMenu(
                "extension_management_menu_001",
                "dev_guide_dir",
                "扩展管理",
                "C",
                2);
        assertMenuPath("extension_management_menu_001", "/dev/extensions");

        assertMenu(
                "work_calendar_menu_001",
                "400",
                "工作日历",
                "C",
                7);
        assertMenu(
                "sla_management_dir_001",
                "400",
                "SLA管理",
                "M",
                8);
        assertMenuPath("sla_management_dir_001", "/system/sla");
        assertMenu(
                "task_sla_policy_menu_001",
                "sla_management_dir_001",
                "SLA策略",
                "C",
                1);
        assertMenuPath("task_sla_policy_menu_001", "/system/sla/policies");
        assertMenu(
                "task_sla_monitor_menu_001",
                "sla_management_dir_001",
                "SLA监控",
                "C",
                2);
        assertMenuPath("task_sla_monitor_menu_001", "/system/sla/monitor");
    }

    /** 复现已执行 V075、尚未执行 V076 的运行库路径状态。 */
    private void assertLegacyRoutesAfterV075() throws Exception {
        assertMenuPath("300", "/entity");
        assertMenuPath("403", "/system/group");
        assertMenuPath(
                "list_column_template_menu_001",
                "/system/list-column-templates");
        assertMenuPath("flow_setting_menu_001", "");
        assertMenuPath("dev_guide_list", "/system/dev-guide");
        assertMenuPath(
                "list_field_guide_v2_001",
                "/system/list-field-guide");
        assertMenuPath("custom_list_guide", "/system/custom-list-guide");
        assertMenuPath("custom_form_guide", "/system/custom-form-guide");
        assertMenuPath(
                "flow_action_guide_menu_001",
                "/system/flow-action-guide");
        assertMenuPath("extension_management_menu_001", "/system/extensions");
        assertMenuPath("sla_management_dir_001", "");
        assertMenuPath("task_sla_policy_menu_001", "/process/sla-policies");
        assertMenuPath("task_sla_monitor_menu_001", "/process/sla-monitor");
    }

    /**
     * 模拟 V001 历史基线未收录、但既有运行库实际存在的旧菜单行。
     */
    private void seedLegacyNavigation() throws Exception {
        execute("""
                INSERT INTO sys_menu (
                  id, parent_id, menu_name, menu_type, icon, sort, path,
                  component, perm, status, visible, deleted, remark
                ) VALUES
                  (
                    '300', '0', '配置管理', 'M', 'LegacyBox', 30,
                    '/entity', NULL, NULL, '0', '0', 0,
                    'legacy-navigation-parent'
                  ),
                  (
                    '403', '400', '用户组管理', 'C', 'LegacyGroup', 41,
                    '/system/group', 'system/Group', NULL, '0', '0', 0,
                    'legacy-navigation-row:user-group'
                  ),
                  (
                    'dev_guide_list', 'dev_guide_dir', '列表字段扩展', 'C',
                    'Document', 1, '/system/dev-guide', 'system/DevGuide',
                    'system:dev:list', '0', '0', 0,
                    'legacy-navigation-row:dev-guide'
                  ),
                  (
                    'custom_list_guide', 'dev_guide_dir', '自定义列表组件',
                    'C', 'Document', 3, '/system/custom-list-guide',
                    'system/CustomListGuide', 'system:dev:list', '0', '0', 0,
                    'legacy-navigation-row:custom-list'
                  ),
                  (
                    'custom_form_guide', 'dev_guide_dir', '自定义表单组件',
                    'C', 'Document', 4, '/system/custom-form-guide',
                    'system/CustomFormGuide', 'system:dev:list', '0', '0', 0,
                    'legacy-navigation-row:custom-form'
                  ),
                  (
                    'tenant_custom_dev_tool_001', 'dev_guide_dir',
                    '租户自定义工具', 'C', 'Tools', 99,
                    '/system/tenant-custom-dev-tool',
                    'system/TenantCustomDevTool', NULL, '0', '0', 0,
                    'legacy-navigation-row:tenant-custom-tool'
                  )
                """);

        execute("""
                UPDATE sys_menu
                SET parent_id = CASE id
                      WHEN 'list_column_template_menu_001' THEN '400'
                      WHEN 'extension_management_menu_001' THEN '400'
                      WHEN 'work_calendar_menu_001' THEN '300'
                      WHEN 'task_sla_policy_menu_001' THEN '400'
                      WHEN 'task_sla_monitor_menu_001' THEN '400'
                      ELSE parent_id
                    END
                WHERE id IN (
                  'list_column_template_menu_001',
                  'extension_management_menu_001',
                  'work_calendar_menu_001',
                  'task_sla_policy_menu_001',
                  'task_sla_monitor_menu_001'
                )
                """);
    }

    private void assertSuperAdminOwnsTargetMenus() throws Exception {
        assertEquals(16, countRows("""
                SELECT COUNT(*)
                FROM sys_role_menu role_menu
                JOIN sys_role role_record ON role_record.id = role_menu.role_id
                WHERE role_record.role_code = 'super_admin'
                  AND role_record.deleted = 0
                  AND role_menu.menu_id IN (
                """ + TARGET_MENU_IDS + ")"));
    }

    private void assertMenu(
            String id,
            String parentId,
            String name,
            String type,
            int sort) throws Exception {
        assertEquals(1, countRows("""
                SELECT COUNT(*)
                FROM sys_menu
                WHERE id = '%s'
                  AND parent_id = '%s'
                  AND menu_name = '%s'
                  AND menu_type = '%s'
                  AND sort = %d
                  AND status = '0'
                  AND visible = '0'
                  AND deleted = 0
                """.formatted(id, parentId, name, type, sort)));
    }

    /** 校验菜单数据库路径与前端模块路由完全一致。 */
    private void assertMenuPath(String id, String path) throws Exception {
        assertEquals(1, countRows("""
                SELECT COUNT(*)
                FROM sys_menu
                WHERE id = '%s'
                  AND path = '%s'
                  AND deleted = 0
                """.formatted(id, path)));
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

    private Flyway flywayThrough74() {
        return Flyway.configure()
                .dataSource(
                        MYSQL.getJdbcUrl(),
                        MYSQL.getUsername(),
                        MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("74"))
                .cleanDisabled(false)
                .load();
    }

    private Flyway flywayThrough75() {
        return Flyway.configure()
                .dataSource(
                        MYSQL.getJdbcUrl(),
                        MYSQL.getUsername(),
                        MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("75"))
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
}
