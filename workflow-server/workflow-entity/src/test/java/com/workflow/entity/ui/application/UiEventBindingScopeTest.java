package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.ui.api.request.UiEventBindingSaveRequest;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class UiEventBindingScopeTest {

    @Test
    void rejectsEveryListOnlyEventInFormConfiguration() {
        for (String eventCode : List.of(
                "LIST_LOAD",
                "LIST_EXPORT",
                "DATA_DELETE",
                "DATA_BATCH_DELETE",
                "TOOLBAR_BUTTON_CLICK",
                "ROW_BUTTON_CLICK")) {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> service().save(request(
                            "FORM",
                            "OWNER",
                            null,
                            eventCode)));

            assertEquals(
                    "FORM 作用域的 OWNER 目标不支持事件: "
                            + eventCode,
                    error.getMessage());
        }
    }

    @Test
    void rejectsEveryFormOnlyEventInListConfiguration() {
        for (String eventCode : List.of(
                "FORM_OPEN",
                "FORM_SAVE",
                "FORM_RESET",
                "FIELD_CHANGE",
                "ENTITY_SELECTED",
                "FIELD_BUTTON_CLICK",
                "SUBFORM_LOAD",
                "SUBFORM_SAVE",
                "FORM_BUTTON_CLICK")) {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> service().save(request(
                            "LIST",
                            "OWNER",
                            null,
                            eventCode)));

            assertEquals(
                    "LIST 作用域的 OWNER 目标不支持事件: "
                            + eventCode,
                    error.getMessage());
        }
    }

    @Test
    void rejectsNonFieldEventForFieldTarget() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().save(request(
                        "FORM",
                        "FIELD",
                        "status",
                        "FORM_SAVE")));

        assertEquals(
                "FORM 作用域的 FIELD 目标不支持事件: FORM_SAVE",
                error.getMessage());
    }

    @Test
    void rejectsWrongButtonEventForFormTarget() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().save(request(
                        "FORM",
                        "BUTTON",
                        "approve",
                        "ROW_BUTTON_CLICK")));

        assertEquals(
                "FORM 作用域的 BUTTON 目标不支持事件: ROW_BUTTON_CLICK",
                error.getMessage());
    }

    @Test
    void rejectsFieldTargetOutsideFormConfiguration() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().save(request(
                        "LIST",
                        "FIELD",
                        "status",
                        "FIELD_CHANGE")));

        assertEquals(
                "LIST 作用域不支持 FIELD 事件目标",
                error.getMessage());
    }

    @Test
    void runtimeRejectsCrossScopeEventBeforeResolvingRelease() {
        UiEventExecuteRequest request = new UiEventExecuteRequest();
        request.setConfigType("FORM");
        request.setConfigId("form-1");
        request.setTargetType("OWNER");
        request.setEventCode("DATA_BATCH_DELETE");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().resolvePublished(request));

        assertEquals(
                "FORM 作用域的 OWNER 目标不支持事件: DATA_BATCH_DELETE",
                error.getMessage());
    }

    @Test
    void catalogExposesOwnerAndTargetSpecificEventScopes() {
        Map<?, ?> scopes = (Map<?, ?>) service()
                .catalog()
                .get("eventScopes");
        Map<?, ?> formScopes = (Map<?, ?>) scopes.get("FORM");
        Map<?, ?> listScopes = (Map<?, ?>) scopes.get("LIST");
        Set<?> formEvents = (Set<?>) formScopes.get("OWNER");
        Set<?> listEvents = (Set<?>) listScopes.get("OWNER");

        assertTrue(formEvents.contains("FORM_SAVE"));
        assertFalse(formEvents.contains("LIST_LOAD"));
        assertTrue(listEvents.contains("LIST_LOAD"));
        assertFalse(listEvents.contains("FORM_SAVE"));
        assertEquals(
                Set.of("FORM_BUTTON_CLICK"),
                formScopes.get("BUTTON"));
        assertEquals(
                Set.of("TOOLBAR_BUTTON_CLICK", "ROW_BUTTON_CLICK"),
                listScopes.get("BUTTON"));
    }

    private UiEventBindingService service() {
        ObjectMapper objectMapper = new ObjectMapper();
        return new UiEventBindingService(
                mock(UiEventBindingMapper.class),
                mock(UiConfigReleaseMapper.class),
                mock(EntityDefinitionMapper.class),
                mock(EntityFormMapper.class),
                mock(EntityListConfigMapper.class),
                mock(EntityDefinitionAccessPolicy.class),
                mock(UiConfigurationAccessService.class),
                mock(UiDataSourceService.class),
                mock(UiConfigReleaseService.class),
                new JsonDocumentCodec(objectMapper),
                objectMapper);
    }

    private UiEventBindingSaveRequest request(
            String ownerType,
            String targetType,
            String targetKey,
            String eventCode) {
        UiEventBindingSaveRequest request =
                new UiEventBindingSaveRequest();
        request.setOwnerType(ownerType);
        request.setOwnerId(ownerType.toLowerCase() + "-1");
        request.setTargetType(targetType);
        request.setTargetKey(targetKey);
        request.setEventCode(eventCode);
        request.setInheritanceMode("INHERIT");
        request.setSteps(List.of());
        request.setEnabled(true);
        return request;
    }
}
