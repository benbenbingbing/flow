package com.workflow.entity.ui.application;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.api.request.UiEventBindingSaveRequest;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UiEventBindingServiceRevisionTest {

    private static final Pattern REVISION_PARAMETER = Pattern.compile(
            "WHERE.*revision\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}",
            Pattern.CASE_INSENSITIVE);

    @Test
    void updateMatchesPersistedRevisionBeforeIncrementingIt() {
        UiEventBindingMapper mapper =
                mock(UiEventBindingMapper.class);
        UiEventBinding current = binding();
        when(mapper.selectById(current.getId()))
                .thenReturn(current);
        when(mapper.update(isNull(), any()))
                .thenReturn(1);

        UiEventBinding saved =
                service(mapper).save(updateRequest(current));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<UiEventBinding>> captor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        UpdateWrapper<UiEventBinding> update = captor.getValue();
        Matcher matcher = REVISION_PARAMETER.matcher(
                update.getCustomSqlSegment());
        assertTrue(matcher.find());
        assertEquals(
                1,
                update.getParamNameValuePairs().get(matcher.group(1)),
                () -> update.getCustomSqlSegment()
                        + " "
                        + update.getParamNameValuePairs());
        assertEquals(2, saved.getRevision());
        assertEquals("REPLACE", saved.getInheritanceMode());
    }

    @Test
    void entitySharedEventAllowsOneReplacePerProjectedContext() {
        UiEventBindingMapper mapper = mock(UiEventBindingMapper.class);
        UiInterfaceExtensionService dataSourceService =
                mock(UiInterfaceExtensionService.class);
        when(dataSourceService.requireExecutableDefinition(
                "form-source", null))
                .thenReturn(interfaceDefinition(
                        "form-source", "FORM", "READ"));
        when(dataSourceService.requireExecutableDefinition(
                "list-source", null))
                .thenReturn(interfaceDefinition(
                        "list-source", "LIST", "READ"));
        UiEventBindingSaveRequest request = sharedRequest(List.of(
                step("form-source", "form-op", 10),
                step("list-source", "list-op", 20)));

        assertDoesNotThrow(() ->
                service(mapper, dataSourceService).save(request));
    }

    @Test
    void entitySharedEventRejectsTwoReplacesInSameProjectedContext() {
        UiEventBindingMapper mapper = mock(UiEventBindingMapper.class);
        UiInterfaceExtensionService dataSourceService =
                mock(UiInterfaceExtensionService.class);
        when(dataSourceService.requireExecutableDefinition(
                "form-source-a", null))
                .thenReturn(interfaceDefinition(
                        "form-source-a", "FORM", "READ"));
        when(dataSourceService.requireExecutableDefinition(
                "form-source-b", null))
                .thenReturn(interfaceDefinition(
                        "form-source-b", "FORM", "READ"));
        UiEventBindingSaveRequest request = sharedRequest(List.of(
                step("form-source-a", "form-op-a", 10),
                step("form-source-b", "form-op-b", 20)));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service(mapper, dataSourceService).save(request));

        assertTrue(error.getMessage().contains("FORM"));
    }

    @Test
    void formButtonBindingRejectsWriteProviderBeforePublish() {
        UiEventBindingMapper mapper = mock(UiEventBindingMapper.class);
        UiInterfaceExtensionService dataSourceService =
                mock(UiInterfaceExtensionService.class);
        when(dataSourceService.requireExecutableDefinition(
                "write-source", null))
                .thenReturn(interfaceDefinition(
                        "write-source", "FORM", "WRITE"));
        UiEventBindingSaveRequest request = formButtonRequest(List.of(
                step("write-source", "write-op", 10)));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service(mapper, dataSourceService).save(request));

        assertTrue(error.getMessage().contains("只允许 READ"));
        assertTrue(error.getMessage().contains("Outbox"));
    }

    @Test
    void buttonScopedFormButtonRejectsDisableButOwnerScopeKeepsIt() {
        UiEventBindingMapper mapper = mock(UiEventBindingMapper.class);
        UiEventBindingSaveRequest buttonRequest = formButtonRequest(List.of());
        buttonRequest.setInheritanceMode("DISABLE");

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> service(mapper).save(buttonRequest));

        assertEquals("UI_EVENT_FORM_BUTTON_DISABLE_UNSUPPORTED",
                error.getErrorCode());
        assertTrue(error.getMessage().contains("启用开关"));

        UiEventBindingSaveRequest ownerRequest = formButtonRequest(List.of());
        ownerRequest.setOwnerType("ENTITY");
        ownerRequest.setOwnerId("entity-1");
        ownerRequest.setTargetType("OWNER");
        ownerRequest.setTargetKey(null);
        ownerRequest.setInheritanceMode("DISABLE");
        assertDoesNotThrow(() -> service(mapper).save(ownerRequest));
    }

    @Test
    void buttonScopedFormButtonReplaceRequiresOneLocalMainStep() {
        UiEventBindingMapper mapper = mock(UiEventBindingMapper.class);
        UiEventBindingSaveRequest request = formButtonRequest(List.of(Map.of(
                "strategy", "BEFORE",
                "failurePolicy", "STOP",
                "order", 10,
                "outputMapping", Map.of(
                        "form.status", "input.form.status"))));
        request.setInheritanceMode("REPLACE");

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> service(mapper).save(request));

        assertEquals("UI_EVENT_FORM_BUTTON_MAIN_STEP_REQUIRED",
                error.getErrorCode());
        assertTrue(error.getMessage().contains("本层"));
        assertTrue(error.getMessage().contains("当前为 0 个"));
    }

    @Test
    void buttonScopedFormButtonRejectsMultipleLocalMainSteps() {
        UiEventBindingMapper mapper = mock(UiEventBindingMapper.class);
        UiEventBindingSaveRequest request = formButtonRequest(List.of(
                Map.of(
                        "strategy", "REPLACE",
                        "failurePolicy", "STOP",
                        "order", 10,
                        "outputMapping", Map.of(
                                "form.status", "input.form.status")),
                Map.of(
                        "strategy", "REPLACE",
                        "failurePolicy", "STOP",
                        "order", 20,
                        "outputMapping", Map.of(
                                "form.code", "input.form.code"))));
        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> service(mapper).save(request));

        assertEquals("UI_EVENT_FORM_BUTTON_MAIN_STEP_REQUIRED",
                error.getErrorCode());
        assertTrue(error.getMessage().contains("当前为 2 个"));
    }

    @Test
    void formButtonRejectsConditionalMainStepAtButtonAndOwnerScopes() {
        UiEventBindingMapper mapper = mock(UiEventBindingMapper.class);
        List<Map<String, Object>> steps = List.of(Map.of(
                "strategy", "REPLACE",
                "failurePolicy", "STOP",
                "order", 10,
                "condition", Map.of(
                        "path", "input.form.status",
                        "equals", "DRAFT"),
                "outputMapping", Map.of(
                        "form.status", "input.form.status")));
        UiEventBindingSaveRequest buttonRequest = formButtonRequest(steps);
        buttonRequest.setInheritanceMode("REPLACE");

        BusinessConflictException buttonError = assertThrows(
                BusinessConflictException.class,
                () -> service(mapper).save(buttonRequest));

        assertEquals(
                "UI_EVENT_FORM_BUTTON_MAIN_STEP_CONDITION_UNSUPPORTED",
                buttonError.getErrorCode());
        assertTrue(buttonError.getMessage().contains("必须无条件执行"));

        UiEventBindingSaveRequest ownerRequest = formButtonRequest(steps);
        ownerRequest.setOwnerType("ENTITY");
        ownerRequest.setOwnerId("entity-1");
        ownerRequest.setTargetType("OWNER");
        ownerRequest.setTargetKey(null);
        BusinessConflictException ownerError = assertThrows(
                BusinessConflictException.class,
                () -> service(mapper).save(ownerRequest));
        assertEquals(
                "UI_EVENT_FORM_BUTTON_MAIN_STEP_CONDITION_UNSUPPORTED",
                ownerError.getErrorCode());
    }

    @Test
    void nonFormButtonWriteProviderRemainsSupported() {
        UiEventBindingMapper mapper = mock(UiEventBindingMapper.class);
        UiInterfaceExtensionService dataSourceService =
                mock(UiInterfaceExtensionService.class);
        when(dataSourceService.requireExecutableDefinition(
                "write-source", null))
                .thenReturn(interfaceDefinition(
                        "write-source", "FORM", "WRITE"));
        UiEventBindingSaveRequest request = sharedRequest(List.of(
                step("write-source", "write-op", 10)));

        assertDoesNotThrow(() ->
                service(mapper, dataSourceService).save(request));
    }

    @Test
    void publishedButtonInheritsOwnerStepWithTrustedSourceIdentity() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonDocumentCodec codec = new JsonDocumentCodec(objectMapper);
        UiEventBindingMapper mapper = mock(UiEventBindingMapper.class);
        EntityDefinitionMapper definitionMapper =
                mock(EntityDefinitionMapper.class);
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        EntityDefinition entity = new EntityDefinition();
        entity.setId("entity-1");
        entity.setEntityCode("expense");
        when(formMapper.selectById("form-1")).thenReturn(form);
        when(definitionMapper.selectById("entity-1"))
                .thenReturn(entity);
        Map<String, Object> snapshot = Map.of(
                "configType", "FORM",
                "form", Map.of(
                        "id", "form-1",
                        "entityId", "entity-1"),
                "eventBindings", List.of(
                        Map.of(
                                "ownerType", "ENTITY",
                                "ownerId", "entity-1",
                                "targetType", "OWNER",
                                "targetKey", "",
                                "eventCode", "FORM_BUTTON_CLICK",
                                "inheritanceMode", "INHERIT",
                                "steps", List.of(Map.of(
                                        "serviceId", "source-1",
                                        "operationCode", "query",
                                        "strategy", "REPLACE",
                                        "order", 10))),
                        Map.of(
                                "ownerType", "FORM",
                                "ownerId", "form-1",
                                "targetType", "BUTTON",
                                "targetKey", "generate",
                                "eventCode", "FORM_BUTTON_CLICK",
                                "inheritanceMode", "INHERIT",
                                "steps", List.of())));
        when(releaseService.resolveRuntimeEventSnapshot(
                "form-1", null, null, null))
                .thenReturn(new UiConfigReleaseService
                        .ResolvedUiEventSnapshot(
                                snapshot,
                                "release-1",
                                1,
                                "hotfix-2",
                                true,
                                "effective-hash"));
        UiEventBindingService service = new UiEventBindingService(
                mapper,
                mock(UiConfigReleaseMapper.class),
                definitionMapper,
                formMapper,
                mock(EntityListConfigMapper.class),
                mock(EntityDefinitionAccessPolicy.class),
                mock(UiConfigurationAccessService.class),
                mock(UiInterfaceExtensionService.class),
                mock(UiEventBindingSnapshotService.class),
                releaseService,
                codec,
                objectMapper);
        UiEventExecuteRequest request = new UiEventExecuteRequest();
        request.setConfigType("FORM");
        request.setConfigId("form-1");
        request.setEventCode("FORM_BUTTON_CLICK");
        request.setTargetType("BUTTON");
        request.setTargetKey("generate");

        UiEventBindingService.ResolvedEventChain chain =
                service.resolvePublished(request);

        assertEquals(1, chain.steps().size());
        assertEquals("ENTITY", chain.steps().get(0).get(
                "bindingOwnerType"));
        assertEquals("OWNER", chain.steps().get(0).get(
                "bindingTargetType"));
        assertEquals("hotfix-2", chain.effectiveReleaseId());
        assertEquals("effective-hash", chain.effectiveContentHash());
    }

    @Test
    void draftResolutionAndSnapshotApiProjectSharedEntityStepsByContext() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonDocumentCodec codec = new JsonDocumentCodec(objectMapper);
        UiEventBindingMapper mapper = mock(UiEventBindingMapper.class);
        UiExtensionDefinitionMapper sourceMapper =
                mock(UiExtensionDefinitionMapper.class);
        EntityDefinitionMapper definitionMapper =
                mock(EntityDefinitionMapper.class);
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        EntityListConfigMapper listMapper =
                mock(EntityListConfigMapper.class);
        UiInterfaceExtensionService dataSourceService =
                mock(UiInterfaceExtensionService.class);
        UiEventBindingSnapshotService snapshotService =
                new UiEventBindingSnapshotService(
                        mapper,
                        sourceMapper,
                        dataSourceService,
                        codec);
        UiEventBindingService service = new UiEventBindingService(
                mapper,
                mock(UiConfigReleaseMapper.class),
                definitionMapper,
                formMapper,
                listMapper,
                mock(EntityDefinitionAccessPolicy.class),
                mock(UiConfigurationAccessService.class),
                dataSourceService,
                snapshotService,
                mock(UiConfigReleaseService.class),
                codec,
                objectMapper);
        UiEventBinding binding = binding();
        binding.setStepsDocument(codec.write(
                List.of(
                        Map.of(
                                "serviceId", "source-mixed",
                                "operationCode", "form-op",
                                "order", 10),
                        Map.of(
                                "serviceId", "source-mixed",
                                "operationCode", "list-op",
                                "order", 20)),
                "测试事件步骤"));
        UiExtensionDefinition formInterface = new UiExtensionDefinition();
        formInterface.setId("form-interface");
        formInterface.setExtensionType("INTERFACE");
        formInterface.setInterfaceContextType("FORM");
        UiExtensionDefinition listInterface = new UiExtensionDefinition();
        listInterface.setId("list-interface");
        listInterface.setExtensionType("INTERFACE");
        listInterface.setInterfaceContextType("LIST");
        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        EntityDefinition entity = new EntityDefinition();
        entity.setId("entity-1");
        entity.setEntityCode("acceptance");
        EntityListConfig list = new EntityListConfig();
        list.setId("list-1");
        list.setEntityId("entity-1");
        list.setEntityCode("acceptance");
        list.setListKey("default");
        when(mapper.findForSnapshot(
                "FORM", "form-1", "entity-1"))
                .thenReturn(List.of(binding));
        when(mapper.findForSnapshot(
                "LIST", "list-1", "entity-1"))
                .thenReturn(List.of(binding));
        when(dataSourceService.resolveDefinitionReference(
                "source-mixed", "form-op"))
                .thenReturn(formInterface);
        when(dataSourceService.resolveDefinitionReference(
                "source-mixed", "list-op"))
                .thenReturn(listInterface);
        when(dataSourceService.requireExecutableDefinition(
                "form-interface", null))
                .thenReturn(formInterface);
        when(dataSourceService.requireExecutableDefinition(
                "list-interface", null))
                .thenReturn(listInterface);
        when(formMapper.selectById("form-1")).thenReturn(form);
        when(definitionMapper.selectById("entity-1"))
                .thenReturn(entity);
        when(listMapper.selectById("list-1")).thenReturn(list);

        List<Map<String, Object>> formSnapshot = service.snapshotBindings(
                "form", "form-1", "entity-1");
        List<Map<String, Object>> listSnapshot = service.snapshotBindings(
                "list", "list-1", "entity-1");
        Map<String, Object> formResolved = service.resolveDraft(
                "form", "form-1", "DETAIL_LOAD");
        Map<String, Object> listResolved = service.resolveDraft(
                "list", "list-1", "DETAIL_LOAD");

        assertEquals(List.of("form-interface"), extensionIds(
                formSnapshot.get(0).get("steps")));
        assertEquals(List.of("list-interface"), extensionIds(
                listSnapshot.get(0).get("steps")));
        assertEquals(List.of("form-interface"), extensionIds(
                formResolved.get("steps")));
        assertEquals(List.of("list-interface"), extensionIds(
                listResolved.get("steps")));
        assertEquals("INHERITED", formResolved.get("source"));
        assertEquals("INHERITED", listResolved.get("source"));
    }

    private UiEventBindingService service(
            UiEventBindingMapper mapper) {
        return service(mapper, mock(UiInterfaceExtensionService.class));
    }

    private UiEventBindingService service(
            UiEventBindingMapper mapper,
            UiInterfaceExtensionService dataSourceService) {
        return new UiEventBindingService(
                mapper,
                mock(UiConfigReleaseMapper.class),
                mock(EntityDefinitionMapper.class),
                mock(EntityFormMapper.class),
                mock(EntityListConfigMapper.class),
                mock(EntityDefinitionAccessPolicy.class),
                mock(UiConfigurationAccessService.class),
                dataSourceService,
                mock(UiEventBindingSnapshotService.class),
                mock(UiConfigReleaseService.class),
                new JsonDocumentCodec(new ObjectMapper()),
                new ObjectMapper());
    }

    private UiEventBindingSaveRequest sharedRequest(
            List<Map<String, Object>> steps) {
        UiEventBindingSaveRequest request = new UiEventBindingSaveRequest();
        request.setOwnerType("ENTITY");
        request.setOwnerId("entity-1");
        request.setTargetType("OWNER");
        request.setEventCode("DETAIL_LOAD");
        request.setInheritanceMode("INHERIT");
        request.setSteps(steps);
        request.setEnabled(true);
        return request;
    }

    private UiEventBindingSaveRequest formButtonRequest(
            List<Map<String, Object>> steps) {
        UiEventBindingSaveRequest request = new UiEventBindingSaveRequest();
        request.setOwnerType("FORM");
        request.setOwnerId("form-1");
        request.setTargetType("BUTTON");
        request.setTargetKey("generate-report");
        request.setEventCode("FORM_BUTTON_CLICK");
        request.setInheritanceMode("INHERIT");
        request.setSteps(steps);
        request.setEnabled(true);
        return request;
    }

    private Map<String, Object> step(
            String extensionId,
            String operationCode,
            int order) {
        return Map.of(
                "extensionId", extensionId,
                "strategy", "REPLACE",
                "failurePolicy", "STOP",
                "order", order);
    }

    private UiExtensionDefinition interfaceDefinition(
            String id,
            String contextType,
            String kind) {
        UiExtensionDefinition definition = new UiExtensionDefinition();
        definition.setId(id);
        definition.setExtensionType("INTERFACE");
        definition.setInterfaceContextType(contextType);
        definition.setInterfaceKind(kind);
        definition.setEnabled(true);
        definition.setDeleted(0);
        return definition;
    }

    private List<String> extensionIds(Object value) {
        if (!(value instanceof List<?> steps)) {
            return List.of();
        }
        return steps.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(step -> step.get("extensionId"))
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .toList();
    }

    private UiEventBinding binding() {
        UiEventBinding binding = new UiEventBinding();
        binding.setId("binding-1");
        binding.setOwnerType("ENTITY");
        binding.setOwnerId("entity-1");
        binding.setTargetType("OWNER");
        binding.setTargetKey("");
        binding.setEventCode("DETAIL_LOAD");
        binding.setInheritanceMode("INHERIT");
        binding.setRevision(1);
        binding.setEnabled(true);
        binding.setDeleted(0);
        return binding;
    }

    private UiEventBindingSaveRequest updateRequest(
            UiEventBinding current) {
        UiEventBindingSaveRequest request =
                new UiEventBindingSaveRequest();
        request.setId(current.getId());
        request.setExpectedRevision(current.getRevision());
        request.setOwnerType(current.getOwnerType());
        request.setOwnerId(current.getOwnerId());
        request.setTargetType(current.getTargetType());
        request.setTargetKey(current.getTargetKey());
        request.setEventCode(current.getEventCode());
        request.setInheritanceMode("REPLACE");
        request.setSteps(List.of());
        request.setEnabled(true);
        return request;
    }
}
