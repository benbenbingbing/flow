package com.workflow.embed.application.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.EmbedRecordCreatePort;
import com.workflow.contracts.embed.EmbedRuntimeFormPort;
import com.workflow.embed.application.audit.EmbedRuntimeAudit;
import com.workflow.embed.application.form.EmbedRuntimeFormFacade.CreateAuthorization;
import com.workflow.embed.application.port.EmbedIdempotencyPort;
import com.workflow.embed.application.port.EmbedOperationReceiptPort;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedIdempotencyClaim;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

class EmbedRecordCreateTransactionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T05:00:00Z");

    @Test
    void writesEntityReceiptAndFencedCompletionInOneOrderedUseCase() {
        EmbedRecordCreatePort entityPort = mock(EmbedRecordCreatePort.class);
        EmbedOperationReceiptPort receiptPort = mock(EmbedOperationReceiptPort.class);
        EmbedIdempotencyPort idempotencyPort = mock(EmbedIdempotencyPort.class);
        EmbedRuntimeAudit runtimeAudit = mock(EmbedRuntimeAudit.class);
        when(entityPort.create(any())).thenReturn(
                new EmbedRecordCreatePort.CreatedRecord("record-1", 9L));
        EmbedRecordCreateTransactionService service = service(
                entityPort, receiptPort, idempotencyPort, runtimeAudit);
        EmbedIdempotencyClaim claim = acquired();

        var result = service.create(
                claim, authorization(), "actor-digest", NOW, "trace-create");

        assertEquals("record-1", result.record().recordId());
        assertEquals("record-1", result.receipt().targetId());
        assertFalse(result.receipt().resultSummaryJson().contains("secret-value"));
        ArgumentCaptor<String> envelope = ArgumentCaptor.forClass(String.class);
        var ordered = inOrder(
                entityPort, receiptPort, idempotencyPort, runtimeAudit);
        ordered.verify(entityPort).create(any());
        ordered.verify(receiptPort).insertInBusinessTransaction(result.receipt());
        ordered.verify(idempotencyPort).completeInBusinessTransaction(
                eq(claim), eq("EMBED_OPERATION_RECEIPT"),
                eq(result.receipt().id()), eq(201), envelope.capture(), eq(NOW));
        ordered.verify(runtimeAudit).recordCreatedRequired(
                eq(authorization().session()),
                eq("record-1"),
                eq("trace-create"),
                anyLong());
        assertFalse(envelope.getValue().contains("secret-value"));
        assertFalse(envelope.getValue().contains("values"));
    }

    @Test
    void staleFenceFailurePropagatesSoTransactionCanRollback() {
        EmbedRecordCreatePort entityPort = mock(EmbedRecordCreatePort.class);
        EmbedOperationReceiptPort receiptPort = mock(EmbedOperationReceiptPort.class);
        EmbedIdempotencyPort idempotencyPort = mock(EmbedIdempotencyPort.class);
        when(entityPort.create(any())).thenReturn(
                new EmbedRecordCreatePort.CreatedRecord("record-1", null));
        org.mockito.Mockito.doThrow(new IllegalStateException("stale fence"))
                .when(idempotencyPort).completeInBusinessTransaction(
                        any(), any(), any(), eq(201), any(), any());

        assertThrows(IllegalStateException.class,
                () -> service(
                        entityPort,
                        receiptPort,
                        idempotencyPort,
                        mock(EmbedRuntimeAudit.class))
                        .create(acquired(), authorization(), "actor-digest", NOW,
                                "trace-create"));
    }

    @Test
    void requiredAuditFailurePropagatesSoBusinessTransactionCanRollback() {
        EmbedRecordCreatePort entityPort = mock(EmbedRecordCreatePort.class);
        EmbedOperationReceiptPort receiptPort = mock(EmbedOperationReceiptPort.class);
        EmbedIdempotencyPort idempotencyPort = mock(EmbedIdempotencyPort.class);
        EmbedRuntimeAudit runtimeAudit = mock(EmbedRuntimeAudit.class);
        when(entityPort.create(any())).thenReturn(
                new EmbedRecordCreatePort.CreatedRecord("record-1", null));
        org.mockito.Mockito.doThrow(new IllegalStateException("audit unavailable"))
                .when(runtimeAudit).recordCreatedRequired(
                        any(), any(), any(), anyLong());

        assertThrows(IllegalStateException.class,
                () -> service(entityPort, receiptPort, idempotencyPort, runtimeAudit)
                        .create(acquired(), authorization(), "actor-digest", NOW,
                                "trace-create"));
    }

    @Test
    void useCaseDeclaresRollbackForCheckedAndRuntimeFailures() throws Exception {
        Method method = EmbedRecordCreateTransactionService.class.getMethod(
                "create", EmbedIdempotencyClaim.class, CreateAuthorization.class,
                String.class, Instant.class, String.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertEquals(Exception.class, transactional.rollbackFor()[0]);
    }

    private static EmbedRecordCreateTransactionService service(
            EmbedRecordCreatePort entityPort,
            EmbedOperationReceiptPort receiptPort,
            EmbedIdempotencyPort idempotencyPort,
            EmbedRuntimeAudit runtimeAudit) {
        return new EmbedRecordCreateTransactionService(
                entityPort, receiptPort, idempotencyPort,
                new ObjectMapper().findAndRegisterModules(), runtimeAudit);
    }

    private static EmbedIdempotencyClaim acquired() {
        return new EmbedIdempotencyClaim(
                "idem-1", 3, EmbedIdempotencyClaim.Disposition.ACQUIRED,
                null, null, null, null);
    }

    private static CreateAuthorization authorization() {
        AuthenticatedEmbedSession session = new AuthenticatedEmbedSession(
                "session-1", "app-1", "grant-1", "provider-1", "binding-1",
                "view-1", "release-1", "user-1", "zhangsan",
                "https://portal.example.com", "channel-1234567890", "CREATE",
                null, Map.of(), Set.of("RECORD_CREATE"),
                NOW.plusSeconds(900), NOW.plusSeconds(3600));
        EmbedRuntimeFormPort.Target target = new EmbedRuntimeFormPort.Target(
                "work_order", "form-1", "form-release-1", 4,
                null, null, null);
        return new CreateAuthorization(
                session, "view-key", target,
                Map.of("title", "browser-value"),
                Map.of("title", "secret-value"),
                Map.of("title", "TEXT"), Map.of());
    }
}
