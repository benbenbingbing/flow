package com.workflow.entity.permission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.entity.data.application.EntityRecordTeamService;
import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.permission.api.response.EntityListScopePolicyPreviewDTO;
import com.workflow.entity.permission.api.web.EntityListScopeController;
import com.workflow.entity.permission.infrastructure.persistence.mapper.EntityListScopeBindingMapper;
import com.workflow.entity.permission.infrastructure.persistence.mapper.EntityListScopePolicyMapper;
import com.workflow.entity.permission.infrastructure.persistence.mapper.EntityListScopeReleaseMapper;
import com.workflow.entity.permission.infrastructure.persistence.record.EntityListScopePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 用真实条件编译器覆盖行内模拟，防止再次退回列表权限计算得到无关的 1=1。 */
class EntityListScopePolicyPreviewTest {
    private final EntityListScopePolicyMapper policyMapper = mock(EntityListScopePolicyMapper.class);
    private final EntityListScopeBindingMapper bindingMapper = mock(EntityListScopeBindingMapper.class);
    private final EntityListScopeReleaseMapper releaseMapper = mock(EntityListScopeReleaseMapper.class);
    private final EntityListConfigMapper listMapper = mock(EntityListConfigMapper.class);
    private final EntityRecordTeamService teamService = mock(EntityRecordTeamService.class);
    private EntityListScopeService service;
    private SysUser user;

    @BeforeEach
    void setUp() {
        EntityDefinitionMapper definitionMapper = mock(EntityDefinitionMapper.class);
        EntityFieldMapper fieldMapper = mock(EntityFieldMapper.class);
        EntityDefinition entity = new EntityDefinition();
        entity.setId("entity-1");
        entity.setEntityCode("expense");
        when(definitionMapper.findByEntityCode("expense")).thenReturn(Optional.of(entity));
        when(fieldMapper.findByEntityId("entity-1")).thenReturn(List.of());
        PermissionSqlBuilder builder = new PermissionSqlBuilder(
                definitionMapper, fieldMapper, mock(EntityStatusMapper.class), List.of(), teamService);
        service = new EntityListScopeService(
                policyMapper, bindingMapper, releaseMapper, listMapper, definitionMapper,
                builder, new PermissionRuleMatcher(null, null, List.of()), new ObjectMapper(),
                mock(EntityListScopeAuditService.class), mock(EntityDefinitionAccessPolicy.class));
        user = new SysUser();
        user.setId("u1");
        user.setUsername("alice");
    }

    @Test
    void unboundPersonalRuleCompilesItsOwnConditionsEvenForBypassUser() {
        policy("personal", """
                {"type":"RULE","root":{"type":"GROUP","logic":"OR","children":[
                  {"type":"RELATION","relation":"CURRENT_USER_IS_CREATOR"},
                  {"type":"RELATION","relation":"CURRENT_USER_IS_SUBMITTER"}
                ]}}
                """);
        SysMenuMapper permissions = mock(SysMenuMapper.class);
        when(permissions.selectPermsByUserId("u1"))
                .thenReturn(Set.of("entity:expense:scope:bypass"));
        Object originalMapper = ReflectionTestUtils.getField(PermissionUtil.class, "staticMenuMapper");
        ReflectionTestUtils.setField(PermissionUtil.class, "staticMenuMapper", permissions);
        try {

            EntityListScopePolicyPreviewDTO preview = service.previewPolicy("personal", user);

            assertTrue(preview.getSql().contains("create_by IN ('u1','alice')"));
            assertTrue(preview.getSql().contains("submitter_id IN ('u1','alice')"));
            assertTrue(preview.getSql().contains(" OR "));
            assertTrue(preview.isAudienceMatched());
            assertEquals("personal", preview.getPolicyId());
            verifyNoInteractions(permissions);
        } finally {
            // PermissionUtil 持有静态 Mapper，恢复原值避免污染同一 JVM 的权限引擎测试。
            ReflectionTestUtils.setField(PermissionUtil.class, "staticMenuMapper", originalMapper);
        }
        verifyNoInteractions(bindingMapper, releaseMapper, listMapper);
    }

    @Test
    void relatedPeopleRuleUsesSelectedUsersTeamCondition() {
        policy("team", "{\"type\":\"TEAM\"}");
        String teamSql = "EXISTS (SELECT 1 FROM wf_expense_team team "
                + "WHERE team.record_id = wf_expense.id AND team.user_id = 'u2')";
        when(teamService.relatedPeopleSql("expense", "u2", "bob")).thenReturn(teamSql);
        user.setId("u2");
        user.setUsername("bob");

        EntityListScopePolicyPreviewDTO preview = service.previewPolicy("team", user);

        assertEquals(teamSql, preview.getSql());
        assertEquals("u2", preview.getUserId());
        assertEquals("bob", preview.getUsername());
        verifyNoInteractions(bindingMapper, releaseMapper, listMapper);
    }

    @Test
    void disabledNonMatchingDenyRuleStillExplainsItsDataCondition() {
        EntityListScopePolicy policy = policy("disabled", """
                {"type":"PERSONAL","ruleEffect":"DENY","audience":{
                  "conditions":[{"scopeType":"USER","targetIds":["someone-else"]}]
                }}
                """);
        policy.setEnabled(0);

        EntityListScopePolicyPreviewDTO preview = service.previewPolicy("disabled", user);

        assertFalse(preview.isEnabled());
        assertFalse(preview.isAudienceMatched());
        assertEquals("DENY", preview.getRuleEffect());
        assertEquals("create_by IN ('u1','alice')", preview.getSql());
    }

    @Test
    void allRecordsRuleMayLegitimatelyCompileToOneEqualsOne() {
        policy("all", "{\"type\":\"ALL\"}");
        assertEquals("1=1", service.previewPolicy("all", user).getSql());
    }

    @Test
    void missingUserRuleAndMalformedConditionsFailInsteadOfReturningAllowAll() {
        policy("broken", "not-json");
        policy("invalid", "{\"type\":\"RULE\"}");
        policy("deleted", "{\"type\":\"ALL\"}").setDeleted(1);
        assertThrows(IllegalArgumentException.class, () -> service.previewPolicy("missing", user));
        assertThrows(IllegalArgumentException.class, () -> service.previewPolicy("deleted", user));
        assertThrows(IllegalArgumentException.class, () -> service.previewPolicy("invalid", user));
        assertThrows(IllegalArgumentException.class, () -> service.previewPolicy("broken", null));
        assertThrows(IllegalStateException.class, () -> service.previewPolicy("broken", user));
    }

    @Test
    void controllerUsesCurrentUserByDefaultAndSupportsExplicitSimulationUser() {
        EntityListScopeService mockedService = mock(EntityListScopeService.class);
        CurrentUserRoleService roles = mock(CurrentUserRoleService.class);
        SysUserService users = mock(SysUserService.class);
        EntityListScopeController controller = new EntityListScopeController(mockedService, roles, users);
        SysUser selected = new SysUser();
        selected.setId("u2");
        when(users.getById("u1")).thenReturn(user);
        when(users.getById("u2")).thenReturn(selected);
        UserContext.setCurrentUser("u1", "alice");
        try {
            controller.previewPolicy("personal", null);
            controller.previewPolicy("personal", "u2");
            verify(mockedService).previewPolicy("personal", user);
            verify(mockedService).previewPolicy("personal", selected);
            verify(roles, times(2)).requireSuperAdmin();
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void controllerRejectsUnauthorizedSimulationBeforeResolvingUser() {
        CurrentUserRoleService roles = mock(CurrentUserRoleService.class);
        SysUserService users = mock(SysUserService.class);
        EntityListScopeService mockedService = mock(EntityListScopeService.class);
        doThrow(new IllegalStateException("仅超级管理员可模拟")).when(roles).requireSuperAdmin();
        EntityListScopeController controller = new EntityListScopeController(mockedService, roles, users);

        assertThrows(IllegalStateException.class, () -> controller.previewPolicy("personal", "u2"));
        verifyNoInteractions(users, mockedService);
    }

    /** 创建独立的已保存规则；刻意不设置任何列表绑定，覆盖目录规则未绑定的场景。 */
    private EntityListScopePolicy policy(String id, String filter) {
        EntityListScopePolicy policy = new EntityListScopePolicy();
        policy.setId(id);
        policy.setEntityCode("expense");
        policy.setPolicyName(id);
        policy.setEnabled(1);
        policy.setDeleted(0);
        policy.setFilterConfig(filter);
        when(policyMapper.selectById(id)).thenReturn(policy);
        return policy;
    }
}
