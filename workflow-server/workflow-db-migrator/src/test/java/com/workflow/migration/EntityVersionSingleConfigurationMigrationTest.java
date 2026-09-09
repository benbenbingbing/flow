package com.workflow.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 V082 回填唯一当前配置，并保留滚动发布期间的旧结构。 */
class EntityVersionSingleConfigurationMigrationTest {

    private static MySQLContainer<?> container;
    private static String url;
    private static String user;
    private static String password;

    @BeforeAll
    static void connect() {
        String externalUrl = System.getProperty("flow.version.mysql.url");
        if (externalUrl != null) {
            if (!externalUrl.matches(
                    "jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):\\d+/"
                            + "flow_entity_version_v082_test"
                            + "(?:_[a-zA-Z0-9]+)?(?:\\?.*)?")) {
                throw new IllegalArgumentException(
                        "外部迁移测试只允许使用专用 flow_entity_version_v082_test 数据库");
            }
            url = externalUrl;
            user = System.getProperty("flow.version.mysql.user", "root");
            password = System.getProperty(
                    "flow.version.mysql.password", "");
            return;
        }
        Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "需要 Docker 运行 MySQL 迁移验证");
        container = new MySQLContainer<>("mysql:8.4")
                .withDatabaseName("flow_entity_version_v082_test");
        container.start();
        url = container.getJdbcUrl();
        user = container.getUsername();
        password = container.getPassword();
    }

    @AfterAll
    static void close() {
        if (container != null) {
            container.stop();
        }
    }

    @BeforeEach
    void createPreviousVersionSchema() throws Exception {
        Flyway migration = flyway();
        migration.clean();
        // 只构造 V082 实际依赖的 V081 表形态，避免无关历史迁移影响本迁移验证。
        execute("""
                CREATE TABLE entity_version_config (
                    id varchar(64) NOT NULL,
                    entity_id varchar(64) NOT NULL,
                    entity_code varchar(100) NOT NULL,
                    enabled tinyint NOT NULL DEFAULT 0,
                    contract_version int NOT NULL DEFAULT 1,
                    draft_document longtext,
                    migration_state varchar(30) NOT NULL DEFAULT 'NATIVE',
                    active_release_id varchar(64),
                    revision int NOT NULL DEFAULT 1,
                    status varchar(20) NOT NULL DEFAULT 'DRAFT',
                    create_by varchar(64),
                    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_by varchar(64),
                    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted tinyint NOT NULL DEFAULT 0,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_entity_version_config_code
                        (entity_code,deleted),
                    KEY idx_entity_version_config_release
                        (active_release_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  COLLATE=utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE entity_version_config_release (
                    id varchar(64) NOT NULL,
                    config_id varchar(64) NOT NULL,
                    version int NOT NULL,
                    contract_version int NOT NULL DEFAULT 1,
                    config_document longtext NOT NULL,
                    scope_hash varchar(64),
                    published_by varchar(64),
                    published_by_name varchar(100),
                    publish_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_entity_version_config_release
                        (config_id,version)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  COLLATE=utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE entity_record_version (
                    id varchar(64) NOT NULL,
                    entity_code varchar(100) NOT NULL,
                    record_id varchar(64) NOT NULL,
                    version_no int NOT NULL,
                    scenario_code varchar(100) NOT NULL,
                    scenario_name varchar(200) NOT NULL,
                    operation_type varchar(30) NOT NULL,
                    source_type varchar(30) NOT NULL,
                    business_intent_code varchar(100) NOT NULL,
                    business_intent_name varchar(200) NOT NULL,
                    idempotency_key varchar(200) NOT NULL,
                    snapshot_hash varchar(64) NOT NULL,
                    snapshot_document longtext NOT NULL,
                    config_release_id varchar(64),
                    config_release_version int,
                    PRIMARY KEY (id),
                    KEY idx_entity_record_version_release
                        (config_release_id),
                    CONSTRAINT fk_entity_record_version_config_release
                        FOREIGN KEY (config_release_id)
                        REFERENCES entity_version_config_release (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  COLLATE=utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE sys_menu (
                    id varchar(64) NOT NULL,
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
        execute("""
                CREATE TABLE sys_role_menu (
                    id varchar(64) NOT NULL,
                    role_id varchar(64) NOT NULL,
                    menu_id varchar(64) NOT NULL,
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
        execute("""
                INSERT INTO sys_menu (id)
                VALUES ('entity_version_config_publish_001')
                """);
    }

    @Test
    void promotesActiveConfigurationAndKeepsCompatibilitySchema()
            throws Exception {
        String draft = """
                {"schemaVersion":2,"enabled":false,"triggers":[]}
                """.trim();
        String published = """
                {"schemaVersion":2,"enabled":true,"status":"PUBLISHED","migrationState":"MIGRATED","activeReleaseId":"release-active","activeReleaseVersion":3,"triggers":[{"triggerType":"MANUAL"}]}
                """.trim();
        insertConfig(
                "config-active", "asset", true,
                "release-active", 7, draft);
        insertConfig(
                "config-v1", "legacy_asset", true,
                "release-v1", 4, draft);
        execute("""
                INSERT INTO entity_version_config_release
                    (id,config_id,version,contract_version,config_document)
                VALUES
                    ('release-active','config-active',3,2,?),
                    ('release-v1','config-v1',2,1,
                     '{"enabled":true,"scenarios":[]}')
                """, published);
        execute("""
                INSERT INTO entity_record_version
                    (id,entity_code,record_id,version_no,scenario_code,
                     scenario_name,operation_type,source_type,
                     business_intent_code,business_intent_name,
                     idempotency_key,snapshot_hash,snapshot_document,
                     config_release_id,config_release_version)
                VALUES
                    ('version-1','asset','record-1',1,'UPDATED','更新',
                     'UPDATE','ENTITY_API','UPDATE','更新','request-1',
                     'hash-1','{}','release-active',3)
                """);
        execute("""
                INSERT INTO sys_role_menu (id,role_id,menu_id)
                VALUES ('publish-grant','role-1','entity_version_config_publish_001')
                """);

        Flyway current = flyway();
        current.migrate();
        current.validate();

        assertEquals(
                "082",
                current.info().current().getVersion().getVersion());
        assertEquals("2", scalar("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                    config_document, '$.schemaVersion'))
                FROM entity_version_config
                WHERE id='config-active'
                """));
        assertEquals("MANUAL", scalar("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                    config_document, '$.triggers[0].triggerType'))
                FROM entity_version_config
                WHERE id='config-active'
                """));
        assertEquals("0", scalar("""
                SELECT JSON_CONTAINS_PATH(
                    config_document, 'one',
                    '$.status', '$.migrationState',
                    '$.activeReleaseId', '$.activeReleaseVersion')
                FROM entity_version_config
                WHERE id='config-active'
                """));
        assertEquals("1", scalar("""
                SELECT enabled FROM entity_version_config
                WHERE id='config-active'
                """));
        assertEquals("8", scalar("""
                SELECT revision FROM entity_version_config
                WHERE id='config-active'
                """));
        assertEquals("1", scalar("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                    config_document, '$.schemaVersion'))
                FROM entity_version_config
                WHERE id='config-v1'
                """));
        assertEquals("{}", scalar("""
                SELECT snapshot_document FROM entity_record_version
                WHERE id='version-1'
                """));

        assertTrue(columnExists(
                "entity_version_config", "config_document"));
        assertEquals("YES", scalar("""
                SELECT IS_NULLABLE
                FROM information_schema.columns
                WHERE table_schema=DATABASE()
                  AND table_name='entity_version_config'
                  AND column_name='config_document'
                """));
        assertTrue(columnExists(
                "entity_version_config", "draft_document"));
        assertTrue(columnExists(
                "entity_version_config", "active_release_id"));
        assertTrue(columnExists(
                "entity_version_config", "status"));
        assertTrue(columnExists(
                "entity_record_version", "config_release_id"));
        assertTrue(tableExists("entity_version_config_release"));
        assertEquals("0", scalar("""
                SELECT COUNT(*)
                FROM information_schema.triggers
                WHERE trigger_schema=DATABASE()
                  AND trigger_name='trg_entity_version_release_sync_current'
                """));
        assertEquals("1", scalar("""
                SELECT COUNT(*) FROM sys_menu
                WHERE id='entity_version_config_publish_001'
                """));
        assertEquals("1", scalar("""
                SELECT COUNT(*) FROM sys_role_menu
                WHERE menu_id='entity_version_config_publish_001'
                """));
    }

    @Test
    void rerunsSafelyAfterConfigColumnWasAlreadyAdded() throws Exception {
        execute("""
                ALTER TABLE entity_version_config
                ADD COLUMN config_document longtext NULL
                AFTER draft_document
                """);
        String draft = """
                {"schemaVersion":2,"enabled":true,"triggers":[]}
                """.trim();
        String published = """
                {"schemaVersion":2,"enabled":false,"triggers":[{"triggerType":"MANUAL"}]}
                """.trim();
        insertConfig(
                "config-partial", "partial_asset", true,
                "release-partial", 8, draft);
        execute("""
                INSERT INTO entity_version_config_release
                    (id,config_id,version,contract_version,config_document)
                VALUES ('release-partial','config-partial',2,2,?)
                """, published);
        execute("""
                UPDATE entity_version_config
                SET config_document=?,
                    update_time='2020-01-02 03:04:05'
                WHERE id='config-partial'
                """, draft);

        Flyway current = flyway();
        current.migrate();
        current.validate();

        assertEquals("082", current.info().current()
                .getVersion().getVersion());
        assertEquals("1", scalar("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema=DATABASE()
                  AND table_name='entity_version_config'
                  AND column_name='config_document'
                """));
        assertEquals("8", scalar("""
                SELECT revision FROM entity_version_config
                WHERE id='config-partial'
                """));
        assertEquals("2020-01-02 03:04:05", scalar("""
                SELECT DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s')
                FROM entity_version_config
                WHERE id='config-partial'
                """));
        assertEquals("false", scalar("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                    config_document, '$.enabled'))
                FROM entity_version_config
                WHERE id='config-partial'
                """));
        assertEquals("MANUAL", scalar("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                    config_document, '$.triggers[0].triggerType'))
                FROM entity_version_config
                WHERE id='config-partial'
                """));
    }

    @Test
    void keepsUnpublishedDocumentButDoesNotEnableIt() throws Exception {
        String draft = """
                {"schemaVersion":2,"enabled":true,"migrationState":"REVIEW_REQUIRED","triggers":[{"triggerCode":"MANUAL_CHECKPOINT"}]}
                """.trim();
        insertConfig(
                "config-draft", "draft_only", true,
                null, 2, draft);

        flyway().migrate();

        assertEquals("MANUAL_CHECKPOINT", scalar("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                    config_document, '$.triggers[0].triggerCode'))
                FROM entity_version_config
                WHERE id='config-draft'
                """));
        assertEquals("0", scalar("""
                SELECT enabled FROM entity_version_config
                WHERE id='config-draft'
                """));
        assertEquals("false", scalar("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                    config_document, '$.enabled'))
                FROM entity_version_config
                WHERE id='config-draft'
                """));
        assertEquals("0", scalar("""
                SELECT JSON_CONTAINS_PATH(
                    config_document, 'one', '$.migrationState')
                FROM entity_version_config
                WHERE id='config-draft'
                """));
    }

    private void insertConfig(
            String id,
            String entityCode,
            boolean enabled,
            String activeReleaseId,
            int revision,
            String document) throws Exception {
        execute("""
                INSERT INTO entity_version_config
                    (id,entity_id,entity_code,enabled,contract_version,
                     draft_document,migration_state,active_release_id,
                     revision,status)
                VALUES (?,?,?, ?,2,?,'NATIVE',?,?,'DRAFT')
                """,
                id, "entity-" + entityCode, entityCode,
                enabled, document, activeReleaseId, revision);
    }

    private void execute(String sql, Object... values) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }

    private String scalar(String sql) throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private boolean tableExists(String tableName) throws Exception {
        try (Connection connection = connection();
             ResultSet tables = connection.getMetaData().getTables(
                     connection.getCatalog(), null, tableName, null)) {
            return tables.next();
        }
    }

    private boolean columnExists(
            String tableName,
            String columnName) throws Exception {
        try (Connection connection = connection();
             ResultSet columns = connection.getMetaData().getColumns(
                     connection.getCatalog(), null,
                     tableName, columnName)) {
            return columns.next();
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(url, user, password);
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(url, user, password)
                .cleanDisabled(false)
                .placeholderReplacement(false)
                .baselineOnMigrate(true)
                .baselineVersion("081")
                .locations("classpath:db/migration")
                .load();
    }
}
