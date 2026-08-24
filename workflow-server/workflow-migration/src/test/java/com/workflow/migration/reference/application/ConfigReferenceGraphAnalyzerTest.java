package com.workflow.migration.reference.application;

import com.workflow.migration.reference.application.ConfigurationReferenceModels.ReferenceEdge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigReferenceGraphAnalyzerTest {

    private final ConfigReferenceGraphAnalyzer analyzer = new ConfigReferenceGraphAnalyzer();

    @Test
    void tracesForwardReverseCyclesAndUnknownReferences() {
        List<ReferenceEdge> edges = List.of(
                edge("1", "ENTITY", "customer", "FORM", "customer-edit", true, "RESOLVED"),
                edge("2", "FORM", "customer-edit", "PROCESS", "approval", true, "UNKNOWN"),
                edge("3", "PROCESS", "approval", "ENTITY", "customer", false, "RESOLVED")
        );

        var report = analyzer.analyze("ENTITY", "customer", "BOTH", 8, edges);

        assertEquals(2, report.nodes().size());
        assertFalse(report.cycles().isEmpty());
        assertEquals(1, report.unknownReferences().size());
        assertTrue(report.hardDependencyBlocked());
        assertFalse(report.truncated());
    }

    @Test
    void respectsDepthBound() {
        List<ReferenceEdge> edges = List.of(
                edge("1", "A", "1", "B", "2", true, "RESOLVED"),
                edge("2", "B", "2", "C", "3", true, "RESOLVED"));
        var report = analyzer.analyze("A", "1", "DOWNSTREAM", 1, edges);
        assertEquals(List.of("2"), report.nodes().stream().map(node -> node.key()).toList());
    }

    private ReferenceEdge edge(
            String id,
            String sourceType,
            String sourceKey,
            String targetType,
            String targetKey,
            boolean required,
            String parseStatus) {
        return new ReferenceEdge(id, sourceType, sourceKey, 3, targetType, targetKey,
                required, required ? "HARD" : "SOFT", "$/test", parseStatus);
    }
}
