package com.workflow.dbmigrator;

import db.migration.V096__remove_entity_title_and_data_no;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 使用隔离 MySQL 验证真实列删除、字段清理和动态实体范围边界。 */
class EntityIdentityColumnsRemovalMigrationTest {
    private static MySQLContainer<?> mysql;
    private static String url;
    private static String username;
    private static String password;

    @BeforeAll
    static void connect() {
        url = System.getProperty("flow.entity-identity.mysql.url");
        if (url != null) {
            if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/flow_entity_identity_test_[a-z0-9_]+(?:\\?.*)?")) {
                throw new IllegalArgumentException("仅允许显式指定回环地址上的 flow_entity_identity_test_ 隔离库");
            }
            username = System.getProperty("flow.entity-identity.mysql.user", "root");
            password = System.getProperty("flow.entity-identity.mysql.password", "");
        } else {
            assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "需要 Docker 或显式隔离 MySQL");
            mysql = new MySQLContainer<>("mysql:8.4").withDatabaseName("flow_entity_identity_test_container");
            mysql.start();
            url = mysql.getJdbcUrl();
            username = mysql.getUsername();
            password = mysql.getPassword();
        }
    }

    @AfterAll
    static void close() {
        if (mysql != null) mysql.stop();
    }

    @BeforeEach
    void seed() throws SQLException {
        flyway().clean();
        execute("""
                CREATE TABLE entity_definition (id BIGINT PRIMARY KEY, table_name VARCHAR(100),
                  storage_mode VARCHAR(20), lifecycle_mode VARCHAR(20), deleted TINYINT);
                CREATE TABLE entity_field (id BIGINT PRIMARY KEY, entity_id BIGINT,
                  field_code VARCHAR(100), db_column_name VARCHAR(100));
                CREATE TABLE entity_field_option (id VARCHAR(64), field_id VARCHAR(64));
                CREATE TABLE entity_field_file_item (id VARCHAR(64), field_id VARCHAR(64));
                CREATE TABLE entity_list_field (id VARCHAR(64), field_id VARCHAR(64));
                CREATE TABLE biz_standalone (id VARCHAR(64) PRIMARY KEY, name VARCHAR(200),
                  code VARCHAR(100), title VARCHAR(500), data_no VARCHAR(100), amount INT);
                CREATE TABLE biz_workflow LIKE biz_standalone;
                CREATE TABLE biz_deleted LIKE biz_standalone;
                CREATE TABLE biz_partial LIKE biz_standalone;
                CREATE TABLE biz_unregistered LIKE biz_standalone;
                CREATE TABLE biz_standalone_multi LIKE biz_standalone;
                CREATE TABLE sys_sample LIKE biz_standalone;
                ALTER TABLE biz_partial DROP COLUMN title;
                INSERT INTO entity_definition VALUES
                  (1,'biz_standalone','DYNAMIC','STANDALONE',0),
                  (2,'biz_workflow','DYNAMIC','WORKFLOW',0),
                  (3,'biz_deleted','DYNAMIC','WORKFLOW',1),
                  (4,'biz_partial','DYNAMIC','STANDALONE',0),
                  (5,'sys_sample','SYSTEM','STANDALONE',0),
                  (6,'biz_unpublished','DYNAMIC','STANDALONE',0),
                  (7,NULL,'DYNAMIC','STANDALONE',0);
                INSERT INTO biz_standalone VALUES ('record-1','当前名称','CODE-001','旧标题','OLD-001',42);
                INSERT INTO entity_field VALUES
                  (11,1,'dataNo',NULL),(12,1,'title','title'),(13,1,'name','name'),(14,1,'code','code'),
                  (21,2,'dataNo','data_no'),(31,3,'dataNo','data_no'),(41,4,'data_no','data_no'),
                  (51,5,'title','title'),(61,6,'dataNo',NULL),(71,7,'dataNo',NULL);
                INSERT INTO entity_field_option VALUES ('retired','11'),('keep','13'),('system','51');
                INSERT INTO entity_field_file_item SELECT * FROM entity_field_option;
                INSERT INTO entity_list_field SELECT * FROM entity_field_option;
                INSERT INTO entity_list_field VALUES ('virtual','custom-summary');
                """);
    }

    @Test
    void removesBothColumnsAndMetadataWithoutCopyingValuesOrTouchingOtherTables() throws Exception {
        Flyway migration = flyway();
        assertEquals(1, migration.migrate().migrationsExecuted);
        migration.validate();
        assertEquals(0, migration.migrate().migrationsExecuted);

        assertEquals(0, count("""
                SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
                  AND TABLE_NAME IN ('biz_standalone','biz_workflow','biz_deleted','biz_partial')
                  AND COLUMN_NAME IN ('title','data_no')
                """));
        assertEquals(6, count("""
                SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
                  AND TABLE_NAME IN ('biz_unregistered','biz_standalone_multi','sys_sample')
                  AND COLUMN_NAME IN ('title','data_no')
                """));
        assertEquals(1, count("SELECT COUNT(*) FROM biz_standalone WHERE name='当前名称' AND code='CODE-001' AND amount=42"));
        assertEquals(3, count("SELECT COUNT(*) FROM entity_field"));
        assertEquals(2, count("SELECT COUNT(*) FROM entity_field_option"));
        assertEquals(2, count("SELECT COUNT(*) FROM entity_field_file_item"));
        assertEquals(3, count("SELECT COUNT(*) FROM entity_list_field"));

        // 删除后用当前契约实际新增、修改与查询，避免仅验证 information_schema。
        execute("INSERT INTO biz_workflow (id,name,code) VALUES ('record-2','流程记录','CODE-002');"
                + "UPDATE biz_workflow SET name='更新名称' WHERE id='record-2';");
        assertEquals(1, count("SELECT COUNT(*) FROM biz_workflow WHERE name='更新名称' AND code='CODE-002'"));
        runDirectly();
        assertEquals(1, count("SELECT COUNT(*) FROM biz_workflow"));
    }

    @Test
    void rejectsInvalidDynamicTableRegistrationBeforeAnyColumnIsDropped() throws Exception {
        execute("UPDATE entity_definition SET storage_mode='DYNAMIC' WHERE id=5;");
        assertThrows(SQLException.class, this::runDirectly);
        assertEquals(2, count("""
                SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
                  AND TABLE_NAME='biz_standalone' AND COLUMN_NAME IN ('title','data_no')
                """));
        assertEquals(10, count("SELECT COUNT(*) FROM entity_field"));
    }

    @Test
    void succeedsWhenNoDynamicEntitiesHaveBeenPublished() throws Exception {
        execute("DELETE FROM entity_definition WHERE storage_mode='DYNAMIC';");
        assertEquals(1, flyway().migrate().migrationsExecuted);
        assertEquals(2, count("""
                SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
                  AND TABLE_NAME='sys_sample' AND COLUMN_NAME IN ('title','data_no')
                """));
    }

    /** 只执行 V096；测试库清理由上面的专用 URL 白名单约束。 */
    private Flyway flyway() {
        return Flyway.configure().dataSource(url, username, password)
                .locations("classpath:entity-identity-test-no-sql")
                .javaMigrations(new V096__remove_entity_title_and_data_no())
                .baselineOnMigrate(true).baselineVersion("95").cleanDisabled(false).load();
    }

    private void runDirectly() throws Exception {
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            new V096__remove_entity_title_and_data_no().migrate(new Context() {
                public Configuration getConfiguration() { return Flyway.configure(); }
                public Connection getConnection() { return connection; }
            });
        }
    }

    private void execute(String sql) throws SQLException {
        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            for (String command : sql.split(";")) {
                if (!command.isBlank()) statement.execute(command);
            }
        }
    }

    private int count(String sql) throws SQLException {
        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
