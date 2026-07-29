package com.workflow.entity.version.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationResult;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityMutationReceiptMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityMutationReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityMutationReceiptServiceTest {

    @Mock
    private EntityMutationReceiptMapper receiptMapper;

    private EntityMutationReceiptService service;

    @BeforeEach
    void setUp() {
        service = new EntityMutationReceiptService(
                receiptMapper,
                new ObjectMapper());
    }

    @Test
    void firstExecutionCreatesPendingReceipt() {
        EntityMutationCommand command = command(
                Map.of("data", Map.of("name", "新名称")));

        assertNull(service.acquire(command));

        ArgumentCaptor<EntityMutationReceipt> captor =
                ArgumentCaptor.forClass(
                        EntityMutationReceipt.class);
        verify(receiptMapper).insert(captor.capture());
        EntityMutationReceipt receipt = captor.getValue();
        assertEquals("mutation-1",
                receipt.getIdempotencyKey());
        assertEquals("PENDING", receipt.getStatus());
        assertEquals("asset", receipt.getEntityCode());
        assertEquals("record-1", receipt.getRecordId());
        assertTrue(receipt.getCommandHash()
                .matches("[0-9a-f]{64}"));
    }

    @Test
    void completedReceiptReplaysOriginalResult() {
        EntityMutationCommand command = command(
                Map.of("data", Map.of("name", "新名称")));
        EntityMutationReceipt receipt =
                insertedReceipt(command);
        receipt.setStatus("SUCCESS");
        receipt.setRecordId("record-1");
        receipt.setResultDocument(
                "{\"data\":{\"name\":\"新名称\"}}");
        receipt.setVersionNo(2);
        receipt.setVersionScenarioCode(
                "CHANGE_EFFECTIVE");
        receipt.setChanged(true);
        when(receiptMapper.findByIdempotencyKey(
                "mutation-1")).thenReturn(receipt);

        EntityMutationResult result =
                service.acquire(command);

        assertTrue(result.replayed());
        assertEquals(2, result.versionNo());
        assertEquals("CHANGE_EFFECTIVE",
                result.versionScenarioCode());
        assertEquals("新名称",
                ((Map<?, ?>) result.record()
                        .get("data")).get("name"));
    }

    @Test
    void sameKeyWithDifferentCommandIsRejected() {
        EntityMutationCommand original = command(
                Map.of("data", Map.of("name", "名称一")));
        EntityMutationReceipt receipt =
                insertedReceipt(original);
        receipt.setStatus("SUCCESS");
        receipt.setResultDocument("{}");
        when(receiptMapper.findByIdempotencyKey(
                "mutation-1")).thenReturn(receipt);

        BusinessConflictException exception =
                assertThrows(
                        BusinessConflictException.class,
                        () -> service.acquire(command(
                                Map.of(
                                        "data",
                                        Map.of("name", "名称二")))));

        assertEquals(
                "ENTITY_MUTATION_IDEMPOTENCY_CONFLICT",
                exception.getErrorCode());
    }

    @Test
    void completionPersistsReplayableResult() {
        when(receiptMapper.complete(
                anyString(),
                anyString(),
                anyString(),
                eq(3),
                eq("CHANGE_EFFECTIVE"),
                anyBoolean())).thenReturn(1);
        EntityMutationResult result =
                new EntityMutationResult(
                        "operation-1",
                        "asset",
                        "record-1",
                        EntityMutationOperationType.UPDATE,
                        Map.of(
                                "data",
                                Map.of("name", "完成")),
                        3,
                        "CHANGE_EFFECTIVE",
                        false,
                        false);

        service.complete(command(Map.of()), result);

        ArgumentCaptor<String> document =
                ArgumentCaptor.forClass(String.class);
        verify(receiptMapper).complete(
                eq("mutation-1"),
                eq("record-1"),
                document.capture(),
                eq(3),
                eq("CHANGE_EFFECTIVE"),
                eq(false));
        assertTrue(document.getValue()
                .contains("\"name\":\"完成\""));
        assertFalse(result.replayed());
    }

    private EntityMutationReceipt insertedReceipt(
            EntityMutationCommand command) {
        service.acquire(command);
        ArgumentCaptor<EntityMutationReceipt> captor =
                ArgumentCaptor.forClass(
                        EntityMutationReceipt.class);
        verify(receiptMapper).insert(captor.capture());
        return captor.getValue();
    }

    private EntityMutationCommand command(
            Map<String, Object> payload) {
        EntityMutationContext context =
                EntityMutationContext.builder(
                                EntityMutationSourceType.FORM,
                                "CHANGE_EFFECTIVE",
                                "变更审批生效")
                        .sourceId("form-1")
                        .sourceRecord("change", "change-1")
                        .process(
                                "process-definition-1",
                                "process-instance-1",
                                "task-1")
                        .operator("user-1", "张三")
                        .trace("trace-1", "mutation-1")
                        .extraParams(Map.of("channel", "web"))
                        .build();
        return new EntityMutationCommand(
                "operation-1",
                "asset",
                "record-1",
                EntityMutationOperationType.UPDATE,
                payload,
                context);
    }
}
