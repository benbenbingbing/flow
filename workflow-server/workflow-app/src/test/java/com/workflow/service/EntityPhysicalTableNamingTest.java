package com.workflow.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实体物理表命名服务测试。
 *
 * <p>被测对象：{@link EntityPhysicalTableNaming}，覆盖表名生成（biz 前缀 + 蛇形）、
 * MySQL 标识符长度限制内的稳定性、保留配置表名拒绝、遗留表运行期拒绝但允许迁移等场景。
 */
class EntityPhysicalTableNamingTest {

    /** 被测命名服务 */
    private final EntityPhysicalTableNaming naming = new EntityPhysicalTableNaming();

    /** 测试生成 biz 前缀的蛇形表名：验证驼峰与下划线输入产出一致 */
    @Test
    void shouldGenerateBizPrefixedSnakeCaseName() {
        assertEquals("biz_expense_application", naming.generate("ExpenseApplication"));
        assertEquals("biz_expense_application", naming.generate("expense_application"));
    }

    /** 测试在 MySQL 标识符长度限制（64）内生成稳定表名：验证重复生成结果一致且不超长 */
    @Test
    void shouldGenerateStableNameWithinMysqlIdentifierLimit() {
        String entityCode = "very_long_entity_code_".repeat(5);

        String first = naming.generate(entityCode);
        String second = naming.generate(entityCode);

        assertEquals(first, second);
        assertTrue(first.startsWith("biz_"));
        assertTrue(first.length() <= 64);
    }

    /** 测试拒绝保留的配置表名：验证对系统配置表名校验抛出 IllegalArgumentException */
    @Test
    void shouldRejectReservedConfigurationTableNames() {
        assertThrows(
                IllegalArgumentException.class,
                () -> naming.validateStoredName("entity_definition"));
        assertThrows(
                IllegalArgumentException.class,
                () -> naming.validateStoredName("process_action"));
    }

    /** 测试运行期拒绝遗留表但允许迁移：验证存储校验抛异常，迁移校验放行 */
    @Test
    void shouldRejectLegacyTableAtRuntimeButAllowMigration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> naming.validateStoredName("entity_data_order"));
        assertEquals(
                "entity_data_order",
                naming.validateMigrationName("entity_data_order"));
    }
}
