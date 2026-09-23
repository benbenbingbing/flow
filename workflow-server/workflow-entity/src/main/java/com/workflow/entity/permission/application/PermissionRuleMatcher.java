package com.workflow.entity.permission.application;

import com.workflow.entity.permission.api.response.MatchConfigDTO;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
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
            Set.of("ALL_USERS", "USER", "ROLE", "GROUP", "DEPT", "ORG", "SQL");

    private final SysOrganizationMapper orgMapper;
    private final SysUserGroupMapper userGroupMapper;
    private final List<EntityDataPermissionMatchProvider> matchProviders;
    private final PermissionSqlFragmentCompiler sqlFragmentCompiler;

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
        this(orgMapper, userGroupMapper, matchProviders, null);
    }

    /**
     * 初始化权限规则匹配器，保存构造参数供后续方法使用。
     *
     * @param orgMapper 组织映射器依赖，保存到当前对象供后续业务方法调用
     * @param userGroupMapper 用户分组映射器依赖，保存到当前对象供后续业务方法调用
     * @param matchProviders 匹配提供者集合依赖，保存到当前对象供后续业务方法调用
     * @param sqlFragmentCompiler SQL{@code fragment}{@code compiler}依赖，保存到当前对象供后续业务方法调用
     */
    @org.springframework.beans.factory.annotation.Autowired
    public PermissionRuleMatcher(
            SysOrganizationMapper orgMapper,
            SysUserGroupMapper userGroupMapper,
            List<EntityDataPermissionMatchProvider> matchProviders,
            PermissionSqlFragmentCompiler sqlFragmentCompiler) {
        this.orgMapper = orgMapper;
        this.userGroupMapper = userGroupMapper;
        this.matchProviders = matchProviders == null ? List.of() : matchProviders;
        this.sqlFragmentCompiler = sqlFragmentCompiler;
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

    /**
     * 校验节点；不满足约束时阻止后续处理。
     *
     * @param node 节点，作为 {@code validateCondition} 的输入影响后续处理
     * @param depth 深度，供本方法校验节点时使用
     * @param count 数量，供本方法校验节点时使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 校验条件；不满足约束时阻止后续处理。
     *
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
        if ("SQL".equals(type)) {
            if (sqlFragmentCompiler == null) {
                throw new IllegalArgumentException("未配置 SQL 条件编译器");
            }
            sqlFragmentCompiler.validate(condition.getSql(), false);
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

    /**
     * 判断是否匹配节点；判断结果决定调用方的后续分支。
     *
     * @param node 节点，作为 {@code matchesCondition} 的输入影响后续处理
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 节点条件成立时为 true，否则为 false
     */
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

    /**
     * 判断是否匹配{@code conditions}；判断结果决定调用方的后续分支。
     *
     * @param logic {@code logic}，供本方法判断是否匹配{@code conditions}时使用
     * @param conditions {@code conditions}，供本方法判断是否匹配{@code conditions}时使用
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return {@code conditions}条件成立时为 true，否则为 false
     */
    private boolean matchesConditions(
            String logic,
            List<MatchConfigDTO.MatchConditionDTO> conditions,
            SysUser user) {
        if ("AND".equalsIgnoreCase(logic)) {
            return conditions.stream().allMatch(condition -> matchesCondition(condition, user));
        }
        return conditions.stream().anyMatch(condition -> matchesCondition(condition, user));
    }

    /**
     * 判断是否匹配条件；判断结果决定调用方的后续分支。
     *
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 条件条件成立时为 true，否则为 false
     */
    private boolean matchesCondition(MatchConfigDTO.MatchConditionDTO condition, SysUser user) {
        if (condition == null || condition.getScopeType() == null) {
            return false;
        }
        String scopeType = condition.getScopeType().toUpperCase(Locale.ROOT);
        // 根据范围类型分派到不同的内置匹配或自定义扩展匹配
        return switch (scopeType) {
            case "ALL_USERS" -> true;
            case "SQL" -> sqlFragmentCompiler != null
                    && sqlFragmentCompiler.matchesUser(condition.getSql(), user);
            case "USER" -> matchesCollection(condition, userIdentities(user));
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

    /**
     * 指定用户既可能保存系统用户 ID，也可能保存用户名（选择器 value-key 或历史数据）。
     *
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 权限规则匹配器集合，供调用方遍历或展示
     */
    private List<String> userIdentities(SysUser user) {
        LinkedHashSet<String> identities = new LinkedHashSet<>();
        if (user != null && user.getId() != null && !user.getId().isBlank()) {
            identities.add(user.getId());
        }
        if (user != null && user.getUsername() != null && !user.getUsername().isBlank()) {
            identities.add(user.getUsername());
        }
        return List.copyOf(identities);
    }

    /**
     * 判断是否匹配集合；判断结果决定调用方的后续分支。
     *
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param currentIds 当前ID 集合，供本方法判断是否匹配集合时使用
     * @return 集合条件成立时为 true，否则为 false
     */
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

    /**
     * 判断是否匹配组织；判断结果决定调用方的后续分支。
     *
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param currentOrganizationId 当前组织ID，后续用于判断是否匹配组织时定位或关联目标
     * @return 组织条件成立时为 true，否则为 false
     */
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

    /**
     * 判断是否匹配自定义；判断结果决定调用方的后续分支。
     *
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 自定义条件成立时为 true，否则为 false
     */
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

    /**
     * 生成规范化文本，供后续匹配或展示。
     *
     * @param value 待处理规范化的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的规范化文本，供调用方比较或展示
     */
    private String normalized(String value, String fallback) {
        return value == null || value.isBlank()
                ? fallback
                : value.trim().toUpperCase(Locale.ROOT);
    }
}
