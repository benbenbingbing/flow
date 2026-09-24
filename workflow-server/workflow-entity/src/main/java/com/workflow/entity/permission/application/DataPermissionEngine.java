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
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 计算后的数据权限{@code engine}结果，供调用方继续处理
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
        // 所有规则共享参数命名空间，合并 SQL 时保留各自的绑定值。
        Map<String, Object> sqlParameters = new LinkedHashMap<>();
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
                        user,
                        sqlParameters);
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
                ? resolveUnboundDecision(entityCode, listKey, snapshot, user, sqlParameters)
                : null;
        String allow = unboundAllow ? unboundDecision.sql() : or(listAllows);
        String delegatedAllow = buildDelegatedAllow(
                entityCode, snapshot, policyMap, user, sqlParameters);
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
                : DataPermissionResult.withCondition(finalSql, sqlParameters);
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
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param snapshot 快照，作为 {@code detail.put} 的输入影响后续处理
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param sqlParameters SQL参数集合，作为 {@code UnboundDecision} 的输入影响后续处理
     * @return 解析后的{@code unbound}决策结果，供调用方继续处理
     */
    private UnboundDecision resolveUnboundDecision(
            String entityCode,
            String listKey,
            EntityListScopeSnapshotDTO snapshot,
            SysUser user, Map<String, Object> sqlParameters) {
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
                            personalFilter("PERSONAL", user, sqlParameters),
                            personalFilter("SUBMITTER", user, sqlParameters))),
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

    /**
     * 构建委托允许；结果供后续流程传递或持久化。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param snapshot 快照，供本方法构建委托允许时使用
     * @param policyMap 策略映射，作为 {@code compileDelegatedPolicy} 的输入影响后续处理
     * @param recipient {@code recipient}，作为 {@code delegationMapper.findActiveByToUserId} 的输入影响后续处理
     * @param sqlParameters SQL参数集合，作为 {@code personalFilter} 的输入影响后续处理
     * @return 构建后的委托允许文本，供调用方比较或展示
     */
    private String buildDelegatedAllow(
            String entityCode,
            EntityListScopeSnapshotDTO snapshot,
            Map<String, EntityListScopePolicyDTO> policyMap,
            SysUser recipient,
            Map<String, Object> sqlParameters) {
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
                    case "CREATED" -> personalFilter("PERSONAL", delegator, sqlParameters);
                    case "SUBMITTED" -> personalFilter("SUBMITTER", delegator, sqlParameters);
                    case "CURRENT_TASK" -> personalFilter("CURRENT_ASSIGNEE", delegator, sqlParameters);
                    case "POLICY" -> compileDelegatedPolicy(
                            entityCode, policyMap.get(delegation.getPolicyId()), delegator, sqlParameters);
                    case "CONDITION" -> compileDelegatedCondition(
                            entityCode, delegation.getDelegateConfig(), delegator, sqlParameters);
                    default -> "(" + personalFilter("PERSONAL", delegator, sqlParameters)
                            + ") OR (" + personalFilter("SUBMITTER", delegator, sqlParameters) + ")";
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

    /**
     * 编译委托策略；结果供调用方的后续步骤使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param policy 策略内容，决定后续委托策略的处理规则
     * @param delegator {@code delegator}，供本方法编译委托策略时使用
     * @param sqlParameters SQL参数集合，供本方法编译委托策略时使用
     * @return 编译后的委托策略文本，供调用方比较或展示
     */
    private String compileDelegatedPolicy(
            String entityCode,
            EntityListScopePolicyDTO policy,
            SysUser delegator,
            Map<String, Object> sqlParameters) {
        if (policy == null || !Integer.valueOf(1).equals(policy.getEnabled())) {
            return null;
        }
        sqlBuilder.validateFilter(entityCode, policy.getFilterConfig());
        return sqlBuilder.buildFilterSql(entityCode, policy.getFilterConfig(), delegator, sqlParameters);
    }

    /**
     * 编译委托条件；结果供调用方的后续步骤使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param config 配置内容，决定后续委托条件的处理规则
     * @param delegator {@code delegator}，作为 {@code sqlBuilder.buildFilterSql} 的输入影响后续处理
     * @param sqlParameters SQL参数集合，作为 {@code sqlBuilder.buildFilterSql} 的输入影响后续处理
     * @return 编译后的委托条件文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String compileDelegatedCondition(
            String entityCode,
            String config,
            SysUser delegator,
            Map<String, Object> sqlParameters) {
        if (!StringUtils.hasText(config)) {
            return null;
        }
        try {
            FilterConfigDTO filter = objectMapper.readValue(config, FilterConfigDTO.class);
            sqlBuilder.validateFilter(entityCode, filter);
            return sqlBuilder.buildFilterSql(entityCode, filter, delegator, sqlParameters);
        } catch (Exception exception) {
            throw new IllegalArgumentException("委托条件配置损坏", exception);
        }
    }

    /**
     * 本人兜底与委托范围复用标准编译器，与显式允许/拒绝规则共享绑定参数。
     *
     * @param type 类型标识，决定后续{@code personal}过滤采用的处理分支
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param parameters 参数集合，作为 {@code sqlBuilder.buildFilterSql} 的输入影响后续处理
     * @return 处理后的{@code personal}过滤文本，供调用方比较或展示
     */
    private String personalFilter(String type, SysUser user, Map<String, Object> parameters) {
        FilterConfigDTO filter = new FilterConfigDTO();
        filter.setType(type);
        return sqlBuilder.buildFilterSql(null, filter, user, parameters);
    }

    /**
     * 判断是否有效；判断结果决定调用方的后续分支。
     *
     * @param binding 绑定，供本方法判断是否有效时使用
     * @param now 当前时间，供本方法判断是否有效时使用
     * @return 有效条件成立时为 true，否则为 false
     */
    private boolean isEffective(
            EntityListScopeBindingDTO binding,
            LocalDateTime now) {
        return (binding.getEffectiveStartTime() == null
                || !binding.getEffectiveStartTime().isAfter(now))
                && (binding.getEffectiveEndTime() == null
                || !binding.getEffectiveEndTime().isBefore(now));
    }

    /**
     * 处理详情，并将结果传给后续步骤。
     *
     * @param policy 策略内容，决定后续详情的处理规则
     * @param binding 绑定，作为 {@code detail.setRuleEffect} 的输入影响后续处理
     * @param sql SQL，作为 {@code detail.setSql} 的输入影响后续处理
     * @return 处理后的详情结果，供调用方继续处理
     */
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

    /**
     * 处理已拒绝，并将结果传给后续步骤。
     *
     * @param reason 原因，供本方法处理已拒绝时使用
     * @param releaseVersion 发布版本，供本方法处理已拒绝时使用
     * @param matched {@code matched}，供本方法处理已拒绝时使用
     * @return 处理后的已拒绝结果，供调用方继续处理
     */
    private CalculationResult denied(
            String reason,
            Integer releaseVersion,
            List<PermissionPreviewDTO.MatchedRuleDTO> matched) {
        return denied(reason, releaseVersion, matched, "INHERIT");
    }

    /**
     * 处理已拒绝，并将结果传给后续步骤。
     *
     * @param reason 原因，作为 {@code result.setExplanation} 的输入影响后续处理
     * @param releaseVersion 发布版本，作为 {@code result.setReleaseVersion} 的输入影响后续处理
     * @param matched {@code matched}，作为 {@code result.setMatchedRuleNames} 的输入影响后续处理
     * @param mode 模式标识，决定后续已拒绝采用的处理分支
     * @return 处理后的已拒绝结果，供调用方继续处理
     */
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

    /**
     * 生成{@code explanation}文本，供后续匹配或展示。
     *
     * @param unboundAllow {@code unbound}允许，供本方法处理{@code explanation}时使用
     * @param denies {@code denies}，供本方法处理{@code explanation}时使用
     * @param unboundExplanation {@code unbound}{@code explanation}，供本方法处理{@code explanation}时使用
     * @return 处理后的{@code explanation}文本，供调用方比较或展示
     */
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

    /**
     * 生成或文本，供后续匹配或展示。
     *
     * @param parts {@code parts}，供本方法处理或时使用
     * @return 处理后的或文本，供调用方比较或展示
     */
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

    /**
     * 生成或非空值文本，供后续匹配或展示。
     *
     * @param left 左侧，供本方法处理或非空值时使用
     * @param right 右侧，作为 {@code OR} 的输入影响后续处理
     * @return 处理后的或非空值文本，供调用方比较或展示
     */
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

    /**
     * 生成规范化文本，供后续匹配或展示。
     *
     * @param value 待处理规范化的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的规范化文本，供调用方比较或展示
     */
    private String normalized(String value, String fallback) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : fallback;
    }

    /**
     * 判断是否具有{@code bypass}；判断结果决定调用方的后续分支。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param permission 数据访问权限，后续与查询条件合并以限制可见记录
     * @return {@code bypass}条件成立时为 true，否则为 false
     */
    private boolean hasBypass(String userId, String permission) {
        try {
            return PermissionUtil.getUserPermissions(userId).contains(permission);
        } catch (RuntimeException exception) {
            log.debug("权限上下文尚未初始化，按无绕过权限处理");
            return false;
        }
    }

    /**
     * 封装{@code calculation}的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param result 结果，保存在对象中供后续校验、查询或展示
     * @param matchedRules {@code matched}规则集合，保存在对象中供后续校验、查询或展示
     */
    private record CalculationResult(
            DataPermissionResult result,
            List<PermissionPreviewDTO.MatchedRuleDTO> matchedRules) {
    }

    /**
     * 高风险只读入口使用的结构化列表权限证据。
     *
     * @param permission 数据访问权限，后续与查询条件合并以限制可见记录
     * @param explicitAllowMatched {@code explicit}允许{@code matched}，保存在对象中供后续校验、查询或展示
     */
    public record ExplicitListPermission(
            DataPermissionResult permission,
            boolean explicitAllowMatched) {
    }

    /**
     * 未绑定规则决策结果；sql 为空表示拒绝全部。
     *
     * @param sql SQL，保存在对象中供后续校验、查询或展示
     * @param explanation {@code explanation}，保存在对象中供后续校验、查询或展示
     */
    private record UnboundDecision(String sql, String explanation) {
    }
}
