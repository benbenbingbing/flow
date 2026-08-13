package com.workflow.entity.list.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiReleaseResolutionTokenService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityListPublishedRuntimeServiceTest {

    @Test
    void resolvesExactPublishedListRelease() {
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        UiReleaseResolutionTokenService tokenService =
                mock(UiReleaseResolutionTokenService.class);
        JsonDocumentCodec codec =
                new JsonDocumentCodec(new ObjectMapper());
        EntityListPublishedRuntimeService service =
                new EntityListPublishedRuntimeService(
                        releaseService,
                        tokenService,
                        codec);
        EntityListConfig draft = new EntityListConfig();
        draft.setId("list-1");
        draft.setListName("草稿列表");
        EntityListConfigDTO published = new EntityListConfigDTO();
        published.setId("list-1");
        published.setEntityId("entity-1");
        published.setEntityCode("asset");
        published.setListKey("default");
        published.setListName("发布列表");
        published.setAllowedScenes(List.of("EMBEDDED"));
        published.setFields(List.of());
        published.setToolbarConfig(List.of());
        published.setRowActionConfig(List.of());
        when(releaseService.resolveRuntimeListRelease(
                "list-1",
                "release-2",
                2,
                "signed-token"))
                .thenReturn(new UiConfigReleaseService
                        .ResolvedEntityListRelease(
                        published,
                        "release-2",
                        2,
                        true,
                        Map.of()));

        EntityListConfig result = service.resolveConfig(
                draft,
                "release-2",
                2,
                "signed-token");

        assertEquals("发布列表", result.getListName());
        assertEquals("release-2", result.getActiveReleaseId());
        assertEquals(2, result.getPublishedVersion());
        assertEquals("signed-token", result.getReleaseResolutionToken());
        assertTrue(result.getPublishedSnapshot());
        assertTrue(result.getPinnedRelease());
    }

    @Test
    void signsPinnedTargetFormReleaseInPublishedButtons() {
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        UiReleaseResolutionTokenService tokenService =
                mock(UiReleaseResolutionTokenService.class);
        JsonDocumentCodec codec =
                new JsonDocumentCodec(new ObjectMapper());
        EntityListPublishedRuntimeService service =
                new EntityListPublishedRuntimeService(
                        releaseService,
                        tokenService,
                        codec);
        EntityListConfig config = new EntityListConfig();
        config.setId("list-1");
        config.setPublishedSnapshot(true);
        Map<String, Object> sourceButton = new LinkedHashMap<>();
        sourceButton.put("key", "create");
        sourceButton.put("targetFormId", "form-1");
        sourceButton.put("targetFormReleaseId", "release-3");
        sourceButton.put("targetFormReleaseVersion", 3);
        config.setToolbarConfig(codec.write(
                List.of(sourceButton),
                "测试发布工具栏"));
        when(tokenService.issue(
                UiRuntimeResolutionContext.standalone(),
                "form-1",
                "release-3",
                3,
                0)).thenReturn("signed-token");

        List<Map<String, Object>> buttons =
                service.resolveToolbar(config, List.of());

        assertEquals(
                "signed-token",
                buttons.get(0).get(
                        "targetFormReleaseResolutionToken"));
        assertFalse(sourceButton.containsKey(
                "targetFormReleaseResolutionToken"));
    }
}
