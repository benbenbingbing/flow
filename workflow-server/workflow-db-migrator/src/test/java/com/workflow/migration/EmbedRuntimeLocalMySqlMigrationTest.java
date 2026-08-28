package com.workflow.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Opt-in Embed migration contract for a developer's local macOS MySQL service.
 *
 * <p>The test never reads {@code DB_NAME}. It creates a new, explicitly named database,
 * rejects any name outside the {@code workflow_embed_test_} namespace, refuses to reuse
 * an existing database by default, and drops only the database it successfully created.
 * A DBA-precreated empty database is supported only through the explicit reuse flag; that
 * mode verifies the schema is empty before use and cleans its test objects without dropping
 * the database.</p>
 */
@EnabledIfEnvironmentVariable(named = "EMBED_LOCAL_MYSQL_TEST", matches = "(?i:true)")
class EmbedRuntimeLocalMySqlMigrationTest extends AbstractEmbedRuntimeMigrationTest {

    private static final Pattern SAFE_DATABASE_NAME = Pattern.compile(
            "workflow_embed_test_[a-z0-9][a-z0-9_]{0,43}");
    private static final Map<String, String> ENVIRONMENT = System.getenv();

    private static String jdbcUrl;
    private static String adminJdbcUrl;
    private static String username;
    private static String password;
    private static String database;
    private static boolean databaseCreated;
    private static boolean databaseReused;

    /**
     * Creates a brand-new test database on loopback MySQL, or explicitly claims a
     * DBA-precreated empty database. Reuse is opt-in because the inherited contract invokes
     * Flyway clean and must never point at a database containing developer or business data.
     */
    @BeforeAll
    static void createIsolatedDatabase() throws Exception {
        String host = environmentOrDefault("DB_HOST", "127.0.0.1")
                .toLowerCase(Locale.ROOT);
        if (!"127.0.0.1".equals(host) && !"localhost".equals(host)) {
            throw new IllegalArgumentException(
                    "DB_HOST must be 127.0.0.1 or localhost for the local Embed test");
        }

        int port = parsePort(environmentOrDefault("DB_PORT", "3306"));
        username = requiredEnvironment("DB_USERNAME");
        password = requiredEnvironmentAllowEmpty("DB_PASSWORD");
        database = requiredEnvironment("EMBED_LOCAL_MYSQL_DATABASE");
        validateDatabaseName(database);

        String options = "useUnicode=true&characterEncoding=UTF-8&useSSL=false"
                + "&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                + "&connectTimeout=3000&socketTimeout=5000";
        adminJdbcUrl = "jdbc:mysql://" + host + ":" + port + "/?" + options;
        jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + "?" + options;

        try (Connection connection = DriverManager.getConnection(
                adminJdbcUrl, username, password)) {
            boolean reuseEmptyDatabase = booleanEnvironment(
                    "EMBED_LOCAL_MYSQL_REUSE_EMPTY_DATABASE");
            boolean databaseExists = databaseExists(connection, database);
            if (reuseEmptyDatabase) {
                if (!databaseExists) {
                    throw new IllegalStateException(
                            "Precreated local test database does not exist: " + database);
                }
                ensureDatabaseIsEmpty(connection, database);
                databaseReused = true;
            } else {
                if (databaseExists) {
                    throw new IllegalStateException(
                            "Refusing to reuse existing local test database without "
                                    + "EMBED_LOCAL_MYSQL_REUSE_EMPTY_DATABASE=true: "
                                    + database);
                }
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE DATABASE `" + database
                            + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                }
                databaseCreated = true;
            }
        }
    }

    /**
     * Drops only a database created by this test. In explicit reuse mode, Flyway removes
     * the test objects but leaves the DBA-owned empty database in place.
     */
    @AfterAll
    static void dropIsolatedDatabase() throws Exception {
        if (databaseReused) {
            Flyway.configure()
                    .dataSource(jdbcUrl, username, password)
                    .locations("classpath:db/migration")
                    .cleanDisabled(false)
                    .load()
                    .clean();
            try (Connection connection = DriverManager.getConnection(
                    adminJdbcUrl, username, password)) {
                ensureDatabaseIsEmpty(connection, database);
            }
            databaseReused = false;
            return;
        }
        if (!databaseCreated) {
            return;
        }
        validateDatabaseName(database);
        try (Connection connection = DriverManager.getConnection(
                adminJdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP DATABASE `" + database + "`");
            databaseCreated = false;
        }
    }

    private static boolean databaseExists(Connection connection, String name)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                  FROM information_schema.schemata
                 WHERE schema_name = ?
                """)) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    /**
     * Rejects reuse when the named schema contains any table, view, routine, trigger, or
     * event. This check is intentionally broader than Flyway history so an uninitialized
     * but nonempty developer database cannot be mistaken for a disposable test schema.
     */
    private static void ensureDatabaseIsEmpty(Connection connection, String name)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT
                    (SELECT COUNT(*) FROM information_schema.tables
                      WHERE table_schema = ?)
                  + (SELECT COUNT(*) FROM information_schema.routines
                      WHERE routine_schema = ?)
                  + (SELECT COUNT(*) FROM information_schema.triggers
                      WHERE trigger_schema = ?)
                  + (SELECT COUNT(*) FROM information_schema.events
                      WHERE event_schema = ?) AS object_count
                """)) {
            for (int index = 1; index <= 4; index++) {
                statement.setString(index, name);
            }
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getLong("object_count") != 0) {
                    throw new IllegalStateException(
                            "Refusing to reuse nonempty local test database: " + name);
                }
            }
        }
    }

    private static void validateDatabaseName(String name) {
        if (name == null || !SAFE_DATABASE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "EMBED_LOCAL_MYSQL_DATABASE must match "
                            + "workflow_embed_test_[a-z0-9][a-z0-9_]{0,43}");
        }
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("DB_PORT must be between 1 and 65535");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("DB_PORT must be an integer", exception);
        }
    }

    private static boolean booleanEnvironment(String name) {
        String value = ENVIRONMENT.get(name);
        if (value == null || value.isBlank()) {
            return false;
        }
        if ("true".equalsIgnoreCase(value.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(value.trim())) {
            return false;
        }
        throw new IllegalArgumentException(name + " must be true or false");
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = ENVIRONMENT.get(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String requiredEnvironment(String name) {
        String value = ENVIRONMENT.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required environment variable: " + name);
        }
        return value.trim();
    }

    private static String requiredEnvironmentAllowEmpty(String name) {
        String value = ENVIRONMENT.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Missing required environment variable: " + name);
        }
        return value;
    }

    @Override
    protected String jdbcUrl() {
        return jdbcUrl;
    }

    @Override
    protected String databaseUsername() {
        return username;
    }

    @Override
    protected String databasePassword() {
        return password;
    }

    @Override
    protected String databaseName() {
        return database;
    }
}
