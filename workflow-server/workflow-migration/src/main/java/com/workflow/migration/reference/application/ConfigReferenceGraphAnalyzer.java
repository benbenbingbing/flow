package com.workflow.migration.reference.application;

import com.workflow.migration.reference.application.ConfigurationReferenceModels.ImpactReport;
import com.workflow.migration.reference.application.ConfigurationReferenceModels.ReferenceEdge;
import com.workflow.migration.reference.application.ConfigurationReferenceModels.ReferenceNode;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 版本感知的有界配置引用图遍历器。 */
@Component
public class ConfigReferenceGraphAnalyzer {

    private static final int MAX_NODES = 1_000;

    public ImpactReport analyze(
            String focusType,
            String focusKey,
            String direction,
            int requestedDepth,
            List<ReferenceEdge> edges) {
        String effectiveDirection = direction == null ? "BOTH" : direction.trim().toUpperCase();
        if (!Set.of("UPSTREAM", "DOWNSTREAM", "BOTH").contains(effectiveDirection)) {
            throw new IllegalArgumentException("引用分析方向只能是 UPSTREAM、DOWNSTREAM 或 BOTH");
        }
        int maxDepth = Math.max(1, Math.min(requestedDepth <= 0 ? 8 : requestedDepth, 20));
        String root = focusType + ":" + focusKey;
        Map<String, List<Traversal>> graph = graph(edges, effectiveDirection);
        ArrayDeque<State> queue = new ArrayDeque<>();
        queue.add(new State(root, 0, "ROOT", List.of(root), null));
        Map<String, ReferenceNode> nodes = new LinkedHashMap<>();
        Set<String> edgeIds = new LinkedHashSet<>();
        List<List<String>> cycles = new ArrayList<>();
        boolean truncated = false;
        while (!queue.isEmpty()) {
            State state = queue.removeFirst();
            if (state.depth() >= maxDepth) continue;
            for (Traversal traversal : graph.getOrDefault(state.nodeKey(), List.of())) {
                edgeIds.add(traversal.edge().id());
                if (state.path().contains(traversal.nextKey())) {
                    List<String> cycle = new ArrayList<>(state.path());
                    cycle.add(traversal.nextKey());
                    cycles.add(List.copyOf(cycle));
                    continue;
                }
                if (nodes.size() >= MAX_NODES) {
                    truncated = true;
                    queue.clear();
                    break;
                }
                int split = traversal.nextKey().indexOf(':');
                String type = traversal.nextKey().substring(0, split);
                String key = traversal.nextKey().substring(split + 1);
                int depth = state.depth() + 1;
                ReferenceNode current = nodes.get(traversal.nextKey());
                if (current == null || depth < current.depth()) {
                    nodes.put(traversal.nextKey(), new ReferenceNode(
                            type, key, depth, traversal.edge().strength(),
                            traversal.edge().sourceVersion()));
                }
                List<String> path = new ArrayList<>(state.path());
                path.add(traversal.nextKey());
                queue.addLast(new State(
                        traversal.nextKey(), depth, traversal.edge().strength(),
                        List.copyOf(path), traversal.edge().sourceVersion()));
            }
        }
        List<ReferenceEdge> affected = (edges == null ? List.<ReferenceEdge>of() : edges).stream()
                .filter(edge -> edgeIds.contains(edge.id())).toList();
        List<ReferenceEdge> unknown = affected.stream()
                .filter(edge -> !"RESOLVED".equalsIgnoreCase(edge.parseStatus())).toList();
        boolean blocked = affected.stream().anyMatch(edge -> edge.required()
                || "HARD".equalsIgnoreCase(edge.strength()));
        return new ImpactReport(
                focusType, focusKey, effectiveDirection, List.copyOf(nodes.values()), affected,
                List.copyOf(cycles), unknown, blocked, truncated);
    }

    private Map<String, List<Traversal>> graph(List<ReferenceEdge> edges, String direction) {
        Map<String, List<Traversal>> graph = new HashMap<>();
        for (ReferenceEdge edge : edges == null ? List.<ReferenceEdge>of() : edges) {
            if ("DOWNSTREAM".equals(direction) || "BOTH".equals(direction)) {
                graph.computeIfAbsent(edge.sourceNodeKey(), ignored -> new ArrayList<>())
                        .add(new Traversal(edge, edge.targetNodeKey()));
            }
            if ("UPSTREAM".equals(direction) || "BOTH".equals(direction)) {
                graph.computeIfAbsent(edge.targetNodeKey(), ignored -> new ArrayList<>())
                        .add(new Traversal(edge, edge.sourceNodeKey()));
            }
        }
        return graph;
    }

    private record Traversal(ReferenceEdge edge, String nextKey) {
    }

    private record State(
            String nodeKey,
            int depth,
            String reachedBy,
            List<String> path,
            Integer sourceVersion) {
    }
}
