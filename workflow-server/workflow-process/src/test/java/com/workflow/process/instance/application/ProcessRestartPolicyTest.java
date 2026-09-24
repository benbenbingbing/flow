package com.workflow.process.instance.application;

import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.instance.infrastructure.persistence.record.EntityProcessLink;
import com.workflow.process.status.application.ProcessEndReason;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessRestartPolicyTest {
    @Mock EntityProcessLinkMapper links;
    @Mock RuntimeService runtimeService;
    @Mock HistoryService historyService;
    @InjectMocks ProcessRuntimeService service;
    EntityProcessLink link;

    @BeforeEach void setup() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "historyService", historyService);
        link = new EntityProcessLink(); link.setProcessInstanceId("old");
        link.setState("ENDED"); link.setEndType("WITHDRAWN");
        lenient().when(links.selectLatest("expense", "record")).thenReturn(link);
    }

    @Test void implicitStartAndOldRestartRequestCannotReserveAnotherGeneration() {
        when(links.selectLatestForUpdate("expense", "record")).thenReturn(link);
        var config = new com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig();
        var initial = new com.workflow.contracts.process.model.ProcessStartRequest("config", "expense", "record",
                "EXP-1", "starter", "发起人", "PENDING", java.util.Map.of(), java.util.Map.of());
        assertThrows(com.workflow.core.error.BusinessConflictException.class,
                () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "reserveLink", initial, config));
        var stale = new com.workflow.contracts.process.model.ProcessStartRequest("config", "expense", "record",
                "EXP-1", "starter", "发起人", "PENDING", java.util.Map.of(), java.util.Map.of(), "older");
        assertThrows(com.workflow.core.error.BusinessConflictException.class,
                () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "reserveLink", stale, config));
        verifyNoInteractions(runtimeService, historyService);
    }

    @Test void onlyTrueWithdrawalOfTheLatestInstanceByItsInitiatorCanRestart() {
        var running = mock(ProcessInstanceQuery.class, RETURNS_SELF);
        var query = mock(HistoricProcessInstanceQuery.class, RETURNS_SELF);
        var historic = mock(HistoricProcessInstance.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(running);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(historic);
        when(historic.getEndTime()).thenReturn(new Date());
        when(historic.getStartUserId()).thenReturn("starter");
        when(historic.getDeleteReason()).thenReturn(ProcessEndReason.encode("WITHDRAWN", "改正金额"));
        assertTrue(service.canRestart("expense", "record", "old", "starter"));
        assertFalse(service.canRestart("expense", "record", "old", "other"));
        when(historic.getDeleteReason()).thenReturn(ProcessEndReason.encode("TERMINATED", "发起人撤回"));
        assertFalse(service.canRestart("expense", "record", "old", "starter"));
    }

    @Test void supersededOrUnreconciledInstancesCannotStartAnotherGeneration() {
        assertFalse(service.canRestart("expense", "record", "older", "starter"));
        link.setState("ACTIVE");
        assertFalse(service.canRestart("expense", "record", "old", "starter"));
        link.setState("ENDED"); link.setEndType("TERMINATED");
        assertFalse(service.canRestart("expense", "record", "old", "starter"));
        verifyNoInteractions(runtimeService, historyService);
    }
}
