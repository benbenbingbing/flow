package com.workflow.service.permission;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.UserContext;
import com.workflow.dto.permission.*;
import com.workflow.entity.*;
import com.workflow.mapper.*;
import com.workflow.service.EntityDefinitionAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 数据范围草稿、发布快照和回滚的统一服务。
 */
@Service
@RequiredArgsConstructor
public class EntityListScopeService {

    private static final Pattern KEY_PATTERN =
            Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,99}");
    private static final Set<String> EFFECTS = Set.of("ALLOW", "DENY");
    private static final Set<String> LIST_MODES =
            Set.of("INHERIT", "NARROW", "OVERRIDE");

    private final EntityListScopePolicyMapper policyMapper;
    private final EntityListScopeBindingMapper bindingMapper;
    private final EntityListScopeReleaseMapper releaseMapper;
    private final EntityListConfigMapper listConfigMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final PermissionSqlBuilder sqlBuilder;
    private final PermissionRuleMatcher ruleMatcher;
    private final ObjectMapper objectMapper;
    private final EntityListScopeAuditService auditService;
    private final EntityDefinitionAccessPolicy entityAccessPolicy;

    @Transactional(readOnly = true)
    public EntityListScopeConfigurationDTO getConfiguration(String entityCode) {
        requireEntity(entityCode);
        EntityListScopeConfigurationDTO result = new EntityListScopeConfigurationDTO();
        result.setEntityCode(entityCode);
        EntityListScopeRelease active = releaseMapper.findActive(entityCode);
        result.setActiveVersion(active == null ? null : active.getVersion());
        result.setPolicies(policyMapper.findByEntityCode(entityCode).stream()
                .map(this::toPolicyDTO)
                .toList());
        result.setBindings(bindingMapper.findByEntityCode(entityCode).stream()
                .map(this::toBindingDTO)
                .toList());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityListScopePolicyDTO savePolicy(
            String id,
            EntityListScopePolicyDTO request) {
        if (request == null || !StringUtils.hasText(request.getEntityCode())) {
            throw new IllegalArgumentException("实体编码不能为空");
        }
        requireEntity(request.getEntityCode());
        if (!StringUtils.hasText(request.getPolicyKey())
                || !KEY_PATTERN.matcher(request.getPolicyKey()).matches()) {
            throw new IllegalArgumentException("方案编码必须以字母开头，只能包含字母、数字、下划线和短横线");
        }
        if (!StringUtils.hasText(request.getPolicyName())) {
            throw new IllegalArgumentException("方案名称不能为空");
        }
        FilterConfigDTO filter = request.getFilterConfig();
        if (filter == null) {
            throw new IllegalArgumentException("数据范围条件不能为空");
        }
        sqlBuilder.validateFilter(request.getEntityCode(), filter);

        EntityListScopePolicy duplicate = policyMapper.selectOne(
                new LambdaQueryWrapper<EntityListScopePolicy>()
                        .eq(EntityListScopePolicy::getEntityCode, request.getEntityCode())
                        .eq(EntityListScopePolicy::getPolicyKey, request.getPolicyKey())
                        .eq(EntityListScopePolicy::getDeleted, 0)
                        .ne(StringUtils.hasText(id), EntityListScopePolicy::getId, id)
                        .last("LIMIT 1"));
        if (duplicate != null) {
            throw new IllegalArgumentException("方案编码已存在: " + request.getPolicyKey());
        }

        EntityListScopePolicy policy = StringUtils.hasText(id)
                ? policyMapper.selectById(id)
                : new EntityListScopePolicy();
        if (StringUtils.hasText(id) && policy == null) {
            throw new IllegalArgumentException("数据范围方案不存在");
        }
        if (policy != null && StringUtils.hasText(policy.getEntityCode())
                && !policy.getEntityCode().equals(request.getEntityCode())) {
            throw new IllegalArgumentException("不能修改方案所属实体");
        }
        policy.setEntityCode(request.getEntityCode());
        policy.setPolicyKey(request.getPolicyKey().trim());
        policy.setPolicyName(request.getPolicyName().trim());
        policy.setDescription(request.getDescription());
        policy.setPresetCode(request.getPresetCode());
        policy.setFilterConfig(writeJson(filter));
        policy.setStatus("DRAFT");
        policy.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        policy.setVersion((policy.getVersion() == null ? 0 : policy.getVersion()) + 1);
        policy.setReviewRequired(0);
        policy.setUpdatedAt(LocalDateTime.now());
        if (!StringUtils.hasText(id)) {
            policy.setCreatedBy(UserContext.getUserId());
            policy.setCreatedAt(LocalDateTime.now());
            policy.setDeleted(0);
            policyMapper.insert(policy);
        } else {
            policyMapper.updateById(policy);
        }
        auditService.record(
                policy.getEntityCode(), null, UserContext.getUserId(),
                "SAVE", "SUCCESS", Map.of("policyKey", policy.getPolicyKey()));
        return toPolicyDTO(policyMapper.selectById(policy.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityListScopeBindingDTO saveBinding(
            String id,
            EntityListScopeBindingDTO request) {
        if (request == null || !StringUtils.hasText(request.getEntityCode())) {
            throw new IllegalArgumentException("实体编码不能为空");
        }
        requireEntity(request.getEntityCode());
        EntityListScopePolicy policy = policyMapper.selectById(request.getPolicyId());
        if (policy == null || !request.getEntityCode().equals(policy.getEntityCode())) {
            throw new IllegalArgumentException("数据范围方案不存在或不属于当前实体");
        }
        if (StringUtils.hasText(request.getListKey())
                && listConfigMapper.findByEntityCodeAndListKey(
                request.getEntityCode(), request.getListKey()) == null) {
            throw new IllegalArgumentException("适用列表不存在: " + request.getListKey());
        }
        MatchConfigDTO match = request.getMatchConfig();
        validateMatchConfig(match);
        String effect = normalized(request.getRuleEffect(), "ALLOW");
        if (!EFFECTS.contains(effect)) {
            throw new IllegalArgumentException("规则效果只能是 ALLOW 或 DENY");
        }
        if (request.getEffectiveStartTime() != null
                && request.getEffectiveEndTime() != null
                && request.getEffectiveEndTime().isBefore(request.getEffectiveStartTime())) {
            throw new IllegalArgumentException("失效时间不能早于生效时间");
        }

        EntityListScopeBinding binding = StringUtils.hasText(id)
                ? bindingMapper.selectById(id)
                : new EntityListScopeBinding();
        if (StringUtils.hasText(id) && binding == null) {
            throw new IllegalArgumentException("数据范围绑定不存在");
        }
        binding.setEntityCode(request.getEntityCode());
        binding.setPolicyId(request.getPolicyId());
        binding.setListKey(StringUtils.hasText(request.getListKey())
                ? request.getListKey().trim() : null);
        binding.setMatchConfig(writeJson(match));
        binding.setRuleEffect(effect);
        binding.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        binding.setEffectiveStartTime(request.getEffectiveStartTime());
        binding.setEffectiveEndTime(request.getEffectiveEndTime());
        binding.setUpdatedAt(LocalDateTime.now());
        if (!StringUtils.hasText(id)) {
            binding.setCreatedBy(UserContext.getUserId());
            binding.setCreatedAt(LocalDateTime.now());
            binding.setDeleted(0);
            bindingMapper.insert(binding);
        } else {
            bindingMapper.updateById(binding);
        }
        auditService.record(
                binding.getEntityCode(), binding.getListKey(), UserContext.getUserId(),
                "SAVE", "SUCCESS", Map.of("policyId", binding.getPolicyId()));
        return toBindingDTO(bindingMapper.selectById(binding.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deletePolicy(String id) {
        EntityListScopePolicy policy = policyMapper.selectById(id);
        if (policy == null) {
            return;
        }
        long bindingCount = bindingMapper.selectCount(
                new LambdaQueryWrapper<EntityListScopeBinding>()
                        .eq(EntityListScopeBinding::getPolicyId, id)
                        .eq(EntityListScopeBinding::getDeleted, 0));
        if (bindingCount > 0) {
            throw new IllegalStateException("方案仍被适用对象绑定引用，不能删除");
        }
        policyMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteBinding(String id) {
        bindingMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityListScopeRelease publish(String entityCode, String description) {
        requireEntity(entityCode);
        List<EntityListScopePolicy> policies = policyMapper.findByEntityCode(entityCode);
        List<EntityListScopeBinding> bindings = bindingMapper.findByEntityCode(entityCode);
        if (policies.stream().anyMatch(policy -> Integer.valueOf(1).equals(policy.getReviewRequired()))) {
            throw new EntityListScopeManualReviewRequiredException(
                    "存在需要人工确认的历史复杂规则，请重新保存方案后再发布");
        }
        if (bindings.stream().noneMatch(binding ->
                binding.getListKey() == null
                        && "ALLOW".equalsIgnoreCase(binding.getRuleEffect())
                        && Integer.valueOf(1).equals(binding.getEnabled()))) {
            throw new IllegalStateException("必须至少配置一个实体默认 ALLOW 数据范围");
        }

        EntityListScopeSnapshotDTO snapshot = new EntityListScopeSnapshotDTO();
        snapshot.setEntityCode(entityCode);
        int version = releaseMapper.findMaxVersion(entityCode) + 1;
        snapshot.setVersion(version);
        snapshot.setPolicies(policies.stream().map(this::toPolicyDTO).toList());
        snapshot.setBindings(bindings.stream().map(this::toBindingDTO).toList());
        for (EntityListConfig config : listConfigMapper.findByEntityCode(entityCode)) {
            String mode = normalized(config.getDataScopeMode(), "INHERIT");
            if (!LIST_MODES.contains(mode)) {
                throw new IllegalStateException("列表数据范围模式无效: " + config.getListKey());
            }
            if ("NARROW".equals(mode) && bindings.stream().noneMatch(binding ->
                    config.getListKey().equals(binding.getListKey())
                            && "ALLOW".equalsIgnoreCase(binding.getRuleEffect())
                            && Integer.valueOf(1).equals(binding.getEnabled()))) {
                throw new IllegalStateException(
                        "缩小范围列表必须至少配置一个列表级 ALLOW 绑定: " + config.getListKey());
            }
            snapshot.getListModes().put(config.getListKey(), mode);
            config.setPublishedVersion(version);
            listConfigMapper.updateById(config);
        }

        String snapshotJson = writeJson(snapshot);
        releaseMapper.deactivate(entityCode);
        EntityListScopeRelease release = new EntityListScopeRelease();
        release.setEntityCode(entityCode);
        release.setVersion(version);
        release.setSnapshotJson(snapshotJson);
        release.setContentHash(sha256(snapshotJson));
        release.setStatus("ACTIVE");
        release.setDescription(description);
        release.setPublishedBy(UserContext.getUserId());
        release.setPublishedAt(LocalDateTime.now());
        releaseMapper.insert(release);

        for (EntityListScopePolicy policy : policies) {
            policy.setStatus("PUBLISHED");
            policyMapper.updateById(policy);
        }
        auditService.record(
                entityCode, null, UserContext.getUserId(), "PUBLISH", "SUCCESS",
                Map.of("version", version, "contentHash", release.getContentHash()));
        return release;
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityListScopeRelease activateRelease(String entityCode, int version) {
        EntityListScopeRelease release = releaseMapper.selectOne(
                new LambdaQueryWrapper<EntityListScopeRelease>()
                        .eq(EntityListScopeRelease::getEntityCode, entityCode)
                        .eq(EntityListScopeRelease::getVersion, version)
                        .last("LIMIT 1"));
        if (release == null) {
            throw new IllegalArgumentException("数据范围发布版本不存在");
        }
        releaseMapper.deactivate(entityCode);
        release.setStatus("ACTIVE");
        releaseMapper.updateById(release);
        auditService.record(
                entityCode, null, UserContext.getUserId(), "ROLLBACK", "SUCCESS",
                Map.of("version", version));
        return release;
    }

    @Transactional(readOnly = true)
    public EntityListScopeSnapshotDTO getActiveSnapshot(String entityCode) {
        EntityListScopeRelease release = releaseMapper.findActive(entityCode);
        if (release == null || !StringUtils.hasText(release.getSnapshotJson())) {
            return null;
        }
        try {
            return objectMapper.readValue(
                    release.getSnapshotJson(),
                    EntityListScopeSnapshotDTO.class);
        } catch (Exception exception) {
            throw new IllegalStateException("数据范围发布快照损坏: " + entityCode, exception);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void ensureDefaultAndRelease(String entityCode) {
        requireEntity(entityCode);
        if (releaseMapper.findActive(entityCode) != null) {
            return;
        }
        List<EntityListScopePolicy> existing = policyMapper.findByEntityCode(entityCode);
        if (existing.isEmpty()) {
            FilterConfigDTO filter = defaultPersonalFilter();
            EntityListScopePolicyDTO policyRequest = new EntityListScopePolicyDTO();
            policyRequest.setEntityCode(entityCode);
            policyRequest.setPolicyKey("default_personal");
            policyRequest.setPolicyName("本人创建或提交的数据");
            policyRequest.setDescription("系统为新实体生成的默认安全范围");
            policyRequest.setPresetCode("PERSONAL_OR_SUBMITTER");
            policyRequest.setFilterConfig(filter);
            policyRequest.setEnabled(1);
            EntityListScopePolicyDTO policy = savePolicy(null, policyRequest);

            EntityListScopeBindingDTO binding = new EntityListScopeBindingDTO();
            binding.setEntityCode(entityCode);
            binding.setPolicyId(policy.getId());
            binding.setRuleEffect("ALLOW");
            binding.setEnabled(1);
            binding.setMatchConfig(allUsersMatch());
            saveBinding(null, binding);
        }
        publish(entityCode, "系统初始化数据范围");
    }

    private EntityListScopePolicyDTO toPolicyDTO(EntityListScopePolicy policy) {
        EntityListScopePolicyDTO dto = new EntityListScopePolicyDTO();
        BeanUtils.copyProperties(policy, dto);
        dto.setFilterConfig(readJson(policy.getFilterConfig(), FilterConfigDTO.class));
        return dto;
    }

    private EntityListScopeBindingDTO toBindingDTO(EntityListScopeBinding binding) {
        EntityListScopeBindingDTO dto = new EntityListScopeBindingDTO();
        BeanUtils.copyProperties(binding, dto);
        dto.setMatchConfig(readJson(binding.getMatchConfig(), MatchConfigDTO.class));
        return dto;
    }

    private void validateMatchConfig(MatchConfigDTO match) {
        if (match == null) {
            throw new IllegalArgumentException("适用用户配置不能为空");
        }
        if (match.getRoot() == null
                && (match.getConditions() == null || match.getConditions().isEmpty())) {
            throw new IllegalArgumentException("至少配置一个适用用户条件");
        }
        ruleMatcher.validate(match);
    }

    private FilterConfigDTO defaultPersonalFilter() {
        EntityActionRuleDTO.RuleNode root = new EntityActionRuleDTO.RuleNode();
        root.setType("GROUP");
        root.setLogic("OR");
        EntityActionRuleDTO.RuleNode creator = new EntityActionRuleDTO.RuleNode();
        creator.setType("RELATION");
        creator.setRelation("CURRENT_USER_IS_CREATOR");
        EntityActionRuleDTO.RuleNode submitter = new EntityActionRuleDTO.RuleNode();
        submitter.setType("RELATION");
        submitter.setRelation("CURRENT_USER_IS_SUBMITTER");
        root.setChildren(List.of(creator, submitter));
        FilterConfigDTO filter = new FilterConfigDTO();
        filter.setType("RULE");
        filter.setRoot(root);
        return filter;
    }

    private MatchConfigDTO allUsersMatch() {
        MatchConfigDTO match = new MatchConfigDTO();
        MatchConfigDTO.MatchConditionDTO condition =
                new MatchConfigDTO.MatchConditionDTO();
        condition.setScopeType("ALL_USERS");
        match.setConditions(List.of(condition));
        return match;
    }

    private void requireEntity(String entityCode) {
        entityAccessPolicy.requireDynamicByCode(entityCode);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("配置序列化失败", exception);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception exception) {
            throw new IllegalStateException("数据范围配置损坏", exception);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("计算数据范围快照哈希失败", exception);
        }
    }

    private String normalized(String value, String fallback) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : fallback;
    }
}
