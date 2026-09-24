package com.workflow.entity.list.application;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityListFixedFiltersTest {
    @Test
    void fixedLowerBoundIntersectsUserEqualityAndPreservesItsOperator() {
        Map<String, Object> fixed = Map.of("amount_start", "2", "amount_op", "BETWEEN");
        assertEquals(Map.of("amount", "1", "amount_op", "EQ", "amount_start", "2"),
                EntityListFixedFilters.apply(Map.of("amount", "1", "amount_op", "EQ"), fixed));
        assertEquals(Map.of("amount", "3", "amount_op", "GT", "amount_start", "2"),
                EntityListFixedFilters.apply(Map.of("amount", "3", "amount_op", "GT"), fixed));
    }

    @Test
    void sameDirectionBoundsUseStricterValueWithoutWeakeningFixedFilter() {
        Map<String, Object> fixed = Map.of("amount_start", "2", "amount_end", "100");
        assertEquals(Map.of("amount_start", "10", "amount_end", "90"),
                EntityListFixedFilters.apply(Map.of("amount_start", "10", "amount_end", "90"), fixed));
        assertEquals(Map.of("amount_start", "2", "amount_end", "100"),
                EntityListFixedFilters.apply(Map.of("amount_start", "1", "amount_end", "101"), fixed));
    }

    @Test
    void trustedScalarOperatorOverridesClientOperatorButKeepsIndependentRange() {
        assertEquals(Map.of("status", "ACTIVE", "status_op", "EQ", "status_start", "B"),
                EntityListFixedFilters.apply(
                        Map.of("status", "DELETED", "status_op", "NE", "status_start", "B"),
                        EntityListFixedFilters.normalize(Map.of("status", "ACTIVE"))));
    }

    @Test
    void fixedEqualityConflictsWithIncompatibleUserScalarOrSet() {
        Map<String, Object> fixed = EntityListFixedFilters.normalize(Map.of("amount", "2"));
        assertTrue(EntityListFixedFilters.conflictsWithFixedEquality(
                Map.of("amount", 1, "amount_op", "EQ"), fixed));
        assertFalse(EntityListFixedFilters.conflictsWithFixedEquality(
                Map.of("amount", 2, "amount_op", "EQ"), fixed));
        assertTrue(EntityListFixedFilters.conflictsWithFixedEquality(
                Map.of("amount", 2, "amount_op", "NE"), fixed));
        assertTrue(EntityListFixedFilters.conflictsWithFixedEquality(
                Map.of("amount", java.util.List.of(1, 3), "amount_op", "IN"), fixed));
        assertFalse(EntityListFixedFilters.conflictsWithFixedEquality(
                Map.of("amount", java.util.List.of(1, 2), "amount_op", "IN"), fixed));
        assertFalse(EntityListFixedFilters.conflictsWithFixedEquality(
                Map.of("name", "部分", "name_op", "LIKE"),
                EntityListFixedFilters.normalize(Map.of("name", "完整部分名称"))));
    }
}
