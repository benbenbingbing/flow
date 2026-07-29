package com.workflow.openapi.webhook.application;

import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookDeliveryMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookEndpointMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookEventMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "workflow.open-api.webhook.enabled",
        havingValue = "true")
public class WebhookRetentionService {

    private static final int BATCH_SIZE = 500;

    private final WebhookDeliveryMapper deliveryMapper;
    private final WebhookEventMapper eventMapper;
    private final WebhookEndpointMapper endpointMapper;
    private final Clock clock = Clock.systemUTC();

    @Scheduled(
            cron = "${workflow.open-api.webhook.retention-cron:0 37 3 * * *}")
    @Transactional(rollbackFor = Exception.class)
    public void cleanExpired() {
        LocalDateTime now = LocalDateTime.ofInstant(
                clock.instant(),
                ZoneOffset.UTC);
        deliveryMapper.expireOutstandingDeliveries(
                now,
                BATCH_SIZE);
        deliveryMapper.deleteExpiredFinalDeliveries(
                now,
                BATCH_SIZE);
        eventMapper.deleteExpiredWithoutDeliveries(
                now,
                BATCH_SIZE);
        endpointMapper.clearExpiredPreviousSecrets();
    }
}
