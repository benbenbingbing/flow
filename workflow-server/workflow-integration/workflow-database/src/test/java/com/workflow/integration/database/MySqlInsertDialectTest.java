package com.workflow.integration.database;

import com.workflow.integration.database.api.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MySqlInsertDialectTest {
    private final DatabaseInsertDialect dialect = DatabaseDialects.insert(DatabaseVendor.MYSQL);

    @Test
    void valuesAreBoundAndNullIsPreserved() {
        var values = new LinkedHashMap<String, Object>();
        values.put("id", "a'); DROP TABLE victim; --");
        values.put("note", null);
        var command = dialect.insert("biz_claim", values);
        assertEquals("INSERT INTO `biz_claim` (`id`, `note`) VALUES (?, ?)", command.sql());
        assertEquals(values.get("id"), command.parameters().get(0));
        assertNull(command.parameters().get(1));
        values.put("id", "changed");
        assertNotEquals("changed", command.parameters().get(0));
    }

    @Test
    void rejectsSqlNamesAndRepeatedPhysicalColumns() {
        assertThrows(IllegalArgumentException.class, () -> dialect.insert("biz_claim; DROP TABLE x", Map.of("id", 1)));
        assertThrows(IllegalArgumentException.class, () -> dialect.insert("biz_claim", Map.of("id) VALUES(1)--", 1)));
        assertThrows(IllegalArgumentException.class, () -> dialect.insert("biz_claim", Map.of("id", 1, "ID", 2)));
        assertThrows(IllegalArgumentException.class, () -> dialect.insert("biz_claim", Map.of()));
    }

    @Test
    void integrityCategoryAloneCannotMeanDuplicate() {
        assertTrue(dialect.isUniqueViolation("23000", 1062));
        for (int code : new int[]{1048, 1452, 3819, 1213, 1205, 0}) {
            assertFalse(dialect.isUniqueViolation("23000", code));
        }
        assertFalse(dialect.isUniqueViolation("23505", 0));
    }

    @Test
    void lockPlanBindsCompositeKeysInTheDeclaredOrderAndNeverOverwritesCounters() {
        var values = new LinkedHashMap<String, Object>();
        values.put("counter", 0); values.put("record_id", "'; --"); values.put("entity_code", "asset");
        var plan = dialect.rowLock("biz_counter", values, java.util.List.of("entity_code", "record_id"));
        assertTrue(plan.initialize().sql().endsWith("ON DUPLICATE KEY UPDATE `entity_code` = `entity_code`"));
        assertEquals("SELECT 1 FROM `biz_counter` WHERE `entity_code` = ? AND `record_id` = ? FOR UPDATE", plan.lock().sql());
        assertEquals(java.util.List.of("asset", "'; --"), plan.lock().parameters());
        assertFalse(plan.initialize().sql().contains("'; --"));
        assertFalse(plan.recoverInsertConflict());
    }

    @Test
    void lockKeyMustBeCompleteNonNullAndUnique() {
        assertThrows(IllegalArgumentException.class, () -> dialect.rowLock("biz_counter", Map.of("id", 1), java.util.List.of()));
        assertThrows(IllegalArgumentException.class, () -> dialect.rowLock("biz_counter", Map.of("id", 1), java.util.List.of("other")));
        assertThrows(IllegalArgumentException.class, () -> dialect.rowLock("biz_counter", Map.of("id", 1), java.util.List.of("id", "id")));
        var values = new LinkedHashMap<String, Object>(); values.put("id", null);
        assertThrows(IllegalArgumentException.class, () -> dialect.rowLock("biz_counter", values, java.util.List.of("id")));
    }
}
