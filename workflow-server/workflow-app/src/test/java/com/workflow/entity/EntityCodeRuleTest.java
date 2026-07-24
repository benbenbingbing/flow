package com.workflow.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实体编码规则单元测试。
 *
 * <p>被测对象为 {@link EntityCodeRule}，验证默认前缀生成规则：
 * 短编码原样保留、长编码确定性地截断为最大前缀长度，以及相同前缀不同后缀的区分。</p>
 */
class EntityCodeRuleTest {

    /** 短实体编码应原样返回大写形式作为前缀 */
    @Test
    void defaultPrefixKeepsShortEntityCodeReadable() {
        assertEquals("EXPENSE_APPLY", EntityCodeRule.defaultPrefix("expense_apply"));
    }

    /** 长实体编码应确定性地截断为最大前缀长度，且重复调用结果一致 */
    @Test
    void defaultPrefixShortensLongEntityCodeDeterministically() {
        String entityCode = "cfg_entity_26071503339b5";

        String prefix = EntityCodeRule.defaultPrefix(entityCode);

        assertEquals(prefix, EntityCodeRule.defaultPrefix(entityCode));
        assertEquals(EntityCodeRule.MAX_PREFIX_LENGTH, prefix.length());
        assertTrue(prefix.startsWith("CFG_ENTITY_"));
    }

    /** 可读前缀相同但后缀不同的长编码应生成不同的前缀，避免冲突 */
    @Test
    void longEntityCodesWithSameReadableStartUseDifferentSuffixes() {
        String first = EntityCodeRule.defaultPrefix("very_long_entity_code_alpha");
        String second = EntityCodeRule.defaultPrefix("very_long_entity_code_beta");

        assertNotEquals(first, second);
        assertTrue(first.length() <= EntityCodeRule.MAX_PREFIX_LENGTH);
        assertTrue(second.length() <= EntityCodeRule.MAX_PREFIX_LENGTH);
    }
}
