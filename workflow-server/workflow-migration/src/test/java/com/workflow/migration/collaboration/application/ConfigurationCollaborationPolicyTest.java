package com.workflow.migration.collaboration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationCollaborationPolicyTest {

    private final ConfigurationCollaborationPolicy policy =
            new ConfigurationCollaborationPolicy(new ObjectMapper());

    @Test
    void hashIsStableAcrossMapInsertionOrder() {
        Map<String, Object> left = new LinkedHashMap<>();
        left.put("name", "form");
        left.put("version", 2);
        Map<String, Object> right = new LinkedHashMap<>();
        right.put("version", 2);
        right.put("name", "form");
        assertEquals(policy.hash(left), policy.hash(right));
    }

    @Test
    void threeWayMergeNeverSilentlyOverwritesConcurrentChanges() {
        Map<String, Object> base = Map.of("name", "base");
        String baseHash = policy.hash(base);
        var clean = policy.merge(baseHash, base, Map.of("name", "branch"), null);
        assertFalse(clean.conflict());
        assertEquals("branch", clean.content().get("name"));

        var conflict = policy.merge(baseHash,
                Map.of("name", "target"), Map.of("name", "branch"), null);
        assertTrue(conflict.conflict());
        var resolved = policy.merge(baseHash,
                Map.of("name", "target"), Map.of("name", "branch"),
                Map.of("name", "resolved"));
        assertFalse(resolved.conflict());
        assertEquals("EXPLICIT_RESOLUTION", resolved.reason());
    }

    @Test
    void requesterCannotApproveOwnReview() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.requireIndependentReviewer("user-1", "user-1"));
    }
}
