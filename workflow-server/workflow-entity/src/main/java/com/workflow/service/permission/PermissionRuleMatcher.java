package com.workflow.service.permission;

import com.workflow.dto.permission.MatchConfigDTO;
import com.workflow.entity.SysOrganization;
import com.workflow.entity.SysUser;
import com.workflow.mapper.SysOrganizationMapper;
import com.workflow.mapper.SysUserGroupMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 数据权限用户匹配器。
 */
@Component
public class PermissionRuleMatcher {

    private final SysOrganizationMapper orgMapper;
    private final SysUserGroupMapper userGroupMapper;
    private final List<EntityDataPermissionMatchProvider> matchProviders;

    public PermissionRuleMatcher(
            SysOrganizationMapper orgMapper,
            SysUserGroupMapper userGroupMapper,
            List<EntityDataPermissionMatchProvider> matchProviders) {
        this.orgMapper = orgMapper;
        this.userGroupMapper = userGroupMapper;
        this.matchProviders = matchProviders == null ? List.of() : matchProviders;
    }

    public boolean matches(MatchConfigDTO match, SysUser user) {
        if (match == null || user == null) {
            return false;
        }
        if (match.getRoot() != null) {
            return matchesNode(match.getRoot(), user);
        }
        List<MatchConfigDTO.MatchConditionDTO> conditions = match.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            return false;
        }
        return matchesConditions(match.getLogic(), conditions, user);
    }

    private boolean matchesNode(MatchConfigDTO.MatchNodeDTO node, SysUser user) {
        if (node == null) {
            return false;
        }
        if ("GROUP".equalsIgnoreCase(node.getType())) {
            List<MatchConfigDTO.MatchNodeDTO> children = node.getChildren();
            if (children == null || children.isEmpty()) {
                return false;
            }
            if ("AND".equalsIgnoreCase(node.getLogic())) {
                return children.stream().allMatch(child -> matchesNode(child, user));
            }
            return children.stream().anyMatch(child -> matchesNode(child, user));
        }
        return matchesCondition(node.getCondition(), user);
    }

    private boolean matchesConditions(
            String logic,
            List<MatchConfigDTO.MatchConditionDTO> conditions,
            SysUser user) {
        if ("AND".equalsIgnoreCase(logic)) {
            return conditions.stream().allMatch(condition -> matchesCondition(condition, user));
        }
        return conditions.stream().anyMatch(condition -> matchesCondition(condition, user));
    }

    private boolean matchesCondition(MatchConfigDTO.MatchConditionDTO condition, SysUser user) {
        if (condition == null || condition.getScopeType() == null) {
            return false;
        }
        String scopeType = condition.getScopeType().toUpperCase(Locale.ROOT);
        return switch (scopeType) {
            case "ALL_USERS" -> true;
            case "USER" -> matchesCollection(condition, List.of(user.getId()));
            case "ROLE" -> matchesCollection(condition, user.getRoleIds());
            case "GROUP" -> matchesCollection(
                    condition,
                    userGroupMapper.selectGroupIdsByUserId(user.getId()));
            case "DEPT" -> matchesOrganization(
                    condition,
                    user.getDeptId());
            case "ORG" -> matchesOrganization(
                    condition,
                    user.getOrgId());
            default -> matchesCustom(condition, user);
        };
    }

    private boolean matchesCollection(
            MatchConfigDTO.MatchConditionDTO condition,
            List<String> currentIds) {
        List<String> targetIds = condition.getTargetIds();
        if (targetIds == null || targetIds.isEmpty()
                || currentIds == null || currentIds.isEmpty()) {
            return false;
        }
        if ("ALL".equalsIgnoreCase(condition.getOperator())) {
            return currentIds.containsAll(targetIds);
        }
        return targetIds.stream().anyMatch(currentIds::contains);
    }

    private boolean matchesOrganization(
            MatchConfigDTO.MatchConditionDTO condition,
            String currentOrganizationId) {
        List<String> targetIds = condition.getTargetIds();
        if (targetIds == null || targetIds.isEmpty()
                || currentOrganizationId == null || currentOrganizationId.isBlank()) {
            return false;
        }
        if (!Boolean.TRUE.equals(condition.getIncludeSubDept())) {
            return targetIds.contains(currentOrganizationId);
        }
        SysOrganization currentOrganization = orgMapper.selectById(currentOrganizationId);
        if (currentOrganization == null || currentOrganization.getPath() == null) {
            return false;
        }
        String path = currentOrganization.getPath();
        return targetIds.stream().anyMatch(targetId ->
                currentOrganizationId.equals(targetId)
                        || path.contains("/" + targetId + "/"));
    }

    private boolean matchesCustom(
            MatchConfigDTO.MatchConditionDTO condition,
            SysUser user) {
        return matchProviders.stream()
                .filter(provider -> provider.getScopeType()
                        .equalsIgnoreCase(condition.getScopeType()))
                .findFirst()
                .map(provider -> provider.matches(condition, user))
                .orElse(false);
    }
}
