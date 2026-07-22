package com.workflow.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityFormNodeKeyUniquenessMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V033__enforce_active_form_node_key_uniqueness.sql");

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
