package com.workflow.process.runtime;

import com.workflow.process.instance.application.ProcessTerminationService;

import com.workflow.core.result.Result;
import com.workflow.contracts.entity.EntityRecordPort;
import com.workflow.contracts.identity.IdentityDirectoryPort;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.task.application.ProcessTaskService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.Test;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程终止服务单元测试。
 *
 * <p>被测对象为 {@link ProcessTerminationService}，验证终止流程时删除实例、
 * 清理当前任务、写入操作日志，以及非发起人终止与已结束实例的拒绝逻辑。</p>
 */
class ProcessTerminationServiceTest {

    /**
     * 发起人终止运行中流程应删除实例并记录终止活动。
     *
     * <p>场景：mock 运行实例与实体状态映射，断言返回 200，
     * 验证 deleteProcessInstance、deleteTasksByProcessInstance、操作日志插入、
     * 状态更新由流程结束监听器统一处理，命令服务仅记录终止活动。</p>
     */
    @Test
    void terminateProcessDeletesInstanceAndRecordsActivity() {
        Fixture fixture = new Fixture();
        fixture.runningProcess("starter");
        when(fixture.runtimeService.getVariable("pi-1", "entityCode")).thenReturn("expense");
        when(fixture.runtimeService.getVariable("pi-1", "entityDataId")).thenReturn("data-1");
        when(fixture.identityDirectoryPort.getDisplayName("starter")).thenReturn("发起人");

        Result<Void> result = fixture.service().terminateProcess("pi-1", "starter", "主动撤回");

        assertEquals(200, result.getCode());
        verify(fixture.runtimeService).deleteProcessInstance("pi-1", "主动撤回");
        verify(fixture.processTaskService).deleteTasksByProcessInstance("pi-1");
        verify(fixture.operationLogMapper).insert(org.mockito.ArgumentMatchers.any(ProcessOperationLog.class));

        verify(fixture.entityRecordPort).recordActivity(
                "expense",
                "data-1",
                "TERMINATE",
                "主动撤回",
                "pi-1",
                null);
        verify(fixture.entityRecordPort, never()).markProcessEnded(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * 非发起人终止流程应返回 403 且不删除实例。
     */
    @Test
    void terminateProcessRejectsNonStarter() {
        Fixture fixture = new Fixture();
        fixture.runningProcess("starter");

        Result<Void> result = fixture.service().terminateProcess("pi-1", "other", "主动撤回");

        assertEquals(403, result.getCode());
        verify(fixture.runtimeService, never()).deleteProcessInstance(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * 已结束的流程实例不可终止，应返回 400 且不删除实例。
     */
    @Test
    void terminateProcessRejectsEndedInstance() {
        Fixture fixture = new Fixture();
        fixture.endedProcess();

        Result<Void> result = fixture.service().terminateProcess("pi-1", "starter", "主动撤回");

        assertEquals(400, result.getCode());
        verify(fixture.runtimeService, never()).deleteProcessInstance(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    /** 测试夹具：封装 mock 依赖与场景构造方法 */
    private static class Fixture {
        final RuntimeService runtimeService = mock(RuntimeService.class);
        final HistoryService historyService = mock(HistoryService.class);
        final ProcessOperationLogMapper operationLogMapper = mock(ProcessOperationLogMapper.class);
        final ProcessTaskService processTaskService = mock(ProcessTaskService.class);
        final IdentityDirectoryPort identityDirectoryPort = mock(IdentityDirectoryPort.class);
        final EntityRecordPort entityRecordPort = mock(EntityRecordPort.class);
        final ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);
        final HistoricProcessInstanceQuery historicQuery = mock(HistoricProcessInstanceQuery.class);

        /** 构造夹具，设置流程实例与历史查询的 mock 链路 */
        Fixture() {
            when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
            when(processInstanceQuery.processInstanceId("pi-1")).thenReturn(processInstanceQuery);
            when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historicQuery);
            when(historicQuery.processInstanceId("pi-1")).thenReturn(historicQuery);
        }

        /**
         * 设置运行中流程实例桩数据。
         *
         * @param startUserId 发起人用户 ID
         */
        void runningProcess(String startUserId) {
            ProcessInstance processInstance = mock(ProcessInstance.class);
            when(processInstanceQuery.singleResult()).thenReturn(processInstance);
            HistoricProcessInstance historicInstance = mock(HistoricProcessInstance.class);
            when(historicInstance.getStartUserId()).thenReturn(startUserId);
            when(historicQuery.singleResult()).thenReturn(historicInstance);
        }

        /** 设置已结束流程实例桩数据，查询不到运行实例且历史有结束时间 */
        void endedProcess() {
            when(processInstanceQuery.singleResult()).thenReturn(null);
            HistoricProcessInstance historicInstance = mock(HistoricProcessInstance.class);
            when(historicInstance.getEndTime()).thenReturn(new Date());
            when(historicQuery.singleResult()).thenReturn(historicInstance);
        }

        /** 组装并返回被测服务实例 */
        ProcessTerminationService service() {
            return new ProcessTerminationService(
                    runtimeService, historyService, operationLogMapper, processTaskService,
                    identityDirectoryPort, entityRecordPort);
        }
    }
}
