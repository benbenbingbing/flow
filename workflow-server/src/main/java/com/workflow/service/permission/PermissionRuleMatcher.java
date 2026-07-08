package com.workflow.service.permission;

import com.workflow.dto.permission.MatchConfigDTO;
import com.workflow.entity.SysOrganization;
import com.workflow.entity.SysUser;
import com.workflow.mapper.SysOrganizationMapper;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 数据权限匹配规则。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionRuleMatcher {

    private final SysOrganizationMapper orgMapper;

    /** 表达式中允许的变量名白名单，防止访问任意对象 */
    private static final Set<String> ALLOWED_ROOT_VARS = Set.of("user");

    /** 禁止表达式里出现的危险关键字，避免通过 Groovy 执行系统命令 */
    private static final Pattern FORBIDDEN_PATTERN = Pattern.compile(
            "(?i)(System\\.|Runtime\\.|exec\\(|getClass\\(|invokeMethod|\\\\u|import\\s)");

    public boolean matches(MatchConfigDTO match, SysUser user) {
        if (match == null || match.getConditions() == null || match.getConditions().isEmpty()) {
            return false;
        }

        String logic = match.getLogic();
        if (logic == null) {
            logic = "OR";
        }

        List<Boolean> results = new ArrayList<>();
        for (MatchConfigDTO.MatchConditionDTO condition : match.getConditions()) {
            results.add(matchesCondition(condition, user));
        }

        if ("AND".equalsIgnoreCase(logic)) {
            return results.stream().allMatch(Boolean::booleanValue);
        }
        return results.stream().anyMatch(Boolean::booleanValue);
    }

    private boolean matchesCondition(MatchConfigDTO.MatchConditionDTO condition, SysUser user) {
        String scopeType = condition.getScopeType();
        List<String> targetIds = condition.getTargetIds();

        switch (scopeType) {
            case "ALL_USERS":
                return true;
            case "USER":
                return targetIds != null && targetIds.contains(user.getId());
            case "ROLE":
                return matchesRole(condition, user);
            case "DEPT":
                return matchesDept(condition, user);
            case "EXPRESSION":
                return matchesExpression(condition, user);
            default:
                return false;
        }
    }

    private boolean matchesRole(MatchConfigDTO.MatchConditionDTO condition, SysUser user) {
        List<String> targetIds = condition.getTargetIds();
        if (targetIds == null || targetIds.isEmpty()) {
            return false;
        }

        List<String> userRoleIds = user.getRoleIds();
        if (userRoleIds == null || userRoleIds.isEmpty()) {
            return false;
        }

        String operator = condition.getOperator();
        if (operator == null) {
            operator = "ANY";
        }

        if ("ALL".equalsIgnoreCase(operator)) {
            return userRoleIds.containsAll(targetIds);
        }
        return targetIds.stream().anyMatch(userRoleIds::contains);
    }

    private boolean matchesDept(MatchConfigDTO.MatchConditionDTO condition, SysUser user) {
        List<String> targetIds = condition.getTargetIds();
        if (targetIds == null || targetIds.isEmpty()) {
            return false;
        }

        String userDeptId = user.getDeptId();
        if (userDeptId == null) {
            return false;
        }

        Boolean includeSub = condition.getIncludeSubDept();
        if (includeSub != null && includeSub) {
            SysOrganization userOrg = orgMapper.selectById(userDeptId);
            if (userOrg != null && userOrg.getPath() != null) {
                String path = userOrg.getPath();
                for (String targetId : targetIds) {
                    if (path.contains("/" + targetId + "/")) {
                        return true;
                    }
                }
            }
            return false;
        }
        return targetIds.contains(userDeptId);
    }

    /**
     * 通过 Groovy 表达式判断当前用户是否命中规则。
     * 表达式形如 {@code user.deptId == '123'}，user 上下文与 PermissionVariableResolver 一致。
     * 表达式为空或求值异常视为不匹配。
     */
    private boolean matchesExpression(MatchConfigDTO.MatchConditionDTO condition, SysUser user) {
        String expression = condition.getExpression();
        if (expression == null || expression.isBlank()) {
            return false;
        }
        if (FORBIDDEN_PATTERN.matcher(expression).find()) {
            log.warn("权限匹配表达式包含禁止关键字，已拒绝: {}", expression);
            return false;
        }

        try {
            Map<String, Object> userMap = new HashMap<>();
            if (user != null) {
                userMap.put("id", user.getId());
                userMap.put("username", user.getUsername());
                userMap.put("deptId", user.getDeptId());
                userMap.put("roleIds", user.getRoleIds());
            }
            Binding binding = new Binding(Map.of("user", userMap));
            GroovyShell shell = new GroovyShell(binding);
            Object result = shell.evaluate(expression);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("权限匹配表达式求值失败: expr={}, error={}", expression, e.getMessage());
            return false;
        }
    }
}
