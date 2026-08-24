package com.workflow.process.migration.application;

import com.workflow.process.migration.application.ProcessInstanceMigrationModels.SafetyInput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessMigrationSafetyPolicyTest {

    private final ProcessMigrationSafetyPolicy policy = new ProcessMigrationSafetyPolicy();

    @Test
    void allowsSimpleSameKeyUserTaskAndMarksItReversible() {
        var result = policy.assess(new SafetyInput(
                "expense", "expense", List.of("approve"), Map.of(),
                false, false, 0, 0));
        assertTrue(result.allowed());
        assertTrue(result.reversible());
        assertTrue(result.blockers().isEmpty());
    }

    @Test
    void blocksCrossProcessAndSuspendedInstances() {
        var result = policy.assess(new SafetyInput(
                "expense", "contract", List.of("approve"), Map.of(),
                true, false, 0, 0));
        assertFalse(result.allowed());
        assertTrue(result.blockers().size() >= 2);
    }

    @Test
    void multiInstanceAndPendingJobsDisableAutomaticRollback() {
        var result = policy.assess(new SafetyInput(
                "expense", "expense", List.of("joint-review"),
                Map.of("joint-review", "joint-review-v2"),
                false, true, 2, 1));
        assertTrue(result.allowed());
        assertFalse(result.reversible());
        assertTrue(result.warnings().size() >= 3);
    }
}
