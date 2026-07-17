package com.workflow.service.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.permission.DataPermissionResult;
import com.workflow.dto.permission.EntityActionRuleDTO;
import com.workflow.dto.permission.FilterConfigDTO;
import com.workflow.dto.permission.MatchConfigDTO;
import com.workflow.entity.EntityListPermission;
import com.workflow.entity.EntityListPermissionDelegate;
import com.workflow.entity.EntityStatus;
import com.workflow.entity.SysUser;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.EntityListPermissionDelegateMapper;
import com.workflow.mapper.EntityListPermissionMapper;
import com.workflow.mapper.EntityStatusMapper;
import com.workflow.mapper.SysOrganizationMapper;
import com.workflow.mapper.SysUserGroupMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataPermissionEngineTest {

    private final EntityListPermissionMapper permissionMapper =
            mock(EntityListPermissionMapper.class);
    private final EntityListPermissionDelegateMapper delegateMapper =
            mock(EntityListPermissionDelegateMapper.class);
    private final EntityDefinitionMapper definitionMapper =
            mock(EntityDefinitionMapper.class);
    private final EntityFieldMapper fieldMapper = mock(EntityFieldMapper.class);
    private final EntityStatusMapper statusMapper = mock(EntityStatusMapper.class);
    private final SysOrganizationMapper organizationMapper =
            mock(SysOrganizationMapper.class);
    private final SysUserGroupMapper userGroupMapper =
            mock(SysUserGroupMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
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
                permissionMapper,
                delegateMapper,
                objectMapper,
                new PermissionRuleMatcher(
                        organizationMapper,
                        userGroupMapper,
                        List.of()),
                sqlBuilder);
    }

    @Test
    void calculatePermissionDefaultsToCurrentUserWhenNoRulesExist() {
        SysUser user = user("u'1", "alice", "dept-1");
        when(permissionMapper.findEnabledByEntityCode("expense")).thenReturn(List.of());
        when(delegateMapper.findActiveByToUserId("u'1", "expense")).thenReturn(List.of());

        DataPermissionResult result = engine.calculatePermission("expense", user);

        assertTrue(result.isHasPermission());
        assertTrue(result.isNeedFilter());
        assertEquals("create_by = 'u''1'", result.getSqlCondition());
    }

    @Test
    void denyRulesAreAppliedAfterAllowRules() throws Exception {
        SysUser user = user("u1", "alice", "dept-1");
        EntityListPermission allowAll = rule(
                "允许全部",
                "ALLOW",
                "UNION",
                100,
                filter("ALL", null));
        EntityListPermission denySecret = rule(
                "排除保密",
                "DENY",
                "INTERSECT",
                90,
                filter("RULE", condition("STATUS_CODE", "IN", List.of("SECRET"))));
        when(permissionMapper.findEnabledByEntityCode("expense"))
                .thenReturn(List.of(allowAll, denySecret));
        when(delegateMapper.findActiveByToUserId("u1", "expense"))
                .thenReturn(List.of());

        DataPermissionResult result = engine.calculatePermission("expense", user);

        assertTrue(result.isHasPermission());
        assertTrue(result.isNeedFilter());
        assertEquals(
                "(1=1) AND NOT (status IN ('SECRET'))",
                result.getSqlCondition());
        assertEquals(List.of("允许全部", "排除保密"), result.getMatchedRuleNames());
    }

    @Test
    void denyAllCannotBeTurnedIntoAllowAll() throws Exception {
        SysUser user = user("u1", "alice", "dept-1");
        EntityListPermission denyAll = rule(
                "禁止访问",
                "DENY",
                "UNION",
                100,
                filter("ALL", null));
        when(permissionMapper.findEnabledByEntityCode("expense"))
                .thenReturn(List.of(denyAll));

        DataPermissionResult result = engine.calculatePermission("expense", user);

        assertFalse(result.isHasPermission());
        assertEquals("1=0", result.getSqlCondition());
    }

    @Test
    void allowRulesSupportUnionAndIntersectionWithoutAffectingDenySemantics() throws Exception {
        SysUser user = user("u1", "alice", "dept-1");
        EntityListPermission department = rule(
                "本部门",
                "ALLOW",
                "UNION",
                100,
                filter("DEPT", null));
        EntityListPermission openStatus = rule(
                "仅开放状态",
                "ALLOW",
                "INTERSECT",
                90,
                filter("RULE", condition("STATUS_CODE", "IN", List.of("OPEN"))));
        when(permissionMapper.findEnabledByEntityCode("expense"))
                .thenReturn(List.of(department, openStatus));
        when(delegateMapper.findActiveByToUserId("u1", "expense"))
                .thenReturn(List.of());

        DataPermissionResult result = engine.calculatePermission("expense", user);

        assertEquals(
                "(dept_id = 'dept-1') AND (status IN ('OPEN'))",
                result.getSqlCondition());
    }

    @Test
    void allowStopProcessingCannotSkipLowerPriorityDenyRule() throws Exception {
        SysUser user = user("u1", "alice", "dept-1");
        EntityListPermission allowAll = rule(
                "高优先级允许",
                "ALLOW",
                "UNION",
                100,
                filter("ALL", null));
        allowAll.setStopProcessing(1);
        EntityListPermission ignoredAllow = rule(
                "应停止的低优先级允许",
                "ALLOW",
                "UNION",
                90,
                filter("RULE", condition("STATUS_CODE", "EQ", "OPEN")));
        EntityListPermission denySecret = rule(
                "必须执行的低优先级拒绝",
                "DENY",
                "UNION",
                80,
                filter("RULE", condition("STATUS_CODE", "EQ", "SECRET")));
        when(permissionMapper.findEnabledByEntityCode("expense"))
                .thenReturn(List.of(allowAll, ignoredAllow, denySecret));
        when(delegateMapper.findActiveByToUserId("u1", "expense"))
                .thenReturn(List.of());

        DataPermissionResult result = engine.calculatePermission("expense", user);

        assertEquals(
                "(1=1) AND NOT (status = 'SECRET')",
                result.getSqlCondition());
        assertEquals(
                List.of("高优先级允许", "必须执行的低优先级拒绝"),
                result.getMatchedRuleNames());
    }

    @Test
    void delegatedCreatorDataStillRespectsDenyRules() throws Exception {
        SysUser user = user("u1", "alice", "dept-1");
        EntityListPermission personal = rule(
                "本人数据",
                "ALLOW",
                "UNION",
                100,
                filter("PERSONAL", null));
        EntityListPermission denySecret = rule(
                "排除保密数据",
                "DENY",
                "UNION",
                90,
                filter("RULE", condition("STATUS_CODE", "EQ", "SECRET")));
        EntityListPermissionDelegate delegate = new EntityListPermissionDelegate();
        delegate.setFromUserId("u2");
        when(permissionMapper.findEnabledByEntityCode("expense"))
                .thenReturn(List.of(personal, denySecret));
        when(delegateMapper.findActiveByToUserId("u1", "expense"))
                .thenReturn(List.of(delegate));

        DataPermissionResult result = engine.calculatePermission("expense", user);

        assertEquals(
                "((create_by IN ('u1','alice')) AND NOT (status = 'SECRET')) "
                        + "OR ((create_by IN ('u2')) AND NOT (status = 'SECRET'))",
                result.getSqlCondition());
    }

    @Test
    void malformedDenyMatchConfigurationFailsClosed() throws Exception {
        SysUser user = user("u1", "alice", "dept-1");
        EntityListPermission allowAll = rule(
                "允许全部",
                "ALLOW",
                "UNION",
                100,
                filter("ALL", null));
        EntityListPermission brokenDeny = rule(
                "损坏拒绝规则",
                "DENY",
                "UNION",
                90,
                filter("ALL", null));
        brokenDeny.setMatchConfig("{broken-json");
        when(permissionMapper.findEnabledByEntityCode("expense"))
                .thenReturn(List.of(allowAll, brokenDeny));

        DataPermissionResult result = engine.calculatePermission("expense", user);

        assertFalse(result.isHasPermission());
        assertEquals("1=0", result.getSqlCondition());
        assertEquals(List.of("损坏拒绝规则"), result.getMatchedRuleNames());
    }

    @Test
    void malformedDenyFilterConfigurationFailsClosed() throws Exception {
        SysUser user = user("u1", "alice", "dept-1");
        EntityListPermission allowAll = rule(
                "允许全部",
                "ALLOW",
                "UNION",
                100,
                filter("ALL", null));
        EntityListPermission brokenDeny = rule(
                "损坏拒绝过滤",
                "DENY",
                "UNION",
                90,
                filter("ALL", null));
        brokenDeny.setFilterConfig("{broken-json");
        when(permissionMapper.findEnabledByEntityCode("expense"))
                .thenReturn(List.of(allowAll, brokenDeny));

        DataPermissionResult result = engine.calculatePermission("expense", user);

        assertFalse(result.isHasPermission());
        assertEquals("1=0", result.getSqlCondition());
    }

    private SysUser user(String id, String username, String deptId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setDeptId(deptId);
        return user;
    }

    private EntityStatus status(String code) {
        EntityStatus status = new EntityStatus();
        status.setStatusCode(code);
        return status;
    }

    private EntityListPermission rule(
            String name,
            String effect,
            String combineMode,
            int priority,
            FilterConfigDTO filter) throws Exception {
        MatchConfigDTO match = new MatchConfigDTO();
        MatchConfigDTO.MatchConditionDTO allUsers = new MatchConfigDTO.MatchConditionDTO();
        allUsers.setScopeType("ALL_USERS");
        match.setConditions(List.of(allUsers));

        EntityListPermission rule = new EntityListPermission();
        rule.setEntityCode("expense");
        rule.setRuleName(name);
        rule.setRuleEffect(effect);
        rule.setCombineMode(combineMode);
        rule.setPriority(priority);
        rule.setMatchConfig(objectMapper.writeValueAsString(match));
        rule.setFilterConfig(objectMapper.writeValueAsString(filter));
        return rule;
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
