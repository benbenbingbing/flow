package com.workflow.process.task.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.port.EntityRecordPort;
import com.workflow.contracts.entity.form.port.EntityFormRuntimePort;
import com.workflow.contracts.identity.port.IdentityDirectoryPort;
import com.workflow.process.configuration.infrastructure.persistence.mapper.NodeConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import com.workflow.process.sla.runtime.application.TaskSlaRuntimeService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.service.delegate.DelegateTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import com.workflow.contracts.identity.model.IdentityGroup;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessTaskCreationTest {
    private final ProcessTaskMapper mapper = mock(ProcessTaskMapper.class);
    private final RuntimeService runtime = mock(RuntimeService.class, RETURNS_DEEP_STUBS);
    private final TaskSlaRuntimeService sla = mock(TaskSlaRuntimeService.class);
    private final TaskService engineTasks = mock(TaskService.class);
    private final IdentityDirectoryPort directory = mock(IdentityDirectoryPort.class);
    private final ProcessTaskService service = new ProcessTaskService(mapper, engineTasks, runtime,
            mock(RepositoryService.class, RETURNS_DEEP_STUBS), mock(NodeConfigMapper.class),
            mock(EntityFormRuntimePort.class), mock(ProcessDefinitionConfigMapper.class), new ObjectMapper(),
            mock(EntityRecordPort.class), directory, sla);

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bothCreationEntrypointsInitializeMirrorAndSlaExactlyOnce(boolean listener) {
        var variables = Map.<String,Object>of("entityCode", "expense", "entityDataId", "record-1", "submitterName", "发起人");
        when(runtime.createProcessInstanceQuery().processInstanceId("pi-1").singleResult().getBusinessKey()).thenReturn("expense:1");
        ProcessTask result;
        if (listener) {
            DelegateTask task = mock(DelegateTask.class);
            when(task.getId()).thenReturn("task-1"); when(task.getProcessInstanceId()).thenReturn("pi-1");
            when(task.getProcessDefinitionId()).thenReturn("pd-1"); when(task.getTaskDefinitionKey()).thenReturn("review");
            when(task.getName()).thenReturn("审批"); when(task.getAssignee()).thenReturn("alice"); when(task.getPriority()).thenReturn(50);
            result = service.createTask(task, variables);
        } else {
            Task task = mock(Task.class);
            when(task.getId()).thenReturn("task-1"); when(task.getProcessInstanceId()).thenReturn("pi-1");
            when(task.getProcessDefinitionId()).thenReturn("pd-1"); when(task.getTaskDefinitionKey()).thenReturn("review");
            when(task.getName()).thenReturn("审批"); when(task.getAssignee()).thenReturn("alice"); when(task.getPriority()).thenReturn(50);
            result = service.createTask(task, variables);
        }
        assertEquals("pi-1", result.getProcessInstanceId()); assertEquals("pd-1", result.getProcessDefinitionId());
        assertEquals("task-1", result.getTaskId()); assertEquals("review", result.getNodeId());
        assertEquals("审批", result.getNodeName()); assertEquals("USER_TASK", result.getNodeType());
        assertEquals("expense:1", result.getBusinessKey()); assertEquals("expense", result.getEntityCode());
        assertEquals("record-1", result.getEntityDataId()); assertEquals("alice", result.getAssigneeId());
        assertEquals("user", result.getAssigneeType()); assertEquals("todo", result.getStatus()); assertEquals(50, result.getPriority());
        assertNotNull(result.getStartTime()); assertNotNull(result.getCreateTime());
        // 两个入口既有的名称种子不同，不能因合并而互相覆盖。
        assertEquals(listener ? null : "发起人", result.getAssigneeName());
        var order = inOrder(mapper, sla);
        order.verify(mapper).insert(result); order.verify(sla).initialize(result, variables);
    }

    @Test
    void duplicateCreationEventsKeepExistingMirrorAndDoNotReinitializeSla() {
        var existing = new ProcessTask(); existing.setStatus("todo");
        when(mapper.selectByTaskId("task-1")).thenReturn(existing);
        Task task = mock(Task.class); when(task.getId()).thenReturn("task-1");
        DelegateTask event = mock(DelegateTask.class); when(event.getId()).thenReturn("task-1");
        assertSame(existing, service.createTask(task, Map.of()));
        assertSame(existing, service.createTask(event, Map.of()));
        verify(mapper, never()).insert(any(ProcessTask.class));
        verifyNoInteractions(sla, runtime);
    }
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"财务组"})
    void emptyCandidateGroupFallsBackToNameOrCodeWithoutInventingNullLabel(String name) {
        Task task = mock(Task.class); when(task.getId()).thenReturn("task-1");
        when(task.getProcessInstanceId()).thenReturn("pi-1");
        var link = mock(org.flowable.identitylink.api.IdentityLink.class);
        when(link.getType()).thenReturn("candidate"); when(link.getGroupId()).thenReturn("finance");
        when(engineTasks.getIdentityLinksForTask("task-1")).thenReturn(List.of(link));
        when(directory.findGroup("finance")).thenReturn(Optional.of(new IdentityGroup("g1", "finance", name)));
        var result = service.createTask(task, Map.of());
        assertNull(result.getAssigneeId()); assertEquals("group", result.getAssigneeType());
        assertEquals(name == null ? "finance" : name, result.getAssigneeName());
    }

    @Test
    void transferRestoresExistingMirrorWithoutInsertingOrRestartingSla() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), ProcessTask.class);
        var existing = new ProcessTask(); existing.setId(42L); existing.setStatus("done");
        existing.setAction("transfer"); existing.setActionLabel("转办"); existing.setComment("交给另一位审批人");
        var restored = new ProcessTask(); restored.setId(42L); restored.setStatus("todo");
        when(mapper.selectByTaskId("task-1")).thenReturn(existing);
        when(mapper.selectById(42L)).thenReturn(restored);
        Task task = mock(Task.class); when(task.getId()).thenReturn("task-1"); when(task.getAssignee()).thenReturn("bob");
        assertSame(restored, service.createTask(task, Map.of()));
        @SuppressWarnings("unchecked")
        var update = org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(mapper).update(isNull(), update.capture());
        String sql = update.getValue().getSqlSet();
        var params = update.getValue().getParamNameValuePairs();
        // 显式绑定 null 才能清除旧结果；仅返回一个空字段 DTO 不能证明数据库更新正确。
        for (String field : List.of("action", "action_label", "comment", "end_time", "duration")) {
            var binding = java.util.regex.Pattern.compile("(?:^|,)" + field + "=#\\{ew.paramNameValuePairs.(MPGENVAL[0-9]+)}")
                    .matcher(sql);
            assertTrue(binding.find(), field + " must be cleared");
            assertNull(params.get(binding.group(1)), field);
        }
        assertTrue(params.containsValue("bob")); assertTrue(params.containsValue("todo"));
        verify(mapper, never()).insert(any(ProcessTask.class));
        verifyNoInteractions(sla, runtime, engineTasks);
    }

}
