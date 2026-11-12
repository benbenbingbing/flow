package com.workflow.dbmigrator;

import db.migration.V131__identity_source_snapshot;
import com.workflow.integration.database.api.DatabaseVendor;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 实际迁移、MySQL 手工升级与 Flyway 兼容，以及七种空库脚本的结构和注释同步。 */
class IdentitySourceSnapshotMigrationTest {
    @TempDir Path migrations;

    @Test void flywayCreatesNineColumnsAndDoesNotBackfillOrGrowOnRepeat() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        var flyway = Flyway.configure().dataSource(url, "sa", "").locations("filesystem:" + migrations)
                .javaMigrations(new V131__identity_source_snapshot()).load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate(); assertEquals(0, flyway.migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            Context context = mock(Context.class); when(context.getConnection()).thenReturn(connection);
            new V131__identity_source_snapshot().migrate(context);
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM sys_identity_source_snapshot")) {
                assertEquals(9, rows.getMetaData().getColumnCount()); assertFalse(rows.next());
            }
            var columns = connection.getMetaData().getColumns(null, null, "sys_identity_source_snapshot", null);
            while (columns.next()) {
                assertFalse(columns.getString("REMARKS").isBlank());
                assertEquals(columns.getString("COLUMN_NAME").equals("source_version") ? 1 : 0, columns.getInt("NULLABLE"));
            }
        }
    }

    @ParameterizedTest @EnumSource(DatabaseVendor.class)
    void allDialectScriptsMatchMigrationStructureAndComments(DatabaseVendor vendor) throws Exception {
        String file = switch (vendor) {
            case MYSQL -> "mysql"; case POSTGRESQL -> "pg"; case ORACLE -> "oracle";
            case DM -> "dameng"; case KINGBASE -> "kingbase";
            case OCEANBASE_MYSQL -> "obmysql"; case OCEANBASE_ORACLE -> "oboracle";
        };
        String script = Files.readString(Path.of("../../docs/sql/" + file + ".sql"));
        String normal = normalize(script);
        for (String sql : V131__identity_source_snapshot.statements(vendor)) {
            assertTrue(normal.contains(normalize(sql)), () -> file + " 缺少 V131 的结构或用途注释：" + sql);
        }
    }

    @Test void manualMysqlSqlMatchesHistoricalMigrationAndInitializer() throws Exception {
        // 历史迁移保持不变；手工入口只增加存在性保护，DDL 与原迁移必须完全等价。
        String ddl = manualSql().lines().filter(line -> !line.stripLeading().startsWith("--"))
                .reduce("", (result, line) -> result + "\n" + line);
        ddl = ddl.replace("CREATE TABLE IF NOT EXISTS", "CREATE TABLE").replace(";", "");
        String historical = V131__identity_source_snapshot.statements(DatabaseVendor.MYSQL).get(0);
        assertEquals(normalize(historical), normalize(ddl));
        assertTrue(normalize(Files.readString(Path.of("../../docs/sql/mysql.sql"))).contains(normalize(ddl)));
    }

    @Test void manualSqlCanRepeatWithoutChangingSavedOriginalData() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            executeManualSql(connection);
            String raw = "{\"说明\":\"" + "原文".repeat(10_000) + "\",\"id\":9007199254740993}";
            try (var insert = connection.prepareStatement("INSERT INTO sys_identity_source_snapshot "
                    + "(source_key, source_version, payload_hash, raw_json, raw_hash, raw_bytes, recorded_at, recorded_by) "
                    + "VALUES (?, NULL, ?, ?, ?, ?, '2026-11-12 12:34:56.123456', 'manual-test')")) {
                insert.setString(1, "a".repeat(64));
                insert.setString(2, "b".repeat(64));
                insert.setString(3, raw);
                insert.setString(4, "c".repeat(64));
                insert.setInt(5, raw.getBytes(StandardCharsets.UTF_8).length);
                insert.executeUpdate();
            }
            executeManualSql(connection);
            var context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            new V131__identity_source_snapshot().migrate(context);
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM sys_identity_source_snapshot")) {
                assertTrue(rows.next());
                assertEquals(9, rows.getMetaData().getColumnCount());
                assertEquals("a".repeat(64), rows.getString("source_key"));
                assertNull(rows.getObject("source_version"));
                assertEquals("b".repeat(64), rows.getString("payload_hash"));
                assertEquals(raw, rows.getString("raw_json"));
                assertEquals("c".repeat(64), rows.getString("raw_hash"));
                assertEquals(raw.getBytes(StandardCharsets.UTF_8).length, rows.getInt("raw_bytes"));
                assertEquals(1, rows.getInt("is_current"));
                assertEquals("2026-11-12 12:34:56.123456", rows.getTimestamp("recorded_at").toString());
                assertEquals("manual-test", rows.getString("recorded_by"));
                assertFalse(rows.next());
            }
        }
    }

    @Test void flywayRecordsOriginalJavaMigrationAfterManualSqlWithoutBaselineOrRepair() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Files.writeString(migrations.resolve("V130__test_previous_version.sql"), "CREATE TABLE previous_version_fixture (id INTEGER PRIMARY KEY);");
        // 先登记前置版本，模拟已经由 Flyway 管理的存量库，再手工补建 V131 表。
        var previous = Flyway.configure().dataSource(url, "sa", "").locations("filesystem:" + migrations).target("130").load();
        assertEquals(1, previous.migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            executeManualSql(connection);
        }
        var flyway = Flyway.configure().dataSource(url, "sa", "").locations("filesystem:" + migrations)
                .javaMigrations(new V131__identity_source_snapshot()).load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals("131", flyway.info().current().getVersion().toString());
    }

    /** 直接执行交付给 DBA 的文件，避免测试另写一份 SQL 而漏掉脚本漂移。 */
    private void executeManualSql(Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute(manualSql());
        }
    }

    private String manualSql() throws Exception {
        return Files.readString(Path.of("../../docs/sql/upgrade/mysql/V131__identity_source_snapshot.sql"));
    }

    private String normalize(String text) {
        return text.replace("`", "").replace("\"", "").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).trim();
    }
}
