package com.workflow.openapi.webhook.delivery;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookDeliveryWorkRecord;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class WebhookDeliveryMetrics {

    private final MeterRegistry registry;

    @Autowired
    public WebhookDeliveryMetrics(
            ObjectProvider<MeterRegistry> provider) {
        this.registry = provider.getIfAvailable();
    }

    WebhookDeliveryMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(
            WebhookDeliveryWorkRecord delivery,
            String status,
            Duration duration) {
        if (registry == null) {
            return;
        }
        Counter.builder("flow.webhook.deliveries")
                .description("Webhook delivery outcomes")
                .tag("application", delivery.applicationId())
                .tag("event_type", delivery.eventType())
                .tag("status", status)
                .register(registry)
                .increment();
        Timer.builder("flow.webhook.delivery.duration")
                .description("Webhook delivery attempt duration")
                .tag("application", delivery.applicationId())
                .register(registry)
                .record(duration);
    }

    public void leaseRecovered(int count) {
        if (registry != null && count > 0) {
            Counter.builder(
                            "flow.webhook.lease.recovered")
                    .description(
                            "Recovered expired webhook delivery leases")
                    .register(registry)
                    .increment(count);
        }
    }

    public void executorRejected() {
        if (registry != null) {
            Counter.builder(
                            "flow.webhook.executor.rejected")
                    .description(
                            "Webhook deliveries rejected by the worker executor")
                    .register(registry)
                    .increment();
        }
    }

}
