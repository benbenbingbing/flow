package com.workflow.dbmigrator;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.Objects;
import java.util.UUID;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 使用隔离 MySQL 验证已有实体补齐系统字段、字段身份保留及物理表不变。 */
class EntityAuditFieldsMigrationTest {
    private static MySQLContainer<?> mysql;
    private static String url;
    private static String username;
    private static String password;
    private static final String MIGRATION_USER = "audit_migration_"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private static final String MIGRATION_PASSWORD = UUID.randomUUID().toString();

    @BeforeAll
    static void connect() throws Exception {
        url = System.getProperty("flow.entity-audit.mysql.url");
        if (url != null) {
            if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/flow_entity_audit_test_[a-z0-9_]+(?:\\?.*)?")) {
                throw new IllegalArgumentException("仅允许回环地址上的 flow_entity_audit_test_ 隔离库");
            }
            username = System.getProperty("flow.entity-audit.mysql.user", "root");
            password = System.getProperty("flow.entity-audit.mysql.password", "");
        } else {
            assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "需要 Docker 或显式隔离 MySQL");
            mysql = new MySQLContainer<>("mysql:8.4").withDatabaseName("flow_entity_audit_test_container");
            mysql.start();
            url = mysql.getJdbcUrl();
            username = "root";
            password = mysql.getPassword();
        }
        // 管理员只负责创建测试账号；夹具和迁移均使用本地 workflow_schema 的受限权限，避免 root 掩盖权限缺口。
        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE USER '" + MIGRATION_USER + "'@'%' IDENTIFIED BY '"
                    + MIGRATION_PASSWORD + "'");
            statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, REFERENCES, INDEX, ALTER ON `"
                    + connection.getCatalog() + "`.* TO '" + MIGRATION_USER + "'@'%'");
        }
    }

    @AfterAll
    static void close() throws Exception {
        try {
            if (url != null && username != null) {
                try (var connection = DriverManager.getConnection(url, username, password);
                     var statement = connection.createStatement()) {
                    statement.execute("DROP USER IF EXISTS '" + MIGRATION_USER + "'@'%'");
                }
            }
        } finally {
            if (mysql != null) mysql.stop();
        }
    }

    @BeforeEach
    void seed() throws Exception {
        flyway().clean();
        // 使用实际历史建表语句，验证列默认值、唯一约束和自动生成的字段 ID。
        String schema = resource("/db/migration/V001__business_schema.sql");
        int start = schema.indexOf("CREATE TABLE `entity_field` (");
        execute(schema.substring(start, schema.indexOf(';', start) + 1));
        execute("""
                ALTER TABLE entity_field CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
                CREATE TABLE entity_definition (id BIGINT PRIMARY KEY, storage_mode VARCHAR(20),
                  status VARCHAR(20), lifecycle_mode VARCHAR(20), deleted TINYINT);
                INSERT INTO entity_definition VALUES
                  (1,'DYNAMIC','PUBLISHED','STANDALONE',0),
                  (2,'DYNAMIC','DRAFT','WORKFLOW',0),
                  (3,'DYNAMIC','PUBLISHED','WORKFLOW',1),
                  (4,'SYSTEM','PUBLISHED','STANDALONE',0);
                INSERT INTO entity_field (id,entity_id,field_code,field_name,field_type,
                  sort_order,is_system,editable,is_required,default_value,deleted) VALUES
                  (101,1,'name','名称','STRING',1,1,1,0,NULL,0),
                  (102,1,'id','记录主键','STRING',0,0,1,1,'client-id',0),
                  (103,1,'createdAt','登记时间','STRING',14,0,1,1,'client-time',1),
                  (104,4,'id','用户 ID','STRING',1,1,0,1,NULL,0);
                CREATE TABLE biz_sample (id VARCHAR(64) PRIMARY KEY, create_time DATETIME,
                  update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64), deleted TINYINT);
                INSERT INTO biz_sample VALUES ('record-1','2026-09-20 10:00:00',
                  '2026-09-20 11:00:00','creator','updater',0);
                CREATE TABLE entity_publish_history (snapshot TEXT);
                INSERT INTO entity_publish_history VALUES ('original-snapshot');
                """);
    }

    @Test
    void registersAllAuditFieldsWithoutChangingDataOrPublishedSnapshots() throws Exception {
        Flyway migration = flyway();
        assertEquals(1, migration.migrate().migrationsExecuted);
        migration.validate();
        assertEquals(0, migration.migrate().migrationsExecuted);
        assertEquals(18, count("SELECT COUNT(*) FROM entity_field WHERE entity_id IN (1,2,3) AND field_code <> 'name'"));
        assertEquals(18, count("""
                SELECT COUNT(*) FROM entity_field WHERE entity_id IN (1,2,3) AND field_code <> 'name'
                  AND is_system=1 AND editable=0 AND is_required=0 AND is_unique=0
                  AND default_value IS NULL AND validate_rules IS NULL AND deleted=0
                """));
        assertEquals(3, count("SELECT COUNT(*) FROM entity_field WHERE field_code='createdAt' AND db_column_name='create_time'"));
        assertEquals(3, count("SELECT COUNT(*) FROM entity_field WHERE field_code='updatedAt' AND db_column_name='update_time'"));
        assertEquals(3, count("SELECT COUNT(*) FROM entity_field WHERE field_code='createdBy' AND db_column_name='create_by'"));
        assertEquals(3, count("SELECT COUNT(*) FROM entity_field WHERE field_code='updatedBy' AND db_column_name='update_by'"));
        assertEquals(1, count("SELECT COUNT(*) FROM entity_field WHERE id=102 AND field_name='记录主键'"));
        assertEquals(1, count("SELECT COUNT(*) FROM entity_field WHERE id=103 AND field_name='登记时间'"));
        assertEquals(1, count("SELECT COUNT(*) FROM entity_field WHERE id=101 AND editable=1"));
        assertEquals(1, count("SELECT COUNT(*) FROM entity_field WHERE entity_id=4 AND id=104 AND is_required=1"));
        assertEquals(6, count("SELECT COUNT(*) FROM entity_field WHERE entity_id=2 AND is_published=0"));
        assertEquals(1, count("SELECT COUNT(*) FROM biz_sample WHERE id='record-1' AND create_by='creator' AND update_by='updater' AND deleted=0"));
        assertEquals(1, count("SELECT COUNT(*) FROM entity_publish_history WHERE snapshot='original-snapshot'"));
        execute(resource("/db/migration/V097__register_entity_identity_and_audit_fields.sql"));
        assertEquals(20, count("SELECT COUNT(*) FROM entity_field"));
    }

    @Test
    void succeedsWithoutDynamicEntities() throws Exception {
        execute("DELETE FROM entity_definition WHERE storage_mode='DYNAMIC'");
        assertEquals(1, flyway().migrate().migrationsExecuted);
        assertEquals(4, count("SELECT COUNT(*) FROM entity_field"));
    }

    /** 回归启动故障：迁移账号明确不能建临时表，但必须可以执行 V097。 */
    @Test
    void migratesWithSchemaPermissionsWithoutTemporaryTablePrivilege() throws Exception {
        try (var connection = DriverManager.getConnection(url, MIGRATION_USER, MIGRATION_PASSWORD);
             var statement = connection.createStatement()) {
            SQLException denied = assertThrows(SQLException.class,
                    () -> statement.execute("CREATE TEMPORARY TABLE audit_privilege_probe (id INT)"));
            assertEquals(1044, denied.getErrorCode());
        }
        assertEquals(1, flyway().migrate().migrationsExecuted);
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history WHERE version='097' AND success=1"));
        assertEquals(20, count("SELECT COUNT(*) FROM entity_field"));
    }

    /** 从 V096 基线以受限账号执行 V097；clean 只能作用于上述专用测试库。 */
    private Flyway flyway() {
        return Flyway.configure().dataSource(url, MIGRATION_USER, MIGRATION_PASSWORD)
                .locations("classpath:db/migration").target("97")
                .baselineOnMigrate(true).baselineVersion("96").cleanDisabled(false).load();
    }

    private String resource(String path) throws Exception {
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream(path))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void execute(String sql) throws Exception {
        try (var connection = DriverManager.getConnection(url, MIGRATION_USER, MIGRATION_PASSWORD);
             var statement = connection.createStatement()) {
            for (String command : sql.split(";")) {
                if (!command.isBlank()) statement.execute(command);
            }
        }
    }

    private int count(String sql) throws Exception {
        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
