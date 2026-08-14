package com.workflow.process.task.application.nextapproval;

import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.process.task.api.response.NextApprovalPreviewStatus;
import com.workflow.process.task.api.response.NextApproverCandidateDTO;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.UserTask;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class NextApproverMultiInstanceDefaultsTest {

    @Test
    void deployedCollectionVariableWinsOverReResolvingMutableMembership() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectByUsername("bob"))
                .thenReturn(user("user-2", "bob"));
        when(userMapper.selectByUsername("alice"))
                .thenReturn(user("user-1", "alice"));
        PersonResolverRuntimeService resolverRuntimeService =
                mock(PersonResolverRuntimeService.class);
        NextApproverCandidateService service =
                new NextApproverCandidateService(
                        mock(NextApprovalRouteService.class),
                        resolverRuntimeService,
                        userMapper,
                        mock(SysRoleMapper.class),
                        mock(SysUserRoleMapper.class),
                        mock(SysGroupMapper.class),
                        mock(SysUserGroupMapper.class),
                        mock(SysOrganizationMapper.class));
        UserTask userTask = new UserTask();
        userTask.setId("joint-review");
        MultiInstanceLoopCharacteristics loop =
                new MultiInstanceLoopCharacteristics();
        loop.setInputDataItem("${reviewers}");
        userTask.setLoopCharacteristics(loop);
        NextApproverSelectionPolicy policy =
                new NextApproverSelectionPolicy(
                        true,
                        1,
                        true,
                        true,
                        "MULTI_INSTANCE",
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
                userTask,
                Map.of(
                        "collectionSource", "resolver",
                        "collectionResolverCode", "mutableResolver"),
                policy);
        NextApprovalResolution resolution = new NextApprovalResolution(
                mock(Task.class),
                NextApprovalPreviewStatus.READY,
                null,
                "scope-1",
                List.of(target),
                Map.of("reviewers", List.of("bob", "alice")));

        List<NextApproverCandidateDTO> defaults =
                service.defaultAssignees(resolution, target);

        assertEquals(List.of("bob", "alice"), defaults.stream()
                .map(NextApproverCandidateDTO::getUsername)
                .toList());
        verifyNoInteractions(resolverRuntimeService);
    }

    @Test
    void directResolverExposesOnlyThePrimaryResolvedAssignee() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectByUsername("bob"))
                .thenReturn(user("user-2", "bob"));
        when(userMapper.selectByUsername("alice"))
                .thenReturn(user("user-1", "alice"));
        PersonResolverRuntimeService resolverRuntimeService =
                mock(PersonResolverRuntimeService.class);
        when(resolverRuntimeService.resolveUsernames(
                eq("managerResolver"), any()))
                .thenReturn(List.of("bob", "alice"));
        NextApproverCandidateService service =
                new NextApproverCandidateService(
                        mock(NextApprovalRouteService.class),
                        resolverRuntimeService,
                        userMapper,
                        mock(SysRoleMapper.class),
                        mock(SysUserRoleMapper.class),
                        mock(SysGroupMapper.class),
                        mock(SysUserGroupMapper.class),
                        mock(SysOrganizationMapper.class));
        UserTask userTask = new UserTask();
        userTask.setId("manager-review");
        NextApproverSelectionPolicy policy =
                new NextApproverSelectionPolicy(
                        true,
                        1,
                        true,
                        false,
                        "DIRECT",
                        false,
                        NextApproverSelectionPolicy.SourceType.SCOPE,
                        List.of(new NextApproverSelectionPolicy.Scope(
                                NextApproverSelectionPolicy.ScopeType.ALL_USERS,
                                List.of(),
                                false)),
                        null,
                        Map.of(),
                        "policy-scope");
        NextApprovalTarget target = new NextApprovalTarget(
                userTask,
                Map.of(
                        "assigneeType", "resolver",
                        "resolverCode", "managerResolver"),
                policy);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(task.getProcessDefinitionId()).thenReturn("definition-1");
        when(task.getProcessInstanceId()).thenReturn("instance-1");
        NextApprovalResolution resolution = new NextApprovalResolution(
                task,
                NextApprovalPreviewStatus.READY,
                null,
                "scope-1",
                List.of(target),
                Map.of());

        List<NextApproverCandidateDTO> defaults =
                service.defaultAssignees(resolution, target);

        assertEquals(List.of("bob"), defaults.stream()
                .map(NextApproverCandidateDTO::getUsername)
                .toList());
    }

    @Test
    void directLiteralAssigneePrecedesCandidateUsers() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectByUsername("alice"))
                .thenReturn(user("user-1", "alice"));
        when(userMapper.selectByUsername("bob"))
                .thenReturn(user("user-2", "bob"));
        NextApproverCandidateService service =
                new NextApproverCandidateService(
                        mock(NextApprovalRouteService.class),
                        mock(PersonResolverRuntimeService.class),
                        userMapper,
                        mock(SysRoleMapper.class),
                        mock(SysUserRoleMapper.class),
                        mock(SysGroupMapper.class),
                        mock(SysUserGroupMapper.class),
                        mock(SysOrganizationMapper.class));
        UserTask userTask = new UserTask();
        userTask.setId("manager-review");
        userTask.setAssignee("alice");
        userTask.setCandidateUsers(List.of("bob"));
        NextApproverSelectionPolicy policy =
                new NextApproverSelectionPolicy(
                        true,
                        1,
                        true,
                        true,
                        "DIRECT",
                        false,
                        NextApproverSelectionPolicy.SourceType.SCOPE,
                        List.of(new NextApproverSelectionPolicy.Scope(
                                NextApproverSelectionPolicy.ScopeType.ALL_USERS,
                                List.of(),
                                false)),
                        null,
                        Map.of(),
                        "policy-scope");
        NextApprovalTarget target = new NextApprovalTarget(
                userTask,
                Map.of(
                        "assigneeType", "user",
                        "assigneeValue", "alice",
                        "candidateUsers", "bob"),
                policy);
        NextApprovalResolution resolution = new NextApprovalResolution(
                mock(Task.class),
                NextApprovalPreviewStatus.READY,
                null,
                "scope-1",
                List.of(target),
                Map.of());

        List<NextApproverCandidateDTO> defaults =
                service.defaultAssignees(resolution, target);

        assertEquals(List.of("alice"), defaults.stream()
                .map(NextApproverCandidateDTO::getUsername)
                .toList(),
                "DIRECT 默认人员必须是 Flowable assignee，候选人仅是候补");
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
