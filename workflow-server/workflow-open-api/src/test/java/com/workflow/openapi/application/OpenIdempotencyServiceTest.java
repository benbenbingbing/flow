package com.workflow.openapi.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.openapi.api.error.OpenApiException;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationIdempotencyMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationIdempotencyRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenIdempotencyServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-29T08:30:00Z");

    private IntegrationIdempotencyMapper mapper;
    private OpenIdempotencyService service;
    private AtomicReference<String> insertedHash;

    @BeforeEach
    void setUp() {
        mapper = mock(IntegrationIdempotencyMapper.class);
        insertedHash = new AtomicReference<>();
        service = new OpenIdempotencyService(
                mapper,
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(mapper.insertProcessing(
                anyString(),
                eq("application-01"),
                eq("PROCESS_START"),
                eq("request-01"),
                anyString(),
                any(),
                any())).thenAnswer(invocation -> {
                    insertedHash.set(invocation.getArgument(4));
                    return 1;
                });
        when(mapper.find(
                "application-01",
                "PROCESS_START",
                "request-01")).thenAnswer(invocation ->
                        record(
                                insertedHash.get(),
                                "PROCESSING",
                                null));
    }

    @Test
    void equivalentMapOrderProducesTheSameRequestHash() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("title", "Release");
        first.put("risk", "HIGH");
        var firstClaim = service.claim(
                "application-01",
                "PROCESS_START",
                "request-01",
                first);
        String firstHash = insertedHash.get();

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("risk", "HIGH");
        second.put("title", "Release");
        service.claim(
                "application-01",
                "PROCESS_START",
                "request-01",
                second);

        assertEquals(firstHash, insertedHash.get());
        assertEquals(1L, firstClaim.fencingToken());
    }

    @Test
    void differentInputForTheSameKeyIsRejected() {
        service.claim(
                "application-01",
                "PROCESS_START",
                "request-01",
                Map.of("title", "First"));
        String firstHash = insertedHash.get();
        when(mapper.insertProcessing(
                anyString(),
                eq("application-01"),
                eq("PROCESS_START"),
                eq("request-01"),
                anyString(),
                any(),
                any())).thenAnswer(invocation -> {
                    insertedHash.set(invocation.getArgument(4));
                    return 0;
                });
        when(mapper.find(
                "application-01",
                "PROCESS_START",
                "request-01")).thenReturn(
                        record(firstHash, "PROCESSING", null));

        OpenApiException exception = assertThrows(
                OpenApiException.class,
                () -> service.claim(
                        "application-01",
                        "PROCESS_START",
                        "request-01",
                        Map.of("title", "Second")));
        assertEquals(
                "IDEMPOTENCY_KEY_REUSED",
                exception.getErrorCode());
    }

    @Test
    void succeededRecordReplaysStoredData() {
        when(mapper.insertProcessing(
                anyString(),
                eq("application-01"),
                eq("PROCESS_START"),
                eq("request-01"),
                anyString(),
                any(),
                any())).thenAnswer(invocation -> {
                    insertedHash.set(invocation.getArgument(4));
                    return 0;
                });
        when(mapper.find(
                "application-01",
                "PROCESS_START",
                "request-01")).thenAnswer(invocation ->
                        record(
                                insertedHash.get(),
                                "SUCCEEDED",
                                "{\"value\":\"original\"}"));

        var claim = service.claim(
                "application-01",
                "PROCESS_START",
                "request-01",
                Map.of("title", "Release"));

        assertEquals(
                "original",
                service.readReplay(claim, ReplayValue.class).value());
    }

    private IntegrationIdempotencyRecord record(
            String hash,
            String status,
            String responseBody) {
        IntegrationIdempotencyRecord record =
                new IntegrationIdempotencyRecord();
        record.setId("idempotency-01");
        record.setRequestHash(hash);
        record.setStatus(status);
        record.setFencingToken(1L);
        record.setResponseStatus(
                responseBody == null ? null : 201);
        record.setResponseBody(responseBody);
        return record;
    }

    private record ReplayValue(String value) {
    }
}
