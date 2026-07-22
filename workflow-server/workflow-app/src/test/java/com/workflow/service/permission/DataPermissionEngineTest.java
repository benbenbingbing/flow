package com.workflow.service.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.permission.*;
import com.workflow.entity.EntityStatus;
import com.workflow.entity.SysUser;
import com.workflow.mapper.*;
import com.workflow.service.SysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataPermissionEngineTest {

    private final EntityListScopeService scopeService =
            mock(EntityListScopeService.class);
    private final EntityListScopeDelegationMapper delegationMapper =
            mock(EntityListScopeDelegationMapper.class);
    private final EntityDefinitionMapper definitionMapper =
            mock(EntityDefinitionMapper.class);
    private final EntityFieldMapper fieldMapper = mock(EntityFieldMapper.class);
    private final EntityStatusMapper statusMapper = mock(EntityStatusMapper.class);
    private final SysOrganizationMapper organizationMapper =
            mock(SysOrganizationMapper.class);
    private final SysUserGroupMapper userGroupMapper =
            mock(SysUserGroupMapper.class);
    private final SysUserService userService = mock(SysUserService.class);
    private final EntityListScopeAuditService auditService =
            mock(EntityListScopeAuditService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final com.workflow.service.EntityRecordTeamService entityRecordTeamService =
            mock(com.workflow.service.EntityRecordTeamService.class);
    private DataPermissionEngine engine;

    @BeforeEach
    void setUp() {
        when(definitionMapper.findByEntityCode("expense")).thenReturn(Optional.empty());
        when(statusMapper.findByEntityAndCode("expense", "OPEN"))
                .thenReturn(status("OPEN"));
        when(statusMapper.findByEntityAndCode("expense", "SECRET"))
                .thenReturn(status("SECRET"));
        PermissionSqlBuilder sqlBuilder = new PermissionSqlBuilder(
                definitionMapper,
                fieldMapper,
                statusMapper,
                List.of());
        engine = new DataPermissionEngine(
                scopeService,
                delegationMapper,
                objectMapper,
                new PermissionRuleMatcher(
                        organizationMapper,
                        userGroupMapper,
                        List.of()),
                sqlBuilder,
                userService,
                auditService,
                entityRecordTeamService);
        when(entityRecordTeamService.teamPermission(anyString(), anyString()))
                .thenReturn(com.workflow.service.EntityRecordTeamService.TeamPermission.disabled());
    }

    @Test
    void missingPublishedSnapshotFailsClosed() {
        when(scopeService.getActiveSnapshot("expense")).thenReturn(null);

        var result = engine.calculatePermission("expense", "default", user());

        assertFalse(result.isHasPermission());
        assertEquals("1=0", result.getSqlCondition());
        assertTrue(result.getExplanation().contains("没有已发布"));
    }

    @Test
    void absoluteTeamAccessSurvivesMissingNormalAllow() {
        EntityListScopeSnapshotDTO snapshot = snapshot("INHERIT");
        snapshot.setBindings(List.of());
        when(scopeService.getActiveSnapshot("expense")).thenReturn(snapshot);
        when(entityRecordTeamService.teamPermission("expense", "u1"))
                .thenReturn(new com.workflow.service.EntityRecordTeamService.TeamPermission(
                        true,
                        com.workflow.entity.EntityDefinition.TeamVisibilityLevel.ABSOLUTE,
                        "EXISTS (SELECT 1 FROM expense_team)"));

        var result = engine.calculatePermission("expense", "default", user());

        assertTrue(result.isHasPermission());
        assertTrue(result.getSqlCondition().contains("expense_team"));
    }

    @Test
    void inheritUsesEntityAllowAndCurrentListDeny() {
        EntityListScopeSnapshotDTO snapshot = snapshot(
                "INHERIT",
                policy("personal", filter("PERSONAL", null)),
                policy("secret", filter("RULE", condition(
                        "STATUS_CODE", "EQ", "SECRET"))));
        snapshot.setBindings(List.of(
                binding("personal", null, "ALLOW"),
                binding("secret", "default", "DENY")));
        when(scopeService.getActiveSnapshot("expense")).thenReturn(snapshot);

        var result = engine.calculatePermission("expense", "default", user());

        assertTrue(result.isHasPermission());
        assertTrue(result.getSqlCondition().contains("create_by"));
        assertTrue(result.getSqlCondition().contains("NOT (status = 'SECRET')"));
        assertEquals("INHERIT", result.getDataScopeMode());
    }

    @Test
    void narrowIntersectsEntityAndListAllow() {
        EntityListScopeSnapshotDTO snapshot = snapshot(
                "NARROW",
                policy("personal", filter("PERSONAL", null)),
                policy("open", filter("RULE", condition(
                        "STATUS_CODE", "EQ", "OPEN"))));
        snapshot.setBindings(List.of(
                binding("personal", null, "ALLOW"),
                binding("open", "default", "ALLOW")));
        when(scopeService.getActiveSnapshot("expense")).thenReturn(snapshot);

        var result = engine.calculatePermission("expense", "default", user());

        assertTrue(result.getSqlCondition().contains("create_by"));
        assertTrue(result.getSqlCondition().contains("AND (status = 'OPEN')"));
    }

    @Test
    void overrideUsesOnlyListAllow() {
        EntityListScopeSnapshotDTO snapshot = snapshot(
                "OVERRIDE",
                policy("personal", filter("PERSONAL", null)),
                policy("open", filter("RULE", condition(
                        "STATUS_CODE", "EQ", "OPEN"))));
        snapshot.setBindings(List.of(
                binding("personal", null, "ALLOW"),
                binding("open", "default", "ALLOW")));
        when(scopeService.getActiveSnapshot("expense")).thenReturn(snapshot);

        var result = engine.calculatePermission("expense", "default", user());

        assertEquals("status = 'OPEN'", result.getSqlCondition());
        assertEquals("OVERRIDE", result.getDataScopeMode());
    }

    @Test
    void denyForAnotherListDoesNotAffectCurrentList() {
        EntityListScopeSnapshotDTO snapshot = snapshot(
                "INHERIT",
                policy("all", filter("ALL", null)),
                policy("secret", filter("ALL", null)));
        snapshot.getListModes().put("other", "INHERIT");
        snapshot.setBindings(List.of(
                binding("all", null, "ALLOW"),
                binding("secret", "other", "DENY")));
        when(scopeService.getActiveSnapshot("expense")).thenReturn(snapshot);

        var result = engine.calculatePermission("expense", "default", user());

        assertTrue(result.isHasPermission());
        assertFalse(result.isNeedFilter());
    }

    private EntityListScopeSnapshotDTO snapshot(
            String mode,
            EntityListScopePolicyDTO... policies) {
        EntityListScopeSnapshotDTO snapshot = new EntityListScopeSnapshotDTO();
        snapshot.setEntityCode("expense");
        snapshot.setVersion(3);
        snapshot.setPolicies(List.of(policies));
        snapshot.setListModes(new java.util.LinkedHashMap<>(Map.of("default", mode)));
        return snapshot;
    }

    private EntityListScopePolicyDTO policy(String id, FilterConfigDTO filter) {
        EntityListScopePolicyDTO policy = new EntityListScopePolicyDTO();
        policy.setId(id);
        policy.setPolicyKey(id);
        policy.setPolicyName(id);
        policy.setEnabled(1);
        policy.setFilterConfig(filter);
        return policy;
    }

    private EntityListScopeBindingDTO binding(
            String policyId,
            String listKey,
            String effect) {
        EntityListScopeBindingDTO binding = new EntityListScopeBindingDTO();
        binding.setPolicyId(policyId);
        binding.setListKey(listKey);
        binding.setRuleEffect(effect);
        binding.setEnabled(1);
        MatchConfigDTO match = new MatchConfigDTO();
        MatchConfigDTO.MatchConditionDTO allUsers =
                new MatchConfigDTO.MatchConditionDTO();
        allUsers.setScopeType("ALL_USERS");
        match.setConditions(List.of(allUsers));
        binding.setMatchConfig(match);
        return binding;
    }

    private SysUser user() {
        SysUser user = new SysUser();
        user.setId("u1");
        user.setUsername("alice");
        user.setDeptId("dept-1");
        return user;
    }

    private EntityStatus status(String code) {
        EntityStatus status = new EntityStatus();
        status.setStatusCode(code);
        return status;
    }

    private FilterConfigDTO filter(
            String type,
            EntityActionRuleDTO.RuleNode root) {
        FilterConfigDTO filter = new FilterConfigDTO();
        filter.setType(type);
        filter.setRoot(root);
        return filter;
    }

    private EntityActionRuleDTO.RuleNode condition(
            String type,
            String operator,
            Object value) {
        EntityActionRuleDTO.RuleNode node = new EntityActionRuleDTO.RuleNode();
        node.setType(type);
        node.setOperator(operator);
        node.setValue(value);
        return node;
    }
}
