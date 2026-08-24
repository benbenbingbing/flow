package com.workflow.entity.list.experience.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityIndexAdvisorServiceTest {

    @Test
    void buildsExplainableIndexCandidatesWithoutDuplicateColumnSets() {
        List<List<String>> candidates = EntityIndexAdvisorService.buildCandidateColumns(
                Set.of("status", "dept_id"), Set.of("created_at"));
        assertEquals(candidates.size(), candidates.stream().collect(Collectors.toSet()).size());
        assertTrue(candidates.stream().anyMatch(columns -> columns.size() > 1));

        var lowSelectivity = EntityIndexAdvisorService.assessCandidate(
                List.of("status"), 1_000_000, 0.02D, false, false);
        assertEquals("NOT_RECOMMENDED", lowSelectivity.status());
        assertTrue(lowSelectivity.recommendation().contains("反建议"));

        var duplicate = EntityIndexAdvisorService.assessCandidate(
                List.of("dept_id"), 100_000, 0.80D, true, false);
        assertEquals("NOT_RECOMMENDED", duplicate.status());
    }
}
