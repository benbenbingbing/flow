package com.workflow.dbmigrator;

import com.workflow.integration.database.api.DatabaseVendor;
import db.migration.V138__flow_action_dependencies;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlowActionDependenciesMigrationTest {
    @Test void sevenFinalScriptsMatchMigrationColumnSemanticsAndIndexes() throws Exception {
        for (String dialect : List.of("mysql", "obmysql", "pg", "kingbase", "oracle", "dameng", "oboracle")) {
            String sql = Files.readString(Path.of("../../docs/sql/" + dialect + ".sql"));
            String normalized = sql.replace("`", "").replace("\"", "").toLowerCase(Locale.ROOT);
            boolean mysql = dialect.equals("mysql") || dialect.equals("obmysql");
            boolean oracle = List.of("oracle", "dameng", "oboracle").contains(dialect);
            for (var column : V138__flow_action_dependencies.COLUMNS) {
                var table = Pattern.compile("create table " + column.table() + " \\(([\\s\\S]*?)\\n\\)").matcher(normalized);
                assertTrue(table.find(), dialect + ":" + column.table());
                var definition = Pattern.compile("(?m)^\\s*" + column.name() + " ([^\\n]+)").matcher(table.group(1));
                assertTrue(definition.find(), dialect + ":" + column.name());
                String expected = column.large() ? mysql ? "longtext" : oracle ? "clob" : "text"
                        : oracle ? "varchar2(64 char)" : "varchar(64)";
                assertTrue(definition.group(1).startsWith(expected), dialect + ":" + column.name());
                assertFalse(definition.group(1).contains("not null"));
                assertTrue(definition.group(1).contains("default null"));
                assertTrue(sql.contains(column.comment()), dialect + ":" + column.name());
            }
            assertTrue(normalized.contains("idx_action_exec_predecessor"));
            assertTrue(normalized.contains("idx_action_exec_replay_success"));
            assertTrue(normalized.contains("waiting"));
        }
    }
    @Test void migrationPreservesOldRowsSupportsRestartAndFlywayValidation() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE process_action(id VARCHAR(64) PRIMARY KEY)");
            statement.execute("CREATE TABLE process_action_execution(id VARCHAR(64) PRIMARY KEY, status VARCHAR(20), replay_root_id VARCHAR(64))");
            statement.execute("INSERT INTO process_action VALUES ('old')");
            statement.execute("INSERT INTO process_action_execution VALUES ('old','SUCCESS',NULL)");
        }
        var flyway = Flyway.configure().dataSource(url,"sa", "").locations("classpath:no-migrations")
                .baselineOnMigrate(true).baselineVersion("137").javaMigrations(new V138__flow_action_dependencies()).load();
        assertEquals(1, flyway.migrate().migrationsExecuted); flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            var context = mock(Context.class); when(context.getConnection()).thenReturn(connection);
            new V138__flow_action_dependencies().migrate(context);
            try (var rows = statement.executeQuery("SELECT * FROM process_action_execution")) {
                assertTrue(rows.next()); assertEquals("SUCCESS", rows.getString("status"));
                assertNull(rows.getString("predecessor_execution_id")); assertNull(rows.getString("output_json"));
            }
            String large = "合同编号🙂".repeat(18000);
            try (var insert = connection.prepareStatement("INSERT INTO process_action_execution(id,status,output_json) VALUES ('new','SUCCESS',?)")) {
                insert.setString(1, large); insert.executeUpdate();
            }
            try (var rows = statement.executeQuery("SELECT output_json FROM process_action_execution WHERE id='new'")) {
                assertTrue(rows.next()); assertEquals(large, rows.getString(1));
            }
            var indexes = new HashSet<String>();
            try (var rows = connection.getMetaData().getIndexInfo(null, "PUBLIC", "PROCESS_ACTION_EXECUTION", false, false)) {
                while (rows.next()) indexes.add(rows.getString("INDEX_NAME").toLowerCase(Locale.ROOT));
            }
            assertTrue(indexes.contains("idx_action_exec_predecessor")); assertTrue(indexes.contains("idx_action_exec_replay_success"));
        }
    }

    @Test void allSevenVendorsHaveEquivalentNullableColumnsAndPurposeComments() throws Exception {
        for (var vendor : DatabaseVendor.values()) {
            String mode = switch (vendor) {
                case MYSQL, OCEANBASE_MYSQL -> "MySQL";
                case POSTGRESQL, KINGBASE -> "PostgreSQL";
                default -> "Oracle";
            };
            try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=" + mode, "sa", "");
                 var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE process_action(id VARCHAR(64))");
                statement.execute("CREATE TABLE process_action_execution(id VARCHAR(64))");
                for (var column : V138__flow_action_dependencies.COLUMNS) {
                    for (String ddl : V138__flow_action_dependencies.columnStatements(vendor, column)) statement.execute(ddl);
                    try (var rows = statement.executeQuery("SELECT " + column.name() + " FROM " + column.table())) {
                        assertEquals(ResultSetMetaData.columnNullable, rows.getMetaData().isNullable(1));
                        if (!column.large()) assertEquals(64, rows.getMetaData().getPrecision(1));
                    }
                }
            }
        }
    }
}
