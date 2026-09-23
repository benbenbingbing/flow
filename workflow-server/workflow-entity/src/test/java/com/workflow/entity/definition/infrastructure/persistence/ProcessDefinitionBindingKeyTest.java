package com.workflow.entity.definition.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProcessDefinitionBindingKeyTest {
    @Test void acceptsPositiveBigintAliasesWithoutLosingPrecision() {
        assertEquals(12L, ProcessDefinitionBindingKey.parse(" 00012 "));
        assertEquals(9007199254740993L, ProcessDefinitionBindingKey.parse("9007199254740993"));
        assertEquals(Long.MAX_VALUE, ProcessDefinitionBindingKey.parse("0009223372036854775807"));
        assertEquals(1L, ProcessDefinitionBindingKey.parse("0".repeat(1000) + "1"));
    }

    @Test void rejectsMalformedZeroNegativeAndOverflowValuesInsteadOfCoercingThem() {
        assertNull(ProcessDefinitionBindingKey.parse(null));
        for (String input : new String[]{"", " ", "0", "0000", "-1", "+12", "12abc", "1e2", "12.0", "1 2",
                "１２", "١٢", "12' OR 1=1 --", "9223372036854775808", "18446744073709551615", "9".repeat(1000)}) {
            assertNull(ProcessDefinitionBindingKey.parse(input), input);
        }
    }
}
