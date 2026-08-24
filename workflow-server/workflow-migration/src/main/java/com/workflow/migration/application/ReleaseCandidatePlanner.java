package com.workflow.migration.application;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 发布候选 DAG 与确定性步骤规划器。
 * 依赖边方向为“被依赖条目 -> 依赖方条目”，拓扑排序同优先级时按类型、业务键和 ID 稳定排序。
 */
@Component
public class ReleaseCandidatePlanner {

    private static final List<String> CANONICAL_STAGES = List.of(
            "ENTITY_SCHEMA",
            "ENTITY_CONFIG",
            "FORM_LIST",
            "DATA_SCOPE",
            "PROCESS",
            "MENU_PERMISSION",
            "EXTERNAL_DEPENDENCY");

    /** 根据资产与依赖边生成拓扑顺序和发布步骤。 */
    public PlanResult plan(List<ItemNode> items, List<DependencyEdge> dependencies) {
        Map<String, ItemNode> byId = new LinkedHashMap<>();
        for (ItemNode item : items) {
            byId.put(item.id(), item);
        }
        Map<String, Set<String>> outgoing = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        byId.keySet().forEach(id -> indegree.put(id, 0));
        for (DependencyEdge edge : dependencies) {
            if (edge.requiredItemId() == null
                    || !byId.containsKey(edge.requiredItemId())
                    || !byId.containsKey(edge.dependentItemId())
                    || edge.requiredItemId().equals(edge.dependentItemId())) {
                continue;
            }
            Set<String> targets = outgoing.computeIfAbsent(
                    edge.requiredItemId(), ignored -> new LinkedHashSet<>());
            if (targets.add(edge.dependentItemId())) {
                indegree.computeIfPresent(edge.dependentItemId(), (key, value) -> value + 1);
            }
        }

        Comparator<ItemNode> stableOrder = Comparator
                .comparingInt(ItemNode::sortOrder)
                .thenComparing(ItemNode::businessKey)
                .thenComparing(ItemNode::id);
        PriorityQueue<ItemNode> ready = new PriorityQueue<>(stableOrder);
        byId.values().stream()
                .filter(item -> indegree.get(item.id()) == 0)
                .forEach(ready::add);
        List<ItemNode> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            ItemNode current = ready.remove();
            ordered.add(current);
            for (String target : outgoing.getOrDefault(current.id(), Set.of())) {
                int remaining = indegree.computeIfPresent(target, (key, value) -> value - 1);
                if (remaining == 0) {
                    ready.add(byId.get(target));
                }
            }
        }
        Set<String> orderedIds = new HashSet<>();
        ordered.forEach(item -> orderedIds.add(item.id()));
        List<String> cycleItemIds = byId.values().stream()
                .filter(item -> !orderedIds.contains(item.id()))
                .sorted(stableOrder)
                .map(ItemNode::id)
                .toList();

        List<PlannedStep> steps = new ArrayList<>();
        int stepNo = 1;
        for (String stage : CANONICAL_STAGES) {
            for (ItemNode item : ordered) {
                if (stagesFor(item.assetType()).contains(stage)) {
                    steps.add(new PlannedStep(
                            stepNo++,
                            "VERIFY_" + stage + ":" + item.id(),
                            "VERIFY_" + stage,
                            item.id(),
                            item.assetType(),
                            item.businessKey()));
                }
            }
        }
        steps.add(new PlannedStep(
                stepNo++, "DEPLOY_IMPORT_PACKAGE", "DEPLOY_IMPORT_PACKAGE",
                null, "IMPORT_PACKAGE", "ALL"));
        steps.add(new PlannedStep(
                stepNo, "FINALIZE_REPORT", "FINALIZE_REPORT",
                null, "REPORT", "ALL"));
        return new PlanResult(
                ordered.stream().map(ItemNode::id).toList(),
                cycleItemIds,
                steps);
    }

    /** 资产类型的默认排序，仅作为无依赖冲突时的稳定 tie-breaker。 */
    public static int sortOrder(String assetType) {
        return switch (assetType == null ? "" : assetType) {
            case "ENTITY" -> 10;
            case "SYSTEM_ENTITY_UI" -> 30;
            case "PROCESS" -> 50;
            default -> 70;
        };
    }

    private Set<String> stagesFor(String assetType) {
        return switch (assetType == null ? "" : assetType) {
            case "ENTITY" -> Set.of(
                    "ENTITY_SCHEMA", "ENTITY_CONFIG", "FORM_LIST",
                    "DATA_SCOPE", "MENU_PERMISSION");
            case "SYSTEM_ENTITY_UI" -> Set.of("FORM_LIST");
            case "PROCESS" -> Set.of("PROCESS");
            default -> Set.of("EXTERNAL_DEPENDENCY");
        };
    }

    public record ItemNode(
            String id,
            String assetType,
            String businessKey,
            int sortOrder) {
    }

    public record DependencyEdge(
            String dependentItemId,
            String requiredItemId) {
    }

    public record PlannedStep(
            int stepNo,
            String stepKey,
            String stepType,
            String itemId,
            String assetType,
            String businessKey) {
    }

    public record PlanResult(
            List<String> orderedItemIds,
            List<String> cycleItemIds,
            List<PlannedStep> steps) {

        public boolean hasCycle() {
            return !cycleItemIds.isEmpty();
        }
    }
}
