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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityListPublishedRuntimeServiceTest {

    @Test
    void signsPinnedTargetFormReleaseInPublishedButtons() {
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        UiReleaseResolutionTokenService tokenService =
                mock(UiReleaseResolutionTokenService.class);
        EntityListPublishedRuntimeService service =
                new EntityListPublishedRuntimeService(
                        releaseService,
                        tokenService,
                        new JsonDocumentCodec(new ObjectMapper()));
        EntityListConfig config = new EntityListConfig();
        config.setId("list-1");
        config.setPublishedSnapshot(true);
        Map<String, Object> sourceButton = new LinkedHashMap<>();
        sourceButton.put("key", "create");
        sourceButton.put("targetFormId", "form-1");
        sourceButton.put("targetFormReleaseId", "release-3");
        sourceButton.put("targetFormReleaseVersion", 3);
        EntityListConfigDTO snapshot = new EntityListConfigDTO();
        snapshot.setToolbarConfig(List.of(sourceButton));
        when(releaseService.resolveRuntimeList("list-1"))
                .thenReturn(snapshot);
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
