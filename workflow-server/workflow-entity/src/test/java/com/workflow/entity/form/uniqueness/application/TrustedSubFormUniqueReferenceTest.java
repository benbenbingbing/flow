package com.workflow.entity.form.uniqueness.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustedSubFormUniqueReferenceTest {

    @Test
    void trustedMarkerWithoutEntityFailsClosed() {
        FormUniqueMutationContext.Reference reference =
                new FormUniqueMutationContext.Reference(
                        "child-form", "release-1", 1, "release-1");

        assertThrows(
                IllegalArgumentException.class,
                () -> TrustedSubFormUniqueReference.attach(
                        new LinkedHashMap<>(), " ", reference));
    }

    @Test
    void ordinaryClientValuesCannotForgeTrustedMarker() {
        Map<String, Object> stringForgery = new LinkedHashMap<>();
        stringForgery.put(
                TrustedSubFormUniqueReference.transportKey(),
                "SERVER_TRUSTED_SUBFORM_RELEASE");
        assertTrue(TrustedSubFormUniqueReference
                .remove(stringForgery)
                .isEmpty());
        assertFalse(stringForgery.containsKey(
                TrustedSubFormUniqueReference.transportKey()));

        Map<String, Object> mapForgery = new LinkedHashMap<>();
        mapForgery.put(
                TrustedSubFormUniqueReference.transportKey(),
                Map.of(
                        "formId", "forged-form",
                        "releaseId", "forged-release"));
        assertTrue(TrustedSubFormUniqueReference
                .remove(mapForgery)
                .isEmpty());
    }

    @Test
    void serverMarkerMergesDistinctReferencesAndIsRemovedOnce()
            throws Exception {
        FormUniqueMutationContext.Reference reference =
                new FormUniqueMutationContext.Reference(
                        "child-form",
                        "release-1",
                        1,
                        "hotfix-2",
                        "hash-2",
                        "target-2");
        FormUniqueMutationContext.Reference second =
                new FormUniqueMutationContext.Reference(
                        "child-form-2",
                        "release-4",
                        4,
                        "release-4");
        Map<String, Object> row = new LinkedHashMap<>(
                Map.of("name", "\u660e\u7ec6A"));

        TrustedSubFormUniqueReference.attach(
                row, "project_line", reference);
        TrustedSubFormUniqueReference.attach(
                row, "project_line", reference);
        TrustedSubFormUniqueReference.attach(
                row, "project_line", second);

        String serialized = new ObjectMapper()
                .writeValueAsString(row);
        assertTrue(serialized.contains(
                "SERVER_TRUSTED_SUBFORM_RELEASE"));
        assertFalse(serialized.contains("child-form"));
        assertFalse(serialized.contains("hotfix-2"));
        assertFalse(serialized.contains("hash-2"));
        assertFalse(serialized.contains("target-2"));
        assertEquals(
                List.of(reference, second),
                TrustedSubFormUniqueReference.remove(row));
        assertTrue(TrustedSubFormUniqueReference
                .remove(row)
                .isEmpty());
        assertEquals(Map.of("name", "\u660e\u7ec6A"), row);
    }

    @Test
    void payloadSnapshotAllowsFieldPatchButRejectsMarkerReplacement() {
        FormUniqueMutationContext.Reference reference =
                new FormUniqueMutationContext.Reference(
                        "child-form", "release-1", 1, "release-1");
        Map<String, Object> child = new LinkedHashMap<>(
                Map.of("name", "明细A"));
        TrustedSubFormUniqueReference.attach(
                child, "project_line", reference);
        Map<String, Object> payload = new LinkedHashMap<>(
                Map.of("details", List.of(child)));
        TrustedSubFormUniqueReference.PayloadSnapshot snapshot =
                TrustedSubFormUniqueReference.snapshot(payload);

        child.put("name", "明细B");
        TrustedSubFormUniqueReference.requireUnchanged(
                snapshot, payload);

        // 即使实体和发布引用完全相同，重新 attach 也会替换私有 Marker 身份。
        TrustedSubFormUniqueReference.attach(
                child, "project_line", reference);
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> TrustedSubFormUniqueReference.requireUnchanged(
                        snapshot, payload));
        assertEquals(
                "实体变换替换或移动了可信子表单标记",
                exception.getMessage());
    }

    @Test
    void payloadSnapshotRejectsMarkerRemovalOrPathMove() {
        FormUniqueMutationContext.Reference reference =
                new FormUniqueMutationContext.Reference(
                        "child-form", "release-1", 1, "release-1");
        Map<String, Object> first = new LinkedHashMap<>(
                Map.of("name", "明细A"));
        Map<String, Object> second = new LinkedHashMap<>(
                Map.of("name", "明细B"));
        TrustedSubFormUniqueReference.attach(
                first, "project_line", reference);
        TrustedSubFormUniqueReference.attach(
                second, "project_line", reference);
        List<Map<String, Object>> rows = new java.util.ArrayList<>(
                List.of(first, second));
        Map<String, Object> payload = new LinkedHashMap<>(
                Map.of("details", rows));
        TrustedSubFormUniqueReference.PayloadSnapshot snapshot =
                TrustedSubFormUniqueReference.snapshot(payload);

        TrustedSubFormUniqueReference.removePrepared(first);
        assertThrows(
                IllegalStateException.class,
                () -> TrustedSubFormUniqueReference.requireUnchanged(
                        snapshot, payload));

        TrustedSubFormUniqueReference.attach(
                first, "project_line", reference);
        TrustedSubFormUniqueReference.PayloadSnapshot reordered =
                TrustedSubFormUniqueReference.snapshot(payload);
        java.util.Collections.swap(rows, 0, 1);
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> TrustedSubFormUniqueReference.requireUnchanged(
                        reordered, payload));
        assertEquals(
                "实体变换替换或移动了可信子表单标记",
                exception.getMessage());
    }
}
