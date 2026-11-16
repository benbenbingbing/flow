package com.workflow.dbmigrator;

import com.workflow.integration.database.api.DatabaseVendor;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** 执行实际增量迁移，验证历史数据、可查询列及七份空库脚本的方言等价语义。 */
class IdentityMainTableExtensionsMigrationTest {
    private static final String MIGRATION = "V135__identity_main_table_extensions.sql";
    @TempDir Path migrations;

    @Test
    void addsFiveNullableColumnsToBothTablesAndPreservesDataAcrossFlywayRetries() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Files.writeString(migrations.resolve("V134__previous_identity_tables.sql"), """
                CREATE TABLE sys_user (id VARCHAR(64) PRIMARY KEY, username VARCHAR(50));
                CREATE TABLE sys_organization (id VARCHAR(64) PRIMARY KEY, org_code VARCHAR(100));
                INSERT INTO sys_user VALUES ('u1', 'alice');
                INSERT INTO sys_organization VALUES ('o1', 'HQ');
                """);
        Files.writeString(migrations.resolve(MIGRATION), migrationSql());
        var flyway = Flyway.configure().dataSource(url, "sa", "").locations("filesystem:" + migrations).load();
        assertEquals(2, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            for (String table : List.of("sys_user", "sys_organization")) {
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM " + table)) {
                    assertTrue(rows.next());
                    assertEquals(table.equals("sys_user") ? "alice" : "HQ", rows.getString(2));
                    for (int index = 1; index <= 5; index++) assertNull(rows.getString("ext" + index));
                    assertFalse(rows.next());
                }
                for (int index = 1; index <= 5; index++) {
                    try (var columns = connection.getMetaData().getColumns(null, null, table, "ext" + index)) {
                        assertTrue(columns.next());
                        assertEquals(500, columns.getInt("COLUMN_SIZE"));
                        assertEquals(1, columns.getInt("NULLABLE"));
                        assertTrue(columns.getString("REMARKS").contains("用于主表查询和关联"));
                    }
                }
                try (var statement = connection.createStatement()) {
                    statement.execute("UPDATE " + table + " SET ext1='0007', ext5='中文'");
                }
            }
            assertEquals(0, flyway.migrate().migrationsExecuted);
            for (String table : List.of("sys_user", "sys_organization")) {
                try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                        "SELECT ext5 FROM " + table + " WHERE ext1='0007'")) {
                    assertTrue(rows.next()); assertEquals("中文", rows.getString(1));
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(DatabaseVendor.class)
    void sevenInitializersMatchColumnTypesLengthsNullabilityDefaultsAndComments(DatabaseVendor vendor) throws Exception {
        String file = switch (vendor) {
            case MYSQL -> "mysql"; case POSTGRESQL -> "pg"; case ORACLE -> "oracle";
            case DM -> "dameng"; case KINGBASE -> "kingbase";
            case OCEANBASE_MYSQL -> "obmysql"; case OCEANBASE_ORACLE -> "oboracle";
        };
        String script = Files.readString(Path.of("../../docs/sql/" + file + ".sql"));
        boolean mysql = vendor == DatabaseVendor.MYSQL || vendor == DatabaseVendor.OCEANBASE_MYSQL;
        boolean oracle = vendor == DatabaseVendor.ORACLE || vendor == DatabaseVendor.DM || vendor == DatabaseVendor.OCEANBASE_ORACLE;
        for (String table : List.of("sys_user", "sys_organization")) {
            var definition = Pattern.compile("CREATE TABLE [`\"]" + table + "[`\"] \\([\\s\\S]*?\\n\\)", Pattern.CASE_INSENSITIVE).matcher(script);
            assertTrue(definition.find(), file + ": " + table);
            String columns = normalize(definition.group());
            var migration = Pattern.compile("ALTER TABLE " + table + " ADD COLUMN (ext[1-5]) VARCHAR\\(500\\) DEFAULT NULL\\s+COMMENT '([^']+)'")
                    .matcher(migrationSql());
            int count = 0;
            while (migration.find()) {
                String column = migration.group(1), comment = migration.group(2);
                String expected = column + " " + (oracle ? "VARCHAR2(500 CHAR)" : "VARCHAR(500)")
                        + " DEFAULT NULL" + (mysql ? " COMMENT '" + comment + "'" : "");
                assertTrue(columns.contains(normalize(expected)), file + ": " + expected);
                if (!mysql) assertTrue(normalize(script).contains(normalize(
                        "COMMENT ON COLUMN " + table + "." + column + " IS '" + comment + "'")), file + ": " + comment);
                count++;
            }
            assertEquals(5, count);
        }
    }

    private String migrationSql() throws Exception {
        try (var stream = getClass().getResourceAsStream("/db/migration/" + MIGRATION)) {
            assertNotNull(stream, MIGRATION);
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private String normalize(String sql) {
        return sql.replace("`", "").replace("\"", "").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
