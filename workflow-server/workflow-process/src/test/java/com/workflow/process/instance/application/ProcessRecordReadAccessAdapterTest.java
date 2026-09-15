package com.workflow.process.instance.application;

import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.error.ForbiddenException;
import com.workflow.process.cc.infrastructure.persistence.mapper.ProcessCcRecordMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.publish.application.ProcessPublishedSnapshotService;
import com.workflow.process.task.application.LocalAddSignTaskAccessService;
import com.workflow.process.task.application.TaskIdentityAccessService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.flowable.variable.api.history.HistoricVariableInstanceQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** 使用真实实例授权验证已办/知会只读访问，持久化与流程引擎查询使用替身。 */
class ProcessRecordReadAccessAdapterTest {

    private final HistoryService historyService = mock(HistoryService.class);
    private final TaskService taskService = mock(TaskService.class);
    private final ProcessCcRecordMapper ccMapper = mock(ProcessCcRecordMapper.class);
    private final ProcessPublishedSnapshotService published = mock(ProcessPublishedSnapshotService.class);
    private final HistoricTaskInstanceQuery historyTasks = mock(HistoricTaskInstanceQuery.class, RETURNS_SELF);
    private final HistoricVariableInstance entityCode = mock(HistoricVariableInstance.class);
    private final HistoricVariableInstance recordId = mock(HistoricVariableInstance.class);
    private final ProcessVersionHistory version = new ProcessVersionHistory();
    private ProcessRecordReadAccessAdapter adapter;

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUser("lisi-id", "lisi");
        var instances = mock(HistoricProcessInstanceQuery.class, RETURNS_SELF);
        var instance = mock(HistoricProcessInstance.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(instances);
        when(instances.singleResult()).thenReturn(instance);
        when(instance.getProcessDefinitionId()).thenReturn("definition-1");
        when(instance.getStartUserId()).thenReturn("admin");
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historyTasks);
        var tasks = mock(TaskQuery.class, RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(tasks);
        when(tasks.list()).thenReturn(List.of());
        var variables = mock(HistoricVariableInstanceQuery.class, RETURNS_SELF);
        var entityQuery = mock(HistoricVariableInstanceQuery.class);
        var recordQuery = mock(HistoricVariableInstanceQuery.class);
        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variables);
        when(variables.variableName("entityCode")).thenReturn(entityQuery);
        when(variables.variableName("entityDataId")).thenReturn(recordQuery);
        when(entityQuery.singleResult()).thenReturn(entityCode);
        when(recordQuery.singleResult()).thenReturn(recordId);
        when(entityCode.getValue()).thenReturn("expense");
        when(recordId.getValue()).thenReturn("record-1");
        version.setId("history-1");
        when(published.getVersionByProcessDefinitionId("definition-1")).thenReturn(version);
        var access = new ProcessInstanceAccessService(historyService, taskService, ccMapper,
                mock(CurrentUserRoleService.class), mock(TaskIdentityAccessService.class),
                mock(LocalAddSignTaskAccessService.class));
        adapter = new ProcessRecordReadAccessAdapter(access, historyService, published);
    }

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void historicApproverCanReadWithoutAnyCurrentTaskOrEntityPermission() {
        when(historyTasks.count()).thenReturn(1L);

        assertDoesNotThrow(() -> adapter.requireReadAccess("expense", "record-1", "process-1", "history-1"));

        verify(historyTasks).taskAssignee("lisi-id");
        verify(published).getVersionByProcessDefinitionId("definition-1");
    }

    @Test
    void ccRecipientCanReadWithoutApprovalParticipation() {
        when(ccMapper.existsForUser("process-1", "lisi-id", "lisi")).thenReturn(1L);

        assertDoesNotThrow(() -> adapter.requireReadAccess("expense", "record-1", "process-1", "history-1"));

        verify(historyTasks).taskAssignee("lisi-id");
        verify(historyTasks).taskAssignee("lisi");
    }

    @Test
    void unrelatedUserCannotReadEvenWithMatchingRecordAndRelease() {
        assertThrows(ForbiddenException.class,
                () -> adapter.requireReadAccess("expense", "record-1", "process-1", "history-1"));

        verify(historyService, never()).createHistoricVariableInstanceQuery();
        verifyNoInteractions(published);
    }

    @ParameterizedTest
    @ValueSource(strings = {"entity", "record", "version", "missing-record"})
    void instanceVisibilityDoesNotBypassRecordOrReleaseBinding(String mismatch) {
        when(ccMapper.existsForUser("process-1", "lisi-id", "lisi")).thenReturn(1L);
        switch (mismatch) {
            case "entity" -> when(entityCode.getValue()).thenReturn("other_entity");
            case "record" -> when(recordId.getValue()).thenReturn("other-record");
            case "version" -> version.setId("other-history");
            case "missing-record" -> when(recordId.getValue()).thenReturn(null);
        }

        assertThrows(BusinessForbiddenException.class,
                () -> adapter.requireReadAccess("expense", "record-1", "process-1", "history-1"));
    }

    @Test
    void missingProcessCannotBroadenToAnyReadableInstance() {
        assertThrows(BusinessForbiddenException.class,
                () -> adapter.requireReadAccess("expense", "record-1", null, "history-1"));
        verifyNoInteractions(historyService, published, ccMapper);
    }
}
