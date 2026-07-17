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
 * 支持多规则编排、列表级规则和结构化数据过滤。
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
        return applyDelegation(calc, entityCode, user);
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
            String ruleSql = sqlBuilder.buildFilterSql(rule.getEntityCode(), filter, user);
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
        List<EntityListPermission> sortedRules = scopedRules.stream()
                .sorted((a, b) -> Integer.compare(
                        b.getPriority() == null ? 0 : b.getPriority(),
                        a.getPriority() == null ? 0 : a.getPriority()))
                .toList();
        List<EntityListPermission> matchedRules = new ArrayList<>();
        for (EntityListPermission rule : sortedRules) {
            MatchConfigDTO match = parseMatchConfig(rule.getMatchConfig());
            if (match == null) {
                if ("DENY".equalsIgnoreCase(rule.getRuleEffect())) {
                    log.error("DENY 数据权限规则匹配配置损坏，按拒绝全部处理: ruleId={}, ruleName={}",
                            rule.getId(), rule.getRuleName());
                    PermissionPreviewDTO.MatchedRuleDTO detail =
                            matchedRuleDetail(rule, "1=1");
                    DataPermissionResult denied = DataPermissionResult.denyAll();
                    denied.setMatchedRuleNames(List.of(rule.getRuleName()));
                    return new CalculationResult(
                            denied,
                            List.of(detail),
                            "1=1");
                }
                log.error("ALLOW 数据权限规则匹配配置损坏，按不授权处理: ruleId={}, ruleName={}",
                        rule.getId(), rule.getRuleName());
                continue;
            }
            if (ruleMatcher.matches(match, user)) {
                matchedRules.add(rule);
            }
        }

        if (matchedRules.isEmpty()) {
            // 没有匹配规则 = 默认仅本人
            return new CalculationResult(
                    DataPermissionResult.withCondition(
                            USER_FIELD + " = '" + sqlBuilder.escapeLiteral(user.getId()) + "'"
                    ),
                    List.of()
            );
        }

        // 4. 允许范围单独编排，拒绝范围统一在最后扣除
        String allowSql = null;
        String denySql = null;
        boolean stopAllowProcessing = false;
        List<PermissionPreviewDTO.MatchedRuleDTO> matchedDetails = new ArrayList<>();

        for (EntityListPermission rule : matchedRules) {
            boolean isDeny = "DENY".equalsIgnoreCase(rule.getRuleEffect());
            FilterConfigDTO filter = parseFilterConfig(rule.getFilterConfig());
            String ruleSql;
            try {
                if (filter == null) {
                    throw new IllegalArgumentException("过滤配置 JSON 损坏");
                }
                sqlBuilder.validateFilter(entityCode, filter);
                ruleSql = sqlBuilder.buildFilterSql(entityCode, filter, user);
            } catch (RuntimeException exception) {
                ruleSql = isDeny ? "1=1" : "1=0";
                log.error("{} 数据权限规则过滤配置无效，按 {} 处理: ruleId={}, ruleName={}",
                        isDeny ? "DENY" : "ALLOW",
                        isDeny ? "拒绝全部" : "不授权",
                        rule.getId(),
                        rule.getRuleName());
            }
            PermissionPreviewDTO.MatchedRuleDTO detail =
                    matchedRuleDetail(rule, ruleSql);
            if (ruleSql == null || ruleSql.isBlank()) {
                continue;
            }

            if (isDeny) {
                denySql = combine(denySql, ruleSql, "UNION");
            } else if (!stopAllowProcessing) {
                allowSql = combine(allowSql, ruleSql, rule.getCombineMode());
                if (rule.getStopProcessing() != null && rule.getStopProcessing() == 1) {
                    stopAllowProcessing = true;
                }
            } else {
                continue;
            }

            matchedDetails.add(detail);
        }

        if (allowSql == null) {
            allowSql = defaultUserSql(user);
        }

        DataPermissionResult result;
        if ("1=0".equals(allowSql) || "1=1".equals(denySql)) {
            result = DataPermissionResult.denyAll();
        } else if ("1=1".equals(allowSql) && denySql == null) {
            result = DataPermissionResult.allowAll();
        } else {
            String finalSql = denySql == null
                    ? allowSql
                    : "(" + allowSql + ") AND NOT (" + denySql + ")";
            result = DataPermissionResult.withCondition(finalSql);
        }
        result.setMatchedRuleNames(matchedDetails.stream()
                .map(PermissionPreviewDTO.MatchedRuleDTO::getRuleName)
                .collect(Collectors.toList()));
        return new CalculationResult(result, matchedDetails, denySql);
    }

    /**
     * 应用数据权限委托。
     */
    private DataPermissionResult applyDelegation(
            CalculationResult calculation,
            String entityCode,
            SysUser user) {
        DataPermissionResult baseResult = calculation.getResult();
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
        if (calculation.getDenySql() != null) {
            delegateSql = "(" + delegateSql + ") AND NOT ("
                    + calculation.getDenySql() + ")";
        }

        String combinedSql = "(" + baseResult.getSqlCondition() + ") OR (" + delegateSql + ")";
        DataPermissionResult result = DataPermissionResult.withCondition(combinedSql);
        result.setMatchedRuleNames(baseResult.getMatchedRuleNames());
        return result;
    }

    private MatchConfigDTO parseMatchConfig(String json) {
        try {
            return objectMapper.readValue(json, MatchConfigDTO.class);
        } catch (Exception e) {
            log.error("解析数据权限 match_config 失败: {}", e.getMessage());
            return null;
        }
    }

    private FilterConfigDTO parseFilterConfig(String json) {
        try {
            return objectMapper.readValue(json, FilterConfigDTO.class);
        } catch (Exception e) {
            log.error("解析数据权限 filter_config 失败: {}", e.getMessage());
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

    private String defaultUserSql(SysUser user) {
        return USER_FIELD + " = '" + sqlBuilder.escapeLiteral(user.getId()) + "'";
    }

    private PermissionPreviewDTO.MatchedRuleDTO matchedRuleDetail(
            EntityListPermission rule,
            String sql) {
        PermissionPreviewDTO.MatchedRuleDTO detail =
                new PermissionPreviewDTO.MatchedRuleDTO();
        detail.setRuleName(rule.getRuleName());
        detail.setRuleEffect(
                rule.getRuleEffect() == null ? "ALLOW" : rule.getRuleEffect());
        detail.setCombineMode(
                rule.getCombineMode() == null ? "UNION" : rule.getCombineMode());
        detail.setSql(sql);
        return detail;
    }

    private String combine(String current, String next, String combineMode) {
        if (current == null || current.isBlank()) {
            return next;
        }
        if ("INTERSECT".equalsIgnoreCase(combineMode)) {
            return "(" + current + ") AND (" + next + ")";
        }
        return "(" + current + ") OR (" + next + ")";
    }

    /**
     * 内部计算结果包装类。
     */
    private static class CalculationResult {
        private final DataPermissionResult result;
        private final List<PermissionPreviewDTO.MatchedRuleDTO> matchedRuleDetails;
        private final String denySql;

        CalculationResult(DataPermissionResult result, List<PermissionPreviewDTO.MatchedRuleDTO> matchedRuleDetails) {
            this(result, matchedRuleDetails, null);
        }

        CalculationResult(
                DataPermissionResult result,
                List<PermissionPreviewDTO.MatchedRuleDTO> matchedRuleDetails,
                String denySql) {
            this.result = result;
            this.matchedRuleDetails = matchedRuleDetails;
            this.denySql = denySql;
        }

        DataPermissionResult getResult() {
            return result;
        }

        List<PermissionPreviewDTO.MatchedRuleDTO> getMatchedRuleDetails() {
            return matchedRuleDetails;
        }

        String getDenySql() {
            return denySql;
        }
    }
}
