package com.workflow.service.permission;

import com.workflow.dto.permission.MatchConfigDTO;
import com.workflow.entity.SysOrganization;
import com.workflow.entity.SysUser;
import com.workflow.mapper.SysOrganizationMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionRuleMatcherTest {

    private final SysOrganizationMapper orgMapper = mock(SysOrganizationMapper.class);
    private final PermissionRuleMatcher matcher = new PermissionRuleMatcher(orgMapper);

    @Test
    void matchesAnyRole() {
        SysUser user = new SysUser();
        user.setRoleIds(List.of("role-a", "role-b"));
        MatchConfigDTO match = match("OR", condition("ROLE", List.of("role-b"), "ANY", false));

        assertTrue(matcher.matches(match, user));
    }

    @Test
    void matchesSubDepartmentByPath() {
        SysUser user = new SysUser();
        user.setDeptId("dept-child");
        SysOrganization org = new SysOrganization();
        org.setPath("/root/dept-parent/dept-child/");
        when(orgMapper.selectById("dept-child")).thenReturn(org);
        MatchConfigDTO match = match("OR", condition("DEPT", List.of("dept-parent"), "ANY", true));

        assertTrue(matcher.matches(match, user));
    }

    @Test
    void returnsFalseWhenAllConditionsDoNotMatch() {
        SysUser user = new SysUser();
        user.setId("u1");
        user.setRoleIds(List.of("role-a"));
        MatchConfigDTO match = match("AND",
                condition("USER", List.of("u1"), "ANY", false),
                condition("ROLE", List.of("role-b"), "ANY", false));

        assertFalse(matcher.matches(match, user));
    }

    @Test
    void matchesExpressionWhenGroovyEvaluatesTrue() {
        SysUser user = new SysUser();
        user.setId("u1");
        user.setDeptId("dept-1");
        MatchConfigDTO.MatchConditionDTO cond = new MatchConfigDTO.MatchConditionDTO();
        cond.setScopeType("EXPRESSION");
        cond.setExpression("user.deptId == 'dept-1'");
        MatchConfigDTO match = match("OR", cond);

        assertTrue(matcher.matches(match, user));
    }

    @Test
    void expressionReturnsFalseWhenNotMatched() {
        SysUser user = new SysUser();
        user.setDeptId("dept-1");
        MatchConfigDTO.MatchConditionDTO cond = new MatchConfigDTO.MatchConditionDTO();
        cond.setScopeType("EXPRESSION");
        cond.setExpression("user.deptId == 'dept-other'");
        MatchConfigDTO match = match("OR", cond);

        assertFalse(matcher.matches(match, user));
    }

    @Test
    void expressionRejectsForbiddenKeywords() {
        SysUser user = new SysUser();
        MatchConfigDTO.MatchConditionDTO cond = new MatchConfigDTO.MatchConditionDTO();
        cond.setScopeType("EXPRESSION");
        cond.setExpression("Runtime.getRuntime().exec('ls')");
        MatchConfigDTO match = match("OR", cond);

        assertFalse(matcher.matches(match, user));
    }

    private MatchConfigDTO match(String logic, MatchConfigDTO.MatchConditionDTO... conditions) {
        MatchConfigDTO match = new MatchConfigDTO();
        match.setLogic(logic);
        match.setConditions(List.of(conditions));
        return match;
    }

    private MatchConfigDTO.MatchConditionDTO condition(String scopeType, List<String> targetIds,
                                                       String operator, boolean includeSubDept) {
        MatchConfigDTO.MatchConditionDTO condition = new MatchConfigDTO.MatchConditionDTO();
        condition.setScopeType(scopeType);
        condition.setTargetIds(targetIds);
        condition.setOperator(operator);
        condition.setIncludeSubDept(includeSubDept);
        return condition;
    }
}
