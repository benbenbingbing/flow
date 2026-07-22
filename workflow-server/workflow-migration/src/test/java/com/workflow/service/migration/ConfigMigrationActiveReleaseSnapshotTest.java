package com.workflow.service.migration;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigrationActiveReleaseSnapshotTest {

    @Test
    void activeReleaseFormOverridesDraftMetadata() {
        Map<String, Object> release = Map.of(
                "form",
                Map.of(
                        "formKey", "published",
                        "formName", "已发布表单"));
        Map<String, Object> draft = Map.of(
                "formKey", "draft",
                "formName", "未发布草稿");

        Map<String, Object> selected =
                ConfigMigrationAssetService.selectReleasedSection(
                        release, "form", draft);

        assertEquals("published", selected.get("formKey"));
        assertEquals("已发布表单", selected.get("formName"));
    }

    @Test
    void explicitEmptyPublishedSectionDoesNotFallBackToDraft() {
        Map<String, Object> release = new LinkedHashMap<>();
        release.put("form", Map.of());

        Map<String, Object> selected =
                ConfigMigrationAssetService.selectReleasedSection(
                        release,
                        "form",
                        Map.of("formKey", "draft"));

        assertTrue(selected.isEmpty());
    }

    @Test
    void missingReleaseSectionFallsBackToDraft() {
        Map<String, Object> draft =
                Map.of("formKey", "draft");

        Map<String, Object> selected =
                ConfigMigrationAssetService.selectReleasedSection(
                        Map.of(), "form", draft);

        assertEquals(draft, selected);
    }
}
