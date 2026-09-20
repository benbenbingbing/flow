package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UiListLoadBindingTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final EntityListConfigMapper lists = mock(EntityListConfigMapper.class);
    private final UiConfigReleaseService releases = mock(UiConfigReleaseService.class);
    private final UiEventBindingService service = new UiEventBindingService(
            mock(UiEventBindingMapper.class), mock(UiConfigReleaseMapper.class), mock(EntityDefinitionMapper.class),
            mock(EntityFormMapper.class), lists, mock(EntityDefinitionAccessPolicy.class), mock(UiConfigurationAccessService.class),
            mock(UiInterfaceExtensionService.class), mock(UiEventBindingSnapshotService.class), releases,
            new JsonDocumentCodec(mapper), mapper);

    @Test
    void oldPublishedQuerySlotBecomesReplacementWithoutChangingSnapshot() {
        Map<String, Object> snapshot = snapshot(List.of());
        UiEventExecuteRequest request = request();
        when(releases.resolveRuntimeListRelease("list-1", "old-release", 3, null)).thenReturn(release(snapshot));

        var chain = service.resolvePublished(request);

        assertEquals(1, chain.steps().size());
        assertEquals("old-query", chain.steps().get(0).get("extensionId"));
        assertEquals(true, chain.steps().get(0).get("legacyListQuery"));
        assertEquals("REPLACE", chain.steps().get(0).get("strategy"));
        assertEquals(snapshot, chain.snapshot());
        assertEquals(List.of(), snapshot.get("eventBindings"));
    }

    @Test
    void explicitReplacementKeepsPriorityOverOldQuerySlot() {
        UiEventExecuteRequest request = request();
        Map<String, Object> snapshot = snapshot(List.of(Map.of("ownerType", "LIST", "ownerId", "list-1",
                "targetType", "OWNER", "eventCode", "LIST_LOAD", "inheritanceMode", "REPLACE",
                "steps", List.of(Map.of("strategy", "REPLACE", "extensionId", "new-query")))));
        when(releases.resolveRuntimeListRelease("list-1", "old-release", 3, null)).thenReturn(release(snapshot));

        var chain = service.resolvePublished(request);

        assertEquals(1, chain.steps().size());
        assertEquals("new-query", chain.steps().get(0).get("extensionId"));
        assertNull(chain.steps().get(0).get("legacyListQuery"));
    }

    @Test
    void trustedPinnedListLoadsExactReleaseWithoutResolvingActive() {
        UiEventExecuteRequest request = request();
        request.setServerPinnedRelease(true);
        when(releases.resolveServerPinnedRuntimeListRelease("list-1", "old-release", 3))
                .thenReturn(release(snapshot(List.of())));

        var chain = service.resolvePublished(request);

        assertEquals("old-release", chain.releaseId());
        assertEquals(3, chain.releaseVersion());
        verify(releases, never()).resolveRuntimeListRelease(any(), any(), any(), any());
    }

    private UiConfigReleaseService.ResolvedEntityListRelease release(Map<String, Object> snapshot) {
        return new UiConfigReleaseService.ResolvedEntityListRelease(null, "old-release", 3, true, snapshot);
    }

    private Map<String, Object> snapshot(List<Map<String, Object>> bindings) {
        return Map.of("list", Map.of("queryInterfaceExtensionId", "old-query"), "eventBindings", bindings);
    }

    private UiEventExecuteRequest request() {
        EntityListConfig list = new EntityListConfig();
        list.setId("list-1");
        list.setEntityId("entity-1");
        list.setEntityCode("project");
        list.setListKey("default");
        when(lists.selectById("list-1")).thenReturn(list);
        UiEventExecuteRequest request = new UiEventExecuteRequest();
        request.setConfigType("LIST");
        request.setConfigId("list-1");
        request.setEventCode("LIST_LOAD");
        request.setReleaseId("old-release");
        request.setReleaseVersion(3);
        return request;
    }
}
