package com.workflow.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 可移植 JSON 文档存储迁移脚本单元测试。
 *
 * <p>验证 V025 迁移创建关系化配置表、将原生 JSON 列转为 LONGTEXT(排除 Flowable 表)，
 * 以及动态表运行时不再创建原生 JSON 列。</p>
 */
class PortableJsonDocumentMigrationTest {

    /** V025 迁移脚本路径 */
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V025__portable_json_document_storage.sql");

    /**
     * 迁移应创建全部关系化配置表。
     *
     * <p>断言 SQL 含 8 张关系化配置表的 CREATE TABLE 语句。</p>
     */
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

    /**
     * 迁移应将原生 JSON 列转为 LONGTEXT 且排除 Flowable 表。
     *
     * <p>断言 SQL 含 data_type='json' 条件、排除 act_% 表、MODIFY COLUMN LONGTEXT，
     * 以及剩余原生 JSON 检查。</p>
     */
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

    /**
     * 动态表运行时应创建 LONGTEXT 而非原生 JSON 列。
     *
     * <p>断言 DynamicTableService 源码不含 return "JSON"，
     * 含 isMultiValueField 判断与 return "LONGTEXT" 及多值表保障逻辑。</p>
     */
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
