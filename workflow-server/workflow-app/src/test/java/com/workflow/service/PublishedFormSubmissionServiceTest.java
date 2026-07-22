package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.dto.UiDataSourceExecuteRequest;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import com.workflow.entity.EntityFormNode;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFormMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishedFormSubmissionServiceTest {

    @Test
    void executesNodeBeforeSubmitOnceAndMergesResponse() {
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        UiDataSourceService dataSourceService =
                mock(UiDataSourceService.class);
        JsonDocumentCodec codec =
                new JsonDocumentCodec(new ObjectMapper());
        PublishedFormSubmissionService service =
                new PublishedFormSubmissionService(
                        mock(EntityDefinitionMapper.class),
                        mock(EntityFormMapper.class),
                        releaseService,
                        dataSourceService,
                        codec);

        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        EntityFormField derivedField = new EntityFormField();
        derivedField.setDataSourceBindings(Map.of(
                "BEFORE_SUBMIT",
                Map.of("sourceId", "source-1")));
        EntityFormNode node = new EntityFormNode();
        node.setId("node-1");
        node.setDataSourceBindingsDocument(
                """
                {"BEFORE_SUBMIT":{
                  "sourceId":"source-1",
                  "inputMapping":{
                    "payload.amount":"data.amount",
                    "payload.mode":"context.mode"
                  },
                  "outputMapping":{
                    "normalized":"data.result.valid"
                  }
                }}
                """);
        form.setFields(List.of(derivedField));
        form.setNodes(List.of(node));
        when(releaseService.resolveRuntimeFormRelease(
                "form-1",
                null,
                null))
                .thenReturn(resolution(
                        form,
                        "release-1",
                        1));
        when(dataSourceService.execute(
                eq("source-1"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of(
                        "result", Map.of("valid", true)));

        Map<String, Object> result = service.applyForm(
                "form-1",
                "expense",
                "record-1",
                "edit",
                Map.of("amount", 88),
                executionContext("trace-1"));

        assertEquals(
                Map.of("amount", 88, "normalized", true),
                result);
        ArgumentCaptor<UiDataSourceExecuteRequest> captor =
                ArgumentCaptor.forClass(
                        UiDataSourceExecuteRequest.class);
        verify(dataSourceService, times(1))
                .execute(eq("source-1"), captor.capture());
        assertEquals("BEFORE_SUBMIT", captor.getValue().getUsage());
        assertEquals("expense", captor.getValue().getEntityCode());
        assertEquals(
                "release-1",
                captor.getValue().getReleaseId());
        assertEquals(
                1,
                captor.getValue().getReleaseVersion());
        assertFalse(captor.getValue().isServerPinnedRelease());
        assertEquals(
                Map.of(
                        "amount", 88,
                        "mode", "edit"),
                captor.getValue().getInput().get("payload"));
        assertEquals(
                "trace-1",
                captor.getValue().getInput().get(
                        "businessTraceKey"));
        assertTrue(String.valueOf(
                captor.getValue().getInput().get(
                        "idempotencyKey")).startsWith("fbs_"));
        assertEquals(
                captor.getValue().getInput().get(
                        "idempotencyKey"),
                captor.getValue().getContext().get(
                        "idempotencyKey"));
        assertEquals(
                "node:node-1",
                captor.getValue().getContext().get(
                        "bindingOwner"));
    }

    @Test
    void fallsBackToLegacyFieldsWhenPublishedNodesAreAbsent() {
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        UiDataSourceService dataSourceService =
                mock(UiDataSourceService.class);
        PublishedFormSubmissionService service =
                new PublishedFormSubmissionService(
                        mock(EntityDefinitionMapper.class),
                        mock(EntityFormMapper.class),
                        releaseService,
                        dataSourceService,
                        new JsonDocumentCodec(new ObjectMapper()));

        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        EntityFormField field = new EntityFormField();
        field.setDataSourceBindings(Map.of(
                "BEFORE_SUBMIT", "source-1"));
        form.setFields(List.of(field));
        form.setNodes(List.of());
        when(releaseService.resolveRuntimeFormRelease(
                "form-1",
                null,
                null))
                .thenReturn(resolution(
                        form,
                        "release-1",
                        1));
        when(dataSourceService.execute(
                eq("source-1"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of("approved", true));

        Map<String, Object> result = service.applyForm(
                "form-1",
                "expense",
                null,
                "create",
                Map.of("amount", 88));

        assertEquals(
                Map.of("amount", 88, "approved", true),
                result);
        verify(dataSourceService, times(1))
                .execute(
                        eq("source-1"),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reusesBindingIdempotencyKeyForSameBusinessSubmission() {
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        UiDataSourceService dataSourceService =
                mock(UiDataSourceService.class);
        PublishedFormSubmissionService service =
                new PublishedFormSubmissionService(
                        mock(EntityDefinitionMapper.class),
                        mock(EntityFormMapper.class),
                        releaseService,
                        dataSourceService,
                        new JsonDocumentCodec(
                                new ObjectMapper()));

        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        EntityFormNode first = node(
                "node-1",
                "source-1");
        EntityFormNode second = node(
                "node-2",
                "source-1");
        form.setNodes(List.of(first, second));
        when(releaseService.resolveRuntimeFormRelease(
                "form-1",
                null,
                null))
                .thenReturn(resolution(
                        form,
                        "release-1",
                        1));
        when(dataSourceService.execute(
                eq("source-1"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of());

        FormSubmissionExecutionContext context =
                executionContext("trace-retry");
        service.applyForm(
                "form-1",
                "expense",
                "record-1",
                "edit",
                Map.of("amount", 88),
                context);
        service.applyForm(
                "form-1",
                "expense",
                "record-1",
                "edit",
                Map.of("amount", 88),
                context);

        ArgumentCaptor<UiDataSourceExecuteRequest> captor =
                ArgumentCaptor.forClass(
                        UiDataSourceExecuteRequest.class);
        verify(dataSourceService, times(4))
                .execute(eq("source-1"), captor.capture());
        String firstAttemptFirstBinding =
                idempotencyKey(captor.getAllValues().get(0));
        String firstAttemptSecondBinding =
                idempotencyKey(captor.getAllValues().get(1));
        String retryFirstBinding =
                idempotencyKey(captor.getAllValues().get(2));
        String retrySecondBinding =
                idempotencyKey(captor.getAllValues().get(3));
        assertEquals(
                firstAttemptFirstBinding,
                retryFirstBinding);
        assertEquals(
                firstAttemptSecondBinding,
                retrySecondBinding);
        assertNotEquals(
                firstAttemptFirstBinding,
                firstAttemptSecondBinding);
    }

    @Test
    void preservesFailClosedDataSourceFailure() {
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        UiDataSourceService dataSourceService =
                mock(UiDataSourceService.class);
        PublishedFormSubmissionService service =
                new PublishedFormSubmissionService(
                        mock(EntityDefinitionMapper.class),
                        mock(EntityFormMapper.class),
                        releaseService,
                        dataSourceService,
                        new JsonDocumentCodec(
                                new ObjectMapper()));

        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        form.setNodes(List.of(node(
                "node-1",
                "source-1")));
        when(releaseService.resolveRuntimeFormRelease(
                "form-1",
                null,
                null))
                .thenReturn(resolution(
                        form,
                        "release-1",
                        1));
        when(dataSourceService.execute(
                eq("source-1"),
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException(
                        "validation failed"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.applyForm(
                        "form-1",
                        "expense",
                        "record-1",
                        "edit",
                        Map.of("amount", 88),
                        executionContext("trace-fail")));

        assertEquals(
                "validation failed",
                exception.getMessage());
    }

    @Test
    void resolvesAndExecutesTheExactPinnedRelease() {
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        UiDataSourceService dataSourceService =
                mock(UiDataSourceService.class);
        PublishedFormSubmissionService service =
                new PublishedFormSubmissionService(
                        mock(EntityDefinitionMapper.class),
                        mock(EntityFormMapper.class),
                        releaseService,
                        dataSourceService,
                        new JsonDocumentCodec(
                                new ObjectMapper()));

        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        form.setNodes(List.of(node(
                "node-1",
                "source-1")));
        when(releaseService.resolveRuntimeFormRelease(
                "form-1",
                "release-7",
                7))
                .thenReturn(resolution(
                        form,
                        "release-7",
                        7,
                        true));
        when(dataSourceService.execute(
                eq("source-1"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of());

        service.applyForm(
                "form-1",
                "release-7",
                7,
                "expense",
                "record-1",
                "approve",
                Map.of("amount", 88),
                executionContext("trace-pinned"));

        ArgumentCaptor<UiDataSourceExecuteRequest> captor =
                ArgumentCaptor.forClass(
                        UiDataSourceExecuteRequest.class);
        verify(dataSourceService).execute(
                eq("source-1"),
                captor.capture());
        assertEquals(
                "release-7",
                captor.getValue().getReleaseId());
        assertEquals(
                7,
                captor.getValue().getReleaseVersion());
        assertTrue(captor.getValue().isServerPinnedRelease());
    }

    @Test
    void executesFormLevelBeforeSubmitBinding() {
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        UiDataSourceService dataSourceService =
                mock(UiDataSourceService.class);
        PublishedFormSubmissionService service =
                new PublishedFormSubmissionService(
                        mock(EntityDefinitionMapper.class),
                        mock(EntityFormMapper.class),
                        releaseService,
                        dataSourceService,
                        new JsonDocumentCodec(
                                new ObjectMapper()));

        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        form.setDataSourceBindingsDocument(
                """
                {"BEFORE_SUBMIT":{"sourceId":"form-source"}}
                """);
        form.setNodes(List.of());
        form.setFields(List.of());
        when(releaseService.resolveRuntimeFormRelease(
                "form-1",
                null,
                null))
                .thenReturn(resolution(
                        form,
                        "release-1",
                        1));
        when(dataSourceService.execute(
                eq("form-source"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of("normalized", true));

        Map<String, Object> result = service.applyForm(
                "form-1",
                "expense",
                "record-1",
                "edit",
                Map.of("amount", 88),
                executionContext("trace-form"));

        assertEquals(
                Map.of(
                        "amount", 88,
                        "normalized", true),
                result);
        ArgumentCaptor<UiDataSourceExecuteRequest> captor =
                ArgumentCaptor.forClass(
                        UiDataSourceExecuteRequest.class);
        verify(dataSourceService).execute(
                eq("form-source"),
                captor.capture());
        assertEquals(
                "form:form-1",
                captor.getValue().getContext().get(
                        "bindingOwner"));
    }

    private EntityFormNode node(
            String id,
            String sourceId) {
        EntityFormNode node = new EntityFormNode();
        node.setId(id);
        node.setDataSourceBindingsDocument(
                "{\"BEFORE_SUBMIT\":{\"sourceId\":\""
                        + sourceId + "\"}}");
        return node;
    }

    private FormSubmissionExecutionContext executionContext(
            String traceKey) {
        return new FormSubmissionExecutionContext(
                traceKey,
                "ENTITY_UPDATE",
                Map.of(
                        "recordId",
                        "record-1"));
    }

    private ResolvedEntityFormRelease resolution(
            EntityForm form,
            String releaseId,
            Integer releaseVersion) {
        return new ResolvedEntityFormRelease(
                form,
                releaseId,
                releaseVersion);
    }

    private ResolvedEntityFormRelease resolution(
            EntityForm form,
            String releaseId,
            Integer releaseVersion,
            boolean pinned) {
        return new ResolvedEntityFormRelease(
                form,
                releaseId,
                releaseVersion,
                pinned);
    }

    private String idempotencyKey(
            UiDataSourceExecuteRequest request) {
        return String.valueOf(
                request.getInput().get("idempotencyKey"));
    }
}
