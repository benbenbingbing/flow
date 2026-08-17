package com.workflow.process.assignment.infrastructure.flowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.process.task.application.nextapproval.NextApproverOverrideStore;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.event.FlowableActivityEvent;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiInstanceCollectionListenerSecurityTest {

    @Test
    void startupPrecomputationUsesSelectedDeploymentAndFiltersDisabledUsers() {
        MultiInstanceCollectionListener listener =
                new MultiInstanceCollectionListener();
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ReflectionTestUtils.setField(
                listener, "repositoryService", repositoryService);
        ReflectionTestUtils.setField(listener, "userMapper", userMapper);
        ReflectionTestUtils.setField(
                listener, "objectMapper", new ObjectMapper());

        BpmnModel versionOne = modelWithUsers(
                "active", "disabled");
        BpmnModel versionTwo = modelWithUsers("new-draft-user");
        when(repositoryService.getBpmnModel("definition-v1"))
                .thenReturn(versionOne);
        when(repositoryService.getBpmnModel("definition-v2"))
                .thenReturn(versionTwo);
        when(userMapper.selectByUsername("active"))
                .thenReturn(user("u-active", "active", true));
        when(userMapper.selectByUsername("disabled"))
                .thenReturn(user("u-disabled", "disabled", false));
        Map<String, Object> variables = new LinkedHashMap<>();

        listener.prepareVariables("definition-v1", variables);

        assertEquals(List.of("active"), variables.get("reviewers"));
        verify(repositoryService).getBpmnModel("definition-v1");
        verify(repositoryService, never()).getBpmnModel("definition-v2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void legacyConfigWithoutVersionKeepsMixedUserGroupAndRoleUnion() {
        MultiInstanceCollectionListener listener =
                new MultiInstanceCollectionListener();
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysGroupMapper groupMapper = mock(SysGroupMapper.class);
        SysUserGroupMapper userGroupMapper =
                mock(SysUserGroupMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysUserRoleMapper userRoleMapper =
                mock(SysUserRoleMapper.class);
        PersonResolverRuntimeService resolverRuntimeService =
                mock(PersonResolverRuntimeService.class);
        ReflectionTestUtils.setField(listener, "userMapper", userMapper);
        ReflectionTestUtils.setField(listener, "groupMapper", groupMapper);
        ReflectionTestUtils.setField(
                listener, "userGroupMapper", userGroupMapper);
        ReflectionTestUtils.setField(listener, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(
                listener, "userRoleMapper", userRoleMapper);
        ReflectionTestUtils.setField(
                listener,
                "personResolverRuntimeService",
                resolverRuntimeService);

        when(userMapper.selectByUsername("active"))
                .thenReturn(user("u-active", "active", true));
        when(userMapper.selectByUsername("disabled"))
                .thenReturn(user("u-disabled", "disabled", false));
        when(userMapper.selectById("u-group"))
                .thenReturn(user("u-group", "group-user", true));
        when(userMapper.selectById("u-role"))
                .thenReturn(user("u-role", "role-user", true));
        when(userMapper.selectById("u-disabled-member"))
                .thenReturn(user(
                        "u-disabled-member", "disabled-member", false));

        SysGroup enabledGroup = group(
                "g-enabled", "enabled-group", true);
        SysGroup disabledGroup = group(
                "g-disabled", "disabled-group", false);
        when(groupMapper.selectByGroupCode("enabled-group"))
                .thenReturn(enabledGroup);
        when(groupMapper.selectByGroupCode("disabled-group"))
                .thenReturn(disabledGroup);
        when(userGroupMapper.selectUserIdsByGroupId("g-enabled"))
                .thenReturn(List.of("u-group", "u-disabled-member"));

        SysRole enabledRole = role(
                "r-enabled", "enabled-role", true);
        SysRole disabledRole = role(
                "r-disabled", "disabled-role", false);
        when(roleMapper.selectList(any()))
                .thenReturn(List.of(enabledRole), List.of(disabledRole));
        when(userRoleMapper.selectUserIdsByRoleId("r-enabled"))
                .thenReturn(List.of("u-role", "u-disabled-member"));

        List<String> result = ReflectionTestUtils.invokeMethod(
                listener,
                "resolvePublishedUsers",
                "process-config",
                "joint-review",
                "联合审批",
                Map.of(
                        "multiInstanceUsernames",
                        List.of("active", "disabled"),
                        "multiInstanceGroupCodes",
                        List.of("enabled-group", "disabled-group"),
                        "multiInstanceRoleCodes",
                        List.of("enabled-role", "disabled-role")),
                Map.of(),
                "instance-1",
                "definition-1");

        assertEquals(
                List.of("active", "group-user", "role-user"),
                result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void legacyIdsAndOldMixedFieldUseTheSameStaticAssignmentPath() {
        MultiInstanceCollectionListener listener =
                new MultiInstanceCollectionListener();
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysGroupMapper groupMapper = mock(SysGroupMapper.class);
        SysUserGroupMapper userGroupMapper =
                mock(SysUserGroupMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysUserRoleMapper userRoleMapper =
                mock(SysUserRoleMapper.class);
        PersonResolverRuntimeService resolverRuntimeService =
                mock(PersonResolverRuntimeService.class);
        ReflectionTestUtils.setField(listener, "userMapper", userMapper);
        ReflectionTestUtils.setField(listener, "groupMapper", groupMapper);
        ReflectionTestUtils.setField(
                listener, "userGroupMapper", userGroupMapper);
        ReflectionTestUtils.setField(listener, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(
                listener, "userRoleMapper", userRoleMapper);
        ReflectionTestUtils.setField(
                listener,
                "personResolverRuntimeService",
                resolverRuntimeService);

        when(userMapper.selectById("user-1"))
                .thenReturn(user("user-1", "id-user", true));
        when(userMapper.selectByUsername("mixed-user"))
                .thenReturn(user("user-2", "mixed-user", true));
        when(userMapper.selectById("group-member"))
                .thenReturn(user(
                        "group-member", "group-user", true));
        when(userMapper.selectById("role-member"))
                .thenReturn(user("role-member", "role-user", true));
        when(userMapper.selectById("audit-member"))
                .thenReturn(user(
                        "audit-member", "audit-user", true));

        SysGroup group = group("group-1", "finance", true);
        when(groupMapper.selectByGroupCode("group-1")).thenReturn(null);
        when(groupMapper.selectById("group-1")).thenReturn(group);
        when(userGroupMapper.selectUserIdsByGroupId("group-1"))
                .thenReturn(List.of("group-member"));
        SysRole role = role("role-1", "manager", true);
        SysRole auditRole = role("role-2", "AUDITOR", true);
        when(roleMapper.selectList(any()))
                .thenReturn(List.of(role), List.of(auditRole));
        when(userRoleMapper.selectUserIdsByRoleId("role-1"))
                .thenReturn(List.of("role-member"));
        when(userRoleMapper.selectUserIdsByRoleId("role-2"))
                .thenReturn(List.of("audit-member"));

        List<String> result = ReflectionTestUtils.invokeMethod(
                listener,
                "resolvePublishedUsers",
                "process-config",
                "joint-review",
                "联合审批",
                Map.ofEntries(
                        Map.entry("collectionSource", "variable"),
                        Map.entry(
                                "collectionResolverCode",
                                "staleResolver"),
                        Map.entry("multiInstanceUserIds", "user-1"),
                        Map.entry(
                                "multiInstanceGroupIds",
                                "group-1"),
                        Map.entry("multiInstanceRoleIds", "role-1"),
                        Map.entry(
                                "multiInstanceUsers",
                                "mixed-user,ROLE_AUDITOR")),
                Map.of(),
                "instance-1",
                "definition-1");

        assertEquals(
                List.of(
                        "id-user",
                        "mixed-user",
                        "group-user",
                        "role-user",
                        "audit-user"),
                result);
        verify(resolverRuntimeService, never())
                .resolveUsernames(eq("staleResolver"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void deployedLegacyDocumentsAreMergedBeforeRuntimeResolution() {
        MultiInstanceCollectionListener listener =
                new MultiInstanceCollectionListener();
        ReflectionTestUtils.setField(
                listener, "objectMapper", new ObjectMapper());
        BpmnModel model = modelWithConfigDocuments(
                """
                {"multiInstanceUserIds":"alice",
                 "multiInstanceUsers":"carol,ROLE_AUDITOR"}
                """,
                """
                {"multiInstanceUsernames":"bob",
                 "multiInstanceGroupIds":"finance-id",
                 "multiInstanceRoleIds":"manager-id",
                 "multiInstanceUsers":"dave,ROLE_REVIEWER"}
                """);
        UserTask task = (UserTask) model.getMainProcess()
                .getFlowElement("joint-review");

        Map<String, Object> deployed = ReflectionTestUtils.invokeMethod(
                listener, "deployedAssigneeConfig", task);

        assertEquals(
                List.of("alice", "bob", "carol", "dave"),
                deployed.get("multiInstanceUsernames"));
        assertEquals(
                List.of("finance-id"),
                deployed.get("multiInstanceGroupCodes"));
        assertEquals(
                List.of("manager-id", "AUDITOR", "REVIEWER"),
                deployed.get("multiInstanceRoleCodes"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void versionTwoFixedUsersUseBaseAssignmentAndIgnoreStaleLegacySources() {
        MultiInstanceCollectionListener listener =
                new MultiInstanceCollectionListener();
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysGroupMapper groupMapper = mock(SysGroupMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        PersonResolverRuntimeService resolverRuntimeService =
                mock(PersonResolverRuntimeService.class);
        ReflectionTestUtils.setField(listener, "userMapper", userMapper);
        ReflectionTestUtils.setField(listener, "groupMapper", groupMapper);
        ReflectionTestUtils.setField(listener, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(
                listener,
                "personResolverRuntimeService",
                resolverRuntimeService);

        when(userMapper.selectByUsername("owner"))
                .thenReturn(user("u-owner", "owner", true));
        when(userMapper.selectByUsername("candidate"))
                .thenReturn(user("u-candidate", "candidate", true));

        List<String> result = ReflectionTestUtils.invokeMethod(
                listener,
                "resolvePublishedUsers",
                "process-config-v2",
                "joint-review",
                "联合审批",
                Map.ofEntries(
                        Map.entry("assignmentConfigVersion", 2),
                        Map.entry("assigneeType", "user"),
                        Map.entry("assigneeValue", "owner"),
                        Map.entry("candidateUsers", "candidate,owner"),
                        Map.entry("multiInstanceUsernames", "legacy-user"),
                        Map.entry("multiInstanceGroupCodes", "legacy-group"),
                        Map.entry("multiInstanceRoleCodes", "legacy-role"),
                        Map.entry("collectionSource", "resolver"),
                        Map.entry(
                                "collectionResolverCode",
                                "legacyResolver")),
                Map.of(),
                "instance-v2",
                "definition-v2");

        assertEquals(
                List.of("owner", "candidate"),
                result,
                "v2 多实例必须完整复用基础固定办理人和候选人，且不得被残留旧字段污染");
        verify(resolverRuntimeService, never())
                .resolveUsernames(eq("legacyResolver"), any());
        verify(groupMapper, never()).selectByGroupCode("legacy-group");
        verify(roleMapper, never()).selectList(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolverResultsAreMappedToEnabledLocalUsernames() {
        MultiInstanceCollectionListener listener =
                new MultiInstanceCollectionListener();
        SysUserMapper userMapper = mock(SysUserMapper.class);
        PersonResolverRuntimeService resolverRuntimeService =
                mock(PersonResolverRuntimeService.class);
        ReflectionTestUtils.setField(listener, "userMapper", userMapper);
        ReflectionTestUtils.setField(
                listener,
                "personResolverRuntimeService",
                resolverRuntimeService);
        when(resolverRuntimeService.resolveUsernames(
                org.mockito.ArgumentMatchers.eq("miResolver"),
                any()))
                .thenReturn(List.of("active-id", "disabled"));
        when(userMapper.selectById("active-id"))
                .thenReturn(user("active-id", "active", true));
        when(userMapper.selectByUsername("disabled"))
                .thenReturn(user("disabled-id", "disabled", false));

        List<String> result = ReflectionTestUtils.invokeMethod(
                listener,
                "resolvePublishedUsers",
                "process-config",
                "joint-review",
                "联合审批",
                Map.of(
                        "collectionSource", "resolver",
                        "collectionResolverCode", "miResolver"),
                Map.of(),
                "instance-1",
                "definition-1");

        assertEquals(List.of("active"), result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"visible", "show", "display"})
    void visibilityAliasesMakeResolverFailureRequired(
            String visibilityKey) {
        RuntimeFixture fixture = runtimeFixture(visibilityKey);
        when(fixture.resolverRuntimeService().resolveUsernames(
                eq("miResolver"), any()))
                .thenThrow(new IllegalStateException("resolver offline"));

        assertThrows(
                RuntimeException.class,
                () -> fixture.listener().onEvent(fixture.event()));
        assertTrue(fixture.listener().isFailOnException());
    }

    @Test
    void malformedConfigDeclaringNextApproverSelectionFailsFast() {
        RuntimeFixture fixture = runtimeFixture(modelWithConfigDocument(
                "{\"collectionSource\":\"resolver\","
                        + "\"nextApproverSelection\":"));

        assertThrows(
                RuntimeException.class,
                () -> fixture.listener().onEvent(fixture.event()));
    }

    @Test
    void malformedHiddenVersionTwoConfigFailsFastAtRuntime() {
        RuntimeFixture fixture = runtimeFixture(modelWithConfigDocument(
                "{\"assignmentConfigVersion\":2,"
                        + "\"assigneeType\":\"resolver\""));

        assertThrows(
                RuntimeException.class,
                () -> fixture.listener().onEvent(fixture.event()));
    }

    @Test
    void malformedHiddenVersionTwoConfigFailsFastDuringStartup() {
        MultiInstanceCollectionListener listener =
                new MultiInstanceCollectionListener();
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        ReflectionTestUtils.setField(
                listener, "repositoryService", repositoryService);
        ReflectionTestUtils.setField(
                listener, "objectMapper", new ObjectMapper());
        when(repositoryService.getBpmnModel("definition-v1"))
                .thenReturn(modelWithConfigDocument(
                        "{\"assignmentConfigVersion\":2,"
                                + "\"assigneeType\":\"resolver\""));

        assertThrows(
                RuntimeException.class,
                () -> listener.prepareVariables(
                        "definition-v1", new LinkedHashMap<>()));
    }

    @Test
    void malformedLegacyConfigWithoutSelectionRemainsCompatible() {
        RuntimeFixture fixture = runtimeFixture(modelWithConfigDocument(
                "{\"collectionSource\":\"resolver\""));

        assertDoesNotThrow(
                () -> fixture.listener().onEvent(fixture.event()));
    }

    @Test
    void legacyEditableButHiddenMultiInstanceFailsClosed() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("collectionSource", "resolver");
        config.put("collectionResolverCode", "miResolver");
        config.put(
                "nextApproverSelection",
                Map.of("visible", false, "editable", true));
        RuntimeFixture fixture = runtimeFixture(
                modelWithConfigDocument(
                        new ObjectMapper().valueToTree(config).toString()));

        assertThrows(
                RuntimeException.class,
                () -> fixture.listener().prepareVariables(
                        "definition-v1", new LinkedHashMap<>()));
        assertThrows(
                RuntimeException.class,
                () -> fixture.listener().onEvent(fixture.event()));
    }

    @Test
    void visibleMultiInstanceWithNoResolvedUsersFailsFast() {
        RuntimeFixture fixture = runtimeFixture(true);
        when(fixture.resolverRuntimeService().resolveUsernames(
                eq("miResolver"), any()))
                .thenReturn(List.of());

        assertThrows(
                RuntimeException.class,
                () -> fixture.listener().onEvent(fixture.event()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"editable", "allowEdit", "allowModify"})
    void startupAllowsEditableVisibleNodeWithoutDefaultUsers(
            String editableKey) {
        MultiInstanceCollectionListener listener =
                new MultiInstanceCollectionListener();
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        PersonResolverRuntimeService resolverRuntimeService =
                mock(PersonResolverRuntimeService.class);
        ReflectionTestUtils.setField(
                listener, "repositoryService", repositoryService);
        ReflectionTestUtils.setField(
                listener,
                "personResolverRuntimeService",
                resolverRuntimeService);
        ReflectionTestUtils.setField(
                listener, "userMapper", mock(SysUserMapper.class));
        ReflectionTestUtils.setField(
                listener, "objectMapper", new ObjectMapper());
        when(repositoryService.getBpmnModel("definition-v1"))
                .thenReturn(modelWithResolverSelection(editableKey));
        when(resolverRuntimeService.resolveUsernames(
                eq("miResolver"), any()))
                .thenReturn(List.of());
        Map<String, Object> variables = new LinkedHashMap<>();

        assertDoesNotThrow(() -> listener.prepareVariables(
                "definition-v1", variables));

        assertEquals(false, variables.containsKey("reviewers"));
    }

    @Test
    void allowModifyTakesPrecedenceOverStaleAllowEditAlias() {
        MultiInstanceCollectionListener listener =
                new MultiInstanceCollectionListener();
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        PersonResolverRuntimeService resolverRuntimeService =
                mock(PersonResolverRuntimeService.class);
        ReflectionTestUtils.setField(
                listener, "repositoryService", repositoryService);
        ReflectionTestUtils.setField(
                listener,
                "personResolverRuntimeService",
                resolverRuntimeService);
        ReflectionTestUtils.setField(
                listener, "userMapper", mock(SysUserMapper.class));
        ReflectionTestUtils.setField(
                listener, "objectMapper", new ObjectMapper());
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("collectionSource", "resolver");
        config.put("collectionResolverCode", "miResolver");
        config.put(
                "nextApproverSelection",
                Map.of(
                        "visible", true,
                        "allowModify", true,
                        "allowEdit", false));
        when(repositoryService.getBpmnModel("definition-v1"))
                .thenReturn(modelWithConfigDocument(
                        new ObjectMapper().valueToTree(config).toString()));
        when(resolverRuntimeService.resolveUsernames(
                eq("miResolver"), any()))
                .thenReturn(List.of());
        Map<String, Object> variables = new LinkedHashMap<>();

        assertDoesNotThrow(() -> listener.prepareVariables(
                "definition-v1", variables));
        assertEquals(false, variables.containsKey("reviewers"));
    }

    @Test
    void enteringTheSameEditableVisibleNodeStillRejectsEmptyParticipants() {
        RuntimeFixture fixture = runtimeFixture(
                modelWithResolverSelection("allowModify"));
        when(fixture.resolverRuntimeService().resolveUsernames(
                eq("miResolver"), any()))
                .thenReturn(List.of());

        assertThrows(
                RuntimeException.class,
                () -> fixture.listener().onEvent(fixture.event()));
    }

    @Test
    void hiddenVersionTwoNodeRejectsEmptyParticipantsDuringStartup() {
        MultiInstanceCollectionListener listener =
                new MultiInstanceCollectionListener();
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        PersonResolverRuntimeService resolverRuntimeService =
                mock(PersonResolverRuntimeService.class);
        ReflectionTestUtils.setField(
                listener, "repositoryService", repositoryService);
        ReflectionTestUtils.setField(
                listener,
                "personResolverRuntimeService",
                resolverRuntimeService);
        ReflectionTestUtils.setField(
                listener, "userMapper", mock(SysUserMapper.class));
        ReflectionTestUtils.setField(
                listener, "objectMapper", new ObjectMapper());
        when(repositoryService.getBpmnModel("definition-v1"))
                .thenReturn(modelWithVersionTwoHiddenResolver());
        when(resolverRuntimeService.resolveUsernames(
                eq("miResolver"), any()))
                .thenReturn(List.of());

        assertThrows(
                RuntimeException.class,
                () -> listener.prepareVariables(
                        "definition-v1", new LinkedHashMap<>()));
    }

    @Test
    void hiddenVersionTwoNodeFailsFastOnResolverErrorWhenEntering() {
        RuntimeFixture fixture = runtimeFixture(
                modelWithVersionTwoHiddenResolver());
        when(fixture.resolverRuntimeService().resolveUsernames(
                eq("miResolver"), any()))
                .thenThrow(new IllegalStateException("resolver offline"));

        assertThrows(
                RuntimeException.class,
                () -> fixture.listener().onEvent(fixture.event()));
    }

    @Test
    void invalidExplicitAssignmentVersionCannotHideBehindExistingCollection() {
        MultiInstanceCollectionListener listener =
                new MultiInstanceCollectionListener();
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        ReflectionTestUtils.setField(
                listener, "repositoryService", repositoryService);
        ReflectionTestUtils.setField(
                listener, "objectMapper", new ObjectMapper());
        when(repositoryService.getBpmnModel("definition-v1"))
                .thenReturn(modelWithConfigDocument(
                        "{\"assignmentConfigVersion\":0}"));
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("reviewers", List.of("forged-user"));

        assertThrows(
                RuntimeException.class,
                () -> listener.prepareVariables(
                        "definition-v1", variables));
    }

    @Test
    void visibleMultiInstanceRejectsScalarCollectionValue() {
        RuntimeFixture fixture = runtimeFixture(true);
        when(fixture.runtimeService().getVariable(
                "instance-v1", "reviewers"))
                .thenReturn("alice");

        assertThrows(
                RuntimeException.class,
                () -> fixture.listener().onEvent(fixture.event()));
        verify(fixture.resolverRuntimeService(), never())
                .resolveUsernames(eq("miResolver"), any());
    }

    @Test
    void hiddenLegacyMultiInstanceKeepsCompatibilityOnResolverFailure() {
        RuntimeFixture fixture = runtimeFixture(false);
        when(fixture.resolverRuntimeService().resolveUsernames(
                eq("miResolver"), any()))
                .thenThrow(new IllegalStateException("legacy resolver down"));

        assertDoesNotThrow(
                () -> fixture.listener().onEvent(fixture.event()));
    }

    @Test
    void consumedOverrideWriteFailureRollsBackEvenForLegacyNode() {
        RuntimeFixture fixture = runtimeFixture(false);
        when(fixture.overrideStore().hasStagedOverride(
                "instance-v1", "joint-review"))
                .thenReturn(true);
        when(fixture.overrideStore().consumeForMultiInstance(
                "instance-v1", "joint-review"))
                .thenReturn(List.of("alice"));
        doThrow(new IllegalStateException("variable write failed"))
                .when(fixture.runtimeService())
                .setVariable(
                        "instance-v1",
                        "reviewers",
                        List.of("alice"));

        assertThrows(
                RuntimeException.class,
                () -> fixture.listener().onEvent(fixture.event()));
    }

    @Test
    void referencedResolverUsesCurrentMultiInstanceUsageAndWritesCollection() {
        BpmnModel model = modelWithConfigDocument("""
                {"assignmentConfigVersion":2,
                 "assigneeType":"node_reference",
                 "referencedNodeId":"shared-source"}
                """);
        model.getMainProcess().addFlowElement(configuredTask(
                "shared-source",
                """
                {"assignmentConfigVersion":2,
                 "assigneeType":"resolver",
                 "resolverCode":"sharedResolver"}
                """));
        RuntimeFixture fixture = runtimeFixture(model);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ReflectionTestUtils.setField(
                fixture.listener(), "userMapper", userMapper);
        when(fixture.resolverRuntimeService().resolveUsernames(
                eq("sharedResolver"), any()))
                .thenReturn(List.of("alice"));
        when(userMapper.selectByUsername("alice"))
                .thenReturn(user("user-alice", "alice", true));

        fixture.listener().onEvent(fixture.event());

        ArgumentCaptor<PersonResolveRequest> request =
                ArgumentCaptor.forClass(PersonResolveRequest.class);
        verify(fixture.resolverRuntimeService()).resolveUsernames(
                eq("sharedResolver"), request.capture());
        assertEquals(PersonResolveUsage.MULTI_INSTANCE,
                request.getValue().usage());
        verify(fixture.runtimeService()).setVariable(
                "instance-v1", "reviewers", List.of("alice"));
    }

    @Test
    void referencedFixedUsersPopulateCurrentMultiInstanceCollection() {
        BpmnModel model = modelWithConfigDocument("""
                {"assignmentConfigVersion":2,
                 "assigneeType":"node_reference",
                 "referencedNodeId":"fixed-source"}
                """);
        model.getMainProcess().addFlowElement(configuredTask(
                "fixed-source",
                """
                {"assignmentConfigVersion":2,
                 "assigneeType":"user",
                 "assigneeValue":"alice",
                 "candidateUsers":"bob"}
                """));
        RuntimeFixture fixture = runtimeFixture(model);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ReflectionTestUtils.setField(
                fixture.listener(), "userMapper", userMapper);
        when(userMapper.selectByUsername("alice"))
                .thenReturn(user("user-alice", "alice", true));
        when(userMapper.selectByUsername("bob"))
                .thenReturn(user("user-bob", "bob", true));

        fixture.listener().onEvent(fixture.event());

        verify(fixture.runtimeService()).setVariable(
                "instance-v1",
                "reviewers",
                List.of("alice", "bob"));
        verify(fixture.resolverRuntimeService(), never())
                .resolveUsernames(any(), any());
    }

    private RuntimeFixture runtimeFixture(boolean visible) {
        return runtimeFixture(visible ? "visible" : null);
    }

    private RuntimeFixture runtimeFixture(String visibilityKey) {
        return runtimeFixture(modelWithResolver(visibilityKey));
    }

    private RuntimeFixture runtimeFixture(BpmnModel deployedModel) {
        MultiInstanceCollectionListener listener =
                new MultiInstanceCollectionListener();
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        PersonResolverRuntimeService resolverRuntimeService =
                mock(PersonResolverRuntimeService.class);
        NextApproverOverrideStore overrideStore =
                mock(NextApproverOverrideStore.class);
        ReflectionTestUtils.setField(
                listener, "repositoryService", repositoryService);
        ReflectionTestUtils.setField(
                listener, "runtimeService", runtimeService);
        ReflectionTestUtils.setField(
                listener,
                "personResolverRuntimeService",
                resolverRuntimeService);
        ReflectionTestUtils.setField(
                listener,
                "nextApproverOverrideStore",
                overrideStore);
        ReflectionTestUtils.setField(
                listener, "objectMapper", new ObjectMapper());

        ProcessDefinitionQuery definitionQuery =
                mock(ProcessDefinitionQuery.class);
        ProcessDefinition definition = mock(ProcessDefinition.class);
        when(repositoryService.createProcessDefinitionQuery())
                .thenReturn(definitionQuery);
        when(definitionQuery.processDefinitionId("definition-v1"))
                .thenReturn(definitionQuery);
        when(definitionQuery.singleResult()).thenReturn(definition);
        when(repositoryService.getBpmnModel("definition-v1"))
                .thenReturn(deployedModel);
        when(runtimeService.getVariable(
                "instance-v1",
                NextApproverOverrideStore.VARIABLE_NAME))
                .thenReturn(null);
        when(runtimeService.getVariable(
                "instance-v1", "reviewers"))
                .thenReturn(null);
        when(runtimeService.getVariables("instance-v1"))
                .thenReturn(Map.of());

        FlowableActivityEvent event = mock(FlowableActivityEvent.class);
        when(event.getType())
                .thenReturn(FlowableEngineEventType.ACTIVITY_STARTED);
        when(event.getProcessInstanceId()).thenReturn("instance-v1");
        when(event.getProcessDefinitionId()).thenReturn("definition-v1");
        when(event.getActivityId()).thenReturn("joint-review");
        return new RuntimeFixture(
                listener,
                runtimeService,
                resolverRuntimeService,
                overrideStore,
                event);
    }

    private BpmnModel modelWithResolver(String visibilityKey) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("collectionSource", "resolver");
        config.put("collectionResolverCode", "miResolver");
        if (visibilityKey != null) {
            config.put(
                    "nextApproverSelection",
                    Map.of("version", 1, visibilityKey, true,
                            "editable", true));
        }
        return modelWithConfigDocument(
                new ObjectMapper().valueToTree(config).toString());
    }

    private BpmnModel modelWithResolverSelection(String editableKey) {
        Map<String, Object> selection = new LinkedHashMap<>();
        selection.put("version", 1);
        selection.put("visible", true);
        selection.put(editableKey, true);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("collectionSource", "resolver");
        config.put("collectionResolverCode", "miResolver");
        config.put("nextApproverSelection", selection);
        return modelWithConfigDocument(
                new ObjectMapper().valueToTree(config).toString());
    }

    private BpmnModel modelWithVersionTwoHiddenResolver() {
        return modelWithConfigDocument("""
                {"assignmentConfigVersion":2,
                 "assigneeType":"resolver",
                 "resolverCode":"miResolver",
                 "nextApproverSelection":{"version":1,
                 "visible":false,"editable":false,
                 "source":{"type":"NODE_ASSIGNMENT"}}}
                """);
    }

    private BpmnModel modelWithConfigDocument(String configDocument) {
        return modelWithConfigDocuments(configDocument, null);
    }

    private BpmnModel modelWithConfigDocuments(
            String assigneeConfig,
            String multiInstanceConfig) {
        UserTask task = new UserTask();
        task.setId("joint-review");
        task.setName("联合审批");
        MultiInstanceLoopCharacteristics loop =
                new MultiInstanceLoopCharacteristics();
        loop.setInputDataItem("${reviewers}");
        task.setLoopCharacteristics(loop);
        ExtensionElement properties = extension("properties");
        ExtensionElement property = extension("property");
        property.addAttribute(new ExtensionAttribute(
                "name", "assigneeConfig"));
        property.addAttribute(new ExtensionAttribute(
                "value", assigneeConfig));
        properties.addChildElement(property);
        if (multiInstanceConfig != null) {
            ExtensionElement multiProperty = extension("property");
            multiProperty.addAttribute(new ExtensionAttribute(
                    "name", "multiInstanceConfig"));
            multiProperty.addAttribute(new ExtensionAttribute(
                    "value", multiInstanceConfig));
            properties.addChildElement(multiProperty);
        }
        task.addExtensionElement(properties);
        org.flowable.bpmn.model.Process process =
                new org.flowable.bpmn.model.Process();
        process.setId("expense_flow");
        process.addFlowElement(task);
        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        return model;
    }

    private UserTask configuredTask(
            String id,
            String configDocument) {
        UserTask task = new UserTask();
        task.setId(id);
        task.setName(id);
        ExtensionElement properties = extension("properties");
        ExtensionElement property = extension("property");
        property.addAttribute(new ExtensionAttribute(
                "name", "assigneeConfig"));
        property.addAttribute(new ExtensionAttribute(
                "value", configDocument));
        properties.addChildElement(property);
        task.addExtensionElement(properties);
        return task;
    }

    private record RuntimeFixture(
            MultiInstanceCollectionListener listener,
            RuntimeService runtimeService,
            PersonResolverRuntimeService resolverRuntimeService,
            NextApproverOverrideStore overrideStore,
            FlowableActivityEvent event) {
    }

    private SysUser user(
            String id,
            String username,
            boolean enabled) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setStatus(enabled
                ? SysUser.Status.ENABLED.getValue()
                : SysUser.Status.DISABLED.getValue());
        user.setDeleted(0);
        return user;
    }

    private BpmnModel modelWithUsers(String... usernames) {
        UserTask task = new UserTask();
        task.setId("joint-review");
        task.setName("联合审批");
        MultiInstanceLoopCharacteristics loop =
                new MultiInstanceLoopCharacteristics();
        loop.setInputDataItem("${reviewers}");
        task.setLoopCharacteristics(loop);
        ExtensionElement properties = extension("properties");
        ExtensionElement property = extension("property");
        property.addAttribute(new ExtensionAttribute(
                "name", "assigneeConfig"));
        property.addAttribute(new ExtensionAttribute(
                "value",
                "{\"multiInstanceUsernames\":"
                        + new ObjectMapper().valueToTree(usernames)
                        + "}"));
        properties.addChildElement(property);
        task.addExtensionElement(properties);
        org.flowable.bpmn.model.Process process =
                new org.flowable.bpmn.model.Process();
        process.setId("expense_flow");
        process.addFlowElement(task);
        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        return model;
    }

    private ExtensionElement extension(String name) {
        ExtensionElement element = new ExtensionElement();
        element.setName(name);
        element.setNamespace("http://flowable.org/bpmn");
        element.setNamespacePrefix("flowable");
        return element;
    }

    private SysGroup group(
            String id,
            String code,
            boolean enabled) {
        SysGroup group = new SysGroup();
        group.setId(id);
        group.setGroupCode(code);
        group.setStatus(enabled
                ? SysGroup.Status.ENABLED.getValue()
                : SysGroup.Status.DISABLED.getValue());
        group.setDeleted(0);
        return group;
    }

    private SysRole role(
            String id,
            String code,
            boolean enabled) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleCode(code);
        role.setStatus(enabled
                ? SysRole.Status.ENABLED.getValue()
                : SysRole.Status.DISABLED.getValue());
        role.setDeleted(0);
        return role;
    }
}
