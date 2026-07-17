package com.workflow.service.permission;

import com.workflow.dto.permission.MatchConfigDTO;
import com.workflow.entity.SysOrganization;
import com.workflow.entity.SysUser;
import com.workflow.mapper.SysOrganizationMapper;
import com.workflow.mapper.SysUserGroupMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionRuleMatcherTest {

    private final SysOrganizationMapper orgMapper = mock(SysOrganizationMapper.class);
    private final SysUserGroupMapper userGroupMapper = mock(SysUserGroupMapper.class);

    @Test
    void matchesAnyRole() {
        PermissionRuleMatcher matcher = matcher();
        SysUser user = new SysUser();
        user.setRoleIds(List.of("role-a", "role-b"));
        MatchConfigDTO match = match("OR", condition("ROLE", List.of("role-b"), "ANY", false));

        assertTrue(matcher.matches(match, user));
    }

    @Test
    void matchesUserGroup() {
        PermissionRuleMatcher matcher = matcher();
        SysUser user = new SysUser();
        user.setId("u1");
        when(userGroupMapper.selectGroupIdsByUserId("u1")).thenReturn(List.of("group-a", "group-b"));
        MatchConfigDTO match = match("OR", condition("GROUP", List.of("group-b"), "ANY", false));

        assertTrue(matcher.matches(match, user));
    }

    @Test
    void matchesSubDepartmentByPath() {
        PermissionRuleMatcher matcher = matcher();
        SysUser user = new SysUser();
        user.setDeptId("dept-child");
        SysOrganization org = new SysOrganization();
        org.setPath("/root/dept-parent/dept-child/");
        when(orgMapper.selectById("dept-child")).thenReturn(org);
        MatchConfigDTO match = match("OR", condition("DEPT", List.of("dept-parent"), "ANY", true));

        assertTrue(matcher.matches(match, user));
    }

    @Test
    void matchesOrganizationSubtree() {
        PermissionRuleMatcher matcher = matcher();
        SysUser user = new SysUser();
        user.setOrgId("org-child");
        SysOrganization org = new SysOrganization();
        org.setPath("/root/org-parent/org-child/");
        when(orgMapper.selectById("org-child")).thenReturn(org);
        MatchConfigDTO match = match("OR", condition("ORG", List.of("org-parent"), "ANY", true));

        assertTrue(matcher.matches(match, user));
    }

    @Test
    void evaluatesNestedConditionGroups() {
        PermissionRuleMatcher matcher = matcher();
        SysUser user = new SysUser();
        user.setId("u1");
        user.setRoleIds(List.of("role-a"));

        MatchConfigDTO.MatchNodeDTO root = group("AND",
                leaf(condition("USER", List.of("u1"), "ANY", false)),
                group("OR",
                        leaf(condition("ROLE", List.of("role-a"), "ANY", false)),
                        leaf(condition("ROLE", List.of("role-b"), "ANY", false))));
        MatchConfigDTO config = new MatchConfigDTO();
        config.setRoot(root);

        assertTrue(matcher.matches(config, user));
    }

    @Test
    void delegatesCustomScopeToProvider() {
        EntityDataPermissionMatchProvider provider = new EntityDataPermissionMatchProvider() {
            @Override
            public String getScopeType() {
                return "CRM:CUSTOMER_MANAGER";
            }

            @Override
            public boolean matches(MatchConfigDTO.MatchConditionDTO condition, SysUser user) {
                return "u1".equals(user.getId());
            }
        };
        PermissionRuleMatcher matcher = new PermissionRuleMatcher(
                orgMapper,
                userGroupMapper,
                List.of(provider));
        SysUser user = new SysUser();
        user.setId("u1");
        MatchConfigDTO match = match(
                "OR",
                condition("CRM:CUSTOMER_MANAGER", List.of(), "ANY", false));

        assertTrue(matcher.matches(match, user));
    }

    @Test
    void rejectsRemovedExpressionScope() {
        PermissionRuleMatcher matcher = matcher();
        SysUser user = new SysUser();
        user.setId("u1");
        MatchConfigDTO match = match(
                "OR",
                condition("EXPRESSION", List.of(), "ANY", false));

        assertFalse(matcher.matches(match, user));
    }

    private PermissionRuleMatcher matcher() {
        return new PermissionRuleMatcher(orgMapper, userGroupMapper, List.of());
    }

    private MatchConfigDTO match(
            String logic,
            MatchConfigDTO.MatchConditionDTO... conditions) {
        MatchConfigDTO match = new MatchConfigDTO();
        match.setLogic(logic);
        match.setConditions(List.of(conditions));
        return match;
    }

    private MatchConfigDTO.MatchConditionDTO condition(
            String scopeType,
            List<String> targetIds,
            String operator,
            boolean includeSubDept) {
        MatchConfigDTO.MatchConditionDTO condition = new MatchConfigDTO.MatchConditionDTO();
        condition.setScopeType(scopeType);
        condition.setTargetIds(targetIds);
        condition.setOperator(operator);
        condition.setIncludeSubDept(includeSubDept);
        return condition;
    }

    private MatchConfigDTO.MatchNodeDTO group(
            String logic,
            MatchConfigDTO.MatchNodeDTO... children) {
        MatchConfigDTO.MatchNodeDTO node = new MatchConfigDTO.MatchNodeDTO();
        node.setType("GROUP");
        node.setLogic(logic);
        node.setChildren(List.of(children));
        return node;
    }

    private MatchConfigDTO.MatchNodeDTO leaf(MatchConfigDTO.MatchConditionDTO condition) {
        MatchConfigDTO.MatchNodeDTO node = new MatchConfigDTO.MatchNodeDTO();
        node.setType("CONDITION");
        node.setCondition(condition);
        return node;
    }
}
