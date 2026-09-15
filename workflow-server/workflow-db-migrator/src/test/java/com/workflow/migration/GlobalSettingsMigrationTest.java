package com.workflow.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 在真实 MySQL 上针对已有系统基础表验证 V090 的表、文本列、约束和菜单。
 * 只运行本次迁移，历史全量迁移重放由独立集成检查承担。
 * 可通过 settingsTestJdbcUrl 指向预建的空白回环测试库；不读取业务数据库配置，不清理外部库。
 */
class GlobalSettingsMigrationTest {
    private static MySQLContainer<?> mysql;
    private static String url;
    private static String username;
    private static String password;
    @TempDir
    static Path migrationDirectory;

    @BeforeAll
    static void setup() throws Exception {
        url = System.getProperty("settingsTestJdbcUrl");
        if (url != null) {
            if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/workflow_settings_test_[a-z0-9_]+(\\?.*)?")) {
                throw new IllegalArgumentException("仅允许显式指定回环地址的 workflow_settings_test_ 测试库");
            }
            username = System.getProperty("settingsTestUsername", "root");
            password = System.getProperty("settingsTestPassword", "");
            assertEquals(0, count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()"),
                    "测试库必须为空；本测试不会清理已有数据库");
        } else {
            assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "真实 MySQL 测试需要 Docker 或显式空白测试库");
            mysql = new MySQLContainer<>("mysql:8.4").withDatabaseName("workflow_settings_test_container");
            mysql.start();
            url = mysql.getJdbcUrl(); username = mysql.getUsername(); password = mysql.getPassword();
        }
    }

    @AfterAll
    static void cleanup() {
        if (mysql != null) mysql.stop();
    }

    @Test
    void upgradePreservesExistingDataAndCreatesPortableTextSettings() throws Exception {
        // 空库先由 Flyway 建立正常历史表，再注入已有系统表夹具，不需要基线或修复历史。
        flyway("latest").migrate();
        // 从既有迁移读取基础表定义作为夹具，不改写历史文件，也不 baseline/repair 业务库。
        String original;
        try (var input = GlobalSettingsMigrationTest.class.getResourceAsStream("/db/migration/V001__business_schema.sql")) {
            assertNotNull(input);
            original = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String table : new String[]{"sys_user", "sys_role", "sys_menu", "sys_role_menu"}) {
            int start = original.indexOf("CREATE TABLE `" + table + "`");
            assertTrue(start >= 0);
            execute(original.substring(start, original.indexOf(';', start) + 1));
        }
        execute("INSERT INTO sys_user (id, username, password) VALUES ('existing-user', 'migration-fixture', 'unused')");
        execute("INSERT INTO sys_role (id, role_name, role_code, deleted) VALUES ('admin-role', '测试管理员', 'super_admin', 0), ('reader-role', '测试用户', 'reader', 0)");
        try (var input = GlobalSettingsMigrationTest.class.getResourceAsStream("/db/migration/V090__global_settings.sql")) {
            assertNotNull(input);
            Files.copy(input, migrationDirectory.resolve("V090__global_settings.sql"));
        }
        int userCount = count("SELECT COUNT(*) FROM sys_user");
        Flyway latest = flyway("90");
        latest.migrate();
        latest.validate();
        assertEquals(0, latest.info().pending().length);
        assertEquals(userCount, count("SELECT COUNT(*) FROM sys_user"));
        assertEquals("text", scalar("SELECT data_type FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_global_setting' AND column_name = 'setting_value'"));
        assertEquals("BOOLEAN", scalar("SELECT setting_value_type FROM sys_global_setting WHERE scope_type = 'SYSTEM' AND owner_id = '0'"));
        assertEquals("false", scalar("SELECT setting_value FROM sys_global_setting WHERE scope_type = 'SYSTEM' AND owner_id = '0'"));
        assertFalse(scalar("SELECT name FROM sys_global_setting LIMIT 1").isBlank());
        assertTrue(scalar("SELECT remark FROM sys_global_setting LIMIT 1").contains("true"));
        assertEquals(3, count("SELECT COUNT(*) FROM sys_menu WHERE id IN ('global_settings_menu', 'global_settings_view', 'global_settings_manage')"));
        assertEquals(3, count("SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 'admin-role' AND menu_id LIKE 'global_settings_%'"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.id = rm.role_id WHERE rm.menu_id LIKE 'global_settings_%' AND r.role_code <> 'super_admin'"));

        insert("user-row", "USER", "user-a", "true", "面板收起状态", 0);
        assertThrows(SQLException.class, () -> insert("duplicate", "USER", "user-a", "false", "面板状态", 0));
        assertThrows(SQLException.class, () -> insert("bad-owner", "USER", "0", "false", "面板状态", 0));
        assertThrows(SQLException.class, () -> insert("bad-system", "SYSTEM", "user-a", "false", "面板状态", 0));
        assertThrows(SQLException.class, () -> insert("bad-name", "USER", "user-b", "false", " ", 0));
        assertThrows(SQLException.class, () -> insert("bad-version", "USER", "user-b", "false", "面板状态", -1));

        // 数据库保存普通文本；非法格式由应用层拒绝，不能引入数据库 JSON 约束。
        assertThrows(SQLException.class, () -> execute("UPDATE sys_global_setting SET setting_value_type = 'XML'"));
        insert("plain-text", "USER", "user-c", "not json", "文本协议测试", 0);
        assertEquals("not json", scalar("SELECT setting_value FROM sys_global_setting WHERE id = 'plain-text'"));
        execute("DELETE FROM sys_global_setting WHERE id = 'user-row'");
        insert("recreated", "USER", "user-a", "false", "面板状态", 0);
        assertEquals(1, count("SELECT COUNT(*) FROM sys_global_setting WHERE scope_type = 'USER' AND owner_id = 'user-a'"));
        assertEquals(0, latest.migrate().migrationsExecuted);
    }

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(url, username, password).locations("filesystem:" + migrationDirectory)
                .placeholderReplacement(false).cleanDisabled(true).target(target).load();
    }

    private static void insert(String id, String scope, String owner, String value, String name, long version) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement statement = connection.prepareStatement("INSERT INTO sys_global_setting (id, scope_type, owner_id, setting_key, name, setting_value_type, setting_value, version) VALUES (?, ?, ?, 'ui.entity_design.field_types_collapsed', ?, 'BOOLEAN', ?, ?)")) {
            statement.setString(1, id); statement.setString(2, scope); statement.setString(3, owner);
            statement.setString(4, name); statement.setString(5, value); statement.setLong(6, version);
            statement.executeUpdate();
        }
    }

    private static String scalar(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static int count(String sql) throws SQLException { return Integer.parseInt(scalar(sql)); }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) { statement.executeUpdate(sql); }
    }
}
