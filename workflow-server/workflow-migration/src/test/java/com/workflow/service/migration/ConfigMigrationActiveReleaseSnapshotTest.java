package com.workflow.service.migration;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConfigMigrationAssetService#selectReleasedSection} 单元测试。
 *
 * <p>验证导出时表单分区优先取已发布快照、显式空发布分区不回退草稿、缺失分区回退草稿的行为。</p>
 */
class ConfigMigrationActiveReleaseSnapshotTest {

    /** 已发布快照含表单分区时，应覆盖草稿元数据。 */
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

    /** 发布快照显式存在空表单分区时，不应回退使用草稿。 */
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

    /** 发布快照不含表单分区时，应回退使用草稿。 */
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
