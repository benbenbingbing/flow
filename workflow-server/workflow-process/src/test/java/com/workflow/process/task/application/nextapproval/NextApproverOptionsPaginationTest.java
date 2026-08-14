package com.workflow.process.task.application.nextapproval;

import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.core.result.PageResult;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.process.task.api.request.NextApproverOptionsRequest;
import com.workflow.process.task.api.response.NextApprovalPreviewStatus;
import com.workflow.process.task.api.response.NextApproverCandidateDTO;
import org.flowable.bpmn.model.UserTask;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NextApproverOptionsPaginationTest {

    @Test
    void sortsByUsernameAndUserIdBeforePaging() {
        NextApprovalRouteService routeService =
                mock(NextApprovalRouteService.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        NextApproverCandidateService service =
                new NextApproverCandidateService(
                        routeService,
                        mock(PersonResolverRuntimeService.class),
                        userMapper,
                        mock(SysRoleMapper.class),
                        mock(SysUserRoleMapper.class),
                        mock(SysGroupMapper.class),
                        mock(SysUserGroupMapper.class),
                        mock(SysOrganizationMapper.class));
        UserTask userTask = new UserTask();
        userTask.setId("next-review");
        NextApproverSelectionPolicy policy =
                new NextApproverSelectionPolicy(
                        true,
                        1,
                        true,
                        true,
                        "CANDIDATE",
                        true,
                        NextApproverSelectionPolicy.SourceType.SCOPE,
                        List.of(new NextApproverSelectionPolicy.Scope(
                                NextApproverSelectionPolicy.ScopeType.ALL_USERS,
                                List.of(),
                                false)),
                        null,
                        Map.of(),
                        "policy-scope");
        NextApprovalTarget target = new NextApprovalTarget(
                userTask, Map.of(), policy);
        Task task = mock(Task.class);
        NextApprovalResolution resolution = new NextApprovalResolution(
                task,
                NextApprovalPreviewStatus.READY,
                null,
                "scope-1",
                List.of(target),
                Map.of());
        when(routeService.resolve(eq("task-1"), any()))
                .thenReturn(resolution);
        when(userMapper.selectList(any())).thenReturn(List.of(
                user("3", "zoe"),
                user("2", "bob"),
                user("1", "alice")));
        NextApproverOptionsRequest request =
                new NextApproverOptionsRequest();
        request.setTargetNodeId("next-review");
        request.setScopeKey("scope-1");
        request.setPageNum(1);
        request.setPageSize(2);

        PageResult<NextApproverCandidateDTO> page =
                service.options("task-1", request);

        assertEquals(List.of("alice", "bob"),
                page.getRecords().stream()
                        .map(NextApproverCandidateDTO::getUsername)
                        .toList());
        assertEquals(3, page.getTotal());
    }

    private SysUser user(String id, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(username);
        user.setStatus(SysUser.Status.ENABLED.getValue());
        user.setDeleted(0);
        return user;
    }
}
