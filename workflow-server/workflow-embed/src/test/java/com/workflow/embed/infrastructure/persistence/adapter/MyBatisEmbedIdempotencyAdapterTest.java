package com.workflow.embed.infrastructure.persistence.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedIdempotencyClaim;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedIdempotencyMapper;
import com.workflow.embed.infrastructure.persistence.record.EmbedIdempotencyRow;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class MyBatisEmbedIdempotencyAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-27T05:00:00Z");
    private static final LocalDateTime LOCAL_NOW =
            LocalDateTime.parse("2026-08-27T05:00:00");

    @Test
    void newClaimOwnsFenceAndProcessingLoserDoesNotReacquire() {
        EmbedIdempotencyMapper mapper = mock(EmbedIdempotencyMapper.class);
        when(mapper.insertProcessing(
                any(), eq("app-1"), eq("EMBED_RECORD_CREATE"),
                eq("key-1"), eq("hash-1"), eq(LOCAL_NOW), any()))
                .thenReturn(1);
        when(mapper.find("app-1", "EMBED_RECORD_CREATE", "key-1"))
                .thenReturn(row("PROCESSING", 1, "hash-1", null, null));
        MyBatisEmbedIdempotencyAdapter adapter = adapter(mapper);

        EmbedIdempotencyClaim acquired = adapter.claim(
                "app-1", "EMBED_RECORD_CREATE", "key-1", "hash-1", NOW);

        assertTrue(acquired.acquired());
        assertEquals(1, acquired.fencingToken());

        when(mapper.insertProcessing(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        when(mapper.find("app-1", "EMBED_RECORD_CREATE", "key-1"))
                .thenReturn(row("PROCESSING", 1, "hash-1", null, null));
        assertTrue(adapter.claim(
                "app-1", "EMBED_RECORD_CREATE", "key-1", "hash-1", NOW)
                .processing());
    }

    @Test
    void failedClaimIsReacquiredWithWinnerFence() {
        EmbedIdempotencyMapper mapper = mock(EmbedIdempotencyMapper.class);
        when(mapper.insertProcessing(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        when(mapper.find("app-1", "EMBED_RECORD_CREATE", "key-1"))
                .thenReturn(
                        row("FAILED_RETRYABLE", 4, "hash-1", null, null),
                        row("PROCESSING", 5, "hash-1", null, null));
        when(mapper.reacquire(
                eq("idem-1"), eq(4L), eq(LOCAL_NOW), any(), any()))
                .thenReturn(1);
        MyBatisEmbedIdempotencyAdapter adapter = adapter(mapper);

        EmbedIdempotencyClaim claim = adapter.claim(
                "app-1", "EMBED_RECORD_CREATE", "key-1", "hash-1", NOW);

        assertTrue(claim.acquired());
        assertEquals(5, claim.fencingToken());
    }

    @Test
    void reacquireRaceObservesSucceededWinnerAsReplay() {
        EmbedIdempotencyMapper mapper = mock(EmbedIdempotencyMapper.class);
        when(mapper.insertProcessing(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        when(mapper.find("app-1", "EMBED_RECORD_CREATE", "key-1"))
                .thenReturn(
                        row("FAILED_RETRYABLE", 2, "hash-1", null, null),
                        row("SUCCEEDED", 3, "hash-1", 201, "{\"receiptId\":\"eor-1\"}"));
        when(mapper.reacquire(
                eq("idem-1"), eq(2L), eq(LOCAL_NOW), any(), any()))
                .thenReturn(0);

        EmbedIdempotencyClaim claim = adapter(mapper).claim(
                "app-1", "EMBED_RECORD_CREATE", "key-1", "hash-1", NOW);

        assertTrue(claim.replay());
        assertEquals("eor-1", claim.resourceId());
    }

    @Test
    void sameKeyWithDifferentBodyIsConflict() {
        EmbedIdempotencyMapper mapper = mock(EmbedIdempotencyMapper.class);
        when(mapper.insertProcessing(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        when(mapper.find("app-1", "EMBED_RECORD_CREATE", "key-1"))
                .thenReturn(row("PROCESSING", 1, "different-hash", null, null));

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> adapter(mapper).claim(
                        "app-1", "EMBED_RECORD_CREATE", "key-1", "hash-1", NOW));

        assertEquals(409, error.getStatus());
        assertEquals(EmbedErrorCode.EMBED_IDEMPOTENCY_KEY_REUSED, error.getErrorCode());
    }

    @Test
    void staleFenceCannotCompleteAndFailureNeverOverwritesNewOwner() {
        EmbedIdempotencyMapper mapper = mock(EmbedIdempotencyMapper.class);
        EmbedIdempotencyClaim claim = new EmbedIdempotencyClaim(
                "idem-1", 7, EmbedIdempotencyClaim.Disposition.ACQUIRED,
                null, null, null, null);
        when(mapper.complete(
                "idem-1", 7, "EMBED_OPERATION_RECEIPT", "eor-1",
                201, "{\"receiptId\":\"eor-1\"}", LOCAL_NOW))
                .thenReturn(0);
        MyBatisEmbedIdempotencyAdapter adapter = adapter(mapper);

        assertThrows(IllegalStateException.class,
                () -> adapter.completeInBusinessTransaction(
                        claim, "EMBED_OPERATION_RECEIPT", "eor-1", 201,
                        "{\"receiptId\":\"eor-1\"}", NOW));

        when(mapper.failRetryable("idem-1", 7, LOCAL_NOW)).thenReturn(0);
        adapter.failRetryable(claim, NOW);
        verify(mapper).failRetryable("idem-1", 7, LOCAL_NOW);
    }

    @Test
    void transactionPropagationMatchesClaimAndBusinessFenceContract()
            throws Exception {
        Method claim = MyBatisEmbedIdempotencyAdapter.class.getMethod(
                "claim", String.class, String.class, String.class,
                String.class, Instant.class);
        Method complete = MyBatisEmbedIdempotencyAdapter.class.getMethod(
                "completeInBusinessTransaction", EmbedIdempotencyClaim.class,
                String.class, String.class, int.class, String.class, Instant.class);
        Method fail = MyBatisEmbedIdempotencyAdapter.class.getMethod(
                "failRetryable", EmbedIdempotencyClaim.class, Instant.class);

        assertEquals(Propagation.REQUIRES_NEW,
                claim.getAnnotation(Transactional.class).propagation());
        assertEquals(Propagation.MANDATORY,
                complete.getAnnotation(Transactional.class).propagation());
        assertEquals(Propagation.REQUIRES_NEW,
                fail.getAnnotation(Transactional.class).propagation());
    }

    private static MyBatisEmbedIdempotencyAdapter adapter(
            EmbedIdempotencyMapper mapper) {
        return new MyBatisEmbedIdempotencyAdapter(
                mapper, new ObjectMapper().findAndRegisterModules());
    }

    private static EmbedIdempotencyRow row(
            String status,
            long fence,
            String requestHash,
            Integer responseStatus,
            String responseBody) {
        boolean succeeded = "SUCCEEDED".equals(status);
        return new EmbedIdempotencyRow(
                "idem-1", requestHash, status,
                succeeded ? "EMBED_OPERATION_RECEIPT" : null,
                succeeded ? "eor-1" : null,
                responseStatus, responseBody, fence,
                LOCAL_NOW.minusSeconds(10));
    }
}
