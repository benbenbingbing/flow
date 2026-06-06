package com.workflow.service.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.permission.*;
import com.workflow.entity.EntityListPermission;
import com.workflow.entity.EntityListPermissionDelegate;
import com.workflow.entity.SysUser;
import com.workflow.mapper.EntityListPermissionDelegateMapper;
import com.workflow.mapper.EntityListPermissionMapper;
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
    private final ObjectMapper objectMapper;
    private final PermissionRuleMatcher ruleMatcher;
    private final PermissionSqlBuilder sqlBuilder;

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
                    "created_by = '" + sqlBuilder.escapeLiteral(user.getId()) + "'"
            );
        }

        // 2. 过滤出匹配的规则
        List<EntityListPermission> matchedRules = rules.stream()
                .filter(rule -> ruleMatcher.matches(parseMatchConfig(rule.getMatchConfig()), user))
                .collect(Collectors.toList());

        if (matchedRules.isEmpty()) {
            // 没有匹配规则 = 默认仅本人
            return DataPermissionResult.withCondition(
                    "created_by = '" + sqlBuilder.escapeLiteral(user.getId()) + "'"
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

            String sql = sqlBuilder.buildFilterSql(filter, user);
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
                .map(sqlBuilder::escapeLiteral)
                .collect(Collectors.joining("','"));
        String delegateSql = DELEGATE_USER_FIELD + " IN ('" + escapedIds + "')";

        String combinedSql = "(" + baseResult.getSqlCondition() + ") OR (" + delegateSql + ")";
        DataPermissionResult result = DataPermissionResult.withCondition(combinedSql);
        result.setMatchedRuleNames(baseResult.getMatchedRuleNames());
        return result;
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

}
