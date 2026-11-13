package com.workflow.dbmigrator;

import com.workflow.integration.database.api.DatabaseVendor;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** 执行实际 SQL 文件，验证存量库升级、Flyway 版本控制及七份空库脚本的等价结构。 */
class DictionaryItemExtensionsMigrationTest {
    private static final String MIGRATION = "V133__dictionary_item_extensions.sql";
    @TempDir Path migrations;

    @Test
    void flywayAddsNullableColumnsWithoutChangingOldRowsAndCanValidateAndRepeat() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Files.writeString(migrations.resolve("V132__previous_dictionary.sql"), """
                CREATE TABLE sys_dict_item (id VARCHAR(64) PRIMARY KEY, item_code VARCHAR(100),
                  item_value VARCHAR(200), remark VARCHAR(500));
                INSERT INTO sys_dict_item (id, item_code, item_value, remark) VALUES ('old', 'A', '01', '旧备注');
                """);
        Files.writeString(migrations.resolve(MIGRATION), migrationSql());
        var flyway = Flyway.configure().dataSource(url, "sa", "").locations("filesystem:" + migrations)
                .load();
        assertEquals(2, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM sys_dict_item")) {
                assertTrue(rows.next());
                assertEquals("A", rows.getString("item_code"));
                assertEquals("01", rows.getString("item_value"));
                assertEquals("旧备注", rows.getString("remark"));
                for (int index = 1; index <= 5; index++) assertNull(rows.getString("ext" + index));
                assertFalse(rows.next());
            }
            for (int index = 1; index <= 5; index++) {
                try (var columns = connection.getMetaData().getColumns(null, null, "sys_dict_item", "ext" + index)) {
                    assertTrue(columns.next());
                    assertEquals(500, columns.getInt("COLUMN_SIZE"));
                    assertEquals(1, columns.getInt("NULLABLE"));
                    assertTrue(columns.getString("REMARKS").contains("供定制模块约定用途"));
                }
            }
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE sys_dict_item SET ext1='定制参数', ext5='保留' WHERE id='old'");
            }
            assertEquals(0, flyway.migrate().migrationsExecuted);
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT ext1, ext5 FROM sys_dict_item")) {
                assertTrue(rows.next()); assertEquals("定制参数", rows.getString(1)); assertEquals("保留", rows.getString(2));
            }
        }
    }

    @Test
    void directSqlExecutionAddsColumnsAndRejectsDuplicateDdlWithoutOverwritingData() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE sys_dict_item (id VARCHAR(64))");
                statement.execute("INSERT INTO sys_dict_item VALUES ('old')");
            }
            executeSql(connection);
            try (var statement = connection.createStatement()) {
                statement.execute("UPDATE sys_dict_item SET ext1='已填写'");
            }
            // 普通 ADD COLUMN 只允许执行一次；已登记版本由 Flyway 跳过，不靠 SQL 忽略结构冲突。
            assertThrows(SQLException.class, () -> executeSql(connection));
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM sys_dict_item")) {
                assertEquals(6, rows.getMetaData().getColumnCount());
                assertTrue(rows.next()); assertEquals("已填写", rows.getString("ext1")); assertNull(rows.getString("ext5"));
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
        var table = Pattern.compile("CREATE TABLE [`\"]sys_dict_item[`\"] \\([\\s\\S]*?\\n\\)", Pattern.CASE_INSENSITIVE).matcher(script);
        assertTrue(table.find(), file);
        String columns = normalize(table.group());
        boolean mysql = vendor == DatabaseVendor.MYSQL || vendor == DatabaseVendor.OCEANBASE_MYSQL;
        boolean oracle = vendor == DatabaseVendor.ORACLE || vendor == DatabaseVendor.DM || vendor == DatabaseVendor.OCEANBASE_ORACLE;
        var definitions = Pattern.compile("ALTER TABLE sys_dict_item ADD COLUMN (ext[1-5]) VARCHAR\\(500\\) DEFAULT NULL\\s+COMMENT '([^']+)'")
                .matcher(migrationSql());
        int count = 0;
        while (definitions.find()) {
            String column = definitions.group(1), comment = definitions.group(2);
            String type = oracle ? "VARCHAR2(500 CHAR)" : "VARCHAR(500)";
            String expected = column + " " + type + " DEFAULT NULL" + (mysql ? " COMMENT '" + comment + "'" : "");
            assertTrue(columns.contains(normalize(expected)), () -> file + " 缺少等价字段定义：" + expected);
            if (!mysql) {
                assertTrue(normalize(script).contains(normalize("COMMENT ON COLUMN sys_dict_item." + column + " IS '" + comment + "'")),
                        () -> file + " 缺少用途注释");
            }
            count++;
        }
        assertEquals(5, count, "迁移应包含五个扩展列定义");
    }

    /** 直接读取交付 SQL，避免测试里重新拼接 DDL 而漏掉实际脚本错误。 */
    private String migrationSql() throws Exception {
        try (var stream = getClass().getResourceAsStream("/db/migration/" + MIGRATION)) {
            assertNotNull(stream, MIGRATION);
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private void executeSql(Connection connection) throws Exception {
        try (var statement = connection.createStatement()) { statement.execute(migrationSql()); }
    }

    private String normalize(String sql) {
        return sql.replace("`", "").replace("\"", "").replaceAll("(?i)COLLATE \\w+", "")
                .replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).trim();
    }
}
