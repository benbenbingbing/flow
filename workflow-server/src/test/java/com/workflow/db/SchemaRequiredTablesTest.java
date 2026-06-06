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
    void schemaDefinesTablesUsedByRoleMenuAndNodeFormMappers() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));

        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS `sys_role_menu`"),
                "schema.sql must define sys_role_menu used by SysRoleMenuMapper");
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS `process_node_form`"),
                "schema.sql must define process_node_form used by ProcessNodeFormMapper");
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS `process_node_approval`"),
                "schema.sql must define process_node_approval used by ProcessNodeApprovalMapper");
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS `process_cc_record`"),
                "schema.sql must define process_cc_record used by ProcessCcRecordMapper");
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS `entity_relation`"),
                "schema.sql must define entity_relation used by EntityRelationMapper");
    }

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
        Path migration = Path.of("src/main/resources/db/migration/V002__seed_builtin_data.sql");

        assertTrue(Files.exists(migration),
                "seed migration must provide built-in demo data");

        String sql = Files.readString(migration);
        assertTrue(sql.contains("full_test_process"),
                "seed migration must include the full test process");
        assertTrue(sql.contains("seed_demo_request_form"),
                "seed migration must include a demo form");
        assertTrue(sql.contains("INSERT INTO `process_node_form`"),
                "seed migration must include node form bindings");
        assertTrue(sql.contains("INSERT INTO `process_node_approval`"),
                "seed migration must include approval settings");
        assertTrue(sql.contains("INSERT INTO `process_version_history`"),
                "seed migration must include a published version record");
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
}
