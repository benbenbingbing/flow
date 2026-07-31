package com.workflow.openapi.webhook.infrastructure.persistence.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class WebhookDeliveryMapperTest {

    @Test
    void recoversOnlyDiscoveredExpiredLeases() {
        WebhookDeliveryMapper mapper = mock(
                WebhookDeliveryMapper.class,
                CALLS_REAL_METHODS);
        when(mapper.selectExpiredLeaseIds()).thenReturn(
                List.of("delivery-01", "delivery-02"));
        when(mapper.recoverExpiredLease("delivery-01")).thenReturn(1);
        when(mapper.recoverExpiredLease("delivery-02")).thenReturn(0);

        assertEquals(1, mapper.recoverExpiredLeases());

        verify(mapper).recoverExpiredLease("delivery-01");
        verify(mapper).recoverExpiredLease("delivery-02");
    }
}
