package com.workflow.migration;

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

@Testcontainers
class IntegrationApplicationMigrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("workflow")
                    .withUsername("workflow_test")
                    .withPassword("workflow_test_password");

    @BeforeEach
    void cleanDatabase() {
        flyway().clean();
    }

    @Test
    void freshDatabaseMigratesThroughIntegrationApplicationSchema()
            throws Exception {
        Flyway flyway = flyway();
        flyway.migrate();

        assertEquals("013", currentVersion());
        assertEquals(
                Set.of(
                        "integration_application",
                        "integration_application_credential",
                        "integration_application_scope",
                        "integration_process_grant",
                        "integration_rate_limit_bucket"),
                integrationTables());
        assertTrue(indexExists(
                "integration_application_credential",
                "uk_integration_credential_active"));
        assertFalse(columnExists(
                "integration_application_credential",
                "client_secret"));
        assertTrue(columnExists(
                "integration_application_credential",
                "secret_hash"));
        assertEquals(4, countRows(
                "SELECT COUNT(*) FROM sys_menu "
                        + "WHERE id LIKE 'integration_perm_%'"));
        for (String table : integrationTables()) {
            assertTrue(columnExists(table, "create_time"));
            assertTrue(columnExists(table, "update_time"));
        }
    }

    @Test
    void versionTwelveUpgradePreservesExistingBusinessData()
            throws Exception {
        Flyway.configure()
                .dataSource(
                        MYSQL.getJdbcUrl(),
                        MYSQL.getUsername(),
                        MYSQL.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .target(MigrationVersion.fromVersion("12"))
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
        assertFalse(tableExists("integration_application"));

        flyway().migrate();

        assertEquals("013", currentVersion());
        assertEquals(1, countRows(
                "SELECT COUNT(*) FROM sys_dict "
                        + "WHERE id = 'upgrade-sentinel' "
                        + "AND dict_code = 'upgrade_sentinel'"));
        assertTrue(tableExists("integration_application"));
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
                    for (int iteration = 0;
                            iteration < incrementsPerWorker;
                            iteration++) {
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
        Set<String> tables = new TreeSet<>();
        try (Connection connection = MYSQL.createConnection("");
                ResultSet result = connection.getMetaData().getTables(
                        MYSQL.getDatabaseName(),
                        null,
                        "integration_%",
                        new String[]{"TABLE"})) {
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
                        new String[]{"TABLE"})) {
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
}
