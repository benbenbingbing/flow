package com.workflow.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 V083 从全新库和 V082 存量库完整退役实体变更策略。 */
class EntityMutationPolicyRemovalMigrationTest {

    private static MySQLContainer<?> container;
    private static String url;
    private static String user;
    private static String password;
    private static boolean externalDatabase;

    @BeforeAll
    static void connect() {
        String externalUrl = System.getProperty("flow.mutation.mysql.url");
        if (externalUrl != null) {
            if (!externalUrl.matches(
                    "jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):\\d+/"
                            + "flow_entity_mutation_v083_test"
                            + "(?:_[a-zA-Z0-9]+)?(?:\\?.*)?")) {
                throw new IllegalArgumentException(
                        "外部迁移测试只允许使用专用 flow_entity_mutation_v083_test 数据库");
            }
            url = externalUrl;
            user = System.getProperty("flow.mutation.mysql.user", "root");
            password = System.getProperty(
                    "flow.mutation.mysql.password", "");
            externalDatabase = true;
            return;
        }
        Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "需要 Docker 或专用本机 MySQL 运行 V083 迁移验证");
        container = new MySQLContainer<>("mysql:8.4")
                .withDatabaseName("flow_entity_mutation_v083_test");
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
    void clean() {
        flyway(null).clean();
    }

    @Test
    void freshDatabaseContainsOnlyRetainedVersionAndWriteInfrastructure()
            throws Exception {
        Assumptions.assumeFalse(
                externalDatabase,
                "全量历史迁移链由项目约定的 MySQL 8.4 Testcontainers 验证");
        Flyway current = flyway("083");
        current.migrate();
        current.validate();

        assertEquals("083", current.info().current()
                .getVersion().getVersion());
        assertRetiredSchemaRemoved();
        assertRetainedSchemaPresent();
        assertEquals(0, count("""
                SELECT COUNT(*) FROM sys_menu
                WHERE id IN (
                    'entity_mutation_policy_management_001',
                    'entity_mutation_policy_list_001',
                    'entity_mutation_policy_update_001',
                    'entity_mutation_policy_publish_001')
                   OR perm IN (
                    'entity:mutation:config:list',
                    'entity:mutation:config:update',
                    'entity:mutation:config:publish')
                   OR path='/system/entity-mutation-policies'
                   OR component='system/EntityMutationPolicyManagement'
                """));
    }

    @Test
    void upgradeRemovesAllTargetStatesClonedMenusAndPolicyJsonKeys()
            throws Exception {
        if (externalDatabase) {
            createRepresentativeV082Schema();
        } else {
            flyway("082").migrate();
        }
        seedVersionDocuments();
        seedMutationPolicy();
        seedTargetStates();
        seedClonedAndOrphanMenuGrants();

        assertEquals(4, count(
                "SELECT COUNT(*) FROM entity_change_target_instance"));

        Flyway current = externalDatabase
                ? flywayFromRepresentativeV082()
                : flyway(null);
        assertEquals(1, current.migrate().migrationsExecuted);
        current.validate();

        assertEquals("083", current.info().current()
                .getVersion().getVersion());
        assertRetiredSchemaRemoved();
        assertRetainedSchemaPresent();
        assertSanitizedVersionDocuments();
        assertEquals(0, count("""
                SELECT COUNT(*) FROM sys_menu
                WHERE id IN (
                    'entity_mutation_policy_management_001',
                    'entity_mutation_policy_list_001',
                    'entity_mutation_policy_update_001',
                    'entity_mutation_policy_publish_001',
                    'mutation_policy_copy_root',
                    'mutation_policy_copy_child')
                   OR perm IN (
                    'entity:mutation:config:list',
                    'entity:mutation:config:update',
                    'entity:mutation:config:publish')
                   OR path='/system/entity-mutation-policies'
                   OR component='system/EntityMutationPolicyManagement'
                """));
        assertEquals(0, count("""
                SELECT COUNT(*) FROM sys_role_menu
                WHERE menu_id IN (
                    'entity_mutation_policy_management_001',
                    'entity_mutation_policy_list_001',
                    'entity_mutation_policy_update_001',
                    'entity_mutation_policy_publish_001',
                    'mutation_policy_copy_root',
                    'mutation_policy_copy_child')
                """));
    }

    /**
     * 为没有 Docker 的开发机建立 V083 实际依赖的最小 V082 结构。
     *
     * <p>这条路径只跳过与本功能无关的历史迁移，不模拟 V083 本身：Flyway
     * 会把该专用测试库基线到 V082，再真实执行 classpath 中的 V083 SQL。</p>
     */
    private void createRepresentativeV082Schema() throws Exception {
        execute("""
                CREATE TABLE entity_version_config (
                  id varchar(64) PRIMARY KEY,
                  entity_id varchar(64) NOT NULL,
                  entity_code varchar(100) NOT NULL,
                  enabled tinyint NOT NULL DEFAULT 0,
                  contract_version int NOT NULL DEFAULT 2,
                  draft_document longtext,
                  config_document longtext,
                  migration_state varchar(20),
                  active_release_id varchar(64),
                  revision int NOT NULL DEFAULT 1,
                  status varchar(20) NOT NULL DEFAULT 'DRAFT',
                  deleted tinyint NOT NULL DEFAULT 0
                ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE entity_version_config_release (
                  id varchar(64) PRIMARY KEY,
                  config_id varchar(64) NOT NULL,
                  version int NOT NULL,
                  contract_version int NOT NULL DEFAULT 2,
                  config_document longtext NOT NULL
                ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE entity_mutation_policy_config (
                  id varchar(64) PRIMARY KEY,
                  entity_id varchar(64) NOT NULL,
                  entity_code varchar(100) NOT NULL,
                  enabled tinyint NOT NULL DEFAULT 0,
                  draft_document longtext,
                  active_release_id varchar(64),
                  revision int NOT NULL DEFAULT 1,
                  status varchar(20) NOT NULL DEFAULT 'DRAFT',
                  migration_state varchar(20),
                  deleted tinyint NOT NULL DEFAULT 0
                ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE entity_mutation_policy_release (
                  id varchar(64) PRIMARY KEY,
                  config_id varchar(64) NOT NULL,
                  version int NOT NULL,
                  config_document longtext NOT NULL
                ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE entity_change_target_instance (
                  id varchar(64) PRIMARY KEY,
                  binding_code varchar(100) NOT NULL,
                  source_entity_code varchar(100) NOT NULL,
                  source_record_id varchar(64) NOT NULL,
                  process_instance_id varchar(64),
                  target_entity_code varchar(100) NOT NULL,
                  target_record_id varchar(64) NOT NULL,
                  target_document longtext,
                  status varchar(30) NOT NULL
                ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE sys_menu (
                  id varchar(64) PRIMARY KEY,
                  parent_id varchar(64) NOT NULL DEFAULT '0',
                  menu_name varchar(100) NOT NULL,
                  menu_type char(1) NOT NULL DEFAULT 'C',
                  path varchar(255) NOT NULL DEFAULT '',
                  component varchar(255) NOT NULL DEFAULT '',
                  perm varchar(255),
                  status char(1) NOT NULL DEFAULT '0',
                  visible char(1) NOT NULL DEFAULT '0',
                  deleted tinyint NOT NULL DEFAULT 0
                ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE sys_role_menu (
                  id varchar(64) PRIMARY KEY,
                  role_id varchar(64) NOT NULL,
                  menu_id varchar(64) NOT NULL,
                  create_time datetime NOT NULL
                ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                """);
        execute("CREATE TABLE entity_mutation_receipt (id varchar(64) PRIMARY KEY)");
        execute("CREATE TABLE entity_record_version (id varchar(64) PRIMARY KEY)");
    }

    /** 预置 V082 可能保留或重新投影出的策略专属 JSON 键。 */
    private void seedVersionDocuments() throws Exception {
        execute("""
                INSERT INTO entity_version_config (
                  id,entity_id,entity_code,enabled,contract_version,
                  draft_document,config_document,migration_state,
                  active_release_id,revision,status,deleted
                ) VALUES (
                  'version-config-with-policy-json','entity-version-asset',
                  'version_asset',1,2,
                  '{"schemaVersion":2,"scenarios":[{"scenarioCode":"DRAFT_RULE"}],"triggers":[{"triggerCode":"DRAFT_TRIGGER"}],"steps":[{"stepName":"retire"}],"targetBindings":[{"bindingCode":"retire"}]}',
                  '{"schemaVersion":2,"scenarios":[{"scenarioCode":"CURRENT_RULE"}],"triggers":[{"triggerCode":"CURRENT_TRIGGER"}],"steps":[{"stepName":"retire"}],"targetBindings":[{"bindingCode":"retire"}]}',
                  'NATIVE','version-release-with-policy-json',3,
                  'PUBLISHED',0)
                """);
        execute("""
                INSERT INTO entity_version_config_release (
                  id,config_id,version,contract_version,config_document
                ) VALUES (
                  'version-release-with-policy-json',
                  'version-config-with-policy-json',1,2,
                  '{"schemaVersion":2,"scenarios":[{"scenarioCode":"RELEASE_RULE"}],"triggers":[{"triggerCode":"RELEASE_TRIGGER"}],"steps":[{"stepName":"retire"}],"targetBindings":[{"bindingCode":"retire"}]}')
                """);
    }

    private void seedMutationPolicy() throws Exception {
        execute("""
                INSERT INTO entity_mutation_policy_config (
                  id,entity_id,entity_code,enabled,draft_document,
                  active_release_id,revision,status,migration_state,deleted
                ) VALUES (
                  'policy-config-1','entity-asset','asset',1,
                  '{"schemaVersion":1,"steps":[]}',
                  'policy-release-1',2,'PUBLISHED','NATIVE',0)
                """);
        execute("""
                INSERT INTO entity_mutation_policy_release (
                  id,config_id,version,config_document
                ) VALUES (
                  'policy-release-1','policy-config-1',1,
                  '{"schemaVersion":1,"steps":[]}')
                """);
    }

    /** 四种历史状态都属于确定性退役范围，不作为迁移门禁。 */
    private void seedTargetStates() throws Exception {
        execute("""
                INSERT INTO entity_change_target_instance (
                  id,binding_code,source_entity_code,source_record_id,
                  process_instance_id,target_entity_code,target_record_id,
                  target_document,status
                ) VALUES
                  ('frozen-target','APPLY','request','request-1','process-1',
                   'asset','asset-1','{}','FROZEN'),
                  ('failed-target','APPLY','request','request-2','process-2',
                   'asset','asset-2','{}','FAILED'),
                  ('conflict-target','APPLY','request','request-3','process-3',
                   'asset','asset-3','{}','CONFLICT'),
                  ('applied-target','APPLY','request','request-4','process-4',
                   'asset','asset-4','{}','APPLIED')
                """);
    }

    /** 模拟复制菜单及固定菜单已删、授权仍在的存量环境。 */
    private void seedClonedAndOrphanMenuGrants() throws Exception {
        execute("""
                INSERT INTO sys_menu (
                  id,parent_id,menu_name,menu_type,path,component,perm,
                  status,visible,deleted
                ) VALUES
                  ('mutation_policy_copy_root','0','实体变更策略副本','C',
                   '/system/entity-mutation-policies-copy',
                   'system/EntityMutationPolicyManagement',
                   'entity:mutation:config:copy','0','0',0),
                  ('mutation_policy_copy_child','mutation_policy_copy_root',
                   '执行策略副本','F','','',
                   'entity:mutation:config:copy-execute','0','0',0)
                """);
        execute("""
                INSERT INTO sys_role_menu (id,role_id,menu_id,create_time)
                VALUES
                  ('mutation-copy-root-grant','1',
                   'mutation_policy_copy_root',CURRENT_TIMESTAMP),
                  ('mutation-copy-child-grant','1',
                   'mutation_policy_copy_child',CURRENT_TIMESTAMP)
                """);
        execute("""
                DELETE FROM sys_menu
                WHERE id='entity_mutation_policy_publish_001'
                """);
    }

    private void assertSanitizedVersionDocuments() throws Exception {
        for (String expression : List.of(
                "(SELECT config_document FROM entity_version_config "
                        + "WHERE id='version-config-with-policy-json')",
                "(SELECT draft_document FROM entity_version_config "
                        + "WHERE id='version-config-with-policy-json')",
                "(SELECT config_document FROM entity_version_config_release "
                        + "WHERE id='version-release-with-policy-json')")) {
            assertEquals(0, count("SELECT JSON_CONTAINS_PATH(" + expression
                    + ",'one','$.steps','$.targetBindings')"));
            assertEquals(1, count("SELECT JSON_CONTAINS_PATH(" + expression
                    + ",'all','$.scenarios','$.triggers')"));
        }
    }

    private void assertRetiredSchemaRemoved() throws Exception {
        for (String table : List.of(
                "entity_change_target_instance",
                "entity_mutation_policy_release",
                "entity_mutation_policy_config")) {
            assertFalse(tableExists(table), table);
        }
    }

    private void assertRetainedSchemaPresent() throws Exception {
        for (String table : List.of(
                "entity_mutation_receipt",
                "entity_version_config",
                "entity_version_config_release",
                "entity_record_version")) {
            assertTrue(tableExists(table), table);
        }
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .placeholderReplacement(false)
                .cleanDisabled(false);
        if (target != null) {
            configuration.target(MigrationVersion.fromVersion(target));
        }
        return configuration.load();
    }

    private Flyway flywayFromRepresentativeV082() {
        return Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .placeholderReplacement(false)
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("082"))
                .target(MigrationVersion.fromVersion("083"))
                .cleanDisabled(false)
                .load();
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int count(String sql) throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private boolean tableExists(String table) throws Exception {
        try (Connection connection = connection();
             ResultSet tables = connection.getMetaData().getTables(
                     connection.getCatalog(), null, table,
                     new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(url, user, password);
    }
}
