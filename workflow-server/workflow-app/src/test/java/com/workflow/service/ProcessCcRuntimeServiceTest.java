package com.workflow.service;

import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import com.workflow.process.cc.application.ProcessCcRuntimeService;
import com.workflow.process.cc.application.ProcessCcService;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import com.workflow.process.task.application.TaskIdentityAccessService;
import com.workflow.process.cc.api.request.TaskCcRequest;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.process.cc.infrastructure.persistence.record.ProcessCcRecord;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.process.cc.application.CcRuntimeContext;
import com.workflow.process.cc.application.ProcessCcConfigService;
import com.workflow.process.cc.application.ProcessCcNotificationPublisher;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 流程知会运行时服务测试。
 *
 * <p>被测对象：{@link ProcessCcRuntimeService}，覆盖固定用户规则触发知会收件箱与 Outbox 一次性写入、
 * 时机不匹配时不触发任何动作等场景。
 */
@ExtendWith(MockitoExtension.class)
class ProcessCcRuntimeServiceTest {
    @Mock TaskService taskService;
    @Mock ProcessTaskMapper processTaskMapper;
    @Mock ProcessOperationLogMapper operationLogMapper;
    @Mock ProcessCcService ccService;
    @Mock ProcessCcNotificationPublisher notificationPublisher;
    @Mock ProcessCcConfigService configService;
    @Mock SysUserMapper userMapper;
    @Mock SysRoleMapper roleMapper;
    @Mock SysUserRoleMapper userRoleMapper;
    @Mock SysGroupMapper groupMapper;
    @Mock SysUserGroupMapper userGroupMapper;
    @Mock SysOrganizationMapper organizationMapper;
    @Mock PersonResolverRuntimeService personResolverRuntimeService;
    @Mock TaskIdentityAccessService taskIdentityAccessService;

    /** 人工知会用例设置当前用户后必须清理，避免影响自动知会及其他测试。 */
    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    /** 测试固定用户规则触发收件箱与 Outbox 各一次：验证知会记录的用户、唯一键与渠道符合预期 */
    @Test
    void fixedUserRuleCreatesInboxAndOutboxOnce() {
        ProcessCcRuntimeService service = new ProcessCcRuntimeService(
                taskService,
                processTaskMapper,
                operationLogMapper,
                ccService,
                notificationPublisher,
                configService,
                userMapper,
                roleMapper,
                userRoleMapper,
                groupMapper,
                userGroupMapper,
                organizationMapper,
                new ObjectMapper(),
                List.of(),
                personResolverRuntimeService,
                taskIdentityAccessService);
        SysUser user = new SysUser();
        user.setId("u1");
        user.setUsername("observer");
        user.setNickname("观察员");
        user.setStatus(SysUser.Status.ENABLED.getValue());
        user.setDeleted(0);
        when(userMapper.selectByUsername("observer")).thenReturn(user);
        when(ccService.createCcRecord(any())).thenAnswer(invocation -> {
            ProcessCcRecord record = invocation.getArgument(0);
            record.setId("cc-1");
            return record;
        });
        String config = """
                {
                  "enabled": true,
                  "timings": ["TASK_COMPLETE"],
                  "channels": ["IN_APP"],
                  "recipientRules": [{"type":"USER","values":["observer"]}],
                  "summary": "审批完成知会"
                }
                """;
        CcRuntimeContext context = new CcRuntimeContext(
                "process-1", "definition-1", "expense", "费用流程", "biz-1",
                "approve-node", "经理审批", "TASK_COMPLETE", "admin", Map.of());

        assertEquals(1, service.trigger(context, config));

        ArgumentCaptor<ProcessCcRecord> captor = ArgumentCaptor.forClass(ProcessCcRecord.class);
        verify(ccService).createCcRecord(captor.capture());
        assertEquals("observer", captor.getValue().getCcUserId());
        assertEquals("AUTO:process-1:approve-node:TASK_COMPLETE:observer", captor.getValue().getUniqueKey());
        verify(notificationPublisher)
                .enqueue(captor.getValue(), List.of("IN_APP"));
    }

    /** 禁用用户组不得在新的流程事件中扩展知会收件人。 */
    @Test
    void disabledGroupRuleDoesNotCreateCcRecipient() {
        ProcessCcRuntimeService service = service();
        SysGroup disabledGroup = group("group-disabled", "disabled-group", false);
        when(groupMapper.selectList(any())).thenReturn(List.of(disabledGroup));

        int created = service.trigger(
                context(), groupRuleConfig("disabled-group"));

        assertEquals(0, created);
        verify(userGroupMapper, never())
                .selectUserIdsByGroupId("group-disabled");
        verifyNoInteractions(ccService, notificationPublisher);
    }

    /** 启用用户组仍应正常解析其启用成员，防止状态收紧误伤正常知会。 */
    @Test
    void enabledGroupRuleCreatesCcRecipient() {
        ProcessCcRuntimeService service = service();
        SysGroup enabledGroup = group("group-enabled", "enabled-group", true);
        SysUser member = enabledUser("user-1", "observer");
        when(groupMapper.selectList(any())).thenReturn(List.of(enabledGroup));
        when(userGroupMapper.selectUserIdsByGroupId("group-enabled"))
                .thenReturn(List.of("user-1"));
        when(userMapper.selectById("user-1")).thenReturn(member);

        int created = service.trigger(
                context(), groupRuleConfig("enabled-group"));

        assertEquals(1, created);
        verify(ccService).createCcRecord(any());
        verify(notificationPublisher).enqueue(any(), eq(List.of("IN_APP")));
    }

    /** 测试时机不匹配时不做任何动作：验证返回 0 且未与知会服务、Outbox 交互 */
    @Test
    void unmatchedTimingDoesNothing() {
        ProcessCcRuntimeService service = new ProcessCcRuntimeService(
                taskService,
                processTaskMapper,
                operationLogMapper,
                ccService,
                notificationPublisher,
                configService,
                userMapper,
                roleMapper,
                userRoleMapper,
                groupMapper,
                userGroupMapper,
                organizationMapper,
                new ObjectMapper(),
                List.of(),
                personResolverRuntimeService,
                taskIdentityAccessService);
        CcRuntimeContext context = new CcRuntimeContext(
                "process-1", "definition-1", "expense", "费用流程", "biz-1",
                "approve-node", "经理审批", "TASK_CREATE", "admin", Map.of());

        assertEquals(0, service.trigger(context,
                "{\"enabled\":true,\"timings\":[\"TASK_COMPLETE\"],\"recipientRules\":[]}"));
        verifyNoInteractions(ccService, notificationPublisher);
    }

    /** 测试节点 allowManualCc 配置：false 禁止人工知会，缺省配置保持向后兼容并允许 */
    @Test
    void manualCcAvailabilityUsesNodeConfiguration() {
        ProcessCcRuntimeService service = new ProcessCcRuntimeService(
                taskService,
                processTaskMapper,
                operationLogMapper,
                ccService,
                notificationPublisher,
                configService,
                userMapper,
                roleMapper,
                userRoleMapper,
                groupMapper,
                userGroupMapper,
                organizationMapper,
                new ObjectMapper(),
                List.of(),
                personResolverRuntimeService,
                taskIdentityAccessService);
        TaskQuery taskQuery = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getProcessDefinitionId()).thenReturn("definition-1");
        when(task.getTaskDefinitionKey()).thenReturn("approve-node");

        when(configService.findConfig("definition-1", "approve-node"))
                .thenReturn("{\"allowManualCc\":false}");
        assertFalse(service.isManualCcAllowed("task-1"));

        when(configService.findConfig("definition-1", "approve-node"))
                .thenReturn("{}");
        assertTrue(service.isManualCcAllowed("task-1"));
    }

    /** 共享校验认可的业务组候选人应能人工知会，且不再依赖 Flowable 自带的用户组关系。 */
    @Test
    void manualCcAcceptsCandidateThroughSharedTaskAccess() {
        UserContext.setCurrentUser("operator-id", "operator");
        Task task = manualCcTask();
        when(task.getProcessInstanceId()).thenReturn("process-1");
        when(task.getProcessDefinitionId()).thenReturn("definition-1");
        when(task.getTaskDefinitionKey()).thenReturn("approve-node");
        when(configService.findConfig("definition-1", "approve-node")).thenReturn("{}");
        ProcessTask mirror = new ProcessTask();
        mirror.setProcessKey("expense");
        when(processTaskMapper.selectByTaskId("task-1")).thenReturn(mirror);
        when(userMapper.selectByUsername("observer"))
                .thenReturn(enabledUser("observer-id", "observer"));

        assertEquals(1, service().manualCc("task-1", manualCcRequest()));

        var order = inOrder(taskIdentityAccessService, ccService);
        order.verify(taskIdentityAccessService).requireCurrentUserAccess(task);
        order.verify(ccService).createCcRecord(any());
        verify(notificationPublisher).enqueue(any(), eq(List.of("IN_APP")));
        verify(operationLogMapper).insert(any(ProcessOperationLog.class));
    }

    /** 身份校验失败必须在配置、候选收件人解析及持久化之前退出，不能借知会接口越权。 */
    @Test
    void manualCcStopsBeforeSideEffectsWhenSharedTaskAccessDenies() {
        UserContext.setCurrentUser("outsider-id", "outsider");
        Task task = manualCcTask();
        doThrow(new ForbiddenException("当前用户不是该任务的候选办理人"))
                .when(taskIdentityAccessService).requireCurrentUserAccess(task);

        assertThrows(ForbiddenException.class,
                () -> service().manualCc("task-1", manualCcRequest()));

        verify(taskIdentityAccessService).requireCurrentUserAccess(task);
        verifyNoInteractions(configService, processTaskMapper, userMapper,
                ccService, notificationPublisher, operationLogMapper);
    }

    /** 仅提供引擎任务，不模拟引擎身份组表，使入口测试约束权限必须委派给共享服务。 */
    private Task manualCcTask() {
        TaskQuery query = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId("task-1")).thenReturn(query);
        when(query.singleResult()).thenReturn(task);
        return task;
    }

    /** 构造人工知会请求。 */
    private TaskCcRequest manualCcRequest() {
        TaskCcRequest request = new TaskCcRequest();
        request.setUserIds(List.of("observer"));
        return request;
    }

    /** 构造含当前测试替身的知会运行时服务。 */
    private ProcessCcRuntimeService service() {
        return new ProcessCcRuntimeService(
                taskService,
                processTaskMapper,
                operationLogMapper,
                ccService,
                notificationPublisher,
                configService,
                userMapper,
                roleMapper,
                userRoleMapper,
                groupMapper,
                userGroupMapper,
                organizationMapper,
                new ObjectMapper(),
                List.of(),
                personResolverRuntimeService,
                taskIdentityAccessService);
    }

    /** 构造自动知会运行时上下文。 */
    private CcRuntimeContext context() {
        return new CcRuntimeContext(
                "process-1", "definition-1", "expense", "费用流程", "biz-1",
                "approve-node", "经理审批", "TASK_COMPLETE", "admin", Map.of());
    }

    /** 构造按用户组解析收件人的知会规则。 */
    private String groupRuleConfig(String groupCode) {
        return """
                {
                  "enabled": true,
                  "timings": ["TASK_COMPLETE"],
                  "channels": ["IN_APP"],
                  "recipientRules": [{"type":"GROUP","values":["%s"]}]
                }
                """.formatted(groupCode);
    }

    /** 构造用户组记录。 */
    private SysGroup group(String id, String code, boolean enabled) {
        SysGroup group = new SysGroup();
        group.setId(id);
        group.setGroupCode(code);
        group.setStatus(enabled
                ? SysGroup.Status.ENABLED.getValue()
                : SysGroup.Status.DISABLED.getValue());
        group.setDeleted(0);
        return group;
    }

    /** 构造可接收知会的启用用户。 */
    private SysUser enabledUser(String id, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setNickname("观察员");
        user.setStatus(SysUser.Status.ENABLED.getValue());
        user.setDeleted(0);
        return user;
    }
}
