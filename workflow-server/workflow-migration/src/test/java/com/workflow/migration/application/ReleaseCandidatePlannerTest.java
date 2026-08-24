package com.workflow.migration.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseCandidatePlannerTest {

    private final ReleaseCandidatePlanner planner = new ReleaseCandidatePlanner();

    @Test
    void createsCanonicalApplicationStepOrder() {
        List<ReleaseCandidatePlanner.ItemNode> items = List.of(
                new ReleaseCandidatePlanner.ItemNode("process", "PROCESS", "approval", 50),
                new ReleaseCandidatePlanner.ItemNode("entity", "ENTITY", "expense", 10),
                new ReleaseCandidatePlanner.ItemNode("dict", "DICTIONARY", "expense_type", 70));

        ReleaseCandidatePlanner.PlanResult result = planner.plan(items, List.of());

        assertFalse(result.hasCycle());
        assertEquals(List.of("entity", "process", "dict"), result.orderedItemIds());
        assertEquals(List.of(
                        "VERIFY_ENTITY_SCHEMA",
                        "VERIFY_ENTITY_CONFIG",
                        "VERIFY_FORM_LIST",
                        "VERIFY_DATA_SCOPE",
                        "VERIFY_PROCESS",
                        "VERIFY_MENU_PERMISSION",
                        "VERIFY_EXTERNAL_DEPENDENCY",
                        "DEPLOY_IMPORT_PACKAGE",
                        "FINALIZE_REPORT"),
                result.steps().stream().map(ReleaseCandidatePlanner.PlannedStep::stepType).toList());
    }

    @Test
    void dependencyCycleBlocksDagPlan() {
        List<ReleaseCandidatePlanner.ItemNode> items = List.of(
                new ReleaseCandidatePlanner.ItemNode("a", "ENTITY", "a", 10),
                new ReleaseCandidatePlanner.ItemNode("b", "PROCESS", "b", 50));
        List<ReleaseCandidatePlanner.DependencyEdge> dependencies = List.of(
                new ReleaseCandidatePlanner.DependencyEdge("a", "b"),
                new ReleaseCandidatePlanner.DependencyEdge("b", "a"));

        ReleaseCandidatePlanner.PlanResult result = planner.plan(items, dependencies);

        assertTrue(result.hasCycle());
        assertEquals(List.of("a", "b"), result.cycleItemIds());
    }

    @Test
    void hardDependencyOverridesDefaultAssetPriority() {
        List<ReleaseCandidatePlanner.ItemNode> items = List.of(
                new ReleaseCandidatePlanner.ItemNode("entity", "ENTITY", "expense", 10),
                new ReleaseCandidatePlanner.ItemNode("dictionary", "DICTIONARY", "expense_type", 70));

        ReleaseCandidatePlanner.PlanResult result = planner.plan(
                items,
                List.of(new ReleaseCandidatePlanner.DependencyEdge("entity", "dictionary")));

        assertEquals(List.of("dictionary", "entity"), result.orderedItemIds());
    }
}
