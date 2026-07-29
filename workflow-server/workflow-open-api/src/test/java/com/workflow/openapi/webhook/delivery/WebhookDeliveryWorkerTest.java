package com.workflow.openapi.webhook.delivery;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookDeliveryMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookDeliveryWorkRecord;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WebhookDeliveryWorkerTest {

    @Test
    void recoversLeasesThenClaimsAndDispatchesReadyWork() {
        WebhookDeliveryMapper mapper =
                mock(WebhookDeliveryMapper.class);
        WebhookDeliveryProcessor processor =
                mock(WebhookDeliveryProcessor.class);
        WebhookDeliveryMetrics metrics =
                mock(WebhookDeliveryMetrics.class);
        when(mapper.recoverExpiredLeases()).thenReturn(2);
        when(mapper.findReadyIds(100)).thenReturn(
                List.of("delivery-01"));
        when(mapper.claim(
                eq("delivery-01"),
                anyString(),
                eq(30))).thenReturn(1);
        when(mapper.selectClaimed(
                eq("delivery-01"),
                anyString())).thenReturn(delivery());
        Executor direct = Runnable::run;
        WebhookDeliveryWorker worker =
                new WebhookDeliveryWorker(
                        mapper,
                        processor,
                        metrics,
                        direct);
        ArgumentCaptor<String> owner =
                ArgumentCaptor.forClass(String.class);

        worker.dispatchReady();

        verify(metrics).leaseRecovered(2);
        verify(processor).process(
                eq("delivery-01"),
                owner.capture(),
                eq(7L),
                eq(30));
        assertTrue(owner.getValue().startsWith("webhook-"));
    }

    @Test
    void releasesLeaseWhenTheBoundedExecutorIsFull() {
        WebhookDeliveryMapper mapper =
                mock(WebhookDeliveryMapper.class);
        WebhookDeliveryProcessor processor =
                mock(WebhookDeliveryProcessor.class);
        WebhookDeliveryMetrics metrics =
                mock(WebhookDeliveryMetrics.class);
        when(mapper.findReadyIds(100)).thenReturn(
                List.of("delivery-01"));
        when(mapper.claim(
                eq("delivery-01"),
                anyString(),
                eq(30))).thenReturn(1);
        when(mapper.selectClaimed(
                eq("delivery-01"),
                anyString())).thenReturn(delivery());
        Executor rejecting = command -> {
            throw new RejectedExecutionException("full");
        };
        WebhookDeliveryWorker worker =
                new WebhookDeliveryWorker(
                        mapper,
                        processor,
                        metrics,
                        rejecting);

        worker.dispatchReady();

        verify(metrics).executorRejected();
        verify(mapper).release(
                eq("delivery-01"),
                anyString(),
                eq(7L),
                eq(1L),
                eq("EXECUTOR_REJECTED"));
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
                "worker-from-database",
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
