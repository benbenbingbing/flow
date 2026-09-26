package com.workflow.dbmigrator;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用真实 MySQL 验证 V088 将多操作接口服务无损拆成独立接口扩展。 */
@Testcontainers(disabledWithoutDocker = true)
class InterfaceExtensionFlatteningMigrationTest {

    private static final String RESTRICTED_USERNAME =
            "workflow_schema_limited";
    private static final String RESTRICTED_PASSWORD =
            "workflow_schema_limited_password";

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("workflow_interface_extension")
                    .withUsername("workflow_test")
                    .withPassword("workflow_test_password");

    /**
     * 为迁移专项创建最小 schema 账号，故意不授予临时表和存储过程
     * 权限，避免 Testcontainers 默认宽权限掩盖生产环境问题。
     */
    @BeforeAll
    static void createRestrictedMigrationUser() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE USER IF NOT EXISTS '"
                    + RESTRICTED_USERNAME
                    + "'@'%' IDENTIFIED BY '"
                    + RESTRICTED_PASSWORD + "'");
            statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE, "
                    + "CREATE, DROP, ALTER, INDEX ON `"
                    + MYSQL.getDatabaseName() + "`.* TO '"
                    + RESTRICTED_USERNAME + "'@'%'");
        }
    }

    @BeforeEach
    void cleanDatabase() throws Exception {
        flyway().clean();
        createRepresentativeV087Schema();
        assertRestrictedMigrationGrants();
    }

    @Test
    void emptyV087DatabaseUsesOnlyUnifiedExtensionStorage()
            throws Exception {
        Flyway current = flywayFromRepresentativeV087();
        current.migrate();
        current.validate();

        assertEquals("089", current.info().current()
                .getVersion().getVersion());
        assertFalse(tableExists("ui_data_source_definition"));
        assertTrue(tableExists("ui_extension_definition"));
        assertTrue(tableExists("ui_event_binding"));
        assertTrue(columnExists(
                "ui_extension_definition", "legacy_service_id"));
        assertTrue(columnExists(
                "entity_list_field", "interface_extension_id"));
        assertTrue(columnExists(
                "entity_list_config", "query_interface_extension_id"));
        assertFalse(columnExists(
                "entity_list_field", "data_source_operation_code"));
        assertFalse(columnExists(
                "entity_list_config", "query_operation_code"));
        assertFalse(tableExists("flow_v088_interface_schema_guard"));
        assertFalse(tableExists("flow_v088_interface_operation_map"));
        assertFalse(tableExists("flow_v088_interface_migration_guard"));
        assertFalse(tableExists("flow_v088_migrated_data_bindings"));
    }

    /**
     * 模拟 MySQL 已提交首个 ALTER、但 V088 尚未成功的真实断点。
     * 清除失败 history 后重跑必须跳过 ADD COLUMN，不得再报重复列。
     */
    @Test
    void resumesWhenAllInterfaceColumnsWereCommittedBeforeFailure()
            throws Exception {
        applyCommittedInitialV088Alter();

        assertEquals(12, countRows("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'ui_extension_definition'
                  AND column_name IN (
                    'implementation_type','provider_code','scope_type',
                    'scope_id','implementation_config_document',
                    'execution_policy_document','input_schema_document',
                    'output_schema_document','interface_kind',
                    'interface_context_type','provider_operation_code',
                    'legacy_service_id')
                """));

        Flyway current = flywayFromRepresentativeV087();
        assertEquals(2, current.migrate().migrationsExecuted);
        current.validate();

        assertEquals("089", current.info().current()
                .getVersion().getVersion());
        assertFalse(tableExists("ui_data_source_definition"));
        assertFalse(tableExists("flow_v088_interface_schema_guard"));
    }

    /**
     * V088 的业务回填全部落库、但 history 未记成功时，repair 后的
     * 重跑必须是幂等的，不重复插入扩展或累加草稿修订号。
     */
    @Test
    void completedExpandBackfillCanBeReplayedBeforeContractCleanup()
            throws Exception {
        seedLegacyState();

        Flyway expandOnly = flywayFromRepresentativeV087ThroughV088();
        assertEquals(1, expandOnly.migrate().migrationsExecuted);
        assertTrue(tableExists("ui_data_source_definition"));
        assertTrue(columnExists("entity_list_field", "data_source_id"));
        assertTrue(columnExists(
                "entity_list_field", "interface_extension_id"));

        int formRevision = countRows("""
                SELECT revision FROM entity_form WHERE id = 'form-legacy'
                """);
        int eventRevision = countRows("""
                SELECT revision FROM ui_event_binding
                WHERE id = 'event-binding-legacy'
                """);
        assertEquals(3, countRows("""
                SELECT COUNT(*) FROM ui_extension_definition
                WHERE extension_type = 'INTERFACE'
                """));

        // 删除本测试刚写入的 history，模拟失败记录经 repair 清理后的状态。
        execute("DELETE FROM flyway_schema_history WHERE version = '088'");

        Flyway resumed = flywayFromRepresentativeV087();
        assertEquals(2, resumed.migrate().migrationsExecuted);
        resumed.validate();

        assertEquals(3, countRows("""
                SELECT COUNT(*) FROM ui_extension_definition
                WHERE extension_type = 'INTERFACE'
                """));
        assertEquals(formRevision, countRows("""
                SELECT revision FROM entity_form WHERE id = 'form-legacy'
                """));
        assertEquals(eventRevision, countRows("""
                SELECT revision FROM ui_event_binding
                WHERE id = 'event-binding-legacy'
                """));
        assertFalse(tableExists("ui_data_source_definition"));
    }

    /** V089 第一组列表旧列已提交删除后，重跑必须继续完成 contract。 */
    @Test
    void contractCleanupResumesAfterListFieldColumnsWereDropped()
            throws Exception {
        migrateLegacyStateThroughV088();
        execute("""
                ALTER TABLE entity_list_field
                  DROP COLUMN data_source_id,
                  DROP COLUMN data_source_operation_code
                """);
        createStaleV089GuardTable();

        Flyway resumed = flywayFromRepresentativeV087();
        assertEquals(1, resumed.migrate().migrationsExecuted);
        resumed.validate();

        assertContractCleanupCompleted();
    }

    /** V089 两组列表旧列均已提交删除后，重跑不得再次 DROP COLUMN。 */
    @Test
    void contractCleanupResumesAfterBothListColumnPairsWereDropped()
            throws Exception {
        migrateLegacyStateThroughV088();
        execute("""
                ALTER TABLE entity_list_field
                  DROP COLUMN data_source_id,
                  DROP COLUMN data_source_operation_code
                """);
        execute("""
                ALTER TABLE entity_list_config
                  DROP COLUMN query_data_source_id,
                  DROP COLUMN query_operation_code
                """);
        createStaleV089GuardTable();

        Flyway resumed = flywayFromRepresentativeV087();
        assertEquals(1, resumed.migrate().migrationsExecuted);
        resumed.validate();

        assertContractCleanupCompleted();
    }

    /** 最终 DROP TABLE 已提交但 history 未记成功时，V089 重跑应为 no-op。 */
    @Test
    void contractCleanupResumesAfterLegacyServiceTableWasDropped()
            throws Exception {
        migrateLegacyStateThroughV088();
        execute("""
                ALTER TABLE entity_list_field
                  DROP COLUMN data_source_id,
                  DROP COLUMN data_source_operation_code
                """);
        execute("""
                ALTER TABLE entity_list_config
                  DROP COLUMN query_data_source_id,
                  DROP COLUMN query_operation_code
                """);
        execute("DROP TABLE ui_data_source_definition");
        createStaleV089GuardTable();

        Flyway resumed = flywayFromRepresentativeV087();
        assertEquals(1, resumed.migrate().migrationsExecuted);
        resumed.validate();

        assertContractCleanupCompleted();
    }

    /** 介于 0 和完整 12 列之间的不一致状态必须中止，不自动猜测修复。 */
    @Test
    void rejectsPartiallyCommittedInterfaceColumnState()
            throws Exception {
        execute("""
                ALTER TABLE ui_extension_definition
                  ADD COLUMN implementation_type varchar(30) DEFAULT NULL
                """);

        FlywayException failure = assertThrows(
                FlywayException.class,
                () -> flywayFromRepresentativeV087().migrate());

        assertTrue(rootMessage(failure).contains(
                "chk_flow_v088_interface_columns_complete"));
        assertTrue(tableExists("ui_data_source_definition"));
        assertFalse(columnExists("ui_extension_definition", "provider_code"));
    }

    @Test
    void upgradeSplitsOperationsRewritesDraftsAndPreservesPublishedBytes()
            throws Exception {
        seedLegacyState();

        String immutableSnapshot = queryString(
                "SELECT snapshot_document FROM ui_config_release "
                        + "WHERE id='release-legacy'");
        String immutableHash = queryString(
                "SELECT content_hash FROM ui_config_release "
                        + "WHERE id='release-legacy'");

        Flyway current = flywayFromRepresentativeV087();
        assertEquals(2, current.migrate().migrationsExecuted);
        current.validate();

        assertFalse(tableExists("ui_data_source_definition"));
        assertTrue(tableExists("ui_event_binding"));
        assertEquals(3, countRows("""
                SELECT COUNT(*)
                FROM ui_extension_definition
                WHERE extension_type = 'INTERFACE'
                """));
        assertEquals(1, countRows("""
                SELECT COUNT(*)
                FROM sys_role_menu
                WHERE role_id = 'interface-parent-only-role'
                  AND menu_id = 'extension_list_permission_001'
                """));
        assertEquals(1, countRows("""
                SELECT COUNT(*)
                FROM ui_extension_definition
                WHERE id = 'existing-component'
                  AND extension_type = 'FORM'
                """));

        String loadId = queryString("""
                SELECT id
                FROM ui_extension_definition
                WHERE legacy_service_id = 'legacy-form-service'
                  AND provider_operation_code = 'load'
                """);
        String queryId = queryString("""
                SELECT id
                FROM ui_extension_definition
                WHERE legacy_service_id = 'legacy-list-service'
                  AND provider_operation_code = 'query'
                """);
        assertEquals("legacy.form.load", queryString("""
                SELECT extension_key
                FROM ui_extension_definition
                WHERE id = '%s'
                """.formatted(loadId)));
        assertEquals("ACTIVE", queryString("""
                SELECT status
                FROM ui_extension_definition
                WHERE id = '%s'
                """.formatted(loadId)));
        assertEquals("DISABLED", queryString("""
                SELECT status
                FROM ui_extension_definition
                WHERE id = '%s'
                """.formatted(queryId)));
        assertEquals("REGISTERED_PROVIDER", queryString("""
                SELECT implementation_type
                FROM ui_extension_definition
                WHERE id = '%s'
                """.formatted(loadId)));
        assertEquals("FORM", queryString("""
                SELECT interface_context_type
                FROM ui_extension_definition
                WHERE id = '%s'
                """.formatted(loadId)));

        // 操作级对象做浅覆盖；显式 JSON null 也必须保留，不能被 merge patch 删除。
        assertEquals("operation", queryString("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                    implementation_config_document, '$.shared'))
                FROM ui_extension_definition
                WHERE id = '%s'
                """.formatted(loadId)));
        assertEquals(1, countRows("""
                SELECT JSON_CONTAINS_PATH(
                    implementation_config_document, 'one', '$.baseOnly')
                FROM ui_extension_definition
                WHERE id = '%s'
                """.formatted(loadId)));
        assertEquals("NULL", queryString("""
                SELECT JSON_TYPE(JSON_EXTRACT(
                    implementation_config_document, '$.nullableValue'))
                FROM ui_extension_definition
                WHERE id = '%s'
                """.formatted(loadId)));
        assertEquals("5000", queryString("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                    execution_policy_document, '$.timeoutMs'))
                FROM ui_extension_definition
                WHERE id = '%s'
                """.formatted(loadId)));
        assertEquals("30", queryString("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                    execution_policy_document, '$.cacheSeconds'))
                FROM ui_extension_definition
                WHERE id = '%s'
                """.formatted(loadId)));

        assertEquals(loadId, queryString("""
                SELECT interface_extension_id
                FROM entity_list_field
                WHERE id = 'list-field-legacy'
                """));
        assertEquals(queryId, queryString("""
                SELECT query_interface_extension_id
                FROM entity_list_config
                WHERE id = 'list-legacy'
                """));
        assertEquals(loadId, queryString("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                    data_source_bindings_document,
                    '$.FORM_INIT[0].extensionId'))
                FROM entity_form
                WHERE id = 'form-legacy'
                """));
        assertEquals(loadId, queryString("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                    data_source_bindings_document,
                    '$.FORM_INIT[1].extensionId'))
                FROM entity_form
                WHERE id = 'form-legacy'
                """));
        assertEquals("first", queryString("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                    data_source_bindings_document,
                    '$.FORM_INIT[0].marker'))
                FROM entity_form
                WHERE id = 'form-legacy'
                """));
        assertEquals("second", queryString("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                    data_source_bindings_document,
                    '$.FORM_INIT[1].marker'))
                FROM entity_form
                WHERE id = 'form-legacy'
                """));
        assertEquals(loadId, queryString("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                    data_source_bindings_document,
                    '$.AFTER_LOAD.extensionId'))
                FROM entity_form
                WHERE id = 'form-legacy'
                """));
        assertEquals(loadId, queryString("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                    data_source_bindings_document,
                    '$.FIELD_DEFAULT[0].extensionId'))
                FROM entity_form_node
                WHERE id = 'node-legacy'
                """));
        assertEquals(loadId, queryString("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                    data_source_bindings_document,
                    '$.FIELD_OPTIONS.extensionId'))
                FROM entity_form_node
                WHERE id = 'node-legacy'
                """));
        assertEquals(0, countRows("""
                SELECT JSON_SEARCH(
                    data_source_bindings_document, 'one',
                    'legacy-form-service', NULL,
                    '$.FORM_INIT[*].serviceId', '$.AFTER_LOAD.serviceId')
                    IS NOT NULL
                FROM entity_form
                WHERE id = 'form-legacy'
                """));
        assertEquals(loadId, queryString("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                    steps_document, '$[0].extensionId'))
                FROM ui_event_binding
                WHERE id = 'event-binding-legacy'
                """));
        assertEquals(0, countRows("""
                SELECT JSON_CONTAINS_PATH(
                    steps_document, 'one',
                    '$[0].serviceId', '$[0].operationCode')
                FROM ui_event_binding
                WHERE id = 'event-binding-legacy'
                """));
        assertEquals("legacy-form-service", queryString("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                    steps_document, '$[0].serviceId'))
                FROM ui_event_binding
                WHERE id = 'event-binding-dangling'
                """));
        assertEquals(5, countRows("""
                SELECT revision
                FROM ui_event_binding
                WHERE id = 'event-binding-dangling'
                """));

        assertEquals(immutableSnapshot, queryString(
                "SELECT snapshot_document FROM ui_config_release "
                        + "WHERE id='release-legacy'"));
        assertEquals(immutableHash, queryString(
                "SELECT content_hash FROM ui_config_release "
                        + "WHERE id='release-legacy'"));
        assertEquals(3, countRows("""
                SELECT COUNT(*)
                FROM sys_role_menu
                WHERE role_id = 'interface-migration-role'
                  AND menu_id IN (
                    'extension_list_permission_001',
                    'extension_update_permission_001',
                    'extension_test_permission_001')
                """));
        assertEquals(0, countRows("""
                SELECT COUNT(*)
                FROM sys_menu
                WHERE id LIKE 'interface_service_%'
                   OR id = 'user_manual_interface_service_001'
                   OR perm LIKE 'system:interface-service:%'
                """));
    }

    /** 列表关系列存在无法解析的历史 pair 时，迁移必须失败且不能清空引用。 */
    @Test
    void upgradeRejectsUnmappedLegacyListInterfaceReference()
            throws Exception {
        execute("""
                INSERT INTO entity_list_config (
                  id,entity_id,entity_code,list_key,list_name,
                  query_data_source_id,query_operation_code
                ) VALUES (
                  'dangling-list','entity-legacy','legacy_entity','dangling',
                  '悬挂列表','missing-service','missing-operation')
                """);

        FlywayException failure = assertThrows(
                FlywayException.class,
                () -> flywayFromRepresentativeV087().migrate());

        assertTrue(rootMessage(failure).contains(
                "chk_flow_v088_list_config_refs_mapped"));
        assertEquals("missing-service", queryString("""
                SELECT query_data_source_id
                FROM entity_list_config
                WHERE id = 'dangling-list'
                """));
        assertTrue(columnExists(
                "entity_list_config", "query_operation_code"));

        execute("DELETE FROM entity_list_config WHERE id = 'dangling-list'");
        Flyway repaired = flywayFromRepresentativeV087();
        repaired.repair();
        assertEquals(2, repaired.migrate().migrationsExecuted);
        repaired.validate();
        assertFalse(tableExists("ui_data_source_definition"));
    }

    /** 只填写 serviceId 或 operationCode 的单边引用同样属于损坏数据。 */
    @Test
    void upgradeRejectsPartiallySpecifiedLegacyListFieldReference()
            throws Exception {
        execute("""
                INSERT INTO entity_list_field (
                  id,list_config_id,field_id,field_code,field_name,
                  data_source_id,data_source_operation_code
                ) VALUES (
                  'partial-field','list-legacy','field-legacy','name',
                  '名称','missing-service',NULL)
                """);

        FlywayException failure = assertThrows(
                FlywayException.class,
                () -> flywayFromRepresentativeV087().migrate());

        assertTrue(rootMessage(failure).contains(
                "chk_flow_v088_list_field_refs_mapped"));
        assertEquals("missing-service", queryString("""
                SELECT data_source_id
                FROM entity_list_field
                WHERE id = 'partial-field'
                """));
        assertTrue(columnExists(
                "entity_list_field", "data_source_operation_code"));
    }

    /**
     * 构造 V088 直接依赖的 V087 表结构。仓库完整历史在 MySQL 8.4 上会先被
     * 既有 V062 的临时表重开限制阻断；专项测试 baseline 到 87，仍由真实
     * Flyway/MySQL 解析和执行 V088，而不篡改任何已发布历史迁移。
     */
    private void createRepresentativeV087Schema() throws Exception {
        execute("""
                CREATE TABLE ui_extension_definition (
                  id varchar(64) NOT NULL,
                  extension_type varchar(20) NOT NULL,
                  extension_key varchar(100) NOT NULL,
                  display_name varchar(200) NOT NULL,
                  version int NOT NULL,
                  snapshot_version int NOT NULL DEFAULT 1,
                  visibility_scope varchar(20) NOT NULL DEFAULT 'GLOBAL',
                  entity_codes_document longtext,
                  supported_modes_document longtext,
                  supported_node_types_document longtext,
                  supported_bindings_document longtext,
                  config_schema_document longtext,
                  capabilities_document longtext,
                  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
                  revision int NOT NULL DEFAULT 1,
                  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ON UPDATE CURRENT_TIMESTAMP,
                  deleted tinyint NOT NULL DEFAULT 0,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_ui_extension_version (
                    extension_type, extension_key, version, deleted)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  COLLATE=utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE ui_data_source_definition (
                  id varchar(64) NOT NULL,
                  source_code varchar(100) NOT NULL,
                  source_name varchar(200) NOT NULL,
                  source_type varchar(30) NOT NULL,
                  provider_code varchar(100) DEFAULT NULL,
                  scope_type varchar(20) NOT NULL DEFAULT 'GLOBAL',
                  scope_id varchar(64) DEFAULT NULL,
                  config_document longtext,
                  execution_policy_document longtext,
                  operations_document longtext,
                  revision int NOT NULL DEFAULT 1,
                  enabled tinyint NOT NULL DEFAULT 1,
                  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ON UPDATE CURRENT_TIMESTAMP,
                  deleted tinyint NOT NULL DEFAULT 0,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_ui_data_source_code (source_code, deleted)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  COLLATE=utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE entity_list_field (
                  id varchar(64) NOT NULL,
                  list_config_id varchar(64) NOT NULL,
                  field_id varchar(64) NOT NULL,
                  field_code varchar(100) NOT NULL,
                  field_name varchar(200) NOT NULL,
                  data_source_id varchar(64) DEFAULT NULL,
                  data_source_operation_code varchar(100) DEFAULT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  COLLATE=utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE entity_list_config (
                  id varchar(64) NOT NULL,
                  entity_id varchar(64) NOT NULL,
                  entity_code varchar(100) NOT NULL,
                  list_key varchar(100) NOT NULL,
                  list_name varchar(200) NOT NULL,
                  query_data_source_id varchar(64) DEFAULT NULL,
                  query_operation_code varchar(100) DEFAULT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  COLLATE=utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE entity_form (
                  id varchar(64) NOT NULL,
                  entity_id varchar(64) NOT NULL,
                  form_name varchar(100) NOT NULL,
                  form_key varchar(100) NOT NULL,
                  data_source_bindings_document longtext,
                  revision int NOT NULL DEFAULT 1,
                  draft_hash varchar(64) DEFAULT NULL,
                  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  COLLATE=utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE entity_form_node (
                  id varchar(64) NOT NULL,
                  form_id varchar(64) NOT NULL,
                  node_key varchar(100) NOT NULL,
                  node_type varchar(30) NOT NULL,
                  data_source_bindings_document longtext,
                  revision int NOT NULL DEFAULT 1,
                  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  COLLATE=utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE ui_event_binding (
                  id varchar(64) NOT NULL,
                  owner_type varchar(20) NOT NULL,
                  owner_id varchar(64) NOT NULL,
                  target_type varchar(20) NOT NULL DEFAULT 'OWNER',
                  target_key varchar(100) NOT NULL DEFAULT '',
                  event_code varchar(50) NOT NULL,
                  inheritance_mode varchar(20) NOT NULL DEFAULT 'INHERIT',
                  steps_document longtext,
                  revision int NOT NULL DEFAULT 1,
                  enabled tinyint NOT NULL DEFAULT 1,
                  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ON UPDATE CURRENT_TIMESTAMP,
                  deleted tinyint NOT NULL DEFAULT 0,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  COLLATE=utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE sys_menu (
                  id varchar(64) NOT NULL,
                  parent_id varchar(64) DEFAULT '0',
                  menu_name varchar(100) NOT NULL,
                  path varchar(200) DEFAULT NULL,
                  component varchar(255) DEFAULT NULL,
                  perm varchar(200) DEFAULT NULL,
                  create_time datetime DEFAULT CURRENT_TIMESTAMP,
                  update_time datetime DEFAULT CURRENT_TIMESTAMP
                    ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  COLLATE=utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE sys_role (
                  id varchar(64) NOT NULL,
                  role_name varchar(50) NOT NULL,
                  role_code varchar(50) NOT NULL,
                  description varchar(200) DEFAULT '',
                  status char(1) DEFAULT '0',
                  deleted tinyint DEFAULT 0,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  COLLATE=utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE sys_role_menu (
                  id varchar(64) NOT NULL,
                  role_id varchar(64) NOT NULL,
                  menu_id varchar(64) NOT NULL,
                  create_time datetime DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_role_menu (role_id, menu_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  COLLATE=utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE ui_config_release (
                  id varchar(64) NOT NULL,
                  config_type varchar(20) NOT NULL,
                  config_id varchar(64) NOT NULL,
                  version int NOT NULL,
                  snapshot_document longtext NOT NULL,
                  content_hash varchar(64) NOT NULL,
                  status varchar(20) NOT NULL DEFAULT 'INACTIVE',
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  COLLATE=utf8mb4_unicode_ci
                """);
        execute("""
                INSERT INTO sys_menu (
                  id,parent_id,menu_name,path,component,perm
                ) VALUES
                  ('extension_list_permission_001','0','查看扩展',NULL,NULL,
                   'system:extension:list'),
                  ('extension_update_permission_001','0','维护扩展',NULL,NULL,
                   'system:extension:update'),
                  ('extension_test_permission_001','0','测试扩展',NULL,NULL,
                   'system:extension:test'),
                  ('interface_service_menu_001','0','接口服务',
                   '/system/interface-services','system/InterfaceServices',
                   'system:interface-service:list'),
                  ('interface_service_list_001',
                   'interface_service_menu_001','查看接口服务',NULL,NULL,
                   'system:interface-service:list'),
                  ('interface_service_update_001',
                   'interface_service_menu_001','维护接口服务',NULL,NULL,
                   'system:interface-service:update'),
                  ('interface_service_test_001',
                   'interface_service_menu_001','测试接口服务',NULL,NULL,
                   'system:interface-service:test'),
                  ('user_manual_interface_service_001','0','接口服务手册',
                   '/manual/interface-service','manual/InterfaceServiceManual',
                   'user-manual:interface-service:view')
                """);
    }

    /** 复现首段 ALTER 已被 MySQL 隐式提交、后续语句失败的断点状态。 */
    private void applyCommittedInitialV088Alter() throws Exception {
        execute("""
                ALTER TABLE ui_extension_definition
                  ADD COLUMN implementation_type varchar(30) DEFAULT NULL,
                  ADD COLUMN provider_code varchar(100) DEFAULT NULL,
                  ADD COLUMN scope_type varchar(20) DEFAULT NULL,
                  ADD COLUMN scope_id varchar(64) DEFAULT NULL,
                  ADD COLUMN implementation_config_document longtext,
                  ADD COLUMN execution_policy_document longtext,
                  ADD COLUMN input_schema_document longtext,
                  ADD COLUMN output_schema_document longtext,
                  ADD COLUMN interface_kind varchar(20) DEFAULT NULL,
                  ADD COLUMN interface_context_type varchar(20) DEFAULT NULL,
                  ADD COLUMN provider_operation_code varchar(100) DEFAULT NULL,
                  ADD COLUMN legacy_service_id varchar(64) DEFAULT NULL,
                  ADD UNIQUE KEY uk_ui_extension_legacy_interface (
                    legacy_service_id, provider_operation_code, deleted)
                """);
    }

    /** 构造 V087 真实结构上的服务、引用、发布制品和非超管授权。 */
    private void seedLegacyState() throws Exception {
        execute("""
                INSERT INTO ui_extension_definition (
                  id,extension_type,extension_key,display_name,version,
                  snapshot_version,visibility_scope,status,revision,deleted
                ) VALUES (
                  'existing-component','FORM','existing.component',
                  '已有组件',1,1,'GLOBAL','ACTIVE',1,0)
                """);
        execute("""
                INSERT INTO ui_data_source_definition (
                  id,source_code,source_name,source_type,provider_code,
                  scope_type,scope_id,config_document,
                  execution_policy_document,operations_document,
                  revision,enabled,deleted
                ) VALUES (
                  'legacy-form-service','legacy.form','历史表单服务',
                  'REGISTERED_PROVIDER','legacy-provider','FORM','form-legacy',
                  '{"shared":"base","baseOnly":true,"nullableValue":"base"}',
                  '{"timeoutMs":3000,"cacheSeconds":30}',
                  '[{"code":"load","name":"加载","kind":"READ",'
                    '"contextType":"FORM","config":{'
                      '"shared":"operation","nullableValue":null},'
                    '"executionPolicy":{"timeoutMs":5000},'
                    '"inputSchema":{"type":"object"},'
                    '"outputSchema":{"type":"object"}},'
                   '{"code":"save","name":"保存","kind":"WRITE",'
                    '"contextType":"FORM"}]',
                  7,1,0),
                  ('legacy-list-service','legacy.list','历史列表服务',
                  'STATIC_OPTIONS',NULL,'LIST','list-legacy',
                  '{"options":[]}','{"timeoutMs":2000}',
                  '[{"code":"query","name":"查询","kind":"READ",'
                    '"contextType":"LIST",'
                    '"outputSchema":{"type":"object",'
                      '"properties":{"records":{"type":"array"}}}}]',
                  2,0,0)
                """);
        execute("""
                INSERT INTO entity_list_config (
                  id,entity_id,entity_code,list_key,list_name,
                  query_data_source_id,query_operation_code
                ) VALUES (
                  'list-legacy','entity-legacy','legacy_entity','default',
                  '历史列表','legacy-list-service','query')
                """);
        execute("""
                INSERT INTO entity_list_field (
                  id,list_config_id,field_id,field_code,field_name,
                  data_source_id,data_source_operation_code
                ) VALUES (
                  'list-field-legacy','list-legacy','field-legacy','name',
                  '名称','legacy-form-service','load')
                """);
        execute("""
                INSERT INTO entity_form (
                  id,entity_id,form_name,form_key,
                  data_source_bindings_document
                ) VALUES (
                  'form-legacy','entity-legacy','历史表单','default',
                  '{"FORM_INIT":['
                    '{"serviceId":"legacy-form-service",'
                      '"operationCode":"load","marker":"first"},'
                    '{"serviceId":"legacy-form-service",'
                      '"operationCode":"load","marker":"second"}],'
                    '"AFTER_LOAD":{"serviceId":"legacy-form-service",'
                      '"operationCode":"load"}}')
                """);
        execute("""
                INSERT INTO entity_form_node (
                  id,form_id,node_key,node_type,data_source_bindings_document
                ) VALUES (
                  'node-legacy','form-legacy','name','FIELD',
                  '{"FIELD_DEFAULT":[{"serviceId":"legacy-form-service",'
                    '"operationCode":"load"}],'
                    '"FIELD_OPTIONS":{"serviceId":"legacy-form-service",'
                    '"operationCode":"load"}}')
                """);
        execute("""
                INSERT INTO ui_event_binding (
                  id,owner_type,owner_id,target_type,target_key,event_code,
                  inheritance_mode,steps_document,revision,enabled,deleted
                ) VALUES (
                  'event-binding-legacy','FORM','form-legacy','OWNER','',
                  'FORM_OPEN','INHERIT',
                  '[{"serviceId":"legacy-form-service",'
                    '"operationCode":"load","name":"加载表单","order":10},'
                   '{"type":"builtin","actionKey":"refresh","order":20}]',
                  3,1,0),(
                  'event-binding-dangling','FORM','form-legacy','OWNER','',
                  'FORM_SUBMIT','INHERIT',
                  '[{"serviceId":"legacy-form-service",'
                    '"operationCode":"removed-operation","order":10}]',
                  5,1,0)
                """);
        execute("""
                INSERT INTO ui_config_release (
                  id,config_type,config_id,version,snapshot_document,
                  content_hash,status
                ) VALUES (
                  'release-legacy','FORM','form-legacy',1,
                  '{"eventBindings":[{"steps":[{'
                    '"serviceId":"legacy-form-service",'
                    '"operationCode":"load"}]}]}',
                  REPEAT('a',64),'ACTIVE')
                """);
        execute("""
                INSERT INTO sys_role (
                  id,role_name,role_code,description,status,deleted
                ) VALUES (
                  'interface-migration-role','接口迁移角色',
                  'interface_migration_role','验证权限迁移','0',0),(
                  'interface-parent-only-role','接口父菜单角色',
                  'interface_parent_only_role','验证父菜单权限迁移','0',0)
                """);
        execute("""
                INSERT INTO sys_role_menu (id,role_id,menu_id)
                VALUES
                  ('legacy-grant-list','interface-migration-role',
                   'interface_service_list_001'),
                  ('legacy-grant-update','interface-migration-role',
                   'interface_service_update_001'),
                  ('legacy-grant-test','interface-migration-role',
                   'interface_service_test_001'),
                  ('legacy-parent-only-grant','interface-parent-only-role',
                   'interface_service_menu_001')
                """);
    }

    /** 构造包含真实引用的 expand 完成态，供 V089 各 DDL 断点测试复用。 */
    private void migrateLegacyStateThroughV088() throws Exception {
        seedLegacyState();
        Flyway expandOnly = flywayFromRepresentativeV087ThroughV088();
        assertEquals(1, expandOnly.migrate().migrationsExecuted);
        expandOnly.validate();
    }

    /** 模拟 V089 失败时尚未来得及清理的普通 guard 辅助表。 */
    private void createStaleV089GuardTable() throws Exception {
        execute("CREATE TABLE flow_v089_interface_contract_guard "
                + "(stale_marker tinyint NOT NULL) ENGINE=InnoDB");
    }

    /** 校验 V089 contract 已完整收口且没有遗留迁移辅助表。 */
    private void assertContractCleanupCompleted() throws Exception {
        assertFalse(tableExists("ui_data_source_definition"));
        assertFalse(columnExists("entity_list_field", "data_source_id"));
        assertFalse(columnExists(
                "entity_list_field", "data_source_operation_code"));
        assertFalse(columnExists(
                "entity_list_config", "query_data_source_id"));
        assertFalse(columnExists(
                "entity_list_config", "query_operation_code"));
        assertFalse(tableExists("flow_v089_interface_contract_guard"));
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return String.valueOf(current.getMessage());
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(),
                        RESTRICTED_USERNAME, RESTRICTED_PASSWORD)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
    }

    private Flyway flywayFromRepresentativeV087() {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(),
                        RESTRICTED_USERNAME, RESTRICTED_PASSWORD)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("87"))
                .cleanDisabled(false)
                .load();
    }

    private Flyway flywayFromRepresentativeV087ThroughV088() {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(),
                        RESTRICTED_USERNAME, RESTRICTED_PASSWORD)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("87"))
                .target(MigrationVersion.fromVersion("88"))
                .cleanDisabled(false)
                .load();
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = restrictedConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int countRows(String sql) throws Exception {
        try (Connection connection = restrictedConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private String queryString(String sql) throws Exception {
        try (Connection connection = restrictedConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private boolean tableExists(String table) throws Exception {
        return countRows("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = '%s'
                """.formatted(table)) == 1;
    }

    private boolean columnExists(String table, String column)
            throws Exception {
        return countRows("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = '%s'
                  AND column_name = '%s'
                """.formatted(table, column)) == 1;
    }

    /** 确认专项迁移账号没有本次回归所针对的特权。 */
    private void assertRestrictedMigrationGrants() throws Exception {
        List<String> grants = new ArrayList<>();
        try (Connection connection = restrictedConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SHOW GRANTS FOR CURRENT_USER")) {
            while (result.next()) {
                grants.add(result.getString(1).toUpperCase());
            }
        }
        String effectiveGrants = String.join("\n", grants);
        assertFalse(effectiveGrants.contains("CREATE TEMPORARY TABLES"));
        assertFalse(effectiveGrants.contains("CREATE ROUTINE"));
        assertFalse(effectiveGrants.contains("ALTER ROUTINE"));
        assertFalse(effectiveGrants.contains("EXECUTE"));
    }

    private Connection restrictedConnection() throws Exception {
        return DriverManager.getConnection(
                MYSQL.getJdbcUrl(),
                RESTRICTED_USERNAME,
                RESTRICTED_PASSWORD);
    }
}
