package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.ProcessCcRecord;
import com.workflow.entity.SysUser;
import com.workflow.mapper.*;
import com.workflow.service.cc.CcRuntimeContext;
import com.workflow.service.cc.ProcessCcConfigService;
import com.workflow.service.cc.ProcessCcOutboxService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessCcRuntimeServiceTest {
    @Mock TaskService taskService;
    @Mock ProcessTaskMapper processTaskMapper;
    @Mock ProcessOperationLogMapper operationLogMapper;
    @Mock ProcessCcService ccService;
    @Mock ProcessCcOutboxService outboxService;
    @Mock ProcessCcConfigService configService;
    @Mock SysUserMapper userMapper;
    @Mock SysRoleMapper roleMapper;
    @Mock SysUserRoleMapper userRoleMapper;
    @Mock SysGroupMapper groupMapper;
    @Mock SysUserGroupMapper userGroupMapper;
    @Mock SysOrganizationMapper organizationMapper;

    @Test
    void fixedUserRuleCreatesInboxAndOutboxOnce() {
        ProcessCcRuntimeService service = new ProcessCcRuntimeService(
                taskService,
                processTaskMapper,
                operationLogMapper,
                ccService,
                outboxService,
                configService,
                userMapper,
                roleMapper,
                userRoleMapper,
                groupMapper,
                userGroupMapper,
                organizationMapper,
                new ObjectMapper(),
                List.of());
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
        verify(outboxService).enqueue(captor.getValue(), List.of("IN_APP"));
    }

    @Test
    void unmatchedTimingDoesNothing() {
        ProcessCcRuntimeService service = new ProcessCcRuntimeService(
                taskService,
                processTaskMapper,
                operationLogMapper,
                ccService,
                outboxService,
                configService,
                userMapper,
                roleMapper,
                userRoleMapper,
                groupMapper,
                userGroupMapper,
                organizationMapper,
                new ObjectMapper(),
                List.of());
        CcRuntimeContext context = new CcRuntimeContext(
                "process-1", "definition-1", "expense", "费用流程", "biz-1",
                "approve-node", "经理审批", "TASK_CREATE", "admin", Map.of());

        assertEquals(0, service.trigger(context,
                "{\"enabled\":true,\"timings\":[\"TASK_COMPLETE\"],\"recipientRules\":[]}"));
        verifyNoInteractions(ccService, outboxService);
    }
}
