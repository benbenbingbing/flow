package com.workflow.service.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.permission.*;
import com.workflow.entity.EntityListPermission;
import com.workflow.entity.EntityListPermissionDelegate;
import com.workflow.entity.SysOrganization;
import com.workflow.entity.SysUser;
import com.workflow.mapper.EntityListPermissionDelegateMapper;
import com.workflow.mapper.EntityListPermissionMapper;
import com.workflow.mapper.SysOrganizationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据权限规则引擎
 * 根据当前用户和实体编码，计算数据权限过滤条件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPermissionEngine {

    private final EntityListPermissionMapper permissionMapper;
    private final EntityListPermissionDelegateMapper delegateMapper;
    private final SysOrganizationMapper orgMapper;
    private final ObjectMapper objectMapper;

    /** 数据权限委托相关字段 */
    private static final String DELEGATE_USER_FIELD = "created_by";

    /**
     * 计算某实体列表的数据权限
     *
     * @param entityCode 实体编码
     * @param user       当前用户
     * @return 权限结果
     */
    public DataPermissionResult calculatePermission(String entityCode, SysUser user) {
        DataPermissionResult baseResult = doCalculatePermission(entityCode, user);
        return applyDelegation(baseResult, entityCode, user);
    }

    /**
     * 核心权限计算（不含委托）
     */
    private DataPermissionResult doCalculatePermission(String entityCode, SysUser user) {
        if (user == null) {
            return DataPermissionResult.denyAll();
        }

        // 1. 查询该实体所有启用的规则
        List<EntityListPermission> rules = permissionMapper.findEnabledByEntityCode(entityCode);

        if (rules.isEmpty()) {
            // 没有配置规则，默认仅本人
            return DataPermissionResult.withCondition(
                    "created_by = '" + escapeSql(user.getId()) + "'"
            );
        }

        // 2. 过滤出匹配的规则
        List<EntityListPermission> matchedRules = rules.stream()
                .filter(rule -> isMatch(rule, user))
                .collect(Collectors.toList());

        if (matchedRules.isEmpty()) {
            // 没有匹配规则 = 默认仅本人
            return DataPermissionResult.withCondition(
                    "created_by = '" + escapeSql(user.getId()) + "'"
            );
        }

        // 3. 检查是否有 ALL 类型的规则（直接放行）
        boolean hasAllRule = matchedRules.stream()
                .anyMatch(r -> {
                    FilterConfigDTO filter = parseFilterConfig(r.getFilterConfig());
                    return filter != null && "ALL".equals(filter.getType());
                });

        if (hasAllRule) {
            return DataPermissionResult.allowAll();
        }

        // 4. 构建每个匹配规则的 SQL 条件
        List<String> conditions = new ArrayList<>();
        List<String> matchedNames = new ArrayList<>();

        for (EntityListPermission rule : matchedRules) {
            FilterConfigDTO filter = parseFilterConfig(rule.getFilterConfig());
            if (filter == null) continue;

            String sql = buildFilterSql(filter, user);
            if (sql != null && !sql.isEmpty()) {
                conditions.add(sql);
                matchedNames.add(rule.getRuleName());
            }
        }

        if (conditions.isEmpty()) {
            return DataPermissionResult.denyAll();
        }

        // 5. 合并条件（默认并集）
        String combinedSql = "(" + String.join(") OR (", conditions) + ")";

        DataPermissionResult result = DataPermissionResult.withCondition(combinedSql);
        result.setMatchedRuleNames(matchedNames);
        return result;
    }

    /**
     * 应用数据权限委托
     * 将受托方权限与委托方数据范围合并
     */
    private DataPermissionResult applyDelegation(DataPermissionResult baseResult, String entityCode, SysUser user) {
        if (user == null || !baseResult.isHasPermission()) {
            return baseResult;
        }

        // allowAll 不需要委托（已经能看到所有数据）
        if (!baseResult.isNeedFilter()) {
            return baseResult;
        }

        List<EntityListPermissionDelegate> delegates = delegateMapper.findActiveByToUserId(user.getId(), entityCode);
        if (delegates == null || delegates.isEmpty()) {
            return baseResult;
        }

        // 收集所有委托方的用户ID
        List<String> fromUserIds = delegates.stream()
                .map(EntityListPermissionDelegate::getFromUserId)
                .filter(id -> id != null && !id.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        if (fromUserIds.isEmpty()) {
            return baseResult;
        }

        String escapedIds = fromUserIds.stream()
                .map(this::escapeSql)
                .collect(Collectors.joining("','"));
        String delegateSql = DELEGATE_USER_FIELD + " IN ('" + escapedIds + "')";

        String combinedSql = "(" + baseResult.getSqlCondition() + ") OR (" + delegateSql + ")";
        DataPermissionResult result = DataPermissionResult.withCondition(combinedSql);
        result.setMatchedRuleNames(baseResult.getMatchedRuleNames());
        return result;
    }

    /**
     * 判断用户是否匹配某条规则
     */
    private boolean isMatch(EntityListPermission rule, SysUser user) {
        MatchConfigDTO match = parseMatchConfig(rule.getMatchConfig());
        if (match == null || match.getConditions() == null || match.getConditions().isEmpty()) {
            return false;
        }

        String logic = match.getLogic();
        if (logic == null) logic = "OR";

        List<Boolean> results = new ArrayList<>();
        for (MatchConfigDTO.MatchConditionDTO condition : match.getConditions()) {
            results.add(matchSingleCondition(condition, user));
        }

        if ("AND".equalsIgnoreCase(logic)) {
            return results.stream().allMatch(Boolean::booleanValue);
        } else {
            return results.stream().anyMatch(Boolean::booleanValue);
        }
    }

    /**
     * 匹配单条条件
     */
    private boolean matchSingleCondition(MatchConfigDTO.MatchConditionDTO condition, SysUser user) {
        String scopeType = condition.getScopeType();
        List<String> targetIds = condition.getTargetIds();

        switch (scopeType) {
            case "ALL_USERS":
                return true;
            case "USER":
                return targetIds != null && targetIds.contains(user.getId());
            case "ROLE":
                return matchRole(condition, user);
            case "DEPT":
                return matchDept(condition, user);
            case "EXPRESSION":
                // 表达式匹配（简化版，实际可扩展Groovy）
                return false;
            default:
                return false;
        }
    }

    /**
     * 匹配角色
     */
    private boolean matchRole(MatchConfigDTO.MatchConditionDTO condition, SysUser user) {
        List<String> targetIds = condition.getTargetIds();
        if (targetIds == null || targetIds.isEmpty()) return false;

        List<String> userRoleIds = user.getRoleIds();
        if (userRoleIds == null || userRoleIds.isEmpty()) return false;

        String operator = condition.getOperator();
        if (operator == null) operator = "ANY";

        if ("ALL".equalsIgnoreCase(operator)) {
            return userRoleIds.containsAll(targetIds);
        } else {
            // ANY：任一角色匹配即可
            return targetIds.stream().anyMatch(userRoleIds::contains);
        }
    }

    /**
     * 匹配部门
     */
    private boolean matchDept(MatchConfigDTO.MatchConditionDTO condition, SysUser user) {
        List<String> targetIds = condition.getTargetIds();
        if (targetIds == null || targetIds.isEmpty()) return false;

        String userDeptId = user.getDeptId();
        if (userDeptId == null) return false;

        Boolean includeSub = condition.getIncludeSubDept();
        if (includeSub != null && includeSub) {
            // 包含子部门：查询用户部门路径，判断是否包含任一目标部门
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
        } else {
            return targetIds.contains(userDeptId);
        }
    }

    /**
     * 构建单条规则的过滤 SQL
     */
    private String buildFilterSql(FilterConfigDTO filter, SysUser user) {
        if (filter == null) return null;

        StringBuilder sql = new StringBuilder();
        String type = filter.getType();
        FilterConfigDTO.FieldMappingDTO mapping = filter.getFieldMapping();
        if (mapping == null) mapping = new FilterConfigDTO.FieldMappingDTO();

        String deptField = mapping.getDeptField();
        String userField = mapping.getUserField();

        switch (type) {
            case "PERSONAL":
                sql.append(userField).append(" = '" ).append(escapeSql(user.getId())).append("'");
                break;
            case "DEPT":
                if (user.getDeptId() != null) {
                    sql.append(deptField).append(" = '").append(escapeSql(user.getDeptId())).append("'");
                } else {
                    sql.append("1=0"); // 无部门 = 看不到数据
                }
                break;
            case "DEPT_TREE":
                String deptTreeSql = buildDeptTreeSql(deptField, user.getDeptId());
                sql.append(deptTreeSql);
                break;
            case "ALL":
                return "1=1";
            case "EXPRESSION":
                // 表达式类型暂不实现，返回本人条件兜底
                sql.append(userField).append(" = '").append(escapeSql(user.getId())).append("'");
                break;
            default:
                sql.append(userField).append(" = '").append(escapeSql(user.getId())).append("'");
        }

        // 附加状态限制
        String statusSql = buildStatusSql(filter.getStatusLimit(), mapping.getStatusField());
        if (statusSql != null) {
            sql.append(" AND ").append(statusSql);
        }

        return sql.toString();
    }

    /**
     * 构建部门树 SQL（含子部门）
     */
    private String buildDeptTreeSql(String deptField, String deptId) {
        if (deptId == null || deptId.isEmpty()) {
            return "1=0";
        }
        // 使用 sys_organization 的 path 字段查子部门
        return deptField + " IN (" +
                "SELECT id FROM sys_organization " +
                "WHERE id = '" + escapeSql(deptId) + "' " +
                "OR path LIKE '%/" + escapeSql(deptId) + "/%')";
    }

    /**
     * 构建状态限制 SQL
     */
    private String buildStatusSql(FilterConfigDTO.StatusLimitDTO statusLimit, String statusField) {
        if (statusLimit == null || !Boolean.TRUE.equals(statusLimit.getEnabled())) {
            return null;
        }

        String mode = statusLimit.getMode();
        List<String> values = statusLimit.getValues();

        if (values == null || values.isEmpty()) {
            return null;
        }

        List<String> escaped = values.stream()
                .map(this::escapeSql)
                .collect(Collectors.toList());

        if ("NOT_IN".equalsIgnoreCase(mode)) {
            return statusField + " NOT IN ('" + String.join("','", escaped) + "')";
        } else {
            return statusField + " IN ('" + String.join("','", escaped) + "')";
        }
    }

    /**
     * 解析匹配配置 JSON
     */
    private MatchConfigDTO parseMatchConfig(String json) {
        try {
            return objectMapper.readValue(json, MatchConfigDTO.class);
        } catch (Exception e) {
            log.warn("解析 match_config 失败: {}", json, e);
            return null;
        }
    }

    /**
     * 解析过滤配置 JSON
     */
    private FilterConfigDTO parseFilterConfig(String json) {
        try {
            return objectMapper.readValue(json, FilterConfigDTO.class);
        } catch (Exception e) {
            log.warn("解析 filter_config 失败: {}", json, e);
            return null;
        }
    }

    /**
     * SQL 字符串转义（防注入基础处理）
     */
    private String escapeSql(String input) {
        if (input == null) return "";
        return input.replace("'", "''");
    }
}
