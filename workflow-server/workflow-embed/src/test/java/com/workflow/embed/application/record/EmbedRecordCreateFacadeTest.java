package com.workflow.embed.application.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.runtime.port.EmbedRecordCreatePort;
import com.workflow.embed.api.web.EmbedRecordCreateRequest;
import com.workflow.embed.application.port.EmbedIdempotencyPort;
import com.workflow.embed.application.port.EmbedOperationReceiptPort;
import com.workflow.embed.application.record.EmbedNativeRecordCreateAuthorizationService.Authorization;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedIdempotencyClaim;
import com.workflow.embed.domain.EmbedOperationReceipt;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmbedRecordCreateFacadeTest {

    private static final Instant NOW = Instant.parse("2026-08-27T05:00:00Z");

    private final EmbedNativeRecordCreateAuthorizationService authorizationService =
            mock(EmbedNativeRecordCreateAuthorizationService.class);
    private final EmbedCanonicalRequestHasher hasher =
            mock(EmbedCanonicalRequestHasher.class);
    private final EmbedIdempotencyPort idempotencyPort =
            mock(EmbedIdempotencyPort.class);
    private final EmbedOperationReceiptPort receiptPort =
            mock(EmbedOperationReceiptPort.class);
    private final EmbedRecordCreateTransactionService transactionService =
            mock(EmbedRecordCreateTransactionService.class);
    private final Authorization authorization = authorization();

    @BeforeEach
    void setUp() {
        when(authorizationService.authorize(any(), any()))
                .thenReturn(authorization);
        when(hasher.actorScopeDigest(authorization.session()))
                .thenReturn("actor-digest");
        when(hasher.requestHash(
                eq("app-1"), eq("actor-digest"), eq("view-key"),
                eq("EMBED_RECORD_CREATE"), eq("ENTITY_FORM"),
                eq(Map.of("entityCode", "work_order", "formId", "form-1")),
                any())).thenReturn("request-hash");
    }

    @Test
    void firstCreateReturnsIdOnlyAndKeepsExistingIdempotentTransaction() {
        EmbedIdempotencyClaim claim = acquired();
        EmbedOperationReceipt receipt = receipt();
        when(idempotencyPort.claim(
                "app-1", "EMBED_RECORD_CREATE", "idem-key",
                "request-hash", NOW)).thenReturn(claim);
        when(transactionService.create(
                claim, authorization, "actor-digest", NOW, "trace-1"))
                .thenReturn(new EmbedRecordCreateTransactionService.BusinessResult(
                        receipt,
                        new com.workflow.contracts.embed.runtime.port.EmbedRecordCreatePort.CreatedRecord("record-1", null)));

        var outcome = facade().create(request(), "idem-key", "trace-1");

        assertFalse(outcome.replay());
        assertEquals("record-1", outcome.result().record().id());
        assertEquals(null, outcome.result().record().recordVersion());
        assertEquals("client-1", outcome.result().clientMutationId());
        assertFalse(outcome.result().toString().contains("browser-value"));
        assertFalse(outcome.result().toString().contains("forced-value"));
    }

    @Test
    void replayReturnsSameIdOnlyReceiptWithoutSecondWrite() {
        EmbedIdempotencyClaim replay = new EmbedIdempotencyClaim(
                "idem-1", 4, EmbedIdempotencyClaim.Disposition.REPLAY,
                201,
                "{\"schema\":\"embed-idempotency-replay-v1\","
                        + "\"receiptId\":\"eor-1\","
                        + "\"outcomeCode\":\"RECORD_CREATED\"}",
                "EMBED_OPERATION_RECEIPT", "eor-1");
        when(idempotencyPort.claim(
                "app-1", "EMBED_RECORD_CREATE", "idem-key",
                "request-hash", NOW)).thenReturn(replay);
        when(receiptPort.findById("eor-1"))
                .thenReturn(Optional.of(receipt()));

        var outcome = facade().create(request(), "idem-key", "trace-1");

        assertTrue(outcome.replay());
        assertEquals("record-1", outcome.result().record().id());
        verify(transactionService, never()).create(
                any(), any(), any(), any(), any());
    }

    @Test
    void processingClaimStillReturnsRetryableConflict() {
        when(idempotencyPort.claim(
                "app-1", "EMBED_RECORD_CREATE", "idem-key",
                "request-hash", NOW)).thenReturn(new EmbedIdempotencyClaim(
                "idem-1", 1, EmbedIdempotencyClaim.Disposition.PROCESSING,
                null, null, null, null));

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> facade().create(request(), "idem-key", "trace-1"));

        assertEquals(EmbedErrorCode.EMBED_REQUEST_IN_PROGRESS,
                error.getErrorCode());
        assertEquals(2L, error.getRetryAfterSeconds());
    }

    private EmbedRecordCreateFacade facade() {
        return new EmbedRecordCreateFacade(
                authorizationService, hasher, idempotencyPort, receiptPort,
                transactionService,
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static EmbedRecordCreateRequest request() {
        EmbedRecordCreateRequest request = new EmbedRecordCreateRequest();
        request.setData(Map.of("title", "browser-value"));
        request.setClientMutationId("client-1");
        return request;
    }

    private static Authorization authorization() {
        AuthenticatedEmbedSession session = new AuthenticatedEmbedSession(
                "session-1", "app-1", "grant-1", "provider-1", "binding-1",
                "view-1", "release-1", "user-1", "zhangsan",
                "https://portal.example.com", "channel-1234567890", "CREATE",
                null, Map.of(), Set.of("RECORD_CREATE"),
                NOW.plusSeconds(900), NOW.plusSeconds(3600));
        return new Authorization(
                session, "view-key",
                new com.workflow.contracts.embed.runtime.port.EmbedRecordCreatePort.Target(
                        "work_order", "form-1", "form-release-1", 4),
                Map.of("title", "browser-value", "owner", "forced-value"),
                Map.of("tenant", "tenant-a"),
                "save", false);
    }

    private static EmbedIdempotencyClaim acquired() {
        return new EmbedIdempotencyClaim(
                "idem-1", 3, EmbedIdempotencyClaim.Disposition.ACQUIRED,
                null, null, null, null);
    }

    private static EmbedOperationReceipt receipt() {
        return new EmbedOperationReceipt(
                "eor-1", "idem-1", "app-1", "EMBED_RECORD_CREATE",
                "actor-digest", "view-key", "RECORD", "record-1",
                "RECORD_CREATED", null,
                "{\"recordId\":\"record-1\","
                        + "\"outcomeCode\":\"RECORD_CREATED\"}");
    }
}
