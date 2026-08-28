package com.workflow.migration;

import com.workflow.migration.runner.BusinessMigrationPreflight;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class IntegrationApplicationMigrationTest {

        @Container
        private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
                        .withDatabaseName("workflow")
                        .withUsername("workflow_test")
                        .withPassword("workflow_test_password");

        @BeforeEach
        void cleanDatabase() {
                flyway().clean();
        }

        @Test
        void freshDatabaseMigratesThroughCurrentSchema()
                        throws Exception {
                Flyway flyway = flyway();
                flyway.migrate();

                assertSchemaIsCurrent(flyway);
                assertEquals(
                                Set.of(
                                                "integration_application",
                                                "integration_application_credential",
                                                "integration_api_request_lease",
                                                "integration_connector_config",
                                                "integration_idempotency_record",
                                                "integration_process_binding",
                                                "integration_secret",
                                                "integration_application_scope",
                                                "integration_process_grant",
                                                "integration_rate_limit_bucket",
                                                "integration_workflow_scenario",
                                                "integration_workflow_scenario_revision"),
                                integrationTables());
                assertEquals(
                                Set.of(
                                                "webhook_delivery",
                                                "webhook_endpoint",
                                                "webhook_event",
                                                "webhook_subscription"),
                                webhookTables());
                assertTrue(indexExists(
                                "integration_application_credential",
                                "uk_integration_credential_active"));
                assertTrue(indexExists(
                                "integration_secret",
                                "uk_integration_secret_active"));
                assertFalse(columnExists(
                                "integration_application_credential",
                                "client_secret"));
                assertFalse(columnExists(
                                "entity_form",
                                "init_config"));
                assertTrue(columnExists(
                                "integration_application_credential",
                                "secret_hash"));
                assertTrue(columnExists(
                                "storage_file_object",
                                "idempotency_key"));
                assertTrue(columnExists(
                                "storage_file_object",
                                "request_hash"));
                assertTrue(columnExists(
                                "ui_extension_definition",
                                "visibility_scope"));
                assertTrue(columnExists(
                                "ui_extension_definition",
                                "entity_codes_document"));
                assertTrue(columnExists(
                                "entity_field_file_item",
                                "is_required"));
                assertTrue(tableExists("auth_refresh_session"));
                assertTrue(tableExists("ui_view_composition"));
                assertTrue(tableExists("entity_form_unique_claim"));
                assertTrue(tableExists(
                                "entity_form_unique_value_gate"));
                assertTrue(indexExists(
                                "entity_form_unique_claim",
                                "uk_form_unique_claim_record"));
                assertTrue(columnExists(
                                "entity_form_unique_claim",
                                "effective_release_id"));
                assertTrue(columnExists(
                                "entity_form_unique_claim",
                                "effective_content_hash"));
                assertTrue(columnExists(
                                "entity_form_unique_claim",
                                "hotfix_target_id"));
                assertTrue(columnExists(
                                "system_operation_log",
                                "operation_id"));
                assertTrue(columnNullable(
                                "system_operation_log",
                                "operation_id"));
                assertTrue(columnExists(
                                "system_operation_log",
                                "parent_operation_id"));
                assertTrue(columnExists(
                                "system_operation_log",
                                "source_type"));
                assertTrue(columnExists(
                                "system_operation_log",
                                "source_event_id"));
                assertTrue(indexExists(
                                "system_operation_log",
                                "idx_system_operation_operation"));
                assertTrue(indexExists(
                                "system_operation_log",
                                "idx_system_operation_source"));
                assertFalse(tableExists("entity_list_scope_inventory"));
                assertEquals(0, countRows("""
                                SELECT COUNT(*)
                                  FROM sys_menu
                                 WHERE id = 'entity_scope_inventory_menu_001'
                                    OR perm = 'entity:list-scope:inventory'
                                    OR path = '/system/entity-scope-inventory'
                                    OR component = 'system/EntityListScopeInventory'
                                """));
                assertEquals(0, countRows("""
                                SELECT COUNT(*)
                                  FROM sys_role_menu
                                 WHERE menu_id = 'entity_scope_inventory_menu_001'
                                """));
                // EXPLICIT_ALL 仍属于列表设计器的通用安全策略，撤除盘点页不能连带移除它。
                assertEquals(1, countRows("""
                                SELECT COUNT(*)
                                  FROM sys_menu
                                 WHERE id = 'entity_scope_explicit_all_permission_001'
                                   AND perm = 'entity:list-scope:explicit-all'
                                """));
                assertEquals(1, countRows("""
                                SELECT COUNT(*)
                                  FROM sys_role_menu role_menu
                                  JOIN sys_role role ON role.id = role_menu.role_id
                                  JOIN sys_menu menu ON menu.id = role_menu.menu_id
                                 WHERE role.role_code = 'super_admin'
                                   AND role.deleted = 0
                                   AND menu.perm = 'entity:list-scope:explicit-all'
                                """));
                for (String retiredTable : Set.of(
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
                        assertFalse(tableExists(retiredTable));
                }
                assertEquals(0, countRows("""
                                SELECT COUNT(*)
                                  FROM sys_menu
                                 WHERE perm IN (
                                           'config:test:list',
                                           'config:intelligence:list',
                                           'platform:capability:list',
                                           'index-advisor:analyze',
                                           'config-reference:list',
                                           'config-collaboration:manage',
                                           'process-instance-migration:preview'
                                       )
                                    OR path IN (
                                           '/system/config-test-center',
                                           '/system/config-intelligence',
                                           '/system/platform-capabilities'
                                       )
                                """));
                // 三个中心会被完整退役，但核心配置迁移与实体结构发布共享底座必须保留。
                assertTrue(tableExists("config_migration_asset_dependency"));
                assertTrue(columnExists(
                                "config_migration_asset_dependency",
                                "reference_location"));
                assertTrue(tableExists("entity_schema_operation"));
                assertTrue(tableExists("entity_schema_operation_event"));
                assertTrue(columnExists(
                                "entity_schema_operation",
                                "operation_source"));
                assertTrue(columnExists(
                                "ui_view_composition",
                                "active_composition_key"));
                assertTrue(indexExists(
                                "ui_view_composition",
                                "uk_ui_view_composition_active_key"));
                assertTrue(tableExists("ui_config_hotfix_request"));
                assertTrue(tableExists("ui_hotfix_observation_metric"));
                for (String reviewHistoryColumn : Set.of(
                                "review_required",
                                "reviewer_id",
                                "reviewer_name",
                                "review_comment",
                                "reviewed_at")) {
                        assertTrue(columnExists(
                                        "ui_config_hotfix_request",
                                        reviewHistoryColumn));
                }
                assertEquals(0, countRows("""
                                SELECT COUNT(*)
                                  FROM sys_menu
                                 WHERE id = 'entity_ui_hotfix_review_permission'
                                    OR perm = 'entity:ui-config:hotfix:review'
                                """));
                assertEquals(0, countRows("""
                                SELECT COUNT(*)
                                  FROM sys_role_menu
                                 WHERE menu_id = 'entity_ui_hotfix_review_permission'
                                """));
                assertEquals(0, countRows("""
                                SELECT COUNT(*)
                                  FROM ui_config_hotfix_request
                                 WHERE status IN ('PENDING_REVIEW', 'APPROVED')
                                   AND release_id IS NULL
                                   AND open_slot = 1
                                """));
                assertTrue(columnExists(
                                "auth_refresh_session",
                                "refresh_token_hash"));
                assertFalse(columnExists(
                                "auth_refresh_session",
                                "refresh_token"));
                assertTrue(indexExists(
                                "auth_refresh_session",
                                "uk_auth_refresh_session_token_hash"));
                assertEquals(
                                columnCollation("sys_user", "id"),
                                columnCollation(
                                                "auth_refresh_session",
                                                "user_id"));
                assertEquals(0, countRows("""
                                SELECT COUNT(*)
                                  FROM auth_refresh_session s
                                  LEFT JOIN sys_user u
                                    ON u.id = s.user_id
                                 WHERE s.id = 'missing-session'
                                """));
                assertTrue(indexExists(
                                "storage_file_object",
                                "uk_storage_file_owner_idempotency"));
                assertEquals(4, countRows(
                                "SELECT COUNT(*) FROM sys_menu "
                                                + "WHERE id LIKE 'integration_perm_%'"));
                assertEquals(1, countRows(
                                "SELECT COUNT(*) FROM sys_menu "
                                                + "WHERE id = 'integration_management_menu_001' "
                                                + "AND path = '/system/open-integration' "
                                                + "AND perm = 'system:integration:view'"));
                assertEquals(4, countRows(
                                "SELECT COUNT(*) FROM sys_menu "
                                                + "WHERE parent_id = "
                                                + "'integration_management_menu_001' "
                                                + "AND id LIKE 'integration_perm_%'"));
                assertEquals(1, countRows(
                                "SELECT COUNT(*) FROM sys_role_menu "
                                                + "WHERE role_id = '1' AND menu_id = "
                                                + "'integration_management_menu_001'"));
                for (String table : integrationTables()) {
                        assertTrue(columnExists(table, "create_time"));
                        assertTrue(columnExists(table, "update_time"));
                }
                for (String table : webhookTables()) {
                        assertTrue(columnExists(table, "create_time"));
                        assertTrue(columnExists(table, "update_time"));
                }
        }

        @Test
        void formUniqueClaimUsesFormScopedAtomicKeys()
                        throws Exception {
                flyway().migrate();
                execute("""
                                INSERT INTO entity_form_unique_claim (
                                  constraint_key, value_hash, entity_code,
                                  form_id, rule_id, field_code,
                                  normalized_value, record_id,
                                  effective_release_id
                                ) VALUES (
                                  'FORM:form-a:release-a-1:uq_name',
                                  'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                                  'project', 'form-a', 'uq_name', 'name',
                                  'project-a', 'record-1', 'release-a-1'
                                )
                                """);

                // 同一表单规则的相同规范化值必须由数据库原子拒绝。
                assertThrows(SQLException.class, () -> execute("""
                                INSERT INTO entity_form_unique_claim (
                                  constraint_key, value_hash, entity_code,
                                  form_id, rule_id, field_code,
                                  normalized_value, record_id,
                                  effective_release_id
                                ) VALUES (
                                  'FORM:form-a:release-a-1:uq_name',
                                  'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                                  'project', 'form-a', 'uq_name', 'name',
                                  'project-a', 'record-2', 'release-a-1'
                                )
                                """));

                // 不同表单拥有独立命名空间，不会继承 form-a 的规则。
                execute("""
                                INSERT INTO entity_form_unique_claim (
                                  constraint_key, value_hash, entity_code,
                                  form_id, rule_id, field_code,
                                  normalized_value, record_id,
                                  effective_release_id
                                ) VALUES (
                                  'FORM:form-b:release-b-1:uq_name',
                                  'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                                  'project', 'form-b', 'uq_name', 'name',
                                  'project-a', 'record-2', 'release-b-1'
                                )
                                """);

                // 同一表单的新有效发布版拥有独立规则空间，旧条件占位不会造成假冲突。
                execute("""
                                INSERT INTO entity_form_unique_claim (
                                  constraint_key, value_hash, entity_code,
                                  form_id, rule_id, field_code,
                                  normalized_value, record_id,
                                  effective_release_id
                                ) VALUES (
                                  'FORM:form-a:release-a-2:uq_name',
                                  'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                                  'project', 'form-a', 'uq_name', 'name',
                                  'project-a', 'record-3', 'release-a-2'
                                )
                                """);
        }

        @Test
        void formUniqueValueGateSerializesDifferentFormsAndReleases()
                        throws Exception {
                flyway().migrate();
                String insertGate = """
                                INSERT IGNORE INTO entity_form_unique_value_gate (
                                  scope_key, value_hash
                                ) VALUES (
                                  'ENTITY:project:name',
                                  'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
                                )
                                """;
                String lockGate = """
                                SELECT value_hash
                                  FROM entity_form_unique_value_gate
                                 WHERE scope_key = 'ENTITY:project:name'
                                   AND value_hash =
                                     'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
                                 FOR UPDATE
                                """;
                ExecutorService executor =
                                Executors.newSingleThreadExecutor();
                try (Connection first = MYSQL.createConnection("")) {
                        first.setTransactionIsolation(
                                        Connection.TRANSACTION_READ_COMMITTED);
                        first.setAutoCommit(false);
                        try (Statement statement = first.createStatement()) {
                                statement.executeUpdate(insertGate);
                                try (ResultSet locked = statement.executeQuery(
                                                lockGate)) {
                                        assertTrue(locked.next());
                                }
                        }

                        CountDownLatch secondStarted =
                                        new CountDownLatch(1);
                        Future<Boolean> second = executor.submit(() -> {
                                try (Connection connection =
                                                     MYSQL.createConnection("")) {
                                        connection.setTransactionIsolation(
                                                        Connection.TRANSACTION_READ_COMMITTED);
                                        connection.setAutoCommit(false);
                                        secondStarted.countDown();
                                        try (Statement statement =
                                                             connection.createStatement()) {
                                                // 模拟另一表单/effective release 写入同一实体字段和值：
                                                // INSERT IGNORE 必须等待前一事务释放稳定 gate 行。
                                                statement.executeUpdate(insertGate);
                                                try (ResultSet locked =
                                                                     statement.executeQuery(
                                                                             lockGate)) {
                                                        assertTrue(locked.next());
                                                }
                                        }
                                        connection.commit();
                                        return true;
                                }
                        });
                        assertTrue(secondStarted.await(
                                        5, TimeUnit.SECONDS));
                        assertThrows(
                                        TimeoutException.class,
                                        () -> second.get(
                                                        250,
                                                        TimeUnit.MILLISECONDS));

                        first.commit();
                        assertTrue(second.get(
                                        5, TimeUnit.SECONDS));
                } finally {
                        executor.shutdownNow();
                }
        }

        @Test
        void uiViewCompositionKeyCanBeReusedAfterRepeatedLogicalDeletes()
                        throws Exception {
                flyway().migrate();
                insertViewComposition("composition-1");
                assertThrows(SQLException.class,
                                () -> insertViewComposition("composition-duplicate"));

                execute("UPDATE ui_view_composition SET deleted = 1 "
                                + "WHERE id = 'composition-1'");
                insertViewComposition("composition-2");
                execute("UPDATE ui_view_composition SET deleted = 1 "
                                + "WHERE id = 'composition-2'");
                insertViewComposition("composition-3");

                assertEquals(1, countRows(
                                "SELECT COUNT(*) FROM ui_view_composition "
                                                + "WHERE owner_type = 'FORM' "
                                                + "AND owner_id = 'form-1' "
                                                + "AND composition_key = 'project_requirements' "
                                                + "AND deleted = 0"));
        }

        @Test
        void scopeInventoryRemovalAlsoCleansRecreatedMenuIds()
                        throws Exception {
                Flyway throughV61 = Flyway.configure()
                                .dataSource(
                                                MYSQL.getJdbcUrl(),
                                                MYSQL.getUsername(),
                                                MYSQL.getPassword())
                                .locations("classpath:db/migration")
                                .cleanDisabled(false)
                                .target(MigrationVersion.fromVersion("61"))
                                .load();
                throughV61.migrate();

                // 模拟存量环境复制盘点菜单后由系统生成了新主键；子菜单本身没有盘点页路径，
                // 只能先记录根菜单真实 ID，才能同时清理其授权和子项。
                execute("""
                                INSERT INTO sys_menu (
                                  id, parent_id, menu_name, menu_type, icon, sort,
                                  path, component, perm, status, visible, is_frame,
                                  is_cache, query, keep_alive, breadcrumb, remark,
                                  deleted, create_by, create_time, update_by, update_time,
                                  entity_code, resource_type, list_key
                                )
                                SELECT
                                  'scope_inventory_copy_root', '0', CONCAT(menu_name, '副本'),
                                  menu_type, icon, sort, '/system/entity-scope-inventory-copy',
                                  'system/EntityListScopeInventory',
                                  'entity:list-scope:inventory:copy', status, visible, is_frame,
                                  is_cache, query, keep_alive, breadcrumb, remark, deleted,
                                  create_by, create_time, update_by, update_time,
                                  entity_code, resource_type, list_key
                                FROM sys_menu
                                WHERE id = 'entity_scope_inventory_menu_001'
                                """);
                execute("""
                                INSERT INTO sys_menu (
                                  id, parent_id, menu_name, menu_type, icon, sort,
                                  path, component, perm, status, visible, is_frame,
                                  is_cache, query, keep_alive, breadcrumb, remark,
                                  deleted, create_by, create_time, update_by, update_time,
                                  entity_code, resource_type, list_key
                                )
                                SELECT
                                  'scope_inventory_copy_child', 'scope_inventory_copy_root',
                                  '盘点副本确认', menu_type, icon, sort, '', '',
                                  'entity:list-scope:inventory:copy-confirm', status, visible,
                                  is_frame, is_cache, query, keep_alive, breadcrumb, remark,
                                  deleted, create_by, create_time, update_by, update_time,
                                  entity_code, resource_type, list_key
                                FROM sys_menu
                                WHERE id = 'entity_scope_explicit_all_permission_001'
                                """);
                execute("""
                                INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
                                VALUES
                                  ('scope-copy-root-grant', '1', 'scope_inventory_copy_root', CURRENT_TIMESTAMP),
                                  ('scope-copy-child-grant', '1', 'scope_inventory_copy_child', CURRENT_TIMESTAMP)
                                """);

                flyway().migrate();

                assertEquals(0, countRows("""
                                SELECT COUNT(*) FROM sys_menu
                                 WHERE id IN ('scope_inventory_copy_root', 'scope_inventory_copy_child')
                                """));
                assertEquals(0, countRows("""
                                SELECT COUNT(*) FROM sys_role_menu
                                 WHERE menu_id IN ('scope_inventory_copy_root', 'scope_inventory_copy_child')
                                """));
                assertEquals(1, countRows("""
                                SELECT COUNT(*) FROM sys_menu
                                 WHERE perm = 'entity:list-scope:explicit-all'
                                """));
        }

        @Test
        void unifiedAuditMigrationKeepsLegacyEventsAsSeparateOperations()
                        throws Exception {
                Flyway throughV63 = Flyway.configure()
                                .dataSource(
                                                MYSQL.getJdbcUrl(),
                                                MYSQL.getUsername(),
                                                MYSQL.getPassword())
                                .locations("classpath:db/migration")
                                .cleanDisabled(false)
                                .target(MigrationVersion.fromVersion("63"))
                                .load();
                throughV63.migrate();
                execute("""
                                INSERT INTO system_operation_log (
                                  id, event_id, trace_id, module_code,
                                  operation_code, operation_name, risk_level,
                                  result, create_time
                                ) VALUES (
                                  'legacy-audit-1', 'legacy-event-1',
                                  'shared-trace', 'ENTITY', 'UPDATE',
                                  '历史实体变更', 'MEDIUM', 'SUCCESS',
                                  CURRENT_TIMESTAMP
                                ), (
                                  'legacy-audit-2', 'legacy-event-2',
                                  'shared-trace', 'PROCESS', 'APPROVE',
                                  '历史流程操作', 'MEDIUM', 'SUCCESS',
                                  CURRENT_TIMESTAMP
                                )
                                """);

                flyway().migrate();

                // V064 是滚动升级的 Expand 迁移。尚未升级的旧 Pod 不会写
                // operation_id，迁移后仍必须允许其追加审计行。
                execute("""
                                INSERT INTO system_operation_log (
                                  id, event_id, trace_id, module_code,
                                  operation_code, operation_name, risk_level,
                                  result, create_time
                                ) VALUES (
                                  'rolling-old-pod-audit',
                                  'rolling-old-pod-event',
                                  'shared-trace', 'PROCESS', 'UPDATE',
                                  '滚动升级旧实例写入', 'MEDIUM', 'SUCCESS',
                                  CURRENT_TIMESTAMP
                                )
                                """);

                assertEquals(1, countRows("""
                                SELECT COUNT(*)
                                  FROM system_operation_log
                                 WHERE event_id = 'legacy-event-1'
                                   AND operation_id = 'legacy-event-1'
                                """));
                assertEquals(1, countRows("""
                                SELECT COUNT(*)
                                  FROM system_operation_log
                                 WHERE event_id = 'legacy-event-2'
                                   AND operation_id = 'legacy-event-2'
                                """));
                assertEquals(2, countRows("""
                                SELECT COUNT(DISTINCT operation_id)
                                  FROM system_operation_log
                                 WHERE trace_id = 'shared-trace'
                                   AND operation_id IS NOT NULL
                                """));
                assertEquals(1, countRows("""
                                SELECT COUNT(*)
                                  FROM system_operation_log
                                 WHERE event_id = 'rolling-old-pod-event'
                                   AND operation_id IS NULL
                                """));
        }

        @Test
        void hotfixReviewRemovalCancelsOnlyUnpublishedOpenRequests()
                        throws Exception {
                Flyway throughV64 = Flyway.configure()
                                .dataSource(
                                                MYSQL.getJdbcUrl(),
                                                MYSQL.getUsername(),
                                                MYSQL.getPassword())
                                .locations("classpath:db/migration")
                                .cleanDisabled(false)
                                .target(MigrationVersion.fromVersion("64"))
                                .load();
                throughV64.migrate();

                execute("""
                                INSERT INTO sys_role_menu (
                                  id, role_id, menu_id, create_time
                                )
                                SELECT
                                  'legacy-hotfix-review-grant', '1', id,
                                  CURRENT_TIMESTAMP
                                FROM sys_menu
                                WHERE id = 'entity_ui_hotfix_review_permission'
                                """);
                execute("""
                                INSERT INTO ui_config_hotfix_request (
                                  id, config_type, config_id, draft_hash,
                                  active_release_id, target_hash,
                                  impact_token_hash, risk_level, reason,
                                  ticket_ref, impact_document, applicant_id,
                                  applicant_name, window_start, window_end,
                                  review_required, status, reviewer_id,
                                  reviewer_name, review_comment, reviewed_at,
                                  release_id
                                ) VALUES (
                                  'legacy-pending', 'FORM', 'form-pending',
                                  REPEAT('a', 64), 'active-pending',
                                  'target-pending', REPEAT('b', 64), 'REVIEW',
                                  '待复核修复', 'INC-PENDING', '{}',
                                  'applicant-pending', '申请人甲',
                                  CURRENT_TIMESTAMP - INTERVAL 1 HOUR,
                                  CURRENT_TIMESTAMP + INTERVAL 1 HOUR,
                                  1, 'PENDING_REVIEW', NULL, NULL, NULL, NULL,
                                  NULL
                                ), (
                                  'legacy-approved', 'FORM', 'form-approved',
                                  REPEAT('c', 64), 'active-approved',
                                  'target-approved', REPEAT('d', 64), 'REVIEW',
                                  '已复核修复', 'INC-APPROVED', '{}',
                                  'applicant-approved', '申请人乙',
                                  CURRENT_TIMESTAMP - INTERVAL 1 HOUR,
                                  CURRENT_TIMESTAMP + INTERVAL 1 HOUR,
                                  1, 'APPROVED', 'legacy-reviewer', '历史复核人',
                                  '历史复核意见', CURRENT_TIMESTAMP, NULL
                                ), (
                                  'legacy-publishing', 'FORM', 'form-publishing',
                                  REPEAT('e', 64), 'active-publishing',
                                  'target-publishing', REPEAT('f', 64), 'SAFE',
                                  '发布中修复', 'INC-PUBLISHING', '{}',
                                  'applicant-publishing', '申请人丙',
                                  CURRENT_TIMESTAMP - INTERVAL 1 HOUR,
                                  CURRENT_TIMESTAMP + INTERVAL 1 HOUR,
                                  0, 'PUBLISHING', NULL, NULL, NULL, NULL, NULL
                                ), (
                                  'legacy-observing', 'FORM', 'form-observing',
                                  REPEAT('1', 64), 'active-observing',
                                  'target-observing', REPEAT('2', 64), 'REVIEW',
                                  '观察中修复', 'INC-OBSERVING', '{}',
                                  'applicant-observing', '申请人丁',
                                  CURRENT_TIMESTAMP - INTERVAL 2 HOUR,
                                  CURRENT_TIMESTAMP - INTERVAL 1 HOUR,
                                  1, 'OBSERVING', 'observing-reviewer',
                                  '观察记录复核人', '已复核',
                                  CURRENT_TIMESTAMP - INTERVAL 2 HOUR,
                                  'release-observing'
                                )
                                """);

                flyway().migrate();

                assertEquals(0, countRows("""
                                SELECT COUNT(*)
                                  FROM sys_menu
                                 WHERE id = 'entity_ui_hotfix_review_permission'
                                    OR perm = 'entity:ui-config:hotfix:review'
                                """));
                assertEquals(0, countRows("""
                                SELECT COUNT(*)
                                  FROM sys_role_menu
                                 WHERE id = 'legacy-hotfix-review-grant'
                                    OR menu_id = 'entity_ui_hotfix_review_permission'
                                """));
                assertEquals(2, countRows("""
                                SELECT COUNT(*)
                                  FROM ui_config_hotfix_request
                                 WHERE id IN ('legacy-pending', 'legacy-approved')
                                   AND status = 'CANCELLED'
                                   AND release_id IS NULL
                                   AND open_slot IS NULL
                                   AND cancelled_by = 'flyway:V065'
                                   AND cancelled_at IS NOT NULL
                                   AND cancel_reason LIKE '%重新预检后直接发布%'
                                """));
                // 取消旧开放申请不能抹掉已经形成的独立复核证据。
                assertEquals(1, countRows("""
                                SELECT COUNT(*)
                                  FROM ui_config_hotfix_request
                                 WHERE id = 'legacy-approved'
                                   AND review_required = 1
                                   AND reviewer_id = 'legacy-reviewer'
                                   AND reviewer_name = '历史复核人'
                                   AND review_comment = '历史复核意见'
                                   AND reviewed_at IS NOT NULL
                                """));
                assertEquals(1, countRows("""
                                SELECT COUNT(*)
                                  FROM ui_config_hotfix_request
                                 WHERE id = 'legacy-publishing'
                                   AND status = 'PUBLISHING'
                                   AND open_slot = 1
                                   AND cancelled_at IS NULL
                                """));
                assertEquals(1, countRows("""
                                SELECT COUNT(*)
                                  FROM ui_config_hotfix_request
                                 WHERE id = 'legacy-observing'
                                   AND status = 'OBSERVING'
                                   AND release_id = 'release-observing'
                                   AND reviewer_id = 'observing-reviewer'
                                   AND cancelled_at IS NULL
                                """));
        }

        @Test
        void versionFourteenUpgradePreservesExistingBusinessData()
                        throws Exception {
                Flyway.configure()
                                .dataSource(
                                                MYSQL.getJdbcUrl(),
                                                MYSQL.getUsername(),
                                                MYSQL.getPassword())
                                .locations("classpath:db/migration")
                                .cleanDisabled(false)
                                .target(MigrationVersion.fromVersion("14"))
                                .load()
                                .migrate();

                execute("""
                                INSERT INTO sys_dict (
                                  id, dict_code, dict_name, status, deleted
                                ) VALUES (
                                  'upgrade-sentinel',
                                  'upgrade_sentinel',
                                  'Upgrade sentinel',
                                  '0',
                                  0
                                )
                                """);
                insertApplication("upgrade-app", "upgrade-client");
                insertBinding(
                                "upgrade-binding",
                                "upgrade-app",
                                "upgrade-business",
                                "upgrade-process-instance");
                assertFalse(tableExists("webhook_endpoint"));

                Flyway currentFlyway = flyway();
                currentFlyway.migrate();

                assertSchemaIsCurrent(currentFlyway);
                assertEquals(1, countRows(
                                "SELECT COUNT(*) FROM sys_dict "
                                                + "WHERE id = 'upgrade-sentinel' "
                                                + "AND dict_code = 'upgrade_sentinel'"));
                assertTrue(tableExists("webhook_endpoint"));
                assertTrue(tableExists("integration_secret"));
                assertTrue(tableExists("integration_connector_config"));
                assertEquals(1, countRows("""
                                SELECT COUNT(*)
                                  FROM integration_process_binding
                                 WHERE application_id = 'upgrade-app'
                                   AND process_instance_id =
                                     'upgrade-process-instance'
                                """));
        }

        @Test
        void relationDuplicatePreflightStopsBeforeFlywayHistoryIsChanged()
                        throws Exception {
                Flyway throughV42 = Flyway.configure()
                                .dataSource(
                                                MYSQL.getJdbcUrl(),
                                                MYSQL.getUsername(),
                                                MYSQL.getPassword())
                                .locations("classpath:db/migration")
                                .cleanDisabled(false)
                                .target(MigrationVersion.fromVersion("42"))
                                .load();
                throughV42.migrate();
                execute("""
                                INSERT INTO entity_relation (
                                  id, parent_entity_id, parent_entity_code,
                                  parent_field_code, relation_code,
                                  child_entity_id, child_entity_code,
                                  child_ref_field_code
                                ) VALUES
                                  ('rel-1', 'parent-1', 'asset', 'lines_a',
                                   'asset_lines', 'child-1', 'asset_line', 'asset_id'),
                                  ('rel-2', 'parent-1', 'asset', 'lines_b',
                                   'asset_lines', 'child-1', 'asset_line', 'asset_id')
                                """);

                IllegalStateException failure = assertThrows(
                                IllegalStateException.class,
                                () -> {
                                        try (Connection connection =
                                                             MYSQL.createConnection("")) {
                                                BusinessMigrationPreflight.verify(connection);
                                        }
                                });

                assertTrue(failure.getMessage().contains("asset_lines"));
                assertEquals("42", currentVersion());
                assertEquals(0, countRows("""
                                SELECT COUNT(*) FROM flyway_schema_history
                                WHERE success = 0
                                """));
        }

        @Test
        void versionIdempotencyPreflightStopsBeforeV46HistoryIsWritten()
                        throws Exception {
                Flyway throughV45 = Flyway.configure()
                                .dataSource(
                                                MYSQL.getJdbcUrl(),
                                                MYSQL.getUsername(),
                                                MYSQL.getPassword())
                                .locations("classpath:db/migration")
                                .cleanDisabled(false)
                                .target(MigrationVersion.fromVersion("45"))
                                .load();
                throughV45.migrate();
                execute("""
                                INSERT INTO entity_record_version (
                                  id, entity_code, record_id, version_no,
                                  scenario_code, scenario_name,
                                  operation_type, source_type,
                                  business_intent_code, business_intent_name,
                                  idempotency_key, snapshot_hash, snapshot_document
                                ) VALUES
                                  ('version-1', 'asset', 'asset-1', 1,
                                   'ROOT_CHANGE', '根变化', 'UPDATE', 'FORM',
                                   'EDIT', '编辑', 'same-key', 'hash-1', '{}'),
                                  ('version-2', 'asset', 'asset-1', 2,
                                   'MANUAL', '手工', 'UPDATE', 'SYSTEM_TASK',
                                   'MANUAL', '手工固化', 'same-key', 'hash-2', '{}')
                                """);

                IllegalStateException failure = assertThrows(
                                IllegalStateException.class,
                                () -> {
                                        try (Connection connection =
                                                             MYSQL.createConnection("")) {
                                                BusinessMigrationPreflight.verify(connection);
                                        }
                                });

                assertTrue(failure.getMessage().contains("same-key"));
                assertEquals("45", currentVersion());
                assertEquals(0, countRows("""
                                SELECT COUNT(*) FROM flyway_schema_history
                                WHERE success = 0
                                """));
        }

        @Test
        void databaseEnforcesSingleActiveCredentialPerApplication()
                        throws Exception {
                flyway().migrate();
                insertApplication("app-single-active", "client-single-active");
                execute("""
                                INSERT INTO integration_application_credential (
                                  id, application_id, secret_hash, credential_hint,
                                  status, credential_version, created_by
                                ) VALUES (
                                  'credential-active-1',
                                  'app-single-active',
                                  '{argon2}first-hash',
                                  'first',
                                  'ACTIVE',
                                  1,
                                  'migration-test'
                                )
                                """);

                assertThrows(SQLException.class, () -> execute("""
                                INSERT INTO integration_application_credential (
                                  id, application_id, secret_hash, credential_hint,
                                  status, credential_version, created_by
                                ) VALUES (
                                  'credential-active-2',
                                  'app-single-active',
                                  '{argon2}second-hash',
                                  'second',
                                  'ACTIVE',
                                  2,
                                  'migration-test'
                                )
                                """));
                assertEquals(1, countRows("""
                                SELECT COUNT(*)
                                  FROM integration_application_credential
                                 WHERE application_id = 'app-single-active'
                                   AND status = 'ACTIVE'
                                """));
        }

        @Test
        void concurrentRateLimitUpdatesDoNotLoseIncrements()
                        throws Exception {
                flyway().migrate();
                int workers = 8;
                int incrementsPerWorker = 25;
                CountDownLatch ready = new CountDownLatch(workers);
                CountDownLatch start = new CountDownLatch(1);
                ExecutorService executor = Executors.newFixedThreadPool(workers);
                try {
                        Set<Future<?>> tasks = new java.util.HashSet<>();
                        for (int worker = 0; worker < workers; worker++) {
                                tasks.add(executor.submit(() -> {
                                        ready.countDown();
                                        start.await();
                                        for (int iteration = 0; iteration < incrementsPerWorker; iteration++) {
                                                incrementRateLimitBucket();
                                        }
                                        return null;
                                }));
                        }
                        ready.await();
                        start.countDown();
                        for (Future<?> task : tasks) {
                                task.get();
                        }
                } finally {
                        executor.shutdownNow();
                }

                assertEquals(workers * incrementsPerWorker, countRows("""
                                SELECT request_count
                                  FROM integration_rate_limit_bucket
                                 WHERE bucket_key =
                                   'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
                                   AND window_epoch = 29772000
                                """));
        }

        @Test
        void databaseEnforcesApplicationScopedIdempotencyAndBindings()
                        throws Exception {
                flyway().migrate();
                insertApplication("app-a", "client-a");
                insertApplication("app-b", "client-b");

                insertIdempotency(
                                "idem-a",
                                "app-a",
                                "start-process",
                                "request-1");
                assertThrows(SQLException.class, () -> insertIdempotency(
                                "idem-a-duplicate",
                                "app-a",
                                "start-process",
                                "request-1"));
                insertIdempotency(
                                "idem-b",
                                "app-b",
                                "start-process",
                                "request-1");

                insertBinding(
                                "binding-a",
                                "app-a",
                                "business-1",
                                "process-instance-a");
                assertThrows(SQLException.class, () -> insertBinding(
                                "binding-a-duplicate-business",
                                "app-a",
                                "business-1",
                                "process-instance-other"));
                assertThrows(SQLException.class, () -> insertBinding(
                                "binding-a-duplicate-instance",
                                "app-a",
                                "business-2",
                                "process-instance-a"));
                assertThrows(SQLException.class, () -> insertBinding(
                                "binding-b",
                                "app-b",
                                "business-1",
                                "process-instance-a"));
                insertBindingWithVersion(
                                "binding-a-v1",
                                "app-a",
                                "business-1",
                                "v1",
                                "process-instance-v1");
                insertBindingWithVersion(
                                "binding-a-v2",
                                "app-a",
                                "business-1",
                                "v2",
                                "process-instance-v2");
                assertThrows(SQLException.class, () -> insertBindingWithVersion(
                                "binding-a-v1-duplicate",
                                "app-a",
                                "business-1",
                                "v1",
                                "process-instance-v1-duplicate"));
        }

        @Test
        void databaseEnforcesWebhookApplicationOwnershipAndReplayUniqueness()
                        throws Exception {
                flyway().migrate();
                insertApplication("webhook-app-a", "webhook-client-a");
                insertApplication("webhook-app-b", "webhook-client-b");
                insertWebhookEndpoint("endpoint-a", "webhook-app-a");
                insertWebhookEndpoint("endpoint-b", "webhook-app-b");
                insertWebhookSubscription(
                                "subscription-a",
                                "webhook-app-a",
                                "endpoint-a");
                assertThrows(SQLException.class, () -> insertWebhookSubscription(
                                "cross-app-subscription",
                                "webhook-app-b",
                                "endpoint-a"));
                insertWebhookEvent("event-a", "webhook-app-a");
                insertWebhookEvent("event-b", "webhook-app-b");
                insertWebhookDelivery(
                                "delivery-a",
                                "webhook-app-a",
                                "subscription-a",
                                "event-a",
                                0);
                assertThrows(SQLException.class, () -> insertWebhookDelivery(
                                "delivery-duplicate",
                                "webhook-app-a",
                                "subscription-a",
                                "event-a",
                                0));
                assertThrows(SQLException.class, () -> insertWebhookDelivery(
                                "delivery-cross-app-event",
                                "webhook-app-a",
                                "subscription-a",
                                "event-b",
                                1));
        }

        @Test
        void integrationRowsCannotReferenceUnknownApplications()
                        throws Exception {
                flyway().migrate();

                assertThrows(SQLException.class, () -> execute("""
                                INSERT INTO integration_application_scope (
                                  application_id, scope, granted_by
                                ) VALUES (
                                  'missing-app',
                                  'process.instance.read',
                                  'migration-test'
                                )
                                """));
                assertThrows(SQLException.class, () -> insertBinding(
                                "binding-orphan",
                                "missing-app",
                                "business-1",
                                "process-instance-1"));
                assertThrows(SQLException.class, () -> insertSecret(
                                "secret-orphan",
                                "missing-app",
                                "api-token",
                                1));
        }

        @Test
        void databaseEnforcesSecretLifecycleAndSingleActiveVersion()
                        throws Exception {
                flyway().migrate();
                insertApplication("secret-app", "secret-client");
                insertSecret("secret-v1", "secret-app", "api-token", 1);

                assertThrows(SQLException.class, () -> insertSecret(
                                "secret-v2-active",
                                "secret-app",
                                "api-token",
                                2));
                execute("""
                                UPDATE integration_secret
                                   SET status = 'REVOKED',
                                       revoked_by = 'migration-test',
                                       revoked_at = CURRENT_TIMESTAMP(6)
                                 WHERE id = 'secret-v1'
                                """);
                insertSecret("secret-v2", "secret-app", "api-token", 2);
                assertThrows(SQLException.class, () -> execute("""
                                UPDATE integration_secret
                                   SET status = 'DESTROYED',
                                       key_version = NULL,
                                       encrypted_data_key = NULL,
                                       data_key_nonce = NULL,
                                       secret_ciphertext = NULL,
                                       secret_nonce = NULL
                                 WHERE id = 'secret-v1'
                                """));
                execute("""
                                UPDATE integration_secret
                                   SET status = 'DESTROYED',
                                       key_version = NULL,
                                       encrypted_data_key = NULL,
                                       data_key_nonce = NULL,
                                       secret_ciphertext = NULL,
                                       secret_nonce = NULL,
                                       destroyed_by = 'migration-test',
                                       destroyed_at = CURRENT_TIMESTAMP(6)
                                 WHERE id = 'secret-v1'
                                """);
        }

        @Test
        void databaseRejectsInvalidConnectorConfiguration()
                        throws Exception {
                flyway().migrate();
                insertApplication("connector-app", "connector-client");
                insertConnectorConfig(
                                "connector-valid",
                                "connector-app",
                                "Primary ERP",
                                JSON_OBJECT_PLACEHOLDER,
                                "JSON_ARRAY('erp.example.com')");
                assertThrows(SQLException.class, () -> insertConnectorConfig(
                                "connector-invalid-json",
                                "connector-app",
                                "Invalid JSON",
                                "'not-json'",
                                "JSON_ARRAY('erp.example.com')"));
                assertThrows(SQLException.class, () -> insertConnectorConfig(
                                "connector-empty-hosts",
                                "connector-app",
                                "Empty hosts",
                                JSON_OBJECT_PLACEHOLDER,
                                "JSON_ARRAY()"));
                assertThrows(SQLException.class, () -> execute("""
                                INSERT INTO integration_connector_config (
                                  id, application_id, config_name, connector_code,
                                  status, configuration_document,
                                  allowed_hosts_document, version,
                                  created_by, updated_by
                                ) VALUES (
                                  'connector-orphan', 'missing-app', 'Orphan',
                                  'http-json', 'ACTIVE', JSON_OBJECT(),
                                  JSON_ARRAY('erp.example.com'), 0,
                                  'migration-test', 'migration-test'
                                )
                                """));
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

        private void insertViewComposition(String id) throws Exception {
                execute("""
                                INSERT INTO ui_view_composition (
                                  id, owner_type, owner_id, composition_key,
                                  anchor_type, config_document, order_key,
                                  revision, deleted
                                ) VALUES (
                                  '%s', 'FORM', 'form-1', 'project_requirements',
                                  'OWNER', JSON_OBJECT(
                                    'target', JSON_OBJECT(
                                      'entityId', 'entity-2',
                                      'contentType', 'LIST',
                                      'contentId', 'list-2'
                                    ),
                                    'presentation', JSON_OBJECT(
                                      'position', 'TAB',
                                      'loadMode', 'ON_DEMAND'
                                    ),
                                    'relation', JSON_OBJECT(
                                      'type', 'REVERSE_REFERENCE',
                                      'targetField', 'projectId'
                                    ),
                                    'actions', JSON_ARRAY('VIEW')
                                  ),
                                  1000, 1, 0
                                )
                                """.formatted(id));
        }

        private void assertSchemaIsCurrent(Flyway flyway) throws Exception {
                assertEquals(0, flyway.info().pending().length);
                assertEquals(
                                flyway.info().current().getVersion().getVersion(),
                                currentVersion());
        }

        private String currentVersion() throws Exception {
                try (Connection connection = MYSQL.createConnection("");
                                Statement statement = connection.createStatement();
                                ResultSet result = statement.executeQuery("""
                                                SELECT version
                                                  FROM flyway_schema_history
                                                 WHERE success = 1
                                                 ORDER BY installed_rank DESC
                                                 LIMIT 1
                                                """)) {
                        assertTrue(result.next());
                        return result.getString(1);
                }
        }

        private Set<String> integrationTables() throws Exception {
                return tablesMatching("integration_%");
        }

        private Set<String> webhookTables() throws Exception {
                return tablesMatching("webhook_%");
        }

        private Set<String> tablesMatching(String pattern) throws Exception {
                Set<String> tables = new TreeSet<>();
                try (Connection connection = MYSQL.createConnection("");
                                ResultSet result = connection.getMetaData().getTables(
                                                MYSQL.getDatabaseName(),
                                                null,
                                                pattern,
                                                new String[] { "TABLE" })) {
                        while (result.next()) {
                                tables.add(result.getString("TABLE_NAME"));
                        }
                }
                return tables;
        }

        private boolean tableExists(String table) throws Exception {
                try (Connection connection = MYSQL.createConnection("");
                                ResultSet result = connection.getMetaData().getTables(
                                                MYSQL.getDatabaseName(),
                                                null,
                                                table,
                                                new String[] { "TABLE" })) {
                        return result.next();
                }
        }

        private boolean columnExists(String table, String column)
                        throws Exception {
                try (Connection connection = MYSQL.createConnection("");
                                ResultSet result = connection.getMetaData().getColumns(
                                                MYSQL.getDatabaseName(),
                                                null,
                                                table,
                                                column)) {
                        return result.next();
                }
        }

        private String columnCollation(String table, String column)
                        throws Exception {
                try (Connection connection = MYSQL.createConnection("");
                                var statement = connection.prepareStatement("""
                                                SELECT collation_name
                                                  FROM information_schema.columns
                                                 WHERE table_schema = ?
                                                   AND table_name = ?
                                                   AND column_name = ?
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

        private boolean columnNullable(String table, String column)
                        throws Exception {
                try (Connection connection = MYSQL.createConnection("");
                                var statement = connection.prepareStatement("""
                                                SELECT is_nullable
                                                  FROM information_schema.columns
                                                 WHERE table_schema = ?
                                                   AND table_name = ?
                                                   AND column_name = ?
                                                """)) {
                        statement.setString(1, MYSQL.getDatabaseName());
                        statement.setString(2, table);
                        statement.setString(3, column);
                        try (ResultSet result = statement.executeQuery()) {
                                assertTrue(result.next());
                                return "YES".equals(result.getString(1));
                        }
                }
        }

        private boolean indexExists(String table, String index)
                        throws Exception {
                try (Connection connection = MYSQL.createConnection("");
                                ResultSet result = connection.getMetaData().getIndexInfo(
                                                MYSQL.getDatabaseName(),
                                                null,
                                                table,
                                                true,
                                                false)) {
                        while (result.next()) {
                                if (index.equals(result.getString("INDEX_NAME"))) {
                                        return true;
                                }
                        }
                        return false;
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

        private void execute(String sql) throws Exception {
                try (Connection connection = MYSQL.createConnection("");
                                Statement statement = connection.createStatement()) {
                        statement.execute(sql);
                }
        }

        private void insertApplication(String id, String clientId)
                        throws Exception {
                execute("""
                                INSERT INTO integration_application (
                                  id, client_id, application_name, status,
                                  rate_limit_per_minute, max_concurrency,
                                  allowed_source_cidrs, version, created_by, updated_by
                                ) VALUES (
                                  '%s', '%s', 'Migration test', 'ACTIVE',
                                  60, 10, JSON_ARRAY(), 0,
                                  'migration-test', 'migration-test'
                                )
                                """.formatted(id, clientId));
        }

        private static final String JSON_OBJECT_PLACEHOLDER = "JSON_OBJECT()";

        private void insertSecret(
                        String id,
                        String applicationId,
                        String name,
                        long version) throws Exception {
                execute("""
                                INSERT INTO integration_secret (
                                  id, application_id, secret_name, secret_version,
                                  status, key_version, encrypted_data_key,
                                  data_key_nonce, secret_ciphertext, secret_nonce,
                                  secret_hint, created_by
                                ) VALUES (
                                  '%s', '%s', '%s', %d,
                                  'ACTIVE', 'master-v1', 'encrypted-data-key',
                                  'data-key-nonce', 'encrypted-secret', 'secret-nonce',
                                  '12345678', 'migration-test'
                                )
                                """.formatted(id, applicationId, name, version));
        }

        private void insertConnectorConfig(
                        String id,
                        String applicationId,
                        String name,
                        String configurationExpression,
                        String hostsExpression) throws Exception {
                execute("""
                                INSERT INTO integration_connector_config (
                                  id, application_id, config_name, connector_code,
                                  status, configuration_document,
                                  allowed_hosts_document, version,
                                  created_by, updated_by
                                ) VALUES (
                                  '%s', '%s', '%s', 'http-json',
                                  'ACTIVE', %s, %s, 0,
                                  'migration-test', 'migration-test'
                                )
                                """.formatted(
                                id,
                                applicationId,
                                name,
                                configurationExpression,
                                hostsExpression));
        }

        private void incrementRateLimitBucket() throws Exception {
                execute("""
                                INSERT INTO integration_rate_limit_bucket (
                                  bucket_key, window_epoch, request_count
                                ) VALUES (
                                  'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                                  29772000,
                                  1
                                )
                                ON DUPLICATE KEY UPDATE
                                  request_count = request_count + 1
                                """);
        }

        private void insertIdempotency(
                        String id,
                        String applicationId,
                        String operation,
                        String key) throws Exception {
                execute("""
                                INSERT INTO integration_idempotency_record (
                                  id, application_id, operation, idempotency_key,
                                  request_hash, status, fencing_token,
                                  processing_started_at, expires_at
                                ) VALUES (
                                  '%s', '%s', '%s', '%s',
                                  'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                                  'PROCESSING', 1,
                                  CURRENT_TIMESTAMP(6),
                                  TIMESTAMPADD(DAY, 7, CURRENT_TIMESTAMP(6))
                                )
                                """.formatted(id, applicationId, operation, key));
        }

        private void insertBinding(
                        String id,
                        String applicationId,
                        String businessId,
                        String processInstanceId) throws Exception {
                execute("""
                                INSERT INTO integration_process_binding (
                                  id, application_id, external_system, business_type,
                                  business_id, process_instance_id,
                                  process_definition_key
                                ) VALUES (
                                  '%s', '%s', 'project-system', 'change-request',
                                  '%s', '%s', 'project_change_process'
                                )
                                """.formatted(
                                id,
                                applicationId,
                                businessId,
                                processInstanceId));
        }

        private void insertBindingWithVersion(
                        String id,
                        String applicationId,
                        String businessId,
                        String businessVersion,
                        String processInstanceId) throws Exception {
                execute("""
                                INSERT INTO integration_process_binding (
                                  id, application_id, external_system, business_type,
                                  business_id, business_version, process_instance_id,
                                  process_definition_key
                                ) VALUES (
                                  '%s', '%s', 'project-system', 'change-request',
                                  '%s', '%s', '%s', 'project_change_process'
                                )
                                """.formatted(
                                id,
                                applicationId,
                                businessId,
                                businessVersion,
                                processInstanceId));
        }

        private void insertWebhookEndpoint(
                        String id,
                        String applicationId) throws Exception {
                execute("""
                                INSERT INTO webhook_endpoint (
                                  id, application_id, endpoint_name, endpoint_url,
                                  endpoint_hash, status, secret_ciphertext,
                                  secret_version, secret_hint, created_by, updated_by
                                ) VALUES (
                                  '%s', '%s', 'Migration endpoint',
                                  'https://example.com/webhook/%s',
                                  'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                                  'ACTIVE', 'encrypted-secret', 1, '12345678',
                                  'migration-test', 'migration-test'
                                )
                                """.formatted(id, applicationId, id));
        }

        private void insertWebhookSubscription(
                        String id,
                        String applicationId,
                        String endpointId) throws Exception {
                execute("""
                                INSERT INTO webhook_subscription (
                                  id, application_id, endpoint_id, event_type,
                                  status, created_by, updated_by
                                ) VALUES (
                                  '%s', '%s', '%s',
                                  'com.flow.process.started.v1',
                                  'ACTIVE', 'migration-test', 'migration-test'
                                )
                                """.formatted(id, applicationId, endpointId));
        }

        private void insertWebhookEvent(
                        String id,
                        String applicationId) throws Exception {
                execute("""
                                INSERT INTO webhook_event (
                                  event_id, source_event_key, application_id,
                                  event_type, subject, process_instance_id,
                                  trace_id, payload_document, occurred_at, expires_at
                                ) VALUES (
                                  '%s', 'source-%s', '%s',
                                  'com.flow.process.started.v1',
                                  'process-instance/process-1', 'process-1',
                                  'trace-1', JSON_OBJECT('specversion', '1.0'),
                                  CURRENT_TIMESTAMP(6),
                                  TIMESTAMPADD(DAY, 30, CURRENT_TIMESTAMP(6))
                                )
                                """.formatted(id, id, applicationId));
        }

        private void insertWebhookDelivery(
                        String id,
                        String applicationId,
                        String subscriptionId,
                        String eventId,
                        int replaySequence) throws Exception {
                execute("""
                                INSERT INTO webhook_delivery (
                                  id, application_id, subscription_id, event_id,
                                  replay_sequence, status, attempt_count,
                                  max_attempts, next_attempt_at,
                                  signing_secret_ciphertext,
                                  signing_secret_version, created_by
                                ) VALUES (
                                  '%s', '%s', '%s', '%s',
                                  %d, 'PENDING', 0, 8,
                                  CURRENT_TIMESTAMP(6),
                                  'encrypted-secret', 1, 'migration-test'
                                )
                                """.formatted(
                                id,
                                applicationId,
                                subscriptionId,
                                eventId,
                                replaySequence));
        }
}
