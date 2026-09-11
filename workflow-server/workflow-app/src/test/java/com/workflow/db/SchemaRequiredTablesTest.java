package com.workflow.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

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
    private static final Path JAVA_MIGRATION_DIRECTORY =
            Path.of("../workflow-db-migrator/src/main/java/db/migration");
    private static final Path BASELINE =
            MIGRATION_DIRECTORY.resolve("V001__business_schema.sql");
    private static final Path CURRENT_BASELINE_PATCH =
            Path.of("src/main/resources/db/upgrade/"
                    + "V001__current_baseline_patch.sql");

    @Test
    void flywayUsesOrderedMigrationSeries() throws Exception {
        List<String> files;
        try (var sqlPaths = Files.list(MIGRATION_DIRECTORY);
             var javaPaths = Files.list(JAVA_MIGRATION_DIRECTORY)) {
            // V080 是需要转换 JSON 文档的 Java 迁移；序列校验必须同时覆盖两类迁移。
            files = java.util.stream.Stream.concat(sqlPaths, javaPaths)
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("V\\d+__.+\\.(sql|java)"))
                    .sorted()
                    .toList();
        }

        assertFalse(files.isEmpty());
        assertTrue(files.stream().anyMatch(name -> name.startsWith(
                        "V083__remove_entity_mutation_policy.")),
                "entity mutation policy removal migration is missing: "
                        + files);
        assertTrue(files.stream().anyMatch(name -> name.startsWith(
                        "V084__remove_retired_open_integration_features.")),
                "open integration retirement migration is missing: "
                        + files);
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
        assertTrue(applicationYaml.contains(
                "transaction-isolation: "
                        + "${DB_TRANSACTION_ISOLATION:TRANSACTION_READ_COMMITTED}"));
        assertFalse(applicationYaml.contains("baseline-version:"));
    }

    @Test
    void historicalBaselineContainsCoreBusinessSchema() throws Exception {
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
                "entity_version_config_release",
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
                "entity_version_config_release",
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
    void entityVersionV2LivesInForwardOnlyMigrations() throws Exception {
        String baseline = Files.readString(BASELINE);
        String relations = Files.readString(MIGRATION_DIRECTORY.resolve(
                "V043__decouple_entity_relations.sql"));
        String versionV2 = Files.readString(MIGRATION_DIRECTORY.resolve(
                "V045__entity_version_scope_snapshot_v2.sql"));
        String globalIdempotency = Files.readString(MIGRATION_DIRECTORY.resolve(
                "V046__record_version_global_idempotency.sql"));

        assertTrue(relations.contains("`relations_snapshot`"));
        assertTrue(relations.contains("`data_key`"));
        assertTrue(versionV2.contains(
                "CREATE TABLE `entity_record_version_dataset`"));
        assertTrue(versionV2.contains(
                "CREATE TABLE `entity_record_version_dataset_row`"));
        assertTrue(versionV2.contains(
                "CREATE TABLE `entity_record_version_counter`"));
        assertTrue(versionV2.contains(
                "fk_entity_record_version_dataset_version"));
        assertTrue(versionV2.contains(
                "fk_entity_record_version_dataset_row_dataset"));
        assertTrue(versionV2.contains(
                "fk_entity_record_version_config_release"));
        assertTrue(globalIdempotency.contains(
                "(`entity_code`, `record_id`, `idempotency_key`)"));
        assertTrue(relations.contains(
                "data_key 本阶段故意保持可空"));

        assertFalse(baseline.contains("`relations_snapshot`"));
        assertFalse(baseline.contains(
                "CREATE TABLE `entity_mutation_policy_config`"));
        assertFalse(baseline.contains(
                "CREATE TABLE `entity_record_version_dataset`"));
    }

    @Test
    void entityVersionConfigurationIsSimplifiedForwardOnly()
            throws Exception {
        String simplification = Files.readString(
                MIGRATION_DIRECTORY.resolve(
                        "V082__simplify_entity_version_configuration.sql"));

        assertTrue(simplification.contains(
                "ADD COLUMN `config_document`"));
        assertTrue(simplification.contains(
                "@flow_v082_config_document_exists"));
        assertTrue(simplification.contains(
                "JSON_SET("));
        assertFalse(simplification.contains("CREATE TRIGGER"));
        assertFalse(simplification.contains(
                "log_bin_trust_function_creators"));
        // 迁移先于新 Pod 执行，V082 必须保持旧运行时仍可访问的结构。
        assertFalse(simplification.contains(
                "DROP COLUMN `active_release_id`"));
        assertFalse(simplification.contains(
                "DROP COLUMN `config_release_id`"));
        assertFalse(simplification.contains(
                "DROP TABLE `entity_version_config_release`"));
        assertFalse(simplification.contains(
                "DROP TABLE `entity_record_version`"));
        assertFalse(simplification.contains(
                "DELETE FROM `entity_record_version`"));
    }

    @Test
    void entityMutationPolicyIsRemovedByForwardOnlyMigration()
            throws Exception {
        String removal = Files.readString(
                MIGRATION_DIRECTORY.resolve(
                        "V083__remove_entity_mutation_policy.sql"));

        for (String table : List.of(
                "entity_change_target_instance",
                "entity_mutation_policy_release",
                "entity_mutation_policy_config")) {
            assertTrue(removal.contains(
                    "DROP TABLE IF EXISTS `" + table + "`"),
                    "missing retired table: " + table);
        }
        for (String permission : List.of(
                "entity:mutation:config:list",
                "entity:mutation:config:update",
                "entity:mutation:config:publish")) {
            assertTrue(removal.contains(permission),
                    "missing retired permission: " + permission);
        }
        assertTrue(removal.contains(
                "/system/entity-mutation-policies"));
        assertTrue(removal.contains(
                "system/EntityMutationPolicyManagement"));
        assertTrue(removal.indexOf("FROM sys_role_menu")
                < removal.indexOf("FROM sys_menu menu\nJOIN"));
        assertTrue(removal.contains(
                "UPDATE `entity_version_config`"));
        assertTrue(removal.contains(
                "UPDATE `entity_version_config_release`"));
        assertTrue(removal.contains(
                "JSON_VALID(`config_document`) = 1"));
        assertTrue(removal.contains(
                "'$.steps', '$.targetBindings'"));

        // 通用写入幂等和数据版本存储不属于实体变更策略，退场迁移不得连带删除。
        assertFalse(removal.contains(
                "DROP TABLE IF EXISTS `entity_mutation_receipt`"));
        assertFalse(removal.contains(
                "DROP TABLE IF EXISTS `entity_version_config_release`"));
        assertFalse(removal.contains(
                "DROP TABLE IF EXISTS `entity_record_version`"));
    }

    @Test
    void openIntegrationRetirementKeepsEmbedSecurityStorage()
            throws Exception {
        String removal = Files.readString(MIGRATION_DIRECTORY.resolve(
                "V084__remove_retired_open_integration_features.sql"));

        for (String table : List.of(
                "integration_application_scope",
                "integration_process_grant",
                "integration_process_binding",
                "integration_workflow_scenario_revision",
                "integration_workflow_scenario",
                "integration_connector_config",
                "integration_secret",
                "webhook_delivery",
                "webhook_subscription",
                "webhook_event",
                "webhook_endpoint")) {
            assertTrue(removal.contains("DROP TABLE `" + table + "`"),
                    "missing retired table: " + table);
        }
        for (String retainedTable : List.of(
                "integration_application",
                "integration_application_credential",
                "integration_rate_limit_bucket",
                "integration_api_request_lease",
                "integration_idempotency_record")) {
            assertFalse(removal.contains(
                            "DROP TABLE `" + retainedTable + "`"),
                    "Embed/OAuth shared table must remain: " + retainedTable);
        }
        assertTrue(removal.contains(
                "WHERE `source_definition`.`source_type` = "
                        + "'INTEGRATION_CONNECTOR'"));
        assertTrue(removal.contains(
                "DELETE FROM `ui_data_source_definition`"));
        for (String operation : List.of(
                "PROCESS_START", "PROCESS_CANCEL", "MESSAGE_CORRELATE")) {
            assertTrue(removal.contains(operation),
                    "missing retired idempotency operation: " + operation);
        }
        assertTrue(removal.contains(
                "DELETE FROM `workflow_outbox_event`\n"
                        + " WHERE `topic` = "
                        + "'INTEGRATION_DOMAIN_EVENT';"));
        assertTrue(removal.contains(
                "DELETE FROM `sys_role_menu`\n"
                        + " WHERE `menu_id` = "
                        + "'integration_perm_delivery_replay';"));
        assertTrue(removal.contains(
                "DELETE FROM `sys_menu`\n"
                        + " WHERE `id` = "
                        + "'integration_perm_delivery_replay';"));
        assertTrue(removal.contains(
                "SET `menu_name` = '轮换应用凭据'"));
        assertTrue(removal.contains(
                "SET `menu_name` = '集成应用与 Embed'"));
        assertTrue(removal.contains(
                "`remark` = '集成应用、Client Credential、OAuth 与 Embed 接入说明'"));
        assertFalse(removal.contains(
                "DELETE FROM `sys_role_menu`\n"
                        + " WHERE `menu_id` = "
                        + "'user_manual_open_integration_001';"));
        assertFalse(removal.contains(
                "DELETE FROM `sys_menu`\n"
                        + " WHERE `id` = "
                        + "'user_manual_open_integration_001';"));
    }

    @Test
    void externalSystemManagementLivesInForwardOnlyMigration()
            throws Exception {
        String baseline = Files.readString(BASELINE);
        String externalSystems = Files.readString(MIGRATION_DIRECTORY.resolve(
                "V079__external_system_management.sql"));

        assertFalse(baseline.contains("CREATE TABLE `sys_external_system`"));
        assertTrue(externalSystems.contains(
                "CREATE TABLE `sys_external_system`"));
        assertTrue(externalSystems.contains(
                "CREATE TABLE `sys_external_system_parameter`"));
        assertTrue(externalSystems.contains(
                "UNIQUE KEY `uk_sys_external_system_code`"));
        assertTrue(externalSystems.contains(
                "UNIQUE KEY `uk_sys_external_system_parameter_active_name`"));
        assertTrue(externalSystems.contains("`version` bigint NOT NULL"));
        assertTrue(externalSystems.contains(
                "'system:external-system:view'"));
        assertTrue(externalSystems.contains(
                "'system:external-system:manage'"));
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
    void uiExtensionScopeMigrationAddsEntityRangeColumns()
            throws Exception {
        String sql = Files.readString(MIGRATION_DIRECTORY.resolve(
                "V021__ui_extension_entity_scope.sql"));
        assertTrue(sql.contains("`visibility_scope`"));
        assertTrue(sql.contains("`entity_codes_document`"));
        assertTrue(sql.contains("DEFAULT 'GLOBAL'"));
    }

    @Test
    void configMigrationBaselinesSupportFineGrainedScopes()
            throws Exception {
        String sql = Files.readString(MIGRATION_DIRECTORY.resolve(
                "V036__config_migration_scoped_baseline.sql"));

        assertTrue(sql.contains("ADD COLUMN `scope_key`"));
        assertTrue(sql.contains("DEFAULT 'FULL'"));
        assertTrue(sql.contains("DROP INDEX `uk_asset_baseline`"));
        assertTrue(sql.contains(
                "UNIQUE KEY `uk_asset_baseline_scope` "
                        + "(`asset_type`, `business_key`, `scope_key`)"));
    }

    @Test
    void refreshSessionMigrationStoresOnlyTokenHashes()
            throws Exception {
        String sql = Files.readString(MIGRATION_DIRECTORY.resolve(
                "V037__refresh_session.sql"));

        assertTrue(sql.contains(
                "CREATE TABLE IF NOT EXISTS `auth_refresh_session`"));
        assertTrue(sql.contains("`refresh_token_hash` char(64)"));
        assertFalse(sql.contains("`refresh_token` varchar"));
        assertTrue(sql.contains(
                "UNIQUE KEY `uk_auth_refresh_session_token_hash`"));
        assertFalse(
                Files.readString(BASELINE).contains(
                        "CREATE TABLE `auth_refresh_session`"),
                "V001 must remain immutable; auth_refresh_session belongs to V037");
    }

    @Test
    void refreshSessionUserCollationMatchesSystemUser()
            throws Exception {
        String sql = Files.readString(MIGRATION_DIRECTORY.resolve(
                "V040__align_refresh_session_user_collation.sql"));

        assertTrue(sql.contains(
                "MODIFY COLUMN `user_id` varchar(64)"));
        assertTrue(sql.contains(
                "COLLATE utf8mb4_0900_ai_ci"));
        assertFalse(sql.contains(
                "MODIFY COLUMN `refresh_token_hash`"));
    }

    @Test
    void publishedInterfaceOperationMigrationKeepsAppliedChecksum()
            throws Exception {
        Path migration = MIGRATION_DIRECTORY.resolve(
                "V034__interface_operation_context.sql");

        assertEquals(
                403469585,
                flywayChecksum(migration),
                "V034 已发布，后续变更必须新增更高版本迁移");
    }

    @Test
    void appliedListExperienceMigrationRemainsImmutableAndRemovalIsForwardOnly()
            throws Exception {
        Path appliedMigration = MIGRATION_DIRECTORY.resolve(
                "V058__list_experience_and_config_references.sql");
        assertEquals(
                854403987,
                flywayChecksum(appliedMigration),
                "V058 已执行，后续变更必须新增更高版本迁移");

        String removal = Files.readString(MIGRATION_DIRECTORY.resolve(
                "V060__remove_list_saved_view_feature.sql"));
        assertTrue(removal.contains("DROP TABLE IF EXISTS entity_list_saved_view"));
        assertTrue(removal.contains("'list-view:aggregate'"));
        assertFalse(removal.contains("DROP TABLE IF EXISTS entity_index_advice"));
        assertFalse(removal.contains("'index-advisor:execute'"));
        assertFalse(removal.contains("'config-reference:list'"));
    }

    @Test
    void optionalGovernanceCentersAreRemovedByForwardMigration()
            throws Exception {
        String removal = Files.readString(MIGRATION_DIRECTORY.resolve(
                "V063__remove_optional_governance_centers.sql"));

        for (String table : List.of(
                "config_test_suite",
                "config_test_case",
                "config_test_run",
                "config_test_result",
                "config_blueprint",
                "config_asset_dependency",
                "config_quality_snapshot",
                "entity_index_advice",
                "config_collaboration_workspace",
                "config_collaboration_branch",
                "config_collaboration_comment",
                "config_collaboration_review",
                "config_scheduled_release",
                "process_instance_migration_batch",
                "process_instance_migration_item",
                "process_instance_migration_lock",
                "process_instance_migration_audit")) {
            assertTrue(removal.contains("DROP TABLE IF EXISTS " + table));
        }
        assertTrue(removal.contains("'/system/config-test-center'"));
        assertTrue(removal.contains("'/system/config-intelligence'"));
        assertTrue(removal.contains("'/system/platform-capabilities'"));
        assertTrue(removal.contains("'config-collaboration:schedule'"));
        assertTrue(removal.contains("'process-instance-migration:execute'"));
        assertFalse(removal.contains(
                "DROP TABLE IF EXISTS config_migration_asset_dependency"));
        assertFalse(removal.contains(
                "DROP TABLE IF EXISTS entity_schema_operation"));
        assertFalse(removal.contains(
                "DROP TABLE IF EXISTS entity_schema_operation_event"));
    }

    @Test
    void listQueryBindingResetLivesInForwardMigration()
            throws Exception {
        String sql = Files.readString(MIGRATION_DIRECTORY.resolve(
                "V039__reset_list_query_interface_binding.sql"));

        assertTrue(sql.contains(
                "UPDATE `entity_list_config`"));
        assertTrue(sql.contains(
                "`query_data_source_id` = NULL"));
        assertTrue(sql.contains(
                "`query_operation_code` = NULL"));
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

    private int flywayChecksum(Path migration)
            throws Exception {
        CRC32 crc32 = new CRC32();
        for (String line : Files.readAllLines(migration)) {
            crc32.update(line.getBytes(
                    java.nio.charset.StandardCharsets.UTF_8));
        }
        return (int) crc32.getValue();
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
