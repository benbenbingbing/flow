package com.workflow.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实体数据权限安全迁移脚本单元测试。
 *
 * <p>验证 V016 迁移脚本禁用不安全的旧版规则(CUSTOM_SQL、EXPRESSION)，
 * 规范化时间戳列名，并添加运行时索引。</p>
 */
class EntityDataPermissionSecurityMigrationTest {

    /**
     * 迁移脚本应禁用不安全的旧版规则并添加运行时索引。
     *
     * <p>断言 SQL 含 SET enabled=0、CUSTOM_SQL、EXPRESSION、字段映射路径、
     * 列名规范化(created_at→create_time)与运行时索引名。</p>
     */
    @Test
    void migrationDisablesUnsafeLegacyRulesAndAddsRuntimeIndex() throws IOException {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V016__secure_entity_list_permission.sql"));

        assertTrue(sql.contains("SET enabled = 0"));
        assertTrue(sql.contains("'CUSTOM_SQL'"));
        assertTrue(sql.contains("'EXPRESSION'"));
        assertTrue(sql.contains("'$.fieldMapping.userField'"));
        assertTrue(sql.contains("CHANGE COLUMN created_at create_time"));
        assertTrue(sql.contains("CHANGE COLUMN updated_at update_time"));
        assertTrue(sql.contains("idx_entity_permission_runtime"));
    }
}
