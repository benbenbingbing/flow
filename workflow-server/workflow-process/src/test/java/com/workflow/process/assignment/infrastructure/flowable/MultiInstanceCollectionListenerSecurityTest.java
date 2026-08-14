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
    void deployedConfigKeepsOnlyEnabledUsersFromEverySource() {
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
    void malformedLegacyConfigWithoutSelectionRemainsCompatible() {
        RuntimeFixture fixture = runtimeFixture(modelWithConfigDocument(
                "{\"collectionSource\":\"resolver\""));

        assertDoesNotThrow(
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

    private BpmnModel modelWithConfigDocument(String configDocument) {
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
                "value", configDocument));
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
