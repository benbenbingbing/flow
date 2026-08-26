package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UiViewCompositionContainmentGuardTest {

    @Test
    void inlineGraphRejectsCrossAssetCycleWithReadablePath() {
        Fixture fixture = fixture();
        UiConfigRelease releaseA = release(
                fixture.codec,
                "release-a",
                "FORM",
                "form-a",
                1,
                snapshot("FORM", "form-a", "需求表单", List.of()));
        UiConfigRelease releaseB = release(
                fixture.codec,
                "release-b",
                "LIST",
                "list-b",
                2,
                snapshot("LIST", "list-b", "项目列表", List.of(
                        edge("back-to-a", "INLINE", releaseA))));
        register(fixture, releaseA, releaseB);
        Map<String, Object> root = snapshot(
                "FORM",
                "form-a",
                "需求表单",
                List.of(edge("projects", "TAB", releaseB)));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> fixture.guard.validate("FORM", "form-a", root));

        assertEquals(
                "UI_VIEW_COMPOSITION_CONTAINMENT_CYCLE",
                exception.getErrorCode());
        assertTrue(exception.getMessage().contains("需求表单"));
        assertTrue(exception.getMessage().contains("项目列表"));
        assertTrue(exception.getMessage().contains("弹窗、抽屉或新页面"));
    }

    @Test
    void userTriggeredNavigationMayFormAssetCycle() {
        Fixture fixture = fixture();
        UiConfigRelease releaseA = release(
                fixture.codec,
                "release-a",
                "FORM",
                "form-a",
                1,
                snapshot("FORM", "form-a", "需求表单", List.of()));
        UiConfigRelease releaseB = release(
                fixture.codec,
                "release-b",
                "LIST",
                "list-b",
                1,
                snapshot("LIST", "list-b", "项目列表", List.of(
                        edge("back-to-a", "DRAWER", releaseA))));
        register(fixture, releaseA, releaseB);

        assertDoesNotThrow(() -> fixture.guard.validate(
                "FORM",
                "form-a",
                snapshot(
                        "FORM",
                        "form-a",
                        "需求表单",
                        List.of(edge("projects", "PAGE", releaseB)))));
    }

    @Test
    void sharedDescendantIsRecheckedAgainstEachCurrentAncestorPath() {
        Fixture fixture = fixture();
        UiConfigRelease oldReleaseC = release(
                fixture.codec,
                "release-c-old",
                "LIST",
                "list-c",
                1,
                snapshot("LIST", "list-c", "历史项目列表", List.of()));
        UiConfigRelease releaseD = release(
                fixture.codec,
                "release-d",
                "FORM",
                "form-d",
                1,
                snapshot("FORM", "form-d", "项目表单", List.of(
                        edge("back-to-c", "INLINE", oldReleaseC))));
        UiConfigRelease releaseB = release(
                fixture.codec,
                "release-b",
                "LIST",
                "list-b",
                1,
                snapshot("LIST", "list-b", "需求列表", List.of(
                        edge("shared-d", "INLINE", releaseD))));
        UiConfigRelease currentReleaseC = release(
                fixture.codec,
                "release-c-current",
                "LIST",
                "list-c",
                2,
                snapshot("LIST", "list-c", "当前项目列表", List.of(
                        edge("shared-d", "INLINE", releaseD))));
        register(
                fixture,
                oldReleaseC,
                releaseD,
                releaseB,
                currentReleaseC);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> fixture.guard.validate(
                        "FORM",
                        "form-a",
                        snapshot("FORM", "form-a", "宿主表单", List.of(
                                edge("first-branch", "INLINE", releaseB),
                                edge("second-branch", "INLINE", currentReleaseC)))));

        assertEquals(
                "UI_VIEW_COMPOSITION_CONTAINMENT_CYCLE",
                exception.getErrorCode());
        assertTrue(exception.getMessage().contains("当前项目列表"));
        assertTrue(exception.getMessage().contains("项目表单"));
    }

    @Test
    void eightContainmentLevelsPassAndNinthLevelFails() {
        Fixture allowed = fixture();
        Map<String, Object> eightLevels = linearGraph(allowed, 8);
        assertDoesNotThrow(() -> allowed.guard.validate(
                "FORM", "asset-1", eightLevels));

        Fixture blocked = fixture();
        Map<String, Object> nineLevels = linearGraph(blocked, 9);
        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> blocked.guard.validate(
                        "FORM", "asset-1", nineLevels));

        assertEquals(
                "UI_VIEW_COMPOSITION_CONTAINMENT_DEPTH_EXCEEDED",
                exception.getErrorCode());
        assertTrue(exception.getMessage().contains("超过 8 层"));
        assertTrue(exception.getMessage().contains("asset-9"));
    }

    private Map<String, Object> linearGraph(
            Fixture fixture,
            int levels) {
        List<UiConfigRelease> releases = new ArrayList<>();
        UiConfigRelease child = null;
        for (int index = levels; index >= 2; index--) {
            String type = index % 2 == 0 ? "LIST" : "FORM";
            List<Map<String, Object>> edges = child == null
                    ? List.of()
                    : List.of(edge("next-" + (index + 1), "INLINE", child));
            child = release(
                    fixture.codec,
                    "release-" + index,
                    type,
                    "asset-" + index,
                    index,
                    snapshot(type, "asset-" + index,
                            "asset-" + index, edges));
            releases.add(child);
        }
        register(fixture, releases.toArray(UiConfigRelease[]::new));
        return snapshot(
                "FORM",
                "asset-1",
                "asset-1",
                List.of(edge("next-2", "ROW_EXPAND", child)));
    }

    private Map<String, Object> snapshot(
            String type,
            String id,
            String name,
            List<Map<String, Object>> edges) {
        Map<String, Object> owner = new LinkedHashMap<>();
        owner.put("id", id);
        owner.put("entityId", "entity-" + id);
        owner.put("FORM".equals(type) ? "formName" : "listName", name);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("configType", type);
        snapshot.put(type.toLowerCase(), owner);
        snapshot.put("viewCompositions", edges);
        return snapshot;
    }

    private Map<String, Object> edge(
            String compositionKey,
            String position,
            UiConfigRelease target) {
        return Map.of(
                "id", "composition-" + compositionKey,
                "compositionKey", compositionKey,
                "anchorType", "OWNER",
                "orderKey", 1000,
                "config", Map.of(
                        "enabled", true,
                        "presentation", Map.of(
                                "position", position,
                                "loadMode", "IMMEDIATE"),
                        "target", Map.of(
                                "entityId", "entity-" + target.getConfigId(),
                                "contentType", target.getConfigType(),
                                "contentId", target.getConfigId(),
                                "releaseId", target.getId(),
                                "releaseVersion", target.getVersion(),
                                "contentHash", target.getContentHash())));
    }

    private UiConfigRelease release(
            JsonDocumentCodec codec,
            String id,
            String type,
            String configId,
            int version,
            Map<String, Object> snapshot) {
        String canonical = codec.canonicalize(
                codec.write(snapshot, "test snapshot"),
                "test snapshot");
        UiConfigRelease release = new UiConfigRelease();
        release.setId(id);
        release.setConfigType(type);
        release.setConfigId(configId);
        release.setVersion(version);
        release.setSnapshotDocument(canonical);
        release.setContentHash(sha256(canonical));
        release.setStatus("ACTIVE");
        return release;
    }

    private void register(Fixture fixture, UiConfigRelease... releases) {
        for (UiConfigRelease release : releases) {
            when(fixture.mapper.selectById(release.getId()))
                    .thenReturn(release);
        }
    }

    private Fixture fixture() {
        UiConfigReleaseMapper mapper = mock(UiConfigReleaseMapper.class);
        JsonDocumentCodec codec = new JsonDocumentCodec(new ObjectMapper());
        return new Fixture(
                mapper,
                codec,
                new UiViewCompositionContainmentGuard(mapper, codec));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Fixture(
            UiConfigReleaseMapper mapper,
            JsonDocumentCodec codec,
            UiViewCompositionContainmentGuard guard) {
    }
}
