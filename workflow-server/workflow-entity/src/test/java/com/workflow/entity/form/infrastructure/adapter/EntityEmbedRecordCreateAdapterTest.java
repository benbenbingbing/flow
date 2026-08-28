package com.workflow.entity.form.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.embed.EmbedRecordCreatePort;
import com.workflow.contracts.embed.EmbedRuntimeFormPort;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationPort;
import com.workflow.contracts.entity.mutation.EntityMutationResult;
import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.application.FormSubmissionExecutionContext;
import com.workflow.entity.form.application.PublishedFormSubmissionService;
import com.workflow.entity.form.application.ResolvedEntityFormRelease;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.uniqueness.application.FormUniqueMutationContext;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class EntityEmbedRecordCreateAdapterTest {

    private EntityActionCapabilityService capabilityService;
    private UiConfigReleaseService releaseService;
    private EntityDefinitionMapper definitionMapper;
    private PublishedFormSubmissionService formSubmissionService;
    private EntityMutationPort mutationPort;
    private EntityEmbedRecordCreateAdapter adapter;
    private EntityForm form;

    @BeforeEach
    void setUp() {
        capabilityService = mock(EntityActionCapabilityService.class);
        releaseService = mock(UiConfigReleaseService.class);
        definitionMapper = mock(EntityDefinitionMapper.class);
        formSubmissionService = mock(PublishedFormSubmissionService.class);
        mutationPort = mock(EntityMutationPort.class);
        adapter = new EntityEmbedRecordCreateAdapter(
                capabilityService, releaseService, definitionMapper,
                formSubmissionService, mutationPort,
                new ObjectMapper().findAndRegisterModules());

        form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        form.setNodes(List.of());
        form.setFields(List.of());
        when(releaseService.resolveRuntimeFormRelease(
                "form-1", "form-release-4", 4,
                UiRuntimeResolutionContext.historical(null, null)))
                .thenReturn(new ResolvedEntityFormRelease(
                        form, "form-release-4", 4, true));
        EntityDefinition definition = new EntityDefinition();
        definition.setId("entity-1");
        definition.setEntityCode("work_order");
        definition.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        when(definitionMapper.findByEntityCode("work_order"))
                .thenReturn(Optional.of(definition));
        when(formSubmissionService.applyFormWithRelease(
                eq("form-1"), eq("form-release-4"), eq(4),
                eq("work_order"), isNull(), eq("create"),
                eq(Map.of("title", "forced-value")),
                any(FormSubmissionExecutionContext.class),
                eq(UiRuntimeResolutionContext.historical(null, null))))
                .thenReturn(new PublishedFormSubmissionService.AuthorizedFormApplication(
                        Map.of("title", "applied-value"),
                        "form-release-4", 4,
                        "hotfix-release-5", "effective-hash", "target-1"));
        when(mutationPort.execute(any())).thenReturn(new EntityMutationResult(
                "embed_create_idem-1", "work_order", "record-1",
                EntityMutationOperationType.CREATE,
                Map.of("title", "applied-value"), 1,
                null, true, false));
        UserContext.setCurrentUser("user-1", "zhangsan");
    }

    @AfterEach
    void cleanUp() {
        UserContext.clear();
    }

    @Test
    void rechecksPermissionAndPinnedReleaseThenUsesUnifiedMutationPort() {
        EmbedRecordCreatePort.CreatedRecord result = adapter.create(command());

        assertEquals("record-1", result.recordId());
        assertNull(result.recordVersion());
        verify(capabilityService).requireStandardPermission(
                "work_order", EntityPermissionAction.CREATE);
        verify(releaseService).resolveRuntimeFormRelease(
                "form-1", "form-release-4", 4,
                UiRuntimeResolutionContext.historical(null, null));

        ArgumentCaptor<EntityMutationCommand> captured =
                ArgumentCaptor.forClass(EntityMutationCommand.class);
        verify(mutationPort).execute(captured.capture());
        EntityMutationCommand mutation = captured.getValue();
        assertEquals("embed_create_idem-1", mutation.operationId());
        assertEquals(EntityMutationOperationType.CREATE, mutation.operationType());
        assertEquals(Map.of("data", Map.of("title", "applied-value")),
                mutation.payload());
        assertEquals("form-release-4", mutation.context().extraParams().get(
                FormUniqueMutationContext.FORM_RELEASE_ID));
        assertEquals("hotfix-release-5", mutation.context().extraParams().get(
                FormUniqueMutationContext.FORM_EFFECTIVE_RELEASE_ID));
    }

    @Test
    void permissionDenialStopsBeforeReleaseAndMutation() {
        doThrow(new ForbiddenException("forbidden"))
                .when(capabilityService).requireStandardPermission(
                        "work_order", EntityPermissionAction.CREATE);

        assertThrows(ForbiddenException.class, () -> adapter.create(command()));

        verify(releaseService, never()).resolveRuntimeFormRelease(
                any(), any(), any(), any());
        verifyNoSubmissionOrMutation();
    }

    @Test
    void nonPinnedOrMismatchedReleaseStopsBeforeSubmission() {
        when(releaseService.resolveRuntimeFormRelease(
                "form-1", "form-release-4", 4,
                UiRuntimeResolutionContext.historical(null, null)))
                .thenReturn(new ResolvedEntityFormRelease(
                        form, "form-release-4", 4, false));

        assertThrows(IllegalStateException.class, () -> adapter.create(command()));

        verifyNoSubmissionOrMutation();
    }

    @Test
    void beforeSubmitBindingIsRejectedBeforeIrreversibleProviderCall() {
        form.setDataSourceBindingsDocument(
                "{\"BEFORE_SUBMIT\":{\"serviceId\":\"remote\"}}");

        assertThrows(IllegalStateException.class, () -> adapter.create(command()));

        verifyNoSubmissionOrMutation();
    }

    @Test
    void adapterMustJoinOuterEmbedBusinessTransaction() throws Exception {
        Method method = EntityEmbedRecordCreateAdapter.class.getMethod(
                "create", EmbedRecordCreatePort.CreateCommand.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertEquals(Propagation.MANDATORY, transactional.propagation());
        assertEquals(Exception.class, transactional.rollbackFor()[0]);
    }

    private void verifyNoSubmissionOrMutation() {
        verify(formSubmissionService, never()).applyFormWithRelease(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(mutationPort, never()).execute(any());
    }

    private static EmbedRecordCreatePort.CreateCommand command() {
        return new EmbedRecordCreatePort.CreateCommand(
                new EmbedRuntimeFormPort.Target(
                        "work_order", "form-1", "form-release-4", 4,
                        null, null, null),
                Map.of("title", "forced-value"), "idem-1");
    }
}
