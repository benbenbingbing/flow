package com.workflow.process.migration.application;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessMigrationExecutionPolicyTest {

    private final ProcessMigrationExecutionPolicy policy = new ProcessMigrationExecutionPolicy();

    @Test
    void appliesFormReleaseMappingsToMigrationVariables() {
        Map<String, Object> variables = policy.migrationVariables(
                Map.of("riskLevel", "HIGH"),
                Map.of("release-v1", "release-v2"));

        assertEquals("HIGH", variables.get("riskLevel"));
        assertEquals(
                Map.of("release-v1", "release-v2"),
                variables.get(ProcessMigrationExecutionPolicy.FORM_RELEASE_MAPPING_VARIABLE));
    }

    @Test
    void protectsReservedVariableAndDefinesRetryBoundary() {
        assertThrows(IllegalArgumentException.class, () -> policy.migrationVariables(
                Map.of(ProcessMigrationExecutionPolicy.FORM_RELEASE_MAPPING_VARIABLE, Map.of()),
                Map.of()));
        assertTrue(policy.retryable("FAILED"));
        assertTrue(policy.retryable("blocked"));
        assertFalse(policy.retryable("SUCCESS"));
    }
}
