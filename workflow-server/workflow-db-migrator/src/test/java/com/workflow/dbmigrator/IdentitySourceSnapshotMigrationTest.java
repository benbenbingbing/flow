package com.workflow.dbmigrator;

import db.migration.V131__identity_source_snapshot;
import com.workflow.integration.database.api.DatabaseVendor;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 实际迁移重试、Flyway 校验及七种空库脚本的字段、注释与大文本方言同步。 */
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

    private String normalize(String text) {
        return text.replace("`", "").replace("\"", "").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).trim();
    }
}
