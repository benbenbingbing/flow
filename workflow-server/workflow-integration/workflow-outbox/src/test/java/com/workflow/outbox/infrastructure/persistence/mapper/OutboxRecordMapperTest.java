package com.workflow.outbox.infrastructure.persistence.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class OutboxRecordMapperTest {

    @Test
    void recoversOnlyDiscoveredExpiredLeases() {
        OutboxRecordMapper mapper = mock(
                OutboxRecordMapper.class,
                CALLS_REAL_METHODS);
        when(mapper.selectExpiredLeaseIds()).thenReturn(
                List.of("outbox-01", "outbox-02"));
        when(mapper.recoverExpiredLease("outbox-01")).thenReturn(1);
        when(mapper.recoverExpiredLease("outbox-02")).thenReturn(0);

        assertEquals(1, mapper.recoverExpiredLeases());

        verify(mapper).recoverExpiredLease("outbox-01");
        verify(mapper).recoverExpiredLease("outbox-02");
    }
}
