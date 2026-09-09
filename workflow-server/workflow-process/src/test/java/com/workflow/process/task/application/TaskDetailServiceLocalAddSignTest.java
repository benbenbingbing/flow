package com.workflow.process.task.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.ui.runtime.UiRuntimePurpose;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.form.application.EntityFormRuntimeService;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.process.publish.application.ProcessPublishedSnapshotService;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 加签详情保持本地任务身份，发布表单必须绑定实际源引擎节点且先通过完整授权。 */
class TaskDetailServiceLocalAddSignTest {

    private final ProcessTaskMapper taskMapper = mock(ProcessTaskMapper.class);
    private final RuntimeService runtimeService = mock(RuntimeService.class);
    private final EntityDataDynamicService dataService = mock(EntityDataDynamicService.class);
    private final ProcessPublishedSnapshotService snapshotService = mock(ProcessPublishedSnapshotService.class);
    private final EntityFormRuntimeService formRuntimeService = mock(EntityFormRuntimeService.class);
    private final LocalAddSignTaskAccessService accessService = mock(LocalAddSignTaskAccessService.class);
    private final ProcessTask local = new ProcessTask();
    private TaskDetailService service;

    @BeforeEach
    void setUp() {
        service = new TaskDetailService(taskMapper, dataService, mock(EntityDefinitionMapper.class),
                mock(EntityFormMapper.class), mock(EntityFieldMapper.class), runtimeService,
                mock(HistoryService.class), snapshotService, formRuntimeService, new ObjectMapper(), accessService);
        local.setTaskId("addsign-1");
        local.setNodeType("ADD_SIGN");
        local.setProcessInstanceId("process-1");
        local.setProcessDefinitionId("stale-definition");
        local.setNodeId("stale-node");
        local.setFormKey("stale-form");
        when(taskMapper.selectByTaskId("addsign-1")).thenReturn(local);
    }

    @Test
    void validAddSignDetailLoadsSourceDeploymentFormWithoutReplacingChildIdentity() {
        Task source = mock(Task.class);
        when(source.getProcessDefinitionId()).thenReturn("source-definition");
        when(source.getTaskDefinitionKey()).thenReturn("source-node");
        when(source.getFormKey()).thenReturn("source-form");
        when(accessService.requireCurrentUserAccess("addsign-1", "process-1"))
                .thenReturn(new LocalAddSignTaskAccessService.AuthorizedAddSignTask(local, source));
        ProcessInstanceQuery query = mock(ProcessInstanceQuery.class);
        ProcessInstance instance = mock(ProcessInstance.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
        when(query.processInstanceId("process-1")).thenReturn(query);
        when(query.singleResult()).thenReturn(instance);
        when(instance.getStartTime()).thenReturn(new Date());
        when(runtimeService.getVariables("process-1")).thenReturn(new HashMap<>());
        ProcessNodeForm binding = new ProcessNodeForm();
        binding.setFormId("published-form");
        binding.setFormReleaseId("release-2");
        binding.setFormReleaseVersion(2);
        binding.setIsReadonly(0);
        ProcessVersionHistory history = new ProcessVersionHistory();
        history.setId("history-1");
        when(snapshotService.getNodeFormsContextByProcessDefinitionId("source-definition", "source-node"))
                .thenReturn(new ProcessPublishedSnapshotService.PublishedNodeForms(history, List.of(binding)));
        EntityForm form = new EntityForm();
        form.setId("published-form");
        form.setFormName("源节点发布表单");
        when(formRuntimeService.getByBinding(binding, "history-1", UiRuntimePurpose.ACTIVE_TASK)).thenReturn(form);

        var detail = service.getTaskDetail("addsign-1");

        assertEquals("addsign-1", detail.getProcessTask().getTaskId());
        assertEquals("published-form", detail.getFormConfig().getEntityFormId());
        assertEquals("source-form", detail.getFormConfig().getFormKey());
        assertEquals("release-2", detail.getFormConfig().getFormReleaseId());
        verify(snapshotService, never()).getNodeFormsContextByProcessDefinitionId("stale-definition", "stale-node");
    }

    @Test
    void invalidAddSignStopsBeforeAnyFormOrEntityRead() {
        when(accessService.requireCurrentUserAccess("addsign-1", "process-1"))
                .thenThrow(new ForbiddenException("加签无效或无权办理"));

        assertThrows(ForbiddenException.class, () -> service.getTaskDetail("addsign-1"));

        verifyNoInteractions(runtimeService, snapshotService, formRuntimeService, dataService);
    }

    @Test
    void controllerAccessEntryDelegatesToFullLocalAddSignGuard() {
        service.requireLocalAddSignTaskAccess("addsign-1");

        verify(accessService).requireCurrentUserAccess("addsign-1", null);
        verifyNoInteractions(taskMapper, runtimeService, snapshotService, formRuntimeService, dataService);
    }
}
