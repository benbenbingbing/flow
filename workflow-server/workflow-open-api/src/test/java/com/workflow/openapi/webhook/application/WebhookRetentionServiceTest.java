package com.workflow.openapi.webhook.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookDeliveryMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookEndpointMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookEventMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class WebhookRetentionServiceTest {

    @Test
    void expiresOutstandingWorkBeforeDeletingRetainedData() {
        WebhookDeliveryMapper deliveryMapper =
                mock(WebhookDeliveryMapper.class);
        WebhookEventMapper eventMapper =
                mock(WebhookEventMapper.class);
        WebhookEndpointMapper endpointMapper =
                mock(WebhookEndpointMapper.class);
        WebhookRetentionService service =
                new WebhookRetentionService(
                        deliveryMapper,
                        eventMapper,
                        endpointMapper);

        service.cleanExpired();

        InOrder order = inOrder(
                deliveryMapper,
                eventMapper,
                endpointMapper);
        order.verify(deliveryMapper)
                .expireOutstandingDeliveries(
                        any(),
                        eq(500));
        order.verify(deliveryMapper)
                .deleteExpiredFinalDeliveries(
                        any(),
                        eq(500));
        order.verify(eventMapper)
                .deleteExpiredWithoutDeliveries(
                        any(),
                        eq(500));
        order.verify(endpointMapper)
                .clearExpiredPreviousSecrets();
    }
}
