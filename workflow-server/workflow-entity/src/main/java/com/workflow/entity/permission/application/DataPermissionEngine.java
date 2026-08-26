package com.workflow.entity.permission.application;

import com.workflow.core.logging.LogValue;
import com.workflow.entity.permission.api.response.DataPermissionResult;
import com.workflow.entity.permission.api.response.EntityListScopeBindingDTO;
import com.workflow.entity.permission.api.response.EntityListScopeDefaultDTO;
import com.workflow.entity.permission.api.response.EntityListScopePolicyDTO;
import com.workflow.entity.permission.api.response.EntityListScopeSnapshotDTO;
import com.workflow.entity.permission.api.response.FilterConfigDTO;
import com.workflow.entity.permission.api.response.PermissionPreviewDTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.entity.permission.infrastructure.persistence.record.EntityListScopeDelegation;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.permission.infrastructure.persistence.mapper.EntityListScopeDelegationMapper;
import com.workflow.admin.identity.user.application.SysUserService;
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

    /**
     * 计算实体级数据权限结果（不区分列表）。
     *
     * @param entityCode 实体编码
     * @param user       当前用户
     * @return 数据权限结果，包含是否授权、SQL 条件等
     */
    public DataPermissionResult calculatePermission(String entityCode, SysUser user) {
        return calculatePermission(entityCode, null, user);
    }

    /**
     * 计算指定列表的数据权限结果。
     *
     * @param entityCode 实体编码
     * @param listKey    列表编码，为空按实体默认范围处理
     * @param user       当前用户
     * @return 数据权限结果
     */
    public DataPermissionResult calculatePermission(
            String entityCode,
            String listKey,
            SysUser user) {
        CalculationResult calculation = calculate(entityCode, listKey, user);
        return calculation.result();
    }

    /**
     * 计算指定列表权限，并结构化标识当前用户是否实际命中显式 ALLOW 绑定。
     *
     * <p>关系图等高风险内部入口不能把 legacy OBSERVE、未绑定默认策略、
     * 委托范围或仅命中 DENY 误认为显式授权，因此不能只检查最终 SQL 或说明
     * 文案。普通列表查询继续使用 {@link #calculatePermission}。</p>
     *
     * @param entityCode 实体编码
     * @param listKey    服务端固定的列表或内部入口键
     * @param user       已认证用户
     * @return 最终权限和显式 ALLOW 命中标识
     */
    public ExplicitListPermission calculateExplicitListPermission(
            String entityCode,
            String listKey,
            SysUser user) {
        CalculationResult calculation = calculate(entityCode, listKey, user);
        boolean explicitAllow = calculation.matchedRules().stream()
                .anyMatch(rule -> rule != null
                        && !"DENY".equalsIgnoreCase(rule.getRuleEffect()));
        return new ExplicitListPermission(
                calculation.result(), explicitAllow);
    }

    /**
     * 预览数据权限计算详情，包含匹配到的规则、SQL 条件和说明。
     *
     * @param entityCode 实体编码
     * @param listKey    列表编码
     * @param user       当前用户
     * @return 权限预览 DTO
     */
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

    /**
     * 核心权限计算逻辑：只认当前列表上的绑定；
     * listKey 为空的旧实体默认绑定不参与；无 ALLOW 时执行列表的安全默认策略，再扣除本列表 DENY。
     * 实体级 team 开关不再叠加，相关人只走 TEAM 规则绑定。
     */
    private CalculationResult calculate(
            String entityCode,
            String listKey,
            SysUser user) {
        if (user == null || !StringUtils.hasText(user.getId())) {
            return denied("当前用户不存在", null, List.of());
        }

        // 检查是否拥有绕过数据范围的显式权限
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
            log.error("读取数据范围发布快照失败: entityCode={}, failureType={}",
                    LogValue.safe(entityCode), LogValue.failureType(exception));
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
        List<String> listAllows = new ArrayList<>();
        List<String> denies = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        boolean hasAllowBinding = false;

        for (EntityListScopeBindingDTO binding : snapshot.getBindings()) {
            if (binding == null || !Integer.valueOf(1).equals(binding.getEnabled())
                    || !isEffective(binding, now)
                    || !StringUtils.hasText(binding.getListKey())
                    || !binding.getListKey().equals(listKey)) {
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
                hasAllowBinding = true;
                continue;
            }
            if (!"DENY".equalsIgnoreCase(binding.getRuleEffect())) {
                hasAllowBinding = true;
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
                } else {
                    hasAllowBinding = true;
                    listAllows.add(sql);
                }
            } catch (RuntimeException exception) {
                if ("DENY".equalsIgnoreCase(binding.getRuleEffect())) {
                    log.error("DENY 数据范围方案无效，按拒绝全部处理: policyKey={}, failureType={}",
                            LogValue.safe(policy.getPolicyKey()), LogValue.failureType(exception));
                    return denied(
                            "拒绝方案配置损坏: " + policy.getPolicyName(),
                            snapshot.getVersion(),
                            matched);
                }
                log.error("ALLOW 数据范围方案无效，按不授权处理: policyKey={}, failureType={}",
                        LogValue.safe(policy.getPolicyKey()), LogValue.failureType(exception));
            }
        }

        boolean unboundAllow = !hasAllowBinding;
        UnboundDecision unboundDecision = unboundAllow
                ? resolveUnboundDecision(entityCode, listKey, snapshot, user)
                : null;
        String allow = unboundAllow ? unboundDecision.sql() : or(listAllows);
        String delegatedAllow = buildDelegatedAllow(
                entityCode, snapshot, policyMap, user);
        allow = orNonNull(allow, delegatedAllow);
        if (!StringUtils.hasText(allow)) {
            return denied(
                    unboundAllow
                            ? unboundDecision.explanation()
                            : "没有匹配到任何允许数据范围",
                    snapshot.getVersion(),
                    matched,
                    mode);
        }

        String deny = or(denies);
        String finalSql = StringUtils.hasText(deny)
                ? "(" + allow + ") AND NOT (" + deny + ")"
                : allow;
        if ("1=0".equals(allow) || "1=1".equals(deny)) {
            return denied(
                    "数据被拒绝方案全部排除",
                    snapshot.getVersion(),
                    matched,
                    mode);
        }

        DataPermissionResult result = "1=1".equals(finalSql)
                ? DataPermissionResult.allowAll()
                : DataPermissionResult.withCondition(finalSql, Map.of());
        result.setMatchedRuleNames(matched.stream()
                .map(PermissionPreviewDTO.MatchedRuleDTO::getRuleName)
                .toList());
        result.setReleaseVersion(snapshot.getVersion());
        result.setDataScopeMode(mode);
        result.setExplanation(explanation(
                unboundAllow,
                denies,
                unboundDecision == null ? null : unboundDecision.explanation()));
        return new CalculationResult(result, matched);
    }

    /**
     * 解析未绑定 ALLOW 规则时的安全策略。旧快照仅在观察期兼容放行并写审计；
     * 新快照缺少列表配置一律拒绝，防止未知入口退化为全量访问。
     */
    private UnboundDecision resolveUnboundDecision(
            String entityCode,
            String listKey,
            EntityListScopeSnapshotDTO snapshot,
            SysUser user) {
        EntityListScopeDefaultDTO configured = StringUtils.hasText(listKey)
                && snapshot.getListDefaults() != null
                ? snapshot.getListDefaults().get(listKey)
                : null;
        boolean legacySnapshot = snapshot.getSecureDefaultsVersion() == null;
        String enforcement = configured == null
                ? (legacySnapshot ? "OBSERVE" : "ENFORCE")
                : normalized(configured.getEnforcementMode(), "ENFORCE");
        String policy = configured == null
                ? (legacySnapshot ? "EXPLICIT_ALL" : "DENY_ALL")
                : normalized(configured.getUnboundPolicy(), "DENY_ALL");

        if ("OBSERVE".equals(enforcement)) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("unboundPolicy", policy);
            detail.put("releaseVersion", snapshot.getVersion());
            detail.put("legacySnapshot", legacySnapshot);
            auditService.record(
                    entityCode,
                    listKey,
                    user.getId(),
                    "UNBOUND_SCOPE_OBSERVE",
                    "WARNING",
                    detail);
            return new UnboundDecision(
                    "1=1",
                    "存量观察期：未绑定允许规则，暂时保持全部可见并已记录审计");
        }
        if ("PERSONAL".equals(policy)) {
            return new UnboundDecision(
                    or(List.of(
                            userRelation("create_by", user),
                            userRelation("submitter_id", user))),
                    "未绑定允许规则，仅可见本人创建或提交的数据");
        }
        if ("EXPLICIT_ALL".equals(policy)
                && configured != null
                && Boolean.TRUE.equals(configured.getConfirmed())) {
            return new UnboundDecision(
                    "1=1",
                    "未绑定允许规则，使用管理员已确认的全量可见策略");
        }
        return new UnboundDecision(
                null,
                "未绑定允许规则，安全默认策略拒绝全部数据");
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
                log.error("数据范围委托配置无效，已忽略: delegationId={}, failureType={}",
                        LogValue.safe(delegation.getId()), LogValue.failureType(exception));
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
            boolean unboundAllow,
            List<String> denies,
            String unboundExplanation) {
        if (unboundAllow) {
            return (StringUtils.hasText(unboundExplanation)
                    ? unboundExplanation : "未绑定允许规则，默认拒绝")
                    + (denies.isEmpty() ? "" : "，最后扣除拒绝范围");
        }
        return "使用本列表绑定的允许规则"
                + (denies.isEmpty() ? "" : "，最后扣除拒绝范围");
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

    /** 高风险只读入口使用的结构化列表权限证据。 */
    public record ExplicitListPermission(
            DataPermissionResult permission,
            boolean explicitAllowMatched) {
    }

    /** 未绑定规则决策结果；sql 为空表示拒绝全部。 */
    private record UnboundDecision(String sql, String explanation) {
    }
}
