package com.workflow.openapi.webhook.delivery;

import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookDeliveryMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        name = "workflow.open-api.webhook.enabled",
        havingValue = "true")
public class WebhookBacklogMetrics {

    private final WebhookDeliveryMapper mapper;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong dead = new AtomicLong();
    private final AtomicLong oldestPendingSeconds =
            new AtomicLong();

    public WebhookBacklogMetrics(
            WebhookDeliveryMapper mapper,
            MeterRegistry registry) {
        this.mapper = mapper;
        Gauge.builder(
                        "flow.webhook.pending",
                        pending,
                        AtomicLong::get)
                .description(
                        "Outstanding webhook deliveries")
                .register(registry);
        Gauge.builder(
                        "flow.webhook.dead",
                        dead,
                        AtomicLong::get)
                .description(
                        "Webhook deliveries in dead-letter state")
                .register(registry);
        Gauge.builder(
                        "flow.webhook.oldest.pending.seconds",
                        oldestPendingSeconds,
                        AtomicLong::get)
                .description(
                        "Age of the oldest outstanding webhook delivery")
                .baseUnit("seconds")
                .register(registry);
    }

    @Scheduled(
            fixedDelayString =
                    "${workflow.open-api.webhook.metrics-refresh-ms:15000}")
    public void refresh() {
        try {
            pending.set(mapper.countOutstanding());
            dead.set(mapper.countDead());
            oldestPendingSeconds.set(
                    mapper.oldestOutstandingAgeSeconds());
        } catch (RuntimeException exception) {
            log.warn("刷新 Webhook 积压指标失败", exception);
        }
    }
}
