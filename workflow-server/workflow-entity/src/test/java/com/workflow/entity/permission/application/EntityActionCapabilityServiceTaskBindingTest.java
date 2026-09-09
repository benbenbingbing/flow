package com.workflow.entity.permission.application;

import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMenuMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.permission.api.response.EntityActionCapabilityDTO;
import com.workflow.entity.permission.api.response.EntityActionRuleDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证列表审批能力与当前认证用户自己的待办任务绑定，避免会签兄弟任务串用。
 */
class EntityActionCapabilityServiceTaskBindingTest {

    private static final String ENTITY_CODE = "ZDWREQ";
    private static final String APPROVE_PERMISSION = "entity:ZDWREQ:approve";

    private final EntityListActionConfigService actionConfigService =
            mock(EntityListActionConfigService.class);
    private final EntityStatusMapper statusMapper =
            mock(EntityStatusMapper.class);
    private final SysUserService userService = mock(SysUserService.class);
    private final CurrentProcessTaskAssigneeLookup assigneeLookup =
            mock(CurrentProcessTaskAssigneeLookup.class);
    private final SysMenuMapper menuMapper = mock(SysMenuMapper.class);
    private final EntityListConfig listConfig = new EntityListConfig();
    private final Map<String, Object> approveButton = Map.of(
            "key", "approve",
            "enabled", true);

    private EntityActionCapabilityService service;
    private SysUser currentUser;

    @BeforeEach
    void setUp() {
        new PermissionUtil(
                mock(SysUserRoleMapper.class),
                mock(SysRoleMenuMapper.class),
                menuMapper).init();
        UserContext.setCurrentUser("user-lisi", "lisi");
        when(menuMapper.selectPermsByUserId("user-lisi"))
                .thenReturn(Set.of(APPROVE_PERMISSION));

        currentUser = new SysUser();
        currentUser.setId("user-lisi");
        currentUser.setUsername("lisi");
        when(userService.getById("user-lisi")).thenReturn(currentUser);
        when(statusMapper.findByEntityCode(ENTITY_CODE)).thenReturn(List.of());
        when(actionConfigService.resolveRowButtons(listConfig, ENTITY_CODE))
                .thenReturn(List.of(approveButton));
        when(actionConfigService.resolveToolbarButtons(listConfig, ENTITY_CODE))
                .thenReturn(List.of());
        when(actionConfigService.permissionFor(ENTITY_CODE, approveButton))
                .thenReturn(APPROVE_PERMISSION);
        when(actionConfigService.readRule(approveButton))
                .thenReturn(assigneeRule());

        EntityActionRuleEvaluator ruleEvaluator =
                new EntityActionRuleEvaluator(List.of(), assigneeLookup);
        service = new EntityActionCapabilityService(
                actionConfigService,
                ruleEvaluator,
                statusMapper,
                userService,
                assigneeLookup);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void bindsApproveCapabilityToCurrentUsersSiblingTask() {
        EntityDataDTO row = multiInstanceRow();
        when(assigneeLookup.isCurrentAssignee(row, currentUser))
                .thenReturn(true);
        when(assigneeLookup.findActionableTaskId(row, currentUser))
                .thenReturn(Optional.of("task-lisi"));

        service.enrichRows(ENTITY_CODE, listConfig, List.of(row));

        EntityActionCapabilityDTO approve =
                row.getActionCapabilities().get("approve");
        assertTrue(approve.isVisible());
        assertTrue(approve.isEnabled());
        assertEquals("task-lisi", approve.getActionableTaskId());
        assertEquals(
                "task-other-assignee",
                row.getCurrentTaskId(),
                "实体流程摘要的 currentTaskId 语义不得被当前用户能力覆盖");
    }

    @Test
    void hidesApproveAndReturnsNoTaskIdWhenCurrentUserHasNoTodo() {
        EntityDataDTO row = multiInstanceRow();
        when(assigneeLookup.isCurrentAssignee(row, currentUser))
                .thenReturn(false);
        when(assigneeLookup.findActionableTaskId(row, currentUser))
                .thenReturn(Optional.empty());

        service.enrichRows(ENTITY_CODE, listConfig, List.of(row));

        EntityActionCapabilityDTO approve =
                row.getActionCapabilities().get("approve");
        assertFalse(approve.isVisible());
        assertFalse(approve.isEnabled());
        assertNull(approve.getActionableTaskId());
    }

    @Test
    void staleMatchingAssigneeSummaryCannotExposeApproveWithoutLiveTodo() {
        EntityDataDTO row = multiInstanceRow();
        row.setCurrentTaskAssignee(currentUser.getId());
        when(assigneeLookup.findActionableTaskId(row, currentUser))
                .thenReturn(Optional.empty());

        service.enrichRows(ENTITY_CODE, listConfig, List.of(row));

        EntityActionCapabilityDTO approve =
                row.getActionCapabilities().get("approve");
        assertFalse(approve.isVisible());
        assertFalse(approve.isEnabled());
        assertNull(approve.getActionableTaskId());
        assertEquals(
                "当前用户没有可办理的审批任务",
                approve.getReason());
    }

    @Test
    void candidateMayApproveWithoutGainingEditDeleteOrTransferAssigneePermissions() {
        EntityDataDTO row = multiInstanceRow();
        when(assigneeLookup.findActionableTaskId(row, currentUser))
                .thenReturn(Optional.of("candidate-task"));
        when(menuMapper.selectPermsByUserId("user-lisi"))
                .thenReturn(Set.of(APPROVE_PERMISSION, "entity:ZDWREQ:edit",
                        "entity:ZDWREQ:delete", "entity:ZDWREQ:transfer"));
        List<Map<String, Object>> buttons = new java.util.ArrayList<>();
        buttons.add(approveButton);
        for (String key : List.of("edit", "delete", "transfer")) {
            Map<String, Object> button = Map.of("key", key, "enabled", true);
            buttons.add(button);
            when(actionConfigService.permissionFor(ENTITY_CODE, button))
                    .thenReturn("entity:ZDWREQ:" + key);
            when(actionConfigService.readRule(button)).thenReturn(assigneeRule());
        }
        when(actionConfigService.resolveRowButtons(listConfig, ENTITY_CODE)).thenReturn(buttons);

        service.enrichRows(ENTITY_CODE, listConfig, List.of(row));

        assertTrue(row.getActionCapabilities().get("approve").isEnabled());
        assertEquals("candidate-task", row.getActionCapabilities().get("approve").getActionableTaskId());
        for (String key : List.of("edit", "delete", "transfer")) {
            assertFalse(row.getActionCapabilities().get(key).isEnabled(), key + "不能继承候选审批权");
            assertNull(row.getActionCapabilities().get(key).getActionableTaskId());
        }
    }

    @Test
    void candidateApprovalStillRequiresConfiguredStatusAndFieldConditions() {
        EntityDataDTO row = multiInstanceRow();
        when(assigneeLookup.findActionableTaskId(row, currentUser))
                .thenReturn(Optional.of("candidate-task"));
        EntityActionRuleDTO rule = assigneeRule();
        EntityActionRuleDTO.RuleNode status = new EntityActionRuleDTO.RuleNode();
        status.setType("STATUS_CODE");
        status.setOperator("EQ");
        status.setValue("PENDING");
        EntityActionRuleDTO.RuleNode field = new EntityActionRuleDTO.RuleNode();
        field.setType("FIELD");
        field.setField("name");
        field.setOperator("EQ");
        field.setValue("ready");
        EntityActionRuleDTO.RuleNode all = new EntityActionRuleDTO.RuleNode();
        all.setType("GROUP");
        all.setLogic("AND");
        all.setChildren(List.of(rule.getRoot(), status, field));
        rule.setRoot(all);
        when(actionConfigService.readRule(approveButton)).thenReturn(rule);

        row.setStatus("DRAFT");
        row.setName("ready");
        service.enrichRows(ENTITY_CODE, listConfig, List.of(row));
        assertFalse(row.getActionCapabilities().get("approve").isEnabled());

        row.setStatus("PENDING");
        row.setName("not-ready");
        service.enrichRows(ENTITY_CODE, listConfig, List.of(row));
        assertFalse(row.getActionCapabilities().get("approve").isEnabled());

        row.setName("ready");
        service.enrichRows(ENTITY_CODE, listConfig, List.of(row));
        assertTrue(row.getActionCapabilities().get("approve").isEnabled());
    }

    @Test
    void candidateIdentityCannotReplaceApprovePermission() {
        when(menuMapper.selectPermsByUserId("user-lisi")).thenReturn(Set.of());
        EntityDataDTO row = multiInstanceRow();

        service.enrichRows(ENTITY_CODE, listConfig, List.of(row));

        assertFalse(row.getActionCapabilities().get("approve").isVisible());
        assertNull(row.getActionCapabilities().get("approve").getActionableTaskId());
        verifyNoInteractions(assigneeLookup);
    }

    /** 构造实体摘要指向其他会签人的兄弟任务。 */
    private EntityDataDTO multiInstanceRow() {
        EntityDataDTO row = new EntityDataDTO();
        row.setId("record-1");
        row.setEntityCode(ENTITY_CODE);
        row.setProcessInstanceId("process-1");
        row.setCurrentTaskId("task-other-assignee");
        row.setCurrentTaskAssignee("other-user");
        return row;
    }

    /** 默认审批规则要求当前用户持有该记录的未完成待办。 */
    private EntityActionRuleDTO assigneeRule() {
        EntityActionRuleDTO rule = new EntityActionRuleDTO();
        rule.setMessage("仅当前任务办理人可以审批");
        EntityActionRuleDTO.RuleNode relation =
                new EntityActionRuleDTO.RuleNode();
        relation.setType("RELATION");
        relation.setRelation("CURRENT_USER_IS_ASSIGNEE");
        rule.setRoot(relation);
        return rule;
    }
}
