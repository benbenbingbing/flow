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
