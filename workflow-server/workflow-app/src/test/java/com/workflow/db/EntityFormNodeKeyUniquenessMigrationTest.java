package com.workflow.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实体表单节点 Key 唯一性迁移脚本单元测试。
 *
 * <p>验证 V033 迁移脚本对活动记录(非删除)施加唯一约束、
 * 添加约束前检测重复、以及迁移可安全重试。</p>
 */
class EntityFormNodeKeyUniquenessMigrationTest {

    /** V033 迁移脚本路径 */
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V033__enforce_active_form_node_key_uniqueness.sql");

    /**
     * 迁移应仅对活动(非删除)的 node_key 施加唯一约束。
     *
     * <p>断言 SQL 含 WHEN deleted=0 条件、唯一键名与列组合，
     * 不含旧的全字段组合唯一键。</p>
     */
    @Test
    void migrationConstrainsOnlyActiveNodeKeys() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains(
                "WHEN deleted = 0 THEN node_key"));
        assertTrue(sql.contains(
                "ADD UNIQUE KEY uk_entity_form_node_active_key"));
        assertTrue(sql.contains(
                "(form_id, active_node_key)"));
        assertFalse(sql.contains(
                "UNIQUE KEY uk_entity_form_node_active_key "
                        + "(form_id, node_key, deleted)"));
    }

    /**
     * 迁移应在添加唯一约束前检测并报告重复数据。
     *
     * <p>断言 SQL 中重复检测(HAVING COUNT>1)、重复报告与唯一约束语句按顺序出现。</p>
     */
    @Test
    void migrationDetectsDuplicatesBeforeAddingConstraint() throws Exception {
        String sql = Files.readString(MIGRATION);
        int duplicateDetection = sql.indexOf(
                "HAVING COUNT(*) > 1");
        int duplicateReport = sql.indexOf(
                "V033 active node_key duplicates");
        int uniqueConstraint = sql.indexOf(
                "ADD UNIQUE KEY uk_entity_form_node_active_key");

        assertTrue(duplicateDetection >= 0);
        assertTrue(duplicateReport > duplicateDetection);
        assertTrue(uniqueConstraint > duplicateReport);
    }

    /**
     * 迁移应在部分 DDL 执行后可安全重试。
     *
     * <p>断言 SQL 含 information_schema 列与索引检查、活动列与索引计数判断，
     * 以及 DROP PROCEDURE IF EXISTS 语句。</p>
     */
    @Test
    void migrationIsSafeToRetryAfterPartialDdl() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains(
                "information_schema.COLUMNS"));
        assertTrue(sql.contains(
                "information_schema.STATISTICS"));
        assertTrue(sql.contains(
                "active_column_count = 0"));
        assertTrue(sql.contains(
                "active_index_count > 0"));
        assertTrue(sql.contains(
                "DROP PROCEDURE IF EXISTS "
                        + "workflow_enforce_active_form_node_key_uniqueness"));
    }
}
