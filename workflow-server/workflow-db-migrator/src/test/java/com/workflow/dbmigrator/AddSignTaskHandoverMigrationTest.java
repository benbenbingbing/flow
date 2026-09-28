package com.workflow.dbmigrator;

import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import db.migration.V111__allow_add_sign_task_handover;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 在隔离 H2 中执行 DDL 与实际写入，覆盖七个方言的索引语法族。
 * Oracle 系目录用 H2 信息目录映射夹具模拟；这不等同于在七种数据库上实测。
 */
class AddSignTaskHandoverMigrationTest {

    @ParameterizedTest
    @EnumSource(DatabaseVendor.class)
    void distinctTasksCanMoveToSamePersonWhileGeneratedTaskRemainsUnique(DatabaseVendor vendor) throws Exception {
        try (Connection connection = fixture(vendor, false)) {
            seedTasks(connection);
            assertThrows(SQLException.class, () -> assignAllToBob(connection));

            migrate(connection, vendor);
            migrate(connection, vendor);
            assignAllToBob(connection);

            assertEquals(3, count(connection, "SELECT COUNT(*) FROM process_task_add_sign_user WHERE user_id = 'bob'"));
            assertThrows(SQLException.class, () -> execute(connection, """
                    INSERT INTO process_task_add_sign_user (id, add_sign_id, user_id, generated_task_id)
                    VALUES ('duplicate', 'other-sign', 'bob', 'task-a')
                    """));
            assertLookupIndex(connection, vendor);
        }
    }

    @ParameterizedTest
    @EnumSource(value = DatabaseVendor.class, names = {"MYSQL", "OCEANBASE_MYSQL"}, mode = EnumSource.Mode.EXCLUDE)
    void removesConstraintOwnedIndexBeforeReplacingIt(DatabaseVendor vendor) throws Exception {
        try (Connection connection = fixture(vendor, true)) {
            seedTasks(connection);
            migrate(connection, vendor);
            assignAllToBob(connection);
            assertEquals(3, count(connection, "SELECT COUNT(*) FROM process_task_add_sign_user WHERE user_id = 'bob'"));
            assertLookupIndex(connection, vendor);
        }
    }

    @Test
    void interruptedDdlCanResumeByRecreatingLookupIndex() throws Exception {
        try (Connection connection = fixture(DatabaseVendor.MYSQL, false)) {
            execute(connection, "DROP INDEX uk_add_sign_user");
            migrate(connection, DatabaseVendor.MYSQL);
            assertLookupIndex(connection, DatabaseVendor.MYSQL);
        }
    }

    @Test
    void missingTaskUniquenessStopsBeforeRelaxingPersonUniqueness() throws Exception {
        try (Connection connection = fixture(DatabaseVendor.MYSQL, false)) {
            execute(connection, "DROP INDEX uk_add_sign_generated_task");
            seedTasks(connection);
            assertThrows(SQLException.class, () -> migrate(connection, DatabaseVendor.MYSQL));
            assertThrows(SQLException.class, () -> assignAllToBob(connection));
        }
    }

    @Test
    void occupiedLookupIndexNameStopsBeforeDroppingOldConstraint() throws Exception {
        try (Connection connection = fixture(DatabaseVendor.MYSQL, false)) {
            execute(connection, "CREATE INDEX idx_add_sign_user ON process_task_add_sign_user (user_id)");
            seedTasks(connection);
            assertThrows(SQLException.class, () -> migrate(connection, DatabaseVendor.MYSQL));
            assertThrows(SQLException.class, () -> assignAllToBob(connection));
        }
    }

    private void migrate(Connection connection, DatabaseVendor vendor) throws SQLException {
        V111__allow_add_sign_task_handover migration = new V111__handover_fixture(vendor);
        assertFalse(migration.canExecuteInTransaction());
        migration.migrate(new Context() {
            @Override public Connection getConnection() { return connection; }
            @Override public Configuration getConfiguration() { return null; }
        });
    }

    private Connection fixture(DatabaseVendor vendor, boolean constraint) throws SQLException {
        boolean oracle = vendor == DatabaseVendor.ORACLE || vendor == DatabaseVendor.DM || vendor == DatabaseVendor.OCEANBASE_ORACLE;
        boolean mysql = vendor == DatabaseVendor.MYSQL || vendor == DatabaseVendor.OCEANBASE_MYSQL;
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:addsign_" + UUID.randomUUID()
                + ";MODE=" + (oracle ? "Oracle" : mysql ? "MySQL" : "PostgreSQL")
                + (oracle ? "" : ";DATABASE_TO_LOWER=TRUE"));
        execute(connection, """
                CREATE TABLE process_task_add_sign_user (
                  id VARCHAR(64) PRIMARY KEY, add_sign_id VARCHAR(64) NOT NULL,
                  user_id VARCHAR(64) NOT NULL, generated_task_id VARCHAR(64) NOT NULL)
                """);
        execute(connection, "CREATE UNIQUE INDEX uk_add_sign_generated_task ON process_task_add_sign_user (generated_task_id)");
        if (constraint) {
            execute(connection, "ALTER TABLE process_task_add_sign_user ADD CONSTRAINT uk_add_sign_user UNIQUE (add_sign_id, user_id)");
        } else {
            // 与导出的跨库基线一样使用独立索引；各库基线不同的索引名不会影响按列识别。
            String index = oracle ? "uq_process_task_add_s_02f3077d" : mysql ? "uk_add_sign_user" : "uq_process_task_add_sign_user_uk_add_sign_user";
            execute(connection, "CREATE UNIQUE INDEX " + index + " ON process_task_add_sign_user (add_sign_id, user_id)");
        }
        if (oracle) {
            execute(connection, """
                    CREATE VIEW all_constraints AS
                    SELECT constraint_schema AS owner, constraint_name, table_name,
                      CASE WHEN constraint_type = 'UNIQUE' THEN 'U' ELSE constraint_type END AS constraint_type
                    FROM information_schema.table_constraints
                    """);
            execute(connection, """
                    CREATE VIEW all_cons_columns AS
                    SELECT constraint_schema AS owner, constraint_name, table_name,
                      column_name, ordinal_position AS position
                    FROM information_schema.key_column_usage
                    """);
        }
        return connection;
    }

    private void seedTasks(Connection connection) throws SQLException {
        execute(connection, "INSERT INTO process_task_add_sign_user VALUES ('a', 'sign-1', 'alice', 'task-a')");
        execute(connection, "INSERT INTO process_task_add_sign_user VALUES ('b', 'sign-1', 'bob', 'task-b')");
        execute(connection, "INSERT INTO process_task_add_sign_user VALUES ('c', 'sign-1', 'carol', 'task-c')");
    }

    private void assignAllToBob(Connection connection) throws SQLException {
        execute(connection, "UPDATE process_task_add_sign_user SET user_id = 'bob' WHERE id IN ('a', 'c')");
    }

    private void assertLookupIndex(Connection connection, DatabaseVendor vendor) throws SQLException {
        String table = DatabaseDialects.runtime(vendor).physicalName("process_task_add_sign_user");
        int columns = 0;
        try (var rows = connection.getMetaData().getIndexInfo(connection.getCatalog(), connection.getSchema(), table, false, false)) {
            while (rows.next()) if ("idx_add_sign_user".equalsIgnoreCase(rows.getString("INDEX_NAME"))) {
                assertTrue(rows.getBoolean("NON_UNIQUE"));
                columns++;
            }
        }
        assertEquals(2, columns);
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private int count(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}

/** BaseJavaMigration 要求实际类名保持版本格式；独立测试类仅替换连接产品识别。 */
class V111__handover_fixture extends V111__allow_add_sign_task_handover {
    private final DatabaseVendor vendor;

    V111__handover_fixture(DatabaseVendor vendor) { this.vendor = vendor; }

    @Override protected DatabaseVendor resolveVendor(Connection ignored) { return vendor; }
}
