package com.workflow.process.assignment.relative;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RelativeOrgPositionConfigTest {

    @Test
    void businessLevelUsesTheGlobalSnapshotDepthAndCanonicalCodes() {
        RelativeOrgPositionConfig config = RelativeOrgPositionConfig.parse(
                Map.of(
                        "schemaVersion", 1,
                        "subject", "process_initiator",
                        "anchor", "department",
                        "positionCode", "unit_leader",
                        "hierarchy", Map.of(
                                "mode", "business_level",
                                "businessLevelCode", "first_level_dept"),
                        "multipleMatchPolicy", "error"));

        assertEquals("UNIT_LEADER", config.positionCode());
        assertEquals(
                "FIRST_LEVEL_DEPT",
                config.hierarchy().businessLevelCode());
        assertEquals(
                RelativeOrgPositionConfig.MAX_CHAIN_DEPTH,
                config.hierarchy().maxHops());
        assertEquals(
                java.util.Set.of("dept"),
                config.hierarchy().eligibleUnitTypes());
    }

    @Test
    void businessLevelRejectsTheNonContractualMaxHopsField() {
        Map<String, Object> hierarchy = new java.util.LinkedHashMap<>();
        hierarchy.put("mode", "BUSINESS_LEVEL");
        hierarchy.put("businessLevelCode", "FIRST_LEVEL_DEPT");
        hierarchy.put("maxHops", 8);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> RelativeOrgPositionConfig.parse(Map.of(
                        "schemaVersion", 1,
                        "subject", "PROCESS_INITIATOR",
                        "anchor", "DEPARTMENT",
                        "positionCode", "UNIT_LEADER",
                        "hierarchy", hierarchy,
                        "multipleMatchPolicy", "ERROR")));

        org.junit.jupiter.api.Assertions.assertTrue(
                failure.getMessage().contains("maxHops"));
    }

    @Test
    void assignmentModeAndMultiplePolicyMustMatch() {
        RelativeOrgPositionConfig directAll = RelativeOrgPositionConfig.parse(
                Map.of(
                        "schemaVersion", 1,
                        "subject", "PROCESS_INITIATOR",
                        "anchor", "DEPARTMENT",
                        "positionCode", "UNIT_LEADER",
                        "hierarchy", Map.of("mode", "SELF"),
                        "multipleMatchPolicy", "ALL"));

        assertThrows(
                IllegalArgumentException.class,
                () -> directAll.validateAssignmentMode("DIRECT", false));
        directAll.validateAssignmentMode(" candidate ", false);
        directAll.validateAssignmentMode("DIRECT", true);
    }
}
