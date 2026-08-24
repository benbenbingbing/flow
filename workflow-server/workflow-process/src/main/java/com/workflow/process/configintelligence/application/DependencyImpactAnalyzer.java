package com.workflow.process.configintelligence.application;

import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.AssetRef;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.DependencyEdge;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.ImpactNode;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.ImpactReport;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 有界配置依赖图分析器，支持上下游、环检测和变更风险分级。 */
@Component
public class DependencyImpactAnalyzer {

    private static final int MAX_NODES = 500;

    public ImpactReport analyze(
            AssetRef focus,
            List<DependencyEdge> edges,
            String direction,
            int requestedDepth) {
        String effectiveDirection = direction == null ? "BOTH" : direction.toUpperCase();
        int maxDepth = Math.max(1, Math.min(requestedDepth <= 0 ? 6 : requestedDepth, 12));
        Map<String, List<Traversal>> graph = graph(edges, effectiveDirection);
        ArrayDeque<State> queue = new ArrayDeque<>();
        queue.add(new State(focus, 0, "ROOT", List.of(focus.key())));
        Map<String, ImpactNode> nodes = new LinkedHashMap<>();
        Set<String> affectedEdgeIds = new LinkedHashSet<>();
        List<List<String>> cycles = new ArrayList<>();
        boolean truncated = false;

        while (!queue.isEmpty()) {
            State state = queue.removeFirst();
            if (state.depth > 0) {
                ImpactNode previous = nodes.get(state.asset.key());
                if (previous == null || state.depth < previous.depth()) {
                    nodes.put(state.asset.key(), new ImpactNode(state.asset, state.depth, state.reachedBy));
                }
            }
            if (state.depth >= maxDepth) {
                continue;
            }
            for (Traversal traversal : graph.getOrDefault(state.asset.key(), List.of())) {
                affectedEdgeIds.add(traversal.edge.id());
                if (state.path.contains(traversal.next.key())) {
                    List<String> cycle = new ArrayList<>(state.path);
                    cycle.add(traversal.next.key());
                    cycles.add(List.copyOf(cycle));
                    continue;
                }
                if (nodes.size() >= MAX_NODES) {
                    truncated = true;
                    queue.clear();
                    break;
                }
                List<String> path = new ArrayList<>(state.path);
                path.add(traversal.next.key());
                queue.addLast(new State(
                        traversal.next,
                        state.depth + 1,
                        traversal.edge.relationType(),
                        List.copyOf(path)));
            }
        }

        List<DependencyEdge> affectedEdges = edges.stream()
                .filter(edge -> affectedEdgeIds.contains(edge.id()))
                .toList();
        long requiredEdges = affectedEdges.stream().filter(DependencyEdge::required).count();
        long crossTypeNodes = nodes.values().stream()
                .filter(node -> !node.asset().type().equals(focus.type()))
                .count();
        int riskScore = Math.min(100,
                nodes.size() * 4 + (int) requiredEdges * 5 + (int) crossTypeNodes * 3 + cycles.size() * 15);
        String riskLevel = riskScore >= 70 ? "CRITICAL" : riskScore >= 40 ? "HIGH"
                : riskScore >= 20 ? "MEDIUM" : "LOW";
        List<String> recommendations = new ArrayList<>();
        if (!nodes.isEmpty()) recommendations.add("对 " + nodes.size() + " 个受影响资产执行统一配置回归套件");
        if (requiredEdges > 0) recommendations.add("优先验证 " + requiredEdges + " 条强依赖关系");
        if (!cycles.isEmpty()) recommendations.add("解除或记录审批依赖环，避免发布顺序不可判定");
        if (truncated) recommendations.add("影响图超过安全上限，请按业务域拆分后继续分析");
        return new ImpactReport(
                focus, effectiveDirection, List.copyOf(nodes.values()), affectedEdges,
                deduplicateCycles(cycles), riskScore, riskLevel, List.copyOf(recommendations), truncated);
    }

    private Map<String, List<Traversal>> graph(List<DependencyEdge> edges, String direction) {
        Map<String, List<Traversal>> graph = new HashMap<>();
        for (DependencyEdge edge : edges == null ? List.<DependencyEdge>of() : edges) {
            if ("DOWNSTREAM".equals(direction) || "BOTH".equals(direction)) {
                graph.computeIfAbsent(edge.source().key(), ignored -> new ArrayList<>())
                        .add(new Traversal(edge, edge.target()));
            }
            if ("UPSTREAM".equals(direction) || "BOTH".equals(direction)) {
                graph.computeIfAbsent(edge.target().key(), ignored -> new ArrayList<>())
                        .add(new Traversal(edge, edge.source()));
            }
        }
        return graph;
    }

    private List<List<String>> deduplicateCycles(List<List<String>> cycles) {
        Set<String> seen = new HashSet<>();
        List<List<String>> result = new ArrayList<>();
        for (List<String> cycle : cycles) {
            String signature = String.join("->", cycle);
            if (seen.add(signature)) result.add(cycle);
        }
        return List.copyOf(result);
    }

    private record Traversal(DependencyEdge edge, AssetRef next) {
    }

    private record State(AssetRef asset, int depth, String reachedBy, List<String> path) {
    }
}
