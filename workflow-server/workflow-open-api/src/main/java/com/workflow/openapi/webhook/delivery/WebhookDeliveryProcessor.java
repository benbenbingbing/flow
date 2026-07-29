package com.workflow.openapi.webhook.delivery;

import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookDeliveryMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookDeliveryWorkRecord;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(
        name = "workflow.open-api.webhook.enabled",
        havingValue = "true")
public class WebhookDeliveryProcessor {

    private static final long[] RETRY_DELAYS = {
            30, 120, 600, 1800, 7200, 21600, 86400
    };

    private final WebhookDeliveryMapper mapper;
    private final WebhookHttpClient httpClient;
    private final WebhookDeliveryMetrics metrics;
    private final TaskScheduler heartbeatScheduler;
    private final ObservationRegistry observationRegistry;

    @Autowired
    public WebhookDeliveryProcessor(
            WebhookDeliveryMapper mapper,
            WebhookHttpClient httpClient,
            WebhookDeliveryMetrics metrics,
            @Qualifier("webhookHeartbeatScheduler")
            TaskScheduler heartbeatScheduler,
            ObjectProvider<ObservationRegistry> observationRegistry) {
        this(
                mapper,
                httpClient,
                metrics,
                heartbeatScheduler,
                observationRegistry.getIfAvailable(
                        () -> ObservationRegistry.NOOP));
    }

    WebhookDeliveryProcessor(
            WebhookDeliveryMapper mapper,
            WebhookHttpClient httpClient,
            WebhookDeliveryMetrics metrics,
            TaskScheduler heartbeatScheduler) {
        this(
                mapper,
                httpClient,
                metrics,
                heartbeatScheduler,
                ObservationRegistry.NOOP);
    }

    WebhookDeliveryProcessor(
            WebhookDeliveryMapper mapper,
            WebhookHttpClient httpClient,
            WebhookDeliveryMetrics metrics,
            TaskScheduler heartbeatScheduler,
            ObservationRegistry observationRegistry) {
        this.mapper = mapper;
        this.httpClient = httpClient;
        this.metrics = metrics;
        this.heartbeatScheduler = heartbeatScheduler;
        this.observationRegistry = observationRegistry;
    }

    public void process(
            String deliveryId,
            String ownerId,
            long leaseToken,
            int leaseSeconds) {
        WebhookDeliveryWorkRecord delivery =
                mapper.selectClaimed(deliveryId, ownerId);
        if (delivery == null
                || delivery.leaseToken() != leaseToken) {
            return;
        }
        ScheduledFuture<?> heartbeat =
                heartbeatScheduler.scheduleAtFixedRate(
                        () -> heartbeat(
                                deliveryId,
                                ownerId,
                                leaseToken,
                                leaseSeconds),
                        Duration.ofSeconds(
                                Math.max(1, leaseSeconds / 3)));
        try {
            if (!"ACTIVE".equals(delivery.endpointStatus())
                    || !"ACTIVE".equals(
                    delivery.subscriptionStatus())) {
                mapper.release(
                        deliveryId,
                        ownerId,
                        leaseToken,
                        300,
                        "WEBHOOK_DISABLED");
                return;
            }
            observeAttempt(delivery, ownerId, leaseToken);
        } finally {
            heartbeat.cancel(false);
        }
    }

    private void observeAttempt(
            WebhookDeliveryWorkRecord delivery,
            String ownerId,
            long leaseToken) {
        Observation observation = Observation.createNotStarted(
                        "flow.webhook.delivery",
                        observationRegistry)
                .lowCardinalityKeyValue(
                        "application",
                        delivery.applicationId())
                .lowCardinalityKeyValue(
                        "event.type",
                        delivery.eventType())
                .highCardinalityKeyValue(
                        "delivery.id",
                        delivery.id())
                .highCardinalityKeyValue(
                        "event.id",
                        delivery.eventId())
                .highCardinalityKeyValue(
                        "trace.id",
                        delivery.traceId() == null
                                ? ""
                                : delivery.traceId())
                .start();
        try (Observation.Scope ignored = observation.openScope()) {
            attempt(delivery, ownerId, leaseToken);
        } catch (RuntimeException | Error exception) {
            observation.error(exception);
            throw exception;
        } finally {
            observation.stop();
        }
    }

    private void attempt(
            WebhookDeliveryWorkRecord delivery,
            String ownerId,
            long leaseToken) {
        int attempt = delivery.attemptCount() + 1;
        long startedAt = System.nanoTime();
        try {
            WebhookHttpResult result =
                    httpClient.send(delivery);
            if (result.statusCode() >= 200
                    && result.statusCode() < 300) {
                if (mapper.markSucceeded(
                        delivery.id(),
                        ownerId,
                        leaseToken,
                        attempt,
                        result.statusCode(),
                        null) == 1) {
                    recordMetric(
                            delivery,
                            "succeeded",
                            startedAt);
                } else {
                    fencingRejected(
                            delivery,
                            ownerId,
                            leaseToken);
                }
                return;
            }
            boolean retryable = retryable(
                    result.statusCode());
            completeFailure(
                    delivery,
                    ownerId,
                    leaseToken,
                    attempt,
                    startedAt,
                    retryable,
                    result.statusCode(),
                    result.responseExcerpt(),
                    "HTTP_" + result.statusCode(),
                    "Webhook endpoint returned HTTP "
                            + result.statusCode(),
                    result.retryAfterSeconds());
        } catch (HttpTimeoutException exception) {
            completeFailure(
                    delivery,
                    ownerId,
                    leaseToken,
                    attempt,
                    startedAt,
                    true,
                    null,
                    null,
                    "HTTP_TIMEOUT",
                    "Webhook request timed out",
                    null);
        } catch (IOException exception) {
            completeFailure(
                    delivery,
                    ownerId,
                    leaseToken,
                    attempt,
                    startedAt,
                    true,
                    null,
                    null,
                    "HTTP_IO_ERROR",
                    boundedMessage(exception),
                    null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            completeFailure(
                    delivery,
                    ownerId,
                    leaseToken,
                    attempt,
                    startedAt,
                    true,
                    null,
                    null,
                    "HTTP_INTERRUPTED",
                    "Webhook request interrupted",
                    null);
        } catch (IllegalArgumentException exception) {
            completeFailure(
                    delivery,
                    ownerId,
                    leaseToken,
                    attempt,
                    startedAt,
                    false,
                    null,
                    null,
                    "DESTINATION_POLICY_REJECTED",
                    boundedMessage(exception),
                    null);
        } catch (RuntimeException exception) {
            completeFailure(
                    delivery,
                    ownerId,
                    leaseToken,
                    attempt,
                    startedAt,
                    false,
                    null,
                    null,
                    "WEBHOOK_CONFIGURATION_ERROR",
                    boundedMessage(exception),
                    null);
        }
    }

    private void completeFailure(
            WebhookDeliveryWorkRecord delivery,
            String ownerId,
            long leaseToken,
            int attempt,
            long startedAt,
            boolean retryable,
            Integer responseStatus,
            String responseExcerpt,
            String errorCode,
            String errorMessage,
            Long retryAfterSeconds) {
        boolean exhausted = attempt >= delivery.maxAttempts();
        if (retryable && !exhausted) {
            long delay = retryDelay(
                    attempt,
                    retryAfterSeconds);
            if (mapper.markRetry(
                    delivery.id(),
                    ownerId,
                    leaseToken,
                    attempt,
                    delay,
                    responseStatus,
                    responseExcerpt,
                    errorCode,
                    errorMessage) == 1) {
                recordMetric(
                        delivery,
                        "retry",
                        startedAt);
            } else {
                fencingRejected(
                        delivery,
                        ownerId,
                        leaseToken);
            }
            return;
        }
        if (mapper.markDead(
                delivery.id(),
                ownerId,
                leaseToken,
                attempt,
                responseStatus,
                responseExcerpt,
                errorCode,
                errorMessage) == 1) {
            recordMetric(
                    delivery,
                    "dead",
                    startedAt);
        } else {
            fencingRejected(
                    delivery,
                    ownerId,
                    leaseToken);
        }
    }

    private boolean retryable(int status) {
        return status == 408
                || status == 409
                || status == 425
                || status == 429
                || status >= 500;
    }

    private long retryDelay(
            int completedAttempt,
            Long retryAfterSeconds) {
        long configured = RETRY_DELAYS[Math.min(
                completedAttempt - 1,
                RETRY_DELAYS.length - 1)];
        if (retryAfterSeconds == null) {
            return configured;
        }
        return Math.min(
                86400,
                Math.max(configured, retryAfterSeconds));
    }

    private void heartbeat(
            String deliveryId,
            String ownerId,
            long leaseToken,
            int leaseSeconds) {
        try {
            if (mapper.heartbeat(
                    deliveryId,
                    ownerId,
                    leaseToken,
                    leaseSeconds) == 0) {
                log.warn(
                        "Webhook 投递心跳被 fencing 拒绝: id={}, owner={}, token={}",
                        deliveryId,
                        ownerId,
                        leaseToken);
            }
        } catch (RuntimeException exception) {
            log.error(
                    "Webhook 投递心跳失败: id={}, owner={}",
                    deliveryId,
                    ownerId,
                    exception);
        }
    }

    private void fencingRejected(
            WebhookDeliveryWorkRecord delivery,
            String ownerId,
            long leaseToken) {
        log.warn(
                "Webhook 投递结果被 fencing 拒绝: id={}, owner={}, token={}",
                delivery.id(),
                ownerId,
                leaseToken);
    }

    private String boundedMessage(Exception exception) {
        String value = exception.getMessage();
        if (value == null || value.isBlank()) {
            value = exception.getClass().getSimpleName();
        }
        String normalized = value
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        return normalized.length() <= 1000
                ? normalized
                : normalized.substring(0, 1000);
    }

    private void recordMetric(
            WebhookDeliveryWorkRecord delivery,
            String status,
            long startedAt) {
        metrics.record(
                delivery,
                status,
                Duration.ofNanos(
                        Math.max(
                                0,
                                System.nanoTime() - startedAt)));
    }
}
