package com.workflow.service.permission;

import com.workflow.dto.permission.MatchConfigDTO;
import com.workflow.entity.SysOrganization;
import com.workflow.entity.SysUser;
import com.workflow.mapper.SysOrganizationMapper;
import com.workflow.mapper.SysUserGroupMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 数据权限用户匹配器。
 *
 * <p>根据 {@link MatchConfigDTO} 中的适用用户条件判断当前用户是否命中。
 * 支持内置范围（全部用户、用户、角色、用户组、部门、组织）以及通过
 * {@link EntityDataPermissionMatchProvider} 扩展的自定义范围。</p>
 */
@Component
public class PermissionRuleMatcher {

    /** 条件树最大嵌套深度，防止配置过于复杂导致递归过深。 */
    private static final int MAX_DEPTH = 6;
    /** 条件树最大节点数，防止配置过于复杂。 */
    private static final int MAX_NODES = 100;
    /** 内置的适用用户范围类型集合。 */
    private static final Set<String> BUILTIN_TYPES =
            Set.of("ALL_USERS", "USER", "ROLE", "GROUP", "DEPT", "ORG");

    private final SysOrganizationMapper orgMapper;
    private final SysUserGroupMapper userGroupMapper;
    private final List<EntityDataPermissionMatchProvider> matchProviders;

    /**
     * 构造匹配器。
     *
     * @param orgMapper       组织架构数据访问
     * @param userGroupMapper 用户组数据访问
     * @param matchProviders  自定义范围匹配扩展点集合，可为 null
     */
    public PermissionRuleMatcher(
            SysOrganizationMapper orgMapper,
            SysUserGroupMapper userGroupMapper,
            List<EntityDataPermissionMatchProvider> matchProviders) {
        this.orgMapper = orgMapper;
        this.userGroupMapper = userGroupMapper;
        this.matchProviders = matchProviders == null ? List.of() : matchProviders;
    }

    /**
     * 判断用户是否命中数据权限适用范围。
     *
     * @param match 适用用户配置，为空返回 false
     * @param user  当前用户，为空返回 false
     * @return 命中返回 true
     */
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

    /**
     * 校验适用用户配置的合法性与复杂度。
     *
     * @param match 适用用户配置，为空抛出异常
     * @throws IllegalArgumentException 配置为空、缺少条件、逻辑非法或过于复杂时抛出
     */
    public void validate(MatchConfigDTO match) {
        if (match == null) {
            throw new IllegalArgumentException("适用用户配置不能为空");
        }
        int[] count = {0};
        if (match.getRoot() != null) {
            validateNode(match.getRoot(), 1, count);
            return;
        }
        if (match.getConditions() == null || match.getConditions().isEmpty()) {
            throw new IllegalArgumentException("至少配置一个适用用户条件");
        }
        String logic = normalized(match.getLogic(), "OR");
        if (!Set.of("AND", "OR").contains(logic)) {
            throw new IllegalArgumentException("适用用户逻辑只能是 AND 或 OR");
        }
        match.getConditions().forEach(this::validateCondition);
    }

    private void validateNode(
            MatchConfigDTO.MatchNodeDTO node,
            int depth,
            int[] count) {
        if (node == null) {
            throw new IllegalArgumentException("适用用户条件节点不能为空");
        }
        if (depth > MAX_DEPTH || ++count[0] > MAX_NODES) {
            throw new IllegalArgumentException("适用用户条件过于复杂");
        }
        if ("GROUP".equalsIgnoreCase(node.getType())) {
            if (!Set.of("AND", "OR").contains(normalized(node.getLogic(), ""))) {
                throw new IllegalArgumentException("适用用户条件组只能使用 AND 或 OR");
            }
            if (node.getChildren() == null || node.getChildren().isEmpty()) {
                throw new IllegalArgumentException("适用用户条件组不能为空");
            }
            for (MatchConfigDTO.MatchNodeDTO child : node.getChildren()) {
                validateNode(child, depth + 1, count);
            }
            return;
        }
        validateCondition(node.getCondition());
    }

    private void validateCondition(MatchConfigDTO.MatchConditionDTO condition) {
        if (condition == null || condition.getScopeType() == null
                || condition.getScopeType().isBlank()) {
            throw new IllegalArgumentException("适用用户条件缺少范围类型");
        }
        String type = normalized(condition.getScopeType(), "");
        if (!BUILTIN_TYPES.contains(type)) {
            matchProviders.stream()
                    .filter(provider -> provider.getScopeType().equalsIgnoreCase(type))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "不支持的适用用户范围: " + condition.getScopeType()))
                    .validate(condition);
            return;
        }
        if (!"ALL_USERS".equals(type)
                && (condition.getTargetIds() == null || condition.getTargetIds().isEmpty())) {
            throw new IllegalArgumentException("适用用户范围未选择目标");
        }
        if (!Set.of("ANY", "ALL").contains(normalized(condition.getOperator(), "ANY"))) {
            throw new IllegalArgumentException("适用用户匹配方式只能是 ANY 或 ALL");
        }
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
        // 根据范围类型分派到不同的内置匹配或自定义扩展匹配
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

    private String normalized(String value, String fallback) {
        return value == null || value.isBlank()
                ? fallback
                : value.trim().toUpperCase(Locale.ROOT);
    }
}
