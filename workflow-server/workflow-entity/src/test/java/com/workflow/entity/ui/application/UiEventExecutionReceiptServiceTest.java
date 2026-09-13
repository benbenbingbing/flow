package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.api.response.UiEventExecutionResult;
import com.workflow.entity.version.application.EntityMutationReceiptService;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityMutationReceiptMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityMutationReceipt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UiEventExecutionReceiptServiceTest {

    private final Map<String, EntityMutationReceipt> receipts =
            new LinkedHashMap<>();
    private UiEventExecutionReceiptService service;

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUser("user-a", "Alice");
        EntityMutationReceiptMapper mapper =
                mock(EntityMutationReceiptMapper.class);
        when(mapper.findByIdempotencyKey(anyString()))
                .thenAnswer(invocation -> receipts.get(
                        invocation.getArgument(0)));
        doAnswer(invocation -> {
            EntityMutationReceipt receipt = invocation.getArgument(0);
            receipts.put(receipt.getIdempotencyKey(), receipt);
            return 1;
        }).when(mapper).insert(any(EntityMutationReceipt.class));
        when(mapper.complete(
                anyString(), anyString(), anyString(),
                any(), any(), any()))
                .thenAnswer(invocation -> {
                    EntityMutationReceipt receipt = receipts.get(
                            invocation.getArgument(0));
                    if (receipt == null || !"PENDING".equals(
                            receipt.getStatus())) {
                        return 0;
                    }
                    receipt.setRecordId(invocation.getArgument(1));
                    receipt.setStatus("SUCCESS");
                    receipt.setResultDocument(invocation.getArgument(2));
                    receipt.setChanged(invocation.getArgument(5));
                    return 1;
                });
        SysUserService userService = mock(SysUserService.class);
        when(userService.getById(anyString()))
                .thenAnswer(invocation -> {
                    SysUser user = new SysUser();
                    user.setId(invocation.getArgument(0));
                    user.setOrgId("tenant-1");
                    return user;
                });
        service = new UiEventExecutionReceiptService(
                new EntityMutationReceiptService(
                        mapper, new ObjectMapper()),
                userService,
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void exactRequestReplaysStoredEventResult() {
        UiEventExecuteRequest request = request(
                "request-1", Map.of("recordId", "record-1"));
        UiEventExecutionReceiptService.AcquireResult first =
                service.acquire(request, chain());
        assertNull(first.replayedResult());
        UiEventExecutionResult result = new UiEventExecutionResult();
        result.setRequestId("request-1");
        result.setData(Map.of("status", "DONE"));
        result.setMessage("已完成");
        result.setEffects(List.of(Map.of(
                "type", "REFRESH_PARENT")));
        service.complete(first.receiptCommand(), result);

        UiEventExecutionReceiptService.AcquireResult replay =
                service.acquire(request(
                        "request-1",
                        Map.of("recordId", "record-1")), chain());

        assertTrue(replay.replayedResult().isReplayed());
        assertEquals("request-1", replay.replayedResult().getRequestId());
        assertEquals(Map.of("status", "DONE"),
                replay.replayedResult().getData());
        assertEquals("REFRESH_PARENT",
                replay.replayedResult().getEffects().get(0).get("type"));
        assertEquals(
                first.receiptCommand().context().idempotencyKey(),
                replay.receiptCommand().context().idempotencyKey());
    }

    @Test
    void pendingDuplicateReturnsExplicitInProgressConflict() {
        service.acquire(request(
                "request-pending", Map.of("value", 1)), chain());

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> service.acquire(request(
                        "request-pending", Map.of("value", 1)), chain()));

        assertEquals("UI_EVENT_REQUEST_IN_PROGRESS", error.getErrorCode());
        assertEquals(1, receipts.size());
    }

    @Test
    void sameRequestIdCannotChangePayload() {
        UiEventExecutionReceiptService.AcquireResult first =
                service.acquire(request(
                        "request-conflict", Map.of("value", 1)), chain());
        service.complete(
                first.receiptCommand(), new UiEventExecutionResult());

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> service.acquire(request(
                        "request-conflict", Map.of("value", 2)), chain()));

        assertEquals("UI_EVENT_REQUEST_CONFLICT", error.getErrorCode());
        assertEquals(1, receipts.size());
    }

    @Test
    void sameClientRequestFromAnotherUserGetsIndependentClaim() {
        UiEventExecutionReceiptService.AcquireResult first =
                service.acquire(request(
                        "request-shared", Map.of()), chain());
        UserContext.setCurrentUser("user-b", "Bob");

        UiEventExecutionReceiptService.AcquireResult other =
                service.acquire(request(
                        "request-shared", Map.of()), chain());

        assertFalse(first.receiptCommand().context().idempotencyKey()
                .equals(other.receiptCommand().context().idempotencyKey()));
        assertEquals(2, receipts.size());
    }

    @Test
    void usernameChangeDoesNotAlterSameUsersReceiptSemantics() {
        UiEventExecutionReceiptService.AcquireResult first =
                service.acquire(request(
                        "request-rename", Map.of()), chain());
        service.complete(
                first.receiptCommand(), new UiEventExecutionResult());
        UserContext.setCurrentUser("user-a", "Alice Renamed");

        UiEventExecutionReceiptService.AcquireResult replay =
                service.acquire(request(
                        "request-rename", Map.of()), chain());

        assertTrue(replay.replayedResult().isReplayed());
        assertEquals(
                first.receiptCommand().context().idempotencyKey(),
                replay.receiptCommand().context().idempotencyKey());
    }

    @Test
    void sameRequestIdUsesIndependentClaimAfterEffectiveHotfixChanges() {
        UiEventExecuteRequest request = request(
                "request-hotfix", Map.of());
        UiEventExecutionReceiptService.AcquireResult first =
                service.acquire(request, chain(
                        "hotfix-1", "effective-hash-1"));
        service.complete(
                first.receiptCommand(), new UiEventExecutionResult());

        UiEventExecutionReceiptService.AcquireResult changed =
                service.acquire(
                        request("request-hotfix", Map.of()),
                        chain("hotfix-2", "effective-hash-2"));

        assertNull(changed.replayedResult());
        assertFalse(first.receiptCommand().context().idempotencyKey()
                .equals(changed.receiptCommand().context()
                        .idempotencyKey()));
        assertEquals(2, receipts.size());
    }

    @Test
    void sameRequestIdCannotReplayResultAcrossAuthorizedTasks() {
        UiEventExecuteRequest firstRequest = request(
                "request-task-bound", Map.of());
        firstRequest.setServerTaskId("task-r1");
        UiEventExecutionReceiptService.AcquireResult first =
                service.acquire(firstRequest, chain());
        service.complete(
                first.receiptCommand(), new UiEventExecutionResult());

        UiEventExecuteRequest secondRequest = request(
                "request-task-bound", Map.of());
        secondRequest.setServerTaskId("task-r2");
        UiEventExecutionReceiptService.AcquireResult second =
                service.acquire(secondRequest, chain());

        assertNull(second.replayedResult());
        assertFalse(first.receiptCommand().context().idempotencyKey()
                .equals(second.receiptCommand().context()
                        .idempotencyKey()));
        assertEquals("task-r2",
                second.receiptCommand().payload()
                        .get("authorizedTaskId"));
    }

    @Test
    void malformedClientRequestIdIsRejectedBeforeClaim() {
        UiEventExecuteRequest request = request("bad request", Map.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.acquire(request, chain()));
        assertTrue(receipts.isEmpty());
    }

    @Test
    void facadeDoesNotCompleteReceiptWhenEventExecutionFails() {
        EntityMutationReceiptService receiptService =
                mock(EntityMutationReceiptService.class);
        SysUserService userService = mock(SysUserService.class);
        SysUser user = new SysUser();
        user.setId("user-a");
        user.setOrgId("tenant-1");
        when(userService.getById("user-a")).thenReturn(user);
        UiEventExecutionReceiptService facade =
                new UiEventExecutionReceiptService(
                        receiptService,
                        userService,
                        new ObjectMapper());
        UiEventExecuteRequest request = request(
                "request-failed", Map.of());

        assertThrows(IllegalStateException.class,
                () -> facade.execute(
                        request,
                        chain(),
                        () -> {
                            throw new IllegalStateException(
                                    "provider failed");
                        }));

        verify(receiptService).acquire(any());
        verify(receiptService, never()).complete(any(), any());
    }

    private UiEventExecuteRequest request(
            String requestId,
            Map<String, Object> input) {
        UiEventExecuteRequest request = new UiEventExecuteRequest();
        request.setEventCode("FORM_BUTTON_CLICK");
        request.setConfigType("FORM");
        request.setConfigId("form-1");
        request.setTargetType("BUTTON");
        request.setTargetKey("generate-report");
        request.setRequestId(requestId);
        request.setRecordId("record-1");
        request.setInput(input);
        return request;
    }

    private UiEventBindingService.ResolvedEventChain chain() {
        return chain("release-1", "effective-hash-1");
    }

    private UiEventBindingService.ResolvedEventChain chain(
            String effectiveReleaseId,
            String effectiveContentHash) {
        Map<String, Object> snapshot = Map.of(
                "configType", "FORM",
                "form", Map.of(
                        "id", "form-1",
                        "entityId", "entity-1"));
        return new UiEventBindingService.ResolvedEventChain(
                List.of(),
                "release-1",
                3,
                "entity-1",
                "expense",
                null,
                snapshot,
                effectiveReleaseId,
                effectiveContentHash);
    }
}
