package com.workflow.dbmigrator;

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
 * 在真实 MySQL 上验证 V090/V091、V100/V101 及 V108 将偏好合并为 JSON 的升级路径。
 * 仅运行设置相关迁移，历史全量迁移重放由独立集成检查承担。
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
        url = System.getProperty("settingsTestJdbcUrl", System.getenv("SETTINGS_TEST_JDBC_URL"));
        if (url != null) {
            if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/workflow_settings_test_[a-z0-9_]+(\\?.*)?")) {
                throw new IllegalArgumentException("仅允许显式指定回环地址的 workflow_settings_test_ 测试库");
            }
            username = System.getProperty("settingsTestUsername", System.getenv().getOrDefault("SETTINGS_TEST_USERNAME", "root"));
            password = System.getProperty("settingsTestPassword", System.getenv().getOrDefault("SETTINGS_TEST_PASSWORD", ""));
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
        for (String table : new String[]{"sys_user", "sys_role", "sys_menu", "sys_role_menu", "config_import_package"}) {
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

        // 已有导入批次保留业务数据，来源验证证据只能标为未知，不能伪造已验签结论。
        execute("INSERT INTO config_import_package (id, package_no, migration_tag, file_name, checksum, status, package_data) VALUES ('old-import', 'OLD', 'OLD', 'old.wfpack', 'old-hash', 'UPLOADED', X'00')");
        try (var input = GlobalSettingsMigrationTest.class.getResourceAsStream("/db/migration/V091__migration_signing_global_setting.sql")) {
            assertNotNull(input);
            Files.copy(input, migrationDirectory.resolve("V091__migration_signing_global_setting.sql"));
        }
        Flyway signing = flyway("91");
        signing.migrate();
        signing.validate();
        String keyQuery = "SELECT setting_value FROM sys_global_setting WHERE scope_type = 'SYSTEM' AND owner_id = '0' AND setting_key = 'config.migration.signing_key'";
        String initializedKey = scalar(keyQuery);
        assertTrue(initializedKey.matches("\"[0-9a-f]{64}\""));
        assertEquals("UNKNOWN", scalar("SELECT signature_status FROM config_import_package WHERE id = 'old-import'"));
        execute("UPDATE config_import_package SET signature_status = 'MISMATCH_CONFIRMED', signature_confirmed_by = 'operator', signature_confirmed_at = CURRENT_TIMESTAMP(6) WHERE id = 'old-import'");
        assertEquals("operator", scalar("SELECT signature_confirmed_by FROM config_import_package WHERE id = 'old-import'"));
        assertEquals(0, signing.migrate().migrationsExecuted);
        assertEquals(initializedKey, scalar(keyQuery), "重复启动不能轮换已初始化的密钥");

        int existingSettings = count("SELECT COUNT(*) FROM sys_global_setting");
        String sidebarSql;
        try (var input = GlobalSettingsMigrationTest.class.getResourceAsStream("/db/migration/V100__sidebar_collapsed_setting.sql")) {
            assertNotNull(input);
            sidebarSql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Files.writeString(migrationDirectory.resolve("V100__sidebar_collapsed_setting.sql"), sidebarSql);
        }
        Flyway sidebar = flyway("100");
        assertEquals(1, sidebar.migrate().migrationsExecuted);
        sidebar.validate();
        String sidebarQuery = "SELECT setting_value FROM sys_global_setting WHERE scope_type = 'SYSTEM' AND owner_id = '0' AND setting_key = 'ui.layout.sidebar_collapsed'";
        assertEquals("false", scalar(sidebarQuery));
        assertEquals("BOOLEAN", scalar("SELECT setting_value_type FROM sys_global_setting WHERE setting_key = 'ui.layout.sidebar_collapsed'"));
        assertEquals(existingSettings + 1, count("SELECT COUNT(*) FROM sys_global_setting"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_global_setting WHERE scope_type = 'USER' AND setting_key = 'ui.layout.sidebar_collapsed'"));
        // 重复初始化不得覆盖管理员已调整的默认值，也不能改变既有设置或用户数据。
        execute("UPDATE sys_global_setting SET setting_value = 'true' WHERE setting_key = 'ui.layout.sidebar_collapsed'");
        execute(sidebarSql);
        assertEquals("true", scalar(sidebarQuery));
        assertEquals(0, sidebar.migrate().migrationsExecuted);
        assertEquals(userCount, count("SELECT COUNT(*) FROM sys_user"));
        assertEquals(initializedKey, scalar(keyQuery));
        try (var input = GlobalSettingsMigrationTest.class.getResourceAsStream("/db/migration/V101__workspace_tabs_setting.sql")) {
            assertNotNull(input);
            Files.copy(input, migrationDirectory.resolve("V101__workspace_tabs_setting.sql"));
        }
        Flyway tabs = flyway("101");
        assertEquals(1, tabs.migrate().migrationsExecuted);
        tabs.validate();
        String tabsQuery = "SELECT setting_value FROM sys_global_setting WHERE scope_type = 'SYSTEM' AND owner_id = '0' AND setting_key = 'ui.layout.tabs_enabled'";
        assertEquals("false", scalar(tabsQuery));
        assertEquals("BOOLEAN", scalar("SELECT setting_value_type FROM sys_global_setting WHERE setting_key = 'ui.layout.tabs_enabled'"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_global_setting WHERE scope_type = 'USER' AND setting_key = 'ui.layout.tabs_enabled'"));
        assertEquals(0, tabs.migrate().migrationsExecuted);
        assertEquals("true", scalar(sidebarQuery), "新增标签模式不能重置其他系统偏好");
        verifyMergedPreferences(tabs, initializedKey, keyQuery);
    }

    /** 验证非法旧值不会部分写入、稀疏继承、显式 false、已有新值优先和重复执行。 */
    private void verifyMergedPreferences(Flyway previous, String signingKey, String keyQuery) throws Exception {
        var migration = new db.migration.V108__merge_user_interface_preferences();
        int before = count("SELECT COUNT(*) FROM sys_global_setting");
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            var context = new org.flywaydb.core.api.migration.Context() {
                public org.flywaydb.core.api.configuration.Configuration getConfiguration() { return previous.getConfiguration(); }
                public Connection getConnection() { return connection; }
            };
            assertThrows(SQLException.class, () -> migration.migrate(context));
            assertTrue(connection.getAutoCommit());
        }
        assertEquals(before, count("SELECT COUNT(*) FROM sys_global_setting"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_global_setting WHERE setting_key = 'ui.user_preferences'"));
        // 修正本测试刻意注入的非法值后，迁移应保留 false，不把它当作未配置。
        execute("UPDATE sys_global_setting SET setting_value='false' WHERE id='plain-text'");
        execute("INSERT INTO sys_global_setting (id, scope_type, owner_id, setting_key, name, setting_value_type, setting_value) VALUES "
                + "('old-sidebar-b','USER','user-b','ui.layout.sidebar_collapsed','菜单偏好','BOOLEAN','false'),"
                + "('old-tabs-b','USER','user-b','ui.layout.tabs_enabled','标签偏好','BOOLEAN','true'),"
                + "('new-a','USER','user-a','ui.user_preferences','用户界面偏好','JSON','{\"fieldTypesCollapsed\":true}')");
        Flyway merged = Flyway.configure().dataSource(url, username, password).locations("filesystem:" + migrationDirectory)
                .javaMigrations(migration).placeholderReplacement(false).cleanDisabled(true).target("108").load();
        assertEquals(1, merged.migrate().migrationsExecuted);
        merged.validate();
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        String base = "SELECT setting_value FROM sys_global_setting WHERE setting_key='ui.user_preferences' AND owner_id=";
        var system = json.readTree(scalar(base + "'0'"));
        assertEquals(3, system.size());
        assertTrue(system.path("sidebarCollapsed").booleanValue());
        assertFalse(system.path("tabsEnabled").booleanValue());
        var userB = json.readTree(scalar(base + "'user-b'"));
        assertEquals(2, userB.size());
        assertFalse(userB.path("sidebarCollapsed").booleanValue());
        assertTrue(userB.path("tabsEnabled").booleanValue());
        assertFalse(userB.has("fieldTypesCollapsed"), "未配置字段必须继续继承，不复制系统默认值");
        assertTrue(json.readTree(scalar(base + "'user-a'")).path("fieldTypesCollapsed").booleanValue());
        assertFalse(json.readTree(scalar(base + "'user-c'")).path("fieldTypesCollapsed").booleanValue());
        assertEquals(0, count("SELECT COUNT(*) FROM sys_global_setting WHERE setting_key IN ('ui.entity_design.field_types_collapsed','ui.layout.sidebar_collapsed','ui.layout.tabs_enabled')"));
        assertEquals(4, count("SELECT COUNT(*) FROM sys_global_setting WHERE setting_key='ui.user_preferences' AND setting_value_type='JSON'"));
        assertEquals(signingKey, scalar(keyQuery));
        assertEquals(0, merged.migrate().migrationsExecuted);
        assertEquals("1", scalar("SELECT version FROM sys_global_setting WHERE id='new-a'"));
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
