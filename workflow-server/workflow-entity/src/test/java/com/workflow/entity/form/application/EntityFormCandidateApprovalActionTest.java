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
import com.workflow.contracts.process.port.ProcessTaskAccessPort;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.api.response.FormActionRuntimeDTO;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormNodeMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.permission.api.response.EntityActionCapabilityDTO;
import com.workflow.entity.permission.api.response.EntityActionRuleDTO;
import com.workflow.entity.permission.application.CurrentProcessTaskAssigneeLookup;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityActionRuleEvaluator;
import com.workflow.entity.permission.application.EntityListActionConfigService;
import com.workflow.entity.ui.application.UiConfigReleaseService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
                mock(EntityFormMapper.class), mock(EntityFormNodeMapper.class), mock(EntityDefinitionMapper.class),
                mock(EntityDataDynamicService.class), capabilityService, new EntityFormActionConfigPolicy(),
                mock(UiConfigReleaseService.class), mock(ProcessCatalogPort.class),
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
    void candidateCannotUseSubmitApprovalWithoutStandardApprovePermission() {
        when(menuMapper.selectPermsByUserId("alice-id")).thenReturn(Set.of("entity:work_order:update"));

        EntityActionCapabilityDTO capability = capabilityService.evaluateApprovalAction(
                ENTITY_CODE, row, assignedRule(), null);
        FormActionRuntimeDTO submit = action("approve", "submitApproval");

        assertFalse(capability.isVisible());
        assertNull(capability.getActionableTaskId());
        assertFalse(submit.isVisible());
        assertEquals("缺少权限：" + APPROVE_PERMISSION, submit.getReason());
        verifyNoInteractions(taskAccessPort);
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
        EntityActionRuleDTO override = enabledApprovalOverride();
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

    private void setSubmitOverride(EntityActionRuleDTO rule) throws Exception {
        form.setViewConfig(objectMapper.writeValueAsString(Map.of("actionBar", Map.of(
                "version", 1,
                "builtInOverrides", Map.of("submitApproval", Map.of(
                        "labelByMode", Map.of("approve", "确认审批"), "availabilityRule", rule)),
                "customButtons", List.of()))));
    }

    private EntityActionRuleDTO assignedRule() {
        EntityActionRuleDTO rule = new EntityActionRuleDTO();
        EntityActionRuleDTO.RuleNode relation = new EntityActionRuleDTO.RuleNode();
        relation.setType("RELATION");
        relation.setRelation("CURRENT_USER_IS_ASSIGNEE");
        rule.setVisibleWhen(relation);
        return rule;
    }

    /** 启用条件同时限制候选身份、状态和业务字段，防止仅验证候选分支。 */
    private EntityActionRuleDTO enabledApprovalOverride() {
        EntityActionRuleDTO rule = new EntityActionRuleDTO();
        EntityActionRuleDTO.RuleNode assignee = new EntityActionRuleDTO.RuleNode();
        assignee.setType("RELATION");
        assignee.setRelation("CURRENT_USER_IS_ASSIGNEE");
        EntityActionRuleDTO.RuleNode status = new EntityActionRuleDTO.RuleNode();
        status.setType("STATUS_CODE");
        status.setOperator("EQ");
        status.setValue("PENDING");
        EntityActionRuleDTO.RuleNode field = new EntityActionRuleDTO.RuleNode();
        field.setType("FIELD");
        field.setField("name");
        field.setOperator("EQ");
        field.setValue("ready");
        EntityActionRuleDTO.RuleNode group = new EntityActionRuleDTO.RuleNode();
        group.setType("GROUP");
        group.setLogic("AND");
        group.setChildren(List.of(assignee, status, field));
        rule.setEnabledWhen(group);
        rule.setDisabledMessage("只允许审批准备完成的待审记录");
        return rule;
    }
}
