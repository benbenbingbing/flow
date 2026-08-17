package com.workflow.process.task.application.nextapproval;

import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.core.result.PageResult;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.task.api.request.NextApproverOptionsRequest;
import com.workflow.process.task.api.response.NextApprovalPreviewStatus;
import com.workflow.process.task.api.response.NextApproverCandidateDTO;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.UserTask;
import org.flowable.task.api.Task;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NextApproverNodeAssignmentCandidateTest {

    @Test
    void multiInstanceStaticUsersDefaultAndAllowedBothUseFullConfiguredList() {
        Fixture fixture = fixture(
                "codex-user", "verify-user", "lisi", "test-user");
        UserTask userTask = new UserTask();
        userTask.setId("manager-review");
        userTask.setName("经理审批");
        MultiInstanceLoopCharacteristics loop =
                new MultiInstanceLoopCharacteristics();
        loop.setInputDataItem("${_wfMultiInstanceUsers_manager_review}");
        userTask.setLoopCharacteristics(loop);
        NextApprovalTarget target = target(
                userTask,
                "MULTI_INSTANCE",
                Map.of(
                        "assignmentConfigVersion", 2,
                        "assigneeType", "user",
                        "assigneeValue", "codex-user",
                        "candidateUsers", "codex-user,verify-user,lisi,test-user"));
        NextApprovalResolution resolution = resolution(
                target,
                Map.of("_wfMultiInstanceUsers_manager_review",
                        List.of("codex-user", "verify-user", "lisi", "test-user")));
        when(fixture.routeService().resolve(eq("task-1"), any()))
                .thenReturn(resolution);

        List<NextApproverCandidateDTO> defaults =
                fixture.service().defaultAssignees(resolution, target);
        PageResult<NextApproverCandidateDTO> options =
                fixture.service().options("task-1", optionsRequest(target));
        List<SysUser> completionAllowed = fixture.service().resolveAllowed(
                resolution,
                target,
                PersonResolveUsage.CANDIDATE);

        List<String> expected = List.of(
                "codex-user", "verify-user", "lisi", "test-user");
        assertEquals(expected, usernames(defaults),
                "多实例默认值应从启动快照读取完整人员");
        assertEquals(expected.stream().sorted().toList(),
                usernames(options.getRecords()).stream().sorted().toList(),
                "options 必须包含完整配置人员");
        assertEquals(expected,
                completionAllowed.stream().map(SysUser::getUsername).toList(),
                "完成重验的允许范围必须与 options 一致，都包含完整配置人员");
    }

    @Test
    void directResolverUsesAssigneeUsageForPreviewOptionsAndCompleteValidation() {
        Fixture fixture = fixture("alice", "bob");
        UserTask userTask = new UserTask();
        userTask.setId("manager-review");
        userTask.setName("经理审批");
        NextApprovalTarget target = target(
                userTask,
                "DIRECT",
                Map.of(
                        "assignmentConfigVersion", 2,
                        "assigneeType", "resolver",
                        "resolverCode", "managerResolver",
                        "extraParams", Map.of("level", 2)));
        NextApprovalResolution resolution = resolution(target, Map.of());
        when(fixture.routeService().resolve(eq("task-1"), any()))
                .thenReturn(resolution);
        when(fixture.resolverRuntimeService().resolveUsernames(
                eq("managerResolver"), any()))
                .thenReturn(List.of("alice", "bob"));

        List<NextApproverCandidateDTO> defaults =
                fixture.service().defaultAssignees(resolution, target);
        PageResult<NextApproverCandidateDTO> options =
                fixture.service().options("task-1", optionsRequest(target));
        List<SysUser> completionAllowed = fixture.service().resolveAllowed(
                resolution,
                target,
                PersonResolveUsage.CANDIDATE);

        assertEquals(List.of("alice"), usernames(defaults),
                "DIRECT 预览只展示基础 resolver 返回的首个默认办理人");
        assertEquals(List.of("alice", "bob"), usernames(options.getRecords()),
                "options 必须允许在基础 resolver 的完整结果内改选");
        assertEquals(List.of("alice", "bob"),
                completionAllowed.stream().map(SysUser::getUsername).toList(),
                "complete 权威重验必须与 options 使用同一集合");
        assertResolverUsage(
                fixture.resolverRuntimeService(),
                "managerResolver",
                PersonResolveUsage.ASSIGNEE,
                3);
        verify(fixture.resolverRuntimeService(), never()).requireConfigured(
                "managerResolver", PersonResolveUsage.CANDIDATE);
        verify(fixture.resolverRuntimeService(), never()).requireConfigured(
                "managerResolver", PersonResolveUsage.MULTI_INSTANCE);
    }

    @Test
    void multiInstanceResolverUsesMultiInstanceUsageAcrossAllThreePaths() {
        Fixture fixture = fixture("alice", "bob");
        UserTask userTask = new UserTask();
        userTask.setId("joint-review");
        userTask.setName("联合审批");
        MultiInstanceLoopCharacteristics loop =
                new MultiInstanceLoopCharacteristics();
        loop.setInputDataItem("${reviewers}");
        userTask.setLoopCharacteristics(loop);
        NextApprovalTarget target = target(
                userTask,
                "MULTI_INSTANCE",
                Map.of(
                        "assignmentConfigVersion", 2,
                        "assigneeType", "resolver",
                        "resolverCode", "jointResolver",
                        "extraParams", Map.of("region", "CN")));
        NextApprovalResolution resolution = resolution(target, Map.of());
        when(fixture.routeService().resolve(eq("task-1"), any()))
                .thenReturn(resolution);
        when(fixture.resolverRuntimeService().resolveUsernames(
                eq("jointResolver"), any()))
                .thenReturn(List.of("alice", "bob"));

        List<NextApproverCandidateDTO> defaults =
                fixture.service().defaultAssignees(resolution, target);
        PageResult<NextApproverCandidateDTO> options =
                fixture.service().options("task-1", optionsRequest(target));
        List<SysUser> completionAllowed = fixture.service().resolveAllowed(
                resolution,
                target,
                PersonResolveUsage.CANDIDATE);

        assertEquals(List.of("alice", "bob"), usernames(defaults));
        assertEquals(List.of("alice", "bob"), usernames(options.getRecords()));
        assertEquals(List.of("alice", "bob"),
                completionAllowed.stream().map(SysUser::getUsername).toList());
        assertResolverUsage(
                fixture.resolverRuntimeService(),
                "jointResolver",
                PersonResolveUsage.MULTI_INSTANCE,
                4);
        verify(fixture.resolverRuntimeService(), never()).requireConfigured(
                "jointResolver", PersonResolveUsage.CANDIDATE);
        verify(fixture.resolverRuntimeService(), never()).requireConfigured(
                "jointResolver", PersonResolveUsage.ASSIGNEE);
    }

    @Test
    void multiInstanceSnapshotIsDefaultOnlyAndAllowedScopeIsRecomputed() {
        Fixture fixture = fixture("alice", "bob");
        UserTask userTask = new UserTask();
        userTask.setId("joint-review");
        MultiInstanceLoopCharacteristics loop =
                new MultiInstanceLoopCharacteristics();
        loop.setInputDataItem("${reviewers}");
        userTask.setLoopCharacteristics(loop);
        NextApprovalTarget target = target(
                userTask,
                "MULTI_INSTANCE",
                Map.of(
                        "assignmentConfigVersion", 2,
                        "assigneeType", "resolver",
                        "resolverCode", "jointResolver"));
        NextApprovalResolution resolution = resolution(
                target, Map.of("reviewers", List.of("alice")));
        when(fixture.routeService().resolve(eq("task-1"), any()))
                .thenReturn(resolution);
        when(fixture.resolverRuntimeService().resolveUsernames(
                eq("jointResolver"), any()))
                .thenReturn(List.of("bob"));

        List<NextApproverCandidateDTO> defaults =
                fixture.service().defaultAssignees(resolution, target);
        PageResult<NextApproverCandidateDTO> options =
                fixture.service().options("task-1", optionsRequest(target));
        List<SysUser> completionAllowed = fixture.service().resolveAllowed(
                resolution,
                target,
                PersonResolveUsage.CANDIDATE);

        assertEquals(List.of(), usernames(defaults),
                "启动快照中的旧默认人员若已不在当前配置范围，默认展示应为空");
        assertEquals(List.of("bob"), usernames(options.getRecords()));
        assertEquals(
                List.of("bob"),
                completionAllowed.stream().map(SysUser::getUsername).toList());
        assertResolverUsage(
                fixture.resolverRuntimeService(),
                "jointResolver",
                PersonResolveUsage.MULTI_INSTANCE,
                3);
    }

    @Test
    void candidateModeUsesTheSameBaseCandidateUsersForPreviewOptionsAndComplete() {
        Fixture fixture = fixture("alice", "bob");
        UserTask userTask = new UserTask();
        userTask.setId("shared-review");
        userTask.setName("共享审批");
        userTask.setCandidateUsers(List.of("alice", "bob"));
        NextApprovalTarget target = target(
                userTask,
                "CANDIDATE",
                Map.of(
                        "assignmentConfigVersion", 2,
                        "assigneeType", "user",
                        "assigneeValue", "",
                        "candidateUsers", "alice,bob"));
        NextApprovalResolution resolution = resolution(target, Map.of());
        when(fixture.routeService().resolve(eq("task-1"), any()))
                .thenReturn(resolution);

        List<NextApproverCandidateDTO> defaults =
                fixture.service().defaultAssignees(resolution, target);
        PageResult<NextApproverCandidateDTO> options =
                fixture.service().options("task-1", optionsRequest(target));
        List<SysUser> completionAllowed = fixture.service().resolveAllowed(
                resolution,
                target,
                PersonResolveUsage.CANDIDATE);

        assertEquals(List.of("alice", "bob"), usernames(defaults));
        assertEquals(List.of("alice", "bob"), usernames(options.getRecords()));
        assertEquals(List.of("alice", "bob"),
                completionAllowed.stream().map(SysUser::getUsername).toList());
    }

    @Test
    void referencedCandidateTaskUsesSourceBpmnAssignmentsAcrossAllPaths() {
        Fixture fixture = fixture("alice", "bob");
        UserTask current = new UserTask();
        current.setId("current-review");
        UserTask source = new UserTask();
        source.setId("shared-source");
        source.setCandidateUsers(List.of("alice", "bob"));
        NextApprovalTarget target = new NextApprovalTarget(
                current,
                Map.of("assignmentConfigVersion", 2,
                        "assigneeType", "candidate"),
                policy("CANDIDATE"),
                source);
        NextApprovalResolution resolution = resolution(target, Map.of());
        when(fixture.routeService().resolve(eq("task-1"), any()))
                .thenReturn(resolution);

        assertEquals(List.of("alice", "bob"),
                usernames(fixture.service().defaultAssignees(
                        resolution, target)));
        assertEquals(List.of("alice", "bob"),
                usernames(fixture.service().options(
                        "task-1", optionsRequest(target)).getRecords()));
        assertEquals(List.of("alice", "bob"),
                fixture.service().resolveAllowed(
                                resolution,
                                target,
                                PersonResolveUsage.CANDIDATE)
                        .stream().map(SysUser::getUsername).toList());
    }

    @Test
    void referencedResolverUsageComesFromCurrentNodeMode() {
        Fixture fixture = fixture("alice");
        UserTask current = new UserTask();
        current.setId("joint-current");
        MultiInstanceLoopCharacteristics currentLoop =
                new MultiInstanceLoopCharacteristics();
        currentLoop.setInputDataItem("${jointUsers}");
        current.setLoopCharacteristics(currentLoop);
        UserTask ordinarySource = new UserTask();
        ordinarySource.setId("ordinary-source");
        NextApprovalTarget multiTarget = new NextApprovalTarget(
                current,
                Map.of("assignmentConfigVersion", 2,
                        "assigneeType", "resolver",
                        "resolverCode", "sharedResolver"),
                policy("MULTI_INSTANCE"),
                ordinarySource);
        NextApprovalResolution multiResolution = resolution(
                multiTarget, Map.of());
        when(fixture.resolverRuntimeService().resolveUsernames(
                eq("sharedResolver"), any()))
                .thenReturn(List.of("alice"));

        fixture.service().resolveAllowed(
                multiResolution,
                multiTarget,
                PersonResolveUsage.CANDIDATE);

        ArgumentCaptor<PersonResolveRequest> request =
                ArgumentCaptor.forClass(PersonResolveRequest.class);
        verify(fixture.resolverRuntimeService()).resolveUsernames(
                eq("sharedResolver"), request.capture());
        assertEquals(PersonResolveUsage.MULTI_INSTANCE,
                request.getValue().usage());
    }

    @Test
    void resolverRequestUsesProcessConfigBoundToTheTaskDeployment() {
        Fixture fixture = fixture("alice");
        RepositoryService repositoryService = mock(RepositoryService.class);
        ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
        ProcessDefinition definition = mock(ProcessDefinition.class);
        ProcessVersionHistoryMapper versionMapper =
                mock(ProcessVersionHistoryMapper.class);
        ProcessVersionHistory history = new ProcessVersionHistory();
        history.setProcessConfigId("process-config-v7");
        when(repositoryService.createProcessDefinitionQuery())
                .thenReturn(query);
        when(query.processDefinitionId("definition-v2"))
                .thenReturn(query);
        when(query.singleResult()).thenReturn(definition);
        when(definition.getDeploymentId()).thenReturn("deployment-v7");
        when(versionMapper.findByDeploymentId("deployment-v7"))
                .thenReturn(Optional.of(history));
        ReflectionTestUtils.setField(
                fixture.service(), "repositoryService", repositoryService);
        ReflectionTestUtils.setField(
                fixture.service(), "processVersionMapper", versionMapper);

        UserTask current = new UserTask();
        current.setId("current-review");
        UserTask source = new UserTask();
        source.setId("resolver-source");
        NextApprovalTarget target = new NextApprovalTarget(
                current,
                Map.of("assignmentConfigVersion", 2,
                        "assigneeType", "resolver",
                        "resolverCode", "sharedResolver"),
                policy("DIRECT"),
                source);
        NextApprovalResolution resolution = resolution(target, Map.of());
        when(fixture.resolverRuntimeService().resolveUsernames(
                eq("sharedResolver"), any()))
                .thenReturn(List.of("alice"));

        fixture.service().resolveAllowed(
                resolution,
                target,
                PersonResolveUsage.CANDIDATE);

        ArgumentCaptor<PersonResolveRequest> request =
                ArgumentCaptor.forClass(PersonResolveRequest.class);
        verify(fixture.resolverRuntimeService()).resolveUsernames(
                eq("sharedResolver"), request.capture());
        assertEquals("process-config-v7",
                request.getValue().processConfigId());
    }

    @Test
    void legacyMultiInstanceNodeAssignmentUsesCollectionResolverInsteadOfStaleBase() {
        Fixture fixture = fixture("alice", "bob");
        UserTask userTask = new UserTask();
        userTask.setId("legacy-joint-review");
        MultiInstanceLoopCharacteristics loop =
                new MultiInstanceLoopCharacteristics();
        loop.setInputDataItem("${legacyReviewers}");
        userTask.setLoopCharacteristics(loop);
        NextApprovalTarget target = target(
                userTask,
                "MULTI_INSTANCE",
                Map.of(
                        "assigneeType", "resolver",
                        "resolverCode", "staleBaseResolver",
                        "collectionSource", "resolver",
                        "collectionResolverCode", "legacyJointResolver"));
        NextApprovalResolution resolution = resolution(target, Map.of());
        when(fixture.resolverRuntimeService().resolveUsernames(
                eq("legacyJointResolver"), any()))
                .thenReturn(List.of("alice", "bob"));

        List<SysUser> allowed = fixture.service().resolveAllowed(
                resolution,
                target,
                PersonResolveUsage.CANDIDATE);

        assertEquals(
                List.of("alice", "bob"),
                allowed.stream().map(SysUser::getUsername).toList());
        ArgumentCaptor<PersonResolveRequest> request =
                ArgumentCaptor.forClass(PersonResolveRequest.class);
        verify(fixture.resolverRuntimeService()).resolveUsernames(
                eq("legacyJointResolver"), request.capture());
        assertEquals(
                PersonResolveUsage.MULTI_INSTANCE,
                request.getValue().usage());
        verify(fixture.resolverRuntimeService(), never())
                .resolveUsernames(eq("staleBaseResolver"), any());
        verify(fixture.resolverRuntimeService(), never()).requireConfigured(
                "legacyJointResolver", PersonResolveUsage.CANDIDATE);
    }

    @Test
    void legacyIdAndMixedFieldsDriveDefaultsAndCompletionScope() {
        Fixture fixture = fixture(
                "mixed-user",
                "group-user",
                "role-user",
                "audit-user");
        when(fixture.userMapper().selectById("user-id"))
                .thenReturn(user("user-id", "id-user"));
        SysGroup group = new SysGroup();
        group.setId("group-id");
        group.setGroupCode("finance");
        group.setStatus(SysGroup.Status.ENABLED.getValue());
        group.setDeleted(0);
        when(fixture.groupMapper().selectList(any()))
                .thenReturn(List.of(group));
        when(fixture.userGroupMapper().selectUserIdsByGroupId("group-id"))
                .thenReturn(List.of("user-1"));
        SysRole manager = role("role-id", "manager");
        SysRole auditor = role("audit-role", "AUDITOR");
        when(fixture.roleMapper().selectList(any()))
                .thenReturn(
                        List.of(manager),
                        List.of(auditor),
                        List.of(manager),
                        List.of(auditor),
                        List.of(manager),
                        List.of(auditor));
        when(fixture.userRoleMapper().selectUserIdsByRoleId("role-id"))
                .thenReturn(List.of("user-2"));
        when(fixture.userRoleMapper().selectUserIdsByRoleId("audit-role"))
                .thenReturn(List.of("user-3"));

        UserTask userTask = new UserTask();
        userTask.setId("legacy-joint-review");
        MultiInstanceLoopCharacteristics loop =
                new MultiInstanceLoopCharacteristics();
        loop.setInputDataItem("${legacyReviewers}");
        userTask.setLoopCharacteristics(loop);
        NextApprovalTarget target = target(
                userTask,
                "MULTI_INSTANCE",
                Map.ofEntries(
                        Map.entry("collectionSource", "variable"),
                        Map.entry(
                                "collectionResolverCode",
                                "staleResolver"),
                        Map.entry("multiInstanceUserIds", "user-id"),
                        Map.entry("multiInstanceGroupIds", "group-id"),
                        Map.entry("multiInstanceRoleIds", "role-id"),
                        Map.entry(
                                "multiInstanceUsers",
                                "mixed-user,ROLE_AUDITOR")));
        NextApprovalResolution resolution = resolution(target, Map.of());

        List<NextApproverCandidateDTO> defaults =
                fixture.service().defaultAssignees(resolution, target);
        List<SysUser> allowed = fixture.service().resolveAllowed(
                resolution,
                target,
                PersonResolveUsage.CANDIDATE);

        List<String> expected = List.of(
                "id-user",
                "mixed-user",
                "group-user",
                "role-user",
                "audit-user");
        assertEquals(expected, usernames(defaults));
        assertEquals(
                expected,
                allowed.stream().map(SysUser::getUsername).toList());
        verify(fixture.resolverRuntimeService(), never())
                .resolveUsernames(eq("staleResolver"), any());
    }

    @Test
    void invalidExplicitVersionIsRejectedBeforePreparedCollectionSnapshot() {
        Fixture fixture = fixture("alice");
        UserTask userTask = new UserTask();
        userTask.setId("joint-review");
        MultiInstanceLoopCharacteristics loop =
                new MultiInstanceLoopCharacteristics();
        loop.setInputDataItem("${reviewers}");
        userTask.setLoopCharacteristics(loop);
        NextApprovalTarget target = target(
                userTask,
                "MULTI_INSTANCE",
                Map.of("assignmentConfigVersion", 0));
        NextApprovalResolution resolution = resolution(
                target, Map.of("reviewers", List.of("alice")));

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().defaultAssignees(
                        resolution, target));
    }

    private void assertResolverUsage(
            PersonResolverRuntimeService resolverRuntimeService,
            String resolverCode,
            PersonResolveUsage usage,
            int invocationCount) {
        ArgumentCaptor<PersonResolveRequest> captor =
                ArgumentCaptor.forClass(PersonResolveRequest.class);
        verify(resolverRuntimeService, times(invocationCount))
                .resolveUsernames(eq(resolverCode), captor.capture());
        assertEquals(
                java.util.Collections.nCopies(invocationCount, usage),
                captor.getAllValues().stream()
                        .map(PersonResolveRequest::usage)
                        .toList());
        verify(resolverRuntimeService, atLeastOnce())
                .requireConfigured(resolverCode, usage);
    }

    private NextApproverOptionsRequest optionsRequest(
            NextApprovalTarget target) {
        NextApproverOptionsRequest request =
                new NextApproverOptionsRequest();
        request.setTargetNodeId(target.userTask().getId());
        request.setScopeKey("scope-1");
        request.setPageNum(1);
        request.setPageSize(20);
        return request;
    }

    private NextApprovalTarget target(
            UserTask userTask,
            String assignmentMode,
            Map<String, Object> assigneeConfig) {
        return new NextApprovalTarget(
                userTask, assigneeConfig, policy(assignmentMode));
    }

    private NextApproverSelectionPolicy policy(String assignmentMode) {
        return new NextApproverSelectionPolicy(
                true,
                1,
                true,
                true,
                assignmentMode,
                !"DIRECT".equals(assignmentMode),
                NextApproverSelectionPolicy.SourceType.NODE_ASSIGNMENT,
                List.of(),
                null,
                Map.of(),
                "policy-scope");
    }

    private NextApprovalResolution resolution(
            NextApprovalTarget target,
            Map<String, Object> variables) {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(task.getProcessDefinitionId()).thenReturn("definition-v2");
        when(task.getProcessInstanceId()).thenReturn("instance-1");
        return new NextApprovalResolution(
                task,
                NextApprovalPreviewStatus.READY,
                null,
                "scope-1",
                List.of(target),
                variables);
    }

    private Fixture fixture(String... usernames) {
        NextApprovalRouteService routeService =
                mock(NextApprovalRouteService.class);
        PersonResolverRuntimeService resolverRuntimeService =
                mock(PersonResolverRuntimeService.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysUserRoleMapper userRoleMapper =
                mock(SysUserRoleMapper.class);
        SysGroupMapper groupMapper = mock(SysGroupMapper.class);
        SysUserGroupMapper userGroupMapper =
                mock(SysUserGroupMapper.class);
        for (int index = 0; index < usernames.length; index++) {
            String username = usernames[index];
            SysUser configured = user("user-" + index, username);
            when(userMapper.selectByUsername(username))
                    .thenReturn(configured);
            when(userMapper.selectById("user-" + index))
                    .thenReturn(configured);
        }
        NextApproverCandidateService service =
                new NextApproverCandidateService(
                        routeService,
                        resolverRuntimeService,
                        userMapper,
                        roleMapper,
                        userRoleMapper,
                        groupMapper,
                        userGroupMapper,
                        mock(SysOrganizationMapper.class));
        return new Fixture(
                service,
                routeService,
                resolverRuntimeService,
                userMapper,
                roleMapper,
                userRoleMapper,
                groupMapper,
                userGroupMapper);
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

    private SysRole role(String id, String roleCode) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleCode(roleCode);
        role.setStatus(SysRole.Status.ENABLED.getValue());
        role.setDeleted(0);
        return role;
    }

    private List<String> usernames(
            List<NextApproverCandidateDTO> candidates) {
        return candidates.stream()
                .map(NextApproverCandidateDTO::getUsername)
                .toList();
    }

    private record Fixture(
            NextApproverCandidateService service,
            NextApprovalRouteService routeService,
            PersonResolverRuntimeService resolverRuntimeService,
            SysUserMapper userMapper,
            SysRoleMapper roleMapper,
            SysUserRoleMapper userRoleMapper,
            SysGroupMapper groupMapper,
            SysUserGroupMapper userGroupMapper) {
    }
}
