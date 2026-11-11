package com.workflow.dbmigrator.runner;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 显式传入独立空库时验证完整历史迁移链；拒绝业务库命名和非空库，不执行 clean/repair/baseline。
 * 测试库由调用方创建和清理，失败时保留现场供排查。
 */
@EnabledIfEnvironmentVariable(named = "WFPACK_FLYWAY_TEST_URL", matches = ".+")
class MySqlFreshInstallMigrationTest {
    @Test
    void migratesThroughOriginalV062PreservingCleanupSemanticsAndHistoryValidation() throws Exception {
        String url = System.getenv("WFPACK_FLYWAY_TEST_URL");
        assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/wfpack_flyway_test_[a-z0-9_]+\\?.+"),
                "只允许显式创建的本机隔离测试库");
        String username = System.getenv("WFPACK_FLYWAY_TEST_USER");
        String password = System.getenv("WFPACK_FLYWAY_TEST_PASSWORD");
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()"));
        }
        flyway(url, username, password, "61").migrate();
        try (Connection connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO sys_menu(id,parent_id,menu_name,perm) VALUES
                    ('qa_v062_root','0','复制的盘点菜单','entity:list-scope:inventory'),
                    ('qa_v062_child','qa_v062_root','盘点子项','qa:v062:child'),
                    ('qa_v062_all','qa_v062_root','保留全量权限','entity:list-scope:explicit-all'),
                    ('qa_v062_keep','0','保留普通菜单','qa:v062:keep')
                    """);
            statement.executeUpdate("""
                    INSERT INTO sys_role_menu(id,role_id,menu_id) VALUES
                    ('qa_grant_root','qa_role','qa_v062_root'),
                    ('qa_grant_child','qa_role','qa_v062_child'),
                    ('qa_grant_all','qa_role','qa_v062_all'),
                    ('qa_grant_keep','qa_role','qa_v062_keep')
                    """);
        }
        Flyway v62 = flyway(url, username, password, "62");
        assertEquals(1, v62.migrate().migrationsExecuted);
        v62.validate();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM sys_role_menu WHERE id IN ('qa_grant_root','qa_grant_child')"));
            assertEquals(2, scalar(connection, "SELECT COUNT(*) FROM sys_role_menu WHERE id IN ('qa_grant_all','qa_grant_keep')"));
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM sys_menu WHERE id IN ('qa_v062_root','qa_v062_child')"));
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM sys_menu WHERE id='qa_v062_all' AND parent_id='0' AND visible='1'"));
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='entity_list_scope_inventory'"));
        }
        Flyway latest = flyway(url, username, password, "latest");
        latest.migrate();
        latest.validate();
        assertEquals(0, latest.info().pending().length);
        assertEquals(0, latest.migrate().migrationsExecuted);
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0"));
        }
    }

    private static Flyway flyway(String url, String username, String password, String target) {
        return Flyway.configure().dataSource(url, username, password).locations("classpath:db/migration")
                .placeholderReplacement(false).cleanDisabled(true).target(target)
                .callbacks(new MySqlV062CompatibilityCallback()).load();
    }

    private static int scalar(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }
}
