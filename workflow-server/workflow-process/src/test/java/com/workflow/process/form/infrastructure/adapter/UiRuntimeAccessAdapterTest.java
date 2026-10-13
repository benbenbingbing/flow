package com.workflow.process.form.infrastructure.adapter;

import com.workflow.contracts.entity.ui.model.UiRuntimePurpose;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.process.instance.application.ProcessInstanceAccessService;
import com.workflow.process.publish.application.ProcessPublishedSnapshotService;
import com.workflow.process.task.application.LocalAddSignTaskAccessService;
import com.workflow.process.task.application.TaskIdentityAccessService;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.flowable.variable.api.history.HistoricVariableInstanceQuery;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 配置令牌移除后，业务定位值仍必须经过实时任务/实例及表单绑定校验。 */
class UiRuntimeAccessAdapterTest {
    final TaskService tasks = mock(TaskService.class, RETURNS_DEEP_STUBS);
    final HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    final ProcessTaskMapper mapper = mock(ProcessTaskMapper.class);
    final TaskIdentityAccessService identity = mock(TaskIdentityAccessService.class);
    final LocalAddSignTaskAccessService addSign = mock(LocalAddSignTaskAccessService.class);
    final ProcessInstanceAccessService instances = mock(ProcessInstanceAccessService.class);
    final ProcessPublishedSnapshotService snapshots = mock(ProcessPublishedSnapshotService.class);
    final EntityDefinitionMapper definitions = mock(EntityDefinitionMapper.class);
    final UiConfigReleaseService releases = mock(UiConfigReleaseService.class);
    final UiRuntimeAccessAdapter service = new UiRuntimeAccessAdapter(tasks, history, mapper, identity, addSign,
            instances, snapshots, definitions, releases);

    @Test void ordinaryPageCannotClaimApprovalThroughMode() {
        assertEquals(UiRuntimePurpose.STANDALONE, service.resolve("form", Map.of("mode", "approve")).purpose());
        verifyNoInteractions(tasks, identity, snapshots);
    }

    @Test void missingOrCompletedTaskNeverFallsBackToStandalone() {
        assertThrows(BusinessForbiddenException.class, () -> service.resolve("form", Map.of("taskId", "gone")));
    }

    @Test void derivesRealTaskCoordinatesAndRechecksIdentity() {
        Task task = active();
        var result = service.resolve("form", Map.of("taskId", "task"));
        assertEquals("record", result.recordId());
        assertEquals("history", result.processVersionHistoryId());
        assertEquals("node", result.nodeId());
        assertEquals(UiRuntimePurpose.ACTIVE_TASK, result.purpose());
        verify(identity).requireCurrentUserAccess(task);
    }

    @Test void rejectsAnotherFormEvenWhenTaskIsAccessible() {
        active();
        assertThrows(BusinessForbiddenException.class, () -> service.resolve("other-form", Map.of("taskId", "task")));
    }

    @Test void rejectsTamperedRecordAndInstanceCoordinates() {
        active();
        assertThrows(BusinessForbiddenException.class, () -> service.resolve("form", Map.of("taskId", "task", "recordId", "other")));
        assertThrows(BusinessForbiddenException.class, () -> service.resolve("form", Map.of("taskId", "task", "processInstanceId", "other")));
    }

    @Test void transferredTaskImmediatelyLosesAccess() {
        Task task = active();
        doThrow(new ForbiddenException("已转办")).when(identity).requireCurrentUserAccess(task);
        assertThrows(ForbiddenException.class, () -> service.resolve("form", Map.of("taskId", "task")));
    }

    @Test void localAddSignKeepsSubmissionTaskButUsesAuthorizedSourceNode() {
        Task source = active();
        ProcessTask local = mapper.selectByTaskId("task");
        local.setNodeType("ADD_SIGN");
        when(addSign.requireCurrentUserAccess("task", null)).thenReturn(
                new LocalAddSignTaskAccessService.AuthorizedAddSignTask(local, source));
        var result = service.resolve("form", Map.of("taskId", "task"));
        assertEquals("task", result.taskId());
        assertEquals("node", result.nodeId());
        verify(addSign).requireCurrentUserAccess("task", null);
        verifyNoInteractions(identity);
    }

    @Test void historicPageUsesInstanceReadPermissionAndCannotClaimActiveTask() {
        active();
        HistoricProcessInstanceQuery query = mock(HistoricProcessInstanceQuery.class, RETURNS_SELF);
        HistoricProcessInstance instance = mock(HistoricProcessInstance.class);
        when(history.createHistoricProcessInstanceQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(instance);
        when(instance.getProcessDefinitionId()).thenReturn("definition");
        var result = service.resolve("form", Map.of("processInstanceId", "instance", "nodeId", "node", "mode", "approve"));
        assertEquals(UiRuntimePurpose.HISTORICAL, result.purpose());
        assertNull(result.taskId());
        verify(instances).requireReadAccess("instance");
        verifyNoInteractions(identity);
    }

    private Task active() {
        ProcessTask row = new ProcessTask();
        row.setTaskId("task"); row.setNodeType("USER_TASK"); row.setProcessInstanceId("instance");
        row.setEntityCode("entity"); row.setEntityDataId("record");
        when(mapper.selectByTaskId("task")).thenReturn(row);
        Task task = mock(Task.class);
        when(task.getProcessInstanceId()).thenReturn("instance");
        when(task.getProcessDefinitionId()).thenReturn("definition");
        when(task.getTaskDefinitionKey()).thenReturn("node");
        TaskQuery taskQuery = mock(TaskQuery.class, RETURNS_SELF);
        when(tasks.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class, RETURNS_SELF);
        when(history.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
        HistoricVariableInstance entity = mock(HistoricVariableInstance.class);
        HistoricVariableInstance record = mock(HistoricVariableInstance.class);
        when(entity.getValue()).thenReturn("entity");
        when(record.getValue()).thenReturn("record");
        when(variableQuery.variableName(anyString())).thenAnswer(invocation -> {
            when(variableQuery.singleResult()).thenReturn("entityCode".equals(invocation.getArgument(0)) ? entity : record);
            return variableQuery;
        });
        ProcessVersionHistory snapshot = new ProcessVersionHistory(); snapshot.setId("history");
        when(snapshots.getVersionByProcessDefinitionId("definition")).thenReturn(snapshot);
        ProcessNodeForm binding = new ProcessNodeForm(); binding.setFormId("form");
        when(snapshots.getNodeFormsByProcessDefinitionId("definition", "node")).thenReturn(List.of(binding));
        return task;
    }
}
