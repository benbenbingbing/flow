package com.workflow.openapi.webhook.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookDeliveryMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookDeliveryWorkRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class WebhookMetricsTest {

    @Test
    void exposesBoundedOutcomeAndDurationLabels() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WebhookDeliveryMetrics metrics =
                new WebhookDeliveryMetrics(registry);

        metrics.record(
                delivery(),
                "succeeded",
                Duration.ofMillis(25));

        assertEquals(
                1.0,
                registry.get("flow.webhook.deliveries")
                        .tag("application", "application-01")
                        .tag(
                                "event_type",
                                "com.flow.process.started.v1")
                        .tag("status", "succeeded")
                        .counter()
                        .count());
        assertEquals(
                1L,
                registry.get("flow.webhook.delivery.duration")
                        .tag("application", "application-01")
                        .timer()
                        .count());
    }

    @Test
    void refreshesBacklogGaugesFromDurableState() {
        WebhookDeliveryMapper mapper =
                mock(WebhookDeliveryMapper.class);
        when(mapper.countOutstanding()).thenReturn(12L);
        when(mapper.countDead()).thenReturn(2L);
        when(mapper.oldestOutstandingAgeSeconds())
                .thenReturn(321L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WebhookBacklogMetrics metrics =
                new WebhookBacklogMetrics(mapper, registry);

        metrics.refresh();

        assertEquals(
                12.0,
                registry.get("flow.webhook.pending")
                        .gauge().value());
        assertEquals(
                2.0,
                registry.get("flow.webhook.dead")
                        .gauge().value());
        assertEquals(
                321.0,
                registry.get(
                                "flow.webhook.oldest.pending.seconds")
                        .gauge().value());
    }

    private WebhookDeliveryWorkRecord delivery() {
        return new WebhookDeliveryWorkRecord(
                "delivery-01",
                "application-01",
                "subscription-01",
                "event-01",
                0,
                "PROCESSING",
                0,
                8,
                "owner-01",
                7,
                LocalDateTime.parse("2026-07-29T08:31:00"),
                "ciphertext",
                1,
                "https://hooks.example.com/flow",
                "ACTIVE",
                "ACTIVE",
                "com.flow.process.started.v1",
                "trace-01",
                "{}");
    }
}
