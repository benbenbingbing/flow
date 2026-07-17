package com.workflow.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityPhysicalTableNamingTest {

    private final EntityPhysicalTableNaming naming = new EntityPhysicalTableNaming();

    @Test
    void shouldGenerateBizPrefixedSnakeCaseName() {
        assertEquals("biz_expense_application", naming.generate("ExpenseApplication"));
        assertEquals("biz_expense_application", naming.generate("expense_application"));
    }

    @Test
    void shouldGenerateStableNameWithinMysqlIdentifierLimit() {
        String entityCode = "very_long_entity_code_".repeat(5);

        String first = naming.generate(entityCode);
        String second = naming.generate(entityCode);

        assertEquals(first, second);
        assertTrue(first.startsWith("biz_"));
        assertTrue(first.length() <= 64);
    }

    @Test
    void shouldRejectReservedConfigurationTableNames() {
        assertThrows(
                IllegalArgumentException.class,
                () -> naming.validateStoredName("entity_definition"));
        assertThrows(
                IllegalArgumentException.class,
                () -> naming.validateStoredName("process_action"));
    }

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
