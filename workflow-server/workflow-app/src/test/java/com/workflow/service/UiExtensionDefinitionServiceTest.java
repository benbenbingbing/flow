package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.dto.UiExtensionDefinitionSaveRequest;
import com.workflow.entity.UiExtensionDefinition;
import com.workflow.mapper.UiExtensionDefinitionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UiExtensionDefinitionServiceTest {

    @Test
    void requiresExplicitRegisteredVersion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service(mock(UiExtensionDefinitionMapper.class))
                        .requireActive("NODE", "risk-matrix", null));
    }

    @Test
    void rejectsUnsupportedNodeType() {
        UiExtensionDefinition definition = new UiExtensionDefinition();
        definition.setExtensionKey("risk-matrix");
        definition.setSnapshotVersion(2);
        definition.setSupportedNodeTypesDocument("[\"FIELD\"]");

        assertThrows(
                IllegalArgumentException.class,
                () -> service(mock(UiExtensionDefinitionMapper.class))
                        .validateCompatibility(
                                definition,
                                "edit",
                                "SECTION",
                                "NONE",
                                1));
    }

    @Test
    void rejectsSnapshotNewerThanRegisteredProtocol() {
        UiExtensionDefinition definition = new UiExtensionDefinition();
        definition.setExtensionKey("risk-matrix");
        definition.setSnapshotVersion(2);

        assertThrows(
                IllegalArgumentException.class,
                () -> service(mock(UiExtensionDefinitionMapper.class))
                        .validateCompatibility(
                                definition,
                                null,
                                "FIELD",
                                "ENTITY_FIELD",
                                3));
    }

    @Test
    void rejectsInvalidRuntimeModeDuringRegistration() {
        UiExtensionDefinitionSaveRequest request =
                new UiExtensionDefinitionSaveRequest();
        request.setExtensionType("FORM");
        request.setExtensionKey("project-form");
        request.setDisplayName("项目表单");
        request.setVersion(1);
        request.setSupportedModes(List.of("create", "execute-shell"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service(mock(UiExtensionDefinitionMapper.class))
                        .save(request));
    }

    @Test
    void rejectsMissingActiveManifest() {
        UiExtensionDefinitionMapper mapper =
                mock(UiExtensionDefinitionMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service(mapper).requireActive(
                        "FORM", "project-form", 1));
    }

    private UiExtensionDefinitionService service(
            UiExtensionDefinitionMapper mapper) {
        return new UiExtensionDefinitionService(
                mapper,
                new JsonDocumentCodec(new ObjectMapper()));
    }
}
