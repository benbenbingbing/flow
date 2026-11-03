package com.workflow.dbmigrator;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 使用隔离 H2 MySQL 模式验证历史迁移可校验、升级只清理示例表及新库最终结构。 */
class ProjectIdentityExtensionRemovalMigrationTest {
    @TempDir
    Path migrations;

    @Test
    void upgradesAppliedV127WithoutChangingHistoryOrOtherBusinessTables() throws Exception {
        copyMigrations();
        String url = database();
        assertEquals(1, flyway(url, "127").migrate().migrationsExecuted);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("INSERT INTO biz_project_user_profile (user_id, technical_level) VALUES ('user-1', 'T2')");
            statement.execute("INSERT INTO biz_project_directory_user (user_id, employee_number) VALUES ('user-1', 'EMP-1')");
            // 即使已有环境手工清理过其中一张表，V128 仍应能完成剩余清理。
            statement.execute("DROP TABLE biz_project_org_profile");
            statement.execute("CREATE TABLE sys_identity_source_binding (local_id VARCHAR(64))");
            statement.execute("INSERT INTO sys_identity_source_binding VALUES ('user-1')");
            statement.execute("CREATE TABLE biz_custom_user_profile (user_id VARCHAR(64))");
            statement.execute("INSERT INTO biz_custom_user_profile VALUES ('user-1')");

            Flyway upgrade = flyway(url, "128");
            // migrate 会先校验已执行的 V127，再应用尚未执行的 V128。
            assertEquals(1, upgrade.migrate().migrationsExecuted);
            upgrade.validate();
            assertEquals(0, upgrade.migrate().migrationsExecuted);
            assertNoExampleTables(connection);
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM sys_identity_source_binding"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM biz_custom_user_profile"));
            assertEquals(2, upgrade.info().applied().length);
        }
    }

    @Test
    void freshDatabaseReplaysBothVersionsAndLeavesNoExampleTables() throws Exception {
        copyMigrations();
        String url = database();
        Flyway fresh = flyway(url, "128");
        assertEquals(2, fresh.migrate().migrationsExecuted);
        fresh.validate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertNoExampleTables(connection);
        }
    }

    /** 复制实际 SQL 到隔离位置，仅执行本次两个版本，不触碰开发者已有数据库。 */
    private void copyMigrations() throws Exception {
        for (String name : List.of("V127__project_identity_extension_examples.sql",
                "V128__remove_project_identity_extension_examples.sql")) {
            try (var sql = getClass().getResourceAsStream("/db/migration/" + name)) {
                assertNotNull(sql);
                Files.copy(sql, migrations.resolve(name));
            }
        }
    }

    private Flyway flyway(String url, String target) {
        return Flyway.configure().dataSource(url, "sa", "")
                .locations("filesystem:" + migrations).target(target).load();
    }

    private String database() {
        return "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    }

    private void assertNoExampleTables(Connection connection) throws Exception {
        assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE LOWER(table_name) IN ('biz_project_user_profile', 'biz_project_directory_user', 'biz_project_org_profile')"));
    }

    private int count(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
