package com.workflow.entity.list.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.runtime.port.EmbedNativeListDependencySnapshotPort;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityListPublishedRuntimeServiceTest {
    @Test void runtimeReturnsDigestAndButtonLocatorsWithoutPageAuthorizationCredentials() {
        var releases = mock(UiConfigReleaseService.class);
        var mapper = mock(UiConfigReleaseMapper.class);
        var json = new ObjectMapper();
        var service = new EntityListPublishedRuntimeService(releases, new JsonDocumentCodec(json), mapper, json,
                mock(EmbedNativeListDependencySnapshotPort.class));
        var draft = new EntityListConfig(); draft.setId("list");
        var published = new EntityListConfigDTO(); published.setId("list"); published.setListKey("main");
        published.setEntityCode("expense");
        published.setToolbarConfig(List.of(Map.of("key", "create", "targetFormId", "form")));
        when(releases.resolveRuntimeListRelease("list", null, Map.of())).thenReturn(
                new UiConfigReleaseService.ResolvedEntityListRelease(published, "list-release", 3, false, Map.of()));
        var row = new UiConfigRelease(); row.setContentHash("list-digest");
        when(mapper.selectById("list-release")).thenReturn(row);
        when(releases.formConfigHash("form")).thenReturn("form-graph");
        var runtime = service.resolveConfig(draft);
        assertEquals("list-digest", runtime.getConfigHash());
        var button = service.resolveToolbar(runtime, List.of()).get(0);
        assertEquals("form-graph", button.get("targetFormConfigHash"));
        assertEquals("create", ((Map<?, ?>) button.get("runtimeContext")).get("buttonId"));
        assertFalse(button.keySet().stream().anyMatch(key -> key.toLowerCase().contains("token")));
        assertFalse(button.containsKey("targetFormReleaseId"));
    }
}
