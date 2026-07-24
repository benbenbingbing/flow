package com.workflow.listener;

import com.workflow.entity.EntityFlowStatusMapping;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.mapper.EntityDataDynamicMapper;
import com.workflow.mapper.EntityFlowStatusMappingMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.service.DynamicTableService;
import org.flowable.engine.RuntimeService;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.delegate.event.impl.FlowableEntityEventImpl;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 实体状态更新监听器单元测试。
 *
 * <p>被测对象为 {@link EntityStatusUpdateListener}，验证任务完成事件触发时
 * 根据流程节点状态映射更新动态实体表，以及任务创建事件不触发状态更新。</p>
 */
class EntityStatusUpdateListenerTest {

    /**
     * 任务完成事件应根据节点状态映射更新动态实体表。
     *
     * <p>场景：mock 任务完成事件、流程实例、实体状态映射，
     * 断言 dynamicMapper.update 收到的 Map 含正确 ID 与映射后的状态码。</p>
     */
    @Test
    void taskCompletionUpdatesDynamicEntityTable() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        EntityDataDynamicMapper dynamicMapper = mock(EntityDataDynamicMapper.class);
        DynamicTableService dynamicTableService = mock(DynamicTableService.class);
        EntityFlowStatusMappingMapper statusMapper = mock(EntityFlowStatusMappingMapper.class);
        ProcessDefinitionConfigMapper processMapper = mock(ProcessDefinitionConfigMapper.class);
        ProcessInstanceQuery processQuery = mock(ProcessInstanceQuery.class);
        ProcessInstance processInstance = mock(ProcessInstance.class);
        TaskEntity task = mock(TaskEntity.class);

        when(task.getProcessInstanceId()).thenReturn("instance-1");
        when(task.getTaskDefinitionKey()).thenReturn("Task_Review");
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processQuery);
        when(processQuery.processInstanceId("instance-1")).thenReturn(processQuery);
        when(processQuery.singleResult()).thenReturn(processInstance);
        when(processInstance.getProcessDefinitionKey()).thenReturn("expense-flow");
        when(runtimeService.getVariable("instance-1", "entityCode")).thenReturn("expense");
        when(runtimeService.getVariable("instance-1", "entityDataId")).thenReturn("data-1");

        ProcessDefinitionConfig processConfig = new ProcessDefinitionConfig();
        processConfig.setId("process-1");
        when(processMapper.findByProcessKey("expense-flow")).thenReturn(Optional.of(processConfig));

        EntityFlowStatusMapping mapping = new EntityFlowStatusMapping();
        mapping.setEntityStatusCode("FINANCE_REVIEW");
        when(statusMapper.findByProcessAndSourceNode("process-1", "Task_Review"))
                .thenReturn(List.of(mapping));
        when(dynamicTableService.getTableName("expense")).thenReturn("biz_expense");
        when(dynamicMapper.selectById("biz_expense", "data-1"))
                .thenReturn(Map.of("id", "data-1", "status", "IN_REVIEW"));

        EntityStatusUpdateListener listener = new EntityStatusUpdateListener(
                runtimeService, dynamicMapper, dynamicTableService, statusMapper, processMapper);
        FlowableEntityEventImpl event = mock(FlowableEntityEventImpl.class);
        when(event.getType()).thenReturn(FlowableEngineEventType.TASK_COMPLETED);
        when(event.getEntity()).thenReturn(task);

        listener.onEvent(event);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(dynamicMapper).update(eq("biz_expense"), captor.capture());
        assertEquals("data-1", captor.getValue().get("id"));
        assertEquals("FINANCE_REVIEW", captor.getValue().get("status"));
    }

    /**
     * 任务创建事件不应触发实体状态更新。
     *
     * <p>场景：事件类型为 TASK_CREATED，断言所有依赖均未被交互。</p>
     */
    @Test
    void taskCreationDoesNotUpdateEntityStatus() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        EntityDataDynamicMapper dynamicMapper = mock(EntityDataDynamicMapper.class);
        DynamicTableService dynamicTableService = mock(DynamicTableService.class);
        EntityFlowStatusMappingMapper statusMapper = mock(EntityFlowStatusMappingMapper.class);
        ProcessDefinitionConfigMapper processMapper = mock(ProcessDefinitionConfigMapper.class);
        FlowableEntityEventImpl event = mock(FlowableEntityEventImpl.class);
        when(event.getType()).thenReturn(FlowableEngineEventType.TASK_CREATED);

        EntityStatusUpdateListener listener = new EntityStatusUpdateListener(
                runtimeService, dynamicMapper, dynamicTableService, statusMapper, processMapper);

        listener.onEvent(event);

        verifyNoInteractions(runtimeService, dynamicMapper, dynamicTableService, statusMapper, processMapper);
    }
}
