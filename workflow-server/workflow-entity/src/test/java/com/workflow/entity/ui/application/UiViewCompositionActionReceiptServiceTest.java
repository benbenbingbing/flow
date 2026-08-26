package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.ui.api.response.UiViewCompositionActionResponse;
import com.workflow.entity.ui.api.response.UiViewCompositionChangedReferenceDTO;
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
import static org.mockito.Mockito.when;

class UiViewCompositionActionReceiptServiceTest {

    private final Map<String, EntityMutationReceipt> receipts =
            new LinkedHashMap<>();
    private UiViewCompositionActionReceiptService service;

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUser("user-a", "Alice");
        EntityMutationReceiptMapper mapper =
                mock(EntityMutationReceiptMapper.class);
        when(mapper.findByIdempotencyKey(anyString()))
                .thenAnswer(invocation ->
                        receipts.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            EntityMutationReceipt receipt = invocation.getArgument(0);
            receipts.put(receipt.getIdempotencyKey(), receipt);
            return 1;
        }).when(mapper).insert(any(EntityMutationReceipt.class));
        when(mapper.complete(
                anyString(),
                anyString(),
                anyString(),
                any(),
                any(),
                any()))
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
                    receipt.setVersionNo(invocation.getArgument(3));
                    receipt.setVersionScenarioCode(invocation.getArgument(4));
                    receipt.setChanged(invocation.getArgument(5));
                    return 1;
                });
        EntityMutationReceiptService receiptService =
                new EntityMutationReceiptService(
                        mapper, new ObjectMapper());
        SysUserService userService = mock(SysUserService.class);
        when(userService.getById(anyString()))
                .thenAnswer(invocation -> {
                    SysUser user = new SysUser();
                    user.setId(invocation.getArgument(0));
                    user.setOrgId("tenant-1");
                    return user;
                });
        service = new UiViewCompositionActionReceiptService(
                receiptService, userService, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void exactSortedTargetSetReplaysWholeBatch() {
        UiViewCompositionActionReceiptService.AcquireResult first = acquire(
                "LINK", List.of("target-2", "target-1"), "operation-1");
        assertNull(first.replayedResponse());
        service.complete(first.receiptCommand(), response(
                "LINK", "operation-1", List.of("target-2", "target-1")));

        UiViewCompositionActionReceiptService.AcquireResult replay = acquire(
                "LINK", List.of("target-1", "target-2"), "operation-1");

        assertTrue(replay.replayedResponse().isReplayed());
        assertEquals(2, replay.replayedResponse()
                .getChangedReferences().size());
        assertTrue(replay.replayedResponse()
                .getChangedReferences().stream()
                .allMatch(UiViewCompositionChangedReferenceDTO::isReplayed));
    }

    @Test
    void sameOperationRejectsActionOrTargetSetChangeBeforeNewReceipt() {
        UiViewCompositionActionReceiptService.AcquireResult first = acquire(
                "LINK", List.of("target-1"), "operation-2");
        service.complete(first.receiptCommand(), response(
                "LINK", "operation-2", List.of("target-1")));

        assertThrows(
                BusinessConflictException.class,
                () -> acquire(
                        "UNLINK", List.of("target-1"), "operation-2"));
        assertThrows(
                BusinessConflictException.class,
                () -> acquire(
                        "LINK", List.of("target-1", "target-2"),
                        "operation-2"));
        assertEquals(1, receipts.size());
    }

    @Test
    void sameClientOperationFromAnotherUserNeverReplaysFirstUsersResult() {
        UiViewCompositionActionReceiptService.AcquireResult first = acquire(
                "LINK", List.of("target-1"), "operation-3");
        service.complete(first.receiptCommand(), response(
                "LINK", "operation-3", List.of("target-1")));

        UserContext.setCurrentUser("user-b", "Bob");
        UiViewCompositionActionReceiptService.AcquireResult other = acquire(
                "LINK", List.of("target-1"), "operation-3");

        assertNull(other.replayedResponse());
        assertFalse(first.receiptCommand().context().idempotencyKey()
                .equals(other.receiptCommand().context().idempotencyKey()));
        assertEquals(2, receipts.size());
    }

    private UiViewCompositionActionReceiptService.AcquireResult acquire(
            String action,
            List<String> targets,
            String operationId) {
        return service.acquire(
                "FORM",
                "owner-form",
                "owner-release",
                3,
                "related-targets",
                "source_entity",
                "source-1",
                action,
                targets,
                operationId);
    }

    private UiViewCompositionActionResponse response(
            String action,
            String operationId,
            List<String> targetIds) {
        return UiViewCompositionActionResponse.builder()
                .operationId(operationId)
                .action(action)
                .sourceRecordId("source-1")
                .changedReferences(targetIds.stream()
                        .map(targetId ->
                                UiViewCompositionChangedReferenceDTO.builder()
                                        .sourceRecordId("source-1")
                                        .targetRecordId(targetId)
                                        .relationType("REVERSE_REFERENCE")
                                        .linked(true)
                                        .changed(true)
                                        .replayed(false)
                                        .build())
                        .toList())
                .build();
    }
}
