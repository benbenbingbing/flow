package com.workflow.service.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.PermissionUtil;
import com.workflow.dto.permission.*;
import com.workflow.entity.EntityListScopeDelegation;
import com.workflow.entity.SysUser;
import com.workflow.mapper.EntityListScopeDelegationMapper;
import com.workflow.service.SysUserService;
import com.workflow.service.EntityRecordTeamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 基于发布快照的数据范围引擎。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPermissionEngine {

    private final EntityListScopeService scopeService;
    private final EntityListScopeDelegationMapper delegationMapper;
    private final ObjectMapper objectMapper;
    private final PermissionRuleMatcher ruleMatcher;
    private final PermissionSqlBuilder sqlBuilder;
    private final SysUserService sysUserService;
    private final EntityListScopeAuditService auditService;
    private final EntityRecordTeamService entityRecordTeamService;

    public DataPermissionResult calculatePermission(String entityCode, SysUser user) {
        return calculatePermission(entityCode, null, user);
    }

    public DataPermissionResult calculatePermission(
            String entityCode,
            String listKey,
            SysUser user) {
        CalculationResult calculation = calculate(entityCode, listKey, user);
        return calculation.result();
    }

    public PermissionPreviewDTO previewPermissionDetail(
            String entityCode,
            String listKey,
            SysUser user) {
        CalculationResult calculation = calculate(entityCode, listKey, user);
        DataPermissionResult result = calculation.result();
        PermissionPreviewDTO preview = new PermissionPreviewDTO();
        preview.setHasPermission(result.isHasPermission());
        preview.setNeedFilter(result.isNeedFilter());
        preview.setSql(result.isHasPermission()
                ? (result.isNeedFilter() ? result.getSqlCondition() : "1=1")
                : "1=0");
        preview.setMatchedRules(calculation.matchedRules());
        preview.setRemark(result.getExplanation());
        preview.setDataScopeMode(result.getDataScopeMode());
        preview.setReleaseVersion(result.getReleaseVersion());
        return preview;
    }

    private CalculationResult calculate(
            String entityCode,
            String listKey,
            SysUser user) {
        if (user == null || !StringUtils.hasText(user.getId())) {
            return denied("当前用户不存在", null, List.of());
        }

        String bypassPermission = "entity:"
                + EntityPermissionAction.normalizeEntityCode(entityCode)
                + ":scope:bypass";
        if (hasBypass(user.getId(), bypassPermission)) {
            auditService.record(
                    entityCode,
                    listKey,
                    user.getId(),
                    "BYPASS",
                    "SUCCESS",
                    Map.of("permission", bypassPermission));
            DataPermissionResult result = DataPermissionResult.allowAll();
            result.setDataScopeMode("BYPASS");
            result.setExplanation("通过显式数据范围绕过权限访问全部数据");
            return new CalculationResult(result, List.of());
        }

        EntityListScopeSnapshotDTO snapshot;
        try {
            snapshot = scopeService.getActiveSnapshot(entityCode);
        } catch (RuntimeException exception) {
            log.error("读取数据范围发布快照失败: entityCode={}", entityCode, exception);
            return denied("数据范围发布快照损坏", null, List.of());
        }
        if (snapshot == null) {
            return denied("实体没有已发布的数据范围", null, List.of());
        }

        Map<String, EntityListScopePolicyDTO> policyMap = new LinkedHashMap<>();
        for (EntityListScopePolicyDTO policy : snapshot.getPolicies()) {
            if (policy != null && Integer.valueOf(1).equals(policy.getEnabled())) {
                policyMap.put(policy.getId(), policy);
            }
        }

        String mode = StringUtils.hasText(listKey)
                ? normalized(snapshot.getListModes().get(listKey), "INHERIT")
                : "INHERIT";
        List<PermissionPreviewDTO.MatchedRuleDTO> matched = new ArrayList<>();
        List<String> entityAllows = new ArrayList<>();
        List<String> listAllows = new ArrayList<>();
        List<String> denies = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (EntityListScopeBindingDTO binding : snapshot.getBindings()) {
            if (binding == null || !Integer.valueOf(1).equals(binding.getEnabled())
                    || !isEffective(binding, now)
                    || (StringUtils.hasText(binding.getListKey())
                    && !binding.getListKey().equals(listKey))) {
                continue;
            }
            EntityListScopePolicyDTO policy = policyMap.get(binding.getPolicyId());
            if (policy == null) {
                if ("DENY".equalsIgnoreCase(binding.getRuleEffect())) {
                    return denied(
                            "拒绝规则引用的方案不存在",
                            snapshot.getVersion(),
                            matched);
                }
                continue;
            }
            try {
                if (!ruleMatcher.matches(binding.getMatchConfig(), user)) {
                    continue;
                }
                sqlBuilder.validateFilter(entityCode, policy.getFilterConfig());
                String sql = sqlBuilder.buildFilterSql(
                        entityCode,
                        policy.getFilterConfig(),
                        user);
                PermissionPreviewDTO.MatchedRuleDTO detail =
                        detail(policy, binding, sql);
                matched.add(detail);
                if ("DENY".equalsIgnoreCase(binding.getRuleEffect())) {
                    denies.add(sql);
                } else if (!StringUtils.hasText(binding.getListKey())) {
                    entityAllows.add(sql);
                } else if (binding.getListKey().equals(listKey)) {
                    listAllows.add(sql);
                }
            } catch (RuntimeException exception) {
                if ("DENY".equalsIgnoreCase(binding.getRuleEffect())) {
                    log.error("DENY 数据范围方案无效，按拒绝全部处理: policyKey={}",
                            policy.getPolicyKey(), exception);
                    return denied(
                            "拒绝方案配置损坏: " + policy.getPolicyName(),
                            snapshot.getVersion(),
                            matched);
                }
                log.error("ALLOW 数据范围方案无效，按不授权处理: policyKey={}",
                        policy.getPolicyKey(), exception);
            }
        }

        EntityRecordTeamService.TeamPermission teamPermission =
                entityRecordTeamService.teamPermission(entityCode, user.getId());
        if (teamPermission.enabled()
                && teamPermission.level()
                == com.workflow.entity.EntityDefinition.TeamVisibilityLevel.ADDITIVE) {
            entityAllows.add(teamPermission.sqlCondition());
        }

        String entityAllow = or(entityAllows);
        String listAllow = or(listAllows);
        String allow = switch (mode) {
            case "NARROW" -> and(entityAllow, listAllow);
            case "OVERRIDE" -> listAllow;
            default -> entityAllow;
        };

        String delegatedAllow = buildDelegatedAllow(
                entityCode, snapshot, policyMap, user);
        allow = orNonNull(allow, delegatedAllow);
        if (teamPermission.enabled()
                && teamPermission.level()
                == com.workflow.entity.EntityDefinition.TeamVisibilityLevel.OVERRIDE_SCOPE) {
            allow = orNonNull(allow, teamPermission.sqlCondition());
        }
        if (!StringUtils.hasText(allow)) {
            if (teamPermission.enabled()
                    && teamPermission.level()
                    == com.workflow.entity.EntityDefinition.TeamVisibilityLevel.ABSOLUTE) {
                allow = "1=0";
            } else {
                return denied(
                        "没有匹配到任何允许数据范围",
                        snapshot.getVersion(),
                        matched,
                        mode);
            }
        }

        String deny = or(denies);
        String finalSql = StringUtils.hasText(deny)
                ? "(" + allow + ") AND NOT (" + deny + ")"
                : allow;
        if (teamPermission.enabled()
                && teamPermission.level()
                == com.workflow.entity.EntityDefinition.TeamVisibilityLevel.ABSOLUTE) {
            finalSql = orNonNull(finalSql, teamPermission.sqlCondition());
        }
        boolean absoluteTeamAccess = teamPermission.enabled()
                && teamPermission.level()
                == com.workflow.entity.EntityDefinition.TeamVisibilityLevel.ABSOLUTE;
        if (!absoluteTeamAccess && ("1=0".equals(allow) || "1=1".equals(deny))) {
            return denied(
                    "数据被拒绝方案全部排除",
                    snapshot.getVersion(),
                    matched,
                    mode);
        }

        DataPermissionResult result = "1=1".equals(finalSql)
                ? DataPermissionResult.allowAll()
                : DataPermissionResult.withCondition(finalSql);
        result.setMatchedRuleNames(matched.stream()
                .map(PermissionPreviewDTO.MatchedRuleDTO::getRuleName)
                .toList());
        result.setReleaseVersion(snapshot.getVersion());
        result.setDataScopeMode(mode);
        result.setExplanation(explanation(mode, entityAllows, listAllows, denies));
        return new CalculationResult(result, matched);
    }

    private String buildDelegatedAllow(
            String entityCode,
            EntityListScopeSnapshotDTO snapshot,
            Map<String, EntityListScopePolicyDTO> policyMap,
            SysUser recipient) {
        List<EntityListScopeDelegation> delegations =
                delegationMapper.findActiveByToUserId(recipient.getId(), entityCode);
        if (delegations == null || delegations.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (EntityListScopeDelegation delegation : delegations) {
            SysUser delegator = sysUserService.getById(delegation.getFromUserId());
            if (delegator == null) {
                continue;
            }
            try {
                String scope = normalized(delegation.getDelegateScope(), "PERSONAL");
                String sql = switch (scope) {
                    case "CREATED" -> userRelation("create_by", delegator);
                    case "SUBMITTED" -> userRelation("submitter_id", delegator);
                    case "CURRENT_TASK" -> userRelation("current_task_assignee", delegator);
                    case "POLICY" -> compileDelegatedPolicy(
                            entityCode, policyMap.get(delegation.getPolicyId()), delegator);
                    case "CONDITION" -> compileDelegatedCondition(
                            entityCode, delegation.getDelegateConfig(), delegator);
                    default -> "(" + userRelation("create_by", delegator)
                            + ") OR (" + userRelation("submitter_id", delegator) + ")";
                };
                if (StringUtils.hasText(sql)) {
                    parts.add(sql);
                }
            } catch (RuntimeException exception) {
                log.error("数据范围委托配置无效，已忽略: delegationId={}",
                        delegation.getId(), exception);
            }
        }
        return or(parts);
    }

    private String compileDelegatedPolicy(
            String entityCode,
            EntityListScopePolicyDTO policy,
            SysUser delegator) {
        if (policy == null || !Integer.valueOf(1).equals(policy.getEnabled())) {
            return null;
        }
        sqlBuilder.validateFilter(entityCode, policy.getFilterConfig());
        return sqlBuilder.buildFilterSql(entityCode, policy.getFilterConfig(), delegator);
    }

    private String compileDelegatedCondition(
            String entityCode,
            String config,
            SysUser delegator) {
        if (!StringUtils.hasText(config)) {
            return null;
        }
        try {
            FilterConfigDTO filter = objectMapper.readValue(config, FilterConfigDTO.class);
            sqlBuilder.validateFilter(entityCode, filter);
            return sqlBuilder.buildFilterSql(entityCode, filter, delegator);
        } catch (Exception exception) {
            throw new IllegalArgumentException("委托条件配置损坏", exception);
        }
    }

    private String userRelation(String column, SysUser user) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (StringUtils.hasText(user.getId())) {
            values.add(user.getId());
        }
        if (StringUtils.hasText(user.getUsername())) {
            values.add(user.getUsername());
        }
        if (values.isEmpty()) {
            return "1=0";
        }
        return column + " IN ('" + values.stream()
                .map(sqlBuilder::escapeLiteral)
                .collect(java.util.stream.Collectors.joining("','")) + "')";
    }

    private boolean isEffective(
            EntityListScopeBindingDTO binding,
            LocalDateTime now) {
        return (binding.getEffectiveStartTime() == null
                || !binding.getEffectiveStartTime().isAfter(now))
                && (binding.getEffectiveEndTime() == null
                || !binding.getEffectiveEndTime().isBefore(now));
    }

    private PermissionPreviewDTO.MatchedRuleDTO detail(
            EntityListScopePolicyDTO policy,
            EntityListScopeBindingDTO binding,
            String sql) {
        PermissionPreviewDTO.MatchedRuleDTO detail =
                new PermissionPreviewDTO.MatchedRuleDTO();
        detail.setRuleName(policy.getPolicyName());
        detail.setRuleEffect(normalized(binding.getRuleEffect(), "ALLOW"));
        detail.setListKey(binding.getListKey());
        detail.setSql(sql);
        return detail;
    }

    private CalculationResult denied(
            String reason,
            Integer releaseVersion,
            List<PermissionPreviewDTO.MatchedRuleDTO> matched) {
        return denied(reason, releaseVersion, matched, "INHERIT");
    }

    private CalculationResult denied(
            String reason,
            Integer releaseVersion,
            List<PermissionPreviewDTO.MatchedRuleDTO> matched,
            String mode) {
        DataPermissionResult result = DataPermissionResult.denyAll();
        result.setReleaseVersion(releaseVersion);
        result.setDataScopeMode(mode);
        result.setExplanation(reason);
        result.setMatchedRuleNames(matched.stream()
                .map(PermissionPreviewDTO.MatchedRuleDTO::getRuleName)
                .toList());
        return new CalculationResult(result, matched);
    }

    private String explanation(
            String mode,
            List<String> entityAllows,
            List<String> listAllows,
            List<String> denies) {
        return switch (mode) {
            case "NARROW" -> "实体默认范围与列表范围取交集"
                    + (denies.isEmpty() ? "" : "，最后扣除拒绝范围");
            case "OVERRIDE" -> "使用列表独立范围"
                    + (denies.isEmpty() ? "" : "，最后扣除拒绝范围");
            default -> "继承实体默认范围"
                    + (denies.isEmpty() ? "" : "，最后扣除拒绝范围");
        };
    }

    private String or(List<String> parts) {
        List<String> valid = parts == null ? List.of() : parts.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (valid.isEmpty()) {
            return null;
        }
        if (valid.stream().anyMatch("1=1"::equals)) {
            return "1=1";
        }
        return valid.size() == 1
                ? valid.get(0)
                : valid.stream().map(value -> "(" + value + ")")
                .collect(java.util.stream.Collectors.joining(" OR "));
    }

    private String and(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return null;
        }
        if ("1=0".equals(left) || "1=0".equals(right)) {
            return "1=0";
        }
        if ("1=1".equals(left)) {
            return right;
        }
        if ("1=1".equals(right)) {
            return left;
        }
        return "(" + left + ") AND (" + right + ")";
    }

    private String orNonNull(String left, String right) {
        if (!StringUtils.hasText(left)) {
            return right;
        }
        if (!StringUtils.hasText(right)) {
            return left;
        }
        if ("1=1".equals(left) || "1=1".equals(right)) {
            return "1=1";
        }
        return "(" + left + ") OR (" + right + ")";
    }

    private String normalized(String value, String fallback) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : fallback;
    }

    private boolean hasBypass(String userId, String permission) {
        try {
            return PermissionUtil.getUserPermissions(userId).contains(permission);
        } catch (RuntimeException exception) {
            log.debug("权限上下文尚未初始化，按无绕过权限处理");
            return false;
        }
    }

    private record CalculationResult(
            DataPermissionResult result,
            List<PermissionPreviewDTO.MatchedRuleDTO> matchedRules) {
    }
}
