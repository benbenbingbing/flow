package com.workflow.entity.list.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.EmbedNativeListDependencyClosure;
import com.workflow.contracts.embed.EmbedNativeListDependencyClosure.FormCoordinate;
import com.workflow.contracts.embed.EmbedNativeListDependencyClosure.ListCoordinate;
import com.workflow.contracts.embed.EmbedNativeListDependencyClosure.ListNode;
import com.workflow.contracts.embed.EmbedNativeListDependencySnapshotPort;
import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiReleaseResolutionTokenService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

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
                        codec,
                        mock(com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper.class),
                        new ObjectMapper(),
                        mock(EmbedNativeListDependencySnapshotPort.class));
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
                        Map.of(
                                "viewCompositions",
                                List.of(Map.of(
                                        "compositionKey",
                                        "project-requirements")))));

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
        assertEquals(
                "project-requirements",
                result.getViewCompositions().get(0)
                        .get("compositionKey"));
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
                        codec,
                        mock(com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper.class),
                        new ObjectMapper(),
                        mock(EmbedNativeListDependencySnapshotPort.class));
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

    @Test
    void embedListTargetFormTokenInheritsSessionAbsoluteExpiry() {
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
                        codec,
                        mock(com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper.class),
                        new ObjectMapper(),
                        mock(EmbedNativeListDependencySnapshotPort.class));
        EntityListConfig config = new EntityListConfig();
        config.setId("list-1");
        config.setPublishedSnapshot(true);
        config.setReleaseResolutionToken("elr1.payload.signature");
        config.setToolbarConfig(codec.write(
                List.of(Map.of(
                        "key", "create",
                        "targetFormId", "form-1",
                        "targetFormReleaseId", "release-3",
                        "targetFormReleaseVersion", 3)),
                "测试 Embed 发布工具栏"));
        long expiresAt = Instant.now().plusSeconds(1200)
                .getEpochSecond();
        when(tokenService.isEmbedListToken(
                "elr1.payload.signature")).thenReturn(true);
        when(tokenService.verifyEmbedList(
                "elr1.payload.signature")).thenReturn(
                new UiReleaseResolutionTokenService.EmbedListClaims(
                        "asset", "list-1", "list-release-2", 2,
                        "session-1", "view-release-1", "user-1",
                        expiresAt - 60, expiresAt));
        when(tokenService.issue(
                UiRuntimeResolutionContext.standalone(),
                "form-1",
                "release-3",
                3,
                0,
                Instant.ofEpochSecond(expiresAt)))
                .thenReturn("session-bound-form-token");

        List<Map<String, Object>> buttons =
                service.resolveToolbar(config, List.of());

        assertEquals(
                "session-bound-form-token",
                buttons.get(0).get(
                        "targetFormReleaseResolutionToken"));
    }

    @Test
    void openListButtonsPinSameEntitySiblingAndCrossEntityTargets() {
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        UiReleaseResolutionTokenService tokenService =
                mock(UiReleaseResolutionTokenService.class);
        JsonDocumentCodec codec =
                new JsonDocumentCodec(new ObjectMapper());
        EmbedNativeListDependencySnapshotPort dependencySnapshotPort =
                mock(EmbedNativeListDependencySnapshotPort.class);
        EntityListPublishedRuntimeService service =
                new EntityListPublishedRuntimeService(
                        releaseService,
                        tokenService,
                        codec,
                        mock(com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper.class),
                        new ObjectMapper(),
                        dependencySnapshotPort);
        EntityListConfig config = new EntityListConfig();
        config.setId("root-list");
        config.setPublishedSnapshot(true);
        config.setReleaseResolutionToken("elr1.root.signature");
        config.setToolbarConfig(codec.write(
                List.of(
                        Map.of(
                                "key", "chooseAsset",
                                "customMode", "open-list",
                                "targetEntityCode", "asset",
                                "targetListKey", "selectable",
                                "targetListId", "asset-list",
                                "targetListReleaseId", "asset-list-release-5",
                                "targetListReleaseVersion", 5),
                        Map.of(
                                "key", "chooseOtherOrder",
                                "customMode", "open-list",
                                "targetEntityCode", "order",
                                "targetListKey", "archive",
                                "targetListId", "order-archive-list",
                                "targetListReleaseId", "order-list-release-3",
                                "targetListReleaseVersion", 3)),
                "测试 open-list 工具栏"));
        long expiresAt = Instant.now().plusSeconds(1200)
                .getEpochSecond();
        UiReleaseResolutionTokenService.EmbedListClaims claims =
                new UiReleaseResolutionTokenService.EmbedListClaims(
                        "order", "root-list", "root-release-2", 2,
                        "session-1", "view-1", "view-release-1",
                        1, "closure-hash", "user-1",
                        expiresAt - 60, expiresAt);
        ListCoordinate assetTarget = new ListCoordinate(
                "asset", "selectable", "asset-list",
                "asset-list-release-5", 5);
        ListCoordinate orderTarget = new ListCoordinate(
                "order", "archive", "order-archive-list",
                "order-list-release-3", 3);
        EmbedNativeListDependencyClosure closure =
                new EmbedNativeListDependencyClosure(
                        1,
                        List.of(
                                new ListNode(
                                        new ListCoordinate(
                                                "order", "root", "root-list",
                                                "root-release-2", 2),
                                        true, null,
                                        List.of(assetTarget, orderTarget)),
                                new ListNode(
                                        assetTarget,
                                        true,
                                        new FormCoordinate(
                                                "asset-form",
                                                "asset-form-release-4", 4),
                                        List.of()),
                                new ListNode(
                                        orderTarget,
                                        true, null, List.of())));
        when(tokenService.isEmbedListToken(
                "elr1.root.signature")).thenReturn(true);
        when(tokenService.verifyEmbedList(
                "elr1.root.signature")).thenReturn(claims);
        when(dependencySnapshotPort.read(any())).thenReturn(closure);
        when(tokenService.issueEmbedList(
                "asset",
                "asset-list",
                "asset-list-release-5",
                5,
                "session-1",
                "view-1",
                "view-release-1",
                1,
                "closure-hash",
                Instant.ofEpochSecond(expiresAt)))
                .thenReturn("elr1.target.signature");
        when(tokenService.issueEmbedList(
                "order",
                "order-archive-list",
                "order-list-release-3",
                3,
                "session-1",
                "view-1",
                "view-release-1",
                1,
                "closure-hash",
                Instant.ofEpochSecond(expiresAt)))
                .thenReturn("elr1.sibling.signature");
        when(tokenService.issue(
                UiRuntimeResolutionContext.standalone(),
                "asset-form",
                "asset-form-release-4",
                4,
                0,
                Instant.ofEpochSecond(expiresAt)))
                .thenReturn("asset-form-session-token");

        List<Map<String, Object>> buttons =
                service.resolveToolbar(config, List.of());

        assertEquals(
                "elr1.target.signature",
                buttons.get(0).get(
                        "targetListReleaseResolutionToken"));
        assertFalse(buttons.get(0).containsKey(
                "targetFormReleaseResolutionToken"));
        assertEquals(
                true,
                buttons.get(0).get("targetDefaultFormResolved"));
        assertEquals(
                "asset-form",
                buttons.get(0).get("targetDefaultFormId"));
        assertEquals(
                "asset-form-release-4",
                buttons.get(0).get(
                        "targetDefaultFormReleaseId"));
        assertEquals(
                4,
                buttons.get(0).get(
                        "targetDefaultFormReleaseVersion"));
        assertEquals(
                "asset-form-session-token",
                buttons.get(0).get(
                        "targetDefaultFormReleaseResolutionToken"));
        assertEquals(
                "elr1.sibling.signature",
                buttons.get(1).get(
                        "targetListReleaseResolutionToken"));
        assertEquals(
                true,
                buttons.get(1).get("targetDefaultFormResolved"));
        assertFalse(buttons.get(1).containsKey(
                "targetDefaultFormId"));

        config.setToolbarConfig(codec.write(
                List.of(Map.of(
                        "key", "tamperedTarget",
                        "customMode", "open-list",
                        "targetEntityCode", "asset",
                        "targetListKey", "selectable",
                        "targetListId", "asset-list",
                        "targetListReleaseId", "asset-list-release-tampered",
                        "targetListReleaseVersion", 5)),
                "测试篡改 open-list 坐标"));
        assertThrows(
                com.workflow.core.error.BusinessForbiddenException.class,
                () -> service.resolveToolbar(config, List.of()));
    }
}
