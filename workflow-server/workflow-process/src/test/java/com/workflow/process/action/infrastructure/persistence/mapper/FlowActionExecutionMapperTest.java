package com.workflow.process.action.infrastructure.persistence.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class FlowActionExecutionMapperTest {

    @Test
    void recoversOnlyDiscoveredExpiredLeases() {
        FlowActionExecutionMapper mapper = mock(
                FlowActionExecutionMapper.class,
                CALLS_REAL_METHODS);
        when(mapper.selectExpiredLeaseIds()).thenReturn(
                List.of("action-01", "action-02"));
        when(mapper.recoverExpiredLease("action-01")).thenReturn(1);
        when(mapper.recoverExpiredLease("action-02")).thenReturn(0);

        assertEquals(1, mapper.recoverExpiredLeases());

        verify(mapper).recoverExpiredLease("action-01");
        verify(mapper).recoverExpiredLease("action-02");
    }
}
