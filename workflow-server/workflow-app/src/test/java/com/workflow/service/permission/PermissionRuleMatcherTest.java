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

/**
 * 权限规则匹配器测试。
 *
 * <p>被测对象：{@link PermissionRuleMatcher}，覆盖角色、用户组、子部门路径、组织子树匹配、
 * 嵌套条件组求值、自定义作用域提供者委托、移除的表达式作用域拒绝等场景。
 */
class PermissionRuleMatcherTest {

    private final SysOrganizationMapper orgMapper = mock(SysOrganizationMapper.class);
    private final SysUserGroupMapper userGroupMapper = mock(SysUserGroupMapper.class);

    /** 测试匹配任意角色：验证用户含目标角色时匹配为真 */
    @Test
    void matchesAnyRole() {
        PermissionRuleMatcher matcher = matcher();
        SysUser user = new SysUser();
        user.setRoleIds(List.of("role-a", "role-b"));
        MatchConfigDTO match = match("OR", condition("ROLE", List.of("role-b"), "ANY", false));

        assertTrue(matcher.matches(match, user));
    }

    /** 测试匹配用户组：验证用户属于目标用户组时匹配为真 */
    @Test
    void matchesUserGroup() {
        PermissionRuleMatcher matcher = matcher();
        SysUser user = new SysUser();
        user.setId("u1");
        when(userGroupMapper.selectGroupIdsByUserId("u1")).thenReturn(List.of("group-a", "group-b"));
        MatchConfigDTO match = match("OR", condition("GROUP", List.of("group-b"), "ANY", false));

        assertTrue(matcher.matches(match, user));
    }

    /** 测试按路径匹配子部门：验证用户部门路径包含目标父部门时匹配为真 */
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

    /** 测试匹配组织子树：验证用户组织路径包含目标父组织时匹配为真 */
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

    /** 测试求值嵌套条件组：验证 AND/OR 嵌套结构按预期求值为真 */
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

    /** 测试自定义作用域委托给提供者：验证自定义类型经提供者匹配为真 */
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

    /** 测试拒绝已移除的表达式作用域：验证 EXPRESSION 类型匹配为假 */
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

    /** 构造无扩展提供者的匹配器 */
    private PermissionRuleMatcher matcher() {
        return new PermissionRuleMatcher(orgMapper, userGroupMapper, List.of());
    }

    /** 构造指定逻辑与条件列表的匹配配置 */
    private MatchConfigDTO match(
            String logic,
            MatchConfigDTO.MatchConditionDTO... conditions) {
        MatchConfigDTO match = new MatchConfigDTO();
        match.setLogic(logic);
        match.setConditions(List.of(conditions));
        return match;
    }

    /** 构造匹配条件（作用域类型、目标 ID、操作符、是否含子部门） */
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

    /** 构造逻辑分组节点（AND/OR），含子节点 */
    private MatchConfigDTO.MatchNodeDTO group(
            String logic,
            MatchConfigDTO.MatchNodeDTO... children) {
        MatchConfigDTO.MatchNodeDTO node = new MatchConfigDTO.MatchNodeDTO();
        node.setType("GROUP");
        node.setLogic(logic);
        node.setChildren(List.of(children));
        return node;
    }

    /** 构造包装单个条件的叶子节点 */
    private MatchConfigDTO.MatchNodeDTO leaf(MatchConfigDTO.MatchConditionDTO condition) {
        MatchConfigDTO.MatchNodeDTO node = new MatchConfigDTO.MatchNodeDTO();
        node.setType("CONDITION");
        node.setCondition(condition);
        return node;
    }
}
