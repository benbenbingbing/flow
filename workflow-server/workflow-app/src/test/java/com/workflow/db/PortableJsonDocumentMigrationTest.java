package com.workflow.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortableJsonDocumentMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V025__portable_json_document_storage.sql");

    @Test
    void migrationShouldCreateRelationalConfigurationTables() throws Exception {
        String sql = Files.readString(MIGRATION);

        for (String table : List.of(
                "entity_list_action",
                "entity_list_scene",
                "entity_field_option",
                "process_node_approval_option",
                "process_action_definition_entity",
                "config_migration_asset_dependency",
                "process_task_candidate_user",
                "process_task_candidate_group")) {
            assertTrue(
                    sql.contains("CREATE TABLE IF NOT EXISTS `" + table + "`"),
                    "V025 缺少关系化配置表: " + table);
        }
    }

    @Test
    void migrationShouldConvertNativeJsonAndExcludeFlowableTables() throws Exception {
        String sql = Files.readString(MIGRATION);
        String normalizedSql = sql.toLowerCase();

        assertTrue(normalizedSql.contains("data_type = 'json'"));
        assertTrue(normalizedSql.contains("table_name not like 'act\\\\_%'"));
        assertTrue(normalizedSql.contains("modify column"));
        assertTrue(normalizedSql.contains("longtext"));
        assertTrue(normalizedSql.contains("remaining_native_json"));
    }

    @Test
    void dynamicTableRuntimeShouldNotCreateNativeJsonColumns() throws Exception {
        String source = Files.readString(Path.of(
                "../workflow-entity/src/main/java/com/workflow/service/DynamicTableService.java"));

        assertFalse(source.contains("return \"JSON\""));
        assertTrue(source.contains("isMultiValueField(field)"));
        assertTrue(source.contains("return \"LONGTEXT\""));
        assertTrue(source.contains("ensureMultiValueTable(tableName)"));
    }
}
