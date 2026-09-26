package com.workflow.entity.version.application;

import com.workflow.core.database.jdbc.JdbcWriteAttempt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.result.PageRequest;
import com.workflow.core.result.PageResult;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.version.application.model.EntityRecordVersionCapabilities;
import com.workflow.entity.version.application.model.EntityVersionConfigSummary;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import com.workflow.entity.version.application.model.EntityVersionValidationResult;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityVersionConfigMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityVersionRolloutBridgeMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionConfig;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionRolloutState;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 每个实体唯一的数据版本配置保存与运行时解析服务。
 */
@Service
@RequiredArgsConstructor
public class EntityVersionConfigurationService {

    private final EntityVersionConfigMapper configMapper;
    private final EntityVersionRolloutBridgeMapper rolloutBridgeMapper;
    private final EntityRecordVersionMapper recordVersionMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final ObjectMapper objectMapper;
    private final EntityVersionConfigurationValidator validator;
    private final EntityVersionScopeFreezer scopeFreezer;
    private final JdbcWriteAttempt writeAttempt;

    /**
     * 返回兼容旧客户端的完整配置摘要列表。
     *
     * @param keyword 实体名称或编码的模糊关键字，可为空
     * @return 匹配关键字的全部摘要，顺序稳定
     */
    @Transactional(readOnly = true)
    public List<EntityVersionConfigSummary> list(String keyword) {
        return List.copyOf(summaries(keyword, null));
    }

    /**
     * 按实体名称/编码和启用状态分页查询数据版本配置摘要。
     *
     * <p>{@code enabled=false} 遵循摘要字段的既有语义：除显式停用配置外，
     * 未配置实体和混部期间只有 legacy 草稿的占位行也归入未启用。分页参数沿用
     * 平台统一规范，页码最小为 1，每页大小限制在 1 到 100。</p>
     *
     * @param keyword 实体名称或编码的模糊关键字，可为空
     * @param enabled 启用状态；为空表示全部
     * @param requestedPageNum 请求页码
     * @param requestedPageSize 请求每页大小
     * @return 筛选后的分页摘要
     */
    @Transactional(readOnly = true)
    public PageResult<EntityVersionConfigSummary> listPage(
            String keyword,
            Boolean enabled,
            Integer requestedPageNum,
            Integer requestedPageSize) {
        PageRequest page = PageRequest.normalize(
                requestedPageNum, requestedPageSize, 20, 100);
        List<EntityVersionConfigSummary> result =
                summaries(keyword, enabled);
        int start = page.startIndex(result.size());
        int end = (int) Math.min(
                (long) start + page.pageSize(), result.size());
        return new PageResult<>(
                List.copyOf(result.subList(start, end)),
                result.size(),
                page.pageNumber(),
                page.pageSize());
    }

    /**
     * 两种列表契约共用批量读取和筛选逻辑，实体定义查询统一保证稳定顺序。
     *
     * @param keyword 关键字，作为 {@code text} 的输入影响后续处理
     * @param enabled 启用，供本方法处理{@code summaries}时使用
     * @return 实体版本配置摘要集合，供调用方遍历或展示
     */
    private List<EntityVersionConfigSummary> summaries(
            String keyword,
            Boolean enabled) {
        String normalizedKeyword = text(keyword);
        Map<String, EntityVersionConfig> configsByEntityCode =
                new LinkedHashMap<>();
        // 管理列表包含未配置实体，先批量建立当前配置索引，避免逐实体 N+1 查询。
        for (EntityVersionConfig config
                : configMapper.findAllForManagementList()) {
            if (config != null
                    && StringUtils.hasText(config.getEntityCode())) {
                // 数据库实体编码使用 CI collation；内存索引必须保持相同的
                // 大小写语义，否则历史 ASSET/asset 数据会被误判为未配置。
                configsByEntityCode.putIfAbsent(
                        entityCodeKey(config.getEntityCode()), config);
            }
        }
        List<EntityVersionConfigSummary> result = new ArrayList<>();
        for (EntityDefinition definition
                : definitionMapper.findAllWithFields()) {
            if (normalizedKeyword != null
                    && !containsIgnoreCase(
                            definition.getEntityCode(),
                            normalizedKeyword)
                    && !containsIgnoreCase(
                            definition.getEntityName(),
                            normalizedKeyword)) {
                continue;
            }
            EntityVersionConfig config = configsByEntityCode.get(
                    entityCodeKey(definition.getEntityCode()));
            EntityVersionConfiguration document = config != null
                    && StringUtils.hasText(config.getConfigDocument())
                    ? readConfiguration(config.getConfigDocument()) : null;
            int triggerCount = document == null
                    ? 0 : safe(document.getTriggers()).size();
            int scopeRelationCount = document == null
                    || document.getSnapshotScope() == null
                    ? 0 : (int) safe(document.getSnapshotScope().getRelations())
                            .stream()
                            .filter(item -> !Boolean.FALSE.equals(
                                    item.getEnabled()))
                            .count();
            // 混部期间旧 Pod 保存草稿会先改旧 enabled 列；Mapper 只让有效的
            // active release 覆盖 config_document，legacy 草稿始终不参与运行语义。
            boolean runtimeEnabled = document != null
                    && Boolean.TRUE.equals(document.getEnabled());
            if (enabled != null
                    && enabled.booleanValue() != runtimeEnabled) {
                continue;
            }
            result.add(new EntityVersionConfigSummary(
                    definition.getId(),
                    definition.getEntityCode(),
                    definition.getEntityName(),
                    runtimeEnabled,
                    config == null ? 0 : config.getRevision(),
                    runtimeEnabled,
                    triggerCount,
                    scopeRelationCount,
                    config == null ? null : config.getUpdateTime()));
        }
        return result;
    }

    /**
     * 读取实体版本配置；结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 符合条件的实体版本配置结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public EntityVersionConfiguration get(
            String entityCode) {
        EntityDefinition definition =
                requireDefinition(entityCode);
        EntityVersionConfig config =
                configMapper.findByEntityCode(entityCode);
        if (config == null) {
            return scopeFreezer.enrichManagementOptions(
                    defaultConfiguration(definition));
        }
        EntityVersionConfiguration result;
        if (StringUtils.hasText(config.getConfigDocument())) {
            result = readConfiguration(config.getConfigDocument());
            hydrateCurrentEnvelope(result, definition, config);
        } else {
            // expand 阶段旧 Pod 仍可能新建只有 draft_document 的行。它不是当前
            // 生效配置，但必须保留 id/revision 供新客户端用 If-Match 安全接管。
            result = defaultConfiguration(definition);
            hydrateCurrentEnvelope(result, definition, config);
            result.setEnabled(false);
        }
        return scopeFreezer.enrichManagementOptions(result);
    }

    /**
     * 读取当前生效配置。每次调用只解析当前行一次，调用方应把返回对象贯穿本次捕获。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 匹配的实体版本配置当前；未找到时为空
     */
    @Transactional(readOnly = true)
    public Optional<EntityVersionConfiguration> getCurrent(
            String entityCode) {
        EntityVersionConfig config =
                configMapper.findByEntityCode(entityCode);
        if (config == null
                || !StringUtils.hasText(config.getConfigDocument())) {
            return Optional.empty();
        }
        EntityVersionConfiguration document =
                readConfiguration(config.getConfigDocument());
        hydrateCurrentEnvelope(document, null, config);
        return Optional.of(document);
    }

    /**
     * 读取实体记录版本入口所需的运行时能力。
     *
     * <p>手工固化必须同时满足当前配置启用、V2 和 MANUAL 触发器约束；历史可读性
     * 独立计算，使策略停用后仍可从列表进入已有历史的只读抽屉。</p>
     *
     * @param entityCode 实体编码
     * @return 当前配置对应的运行时与历史读取能力
     */
    @Transactional(readOnly = true)
    public EntityRecordVersionCapabilities recordCapabilities(
            String entityCode) {
        boolean historyReadable = recordVersionMapper
                .existsByEntityCode(entityCode);
        Optional<EntityVersionConfiguration> current =
                getCurrent(entityCode);
        if (current.isEmpty()
                || !Boolean.TRUE.equals(current.get().getEnabled())) {
            return EntityRecordVersionCapabilities.disabled(
                    historyReadable);
        }
        EntityVersionConfiguration configuration = current.get();
        boolean manualCaptureEnabled =
                value(configuration.getSchemaVersion(), 1) >= 2
                        && safe(configuration.getTriggers()).stream()
                                .filter(Objects::nonNull)
                                .anyMatch(trigger ->
                                        !Boolean.FALSE.equals(
                                                trigger.getEnabled())
                                                && "MANUAL".equals(
                                                        trigger.getTriggerType()));
        return new EntityRecordVersionCapabilities(
                true, manualCaptureEnabled, historyReadable);
    }

    /**
     * 查找把指定子实体纳入 RELATED_MUTATION 触发范围的当前 V2 配置。
     *
     * @param childEntityCode 子级实体编码，后续用于查询当前关联{@code configurations}时定位或关联目标
     * @return 实体版本配置集合，供调用方遍历或展示
     */
    @Transactional(readOnly = true)
    public List<EntityVersionConfiguration> findCurrentRelatedConfigurations(
            String childEntityCode) {
        return findCurrentScopedConfigurations(childEntityCode).stream()
                .filter(document -> safe(document.getTriggers()).stream()
                        .anyMatch(trigger -> !Boolean.FALSE.equals(
                                trigger.getEnabled())
                                && "RELATED_MUTATION".equals(
                                        trigger.getTriggerType())))
                .toList();
    }

    /**
     * 查找把 B 纳入快照的根配置；即使不传播生成根版本，也用于 ROOT→…→B 锁序。
     *
     * @param childEntityCode 子级实体编码，后续用于查询当前{@code scoped}{@code configurations}时定位或关联目标
     * @return 实体版本配置集合，供调用方遍历或展示
     */
    @Transactional(readOnly = true)
    public List<EntityVersionConfiguration> findCurrentScopedConfigurations(
            String childEntityCode) {
        if (!StringUtils.hasText(childEntityCode)) {
            return List.of();
        }
        List<EntityVersionConfiguration> result = new ArrayList<>();
        for (EntityVersionConfig config : configMapper.findAllCurrent()) {
            if (config == null
                    || !StringUtils.hasText(config.getConfigDocument())) {
                continue;
            }
            EntityVersionConfiguration document =
                    readConfiguration(config.getConfigDocument());
            hydrateCurrentEnvelope(document, null, config);
            if (!Boolean.TRUE.equals(document.getEnabled())
                    || value(document.getSchemaVersion(), 1) < 2) {
                continue;
            }
            boolean relationMatches = safe(document.getSnapshotScope() == null
                    ? null : document.getSnapshotScope().getRelations())
                    .stream()
                    .anyMatch(scope -> !Boolean.FALSE.equals(scope.getEnabled())
                            && childEntityCode.equals(scope.getChildEntityCode()));
            if (relationMatches) {
                result.add(document);
            }
        }
        return result;
    }

    /**
     * 实体重新发布前校验：活动 V2 范围引用的组成关系不能从发布快照中消失。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param publishingRelationCodes {@code publishing}关系编码集合，供本方法校验并获取关系作用域兼容时使用
     */
    @Transactional(readOnly = true)
    public void requireRelationScopeCompatible(
            String entityCode,
            Collection<String> publishingRelationCodes) {
        List<EntityVersionConfiguration.RelationScope> frozenRelations =
                currentScopesUsingParent(entityCode);
        if (frozenRelations.isEmpty()) {
            return;
        }
        Set<String> available = new java.util.LinkedHashSet<>(
                publishingRelationCodes == null
                        ? List.of() : publishingRelationCodes);
        List<String> missing = frozenRelations.stream()
                .filter(item -> !Boolean.FALSE.equals(item.getEnabled()))
                .map(EntityVersionConfiguration.RelationScope::getRelationCode)
                .filter(StringUtils::hasText)
                .filter(code -> !available.contains(code))
                .distinct()
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessConflictException(
                    "ENTITY_VERSION_SCOPE_RELATION_REMOVED",
                    "实体发布会移除活动数据版本范围引用的关系: "
                            + String.join(",", missing)
                        + "；请先调整并保存数据版本配置");
        }
    }

    /**
     * 实体发布候选关系必须与活动范围冻结的选择器语义一致。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param publishingRelations {@code publishing}关系集合，供本方法校验并获取关系作用域{@code definitions}兼容时使用
     */
    @Transactional(readOnly = true)
    public void requireRelationScopeDefinitionsCompatible(
            String entityCode,
            Collection<EntityRelation> publishingRelations) {
        List<EntityVersionConfiguration.RelationScope> frozenRelations =
                currentScopesUsingParent(entityCode);
        if (frozenRelations.isEmpty()) {
            return;
        }
        Map<String, EntityRelation> candidates = new LinkedHashMap<>();
        for (EntityRelation relation : publishingRelations == null
                ? List.<EntityRelation>of() : publishingRelations) {
            if (relation != null
                    && !Boolean.FALSE.equals(relation.getEnabled())
                    && relation.getOwnershipType()
                            == EntityRelation.OwnershipType.COMPOSITION
                    && StringUtils.hasText(relation.getRelationCode())) {
                candidates.put(relation.getRelationCode(), relation);
            }
        }
        List<String> incompatible = new ArrayList<>();
        for (EntityVersionConfiguration.RelationScope frozen
                : frozenRelations) {
            if (Boolean.FALSE.equals(frozen.getEnabled())) {
                continue;
            }
            EntityRelation candidate = candidates.get(frozen.getRelationCode());
            if (candidate == null
                    || !Objects.equals(frozen.getChildEntityCode(),
                            candidate.getChildEntityCode())
                    || !Objects.equals(frozen.getChildRefFieldCode(),
                            candidate.getChildRefFieldCode())
                    || !Objects.equals(frozen.getRelationType(),
                            candidate.getRelationType() == null ? null
                                    : candidate.getRelationType().name())
                    || !Objects.equals(frozen.getDataKey(),
                            firstText(candidate.getDataKey(),
                                    candidate.getParentFieldCode(),
                                    candidate.getRelationCode()))) {
                incompatible.add(frozen.getRelationCode());
            }
        }
        if (!incompatible.isEmpty()) {
            throw new BusinessConflictException(
                    "ENTITY_VERSION_SCOPE_RELATION_INCOMPATIBLE",
                    "实体发布会改变活动数据版本范围的关系语义: "
                            + String.join(",", incompatible)
                        + "；请先调整并保存数据版本配置");
        }
    }

    /**
     * 查找所有以指定实体作为父节点的活动冻结关系。
     *
     * <p>多层版本策略的根配置属于另一个实体，因此不能只读
     * {@code getCurrent(entityCode)}。旧一层配置没有 parentEntityCode 时，仍用
     * ROOT + 配置根实体编码兼容识别。</p>
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 实体版本配置集合，供调用方遍历或展示
     */
    private List<EntityVersionConfiguration.RelationScope>
            currentScopesUsingParent(String entityCode) {
        if (!StringUtils.hasText(entityCode)) {
            return List.of();
        }
        Map<String, EntityVersionConfiguration> documents =
                new LinkedHashMap<>();
        getCurrent(entityCode)
                .filter(document -> Boolean.TRUE.equals(
                        document.getEnabled()))
                .ifPresent(document -> documents.put(
                        document.getId(), document));
        for (EntityVersionConfig config : configMapper.findAllCurrent()) {
            if (config == null
                    || !StringUtils.hasText(config.getConfigDocument())) {
                continue;
            }
            if (documents.containsKey(config.getId())) {
                continue;
            }
            EntityVersionConfiguration document =
                    readConfiguration(config.getConfigDocument());
            hydrateCurrentEnvelope(document, null, config);
            if (!Boolean.TRUE.equals(document.getEnabled())) {
                continue;
            }
            documents.put(config.getId(), document);
        }
        List<EntityVersionConfiguration.RelationScope> result =
                new ArrayList<>();
        for (EntityVersionConfiguration document : documents.values()) {
            if (value(document.getSchemaVersion(), 1) < 2
                    || document.getSnapshotScope() == null) {
                continue;
            }
            for (EntityVersionConfiguration.RelationScope relation
                    : safe(document.getSnapshotScope().getRelations())) {
                String parentNode = firstText(
                        relation.getParentNodeCode(), "ROOT");
                boolean directLegacy = !StringUtils.hasText(
                        relation.getParentEntityCode())
                        && "ROOT".equals(parentNode)
                        && entityCode.equals(document.getEntityCode());
                if (!Boolean.FALSE.equals(relation.getEnabled())
                        && (entityCode.equals(
                                relation.getParentEntityCode())
                                || directLegacy)) {
                    result.add(relation);
                }
            }
        }
        return result;
    }

    /**
     * 校验、冻结并以 CAS 原子保存当前配置，成功后立即参与运行时匹配。
     *
     * <p>冻结发生在任何配置写入之前；更新语句同时比较 revision，避免两个管理员
     * 基于同一旧配置相互覆盖。首次创建要求 {@code expectedRevision=0}。</p>
     *
     * @param entityCode 实体编码
     * @param request 候选配置
     * @param expectedRevision If-Match 携带的当前修订号
     * @return 已生效且带最新修订号的配置
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityVersionConfiguration save(
            String entityCode,
            EntityVersionConfiguration request,
            Integer expectedRevision) {
        EntityDefinition definition =
                requireDefinition(entityCode);
        EntityVersionConfiguration normalized =
                normalize(definition, request);
        validator.validate(normalized);
        EntityVersionConfiguration effective =
                scopeFreezer.freeze(normalized);
        validator.validate(effective);
        EntityVersionConfig current =
                configMapper.findByEntityCode(entityCode);
        EntityVersionConfig config = current == null
                ? new EntityVersionConfig() : current;
        LocalDateTime now = LocalDateTime.now();
        String userId = UserContext.getUserId();
        if (current == null) {
            if (expectedRevision == null || expectedRevision != 0) {
                throw revisionConflict(entityCode, 0, expectedRevision);
            }
            config.setId(id());
            config.setEntityId(definition.getId());
            config.setEntityCode(entityCode);
            config.setRevision(1);
            config.setCreateBy(userId);
            config.setCreateTime(now);
            config.setDeleted(0);
        } else {
            if (expectedRevision == null
                    || !expectedRevision.equals(current.getRevision())) {
                throw revisionConflict(
                        entityCode,
                        current.getRevision(),
                        expectedRevision);
            }
            config.setRevision(value(current.getRevision(), 0) + 1);
        }
        config.setEnabled(
                Boolean.TRUE.equals(effective.getEnabled()));
        config.setUpdateBy(userId);
        config.setUpdateTime(now);
        effective.setId(config.getId());
        effective.setRevision(config.getRevision());
        effective.setUpdateTime(now);
        config.setConfigDocument(write(storedDocument(effective)));
        if (current == null) {
            try {
                writeAttempt.execute(() -> configMapper.insert(config));
            } catch (DuplicateKeyException exception) {
                throw concurrentRevisionConflict(entityCode, expectedRevision);
            }
        } else {
            int updated = configMapper.updateCurrentIfRevision(
                    config.getId(),
                    expectedRevision,
                    config.getEnabled(),
                    config.getConfigDocument(),
                    userId);
            if (updated != 1) {
                throw concurrentRevisionConflict(entityCode, expectedRevision);
            }
        }
        syncRolloutBridge(config, effective);
        return scopeFreezer.enrichManagementOptions(effective);
    }

    /**
     * 读取旧页面正在编辑的 legacy 草稿，而不是当前 {@code config_document}。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 带旧 status/active release 信封的兼容 JSON；这些字段不进入新模型
     */
    @Transactional(readOnly = true)
    public Map<String, Object> legacyDraft(String entityCode) {
        EntityDefinition definition = requireDefinition(entityCode);
        EntityVersionRolloutState state = rolloutBridgeMapper
                .findStateByEntityCode(entityCode);
        if (state == null) {
            return legacyDraftResponse(
                    scopeFreezer.enrichManagementOptions(
                            defaultConfiguration(definition)),
                    "UNCONFIGURED",
                    null,
                    null);
        }
        String document = StringUtils.hasText(state.getDraftDocument())
                ? state.getDraftDocument() : state.getConfigDocument();
        EntityVersionConfiguration draft = StringUtils.hasText(document)
                ? readConfiguration(document)
                : defaultConfiguration(definition);
        hydrateLegacyDraftEnvelope(draft, definition, state);
        draft = scopeFreezer.enrichManagementOptions(draft);
        Integer activeReleaseVersion = StringUtils.hasText(
                state.getActiveReleaseId())
                ? rolloutBridgeMapper.findReleaseVersion(
                        state.getActiveReleaseId(), state.getId())
                : null;
        return legacyDraftResponse(
                draft,
                firstText(state.getStatus(), "DRAFT"),
                state.getActiveReleaseId(),
                activeReleaseVersion);
    }

    /**
     * 兼容旧 POST draft/save：只 CAS 保存 legacy 草稿，绝不提前改变当前运行配置。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param request 本次请求，后续经校验后用于保存旧版草稿
     * @param expectedRevision 预期修订版本，作为 {@code revisionConflict} 的输入影响后续处理
     * @return 旧版草稿键值结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveLegacyDraft(
            String entityCode,
            EntityVersionConfiguration request,
            Integer expectedRevision) {
        EntityDefinition definition = requireDefinition(entityCode);
        EntityVersionConfiguration normalized = normalize(definition, request);
        validator.validate(normalized);
        EntityVersionRolloutState state = rolloutBridgeMapper
                .findStateByEntityCode(entityCode);
        String document = write(storedDocument(normalized));
        String userId = UserContext.getUserId();
        if (state == null) {
            if (expectedRevision == null || expectedRevision != 0) {
                throw revisionConflict(entityCode, 0, expectedRevision);
            }
            try {
                int inserted = writeAttempt.execute(() -> rolloutBridgeMapper.insertLegacyDraft(
                        id(),
                        definition.getId(),
                        entityCode,
                        Boolean.TRUE.equals(normalized.getEnabled()),
                        value(normalized.getSchemaVersion(), 2),
                        document,
                        userId));
                if (inserted != 1) {
                    throw new IllegalStateException(
                            "数据版本兼容草稿创建失败: entity=" + entityCode);
                }
            } catch (DuplicateKeyException exception) {
                throw concurrentRevisionConflict(entityCode, expectedRevision);
            }
        } else {
            if (expectedRevision == null
                    || !expectedRevision.equals(state.getRevision())) {
                throw revisionConflict(
                        entityCode, state.getRevision(), expectedRevision);
            }
            int updated = rolloutBridgeMapper.updateLegacyDraftIfRevision(
                    state.getId(),
                    expectedRevision,
                    Boolean.TRUE.equals(normalized.getEnabled()),
                    value(normalized.getSchemaVersion(), 2),
                    document,
                    userId);
            if (updated != 1) {
                throw concurrentRevisionConflict(entityCode, expectedRevision);
            }
        }
        return legacyDraft(entityCode);
    }

    /**
     * 兼容旧 publish/releases：有待发布草稿时按新流程校验、冻结并接管；已经由
     * 新保存桥同步的状态则只做 revision 校验并幂等返回。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param expectedRevision 预期修订版本，供本方法发布旧版草稿时使用
     * @return 发布后的旧版草稿结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityVersionConfiguration publishLegacyDraft(
            String entityCode,
            Integer expectedRevision) {
        EntityVersionRolloutState state = rolloutBridgeMapper
                .findStateByEntityCode(entityCode);
        if (state == null) {
            throw new IllegalArgumentException("请先保存数据版本配置");
        }
        if (expectedRevision == null
                || !expectedRevision.equals(state.getRevision())) {
            throw revisionConflict(
                    entityCode, state.getRevision(), expectedRevision);
        }
        if ("PUBLISHED".equalsIgnoreCase(state.getStatus())
                && Objects.equals(
                        text(state.getDraftDocument()),
                        text(state.getConfigDocument()))) {
            return get(entityCode);
        }
        if (!StringUtils.hasText(state.getDraftDocument())) {
            throw new IllegalArgumentException("待接管的数据版本草稿不存在");
        }
        // save 内的唯一 CAS 再次校验 revision；并发旧/新保存只能有一个成功。
        return save(
                entityCode,
                readConfiguration(state.getDraftDocument()),
                expectedRevision);
    }

    /**
     * 给旧页面提供单条“当前配置”兼容分页，避免继续暴露已废弃的真实发布历史。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param requestedPageNum 请求页码，后续归一化并换算为数据库查询偏移
     * @param requestedPageSize 请求页大小，后续限制单次查询和返回数量
     * @return 处理后的旧版发布版本分页结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> legacyReleasePage(
            String entityCode,
            long requestedPageNum,
            long requestedPageSize) {
        long pageNum = Math.max(1, requestedPageNum);
        long pageSize = Math.max(1, Math.min(100, requestedPageSize));
        EntityVersionConfig config = configMapper.findByEntityCode(entityCode);
        if (config == null
                || !StringUtils.hasText(config.getConfigDocument())) {
            return new PageResult<>(List.of(), 0, pageNum, pageSize);
        }
        EntityVersionConfiguration document = readConfiguration(
                config.getConfigDocument());
        int relationCount = document.getSnapshotScope() == null
                ? 0 : (int) safe(document.getSnapshotScope().getRelations())
                        .stream()
                        .filter(item -> !Boolean.FALSE.equals(
                                item.getEnabled()))
                        .count();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", config.getId());
        summary.put("version", config.getRevision());
        summary.put("publishedBy", config.getUpdateBy());
        summary.put("publishedByName", config.getUpdateBy());
        summary.put("publishTime", config.getUpdateTime());
        summary.put("relationCount", relationCount);
        summary.put("scopeSummary", relationCount + " 个关联范围");
        return new PageResult<>(pageNum == 1 ? List.of(summary) : List.of(),
                1, pageNum, pageSize);
    }

    /**
     * 校验实体版本配置；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param request 本次请求，后续经校验后用于校验实体版本配置
     * @return 校验后的实体版本配置结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public EntityVersionValidationResult validate(
            String entityCode,
            EntityVersionConfiguration request) {
        EntityVersionConfiguration normalized = normalize(
                requireDefinition(entityCode), request);
        validator.validate(normalized);
        if (value(normalized.getSchemaVersion(), 1) >= 2) {
            scopeFreezer.freeze(normalized);
        }
        List<String> warnings = new ArrayList<>();
        if (normalized.getSnapshotScope() != null
                && safe(normalized.getSnapshotScope().getRelations()).isEmpty()) {
            warnings.add("当前只固化根实体，未选择任何组成关系");
        }
        return EntityVersionValidationResult.valid(warnings);
    }

    /**
     * 供范围预览使用；只解析和冻结，不保存。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param request 本次请求，后续经校验后用于解析候选人
     * @return 解析后的候选人结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public EntityVersionConfiguration resolveCandidate(
            String entityCode,
            EntityVersionConfiguration request) {
        EntityVersionConfiguration normalized = normalize(
                requireDefinition(entityCode), request);
        validator.validate(normalized);
        return value(normalized.getSchemaVersion(), 1) >= 2
                ? scopeFreezer.freeze(normalized) : normalized;
    }

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param definition 定义，作为 {@code source.setEntityId} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于规范化实体版本配置
     * @return 规范化后的实体版本配置结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntityVersionConfiguration normalize(
            EntityDefinition definition,
            EntityVersionConfiguration request) {
        EntityVersionConfiguration source = request == null
                ? new EntityVersionConfiguration() : request;
        if (value(source.getSchemaVersion(), 2) != 2
                || !safe(source.getScenarios()).isEmpty()) {
            throw new IllegalArgumentException(
                    "数据版本配置仅支持 V2");
        }
        source.setEntityId(definition.getId());
        source.setEntityCode(definition.getEntityCode());
        source.setEntityName(definition.getEntityName());
        source.setEnabled(
                Boolean.TRUE.equals(source.getEnabled()));
        source.setSchemaVersion(value(source.getSchemaVersion(), 2));
        source.setScenarios(new ArrayList<>());
        source.setTriggers(source.getTriggers() == null
                ? new ArrayList<>() : source.getTriggers());
        if (source.getSnapshotScope() == null) {
            source.setSnapshotScope(
                    new EntityVersionConfiguration.SnapshotScope());
        }
        if (source.getSnapshotScope().getRoot() == null) {
            source.getSnapshotScope().setRoot(
                    new EntityVersionConfiguration.ScopeNode());
        }
        EntityVersionConfiguration.ScopeNode root =
                source.getSnapshotScope().getRoot();
        root.setNodeCode("ROOT");
        root.setEntityCode(definition.getEntityCode());
        root.setEntityName(definition.getEntityName());
        normalizeNode(root);
        source.getSnapshotScope().setRelations(
                source.getSnapshotScope().getRelations() == null
                        ? new ArrayList<>()
                        : source.getSnapshotScope().getRelations());
        for (EntityVersionConfiguration.RelationScope relation
                : source.getSnapshotScope().getRelations()) {
            relation.setNodeCode(text(relation.getNodeCode()));
            relation.setParentNodeCode(firstText(
                    text(relation.getParentNodeCode()), "ROOT"));
            relation.setRelationCode(text(relation.getRelationCode()));
            relation.setEnabled(!Boolean.FALSE.equals(relation.getEnabled()));
            relation.setMaxRows(value(relation.getMaxRows(), 500));
            normalizeNode(relation);
            if (relation.getFilter() == null) {
                relation.setFilter(new EntityVersionConfiguration.FixedFilter());
            }
            relation.getFilter().setLogic(upper(
                    relation.getFilter().getLogic()));
            relation.getFilter().setConditions(
                    relation.getFilter().getConditions() == null
                            ? new ArrayList<>()
                            : relation.getFilter().getConditions());
            for (EntityVersionConfiguration.FilterCondition condition
                    : relation.getFilter().getConditions()) {
                condition.setFieldCode(text(condition.getFieldCode()));
                condition.setOperator(upper(condition.getOperator()));
            }
        }
        if (source.getSnapshotScope().getLimits() == null) {
            source.getSnapshotScope().setLimits(
                    new EntityVersionConfiguration.ScopeLimits());
        }
        if (source.getDiffPolicy() == null) {
            source.setDiffPolicy(new EntityVersionConfiguration.DiffPolicy());
        }
        source.getDiffPolicy().setIgnoredFieldCodes(
                source.getDiffPolicy().getIgnoredFieldCodes() == null
                        ? new ArrayList<>()
                        : source.getDiffPolicy().getIgnoredFieldCodes());
        for (EntityVersionConfiguration.CaptureTrigger trigger
                : source.getTriggers()) {
            trigger.setTriggerCode(upper(trigger.getTriggerCode()));
            trigger.setTriggerName(text(trigger.getTriggerName()));
            trigger.setTriggerType(upper(trigger.getTriggerType()));
            trigger.setRelationCode(text(trigger.getRelationCode()));
            trigger.setSourceTypes(normalizeList(trigger.getSourceTypes()));
            trigger.setOperationTypes(normalizeList(trigger.getOperationTypes()));
            trigger.setBusinessIntents(normalizeList(trigger.getBusinessIntents()));
            trigger.setCondition(trigger.getCondition() == null
                    ? new LinkedHashMap<>() : trigger.getCondition());
            trigger.setPriority(value(trigger.getPriority(), 0));
            trigger.setEnabled(!Boolean.FALSE.equals(trigger.getEnabled()));
        }
        return source;
    }

    /**
     * 处理默认配置，并将结果传给后续步骤。
     *
     * @param definition 定义，作为 {@code result.setEntityId} 的输入影响后续处理
     * @return 处理后的默认配置结果，供调用方继续处理
     */
    private EntityVersionConfiguration defaultConfiguration(
            EntityDefinition definition) {
        EntityVersionConfiguration result =
                new EntityVersionConfiguration();
        result.setEntityId(definition.getId());
        result.setEntityCode(definition.getEntityCode());
        result.setEntityName(definition.getEntityName());
        result.setEnabled(false);
        result.setRevision(0);
        result.setSchemaVersion(2);
        EntityVersionConfiguration.ScopeNode root =
                new EntityVersionConfiguration.ScopeNode();
        root.setEntityCode(definition.getEntityCode());
        root.setEntityName(definition.getEntityName());
        result.getSnapshotScope().setRoot(root);
        result.setTriggers(new ArrayList<>(List.of(
                trigger(
                        "INITIAL_EFFECTIVE",
                        "初始审批生效",
                        List.of("PROCESS_RUNTIME"),
                        List.of("STATUS_CHANGE"),
                        List.of("INITIAL_EFFECTIVE"),
                        200),
                trigger(
                        "CHANGE_EFFECTIVE",
                        "变更审批生效",
                        List.of(
                                "FLOW_ACTION",
                                "APPROVAL_TASK",
                                "CUSTOM_INTERFACE"),
                        List.of(
                                "APPLY_CHANGE",
                                "UPDATE"),
                        List.of("CHANGE_EFFECTIVE"),
                        100))));
        EntityVersionConfiguration.CaptureTrigger manual =
                new EntityVersionConfiguration.CaptureTrigger();
        manual.setTriggerCode("MANUAL_CHECKPOINT");
        manual.setTriggerName("手工固化");
        manual.setTriggerType("MANUAL");
        manual.setPriority(10);
        result.getTriggers().add(manual);
        return result;
    }

    /**
     * 把本次 CAS 保存的同一份文档投影到旧草稿，并创建新的旧 active release。
     *
     * <p>整个方法运行在 {@link #save(String, EntityVersionConfiguration, Integer)}
     * 的事务内；任一步失败都会回滚主配置 CAS，避免新旧运行时看到不同版本。</p>
     *
     * @param config 配置内容，决定后续同步灰度发布{@code bridge}的处理规则
     * @param effective 有效，作为 {@code value} 的输入影响后续处理
     */
    private void syncRolloutBridge(
            EntityVersionConfig config,
            EntityVersionConfiguration effective) {
        int contractVersion = value(effective.getSchemaVersion(), 2);
        String scopeHash = effective.getSnapshotScope() == null
                ? null : effective.getSnapshotScope().getScopeHash();
        String userId = UserContext.getUserId();
        String username = UserContext.getUsername();
        int draftSynced = rolloutBridgeMapper.syncLegacyDraft(
                config.getId(),
                config.getRevision(),
                contractVersion,
                config.getConfigDocument(),
                userId);
        if (draftSynced != 1) {
            throw new IllegalStateException(
                    "数据版本滚动兼容草稿同步失败: entity="
                            + config.getEntityCode());
        }
        // release 被历史版本引用，必须保持不可变；每次保存创建新快照再切 active。
        String compatibilityReleaseId = id();
        int releaseVersion = value(
                rolloutBridgeMapper.findNextReleaseVersion(config.getId()),
                1);
        int inserted = rolloutBridgeMapper.insertCompatibilityRelease(
                compatibilityReleaseId,
                config.getId(),
                releaseVersion,
                contractVersion,
                config.getConfigDocument(),
                scopeHash,
                userId,
                username);
        if (inserted != 1
                || rolloutBridgeMapper.activateCompatibilityRelease(
                        config.getId(),
                        config.getRevision(),
                        compatibilityReleaseId,
                        userId) != 1) {
            throw new IllegalStateException(
                    "数据版本滚动兼容发布同步失败: entity="
                            + config.getEntityCode());
        }
    }

    /**
     * 新实体默认提供两类根记录采集触发器，与独立变更规则分开维护。
     *
     * @param code 编码，后续用于处理触发条件时定位或关联目标
     * @param name 名称，后续用于处理触发条件时匹配或展示
     * @param sources {@code sources}，作为 {@code value.setSourceTypes} 的输入影响后续处理
     * @param operations 操作集合，作为 {@code value.setOperationTypes} 的输入影响后续处理
     * @param intents {@code intents}，作为 {@code value.setBusinessIntents} 的输入影响后续处理
     * @param priority 优先级，作为 {@code value.setPriority} 的输入影响后续处理
     * @return 处理后的触发条件结果，供调用方继续处理
     */
    private EntityVersionConfiguration.CaptureTrigger trigger(
            String code,
            String name,
            List<String> sources,
            List<String> operations,
            List<String> intents,
            int priority) {
        EntityVersionConfiguration.CaptureTrigger value =
                new EntityVersionConfiguration.CaptureTrigger();
        value.setTriggerCode(code);
        value.setTriggerName(name);
        value.setTriggerType("ROOT_MUTATION");
        value.setSourceTypes(sources);
        value.setOperationTypes(operations);
        value.setBusinessIntents(intents);
        value.setPriority(priority);
        value.setVersionTitleTemplate(
                "V${versionNo} ${triggerName}");
        return value;
    }

    /**
     * 处理{@code hydrate}当前{@code envelope}，并将结果传给后续步骤。
     *
     * @param document 文档，作为 {@code document.setEntityId} 的输入影响后续处理
     * @param definition 定义，作为 {@code document.setEntityId} 的输入影响后续处理
     * @param config 配置内容，决定后续{@code hydrate}当前{@code envelope}的处理规则
     */
    private void hydrateCurrentEnvelope(
            EntityVersionConfiguration document,
            EntityDefinition definition,
            EntityVersionConfig config) {
        document.setId(config.getId());
        document.setEntityId(definition == null
                ? firstText(document.getEntityId(), config.getEntityId())
                : definition.getId());
        document.setEntityCode(definition == null
                ? firstText(document.getEntityCode(), config.getEntityCode())
                : definition.getEntityCode());
        if (definition != null) {
            document.setEntityName(definition.getEntityName());
        }
        // expand 混部期间旧草稿会修改 legacy enabled；当前语义只认文档内开关。
        document.setEnabled(Boolean.TRUE.equals(document.getEnabled()));
        document.setSchemaVersion(value(document.getSchemaVersion(), 2));
        document.setRevision(config.getRevision());
        document.setUpdateTime(config.getUpdateTime());
    }

    /**
     * 旧草稿信封使用旧行 revision，但不把这些管理字段重新放回新模型。
     *
     * @param document 文档，作为 {@code document.setSchemaVersion} 的输入影响后续处理
     * @param definition 定义，作为 {@code document.setEntityId} 的输入影响后续处理
     * @param state 状态标识，决定后续{@code hydrate}旧版草稿{@code envelope}采用的处理分支
     */
    private void hydrateLegacyDraftEnvelope(
            EntityVersionConfiguration document,
            EntityDefinition definition,
            EntityVersionRolloutState state) {
        document.setId(state.getId());
        document.setEntityId(definition.getId());
        document.setEntityCode(definition.getEntityCode());
        document.setEntityName(definition.getEntityName());
        document.setEnabled(Boolean.TRUE.equals(state.getEnabled()));
        document.setSchemaVersion(value(document.getSchemaVersion(), 2));
        document.setRevision(state.getRevision());
        document.setUpdateTime(state.getUpdateTime());
    }

    /**
     * 把废弃信封字段限制在兼容响应中，避免污染新 GET/PUT 契约。
     *
     * @param draft 草稿，作为 {@code objectMapper.convertValue} 的输入影响后续处理
     * @param status 状态标识，决定后续旧版草稿响应采用的处理分支
     * @param activeReleaseId 活动发布版本ID，后续用于处理旧版草稿响应时定位或关联目标
     * @param activeReleaseVersion 活动发布版本，作为 {@code result.put} 的输入影响后续处理
     * @return 旧版草稿响应键值结果，供调用方继续处理
     */
    private Map<String, Object> legacyDraftResponse(
            EntityVersionConfiguration draft,
            String status,
            String activeReleaseId,
            Integer activeReleaseVersion) {
        Map<String, Object> result = objectMapper.convertValue(
                draft,
                new TypeReference<LinkedHashMap<String, Object>>() {
                });
        result.put("status", status);
        result.put("activeReleaseId", activeReleaseId);
        result.put("activeReleaseVersion", activeReleaseVersion);
        return result;
    }

    /**
     * 去掉仅供管理端选择的派生选项后持久化当前生效文档。
     *
     * @param source 待处理已存储文档的原始输入，结果供调用方继续使用
     * @return 处理后的已存储文档结果，供调用方继续处理
     */
    private EntityVersionConfiguration storedDocument(
            EntityVersionConfiguration source) {
        EntityVersionConfiguration result = objectMapper.convertValue(
                source, EntityVersionConfiguration.class);
        result.setRelationOptions(List.of());
        result.setFieldOptions(List.of());
        if (value(result.getSchemaVersion(), 1) >= 2) {
            result.setScenarios(List.of());
        }
        return result;
    }

    /**
     * 规范化节点；输出作为后续校验或处理的输入。
     *
     * @param node 节点，作为 {@code node.setFieldMode} 的输入影响后续处理
     */
    private void normalizeNode(
            EntityVersionConfiguration.ScopeNode node) {
        node.setFieldMode(upper(node.getFieldMode()));
        if (node.getFieldMode() == null) {
            node.setFieldMode("ALL_PUBLISHED");
        }
        node.setFieldCodes(node.getFieldCodes() == null
                ? new ArrayList<>()
                : node.getFieldCodes().stream()
                        .map(this::text)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .toList());
        node.setFields(new ArrayList<>());
    }

    /**
     * 写入已经输掉竞争，冲突提示必须来自当前读取；不再解析旧 release 文档。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param expectedRevision 预期修订版本，供本方法处理{@code concurrent}修订版本冲突时使用
     * @return 处理后的{@code concurrent}修订版本冲突结果，供调用方继续处理
     */
    private BusinessConflictException concurrentRevisionConflict(String entityCode, Integer expectedRevision) {
        return revisionConflict(entityCode,
                configMapper.findCurrentRevisionForConflict(entityCode), expectedRevision);
    }

    /**
     * 构造修订版本冲突异常，供调用方区分失败原因。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param currentRevision 当前修订版本，作为 {@code BusinessConflictException} 的输入影响后续处理
     * @param expectedRevision 预期修订版本，供本方法处理修订版本冲突时使用
     * @return 处理后的修订版本冲突结果，供调用方继续处理
     */
    private BusinessConflictException revisionConflict(
            String entityCode,
            Integer currentRevision,
            Integer expectedRevision) {
        return new BusinessConflictException(
                "ENTITY_VERSION_CONFIG_REVISION_CONFLICT",
                "数据版本配置已被更新: entity=" + entityCode
                        + ", currentRevision=" + currentRevision
                        + ", expectedRevision=" + expectedRevision);
    }

    /**
     * 校验并获取定义；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 校验并获取后的定义结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntityDefinition requireDefinition(
            String entityCode) {
        if (!StringUtils.hasText(entityCode)) {
            throw new IllegalArgumentException(
                    "实体编码不能为空");
        }
        return definitionMapper.findByEntityCode(
                        entityCode.trim())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "实体不存在: " + entityCode));
    }

    /**
     * 读取配置；查询结果供调用方展示或继续处理。
     *
     * @param document 文档，作为 {@code objectMapper.readValue} 的输入影响后续处理
     * @return 读取后的配置结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private EntityVersionConfiguration readConfiguration(
            String document) {
        try {
            return objectMapper.readValue(
                    document,
                    EntityVersionConfiguration.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "实体版本配置解析失败",
                    exception);
        }
    }

    /**
     * 写入实体版本配置；后续读取或执行将使用更新后的状态。
     *
     * @param value 待写入实体版本配置的原始输入，结果供调用方继续使用
     * @return 写入后的实体版本配置文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(
                    value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "实体版本配置无法序列化",
                    exception);
        }
    }

    /**
     * 规范化实体版本配置列表；输出作为后续校验或处理的输入。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 实体版本配置集合，供调用方遍历或展示
     */
    private List<String> normalizeList(
            List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
                .map(this::upper)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    /**
     * 生成{@code upper}文本，供后续匹配或展示。
     *
     * @param value 待处理{@code upper}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code upper}文本，供调用方比较或展示
     */
    private String upper(String value) {
        String normalized = text(value);
        return normalized == null
                ? null
                : normalized.toUpperCase(Locale.ROOT);
    }

    /**
     * 生成实体编码键文本，供后续匹配或展示。
     *
     * @param value 待处理实体编码键的原始输入，结果供调用方继续使用
     * @return 处理后的实体编码键文本，供调用方比较或展示
     */
    private String entityCodeKey(String value) {
        String normalized = text(value);
        return normalized == null
                ? null
                : normalized.toLowerCase(Locale.ROOT);
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 读取或规范化输入值，供后续计算与比较使用。
     *
     * @param value 待处理值的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的值结果，供调用方继续处理
     */
    private int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    /**
     * 判断是否包含{@code ignore}分支；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否包含{@code ignore}分支的原始输入，结果供调用方继续使用
     * @param keyword 关键字，供本方法判断是否包含{@code ignore}分支时使用
     * @return {@code ignore}分支条件成立时为 true，否则为 false
     */
    private boolean containsIgnoreCase(
            String value,
            String keyword) {
        return value != null
                && value.toLowerCase(Locale.ROOT)
                .contains(keyword.toLowerCase(Locale.ROOT));
    }

    /**
     * 生成ID文本，供后续匹配或展示。
     *
     * @return 处理后的ID文本，供调用方比较或展示
     */
    private String id() {
        return UUID.randomUUID().toString()
                .replace("-", "");
    }

    /**
     * 整理安全数据，供调用方遍历或继续处理。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 实体版本配置集合，供调用方遍历或展示
     */
    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
