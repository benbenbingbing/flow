package com.workflow.service.permission;

import com.workflow.dto.permission.MatchConfigDTO;
import com.workflow.entity.SysOrganization;
import com.workflow.entity.SysUser;
import com.workflow.mapper.SysOrganizationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据权限匹配规则。
 */
@Component
@RequiredArgsConstructor
public class PermissionRuleMatcher {

    private final SysOrganizationMapper orgMapper;

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
                return false;
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
}
