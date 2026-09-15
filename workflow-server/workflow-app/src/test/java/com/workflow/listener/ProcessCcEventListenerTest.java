package com.workflow.listener;

import com.workflow.process.cc.infrastructure.flowable.ProcessCcEventListener;

import com.workflow.process.cc.application.ProcessCcRuntimeService;
import com.workflow.process.cc.application.ProcessCcConfigService;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.task.api.Task;
import org.flowable.task.service.event.impl.FlowableTaskEventBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;

/**
 * 流程知会事件监听器单元测试。
 *
 * <p>被测对象为 {@link ProcessCcEventListener}，验证监听器在主事务提交后才触发，
 * 且异常不会导致工作流失败。</p>
 */
class ProcessCcEventListenerTest {
    /** 任务服务使用自己的事件实现，监听器不能只接受 BPMN 引擎的具体实现类。 */
    @Test
    void taskServiceCreatedEventTriggersNodeCc() {
        ProcessCcRuntimeService runtime = mock(ProcessCcRuntimeService.class);
        ProcessCcConfigService configs = mock(ProcessCcConfigService.class);
        RuntimeService engineRuntime = mock(RuntimeService.class);
        HistoryService history = mock(HistoryService.class);
        RepositoryService repository = mock(RepositoryService.class);
        Task task = mock(Task.class);
        when(task.getProcessDefinitionId()).thenReturn("definition-1");
        when(task.getProcessInstanceId()).thenReturn("instance-1");
        when(task.getTaskDefinitionKey()).thenReturn("notify");
        when(configs.findConfig("definition-1", "notify")).thenReturn("config");
        HistoricProcessInstanceQuery historicQuery = mock(HistoricProcessInstanceQuery.class);
        when(history.createHistoricProcessInstanceQuery()).thenReturn(historicQuery);
        when(historicQuery.processInstanceId("instance-1")).thenReturn(historicQuery);
        ProcessDefinitionQuery definitionQuery = mock(ProcessDefinitionQuery.class);
        when(repository.createProcessDefinitionQuery()).thenReturn(definitionQuery);
        when(definitionQuery.processDefinitionId("definition-1")).thenReturn(definitionQuery);
        ProcessCcEventListener listener = new ProcessCcEventListener(
                runtime, configs, engineRuntime, history, repository);

        listener.onEvent(FlowableTaskEventBuilder.createEntityEvent(
                FlowableEngineEventType.TASK_CREATED, task));

        verify(runtime).trigger(argThat(context -> "TASK_CREATE".equals(context.timing())
                && "notify".equals(context.nodeId())), eq("config"));
    }

    /**
     * 监听器应在主事务提交后触发，且异常不应中断工作流。
     *
     * <p>断言 isFireOnTransactionLifecycleEvent 为 true、onTransaction 为 COMMITTED、
     * isFailOnException 为 false。</p>
     */
    @Test
    void dispatchesAfterMainTransactionCommitsWithoutFailingWorkflow() {
        ProcessCcEventListener listener = new ProcessCcEventListener(
                mock(ProcessCcRuntimeService.class),
                mock(ProcessCcConfigService.class),
                mock(RuntimeService.class),
                mock(HistoryService.class),
                mock(RepositoryService.class));

        assertTrue(listener.isFireOnTransactionLifecycleEvent());
        assertEquals("COMMITTED", listener.getOnTransaction());
        assertFalse(listener.isFailOnException());
    }
}
