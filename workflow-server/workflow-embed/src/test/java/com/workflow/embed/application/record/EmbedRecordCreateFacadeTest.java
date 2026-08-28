package com.workflow.embed.application.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.EmbedRecordCreatePort;
import com.workflow.contracts.embed.EmbedRuntimeFormPort;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.embed.api.web.EmbedRecordCreateRequest;
import com.workflow.embed.api.web.EmbedRuntimeFormViews;
import com.workflow.embed.application.form.EmbedRuntimeFormFacade;
import com.workflow.embed.application.form.EmbedRuntimeFormFacade.CreateAuthorization;
import com.workflow.embed.application.port.EmbedIdempotencyPort;
import com.workflow.embed.application.port.EmbedOperationReceiptPort;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedIdempotencyClaim;
import com.workflow.embed.domain.EmbedOperationReceipt;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EmbedRecordCreateFacadeTest {

    private static final Instant NOW = Instant.parse("2026-08-27T05:00:00Z");

    private final EmbedRuntimeFormFacade formFacade = mock(EmbedRuntimeFormFacade.class);
    private final EmbedCanonicalRequestHasher hasher = mock(EmbedCanonicalRequestHasher.class);
    private final EmbedIdempotencyPort idempotencyPort = mock(EmbedIdempotencyPort.class);
    private final EmbedOperationReceiptPort receiptPort = mock(EmbedOperationReceiptPort.class);
    private final EmbedRecordCreateTransactionService transactionService =
            mock(EmbedRecordCreateTransactionService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final CreateAuthorization authorization = authorization();

    @BeforeEach
    void setUp() {
        when(formFacade.authorizeCreate(any())).thenReturn(authorization);
        when(formFacade.projectCreatedRecord(authorization, "record-1"))
                .thenReturn(record());
        when(hasher.actorScopeDigest(authorization.session()))
                .thenReturn("actor-digest");
        when(hasher.requestHash(
                eq("app-1"), eq("actor-digest"), eq("view-key"),
                eq("EMBED_RECORD_CREATE"), eq("ENTITY_FORM"),
                eq(canonicalTarget()), any()))
                .thenReturn("request-hash");
    }

    @Test
    void processingReturnsConflictWithRetryAfterBeforeAnyWrite() {
        when(idempotencyPort.claim(
                "app-1", "EMBED_RECORD_CREATE", "key-1", "request-hash", NOW))
                .thenReturn(new EmbedIdempotencyClaim(
                        "idem-1", 1,
                        EmbedIdempotencyClaim.Disposition.PROCESSING,
                        null, null, null, null));

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> facade().create(request(), "key-1", "trace-create"));

        assertEquals(409, error.getStatus());
        assertEquals(2L, error.getRetryAfterSeconds());
        assertEquals(EmbedErrorCode.EMBED_REQUEST_IN_PROGRESS,
                error.getErrorCode());
        verify(transactionService, never()).create(
                any(), any(), any(), any(), any());
    }

    @Test
    void firstCreateUsesClientProjectionForHashAndReturnsCurrentProjection() {
        EmbedIdempotencyClaim claim = acquired();
        EmbedOperationReceipt receipt = receipt("actor-digest");
        when(idempotencyPort.claim(
                "app-1", "EMBED_RECORD_CREATE", "key-1", "request-hash", NOW))
                .thenReturn(claim);
        when(transactionService.create(
                claim, authorization, "actor-digest", NOW, "trace-create"))
                .thenReturn(new EmbedRecordCreateTransactionService.BusinessResult(
                        receipt,
                        new EmbedRecordCreatePort.CreatedRecord("record-1", null)));

        var outcome = facade().create(request(), "key-1", "trace-create");

        assertFalse(outcome.replay());
        assertEquals("record-1", outcome.result().record().id());
        assertEquals("client-1", outcome.result().clientMutationId());
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(hasher).requestHash(
                eq("app-1"), eq("actor-digest"), eq("view-key"),
                eq("EMBED_RECORD_CREATE"), eq("ENTITY_FORM"),
                eq(canonicalTarget()),
                body.capture());
        assertTrue(body.getValue() instanceof Map<?, ?>);
        assertEquals(Map.of("title", "browser"),
                ((Map<?, ?>) body.getValue()).get("data"));
        assertFalse(body.getValue().toString().contains("forced-server"));
    }

    @Test
    void replayRevalidatesReceiptScopeThenRereadsCurrentOutputPolicy() {
        EmbedIdempotencyClaim replay = new EmbedIdempotencyClaim(
                "idem-1", 4, EmbedIdempotencyClaim.Disposition.REPLAY,
                201,
                "{\"schema\":\"embed-idempotency-replay-v1\","
                        + "\"receiptId\":\"eor-1\","
                        + "\"outcomeCode\":\"RECORD_CREATED\"}",
                "EMBED_OPERATION_RECEIPT", "eor-1");
        when(idempotencyPort.claim(
                "app-1", "EMBED_RECORD_CREATE", "key-1", "request-hash", NOW))
                .thenReturn(replay);
        when(receiptPort.findById("eor-1"))
                .thenReturn(Optional.of(receipt("actor-digest")));

        var outcome = facade().create(request(), "key-1", "trace-create");

        assertTrue(outcome.replay());
        verify(formFacade).projectCreatedRecord(authorization, "record-1");
        verify(transactionService, never()).create(
                any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sameRequestReplaysAcrossSessionAndReleaseRotation() {
        CreateAuthorization rotated = authorization(
                "session-2", "release-2", "form-release-2", 5);
        when(formFacade.authorizeCreate(any()))
                .thenReturn(authorization, rotated);
        when(hasher.actorScopeDigest(rotated.session()))
                .thenReturn("actor-digest");
        when(formFacade.projectCreatedRecord(any(), eq("record-1")))
                .thenReturn(record());
        EmbedIdempotencyClaim first = acquired();
        EmbedIdempotencyClaim replay = new EmbedIdempotencyClaim(
                "idem-1", 4, EmbedIdempotencyClaim.Disposition.REPLAY,
                201,
                "{\"schema\":\"embed-idempotency-replay-v1\","
                        + "\"receiptId\":\"eor-1\","
                        + "\"outcomeCode\":\"RECORD_CREATED\"}",
                "EMBED_OPERATION_RECEIPT", "eor-1");
        when(idempotencyPort.claim(
                "app-1", "EMBED_RECORD_CREATE", "key-1", "request-hash", NOW))
                .thenReturn(first, replay);
        when(transactionService.create(
                first, authorization, "actor-digest", NOW, "trace-create"))
                .thenReturn(new EmbedRecordCreateTransactionService.BusinessResult(
                        receipt("actor-digest"),
                        new EmbedRecordCreatePort.CreatedRecord("record-1", null)));
        when(receiptPort.findById("eor-1"))
                .thenReturn(Optional.of(receipt("actor-digest")));

        var initial = facade().create(request(), "key-1", "trace-create");
        var repeated = facade().create(request(), "key-1", "trace-create");

        assertFalse(initial.replay());
        assertTrue(repeated.replay());
        ArgumentCaptor<Map<String, Object>> targets = ArgumentCaptor.forClass(Map.class);
        verify(hasher, times(2)).requestHash(
                eq("app-1"), eq("actor-digest"), eq("view-key"),
                eq("EMBED_RECORD_CREATE"), eq("ENTITY_FORM"),
                targets.capture(), any());
        assertEquals(List.of(canonicalTarget(), canonicalTarget()),
                targets.getAllValues());
        assertFalse(targets.getAllValues().toString().contains("release"));
        assertFalse(targets.getAllValues().toString().contains("session"));
    }

    @Test
    void replayFromDifferentActorScopeIsRejectedWithoutProjection() {
        EmbedIdempotencyClaim replay = new EmbedIdempotencyClaim(
                "idem-1", 4, EmbedIdempotencyClaim.Disposition.REPLAY,
                201,
                "{\"schema\":\"embed-idempotency-replay-v1\","
                        + "\"receiptId\":\"eor-1\","
                        + "\"outcomeCode\":\"RECORD_CREATED\"}",
                "EMBED_OPERATION_RECEIPT", "eor-1");
        when(idempotencyPort.claim(any(), any(), any(), any(), any()))
                .thenReturn(replay);
        when(receiptPort.findById("eor-1"))
                .thenReturn(Optional.of(receipt("another-actor")));

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> facade().create(request(), "key-1", "trace-create"));

        assertEquals(EmbedErrorCode.EMBED_IDEMPOTENCY_KEY_REUSED, error.getErrorCode());
        verify(formFacade, never()).projectCreatedRecord(any(), any());
    }

    @Test
    void businessFailureMarksClaimRetryableInSeparatePort() {
        EmbedIdempotencyClaim claim = acquired();
        when(idempotencyPort.claim(any(), any(), any(), any(), any()))
                .thenReturn(claim);
        when(transactionService.create(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessConflictException(
                        "ENTITY_UNIQUE_CONFLICT", "unique conflict"));

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> facade().create(request(), "key-1", "trace-create"));

        assertEquals(EmbedErrorCode.EMBED_RECORD_CONFLICT, error.getErrorCode());
        verify(idempotencyPort).failRetryable(claim, NOW);
    }

    @Test
    void formValidationFailureUsesPublic422ContractAndSafeViolation() {
        EmbedIdempotencyClaim claim = acquired();
        when(idempotencyPort.claim(any(), any(), any(), any(), any()))
                .thenReturn(claim);
        when(transactionService.create(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessConflictException(
                        "FORM_REQUIRED_VALIDATION_FAILED",
                        "内部字段标签不得直接外泄"));

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> facade().create(request(), "key-1", "trace-create"));

        assertEquals(422, error.getStatus());
        assertEquals(EmbedErrorCode.FORM_VALIDATION_FAILED,
                error.getErrorCode());
        assertFalse(error.getData().toString().contains("内部字段标签"));
        assertTrue(error.getData().toString().contains("violations"));
        verify(idempotencyPort).failRetryable(claim, NOW);
    }

    private EmbedRecordCreateFacade facade() {
        return new EmbedRecordCreateFacade(
                formFacade, hasher, idempotencyPort, receiptPort,
                transactionService, objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static EmbedRecordCreateRequest request() {
        EmbedRecordCreateRequest request = new EmbedRecordCreateRequest();
        request.setData(Map.of("title", "browser"));
        request.setClientMutationId("client-1");
        return request;
    }

    private static EmbedIdempotencyClaim acquired() {
        return new EmbedIdempotencyClaim(
                "idem-1", 4, EmbedIdempotencyClaim.Disposition.ACQUIRED,
                null, null, null, null);
    }

    private static EmbedOperationReceipt receipt(String actorDigest) {
        return new EmbedOperationReceipt(
                "eor-1", "idem-1", "app-1", "EMBED_RECORD_CREATE",
                actorDigest, "view-key", "RECORD", "record-1",
                "RECORD_CREATED", null,
                "{\"recordId\":\"record-1\","
                        + "\"outcomeCode\":\"RECORD_CREATED\"}");
    }

    private static EmbedRuntimeFormViews.RecordView record() {
        return new EmbedRuntimeFormViews.RecordView(
                "record-1", null, Map.of("title", "projected"),
                new EmbedRuntimeFormViews.RecordMeta(null, null));
    }

    private static CreateAuthorization authorization() {
        return authorization(
                "session-1", "release-1", "form-release-1", 4);
    }

    private static CreateAuthorization authorization(
            String sessionId,
            String viewReleaseId,
            String formReleaseId,
            int formReleaseVersion) {
        AuthenticatedEmbedSession session = new AuthenticatedEmbedSession(
                sessionId, "app-1", "grant-1", "provider-1", "binding-1",
                "view-1", viewReleaseId, "user-1", "zhangsan",
                "https://portal.example.com", "channel-1234567890", "CREATE",
                null, Map.of(), Set.of("RECORD_CREATE"),
                NOW.plusSeconds(900), NOW.plusSeconds(3600));
        return new CreateAuthorization(
                session, "view-key",
                new EmbedRuntimeFormPort.Target(
                        "work_order", "form-1", formReleaseId, formReleaseVersion,
                        null, null, null),
                Map.of("title", "browser"),
                Map.of("title", "forced-server"),
                Map.of("title", "TEXT"), Map.of());
    }

    private static Map<String, Object> canonicalTarget() {
        return Map.of(
                "entityCode", "work_order",
                "formId", "form-1");
    }
}
