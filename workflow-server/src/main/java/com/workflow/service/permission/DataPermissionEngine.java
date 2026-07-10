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
 * 支持多规则编排（优先级、ALLOW/DENY、UNION/INTERSECT、停止规则）、列表级规则及自定义 SQL。
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

    /** 用户字段名（须与实体表 entity_data 的字段名保持一致） */
    private static final String USER_FIELD = "create_by";

    /**
     * 计算某实体的数据权限（不绑定具体列表，仅使用全局规则）。
     */
    public DataPermissionResult calculatePermission(String entityCode, SysUser user) {
        return calculatePermission(entityCode, null, user);
    }

    /**
     * 计算某实体列表的数据权限。
     *
     * @param entityCode   实体编码
     * @param listConfigId 列表配置ID（null 表示不绑定具体列表，只使用全局规则）
     * @param user         当前用户
     * @return 权限结果
     */
    public DataPermissionResult calculatePermission(String entityCode, String listConfigId, SysUser user) {
        CalculationResult calc = doCalculatePermission(entityCode, listConfigId, user);
        return applyDelegation(calc.getResult(), entityCode, user);
    }

    /**
     * 预览某实体列表生成的权限 SQL（不应用委托）。
     *
     * @param entityCode   实体编码
     * @param listConfigId 列表配置ID
     * @param user         当前用户
     * @return 预览结果（含最终 SQL 与命中规则明细）
     */
    public PermissionPreviewDTO previewPermissionDetail(String entityCode, String listConfigId, SysUser user) {
        CalculationResult calc = doCalculatePermission(entityCode, listConfigId, user);
        DataPermissionResult result = calc.getResult();

        PermissionPreviewDTO dto = new PermissionPreviewDTO();
        dto.setHasPermission(result.isHasPermission());
        dto.setNeedFilter(result.isNeedFilter());
        if (!result.isHasPermission()) {
            dto.setSql("1=0");
        } else if (!result.isNeedFilter()) {
            dto.setSql("1=1");
        } else {
            dto.setSql(result.getSqlCondition());
        }
        dto.setMatchedRules(calc.getMatchedRuleDetails());
        return dto;
    }

    /**
     * 预览某实体列表生成的权限 SQL（不应用委托）。
     *
     * @param entityCode   实体编码
     * @param listConfigId 列表配置ID
     * @param user         当前用户
     * @return 生成的 SQL 条件
     */
    public String previewPermissionSql(String entityCode, String listConfigId, SysUser user) {
        return previewPermissionDetail(entityCode, listConfigId, user).getSql();
    }

    /**
     * 预览单条规则生成的权限 SQL（不考虑其它规则与优先级编排）。
     *
     * @param rule 权限规则
     * @param user 当前用户
     * @return 预览结果
     */
    public PermissionPreviewDTO previewRuleSql(EntityListPermission rule, SysUser user) {
        PermissionPreviewDTO dto = new PermissionPreviewDTO();
        if (user == null) {
            dto.setHasPermission(false);
            dto.setNeedFilter(false);
            dto.setSql("1=0");
            return dto;
        }

        FilterConfigDTO filter = parseFilterConfig(rule.getFilterConfig());
        PermissionPreviewDTO.MatchedRuleDTO detail = new PermissionPreviewDTO.MatchedRuleDTO();
        detail.setRuleName(rule.getRuleName());
        detail.setRuleEffect(rule.getRuleEffect() == null ? "ALLOW" : rule.getRuleEffect());
        detail.setCombineMode(rule.getCombineMode() == null ? "UNION" : rule.getCombineMode());

        if (filter == null || "ALL".equals(filter.getType())) {
            detail.setSql("1=1");
            dto.setSql("1=1");
            dto.setNeedFilter(false);
        } else {
            String ruleSql = sqlBuilder.buildFilterSql(filter, user);
            boolean isDeny = "DENY".equalsIgnoreCase(rule.getRuleEffect());

            // 预览模式下，如果当前用户缺少部门ID，给出模板 SQL 与提示
            if ("1=0".equals(ruleSql) && ("DEPT".equals(filter.getType()) || "DEPT_TREE".equals(filter.getType()))
                    && user.getDeptId() == null) {
                String deptField = getDeptField(filter);
                String placeholder = "<当前用户部门ID>";
                String previewSql;
                if ("DEPT".equals(filter.getType())) {
                    previewSql = deptField + " = '" + placeholder + "'";
                } else {
                    previewSql = deptField + " IN (" +
                            "SELECT id FROM sys_organization " +
                            "WHERE id = '" + placeholder + "' " +
                            "OR path LIKE '%/" + placeholder + "/%')";
                }
                detail.setSql(previewSql);
                dto.setSql(isDeny ? "NOT (" + previewSql + ")" : previewSql);
                dto.setRemark("当前用户未设置部门ID，以下为模板 SQL，实际执行时会替换为登录用户的部门ID。");
                dto.setNeedFilter(true);
            } else {
                detail.setSql(ruleSql);
                dto.setSql(isDeny ? "NOT (" + ruleSql + ")" : ruleSql);
                dto.setNeedFilter(true);
            }
        }

        dto.setHasPermission(true);
        dto.setMatchedRules(Collections.singletonList(detail));
        return dto;
    }

    /**
     * 核心权限计算（不含委托）。
     */
    private CalculationResult doCalculatePermission(String entityCode, String listConfigId, SysUser user) {
        if (user == null) {
            return new CalculationResult(DataPermissionResult.denyAll(), List.of());
        }

        // 1. 查询该实体所有启用的规则
        List<EntityListPermission> rules = permissionMapper.findEnabledByEntityCode(entityCode);

        // 2. 按列表过滤：全局规则（list_config_id 为空）或匹配当前列表的规则
        List<EntityListPermission> scopedRules = rules.stream()
                .filter(rule -> rule.getListConfigId() == null || rule.getListConfigId().isBlank()
                        || rule.getListConfigId().equals(listConfigId))
                .collect(Collectors.toList());

        if (scopedRules.isEmpty()) {
            // 没有配置规则，默认仅本人
            return new CalculationResult(
                    DataPermissionResult.withCondition(
                            USER_FIELD + " = '" + sqlBuilder.escapeLiteral(user.getId()) + "'"
                    ),
                    List.of()
            );
        }

        // 3. 过滤出匹配当前用户的规则，并按优先级降序排列
        List<EntityListPermission> matchedRules = scopedRules.stream()
                .filter(rule -> ruleMatcher.matches(parseMatchConfig(rule.getMatchConfig()), user))
                .sorted((a, b) -> Integer.compare(
                        b.getPriority() == null ? 0 : b.getPriority(),
                        a.getPriority() == null ? 0 : a.getPriority()))
                .collect(Collectors.toList());

        if (matchedRules.isEmpty()) {
            // 没有匹配规则 = 默认仅本人
            return new CalculationResult(
                    DataPermissionResult.withCondition(
                            USER_FIELD + " = '" + sqlBuilder.escapeLiteral(user.getId()) + "'"
                    ),
                    List.of()
            );
        }

        // 4. 依次评估规则，进行编排
        DataPermissionResult accumulator = null;
        List<PermissionPreviewDTO.MatchedRuleDTO> matchedDetails = new ArrayList<>();

        for (EntityListPermission rule : matchedRules) {
            FilterConfigDTO filter = parseFilterConfig(rule.getFilterConfig());
            if (filter == null) {
                continue;
            }

            PermissionPreviewDTO.MatchedRuleDTO detail = new PermissionPreviewDTO.MatchedRuleDTO();
            detail.setRuleName(rule.getRuleName());
            detail.setRuleEffect(rule.getRuleEffect() == null ? "ALLOW" : rule.getRuleEffect());
            detail.setCombineMode(rule.getCombineMode() == null ? "UNION" : rule.getCombineMode());

            // ALL 类型直接放行
            if ("ALL".equals(filter.getType())) {
                detail.setSql("1=1");
                DataPermissionResult result = DataPermissionResult.allowAll();
                result.setMatchedRuleNames(Collections.singletonList(rule.getRuleName()));
                return new CalculationResult(result, Collections.singletonList(detail));
            }

            String ruleSql = sqlBuilder.buildFilterSql(filter, user);
            detail.setSql(ruleSql);
            if (ruleSql == null || ruleSql.isBlank()) {
                continue;
            }

            boolean isDeny = "DENY".equalsIgnoreCase(rule.getRuleEffect());
            String effectSql = isDeny ? "NOT (" + ruleSql + ")" : ruleSql;

            if (accumulator == null) {
                accumulator = DataPermissionResult.withCondition(effectSql);
            } else {
                String combineMode = rule.getCombineMode();
                if (combineMode == null || combineMode.isBlank()) {
                    combineMode = "UNION";
                }
                if ("INTERSECT".equalsIgnoreCase(combineMode)) {
                    accumulator.intersect(effectSql);
                } else {
                    accumulator.union(effectSql);
                }
            }

            matchedDetails.add(detail);

            // 命中后停止评估更低优先级规则
            if (rule.getStopProcessing() != null && rule.getStopProcessing() == 1) {
                break;
            }
        }

        if (accumulator == null) {
            return new CalculationResult(DataPermissionResult.denyAll(), matchedDetails);
        }

        accumulator.setMatchedRuleNames(matchedDetails.stream()
                .map(PermissionPreviewDTO.MatchedRuleDTO::getRuleName)
                .collect(Collectors.toList()));
        return new CalculationResult(accumulator, matchedDetails);
    }

    /**
     * 应用数据权限委托。
     */
    private DataPermissionResult applyDelegation(DataPermissionResult baseResult, String entityCode, SysUser user) {
        if (user == null || !baseResult.isHasPermission()) {
            return baseResult;
        }

        if (!baseResult.isNeedFilter()) {
            return baseResult;
        }

        List<EntityListPermissionDelegate> delegates = delegateMapper.findActiveByToUserId(user.getId(), entityCode);
        if (delegates == null || delegates.isEmpty()) {
            return baseResult;
        }

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
        String delegateSql = USER_FIELD + " IN ('" + escapedIds + "')";

        String combinedSql = "(" + baseResult.getSqlCondition() + ") OR (" + delegateSql + ")";
        DataPermissionResult result = DataPermissionResult.withCondition(combinedSql);
        result.setMatchedRuleNames(baseResult.getMatchedRuleNames());
        return result;
    }

    private MatchConfigDTO parseMatchConfig(String json) {
        try {
            return objectMapper.readValue(json, MatchConfigDTO.class);
        } catch (Exception e) {
            log.warn("解析 match_config 失败: {}", json, e);
            return null;
        }
    }

    private FilterConfigDTO parseFilterConfig(String json) {
        try {
            return objectMapper.readValue(json, FilterConfigDTO.class);
        } catch (Exception e) {
            log.warn("解析 filter_config 失败: {}", json, e);
            return null;
        }
    }

    private String getDeptField(FilterConfigDTO filter) {
        if (filter != null && filter.getFieldMapping() != null
                && filter.getFieldMapping().getDeptField() != null
                && !filter.getFieldMapping().getDeptField().isBlank()) {
            return filter.getFieldMapping().getDeptField();
        }
        return "dept_id";
    }

    /**
     * 内部计算结果包装类。
     */
    private static class CalculationResult {
        private final DataPermissionResult result;
        private final List<PermissionPreviewDTO.MatchedRuleDTO> matchedRuleDetails;

        CalculationResult(DataPermissionResult result, List<PermissionPreviewDTO.MatchedRuleDTO> matchedRuleDetails) {
            this.result = result;
            this.matchedRuleDetails = matchedRuleDetails;
        }

        DataPermissionResult getResult() {
            return result;
        }

        List<PermissionPreviewDTO.MatchedRuleDTO> getMatchedRuleDetails() {
            return matchedRuleDetails;
        }
    }
}
