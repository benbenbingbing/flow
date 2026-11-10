package com.workflow.dbmigrator;

import db.migration.V129__allow_out_of_order_organization_provisioning;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证实际 V129 Java 迁移的历史父级回填、空库结构及重复执行，不连接部署数据库。 */
class OutOfOrderOrganizationMigrationTest {
    @TempDir
    Path migrations;
    @Test
    void upgradesHistoricalBindingsAndPreservesRootAndUserSemantics() throws Exception {
        try (Connection connection = database()) {
            createOldSchema(connection);
            try (var sql = connection.createStatement()) {
                sql.execute("INSERT INTO sys_organization VALUES ('root', '0'), ('child', 'root')");
                sql.execute("INSERT INTO sys_identity_source_binding VALUES "
                        + "('root-key', 'erp', 'ORGANIZATION', 'Root', 'root'),"
                        + "('child-key', 'erp', 'ORGANIZATION', 'Child', 'child'),"
                        + "('user-key', 'erp', 'USER', 'Employee', 'user')");
            }
            Context context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            var migration = new V129__allow_out_of_order_organization_provisioning();
            migration.migrate(context);
            migration.migrate(context);
            try (var sql = connection.createStatement();
                 var rows = sql.executeQuery("SELECT parent_external_id, parent_source_key FROM sys_identity_source_binding WHERE source_key = 'child-key'")) {
                assertTrue(rows.next());
                assertEquals("Root", rows.getString(1));
                assertEquals("root-key", rows.getString(2));
            }
            try (var sql = connection.createStatement();
                 var rows = sql.executeQuery("SELECT COUNT(*) FROM sys_identity_source_binding WHERE parent_source_key IS NULL")) {
                assertTrue(rows.next());
                assertEquals(2, rows.getInt(1));
            }
            try (var sql = connection.createStatement();
                 var rows = sql.executeQuery("SELECT COUNT(*) FROM sys_organization WHERE hierarchy_ready = 1")) {
                assertTrue(rows.next());
                assertEquals(2, rows.getInt(1));
            }
        }
    }

    @Test
    void flywayAppliesEmptySchemaUpgradeOnceAndValidatesHistory() throws Exception {
        try (Connection connection = database()) {
            String url = connection.getMetaData().getURL();
            // 用测试目录内的历史结构夹具建立旧版本，避免对非空 schema 使用 baseline。
            Files.writeString(migrations.resolve("V128__organization_fixture.sql"), """
                    CREATE TABLE sys_organization (id VARCHAR(64) PRIMARY KEY, parent_id VARCHAR(64));
                    CREATE TABLE sys_identity_source_binding (source_key VARCHAR(64) PRIMARY KEY,
                        source_code VARCHAR(64), object_type VARCHAR(20), external_id VARCHAR(255), local_id VARCHAR(64));
                    """);
            var flyway = Flyway.configure().dataSource(url, "sa", "")
                    .locations("filesystem:" + migrations)
                    .javaMigrations(new V129__allow_out_of_order_organization_provisioning())
                    .load();
            assertEquals(2, flyway.migrate().migrationsExecuted);
            flyway.validate();
            assertEquals(0, flyway.migrate().migrationsExecuted);
        }
    }

    /** 历史结构仅含此次迁移读取的字段，来源根节点及用户映射没有组织父级语义。 */
    private void createOldSchema(Connection connection) throws Exception {
        try (var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE sys_organization (id VARCHAR(64) PRIMARY KEY, parent_id VARCHAR(64))");
            sql.execute("CREATE TABLE sys_identity_source_binding (source_key VARCHAR(64) PRIMARY KEY, source_code VARCHAR(64), object_type VARCHAR(20), external_id VARCHAR(255), local_id VARCHAR(64))");
        }
    }

    private Connection database() throws Exception {
        return DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }
}
