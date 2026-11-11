package com.workflow.migration.application;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 覆盖同包父子流程编码倒序、多层调用和循环依赖的发布计划。 */
class ConfigMigrationProcessPublicationOrderTest {
    @Test
    void entityRelationsPublishChildrenBeforeParentsAndRejectCycles() {
        assertEquals(List.of("z_child", "a_parent"), ConfigMigrationImportApplyService.publicationOrder(
                Map.of("a_parent", Set.of("z_child"), "z_child", Set.of()), "实体关系"));
        assertThrows(IllegalArgumentException.class, () -> ConfigMigrationImportApplyService.publicationOrder(
                Map.of("a", Set.of("b"), "b", Set.of("a")), "实体关系"));
    }

    @Test
    void publishesChildBeforeAlphabeticallyEarlierParentAndReusesExternalChild() {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        graph.put("a_parent", Set.of("z_child", "already_published"));
        graph.put("z_child", Set.of());
        assertEquals(List.of("z_child", "a_parent"),
                ConfigMigrationImportApplyService.processPublicationOrder(graph));
    }

    @Test
    void sharedGrandchildIsPublishedOnceBeforeBothParents() {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        graph.put("a_root", Set.of("b_left", "c_right"));
        graph.put("b_left", Set.of("z_leaf"));
        graph.put("c_right", Set.of("z_leaf"));
        graph.put("z_leaf", Set.of());
        assertEquals(List.of("z_leaf", "b_left", "c_right", "a_root"),
                ConfigMigrationImportApplyService.processPublicationOrder(graph));
    }

    @Test
    void rejectsCycleAndSelfCallWithoutReturningPartialPlan() {
        assertThrows(IllegalArgumentException.class, () ->
                ConfigMigrationImportApplyService.processPublicationOrder(Map.of(
                        "parent", Set.of("child"), "child", Set.of("parent"))));
        assertThrows(IllegalArgumentException.class, () ->
                ConfigMigrationImportApplyService.processPublicationOrder(Map.of("self", Set.of("self"))));
        assertEquals(List.of(), ConfigMigrationImportApplyService.processPublicationOrder(Map.of()));
    }
}
