package com.workflow.entity.form.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMenuMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.process.port.ProcessCatalogPort;
import com.workflow.contracts.process.port.ProcessRecordReadAccessPort;
import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import com.workflow.contracts.entity.ui.model.UiRuntimePurpose;
import com.workflow.contracts.process.port.ProcessTaskAccessPort;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.api.response.FormActionRuntimeDTO;
import com.workflow.entity.form.api.request.FormActionResolveRequest;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormNodeMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.permission.api.response.EntityActionCapabilityDTO;
import com.workflow.contracts.entity.permission.model.EntityActionRule;
import com.workflow.entity.permission.application.CurrentProcessTaskAssigneeLookup;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityActionRuleEvaluator;
import com.workflow.entity.permission.application.EntityListActionConfigService;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 真实表单运行时、能力服务和规则执行器联合验证候选人的提交审批按钮。
 * 仅流程访问端口与持久化目录使用替身，不能通过 mock 按钮能力掩盖 assigned-only 求值。
 */
class EntityFormCandidateApprovalActionTest {

    private static final String ENTITY_CODE = "work_order";
    private static final String APPROVE_PERMISSION = "entity:work_order:approve";
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ProcessTaskAccessPort taskAccessPort = mock(ProcessTaskAccessPort.class);
    private final SysMenuMapper menuMapper = mock(SysMenuMapper.class);
    private final EntityFormMapper formMapper = mock(EntityFormMapper.class);
    private final EntityDefinitionMapper definitionMapper = mock(EntityDefinitionMapper.class);
    private final EntityDataDynamicService dataService = mock(EntityDataDynamicService.class);
    private final UiConfigReleaseService releaseService = mock(UiConfigReleaseService.class);
    private final ProcessRecordReadAccessPort processReadAccess = mock(ProcessRecordReadAccessPort.class);
    private EntityActionCapabilityService capabilityService;
    private EntityFormActionService formActionService;
    private EntityDefinition definition;
    private EntityForm form;
    private EntityDataDTO row;

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUser("alice-id", "alice");
        new PermissionUtil(mock(SysUserRoleMapper.class), mock(SysRoleMenuMapper.class), menuMapper).init();
        when(menuMapper.selectPermsByUserId("alice-id"))
                .thenReturn(Set.of(APPROVE_PERMISSION, "entity:work_order:update"));
        SysUserService userService = mock(SysUserService.class);
        SysUser user = new SysUser();
        user.setId("alice-id");
        user.setUsername("alice");
        when(userService.getById("alice-id")).thenReturn(user);
        CurrentProcessTaskAssigneeLookup lookup = new CurrentProcessTaskAssigneeLookup(taskAccessPort);
        capabilityService = new EntityActionCapabilityService(
                mock(EntityListActionConfigService.class), new EntityActionRuleEvaluator(List.of(), lookup),
                mock(EntityStatusMapper.class), userService, lookup);
        formActionService = new EntityFormActionService(
                formMapper, mock(EntityFormNodeMapper.class), definitionMapper,
                dataService, capabilityService, new EntityFormActionConfigPolicy(),
                releaseService, mock(ProcessCatalogPort.class), processReadAccess,
                new JsonDocumentCodec(objectMapper), objectMapper);
        definition = new EntityDefinition();
        definition.setId("entity-1");
        definition.setEntityCode(ENTITY_CODE);
        definition.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        definition.setLifecycleMode(EntityDefinition.LifecycleMode.WORKFLOW);
        definition.setProcessDefinitionId("definition-1");
        form = new EntityForm();
        form.setId("published-form-1");
        form.setEntityId(definition.getId());
        form.setNodes(List.of());
        row = new EntityDataDTO();
        row.setId("record-1");
        row.setEntityCode(ENTITY_CODE);
        row.setStatus("PENDING");
        row.setName("ready");
        row.setProcessInstanceId("process-1");
        row.setCurrentTaskId("another-task");
        row.setCurrentTaskAssignee("another-user");
        when(taskAccessPort.findActionableTaskId("alice-id", ENTITY_CODE, "record-1", "process-1"))
                .thenReturn(Optional.of("candidate-task"));
    }

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void defaultSubmitApprovalIsEnabledForUnclaimedCandidateAndBindsTheirTask() {
        EntityActionCapabilityDTO capability = capabilityService.evaluateApprovalAction(
                ENTITY_CODE, row, assignedRule(), null);

        assertTrue(capability.isEnabled());
        assertEquals("candidate-task", capability.getActionableTaskId());
        FormActionRuntimeDTO submit = action("approve", "submitApproval");
        assertTrue(submit.isVisible());
        assertTrue(submit.isEnabled());
        assertEquals("another-task", row.getCurrentTaskId(), "审批能力不能改写实体兄弟任务摘要");
        assertEquals("another-user", row.getCurrentTaskAssignee());
    }

    @Test
    void candidateCanUseSubmitApprovalWithoutEntityPermissions() {
        when(menuMapper.selectPermsByUserId("alice-id")).thenReturn(Set.of());

        EntityActionCapabilityDTO capability = capabilityService.evaluateApprovalAction(
                ENTITY_CODE, row, assignedRule(), null);
        FormActionRuntimeDTO submit = action("approve", "submitApproval");

        assertTrue(capability.isVisible());
        assertEquals("candidate-task", capability.getActionableTaskId());
        assertTrue(submit.isVisible());
        assertTrue(submit.isEnabled());
    }

    /** 重现待办可打开、但按钮解析被实体 DataScope 拦截的完整服务调用路径。 */
    @Test
    void assignedUserWithoutEntityPermissionsCanResolveAndSubmitPinnedApproval() {
        FormActionResolveRequest request = pinnedApprovalRequest();

        List<FormActionRuntimeDTO> actions = formActionService.resolve(request);
        formActionService.requireBuiltInMutationAction(request, "submitApproval");

        assertEquals(List.of("close", "submitApproval"),
                actions.stream().map(FormActionRuntimeDTO::getKey).toList());
        assertTrue(actions.stream().allMatch(FormActionRuntimeDTO::isEnabled));
        verify(dataService, never()).findAccessibleById(anyString(), anyString(), any());
    }

    @Test
    void taskPermissionDoesNotGrantOrdinaryViewOrEditAccess() {
        FormActionResolveRequest request = pinnedApprovalRequest();
        for (String mode : List.of("view", "edit")) {
            request.setMode(mode);
            assertThrows(ForbiddenException.class, () -> formActionService.resolve(request));
        }
        verify(dataService, never()).findById(anyString(), anyString());
    }

    /** 已办/知会复用流程读取授权，运行中与已结束实例都只能呈现只读按钮。 */
    @Test
    void processReaderWithoutEntityPermissionsCanResolveViewActions() {
        FormActionResolveRequest request = pinnedApprovalRequest();
        request.setMode("view");
        request.setTaskId(null);
        for (UiRuntimePurpose purpose : List.of(UiRuntimePurpose.HISTORICAL, UiRuntimePurpose.ACTIVE_TASK)) {
            when(releaseService.findProcessReadContext("task-token", form.getId(), "release-1", 1))
                    .thenReturn(Optional.of(new UiRuntimeResolutionContext(purpose, "history-1", "node-1")));

            List<FormActionRuntimeDTO> actions = formActionService.resolve(request);

            assertEquals(List.of("close"), actions.stream().map(FormActionRuntimeDTO::getKey).toList());
            assertTrue(actions.get(0).isEnabled());
        }
        verify(processReadAccess, org.mockito.Mockito.times(2))
                .requireReadAccess(ENTITY_CODE, "record-1", "process-1", "history-1");
        verify(dataService, never()).findAccessibleById(anyString(), anyString(), any());
    }

    @Test
    void signedProcessContextCannotReplaceActualInstanceReadPermission() {
        FormActionResolveRequest request = pinnedApprovalRequest();
        request.setMode("view");
        when(releaseService.findProcessReadContext("task-token", form.getId(), "release-1", 1))
                .thenReturn(Optional.of(UiRuntimeResolutionContext.historical("history-1", "node-1")));
        doThrow(new ForbiddenException("无权访问该流程实例")).when(processReadAccess)
                .requireReadAccess(ENTITY_CODE, "record-1", "process-1", "history-1");

        assertThrows(ForbiddenException.class, () -> formActionService.resolve(request));
        verify(dataService, never()).findAccessibleById(anyString(), anyString(), any());
    }

    @Test
    void taskBoundReadTokenCannotBeReusedForAnotherRecordOrProcess() {
        FormActionResolveRequest request = pinnedApprovalRequest();
        request.setMode("view");
        for (int mismatch : List.of(0, 1, 2)) {
            when(releaseService.findProcessReadContext("task-token", form.getId(), "release-1", 1))
                    .thenReturn(Optional.of(UiRuntimeResolutionContext.activeTask("history-1", "node-1", "task-1",
                            mismatch == 0 ? "other-process" : "process-1",
                            mismatch == 1 ? "other_entity" : ENTITY_CODE,
                            mismatch == 2 ? "other-record" : "record-1")));

            BusinessForbiddenException error = assertThrows(BusinessForbiddenException.class,
                    () -> formActionService.resolve(request));
            assertEquals("PROCESS_FORM_READ_CONTEXT_MISMATCH", error.getErrorCode());
        }
        org.mockito.Mockito.verifyNoInteractions(processReadAccess);
    }

    @Test
    void processReadPermissionDoesNotEnableCustomViewButtons() {
        form.setViewConfig("""
                {"actionBar":{"version":1,"builtInOverrides":{},"customButtons":[
                  {"key":"generate","label":"生成","enabled":true,
                   "modes":["view"],"perm":"entity:work_order:generate"}]}}
                """);
        FormActionResolveRequest request = pinnedApprovalRequest();
        request.setMode("view");
        when(releaseService.findProcessReadContext("task-token", form.getId(), "release-1", 1))
                .thenReturn(Optional.of(UiRuntimeResolutionContext.historical("history-1", "node-1")));

        FormActionRuntimeDTO custom = formActionService.resolve(request).stream()
                .filter(button -> "generate".equals(button.getKey())).findFirst().orElseThrow();

        assertFalse(custom.isVisible());
        assertFalse(custom.isEnabled());
    }

    @Test
    void approvalTaskDoesNotGrantCustomButtonPermission() {
        form.setViewConfig("""
                {"actionBar":{"version":1,"builtInOverrides":{},"customButtons":[
                  {"key":"generate","label":"生成","enabled":true,
                   "modes":["approve"],"perm":"entity:work_order:generate"}]}}
                """);
        FormActionResolveRequest request = pinnedApprovalRequest();
        FormActionRuntimeDTO custom = formActionService.resolve(request).stream()
                .filter(button -> "generate".equals(button.getKey())).findFirst().orElseThrow();

        assertFalse(custom.isVisible());
        assertFalse(custom.isEnabled());
        UiEventExecuteRequest execute = new UiEventExecuteRequest();
        execute.setConfigId(form.getId());
        execute.setEntityCode(ENTITY_CODE);
        execute.setRecordId(row.getId());
        execute.setTaskId(request.getTaskId());
        execute.setReleaseId(request.getReleaseId());
        execute.setReleaseVersion(request.getReleaseVersion());
        execute.setReleaseResolutionToken(request.getReleaseResolutionToken());
        execute.setTargetKey("generate");
        execute.setContext(Map.of("mode", "approve"));
        assertThrows(ForbiddenException.class, () -> formActionService.requireCustomButton(execute));
    }

    @Test
    void transferredOrCompletedTaskCannotResolveApprovalWithAnOldToken() {
        FormActionResolveRequest request = pinnedApprovalRequest();
        when(taskAccessPort.findActionableTaskContext(
                "alice-id", "candidate-task", ENTITY_CODE, "record-1", "process-1"))
                .thenReturn(Optional.empty());

        BusinessForbiddenException error = assertThrows(BusinessForbiddenException.class,
                () -> formActionService.resolve(request));

        assertEquals("UI_EVENT_APPROVAL_TASK_CONTEXT_MISMATCH", error.getErrorCode());
        verify(releaseService, never()).requireActiveTaskReleaseToken(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void approvalTaskMustMatchEveryRequestedCoordinate() {
        FormActionResolveRequest request = pinnedApprovalRequest();
        for (int mismatchedCoordinate : List.of(0, 1, 2, 3)) {
            when(taskAccessPort.findActionableTaskContext(
                    "alice-id", "candidate-task", ENTITY_CODE, "record-1", "process-1"))
                    .thenReturn(Optional.of(new ProcessTaskAccessPort.ActionableTaskContext(
                            mismatchedCoordinate == 0 ? "other-task" : "candidate-task",
                            mismatchedCoordinate == 1 ? "other-process" : "process-1",
                            "definition-1", "history-1", "node-1",
                            mismatchedCoordinate == 2 ? "other_entity" : ENTITY_CODE,
                            mismatchedCoordinate == 3 ? "other-record" : "record-1")));

            BusinessForbiddenException error = assertThrows(BusinessForbiddenException.class,
                    () -> formActionService.resolve(request));
            assertEquals("UI_EVENT_APPROVAL_TASK_CONTEXT_MISMATCH", error.getErrorCode());
        }
        verify(releaseService, never()).requireActiveTaskReleaseToken(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void missingTaskCoordinateCannotReadApprovalData() {
        FormActionResolveRequest request = pinnedApprovalRequest();
        request.setTaskId(null);

        assertThrows(BusinessForbiddenException.class, () -> formActionService.resolve(request));
        verify(dataService, never()).findById(anyString(), anyString());
    }

    /** 使用真实能力服务模拟没有任何实体权限、仅持有当前待办的用户。 */
    private FormActionResolveRequest pinnedApprovalRequest() {
        when(menuMapper.selectPermsByUserId("alice-id")).thenReturn(Set.of());
        when(formMapper.selectById(form.getId())).thenReturn(form);
        when(definitionMapper.selectById(definition.getId())).thenReturn(definition);
        when(dataService.findAccessibleById(ENTITY_CODE, row.getId(), null))
                .thenThrow(new ForbiddenException("数据不存在或无权访问"));
        when(dataService.findById(ENTITY_CODE, row.getId())).thenReturn(row);
        when(releaseService.resolveRuntimeEventSnapshot(form.getId(), "release-1", 1, "task-token"))
                .thenReturn(new UiConfigReleaseService.ResolvedUiEventSnapshot(
                        Map.of("form", objectMapper.convertValue(form, Map.class),
                                "nodes", List.of(), "eventBindings", List.of()),
                        "release-1", 1, "release-1", false, "hash-1"));
        when(taskAccessPort.findActionableTaskContext(
                "alice-id", "candidate-task", ENTITY_CODE, "record-1", "process-1"))
                .thenReturn(Optional.of(new ProcessTaskAccessPort.ActionableTaskContext(
                        "candidate-task", "process-1", "definition-1", "history-1", "node-1",
                        ENTITY_CODE, "record-1")));
        FormActionResolveRequest request = new FormActionResolveRequest();
        request.setFormId(form.getId());
        request.setEntityCode(ENTITY_CODE);
        request.setRecordId(row.getId());
        request.setTaskId("candidate-task");
        request.setMode("approve");
        request.setReleaseId("release-1");
        request.setReleaseVersion(1);
        request.setReleaseResolutionToken("task-token");
        return request;
    }

    @Test
    void staleMatchingEntityAssigneeCannotExposeSubmitWithoutAnActionableTask() {
        row.setCurrentTaskAssignee("alice");
        when(taskAccessPort.findActionableTaskId("alice-id", ENTITY_CODE, "record-1", "process-1"))
                .thenReturn(Optional.empty());

        EntityActionCapabilityDTO capability = capabilityService.evaluateApprovalAction(
                ENTITY_CODE, row, assignedRule(), null);
        FormActionRuntimeDTO submit = action("approve", "submitApproval");

        assertFalse(capability.isVisible());
        assertNull(capability.getActionableTaskId());
        assertFalse(submit.isVisible());
        assertFalse(submit.isEnabled());
        assertEquals("当前用户没有可办理的审批任务", submit.getReason());
    }

    @Test
    void publishedOverrideStillRecognizesCandidateRelationAndRequiresStatusAndField() throws Exception {
        setSubmitOverride(enabledApprovalOverride());

        FormActionRuntimeDTO ready = action("approve", "submitApproval");
        assertTrue(ready.isEnabled());
        assertEquals("确认审批", ready.getLabel());
        row.setStatus("DRAFT");
        FormActionRuntimeDTO wrongStatus = action("approve", "submitApproval");
        assertTrue(wrongStatus.isVisible());
        assertFalse(wrongStatus.isEnabled());
        assertEquals("只允许审批准备完成的待审记录", wrongStatus.getReason());
        row.setStatus("PENDING");
        row.setName("not-ready");
        assertFalse(action("approve", "submitApproval").isEnabled());
        row.setName("ready");
        assertTrue(action("approve", "submitApproval").isEnabled());
    }

    @Test
    void failedEnabledOverrideDisablesAndDoesNotLeakTaskId() throws Exception {
        EntityActionRule override = enabledApprovalOverride();
        setSubmitOverride(override);
        row.setName("not-ready");

        EntityActionCapabilityDTO capability = capabilityService.evaluateApprovalAction(
                ENTITY_CODE, row, assignedRule(), override);
        FormActionRuntimeDTO submit = action("approve", "submitApproval");

        assertFalse(capability.isEnabled());
        assertNull(capability.getActionableTaskId());
        assertTrue(capability.isVisible());
        assertFalse(submit.isEnabled());
        assertTrue(submit.isVisible());
    }

    @Test
    void mandatoryVisibleConditionRunsBeforeOverrideEnabledCondition() throws Exception {
        setSubmitOverride(enabledApprovalOverride());
        row.setProcessEndTime(LocalDateTime.now());
        row.setName("not-ready");

        FormActionRuntimeDTO submit = action("approve", "submitApproval");

        assertFalse(submit.isVisible());
        assertFalse(submit.isEnabled());
        assertEquals("当前数据不满足显示条件", submit.getReason());
    }

    @Test
    void candidateApprovalDoesNotGrantAssignedOnlySaveOrCustomActionPermissions() throws Exception {
        form.setViewConfig(objectMapper.writeValueAsString(Map.of("actionBar", Map.of(
                "version", 1,
                "builtInOverrides", Map.of("save", Map.of("availabilityRule", assignedRule())),
                "customButtons", List.of(Map.of(
                        "key", "customSave", "label", "特殊保存", "enabled", true,
                        "modes", List.of("approve"), "perm", "entity:work_order:update",
                        "availabilityRule", assignedRule()))))));

        assertTrue(action("approve", "submitApproval").isEnabled());
        assertFalse(action("approve", "customSave").isEnabled());
        assertFalse(action("edit", "save").isEnabled());
        verify(taskAccessPort, org.mockito.Mockito.atLeastOnce())
                .isCurrentAssignee("alice-id", ENTITY_CODE, "record-1", "process-1");
    }

    private FormActionRuntimeDTO action(String mode, String key) {
        return formActionService.resolveTrustedPublishedSnapshot(form, definition, mode, row)
                .stream().filter(button -> key.equals(button.getKey())).findFirst().orElseThrow();
    }

    private void setSubmitOverride(EntityActionRule rule) throws Exception {
        form.setViewConfig(objectMapper.writeValueAsString(Map.of("actionBar", Map.of(
                "version", 1,
                "builtInOverrides", Map.of("submitApproval", Map.of(
                        "labelByMode", Map.of("approve", "确认审批"), "availabilityRule", rule)),
                "customButtons", List.of()))));
    }

    private EntityActionRule assignedRule() {
        EntityActionRule rule = new EntityActionRule();
        EntityActionRule.RuleNode relation = new EntityActionRule.RuleNode();
        relation.setType("RELATION");
        relation.setRelation("CURRENT_USER_IS_ASSIGNEE");
        rule.setVisibleWhen(relation);
        return rule;
    }

    /** 启用条件同时限制候选身份、状态和业务字段，防止仅验证候选分支。 */
    private EntityActionRule enabledApprovalOverride() {
        EntityActionRule rule = new EntityActionRule();
        EntityActionRule.RuleNode assignee = new EntityActionRule.RuleNode();
        assignee.setType("RELATION");
        assignee.setRelation("CURRENT_USER_IS_ASSIGNEE");
        EntityActionRule.RuleNode status = new EntityActionRule.RuleNode();
        status.setType("STATUS_CODE");
        status.setOperator("EQ");
        status.setValue("PENDING");
        EntityActionRule.RuleNode field = new EntityActionRule.RuleNode();
        field.setType("FIELD");
        field.setField("name");
        field.setOperator("EQ");
        field.setValue("ready");
        EntityActionRule.RuleNode group = new EntityActionRule.RuleNode();
        group.setType("GROUP");
        group.setLogic("AND");
        group.setChildren(List.of(assignee, status, field));
        rule.setEnabledWhen(group);
        rule.setDisabledMessage("只允许审批准备完成的待审记录");
        return rule;
    }
}
