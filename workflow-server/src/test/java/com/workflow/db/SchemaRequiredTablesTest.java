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

class SchemaRequiredTablesTest {

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

    @Test
    void baselineMigrationCreatesEntityRelationTable() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V001__business_schema.sql");

        assertTrue(Files.exists(migration),
                "baseline migration must create entity_relation");

        String sql = Files.readString(migration);
        assertTrue(sql.contains("CREATE TABLE `entity_relation`"),
                "baseline migration must create entity_relation");
    }

    @Test
    void seedMigrationCreatesRunnableDemoData() throws Exception {
        Path v001 = Path.of("src/main/resources/db/migration/V001__business_schema.sql");
        assertTrue(Files.exists(v001),
                "V001 business schema migration must exist");
        String sql = Files.readString(v001);
        assertTrue(sql.contains("entity_data"),
                "V001 must create the entity_data table");
        assertTrue(sql.contains("process_definition_config"),
                "V001 must create the process_definition_config table");
    }

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
    }

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
}
