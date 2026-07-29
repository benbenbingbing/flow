package com.workflow.openapi.webhook.delivery;

import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookDeliveryMapper;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        name = "workflow.open-api.webhook.enabled",
        havingValue = "true")
public class WebhookDeliveryWorker {

    private final WebhookDeliveryMapper mapper;
    private final WebhookDeliveryProcessor processor;
    private final WebhookDeliveryMetrics metrics;
    private final Executor executor;
    private final String ownerId =
            "webhook-" + UUID.randomUUID();

    @Value("${workflow.open-api.webhook.worker.batch-size:100}")
    private int batchSize = 100;

    @Value("${workflow.open-api.webhook.worker.lease-seconds:30}")
    private int leaseSeconds = 30;

    public WebhookDeliveryWorker(
            WebhookDeliveryMapper mapper,
            WebhookDeliveryProcessor processor,
            WebhookDeliveryMetrics metrics,
            @Qualifier("webhookDeliveryExecutor")
            Executor executor) {
        this.mapper = mapper;
        this.processor = processor;
        this.metrics = metrics;
        this.executor = executor;
    }

    @Scheduled(
            fixedDelayString =
                    "${workflow.open-api.webhook.worker.delay-ms:1000}")
    public void dispatchReady() {
        int recovered = mapper.recoverExpiredLeases();
        metrics.leaseRecovered(recovered);
        if (recovered > 0) {
            log.warn(
                    "回收超时 Webhook 投递: count={}",
                    recovered);
        }
        int boundedBatch = Math.max(
                1,
                Math.min(batchSize, 1000));
        int boundedLease = Math.max(
                15,
                Math.min(leaseSeconds, 300));
        for (String id : mapper.findReadyIds(boundedBatch)) {
            if (mapper.claim(id, ownerId, boundedLease) == 0) {
                continue;
            }
            var delivery = mapper.selectClaimed(id, ownerId);
            if (delivery == null) {
                continue;
            }
            try {
                executor.execute(() -> processor.process(
                        id,
                        ownerId,
                        delivery.leaseToken(),
                        boundedLease));
            } catch (RejectedExecutionException exception) {
                metrics.executorRejected();
                mapper.release(
                        id,
                        ownerId,
                        delivery.leaseToken(),
                        1,
                        "EXECUTOR_REJECTED");
                log.error(
                        "Webhook 执行队列已满，已释放租约: id={}",
                        id);
            }
        }
    }
}
