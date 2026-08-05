package com.workflow.service;

import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.cc.application.ProcessCcRuntimeService;
import com.workflow.process.cc.application.ProcessCcService;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
                personResolverRuntimeService);
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
                personResolverRuntimeService);
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
                personResolverRuntimeService);
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
}
