package com.workflow.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
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
    private static final Path SECURITY_FOUNDATION =
            MIGRATION_DIRECTORY.resolve("V002__security_foundation.sql");

    @Test
    void flywayUsesContinuousVersionedMigrations() throws Exception {
        List<String> files;
        try (var paths = Files.list(MIGRATION_DIRECTORY)) {
            files = paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }

        assertFalse(files.isEmpty());
        assertEquals("V001__business_schema.sql", files.get(0));
        Pattern migrationName = Pattern.compile(
                "^V(\\d{3})__[a-z0-9_]+\\.sql$");
        List<Integer> versions = files.stream()
                .map(file -> {
                    var matcher = migrationName.matcher(file);
                    assertTrue(matcher.matches(),
                            "invalid migration name: " + file);
                    return Integer.parseInt(matcher.group(1));
                })
                .toList();
        assertEquals(
                IntStream.rangeClosed(1, versions.size()).boxed().toList(),
                versions,
                "migration versions must be continuous");

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
                "runtime_entity_record",
                "entity_form",
                "entity_form_node",
                "entity_list_config",
                "entity_list_scope_policy",
                "entity_list_scope_binding",
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
                "ui_extension_definition",
                "workflow_outbox_event")) {
            assertTrue(
                    sql.contains("CREATE TABLE `" + table + "`"),
                    "missing table: " + table);
        }
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
                "system:flowAction:view",
                "流程动作",
                "扩展管理")) {
            assertTrue(sql.contains(value), "missing seed value: " + value);
        }
        assertTrue(sql.contains("'create_time','创建时间'"));
        assertTrue(sql.contains("'update_time','更新时间'"));
    }

    @Test
    void immutableBaselineContainsLegacyBootstrapAdministrator() throws Exception {
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

    @Test
    void securityMigrationDisablesPublicBootstrapCredential() throws Exception {
        String sql = Files.readString(SECURITY_FOUNDATION);

        assertTrue(sql.contains("SET status = '1'"));
        assertTrue(sql.contains("AND username = 'admin'"));
        assertTrue(sql.contains(
                "AND password = '$2y$10$VPL8vj30niywnU1gYVZGNOiPqQVACc8gG2n81hbOKQlH/.gxI8ZF6'"));
        assertTrue(sql.contains("ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0"));
        for (String permission : List.of(
                "system:user:view",
                "system:role:manage",
                "system:menu:manage",
                "system:organization:manage",
                "system:dictionary:manage",
                "process:definition:publish",
                "entity:definition:publish",
                "storage:file:delete")) {
            assertTrue(sql.contains(permission), "missing permission: " + permission);
        }
    }
}
