package com.workflow.openapi.webhook.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.CurrentActorProvider;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.openapi.api.request.ReplayWebhookDeliveryRequest;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookDeliveryMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookDeliveryAdminRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebhookDeliveryAdministrationServiceTest {

    private WebhookDeliveryMapper mapper;
    private SystemAuditPort auditPort;
    private WebhookDeliveryAdministrationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(WebhookDeliveryMapper.class);
        auditPort = mock(SystemAuditPort.class);
        CurrentActorProvider actorProvider =
                () -> new CurrentActor("admin-01", "Admin");
        service = new WebhookDeliveryAdministrationService(
                mock(IntegrationApplicationMapper.class),
                mapper,
                actorProvider,
                auditPort,
                Clock.fixed(
                        Instant.parse("2026-07-29T08:30:00Z"),
                        ZoneOffset.UTC));
    }

    @Test
    void crossApplicationReplayIsIndistinguishableFromMissing() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.replay(
                        "other-application",
                        "delivery-01",
                        new ReplayWebhookDeliveryRequest(
                                "incident INC-1")));

        verify(mapper, never()).insert(
                any(),
                any(),
                any(),
                any(),
                any(Integer.class),
                any(Integer.class),
                any(),
                any(Long.class),
                any(),
                any());
    }

    @Test
    void onlyDeadDeliveriesCanBeReplayed() {
        when(mapper.findOwnedForReplay(
                "application-01",
                "delivery-01")).thenReturn(source("SUCCEEDED"));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.replay(
                        "application-01",
                        "delivery-01",
                        new ReplayWebhookDeliveryRequest(
                                "incident INC-1")));

        assertEquals(
                "WEBHOOK_DELIVERY_NOT_REPLAYABLE",
                exception.getErrorCode());
    }

    @Test
    void replayRetainsEventAndUsesSerializedSequenceAndCurrentSecret() {
        when(mapper.findOwnedForReplay(
                "application-01",
                "delivery-01")).thenReturn(source("DEAD"));
        when(mapper.lockReplaySequence(
                "subscription-01",
                "event-01")).thenReturn("delivery-00");
        when(mapper.findMaxReplaySequence(
                "subscription-01",
                "event-01")).thenReturn(1);
        when(mapper.insert(
                any(),
                eq("application-01"),
                eq("subscription-01"),
                eq("event-01"),
                eq(2),
                eq(8),
                eq("current-ciphertext"),
                eq(4L),
                eq("admin-01"),
                eq(LocalDateTime.parse(
                        "2026-07-29T08:30:00"))))
                .thenReturn(1);

        var replay = service.replay(
                "application-01",
                "delivery-01",
                new ReplayWebhookDeliveryRequest(
                        "incident INC-1"));

        assertEquals("event-01", replay.eventId());
        assertEquals(2, replay.replaySequence());
        assertEquals("PENDING", replay.status());
        verify(auditPort).record(any());
    }

    private WebhookDeliveryAdminRecord source(String status) {
        return new WebhookDeliveryAdminRecord(
                "delivery-01",
                "application-01",
                "subscription-01",
                "endpoint-01",
                "ERP callback",
                "event-01",
                "com.flow.process.completed.v1",
                1,
                status,
                8,
                8,
                LocalDateTime.parse("2026-07-29T08:30:00"),
                503,
                "HTTP_503",
                "unavailable",
                LocalDateTime.parse("2026-07-29T08:20:00"),
                null,
                LocalDateTime.parse("2026-07-29T08:00:00"),
                "current-ciphertext",
                4,
                "ACTIVE",
                "ACTIVE",
                LocalDateTime.parse("2026-08-28T08:00:00"));
    }
}
