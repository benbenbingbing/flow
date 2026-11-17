package com.workflow.dbmigrator;

import db.migration.V137__flow_action_before_start_execution;
import com.workflow.integration.database.api.DatabaseVendor;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 实际升级、NULL 约束反例、历史数据/索引保留，以及七方言空库结构核对。 */
class FlowActionBeforeStartMigrationTest {
    @TempDir Path migrations;

    @ParameterizedTest
    @ValueSource(strings = {"MySQL"})
    void migratePreservesExistingRowsAndAllowsPreStartExecution(String mode) throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=" + mode + ";DB_CLOSE_DELAY=-1";
        Files.writeString(migrations.resolve("V136__test_previous_schema.sql"), """
                CREATE TABLE process_action_execution(id VARCHAR(64) PRIMARY KEY, process_instance_id VARCHAR(64) NOT NULL);
                CREATE INDEX execution_instance ON process_action_execution(process_instance_id);
                INSERT INTO process_action_execution VALUES ('old','instance-1');
                """);
        var previous = Flyway.configure().dataSource(url, "sa", "").locations("filesystem:" + migrations).target("136").load();
        assertEquals(1, previous.migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.execute("INSERT INTO process_action_execution VALUES ('before',NULL)"));
        }
        var flyway = Flyway.configure().dataSource(url, "sa", "").locations("filesystem:" + migrations)
                .javaMigrations(new V137__flow_action_before_start_execution()).load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate(); assertEquals(0, flyway.migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            Context context = mock(Context.class); when(context.getConnection()).thenReturn(connection);
            new V137__flow_action_before_start_execution().migrate(context);
            statement.execute("INSERT INTO process_action_execution VALUES ('before',NULL)");
            try (var rows = statement.executeQuery("SELECT * FROM process_action_execution ORDER BY id")) {
                assertEquals(ResultSetMetaData.columnNullable, rows.getMetaData().isNullable(2));
                assertEquals(64, rows.getMetaData().getPrecision(2));
                assertTrue(rows.next()); assertEquals("before", rows.getString(1)); assertNull(rows.getString(2));
                assertTrue(rows.next()); assertEquals("old", rows.getString(1)); assertEquals("instance-1", rows.getString(2));
            }
            try (var rows = connection.getMetaData().getIndexInfo(null, null, "PROCESS_ACTION_EXECUTION", false, false)) {
                boolean found = false;
                while (rows.next()) if ("EXECUTION_INSTANCE".equalsIgnoreCase(rows.getString("INDEX_NAME"))) found = true;
                assertTrue(found);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"PostgreSQL", "Oracle"})
    void nonMySqlDdlHasEquivalentNullableSemanticsInH2CompatibilityMode(String mode) throws Exception {
        var vendor = mode.equals("Oracle") ? DatabaseVendor.ORACLE : DatabaseVendor.POSTGRESQL;
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=" + mode, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE process_action_execution(process_instance_id VARCHAR(64) NOT NULL)");
            statement.execute("INSERT INTO process_action_execution VALUES ('old-instance')");
            for (String ddl : V137__flow_action_before_start_execution.statements(vendor)) statement.execute(ddl);
            statement.execute("INSERT INTO process_action_execution VALUES (NULL)");
            try (var rows = statement.executeQuery("SELECT * FROM process_action_execution")) {
                assertEquals(ResultSetMetaData.columnNullable, rows.getMetaData().isNullable(1));
                assertEquals(64, rows.getMetaData().getPrecision(1));
                assertTrue(rows.next()); assertEquals("old-instance", rows.getString(1));
                assertTrue(rows.next()); assertNull(rows.getString(1));
            }
        }
    }

    @Test void allSevenFinalScriptsHaveEquivalentNullableColumnAndCompleteTableInventory() throws Exception {
        Map<String, List<String>> inventory = null;
        for (String dialect : List.of("mysql", "pg", "oracle", "dameng", "kingbase", "obmysql", "oboracle")) {
            String sql = Files.readString(Path.of("../../docs/sql/" + dialect + ".sql"));
            String normalized = sql.replace("`", "").replace("\"", "").toLowerCase(Locale.ROOT);
            var tables = Pattern.compile("create table (\\w+) \\(([\\s\\S]*?)\\n\\)").matcher(normalized);
            Map<String, List<String>> current = new TreeMap<>();
            String actionTable = null;
            while (tables.find()) {
                String name = tables.group(1), body = tables.group(2);
                var columns = body.lines().map(String::trim).filter(line -> line.matches("\\w+ (?:varchar|varchar2|char|number|bigint|int|tinyint|smallint|decimal|numeric|text|longtext|mediumtext|clob|blob|longblob|bit|boolean|timestamp|datetime|date|time|double|float|binary|varbinary|bytea|json|serial).*"))
                        .map(line -> line.substring(0, line.indexOf(' '))).sorted().toList();
                current.put(name, columns);
                if (name.equals("process_action_execution")) actionTable = body;
            }
            assertEquals(202, current.size(), dialect);
            assertEquals(2817, current.values().stream().mapToInt(List::size).sum(), dialect);
            if (inventory == null) inventory = current; else assertEquals(inventory, current, dialect);
            assertNotNull(actionTable);
            var column = Pattern.compile("(?m)^\\s*process_instance_id ([^\\n]+)").matcher(actionTable);
            assertTrue(column.find());
            assertTrue(column.group(1).startsWith("varchar(64)") || column.group(1).startsWith("varchar2(64)"));
            assertFalse(column.group(1).contains("not null"), dialect);
            assertTrue(sql.contains(V137__flow_action_before_start_execution.COMMENT), dialect);
        }
        for (var vendor : DatabaseVendor.values()) {
            String ddl = String.join(" ", V137__flow_action_before_start_execution.statements(vendor));
            assertTrue(ddl.contains("process_action_execution"));
            assertTrue(ddl.contains(V137__flow_action_before_start_execution.COMMENT));
            assertFalse(ddl.contains("DROP TABLE"));
        }
    }
}
