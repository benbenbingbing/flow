package com.workflow.process.instance.application;

import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.identity.port.IdentityDirectoryPort;
import com.workflow.core.error.ForbiddenException;
import com.workflow.process.cc.infrastructure.persistence.mapper.ProcessCcRecordMapper;
import com.workflow.process.task.application.TaskIdentityAccessService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 流程可见性复用真实任务身份校验，并保留历史办理人的查看权限。 */
class ProcessInstanceAccessServiceTest {

    private static final String INSTANCE_ID = "process-1";
    private TaskService taskService;
    private HistoryService historyService;
    private SysGroupMapper groupMapper;
    private SysRoleMapper roleMapper;
    private Task task;
    private HistoricTaskInstanceQuery historyByUserId;
    private HistoricTaskInstanceQuery historyByUsername;
    private ProcessInstanceAccessService service;

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUser("alice-id", "alice");
        taskService = mock(TaskService.class);
        historyService = mock(HistoryService.class);
        groupMapper = mock(SysGroupMapper.class);
        roleMapper = mock(SysRoleMapper.class);
        task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId(INSTANCE_ID)).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));

        HistoricProcessInstanceQuery processQuery = mock(HistoricProcessInstanceQuery.class);
        HistoricProcessInstance instance = mock(HistoricProcessInstance.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
        when(processQuery.processInstanceId(INSTANCE_ID)).thenReturn(processQuery);
        when(processQuery.singleResult()).thenReturn(instance);
        when(instance.getStartUserId()).thenReturn("another-initiator");

        HistoricTaskInstanceQuery historyQuery = mock(HistoricTaskInstanceQuery.class);
        historyByUserId = mock(HistoricTaskInstanceQuery.class);
        historyByUsername = mock(HistoricTaskInstanceQuery.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historyQuery);
        when(historyQuery.processInstanceId(INSTANCE_ID)).thenReturn(historyQuery);
        when(historyQuery.taskAssignee("alice-id")).thenReturn(historyByUserId);
        when(historyQuery.taskAssignee("alice")).thenReturn(historyByUsername);

        service = new ProcessInstanceAccessService(
                historyService, taskService, mock(ProcessCcRecordMapper.class),
                mock(CurrentUserRoleService.class),
                new TaskIdentityAccessService(taskService, groupMapper, roleMapper,
                        mock(IdentityDirectoryPort.class)));
    }

    @AfterEach
    void clearCurrentUser() {
        UserContext.clear();
    }

    /** 普通业务组与角色候选成员在认领前即可打开流程详情。 */
    @ParameterizedTest
    @ValueSource(strings = {"reviewers", "review-group-id", "ROLE_reviewer", "ROLE_review-role-id"})
    void unclaimedBusinessGroupOrRoleCandidateCanReadProcess(String candidateGroup) {
        IdentityLink candidate = mock(IdentityLink.class);
        when(candidate.getType()).thenReturn("candidate");
        when(candidate.getGroupId()).thenReturn(candidateGroup);
        when(taskService.getIdentityLinksForTask("task-1")).thenReturn(List.of(candidate));
        SysGroup group = new SysGroup();
        group.setId("review-group-id");
        group.setGroupCode("reviewers");
        group.setStatus("0");
        group.setDeleted(0);
        when(groupMapper.selectGroupsByUserId("alice-id")).thenReturn(List.of(group));
        SysRole role = new SysRole();
        role.setId("review-role-id");
        role.setRoleCode("reviewer");
        role.setStatus("0");
        role.setDeleted(0);
        when(roleMapper.selectRolesByUserId("alice-id")).thenReturn(List.of(role));

        assertDoesNotThrow(() -> service.requireReadAccess(INSTANCE_ID));

        verify(taskService).getIdentityLinksForTask("task-1");
        verify(historyService, never()).createHistoricTaskInstanceQuery();
    }

    @Test
    void taskClaimedByAnotherUserDoesNotGrantProcessAccessWithoutHistory() {
        when(task.getAssignee()).thenReturn("bob");

        assertThrows(ForbiddenException.class, () -> service.requireReadAccess(INSTANCE_ID));

        // 认领后的残留候选组关系不能继续授予可见性；实际历史参与另行判断。
        verify(taskService, never()).getIdentityLinksForTask(anyString());
        verifyNoInteractions(groupMapper, roleMapper);
        verify(historyByUserId).count();
        verify(historyByUsername).count();
    }

    @ParameterizedTest
    @ValueSource(strings = {"alice-id", "alice"})
    void historicApproverCanStillReadAfterTaskPassesToAnotherUser(String historicalIdentity) {
        when(task.getAssignee()).thenReturn("bob");
        when(("alice-id".equals(historicalIdentity) ? historyByUserId : historyByUsername).count())
                .thenReturn(1L);

        assertDoesNotThrow(() -> service.requireReadAccess(INSTANCE_ID));

        verify(taskService, never()).getIdentityLinksForTask(anyString());
        verifyNoInteractions(groupMapper, roleMapper);
    }
}
