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

/**
 * 数据权限引擎测试。
 *
 * <p>被测对象：{@link DataPermissionEngine}，覆盖缺失发布快照 fail-closed、绝对团队访问、
 * 继承模式下实体允许与当前列表拒绝的交集、收窄模式下实体与列表允许的交集、
 * 覆盖模式下仅用列表允许、其他列表的拒绝不影响当前列表等场景。
 */
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
    /** 被测权限引擎 */
    private DataPermissionEngine engine;

    /** 装配权限引擎及其各 Mock 依赖，预置实体状态与团队权限默认值 */
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

    /** 测试缺失发布快照时 fail-closed：验证无权限且 SQL 为 1=0 */
    @Test
    void missingPublishedSnapshotFailsClosed() {
        when(scopeService.getActiveSnapshot("expense")).thenReturn(null);

        var result = engine.calculatePermission("expense", "default", user());

        assertFalse(result.isHasPermission());
        assertEquals("1=0", result.getSqlCondition());
        assertTrue(result.getExplanation().contains("没有已发布"));
    }

    /** 测试绝对团队访问在缺失常规允许时仍生效：验证有权限且 SQL 含 team 表 */
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

    /** 测试继承模式使用实体允许与当前列表拒绝：验证 SQL 含 create_by 与 NOT SECRET 否定 */
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

    /** 测试收窄模式取实体与列表允许的交集：验证 SQL 含 create_by 与 OPEN 条件 */
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

    /** 测试覆盖模式仅用列表允许：验证 SQL 仅含 status = 'OPEN' 且模式为 OVERRIDE */
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

    /** 测试对其他列表的拒绝不影响当前列表：验证当前列表仍有权限且无需过滤 */
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

    /** 构造指定模式与策略的列表作用域快照 */
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

    /** 构造带 id 与过滤配置的策略对象 */
    private EntityListScopePolicyDTO policy(String id, FilterConfigDTO filter) {
        EntityListScopePolicyDTO policy = new EntityListScopePolicyDTO();
        policy.setId(id);
        policy.setPolicyKey(id);
        policy.setPolicyName(id);
        policy.setEnabled(1);
        policy.setFilterConfig(filter);
        return policy;
    }

    /** 构造匹配全部用户、指定列表与效果（允许/拒绝）的绑定 */
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

    /** 构造测试用户（id=u1） */
    private SysUser user() {
        SysUser user = new SysUser();
        user.setId("u1");
        user.setUsername("alice");
        user.setDeptId("dept-1");
        return user;
    }

    /** 构造指定状态码的实体状态对象 */
    private EntityStatus status(String code) {
        EntityStatus status = new EntityStatus();
        status.setStatusCode(code);
        return status;
    }

    /** 构造带类型与根节点的过滤配置 */
    private FilterConfigDTO filter(
            String type,
            EntityActionRuleDTO.RuleNode root) {
        FilterConfigDTO filter = new FilterConfigDTO();
        filter.setType(type);
        filter.setRoot(root);
        return filter;
    }

    /** 构造字段/状态比较条件节点 */
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
