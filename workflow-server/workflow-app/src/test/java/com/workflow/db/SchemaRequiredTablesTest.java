package com.workflow.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据库 Schema 必需表与迁移版本守护测试。
 *
 * <p>验证基线迁移创建关键表、Flyway 版本号唯一且覆盖关键里程碑，
 * 以及增量迁移(UI 配置、实体生命周期、配置迁移包等)创建所需的表与字段。</p>
 */
class SchemaRequiredTablesTest {

    /**
     * 基线迁移应创建角色菜单关联与节点表单表。
     *
     * <p>断言 V001 SQL 含 sys_role_menu 与 process_node_form 建表语句。</p>
     */
    @Test
    void baselineMigrationCreatesTablesUsedByRoleMenuAndNodeFormMappers() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V001__business_schema.sql");

        assertTrue(Files.exists(migration),
                "baseline migration must create sys_role_menu and process_node_form");

        String sql = Files.readString(migration);
        assertTrue(sql.contains("CREATE TABLE `sys_role_menu`"),
                "baseline migration must create sys_role_menu");
        assertTrue(sql.contains("CREATE TABLE `process_node_form`"),
                "baseline migration must create process_node_form");
    }

    /** 基线迁移应创建节点审批与知会记录表 */
    @Test
    void baselineMigrationCreatesTablesUsedByApprovalAndCcMappers() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V001__business_schema.sql");

        assertTrue(Files.exists(migration),
                "baseline migration must create process_node_approval and process_cc_record");

        String sql = Files.readString(migration);
        assertTrue(sql.contains("CREATE TABLE `process_node_approval`"),
                "baseline migration must create process_node_approval");
        assertTrue(sql.contains("CREATE TABLE `process_cc_record`"),
                "baseline migration must create process_cc_record");
    }

    /** 基线迁移应创建实体关系表 */
    @Test
    void baselineMigrationCreatesEntityRelationTable() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V001__business_schema.sql");

        assertTrue(Files.exists(migration),
                "baseline migration must create entity_relation");

        String sql = Files.readString(migration);
        assertTrue(sql.contains("CREATE TABLE `entity_relation`"),
                "baseline migration must create entity_relation");
    }

    /**
     * 命名隔离迁移应创建最终的运行时表名。
     *
     * <p>断言 V001 创建 process_definition_config 表，
     * V017 将 entity_data 重命名为 runtime_entity_record、flow_action 重命名为 process_action。</p>
     */
    @Test
    void namingIsolationMigrationCreatesFinalRuntimeTableNames() throws Exception {
        Path v001 = Path.of("src/main/resources/db/migration/V001__business_schema.sql");
        Path v017 = Path.of("src/main/resources/db/migration/V017__isolate_database_table_names.sql");
        assertTrue(Files.exists(v001),
                "V001 business schema migration must exist");
        assertTrue(Files.exists(v017),
                "V017 database naming isolation migration must exist");
        String baselineSql = Files.readString(v001);
        String namingSql = Files.readString(v017);
        assertTrue(baselineSql.contains("process_definition_config"),
                "V001 must create the process_definition_config table");
        assertTrue(namingSql.contains("'entity_data', 'runtime_entity_record'"),
                "V017 must rename entity_data to runtime_entity_record");
        assertTrue(namingSql.contains("'flow_action', 'process_action'"),
                "V017 must rename flow_action to process_action");
    }

    /**
     * Flyway 迁移版本号应唯一且覆盖关键里程碑。
     *
     * <p>扫描迁移目录提取版本号，断言无重复且含 001-031 等关键版本。</p>
     */
    @Test
    void flywayMigrationVersionsAreUnique() throws Exception {
        Pattern versionPattern = Pattern.compile("^V(\\d{3})__.+\\.sql$");
        List<String> versions = Files.list(Path.of("src/main/resources/db/migration"))
                .map(path -> path.getFileName().toString())
                .map(versionPattern::matcher)
                .filter(Matcher::matches)
                .map(matcher -> matcher.group(1))
                .collect(Collectors.toList());

        Map<String, Long> counts = versions.stream()
                .collect(Collectors.groupingBy(version -> version, Collectors.counting()));
        List<String> duplicates = counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());

        assertTrue(duplicates.isEmpty(), "duplicate Flyway versions: " + duplicates);
        assertTrue(versions.contains("001"), "baseline schema migration must be V001");
        assertTrue(versions.contains("002"), "built-in seed migration must be V002");
        assertTrue(versions.contains("017"), "database naming isolation must be V017");
        assertTrue(versions.contains("018"), "entity list runtime migration must be V018");
        assertTrue(versions.contains("019"), "entity lifecycle migration must be V019");
        assertTrue(versions.contains("020"), "system entity catalog migration must be V020");
        assertTrue(versions.contains("025"), "portable JSON document migration must be V025");
        assertTrue(versions.contains("026"), "dynamic entity collation migration must be V026");
        assertTrue(versions.contains("030"), "incremental UI configuration migration must be V030");
        assertTrue(versions.contains("031"), "UI extension registry migration must be V031");
    }

    /**
     * V018 迁移应创建作用域表与规范化菜单字段。
     *
     * <p>断言 SQL 含 5 张作用域表建表语句、sys_menu 字段规范化与删除旧权限表。</p>
     */
    @Test
    void entityListRuntimeMigrationCreatesScopeTablesAndCanonicalMenuFields() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V018__unify_entity_list_runtime_and_scope.sql");
        assertTrue(Files.exists(migration), "V018 entity list runtime migration must exist");
        String sql = Files.readString(migration);
        for (String table : List.of(
                "entity_list_scope_policy",
                "entity_list_scope_binding",
                "entity_list_scope_release",
                "entity_list_scope_audit_log",
                "entity_list_scope_delegation")) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + table),
                    "V018 must create " + table);
        }
        assertTrue(sql.contains("'sys_menu',\n  'resource_type'"));
        assertTrue(sql.contains("'sys_menu',\n  'list_key'"));
        assertTrue(sql.contains("DROP TABLE IF EXISTS entity_list_permission"));
    }

    /**
     * V011 配置迁移 Schema 应创建包与基线表并初始化权限。
     *
     * <p>断言 SQL 含 7 张配置迁移表建表语句与 7 项权限种子数据，
     * 且 V012 补充迁移含 list 权限资源与授权语句。</p>
     */
    @Test
    void configMigrationSchemaCreatesPackageAndBaselineTables() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V011__add_config_migration.sql");
        assertTrue(Files.exists(migration), "V011 config migration schema must exist");

        String sql = Files.readString(migration);
        for (String table : List.of(
                "config_migration_asset",
                "config_export_package",
                "config_export_package_item",
                "config_import_package",
                "config_import_item",
                "config_asset_baseline",
                "config_environment_mapping")) {
            assertTrue(sql.contains("CREATE TABLE `" + table + "`"),
                    "V011 must create " + table);
        }
        for (String permission : List.of(
                "config-migration:list",
                "config-migration:export",
                "config-migration:download",
                "config-migration:import",
                "config-migration:analyze",
                "config-migration:publish",
                "config-migration:rollback")) {
            assertTrue(sql.contains(permission), "V011 must seed permission " + permission);
        }

        Path permissionFix = Path.of(
                "src/main/resources/db/migration/V012__fix_config_migration_list_permission.sql");
        assertTrue(Files.exists(permissionFix), "V012 list permission compatibility migration must exist");
        String permissionSql = Files.readString(permissionFix);
        assertTrue(permissionSql.contains("'config_migration_list_001'"),
                "V012 must create an F type list permission resource");
        assertTrue(permissionSql.contains("'config-migration:list'"),
                "V012 must grant config-migration:list");
    }

    /**
     * 实体生命周期与系统目录迁移应存在且含关键字段。
     *
     * <p>断言 V019 含 lifecycle_mode、删除 enable_process 列与 entity_publish_history 表，
     * V020 含 storage_mode、系统表匹配与 information_schema 查询。</p>
     */
    @Test
    void entityLifecycleAndSystemCatalogMigrationsExist() throws Exception {
        String lifecycleSql = Files.readString(Path.of(
                "src/main/resources/db/migration/V019__support_standalone_entity_lifecycle.sql"));
        assertTrue(lifecycleSql.contains("lifecycle_mode"));
        assertTrue(lifecycleSql.contains("DROP COLUMN enable_process"));
        assertTrue(lifecycleSql.contains("entity_publish_history"));

        String storageSql = Files.readString(Path.of(
                "src/main/resources/db/migration/V020__catalog_system_entities.sql"));
        assertTrue(storageSql.contains("storage_mode"));
        assertTrue(storageSql.contains("tables.TABLE_NAME LIKE 'sys\\_%'"));
        assertTrue(storageSql.contains("'SYSTEM'"));
        assertTrue(storageSql.contains("information_schema.COLUMNS"));
    }

    /**
     * V030 增量 UI 配置迁移应创建必需表与字段。
     *
     * <p>断言 SQL 含 5 张 UI 配置表建表语句、revision 等关键列、
     * 以及 snapshot_document LONGTEXT 与 content_hash 列。</p>
     */
    @Test
    void incrementalUiConfigurationMigrationCreatesRequiredTables() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V030__add_incremental_ui_configuration.sql");
        assertTrue(Files.exists(migration), "V030 UI configuration migration must exist");
        String sql = Files.readString(migration);
        for (String table : List.of(
                "entity_form_node",
                "ui_config_release",
                "ui_data_source_definition",
                "ui_component_template",
                "ui_component_template_version")) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + table),
                    "V030 must create " + table);
        }
        for (String column : List.of(
                "'revision'",
                "'active_release_id'",
                "'query_data_source_id'",
                "'data_source_id'",
                "'order_key'")) {
            assertTrue(sql.contains(column), "V030 must add " + column);
        }
        assertTrue(sql.contains("snapshot_document longtext NOT NULL"));
        assertTrue(sql.contains("content_hash varchar(64) NOT NULL"));
    }

    /**
     * V031 UI 扩展注册表迁移应创建清单表与版本绑定。
     *
     * <p>断言 SQL 含 ui_extension_definition 建表语句、5 个版本相关列，
     * 以及唯一键约束(extension_type, extension_key, version, deleted)。</p>
     */
    @Test
    void uiExtensionRegistryMigrationCreatesManifestAndVersionBindings()
            throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V031__add_ui_extension_registry.sql");
        assertTrue(Files.exists(migration),
                "V031 UI extension registry migration must exist");
        String sql = Files.readString(migration);
        assertTrue(sql.contains(
                "CREATE TABLE IF NOT EXISTS ui_extension_definition"));
        for (String column : List.of(
                "'custom_component_version'",
                "'custom_component_snapshot_version'",
                "'component_name'",
                "'component_version'",
                "'snapshot_version'")) {
            assertTrue(sql.contains(column), "V031 must add " + column);
        }
        assertTrue(sql.contains(
                "UNIQUE KEY uk_ui_extension_version "
                        + "(extension_type, extension_key, version, deleted)"));
    }
}
