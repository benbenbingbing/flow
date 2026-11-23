package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Test
    void bpmnOnlySelectionChecksOwnerInTargetWithoutTreatingItAsAProcessCall() {
        List<Map<String, Object>> dependencies = selectedBpmnDependencies("z_child");
        var owner = dependencies.stream().filter(dependency -> "a_parent".equals(dependency.get("key")))
                .findFirst().orElseThrow();
        assertEquals(Boolean.TRUE, owner.get("targetOnly"));
        assertEquals(Set.of("z_child"), processCalls(dependencies));
    }

    @Test
    void bpmnOnlySelectionStillRejectsSelfCallWhenItSharesTheOwnerDependency() {
        Set<String> calls = processCalls(selectedBpmnDependencies("a_parent"));
        assertEquals(Set.of("a_parent"), calls);
        assertThrows(IllegalArgumentException.class, () ->
                ConfigMigrationImportApplyService.processPublicationOrder(Map.of("a_parent", calls)));
    }

    /** 使用实际 BPMN 分区投影，验证包内生成的归属依赖及同键合并。 */
    private List<Map<String, Object>> selectedBpmnDependencies(String calledKey) {
        var codec = new ConfigMigrationPackageCodec(new ObjectMapper(), null);
        var snapshot = Map.<String, Object>of(
                "assetType", "PROCESS",
                "businessKey", "a_parent",
                "definition", Map.of("processKey", "a_parent"),
                "bpmnXml", "<callActivity id=\"call\" calledElement=\"" + calledKey + "\"/>",
                "dependencies", List.of(Map.of("type", "PROCESS", "key", calledKey, "required", true,
                        "source", ConfigMigrationAssetService.CALLED_PROCESS_DEPENDENCY_SOURCE)));
        var selected = codec.selectSnapshot(snapshot, Map.of("full", false, "sections", List.of("bpmnXml")));
        return ConfigMigrationAssignmentSupport.maps(selected.get("dependencies"));
    }

    private Set<String> processCalls(List<Map<String, Object>> dependencies) {
        return dependencies.stream().filter(ConfigMigrationImportApplyService::isProcessCallDependency)
                .map(dependency -> (String) dependency.get("key")).collect(Collectors.toSet());
    }
}
