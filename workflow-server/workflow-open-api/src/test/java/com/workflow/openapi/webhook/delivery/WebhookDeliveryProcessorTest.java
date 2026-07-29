package com.workflow.openapi.webhook.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookDeliveryMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookDeliveryWorkRecord;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

class WebhookDeliveryProcessorTest {

    private WebhookDeliveryMapper mapper;
    private WebhookHttpClient httpClient;
    private WebhookDeliveryMetrics metrics;
    private WebhookDeliveryProcessor processor;
    private ScheduledFuture<?> heartbeat;

    @BeforeEach
    void setUp() {
        mapper = mock(WebhookDeliveryMapper.class);
        httpClient = mock(WebhookHttpClient.class);
        metrics = mock(WebhookDeliveryMetrics.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        heartbeat = mock(ScheduledFuture.class);
        doReturn(heartbeat).when(scheduler).scheduleAtFixedRate(
                any(Runnable.class),
                any(Duration.class));
        processor = new WebhookDeliveryProcessor(
                mapper,
                httpClient,
                metrics,
                scheduler);
        when(mapper.selectClaimed("delivery-01", "owner-01"))
                .thenReturn(delivery(0, 8, "ACTIVE", "ACTIVE"));
    }

    @Test
    void marksAnyTwoHundredResponseAsSucceeded() throws Exception {
        when(httpClient.send(any())).thenReturn(
                new WebhookHttpResult(204, null, false, null));
        when(mapper.markSucceeded(
                "delivery-01",
                "owner-01",
                7,
                1,
                204,
                null)).thenReturn(1);

        processor.process("delivery-01", "owner-01", 7, 30);

        verify(metrics).record(
                any(),
                eq("succeeded"),
                any(Duration.class));
        verify(heartbeat).cancel(false);
    }

    @Test
    void rateLimitUsesTheLargerBoundedRetryAfter() throws Exception {
        when(httpClient.send(any())).thenReturn(
                new WebhookHttpResult(
                        429,
                        "busy",
                        false,
                        600L));
        when(mapper.markRetry(
                "delivery-01",
                "owner-01",
                7,
                1,
                600,
                429,
                "busy",
                "HTTP_429",
                "Webhook endpoint returned HTTP 429"))
                .thenReturn(1);

        processor.process("delivery-01", "owner-01", 7, 30);

        verify(metrics).record(
                any(),
                eq("retry"),
                any(Duration.class));
    }

    @Test
    void clientErrorsBecomeDeadLettersWithoutRetry() throws Exception {
        when(httpClient.send(any())).thenReturn(
                new WebhookHttpResult(
                        400,
                        "invalid",
                        false,
                        null));
        when(mapper.markDead(
                "delivery-01",
                "owner-01",
                7,
                1,
                400,
                "invalid",
                "HTTP_400",
                "Webhook endpoint returned HTTP 400"))
                .thenReturn(1);

        processor.process("delivery-01", "owner-01", 7, 30);

        verify(metrics).record(
                any(),
                eq("dead"),
                any(Duration.class));
        verify(mapper, never()).markRetry(
                any(),
                any(),
                anyLong(),
                eq(1),
                anyLong(),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void finalServerFailureBecomesADeadLetter() throws Exception {
        when(mapper.selectClaimed(
                "delivery-01",
                "owner-01")).thenReturn(
                delivery(7, 8, "ACTIVE", "ACTIVE"));
        when(httpClient.send(any())).thenReturn(
                new WebhookHttpResult(503, null, false, null));
        when(mapper.markDead(
                "delivery-01",
                "owner-01",
                7,
                8,
                503,
                null,
                "HTTP_503",
                "Webhook endpoint returned HTTP 503"))
                .thenReturn(1);

        processor.process("delivery-01", "owner-01", 7, 30);

        verify(metrics).record(
                any(),
                eq("dead"),
                any(Duration.class));
    }

    @Test
    void timeoutIsRetriedWithoutPersistingExceptionDetails()
            throws Exception {
        when(httpClient.send(any())).thenThrow(
                new HttpTimeoutException("secret URL timed out"));
        when(mapper.markRetry(
                "delivery-01",
                "owner-01",
                7,
                1,
                30,
                null,
                null,
                "HTTP_TIMEOUT",
                "Webhook request timed out")).thenReturn(1);

        processor.process("delivery-01", "owner-01", 7, 30);

        verify(metrics).record(
                any(),
                eq("retry"),
                any(Duration.class));
    }

    @Test
    void disabledSubscriptionReleasesTheClaimWithoutSending()
            throws Exception {
        when(mapper.selectClaimed(
                "delivery-01",
                "owner-01")).thenReturn(
                delivery(0, 8, "ACTIVE", "DISABLED"));

        processor.process("delivery-01", "owner-01", 7, 30);

        verify(mapper).release(
                "delivery-01",
                "owner-01",
                7,
                300,
                "WEBHOOK_DISABLED");
        verify(httpClient, never()).send(any());
    }

    @Test
    void staleWorkerCannotReportSuccessOrIncrementMetrics()
            throws Exception {
        when(httpClient.send(any())).thenReturn(
                new WebhookHttpResult(200, null, false, null));
        when(mapper.markSucceeded(
                "delivery-01",
                "owner-01",
                7,
                1,
                200,
                null)).thenReturn(0);

        processor.process("delivery-01", "owner-01", 7, 30);

        verify(metrics, never()).record(
                any(),
                any(),
                any(Duration.class));
    }

    @Test
    void createsOneDeliveryObservationWithBoundedTags() throws Exception {
        ObservationRegistry registry = ObservationRegistry.create();
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger stops = new AtomicInteger();
        AtomicReference<Observation.Context> context =
                new AtomicReference<>();
        registry.observationConfig().observationHandler(
                new ObservationHandler<>() {
                    @Override
                    public void onStart(Observation.Context value) {
                        starts.incrementAndGet();
                        context.set(value);
                    }

                    @Override
                    public void onStop(Observation.Context value) {
                        stops.incrementAndGet();
                    }

                    @Override
                    public boolean supportsContext(
                            Observation.Context value) {
                        return true;
                    }
                });
        TaskScheduler scheduler = processorScheduler();
        processor = new WebhookDeliveryProcessor(
                mapper,
                httpClient,
                metrics,
                scheduler,
                registry);
        when(httpClient.send(any())).thenReturn(
                new WebhookHttpResult(204, null, false, null));
        when(mapper.markSucceeded(
                "delivery-01",
                "owner-01",
                7,
                1,
                204,
                null)).thenReturn(1);

        processor.process("delivery-01", "owner-01", 7, 30);

        assertEquals(1, starts.get());
        assertEquals(1, stops.get());
        assertEquals(
                "flow.webhook.delivery",
                context.get().getName());
        assertEquals(
                "application-01",
                context.get().getLowCardinalityKeyValue(
                        "application").getValue());
        assertEquals(
                "event-01",
                context.get().getHighCardinalityKeyValue(
                        "event.id").getValue());
    }

    private TaskScheduler processorScheduler() {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        doReturn(heartbeat).when(scheduler).scheduleAtFixedRate(
                any(Runnable.class),
                any(Duration.class));
        return scheduler;
    }

    private WebhookDeliveryWorkRecord delivery(
            int attemptCount,
            int maxAttempts,
            String endpointStatus,
            String subscriptionStatus) {
        return new WebhookDeliveryWorkRecord(
                "delivery-01",
                "application-01",
                "subscription-01",
                "event-01",
                0,
                "PROCESSING",
                attemptCount,
                maxAttempts,
                "owner-01",
                7,
                LocalDateTime.parse("2026-07-29T08:31:00"),
                "v1.nonce.ciphertext",
                1,
                "https://hooks.example.com/flow",
                endpointStatus,
                subscriptionStatus,
                "com.flow.process.started.v1",
                "trace-01",
                "{}");
    }
}
