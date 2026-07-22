package com.workflow.process.runtime;

import com.workflow.common.Result;
import com.workflow.entity.EntityStatus;
import com.workflow.entity.ProcessOperationLog;
import com.workflow.mapper.EntityDataDynamicMapper;
import com.workflow.mapper.EntityStatusMapper;
import com.workflow.mapper.ProcessOperationLogMapper;
import com.workflow.service.DynamicTableService;
import com.workflow.service.ProcessTaskService;
import com.workflow.service.SysUserService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessTerminationServiceTest {

    @Test
    void terminateProcessDeletesInstanceAndUpdatesEntityStatus() {
        Fixture fixture = new Fixture();
        fixture.runningProcess("starter");
        when(fixture.runtimeService.getVariable("pi-1", "entityCode")).thenReturn("expense");
        when(fixture.runtimeService.getVariable("pi-1", "entityDataId")).thenReturn("data-1");
        EntityStatus status = new EntityStatus();
        status.setStatusCode("TERMINATED");
        when(fixture.entityStatusMapper.findByCategory("expense", "TERMINATED")).thenReturn(List.of(status));
        when(fixture.dynamicTableService.getTableName("expense")).thenReturn("wf_expense");
        when(fixture.sysUserService.getNicknameByUsername("starter")).thenReturn("发起人");

        Result<Void> result = fixture.service().terminateProcess("pi-1", "starter", "主动撤回");

        assertEquals(200, result.getCode());
        verify(fixture.runtimeService).deleteProcessInstance("pi-1", "主动撤回");
        verify(fixture.processTaskService).deleteTasksByProcessInstance("pi-1");
        verify(fixture.operationLogMapper).insert(org.mockito.ArgumentMatchers.any(ProcessOperationLog.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> updateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(fixture.entityDataDynamicMapper).update(eq("wf_expense"), updateCaptor.capture());
        assertEquals("data-1", updateCaptor.getValue().get("id"));
        assertEquals("TERMINATED", updateCaptor.getValue().get("status"));
        verify(fixture.entityDataDynamicMapper).updateCurrentTask("wf_expense", "data-1", null, null, null);
    }

    @Test
    void terminateProcessRejectsNonStarter() {
        Fixture fixture = new Fixture();
        fixture.runningProcess("starter");

        Result<Void> result = fixture.service().terminateProcess("pi-1", "other", "主动撤回");

        assertEquals(403, result.getCode());
        verify(fixture.runtimeService, never()).deleteProcessInstance(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void terminateProcessRejectsEndedInstance() {
        Fixture fixture = new Fixture();
        fixture.endedProcess();

        Result<Void> result = fixture.service().terminateProcess("pi-1", "starter", "主动撤回");

        assertEquals(400, result.getCode());
        verify(fixture.runtimeService, never()).deleteProcessInstance(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    private static class Fixture {
        final RuntimeService runtimeService = mock(RuntimeService.class);
        final HistoryService historyService = mock(HistoryService.class);
        final DynamicTableService dynamicTableService = mock(DynamicTableService.class);
        final EntityDataDynamicMapper entityDataDynamicMapper = mock(EntityDataDynamicMapper.class);
        final EntityStatusMapper entityStatusMapper = mock(EntityStatusMapper.class);
        final ProcessOperationLogMapper operationLogMapper = mock(ProcessOperationLogMapper.class);
        final ProcessTaskService processTaskService = mock(ProcessTaskService.class);
        final SysUserService sysUserService = mock(SysUserService.class);
        final ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);
        final HistoricProcessInstanceQuery historicQuery = mock(HistoricProcessInstanceQuery.class);

        Fixture() {
            when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
            when(processInstanceQuery.processInstanceId("pi-1")).thenReturn(processInstanceQuery);
            when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historicQuery);
            when(historicQuery.processInstanceId("pi-1")).thenReturn(historicQuery);
        }

        void runningProcess(String startUserId) {
            ProcessInstance processInstance = mock(ProcessInstance.class);
            when(processInstanceQuery.singleResult()).thenReturn(processInstance);
            HistoricProcessInstance historicInstance = mock(HistoricProcessInstance.class);
            when(historicInstance.getStartUserId()).thenReturn(startUserId);
            when(historicQuery.singleResult()).thenReturn(historicInstance);
        }

        void endedProcess() {
            when(processInstanceQuery.singleResult()).thenReturn(null);
            HistoricProcessInstance historicInstance = mock(HistoricProcessInstance.class);
            when(historicInstance.getEndTime()).thenReturn(new Date());
            when(historicQuery.singleResult()).thenReturn(historicInstance);
        }

        ProcessTerminationService service() {
            return new ProcessTerminationService(
                    runtimeService, historyService, dynamicTableService, entityDataDynamicMapper,
                    entityStatusMapper, operationLogMapper, processTaskService, sysUserService,
                    mock(com.workflow.service.EntityRecordTeamService.class));
        }
    }
}
