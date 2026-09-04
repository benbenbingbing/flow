package com.workflow.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the forward-only navigation reorganization migration by stable menu ID.
 *
 * <p>This intentionally remains a lightweight source contract: the database migration
 * integration tests cover Flyway execution, while this test makes the requested product
 * hierarchy explicit and guards it from being lost in later SQL refactors.</p>
 */
class NavigationMenuMigrationContractTest {

    private static final Path HIERARCHY_MIGRATION = Path.of(
            "../workflow-db-migrator/src/main/resources/db/migration/"
                    + "V075__reorganize_navigation_menus.sql");
    private static final Path ROUTE_MIGRATION = Path.of(
            "../workflow-db-migrator/src/main/resources/db/migration/"
                    + "V076__align_navigation_menu_routes.sql");
    private static final String[] GUIDE_MENU_IDS = {
            "dev_guide_list",
            "list_field_guide_v2_001",
            "custom_list_guide",
            "custom_form_guide",
            "flow_action_guide_menu_001"
    };

    @Test
    void migrationsBuildTheRequestedNavigationHierarchyAndRoutes()
            throws Exception {
        String hierarchySql = normalize(withoutLineComments(
                Files.readString(HIERARCHY_MIGRATION)));
        String routeSql = normalize(withoutLineComments(
                Files.readString(ROUTE_MIGRATION)));

        assertStatementContains(hierarchySql,
                "INSERT INTO sys_menu", "'300'", "'0'", "'配置管理'", "'M'");
        assertUpdateContains(hierarchySql,
                "parent_id = '300'", "menu_name = '流程用户组'", "id = '403'");
        assertParentChange(
                hierarchySql, "list_column_template_menu_001", "300");

        assertUpdateContains(hierarchySql,
                "menu_name = '开发手册'",
                "id = 'flow_setting_menu_001'",
                "parent_id = 'dev_guide_dir'");
        Arrays.stream(GUIDE_MENU_IDS).forEach(menuId ->
                assertParentChange(
                        hierarchySql, menuId, "flow_setting_menu_001"));
        assertUpdateContains(hierarchySql,
                "SET sort = CASE id",
                "parent_id = 'flow_setting_menu_001'",
                Arrays.stream(GUIDE_MENU_IDS)
                        .map(id -> "'" + id + "'")
                        .toArray(String[]::new));
        assertParentChange(
                hierarchySql, "extension_management_menu_001", "dev_guide_dir");

        assertParentChange(hierarchySql, "work_calendar_menu_001", "400");
        assertStatementContains(hierarchySql,
                "INSERT INTO sys_menu",
                "'sla_management_dir_001'",
                "'400'",
                "'SLA管理'",
                "'M'");
        assertParentChange(
                hierarchySql, "task_sla_policy_menu_001", "sla_management_dir_001");
        assertParentChange(
                hierarchySql, "task_sla_monitor_menu_001", "sla_management_dir_001");

        assertUpdateContains(routeSql, "path = '/config'", "id = '300'");
        assertUpdateContains(routeSql,
                "path = '/config/process-user-groups'", "id = '403'");
        assertUpdateContains(routeSql,
                "path = '/config/list-column-templates'",
                "id = 'list_column_template_menu_001'");
        assertUpdateContains(routeSql,
                "path = '/dev/manual'", "id = 'flow_setting_menu_001'");
        assertUpdateContains(routeSql,
                "WHEN 'dev_guide_list' THEN '/dev/manual/list-field-extension'",
                "WHEN 'list_field_guide_v2_001' THEN '/dev/manual/list-field-extension-v2'",
                "WHEN 'custom_list_guide' THEN '/dev/manual/custom-list'",
                "WHEN 'custom_form_guide' THEN '/dev/manual/custom-form'",
                "WHEN 'flow_action_guide_menu_001' THEN '/dev/manual/flow-actions'");
        assertUpdateContains(routeSql,
                "path = '/dev/extensions'",
                "id = 'extension_management_menu_001'");
        assertUpdateContains(routeSql,
                "path = '/system/sla'", "id = 'sla_management_dir_001'");
        assertUpdateContains(routeSql,
                "WHEN 'task_sla_policy_menu_001' THEN '/system/sla/policies'",
                "WHEN 'task_sla_monitor_menu_001' THEN '/system/sla/monitor'");
    }

    @Test
    void appliedV075ChecksumRemainsStable() throws Exception {
        CRC32 checksum = new CRC32();
        for (String line : Files.readAllLines(
                HIERARCHY_MIGRATION, StandardCharsets.UTF_8)) {
            checksum.update(line.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(1751974791, (int) checksum.getValue());
    }

    /**
     * Requires one SQL statement to carry both the stable menu ID and its new parent.
     * Keeping both markers in the same statement prevents unrelated mentions or comments
     * from satisfying the hierarchy contract.
     */
    private static void assertParentChange(
            String sql,
            String menuId,
            String parentId) {
        String normalizedId = normalize("'" + menuId + "'");
        String normalizedParent = normalize(
                "parent_id = '" + parentId + "'");
        boolean matched = Arrays.stream(withoutLineComments(sql).split(";"))
                .map(NavigationMenuMigrationContractTest::normalize)
                .filter(statement -> statement.startsWith("UPDATE SYS_MENU"))
                .anyMatch(statement -> {
                    int whereIndex = statement.indexOf(" WHERE ");
                    return whereIndex >= 0
                            && statement.substring(0, whereIndex)
                                    .contains(normalizedParent)
                            && statement.substring(whereIndex)
                                    .contains(normalizedId);
                });
        assertTrue(
                matched,
                "migration UPDATE is missing parent change for " + menuId
                        + " -> " + parentId);
    }

    private static void assertUpdateContains(
            String sql,
            String firstFragment,
            String secondFragment,
            String[] remainingFragments) {
        String[] fragments = new String[remainingFragments.length + 2];
        fragments[0] = firstFragment;
        fragments[1] = secondFragment;
        System.arraycopy(
                remainingFragments,
                0,
                fragments,
                2,
                remainingFragments.length);
        assertUpdateContains(sql, fragments);
    }

    private static void assertUpdateContains(String sql, String... fragments) {
        String[] updateFragments = new String[fragments.length + 1];
        updateFragments[0] = "UPDATE sys_menu";
        System.arraycopy(fragments, 0, updateFragments, 1, fragments.length);
        assertStatementContains(sql, updateFragments);
    }

    /**
     * Finds a semicolon-delimited SQL statement containing every required fragment.
     */
    private static void assertStatementContains(String sql, String... fragments) {
        boolean matched = Arrays.stream(withoutLineComments(sql).split(";"))
                .anyMatch(statement -> Arrays.stream(fragments)
                        .map(NavigationMenuMigrationContractTest::normalize)
                        .allMatch(statement::contains));
        assertTrue(
                matched,
                "migration statement is missing contract fragments: "
                        + Arrays.toString(fragments));
    }

    private static String withoutLineComments(String sql) {
        return sql.replaceAll("(?m)--.*$", "");
    }

    private static String normalize(String value) {
        return value.replace("`", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}
