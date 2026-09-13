package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.form.application.EntityFormActionService;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.api.response.UiEventExecutionResult;
import com.workflow.entity.ui.application.EntitySelectionRuntimeService;
import com.workflow.entity.ui.application.UiDataSourceService;
import com.workflow.entity.ui.application.UiEventBindingService;
import com.workflow.entity.ui.application.UiEventRuntimeService;
import com.workflow.entity.ui.application.UiEventExecutionReceiptService;
import com.workflow.entity.ui.application.UiEventValueMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.BusinessForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UiEventRuntimeServiceTest {

    @Test
    void onlyReceiptFacadeOwnsFormButtonTransaction()
            throws NoSuchMethodException {
        Method controllerEntry = UiEventRuntimeService.class
                .getMethod("execute", UiEventExecuteRequest.class);
        Method defaultHandlerEntry = UiEventRuntimeService.class.getMethod(
                "execute", UiEventExecuteRequest.class, Function.class);
        Method receiptEntry = UiEventExecutionReceiptService.class.getMethod(
                "execute",
                UiEventExecuteRequest.class,
                UiEventBindingService.ResolvedEventChain.class,
                Supplier.class);

        assertFalse(controllerEntry.isAnnotationPresent(
                Transactional.class));
        assertFalse(defaultHandlerEntry.isAnnotationPresent(
                Transactional.class));
        assertTrue(receiptEntry.isAnnotationPresent(
                Transactional.class));
        assertEquals(Exception.class,
                receiptEntry.getAnnotation(Transactional.class)
                        .rollbackFor()[0]);
    }

    @Mock
    private UiEventBindingService bindingService;
    @Mock
    private UiDataSourceService dataSourceService;
    @Mock
    private UiEventValueMapper valueMapper;
    @Mock
    private EntitySelectionRuntimeService selectionRuntimeService;
    @Mock
    private SystemAuditPort auditPort;
    @Mock
    private EntityActionCapabilityService actionCapabilityService;
    @Mock
    private EntityFormActionService formActionService;
    @Mock
    private UiEventExecutionReceiptService executionReceiptService;
    @Mock
    private EntityDataDynamicService entityDataService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UiEventRuntimeService service;

    @BeforeEach
    void setUp() {
        service = new UiEventRuntimeService(
                bindingService,
                dataSourceService,
                valueMapper,
                selectionRuntimeService,
                auditPort,
                actionCapabilityService,
                formActionService,
                executionReceiptService,
                entityDataService,
                objectMapper);
        org.mockito.Mockito.lenient().when(
                formActionService.requireCustomButton(
                        any(UiEventExecuteRequest.class),
                        org.mockito.ArgumentMatchers.anyMap(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> {
                    UiEventExecuteRequest request = invocation.getArgument(0);
                    request.setServerPublishedButton(map(
                            "key", request.getTargetKey(),
                            "label", "已发布按钮",
                            "buttonType", "PRIMARY"));
                    if (request.getTaskId() != null) {
                        request.setServerTaskId(request.getTaskId());
                        request.setServerProcessInstanceId(
                                "verified-process-1");
                        return "approve";
                    }
                    return "edit";
                });
    }

    @Test
    void customFormButtonRequiresUpdatePermission() {
        UiEventExecuteRequest request = request("FORM_BUTTON_CLICK", "submit");
        request.setConfigType("FORM");
        request.setConfigId("form-1");
        request.setRequestId("request-1");
        stubPublishedChain(request);
        stubNewReceipt(request);

        service.execute(request);

        verify(formActionService).requireCustomButton(
                request, formSnapshot(), "release-1", 1);
        verify(formActionService, never()).requireCustomButton(request);
        ArgumentCaptor<SystemAuditEvent> audit =
                ArgumentCaptor.forClass(SystemAuditEvent.class);
        verify(auditPort).record(audit.capture());
        assertTrue(audit.getValue().summary().contains(
                "执行自定义按钮主处理"));
        assertFalse(audit.getValue().summary().contains(
                "替代平台处理"));
    }

    @Test
    void formButtonRejectsOwnerTargetBeforeResolvingChain() {
        UiEventExecuteRequest request = formButtonRequest(
                "request-owner-bypass");
        request.setTargetType("OWNER");

        com.workflow.core.error.BusinessForbiddenException error =
                assertThrows(
                        com.workflow.core.error.BusinessForbiddenException.class,
                        () -> service.execute(request));

        assertEquals("UI_EVENT_FORM_BUTTON_TARGET_REQUIRED",
                error.getErrorCode());
        verify(bindingService, never()).resolvePublished(any());
        verify(executionReceiptService, never()).execute(
                any(), any(), any());
    }

    @Test
    void formButtonRejectsEffectiveChainWithoutMainStepBeforeReceipt() {
        UiEventExecuteRequest request = formButtonRequest(
                "request-empty-chain");
        when(bindingService.resolvePublished(request)).thenReturn(
                new UiEventBindingService.ResolvedEventChain(
                        List.of(Map.of(
                                "strategy", "BEFORE",
                                "name", "prepare-input")),
                        "release-1",
                        1,
                        "entity-1",
                        "expense",
                        null,
                        formSnapshot(),
                        "release-1",
                        "effective-hash"));

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> service.execute(request));

        assertEquals("UI_EVENT_FORM_BUTTON_MAIN_STEP_REQUIRED",
                error.getErrorCode());
        assertTrue(error.getMessage().contains("当前为 0 个"));
        verify(executionReceiptService, never()).execute(
                any(), any(), any());
    }

    @Test
    void formButtonRejectsMultipleMainStepsBeforeReceipt() {
        UiEventExecuteRequest request = formButtonRequest(
                "request-multiple-main-steps");
        when(bindingService.resolvePublished(request)).thenReturn(
                new UiEventBindingService.ResolvedEventChain(
                        List.of(
                                Map.of("strategy", "REPLACE", "name", "main-a"),
                                Map.of("strategy", "REPLACE", "name", "main-b")),
                        "release-1",
                        1,
                        "entity-1",
                        "expense",
                        null,
                        formSnapshot(),
                        "release-1",
                        "effective-hash"));

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> service.execute(request));

        assertEquals("UI_EVENT_FORM_BUTTON_MAIN_STEP_REQUIRED",
                error.getErrorCode());
        assertTrue(error.getMessage().contains("当前为 2 个"));
        verify(executionReceiptService, never()).execute(
                any(), any(), any());
    }

    @Test
    void formButtonRejectsConditionalMainStepBeforeReceipt() {
        UiEventExecuteRequest request = formButtonRequest(
                "request-conditional-main-step");
        when(bindingService.resolvePublished(request)).thenReturn(
                new UiEventBindingService.ResolvedEventChain(
                        List.of(Map.of(
                                "strategy", "REPLACE",
                                "name", "conditional-main",
                                "condition", Map.of(
                                        "path", "input.form.status",
                                        "equals", "DRAFT"))),
                        "release-1",
                        1,
                        "entity-1",
                        "expense",
                        null,
                        formSnapshot(),
                        "release-1",
                        "effective-hash"));

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> service.execute(request));

        assertEquals(
                "UI_EVENT_FORM_BUTTON_MAIN_STEP_CONDITION_UNSUPPORTED",
                error.getErrorCode());
        assertTrue(error.getMessage().contains("必须无条件执行"));
        verify(executionReceiptService, never()).execute(
                any(), any(), any());
    }

    @Test
    void formButtonRejectsReservedRawInputBeforeConditionOrMapping() {
        UiEventExecuteRequest request = formButtonRequest(
                "request-raw-task-spoof");
        request.setInput(Map.of(
                "form", Map.of("amount", 10),
                "taskId", "forged-task"));
        stubPublishedChain(request);

        com.workflow.core.error.BusinessForbiddenException error =
                assertThrows(
                        com.workflow.core.error.BusinessForbiddenException.class,
                        () -> service.execute(request));

        assertEquals("UI_DATA_SOURCE_EXECUTION_CONTEXT_SPOOFED",
                error.getErrorCode());
        verify(valueMapper, never()).matches(any(), any());
        verify(valueMapper, never()).apply(any(), any(), any());
        verify(executionReceiptService, never()).execute(
                any(), any(), any());
    }

    @Test
    void customRowButtonUsesExactPublishedButtonAndAuthoritativeRow() {
        UiEventExecuteRequest request = request("ROW_BUTTON_CLICK", "archive");
        request.setRecordId("record-1");
        request.setInput(Map.of(
                "button", Map.of("key", "forged"),
                "row", Map.of("id", "forged"),
                "recordId", "record-forged",
                "note", "kept"));
        request.setContext(Map.of(
                "selectedIds", List.of("forged"),
                "recordId", "record-forged"));
        Map<String, Object> publishedButton = map(
                "key", "archive",
                "type", "custom",
                "customMode", "event",
                "enabled", true,
                "perm", "entity:expense:archive");
        stubPublishedListButtonChain(request, publishedButton);
        EntityDataDTO row = new EntityDataDTO();
        row.setId("record-1");
        row.setStatus("DRAFT");
        when(entityDataService.findAccessibleById(
                "expense", "record-1", "default"))
                .thenReturn(row);

        service.execute(request);

        verify(actionCapabilityService).requirePublishedListButton(
                "expense", "archive", publishedButton, row);
        assertEquals("archive",
                ((Map<?, ?>) request.getInput().get("button")).get("key"));
        assertEquals("record-1",
                ((Map<?, ?>) request.getInput().get("row")).get("id"));
        assertEquals("record-1", request.getInput().get("recordId"));
        assertEquals("record-1", request.getContext().get("recordId"));
        assertEquals(List.of(), request.getContext().get("selectedIds"));
        assertEquals("kept", request.getInput().get("note"));
        verify(actionCapabilityService, never()).requireStandardPermission(
                org.mockito.ArgumentMatchers.anyString(), any());
    }

    @Test
    void builtInButtonCannotExecuteCustomEventChain() {
        UiEventExecuteRequest request = request("ROW_BUTTON_CLICK", "delete");
        request.setRecordId("record-1");
        Map<String, Object> publishedButton = map(
                "key", "delete", "enabled", true);
        stubPublishedListButtonChain(request, publishedButton);

        BusinessForbiddenException error = assertThrows(
                BusinessForbiddenException.class,
                () -> service.execute(request));

        assertEquals("UI_EVENT_LIST_BUTTON_NOT_EXECUTABLE",
                error.getErrorCode());
        verify(entityDataService, never()).findAccessibleById(
                any(), any(), any());
    }

    @Test
    void listButtonRejectsUnknownPublishedKeyWithoutUpdateFallback() {
        UiEventExecuteRequest request = request(
                "ROW_BUTTON_CLICK", "missing");
        request.setRecordId("record-1");
        stubPublishedListButtonChain(request, map(
                "key", "archive", "enabled", true,
                "perm", "entity:expense:archive"));

        BusinessForbiddenException error = assertThrows(
                BusinessForbiddenException.class,
                () -> service.execute(request));

        assertEquals("UI_EVENT_LIST_BUTTON_NOT_FOUND",
                error.getErrorCode());
        verify(entityDataService, never()).findAccessibleById(
                any(), any(), any());
        verify(actionCapabilityService, never()).requireStandardPermission(
                any(), any());
    }

    @Test
    void selectionToolbarRequiresSelectionAndUsesReloadedRows() {
        UiEventExecuteRequest empty = request(
                "TOOLBAR_BUTTON_CLICK", "exportSelected");
        Map<String, Object> button = map(
                "key", "exportSelected",
                "type", "custom",
                "customMode", "event",
                "enabled", true);
        stubPublishedListButtonChain(empty, button);
        BusinessForbiddenException error = assertThrows(
                BusinessForbiddenException.class,
                () -> service.execute(empty));
        assertEquals("UI_EVENT_LIST_SELECTION_REQUIRED",
                error.getErrorCode());

        UiEventExecuteRequest selected = request(
                "TOOLBAR_BUTTON_CLICK", "exportSelected");
        selected.setSelectedIds(List.of("record-1", "record-1", "record-2"));
        stubPublishedListButtonChain(selected, button);
        EntityDataDTO first = new EntityDataDTO();
        first.setId("record-1");
        EntityDataDTO second = new EntityDataDTO();
        second.setId("record-2");
        when(entityDataService.findAccessibleById(
                "expense", "record-1", "default"))
                .thenReturn(first);
        when(entityDataService.findAccessibleById(
                "expense", "record-2", "default"))
                .thenReturn(second);

        service.execute(selected);

        verify(actionCapabilityService).requirePublishedListButton(
                "expense", "exportSelected", button,
                List.of(first, second));
        assertEquals(List.of("record-1", "record-2"),
                selected.getSelectedIds());
        assertEquals(List.of("record-1", "record-2"),
                ((List<?>) selected.getInput().get("selectedRows")).stream()
                        .map(item -> ((Map<?, ?>) item).get("id"))
                        .toList());
    }

    @Test
    void pinnedListButtonUsesTrustedKeyAndOverwritesMappedIdentity() {
        UiEventExecuteRequest request = request(
                "ROW_BUTTON_CLICK", "archive");
        request.setRecordId("record-1");
        request.setRequestId("list-request-1");
        request.setReleaseResolutionToken("verified-token");
        request.setInput(Map.of("note", "kept"));
        request.setContext(Map.of(
                "recordId", "forged-context-record",
                "selectedIds", List.of("forged-context-selection")));
        Map<String, Object> button = map(
                "key", "archive",
                "type", "custom",
                "customMode", "event",
                "enabled", true,
                "perm", "entity:expense:archive");
        stubPublishedListButtonChain(
                request,
                button,
                List.of(map(
                        "strategy", "REPLACE",
                        "serviceId", "service-archive",
                        "operationCode", "archive",
                        "inputMapping", Map.of())));
        EntityDataDTO row = new EntityDataDTO();
        row.setId("record-1");
        when(entityDataService.findAccessibleById(
                "expense", "record-1", "default"))
                .thenReturn(row);
        when(valueMapper.matches(any(), any())).thenReturn(true);
        when(valueMapper.apply(any(), any(), any()))
                .thenAnswer(invocation -> {
                    if (invocation.getArgument(0) instanceof Map<?, ?>) {
                        return map(
                                "recordId", "forged-mapped-record",
                                "selectedIds", List.of("forged-mapped-selection"),
                                "row", Map.of("id", "forged-mapped-row"),
                                "note", "mapped-note");
                    }
                    return invocation.getArgument(2);
                });
        when(dataSourceService.executeOperation(
                org.mockito.ArgumentMatchers.eq("service-archive"),
                org.mockito.ArgumentMatchers.eq("archive"),
                any())).thenReturn(Map.of("archived", true));

        service.execute(request);

        ArgumentCaptor<com.workflow.entity.ui.api.request.UiDataSourceExecuteRequest>
                execute = ArgumentCaptor.forClass(
                com.workflow.entity.ui.api.request.UiDataSourceExecuteRequest.class);
        verify(dataSourceService).executeOperation(
                org.mockito.ArgumentMatchers.eq("service-archive"),
                org.mockito.ArgumentMatchers.eq("archive"),
                execute.capture());
        assertTrue(execute.getValue().isServerPinnedRelease());
        assertTrue(execute.getValue().getServerIdempotencyKey()
                .startsWith("ui-list-button:"));
        assertEquals("record-1",
                execute.getValue().getInput().get("recordId"));
        assertEquals(List.of(),
                execute.getValue().getInput().get("selectedIds"));
        assertEquals("record-1",
                ((Map<?, ?>) execute.getValue().getInput().get("row"))
                        .get("id"));
        assertEquals("mapped-note",
                execute.getValue().getInput().get("note"));
        assertEquals("record-1",
                execute.getValue().getContext().get("recordId"));
        assertEquals(List.of(),
                execute.getValue().getContext().get("selectedIds"));
    }

    @Test
    void listLoadPassesPaginationToInterfaceOperation() {
        UiEventExecuteRequest request = request(
                "LIST_LOAD",
                null);
        request.setTargetType(null);
        request.setInput(Map.of(
                "filters", Map.of("status", "ACTIVE"),
                "pageNum", 3,
                "pageSize", 50));
        when(bindingService.resolvePublished(request)).thenReturn(
                new UiEventBindingService.ResolvedEventChain(
                        List.of(map(
                                "strategy", "REPLACE",
                                "serviceId", "service-1",
                                "operationCode", "query",
                                "inputMapping", Map.of(),
                                "outputMapping", List.of())),
                        "release-1",
                        1,
                        "entity-1",
                        "expense",
                        "default",
                        Map.of()));
        when(valueMapper.matches(any(), any())).thenReturn(true);
        when(valueMapper.apply(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(dataSourceService.executeOperation(
                org.mockito.ArgumentMatchers.eq("service-1"),
                org.mockito.ArgumentMatchers.eq("query"),
                any())).thenReturn(Map.of("records", List.of()));

        service.execute(request);

        org.mockito.ArgumentCaptor<com.workflow.entity.ui.api.request.UiDataSourceExecuteRequest>
                captor = org.mockito.ArgumentCaptor.forClass(
                        com.workflow.entity.ui.api.request.UiDataSourceExecuteRequest.class);
        verify(dataSourceService).executeOperation(
                org.mockito.ArgumentMatchers.eq("service-1"),
                org.mockito.ArgumentMatchers.eq("query"),
                captor.capture());
        assertEquals("OWNER", captor.getValue().getTargetType());
        assertEquals(3, captor.getValue().getPageNum());
        assertEquals(50, captor.getValue().getPageSize());
    }

    @Test
    void nonFormButtonWriteEventKeepsLegacyProviderExecution() {
        UiEventExecuteRequest request = request("DATA_UPDATE", null);
        request.setTargetType("OWNER");
        when(bindingService.resolvePublished(request)).thenReturn(
                new UiEventBindingService.ResolvedEventChain(
                        List.of(map(
                                "strategy", "REPLACE",
                                "serviceId", "service-write",
                                "operationCode", "update",
                                "inputMapping", Map.of(),
                                "outputMapping", List.of())),
                        "release-1",
                        1,
                        "entity-1",
                        "expense",
                        "default",
                        Map.of()));
        when(valueMapper.matches(any(), any())).thenReturn(true);
        when(valueMapper.apply(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(dataSourceService.executeOperation(
                org.mockito.ArgumentMatchers.eq("service-write"),
                org.mockito.ArgumentMatchers.eq("update"),
                any())).thenReturn(Map.of(
                        "updated", true,
                        "message", "legacy message",
                        "effects", List.of(Map.of(
                                "type", "LEGACY_EFFECT",
                                "data", Map.of(
                                        "legacy", "visible")))));

        UiEventExecutionResult result = service.execute(request);

        assertEquals(true,
                ((Map<?, ?>) result.getData()).get("updated"));
        verify(dataSourceService).executeOperation(
                org.mockito.ArgumentMatchers.eq("service-write"),
                org.mockito.ArgumentMatchers.eq("update"),
                any());
        verify(dataSourceService, never()).executePinnedOperation(
                any(), any(), any());
        verify(executionReceiptService, never()).execute(
                any(), any(), any());
        ArgumentCaptor<SystemAuditEvent> audit =
                ArgumentCaptor.forClass(SystemAuditEvent.class);
        verify(auditPort).record(audit.capture());
        Map<?, ?> after = (Map<?, ?>) audit.getValue().afterData();
        assertEquals("legacy message", after.get("message"));
        assertEquals(result.getEffects(), after.get("effects"));
        assertEquals(result.getTrace(), after.get("trace"));
        assertEquals(result.getTrace(), audit.getValue().changedFields());
    }

    @Test
    void nonFormButtonFailureKeepsLegacyAuditDetail() {
        UiEventExecuteRequest request = request("DATA_UPDATE", null);
        request.setTargetType("OWNER");
        when(bindingService.resolvePublished(request)).thenReturn(
                new UiEventBindingService.ResolvedEventChain(
                        List.of(map(
                                "strategy", "REPLACE",
                                "serviceId", "service-write",
                                "operationCode", "update",
                                "inputMapping", Map.of(),
                                "outputMapping", List.of())),
                        "release-1",
                        1,
                        "entity-1",
                        "expense",
                        "default",
                        Map.of()));
        when(valueMapper.matches(any(), any())).thenReturn(true);
        when(valueMapper.apply(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(dataSourceService.executeOperation(
                org.mockito.ArgumentMatchers.eq("service-write"),
                org.mockito.ArgumentMatchers.eq("update"),
                any())).thenThrow(new IllegalStateException(
                        "legacy provider detail"));

        assertThrows(IllegalStateException.class,
                () -> service.execute(request));

        ArgumentCaptor<SystemAuditEvent> audit =
                ArgumentCaptor.forClass(SystemAuditEvent.class);
        verify(auditPort).record(audit.capture());
        assertEquals("legacy provider detail",
                audit.getValue().errorMessage());
        assertTrue(audit.getValue().summary().contains(
                "legacy provider detail"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONTINUE", "EMPTY"})
    void formButtonFallbackTraceDoesNotExposeProviderFailure(
            String failurePolicy) {
        UiEventExecuteRequest request = formButtonRequest(
                "request-sanitized-" + failurePolicy.toLowerCase());
        stubPinnedProviderChain(request, failurePolicy);
        when(dataSourceService.executePinnedOperation(
                org.mockito.ArgumentMatchers.eq("snapshot-json"),
                org.mockito.ArgumentMatchers.eq("snapshot-hash"),
                any(),
                org.mockito.ArgumentMatchers.eq(formSnapshot()),
                org.mockito.ArgumentMatchers.eq("effective-hash")))
                .thenThrow(new IllegalStateException(
                        "apiToken=provider-secret"));

        UiEventExecutionResult result = service.execute(request);

        assertFalse(String.valueOf(result).contains("provider-secret"));
        assertEquals("表单按钮步骤执行失败",
                result.getTrace().get(0).get("message"));
    }

    @Test
    void formButtonStopFailureReturnsStableSanitizedError() {
        UiEventExecuteRequest request = formButtonRequest(
                "request-sanitized-stop");
        stubPinnedProviderChain(request, "STOP");
        when(dataSourceService.executePinnedOperation(
                org.mockito.ArgumentMatchers.eq("snapshot-json"),
                org.mockito.ArgumentMatchers.eq("snapshot-hash"),
                any(),
                org.mockito.ArgumentMatchers.eq(formSnapshot()),
                org.mockito.ArgumentMatchers.eq("effective-hash")))
                .thenThrow(new IllegalStateException(
                        "apiToken=provider-secret"));

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> service.execute(request));

        assertEquals("UI_EVENT_FORM_BUTTON_STEP_FAILED",
                error.getErrorCode());
        assertFalse(error.getMessage().contains("provider-secret"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UI_DATA_SOURCE_DATA_SCOPE_DENIED",
            "UI_DATA_SOURCE_USER_DISABLED",
            "UI_DATA_SOURCE_PERMISSION_PLAN_UNAVAILABLE"})
    void formButtonAuthorizationErrorsCannotBeSwallowed(
            String errorCode) {
        UiEventExecuteRequest request = formButtonRequest(
                "request-auth-" + errorCode.toLowerCase());
        stubPinnedProviderChain(request, "EMPTY");
        when(dataSourceService.executePinnedOperation(
                org.mockito.ArgumentMatchers.eq("snapshot-json"),
                org.mockito.ArgumentMatchers.eq("snapshot-hash"),
                any(),
                org.mockito.ArgumentMatchers.eq(formSnapshot()),
                org.mockito.ArgumentMatchers.eq("effective-hash")))
                .thenThrow(new com.workflow.core.error.BusinessForbiddenException(
                        errorCode,
                        "authorization detail"));

        com.workflow.core.error.BusinessForbiddenException error =
                assertThrows(
                        com.workflow.core.error.BusinessForbiddenException.class,
                        () -> service.execute(request));

        assertEquals(errorCode, error.getErrorCode());
        assertEquals("无权执行表单按钮", error.getMessage());
        assertFalse(error.getMessage().contains("authorization detail"));
    }

    @Test
    void formButtonUnknownForbiddenErrorCannotBeSwallowed() {
        UiEventExecuteRequest request = formButtonRequest(
                "request-auth-future-code");
        stubPinnedProviderChain(request, "CONTINUE");
        when(dataSourceService.executePinnedOperation(
                org.mockito.ArgumentMatchers.eq("snapshot-json"),
                org.mockito.ArgumentMatchers.eq("snapshot-hash"),
                any(),
                org.mockito.ArgumentMatchers.eq(formSnapshot()),
                org.mockito.ArgumentMatchers.eq("effective-hash")))
                .thenThrow(new com.workflow.core.error.BusinessForbiddenException(
                        "FUTURE_AUTHORIZATION_DENIED",
                        "future authorization detail"));

        com.workflow.core.error.BusinessForbiddenException error =
                assertThrows(
                        com.workflow.core.error.BusinessForbiddenException.class,
                        () -> service.execute(request));

        assertEquals("FUTURE_AUTHORIZATION_DENIED", error.getErrorCode());
        assertEquals("无权执行表单按钮", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UI_INTERFACE_PROVIDER_IDENTITY_INVALID",
            "UI_INTERFACE_PROVIDER_IDENTITY_CONFLICT"})
    void providerIdentityConflictCannotBeSwallowedOrLeakMessage(
            String errorCode) {
        UiEventExecuteRequest request = formButtonRequest(
                "request-provider-identity-" + errorCode.toLowerCase());
        stubPinnedProviderChain(request, "CONTINUE");
        when(dataSourceService.executePinnedOperation(
                org.mockito.ArgumentMatchers.eq("snapshot-json"),
                org.mockito.ArgumentMatchers.eq("snapshot-hash"),
                any(),
                org.mockito.ArgumentMatchers.eq(formSnapshot()),
                org.mockito.ArgumentMatchers.eq("effective-hash")))
                .thenThrow(new BusinessConflictException(
                        errorCode,
                        "apiToken=provider-secret"));

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> service.execute(request));

        assertEquals(errorCode, error.getErrorCode());
        assertEquals("表单按钮发布配置不可用，请刷新后重试",
                error.getMessage());
        assertFalse(error.getMessage().contains("provider-secret"));
    }

    @Test
    void plainProviderForbiddenCannotBeSwallowedOrLeakMessage() {
        UiEventExecuteRequest request = formButtonRequest(
                "request-provider-forbidden");
        stubPinnedProviderChain(request, "EMPTY");
        when(dataSourceService.executePinnedOperation(
                org.mockito.ArgumentMatchers.eq("snapshot-json"),
                org.mockito.ArgumentMatchers.eq("snapshot-hash"),
                any(),
                org.mockito.ArgumentMatchers.eq(formSnapshot()),
                org.mockito.ArgumentMatchers.eq("effective-hash")))
                .thenThrow(new com.workflow.core.error.ForbiddenException(
                        "apiToken=provider-secret"));

        com.workflow.core.error.BusinessForbiddenException error =
                assertThrows(
                        com.workflow.core.error.BusinessForbiddenException.class,
                        () -> service.execute(request));

        assertEquals("UI_EVENT_FORM_BUTTON_FORBIDDEN",
                error.getErrorCode());
        assertEquals("无权执行表单按钮", error.getMessage());
    }

    @Test
    void providerSecurityExceptionCannotBeSwallowedOrLeakMessage() {
        UiEventExecuteRequest request = formButtonRequest(
                "request-provider-security");
        stubPinnedProviderChain(request, "CONTINUE");
        when(dataSourceService.executePinnedOperation(
                org.mockito.ArgumentMatchers.eq("snapshot-json"),
                org.mockito.ArgumentMatchers.eq("snapshot-hash"),
                any(),
                org.mockito.ArgumentMatchers.eq(formSnapshot()),
                org.mockito.ArgumentMatchers.eq("effective-hash")))
                .thenThrow(new SecurityException(
                        "apiToken=provider-secret"));

        com.workflow.core.error.BusinessForbiddenException error =
                assertThrows(
                        com.workflow.core.error.BusinessForbiddenException.class,
                        () -> service.execute(request));

        assertEquals("UI_EVENT_FORM_BUTTON_FORBIDDEN",
                error.getErrorCode());
        assertEquals("无权执行表单按钮", error.getMessage());
        assertFalse(error.getMessage().contains("provider-secret"));
    }

    @Test
    void successfulFormButtonReplaySkipsProviderAndReturnsStoredResult() {
        UiEventExecuteRequest request = request(
                "FORM_BUTTON_CLICK", "submit");
        request.setConfigType("FORM");
        request.setConfigId("form-1");
        request.setRequestId("request-replay");
        stubPublishedChain(request);
        UiEventExecutionResult replayed = new UiEventExecutionResult();
        replayed.setRequestId("request-replay");
        replayed.setReplayed(true);
        replayed.setData(Map.of("status", "DONE"));
        replayed.setMessage("secret customer value");
        replayed.setEffects(List.of(Map.of(
                "type", "secret-sensitive-value",
                "data", Map.of("secret", "sensitive-value"))));
        when(executionReceiptService.execute(any(), any(), any()))
                .thenReturn(replayed);

        UiEventExecutionResult result = service.execute(request);

        assertEquals(Map.of("status", "DONE"), result.getData());
        assertEquals(true, result.isReplayed());
        verify(dataSourceService, never()).executePinnedOperation(
                any(), any(), any());
        ArgumentCaptor<SystemAuditEvent> audit =
                ArgumentCaptor.forClass(SystemAuditEvent.class);
        verify(auditPort).record(audit.capture());
        String auditAfter = String.valueOf(audit.getValue().afterData());
        assertFalse(auditAfter.contains("sensitive-value"));
        assertFalse(auditAfter.contains("secret customer value"));
        assertTrue(auditAfter.contains("UNKNOWN"));
    }

    @Test
    void formButtonPassesTrustedReceiptKeyToPinnedProvider() {
        UiEventExecuteRequest request = request(
                "FORM_BUTTON_CLICK", "submit");
        request.setConfigType("FORM");
        request.setConfigId("form-1");
        request.setRequestId("request-provider");
        request.setRecordId("record-a");
        request.setTaskId("task-1");
        request.setSelectedIds(List.of("forged-record-b"));
        request.setSelection(Map.of(
                "recordId", "forged-record-b",
                "label", "伪造选择"));
        request.setContext(map(
                "formId", "form-1",
                "listKey", "default",
                "entityCode", "expense",
                "mode", "forged-view",
                "taskId", "task-1",
                "processInstanceId", "process-1",
                "event-code", "DATA_DELETE",
                "TARGET_type", "OWNER",
                "Target-Key", "forged-button",
                "clientHint", "safe"));
        request.setInput(Map.of(
                "recordId", "forged-record-b",
                "mode", "forged-view",
                "button", Map.of(
                        "key", "submit",
                        "label", "伪造按钮",
                        "buttonType", "DANGER"),
                "task", Map.of(
                        "name", "伪造待办展示",
                        "processName", "伪造流程"),
                "form", Map.of(
                        "entityCode", "business-field-value",
                        "userId", "business-field-value")));
        when(bindingService.resolvePublished(request)).thenReturn(
                new UiEventBindingService.ResolvedEventChain(
                        List.of(map(
                                "strategy", "REPLACE",
                                "serviceId", "service-1",
                                "operationCode", "generate",
                                "operationSnapshotVersion", 1,
                                "sourceCode", "service-code",
                                "serviceRevision", 3,
                                "executableSnapshot", "snapshot-json",
                                "definitionHash", "snapshot-hash",
                                "bindingOwnerType", "FORM",
                                "bindingOwnerId", "form-1",
                                "bindingTargetType", "BUTTON",
                                "bindingTargetKey", "submit")),
                        "release-1",
                        1,
                        "entity-1",
                        "expense",
                        null,
                        formSnapshot(),
                        "release-1",
                        "effective-hash"));
        stubNewReceipt(request);
        when(valueMapper.matches(any(), any())).thenReturn(true);
        when(valueMapper.apply(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(dataSourceService.executePinnedOperation(
                org.mockito.ArgumentMatchers.eq("snapshot-json"),
                org.mockito.ArgumentMatchers.eq("snapshot-hash"),
                any(),
                org.mockito.ArgumentMatchers.eq(formSnapshot()),
                org.mockito.ArgumentMatchers.eq("effective-hash")))
                .thenReturn(Map.of("status", "DONE"));

        service.execute(request);

        ArgumentCaptor<com.workflow.entity.ui.api.request.UiDataSourceExecuteRequest>
                execute = ArgumentCaptor.forClass(
                        com.workflow.entity.ui.api.request.UiDataSourceExecuteRequest.class);
        verify(dataSourceService).executePinnedOperation(
                org.mockito.ArgumentMatchers.eq("snapshot-json"),
                org.mockito.ArgumentMatchers.eq("snapshot-hash"),
                execute.capture(),
                org.mockito.ArgumentMatchers.eq(formSnapshot()),
                org.mockito.ArgumentMatchers.eq("effective-hash"));
        assertEquals("trusted-key",
                execute.getValue().getServerIdempotencyKey());
        assertEquals("FORM",
                execute.getValue().getServerBindingOwnerType());
        assertEquals("BUTTON",
                execute.getValue().getServerBindingTargetType());
        assertEquals("record-a",
                execute.getValue().getServerRecordId());
        assertEquals("approve",
                execute.getValue().getServerFormMode());
        assertEquals("task-1",
                execute.getValue().getServerTaskId());
        assertEquals("verified-process-1",
                execute.getValue().getServerProcessInstanceId());
        assertEquals("record-a",
                execute.getValue().getInput().get("recordId"));
        assertEquals("approve",
                execute.getValue().getInput().get("mode"));
        assertEquals("已发布按钮",
                ((Map<?, ?>) execute.getValue().getInput().get("button"))
                        .get("label"));
        assertEquals("PRIMARY",
                ((Map<?, ?>) execute.getValue().getInput().get("button"))
                        .get("buttonType"));
        assertEquals(Map.of(
                        "entityCode", "business-field-value",
                        "userId", "business-field-value"),
                execute.getValue().getInput().get("form"));
        Map<String, Object> providerContext =
                execute.getValue().getContext();
        assertEquals("approve", providerContext.get("mode"));
        assertFalse(providerContext.containsKey("clientHint"));
        assertFalse(providerContext.containsKey("taskId"));
        assertFalse(providerContext.containsKey("processInstanceId"));
        assertFalse(providerContext.containsKey("formId"));
        assertFalse(providerContext.containsKey("listKey"));
        assertFalse(providerContext.containsKey("entityCode"));
        Map<?, ?> eventState = (Map<?, ?>) providerContext.get(
                "eventState");
        assertFalse(eventState.containsKey("input"));
        assertFalse(eventState.containsKey("data"));
        assertFalse(eventState.containsKey("context"));
        assertFalse(eventState.containsKey("result"));
        assertEquals(List.of(), eventState.get("selectedIds"));
        assertEquals(false, eventState.get("selectionPresent"));
        ArgumentCaptor<Map<String, Object>> conditionState =
                ArgumentCaptor.forClass(Map.class);
        verify(valueMapper).matches(any(), conditionState.capture());
        Map<?, ?> conditionInput = (Map<?, ?>) conditionState.getValue()
                .get("input");
        assertEquals("record-a", conditionInput.get("recordId"));
        assertEquals("approve", conditionInput.get("mode"));
        assertEquals("已发布按钮",
                ((Map<?, ?>) conditionInput.get("button")).get("label"));
        assertFalse(conditionInput.containsKey("task"));
        assertFalse(conditionInput.containsKey("taskId"));
        Map<?, ?> conditionContext = (Map<?, ?>) conditionState.getValue()
                .get("context");
        assertEquals("approve", conditionContext.get("mode"));
        assertEquals("record-a", conditionContext.get("recordId"));
        assertEquals("FORM_BUTTON_CLICK",
                conditionContext.get("eventCode"));
        assertEquals("BUTTON", conditionContext.get("targetType"));
        assertEquals("submit", conditionContext.get("targetKey"));
        assertFalse(conditionContext.containsKey("clientHint"));
        assertFalse(conditionContext.containsKey("formId"));
        assertFalse(conditionContext.containsKey("listKey"));
        assertFalse(conditionContext.containsKey("entityCode"));
        assertFalse(conditionContext.containsKey("taskId"));
        assertFalse(conditionContext.containsKey("processInstanceId"));
        assertEquals(List.of(), conditionState.getValue().get("selectedIds"));
        assertNull(conditionState.getValue().get("selection"));
        verify(dataSourceService).validatePinnedReadOperation(
                "snapshot-json",
                "snapshot-hash",
                "service-1",
                "service-code",
                3,
                "generate",
                "FORM");
        verify(executionReceiptService).execute(
                org.mockito.ArgumentMatchers.same(request), any(), any());
    }

    @Test
    void legacyUnpinnedReadStepIsFrozenAndValidatedForThisExecution() {
        UiEventExecuteRequest request = formButtonRequest(
                "request-legacy");
        when(bindingService.resolvePublished(request)).thenReturn(
                new UiEventBindingService.ResolvedEventChain(
                        List.of(Map.of(
                                "strategy", "REPLACE",
                                "serviceId", "service-legacy",
                                "operationCode", "query",
                                "bindingOwnerType", "ENTITY",
                                "bindingOwnerId", "entity-1",
                                "bindingTargetType", "OWNER",
                                "bindingTargetKey", "")),
                        "release-legacy",
                        2,
                        "entity-1",
                        "expense",
                        null,
                        formSnapshot(),
                        "hotfix-2",
                        "effective-hash"));
        stubNewReceipt(request);
        UiDataSourceService.PublishedOperationSnapshot frozen =
                new UiDataSourceService.PublishedOperationSnapshot(
                        "service-legacy",
                        "legacy-source",
                        9,
                        "query",
                        "legacy-snapshot",
                        "legacy-hash");
        when(dataSourceService.freezeOperation(
                "service-legacy", "query")).thenReturn(frozen);
        when(valueMapper.matches(any(), any())).thenReturn(true);
        when(valueMapper.apply(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(dataSourceService.executePinnedOperation(
                org.mockito.ArgumentMatchers.eq("legacy-snapshot"),
                org.mockito.ArgumentMatchers.eq("legacy-hash"),
                any(),
                org.mockito.ArgumentMatchers.eq(formSnapshot()),
                org.mockito.ArgumentMatchers.eq("effective-hash")))
                .thenReturn(Map.of("records", List.of()));

        service.execute(request);

        verify(dataSourceService).freezeOperation(
                "service-legacy", "query");
        verify(dataSourceService).validatePinnedReadOperation(
                "legacy-snapshot",
                "legacy-hash",
                "service-legacy",
                "legacy-source",
                9,
                "query",
                "FORM");
        verify(dataSourceService).executePinnedOperation(
                org.mockito.ArgumentMatchers.eq("legacy-snapshot"),
                org.mockito.ArgumentMatchers.eq("legacy-hash"),
                any(),
                org.mockito.ArgumentMatchers.eq(formSnapshot()),
                org.mockito.ArgumentMatchers.eq("effective-hash"));
    }

    @Test
    void partiallyPinnedStepFailsClosedWithoutProviderExecution() {
        UiEventExecuteRequest request = formButtonRequest(
                "request-partial-pin");
        when(bindingService.resolvePublished(request)).thenReturn(
                new UiEventBindingService.ResolvedEventChain(
                        List.of(map(
                                "strategy", "REPLACE",
                                "serviceId", "service-1",
                                "operationCode", "query",
                                "operationSnapshotVersion", 1,
                                "sourceCode", "service-code",
                                "serviceRevision", 3,
                                "executableSnapshot", "snapshot-json",
                                "bindingOwnerType", "FORM",
                                "bindingOwnerId", "form-1",
                                "bindingTargetType", "BUTTON",
                                "bindingTargetKey", "submit",
                                "failurePolicy", "EMPTY")),
                        "release-1",
                        1,
                        "entity-1",
                        "expense",
                        null,
                        Map.of()));
        stubNewReceipt(request);
        when(valueMapper.matches(any(), any())).thenReturn(true);
        when(valueMapper.apply(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> service.execute(request));

        assertEquals("UI_EVENT_PINNED_OPERATION_INVALID",
                error.getErrorCode());
        verify(dataSourceService, never()).freezeOperation(any(), any());
        verify(dataSourceService, never()).validatePinnedReadOperation(
                any(), any(), any(), any(), any(), any(), any());
        verify(dataSourceService, never()).executePinnedOperation(
                any(), any(), any());
    }

    @Test
    void pinnedHashFailureNeverExecutesProvider() {
        UiEventExecuteRequest request = formButtonRequest(
                "request-bad-hash");
        when(bindingService.resolvePublished(request)).thenReturn(
                new UiEventBindingService.ResolvedEventChain(
                        List.of(map(
                                "strategy", "REPLACE",
                                "serviceId", "service-1",
                                "operationCode", "query",
                                "operationSnapshotVersion", 1,
                                "sourceCode", "service-code",
                                "serviceRevision", 3,
                                "executableSnapshot", "snapshot-json",
                                "definitionHash", "tampered-hash",
                                "bindingOwnerType", "FORM",
                                "bindingOwnerId", "form-1",
                                "bindingTargetType", "BUTTON",
                                "bindingTargetKey", "submit",
                                "failurePolicy", "CONTINUE")),
                        "release-1",
                        1,
                        "entity-1",
                        "expense",
                        null,
                        Map.of()));
        stubNewReceipt(request);
        when(valueMapper.matches(any(), any())).thenReturn(true);
        when(valueMapper.apply(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        org.mockito.Mockito.doThrow(new BusinessConflictException(
                        "UI_INTERFACE_PINNED_HASH_CONFLICT",
                        "快照哈希不匹配"))
                .when(dataSourceService)
                .validatePinnedReadOperation(
                        "snapshot-json",
                        "tampered-hash",
                        "service-1",
                        "service-code",
                        3,
                        "query",
                        "FORM");

        assertThrows(
                BusinessConflictException.class,
                () -> service.execute(request));

        verify(dataSourceService, never()).executePinnedOperation(
                any(), any(), any());
        ArgumentCaptor<SystemAuditEvent> audit =
                ArgumentCaptor.forClass(SystemAuditEvent.class);
        verify(auditPort).record(audit.capture());
        assertEquals("BusinessConflictException",
                audit.getValue().errorMessage());
        assertFalse(audit.getValue().summary().contains(
                "快照哈希不匹配"));
    }

    private UiEventExecuteRequest request(String eventCode, String targetKey) {
        UiEventExecuteRequest request = new UiEventExecuteRequest();
        request.setEventCode(eventCode);
        request.setConfigType("LIST");
        request.setConfigId("list-1");
        request.setTargetType("BUTTON");
        request.setTargetKey(targetKey);
        request.setInput(Map.of());
        return request;
    }

    private void stubPublishedChain(UiEventExecuteRequest request) {
        when(bindingService.resolvePublished(request)).thenReturn(
                new UiEventBindingService.ResolvedEventChain(
                        "FORM_BUTTON_CLICK".equals(request.getEventCode())
                                ? List.of(Map.of(
                                        "strategy", "REPLACE",
                                        "name", "mapped-default"))
                                : List.of(),
                        "release-1",
                        1,
                        "entity-1",
                        "expense",
                        "default",
                        "FORM_BUTTON_CLICK".equals(request.getEventCode())
                                ? formSnapshot() : Map.of(),
                        "release-1",
                        "FORM_BUTTON_CLICK".equals(request.getEventCode())
                                ? "effective-hash" : null));
    }

    private void stubPublishedListButtonChain(
            UiEventExecuteRequest request,
            Map<String, Object> button) {
        stubPublishedListButtonChain(request, button, List.of());
    }

    private void stubPublishedListButtonChain(
            UiEventExecuteRequest request,
            Map<String, Object> button,
            List<Map<String, Object>> steps) {
        String section = "ROW_BUTTON_CLICK".equals(request.getEventCode())
                ? "rowActionConfig" : "toolbarConfig";
        when(bindingService.resolvePublished(request)).thenReturn(
                new UiEventBindingService.ResolvedEventChain(
                        steps,
                        "release-1",
                        1,
                        "entity-1",
                        "expense",
                        "default",
                        Map.of("list", Map.of(
                                "id", "list-1",
                                section, List.of(button))),
                        "release-1",
                        "effective-hash"));
    }

    private UiEventExecuteRequest formButtonRequest(String requestId) {
        UiEventExecuteRequest request = request(
                "FORM_BUTTON_CLICK", "submit");
        request.setConfigType("FORM");
        request.setConfigId("form-1");
        request.setRequestId(requestId);
        return request;
    }

    private void stubPinnedProviderChain(
            UiEventExecuteRequest request,
            String failurePolicy) {
        when(bindingService.resolvePublished(request)).thenReturn(
                new UiEventBindingService.ResolvedEventChain(
                        List.of(map(
                                "strategy", "REPLACE",
                                "serviceId", "service-1",
                                "operationCode", "query",
                                "operationSnapshotVersion", 1,
                                "sourceCode", "service-code",
                                "serviceRevision", 3,
                                "executableSnapshot", "snapshot-json",
                                "definitionHash", "snapshot-hash",
                                "bindingOwnerType", "FORM",
                                "bindingOwnerId", "form-1",
                                "bindingTargetType", "BUTTON",
                                "bindingTargetKey", "submit",
                                "failurePolicy", failurePolicy)),
                        "release-1",
                        1,
                        "entity-1",
                        "expense",
                        null,
                        formSnapshot(),
                        "release-1",
                        "effective-hash"));
        stubNewReceipt(request);
        when(valueMapper.matches(any(), any())).thenReturn(true);
        when(valueMapper.apply(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
    }

    private Map<String, Object> formSnapshot() {
        return Map.of(
                "configType", "FORM",
                "form", Map.of(
                        "id", "form-1",
                        "entityId", "entity-1"),
                "eventBindings", List.of());
    }

    private static Map<String, Object> map(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], entries[index + 1]);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void stubNewReceipt(UiEventExecuteRequest request) {
        when(executionReceiptService.execute(
                org.mockito.ArgumentMatchers.same(request),
                any(),
                any())).thenAnswer(invocation -> {
            request.setServerIdempotencyKey("trusted-key");
            Supplier<UiEventExecutionResult> action =
                    invocation.getArgument(2);
            UiEventExecutionResult result = action.get();
            result.setRequestId(request.getRequestId());
            result.setReplayed(false);
            return result;
        });
    }
}
