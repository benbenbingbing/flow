package com.workflow.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityCodeRuleTest {

    @Test
    void defaultPrefixKeepsShortEntityCodeReadable() {
        assertEquals("EXPENSE_APPLY", EntityCodeRule.defaultPrefix("expense_apply"));
    }

    @Test
    void defaultPrefixShortensLongEntityCodeDeterministically() {
        String entityCode = "cfg_entity_26071503339b5";

        String prefix = EntityCodeRule.defaultPrefix(entityCode);

        assertEquals(prefix, EntityCodeRule.defaultPrefix(entityCode));
        assertEquals(EntityCodeRule.MAX_PREFIX_LENGTH, prefix.length());
        assertTrue(prefix.startsWith("CFG_ENTITY_"));
    }

    @Test
    void longEntityCodesWithSameReadableStartUseDifferentSuffixes() {
        String first = EntityCodeRule.defaultPrefix("very_long_entity_code_alpha");
        String second = EntityCodeRule.defaultPrefix("very_long_entity_code_beta");

        assertNotEquals(first, second);
        assertTrue(first.length() <= EntityCodeRule.MAX_PREFIX_LENGTH);
        assertTrue(second.length() <= EntityCodeRule.MAX_PREFIX_LENGTH);
    }
}
