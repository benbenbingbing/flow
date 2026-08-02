package com.workflow.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fresh-install database contract tests.
 */
class SchemaRequiredTablesTest {

    private static final String LEGACY_CREATE_TIME =
            "created" + "_at";
    private static final String LEGACY_UPDATE_TIME =
            "updated" + "_at";
    private static final Path MIGRATION_DIRECTORY =
            Path.of("../workflow-db-migrator/src/main/resources/db/migration");
    private static final Path BASELINE =
            MIGRATION_DIRECTORY.resolve("V001__business_schema.sql");
    private static final Path CURRENT_BASELINE_PATCH =
            Path.of("src/main/resources/db/upgrade/"
                    + "V001__current_baseline_patch.sql");

    @Test
    void flywayUsesOrderedMigrationSeries() throws Exception {
        List<String> files;
        try (var paths = Files.list(MIGRATION_DIRECTORY)) {
            files = paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }

        assertEquals(23, files.size());
        for (int index = 0; index < files.size(); index++) {
            assertTrue(
                    files.get(index).startsWith(
                            "V" + String.format("%03d", index + 1) + "__"),
                    "migration version is missing or out of order: " + files);
        }

        String applicationYaml = Files.readString(
                Path.of("src/main/resources/application.yml"));
        assertTrue(applicationYaml.contains("baseline-on-migrate: false"));
        assertTrue(applicationYaml.contains("validate-on-migrate: true"));
        assertTrue(applicationYaml.contains("clean-disabled: true"));
        assertFalse(applicationYaml.contains("baseline-version:"));
    }

    @Test
    void baselineCreatesCurrentBusinessSchema() throws Exception {
        String sql = Files.readString(BASELINE);
        for (String table : List.of(
                "entity_definition",
                "entity_field",
                "entity_form",
                "entity_form_node",
                "entity_list_config",
                "entity_list_scope_policy",
                "entity_list_scope_binding",
                "entity_version_config",
                "entity_version_scenario",
                "entity_version_step",
                "entity_change_target_binding",
                "entity_version_config_release",
                "entity_change_target_instance",
                "entity_mutation_receipt",
                "entity_record_version",
                "process_definition_config",
                "process_node_config",
                "process_node_form",
                "process_task",
                "process_action",
                "process_action_execution",
                "process_person_resolver_definition",
                "process_ui_release_binding",
                "system_operation_log",
                "ui_config_release",
                "ui_event_binding",
                "ui_extension_definition",
                "workflow_outbox_event")) {
            assertTrue(
                    sql.contains("CREATE TABLE `" + table + "`"),
                    "missing table: " + table);
        }
    }

    @Test
    void existingV001DatabaseHasAnIdempotentCompatibilityPatch()
            throws Exception {
        String sql = Files.readString(CURRENT_BASELINE_PATCH);

        for (String table : List.of(
                "entity_version_config",
                "entity_version_scenario",
                "entity_version_step",
                "entity_change_target_binding",
                "entity_version_config_release",
                "entity_change_target_instance",
                "entity_mutation_receipt",
                "entity_record_version",
                "ui_event_binding")) {
            assertTrue(
                    sql.contains(
                            "CREATE TABLE IF NOT EXISTS `"
                                    + table + "`"),
                    "missing compatibility table: " + table);
        }
        assertTrue(sql.contains("INSERT IGNORE INTO `sys_menu`"));
        assertTrue(sql.contains("INSERT IGNORE INTO `sys_role_menu`"));
        assertTrue(sql.contains("interface_service_menu_001"));
        assertTrue(sql.contains("entity_version_management_001"));
        assertTrue(sql.contains("SET `menu_name` = '接口服务'"));
        assertTrue(sql.contains("SET `menu_name` = '数据版本'"));
    }

    @Test
    void baselineUsesCanonicalTimestampAndDocumentStorage() throws Exception {
        String sql = Files.readString(BASELINE);
        Pattern oldTimestamp = Pattern.compile(
                "\\b(" + LEGACY_CREATE_TIME + "|"
                        + LEGACY_UPDATE_TIME + ")\\b",
                Pattern.CASE_INSENSITIVE);
        Pattern nativeJsonColumn = Pattern.compile(
                "`[^`]+`\\s+json\\b",
                Pattern.CASE_INSENSITIVE);

        assertFalse(oldTimestamp.matcher(sql).find());
        assertFalse(sql.toLowerCase().contains(LEGACY_CREATE_TIME));
        assertFalse(sql.toLowerCase().contains(LEGACY_UPDATE_TIME));
        assertTrue(sql.contains("`create_time` datetime"));
        assertTrue(sql.contains("`update_time` datetime"));
        assertFalse(nativeJsonColumn.matcher(sql).find());
        assertTrue(sql.contains("snapshot_document` longtext"));
        assertTrue(sql.contains("payload_document` longtext"));
    }

    @Test
    void baselineContainsCurrentConstraintsAndIndexes() throws Exception {
        String sql = Files.readString(BASELINE);
        for (String index : List.of(
                "uk_entity_form_node_active_key",
                "uk_entity_list_scope_policy",
                "idx_entity_list_scope_policy_runtime",
                "idx_process_action_execution_ready",
                "idx_workflow_outbox_ready",
                "uk_workflow_outbox_topic_event",
                "uk_entity_mutation_receipt_key",
                "uk_ui_hotfix_target_active",
                "uk_ui_extension_version")) {
            assertTrue(sql.contains(index), "missing index: " + index);
        }
    }

    @Test
    void baselineExcludesUpgradeOnlyAndRetiredTables() throws Exception {
        String sql = Files.readString(BASELINE);
        for (String table : List.of(
                "entity_table_migration_log",
                "system_collation_migration_log",
                "system_json_document_migration_log",
                "system_audit_outbox",
                "process_cc_outbox",
                "entity_list_permission")) {
            assertFalse(sql.contains("CREATE TABLE `" + table + "`"),
                    "retired table must not exist: " + table);
        }
    }

    @Test
    void baselineSeedsRequiredCatalogAndPermissions() throws Exception {
        String sql = Files.readString(BASELINE);
        for (String value : List.of(
                "config-migration:list",
                "entity:ui-config:hotfix",
                "entity:ui-config:hotfix:override",
                "system:audit:list",
                "system:extension:list",
                "system:extension:update",
                "system:interface-service:list",
                "system:interface-service:update",
                "system:interface-service:test",
                "entity:version:config:list",
                "entity:version:config:update",
                "entity:version:config:publish",
                "system:flowAction:view",
                "流程动作",
                "扩展管理",
                "接口服务",
                "数据版本")) {
            assertTrue(sql.contains(value), "missing seed value: " + value);
        }
        assertTrue(sql.contains("'create_time','创建时间'"));
        assertTrue(sql.contains("'update_time','更新时间'"));
    }

    @Test
    void baselineSeedsUsableBootstrapAdministrator() throws Exception {
        String sql = Files.readString(BASELINE);
        assertTrue(sql.contains("INSERT INTO `sys_role`"));
        assertTrue(sql.contains(
                "'1', '超级管理员', 'super_admin', '系统内置超级管理员角色'"));
        assertTrue(sql.contains("INSERT INTO `sys_user`"));
        assertTrue(sql.contains("'1', 'admin'"));
        assertTrue(sql.contains("INSERT INTO `sys_user_role`"));
        assertTrue(sql.contains("'bootstrap_admin_role_001', '1', '1'"));
        assertTrue(sql.contains("INSERT INTO `sys_role_menu`"));
        assertTrue(sql.contains("MD5(CONCAT('1:', `id`))"));
        assertTrue(sql.contains(
                "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, NULL, NULL, 1"));

        String bootstrapPasswordHash =
                "$2y$10$VPL8vj30niywnU1gYVZGNOiPqQVACc8gG2n81hbOKQlH/.gxI8ZF6";
        assertTrue(new BCryptPasswordEncoder().matches(
                "admin", bootstrapPasswordHash));
    }
}
