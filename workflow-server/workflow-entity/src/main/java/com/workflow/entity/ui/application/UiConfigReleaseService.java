package com.workflow.entity.ui.application;

import com.workflow.core.logging.LogValue;
import com.workflow.entity.form.application.EntityFormNodeService;
import com.workflow.entity.form.application.EntityFormActionConfigPolicy;
import com.workflow.entity.form.application.EntityFormService;
import com.workflow.entity.form.application.FormSubmissionExecutionContext;
import com.workflow.entity.form.application.FormSubmissionTraceService;
import com.workflow.entity.form.application.ResolvedEntityFormRelease;
import com.workflow.entity.form.application.validation.EntityFormConfigurationValidator;
import com.workflow.entity.list.application.EntityListConfigService;
import com.workflow.entity.permission.application.EntityListActionConfigService;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.error.RevisionConflictException;
import com.workflow.core.result.PageResult;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.contracts.migration.model.ConfigMigrationPublishRequest;
import com.workflow.contracts.migration.port.MigrationAssetPort;
import com.workflow.contracts.embed.runtime.context.EmbedDelegatedRequestContext;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.AuditEventIds;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditResult;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.model.AuditSourcePointer;
import com.workflow.contracts.audit.context.OperationContext;
import com.workflow.contracts.audit.context.OperationContextHolder;
import com.workflow.contracts.audit.model.SystemAuditEvent;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.contracts.entity.ui.model.UiHotfixProcessImpact;
import com.workflow.contracts.entity.ui.port.UiHotfixProcessImpactPort;
import com.workflow.contracts.entity.ui.model.UiHotfixProcessTarget;
import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;
import com.workflow.contracts.entity.ui.model.UiRuntimePurpose;
import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import com.workflow.contracts.entity.ui.model.UiPublishedFormReference;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.ui.api.response.UiConfigDiffDTO;
import com.workflow.entity.ui.api.response.UiConfigDiffItemDTO;
import com.workflow.entity.ui.api.response.UiConfigDraftDiscardResultDTO;
import com.workflow.entity.ui.api.response.UiConfigActivationPreviewDTO;
import com.workflow.entity.ui.api.response.UiConfigHotfixRiskItemDTO;
import com.workflow.entity.ui.api.response.UiConfigHotfixTargetPreviewDTO;
import com.workflow.entity.ui.api.response.UiConfigPublishPreviewDTO;
import com.workflow.entity.ui.api.response.UiConfigReleaseSummaryDTO;
import com.workflow.entity.ui.api.request.UiConfigPublishRequest;
import com.workflow.entity.ui.api.request.UiConfigDraftDiscardRequest;
import com.workflow.entity.ui.api.model.UiConfigSemanticPatchOperation;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.entity.ui.infrastructure.persistence.record.UiComponentTemplate;
import com.workflow.entity.ui.infrastructure.persistence.record.UiComponentTemplateVersion;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigHotfixTarget;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigReleaseAudit;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigHotfixTargetMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseAuditMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiComponentTemplateMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiComponentTemplateVersionMapper;
import com.workflow.entity.list.application.validation.EntityListConfigurationValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * UI 配置发布服务，负责表单与列表草稿的快照构建、发布、激活、差异比对与运行时解析。
 *
 * <p>发布时构建草稿快照并校验节点树、模板引用、扩展引用和数据源引用，
 * 计算内容哈希保证完整性；支持版本激活回滚、草稿与发布版本差异比对，
 * 以及运行时表单/列表发布版本的解析与完整性校验。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UiConfigReleaseService {

    /** 表单配置类型。 */
    public static final String FORM = "FORM";
    /** 列表配置类型。 */
    public static final String LIST = "LIST";
    /** 普通发布模式。 */
    public static final String STANDARD = "STANDARD";
    /** 兼容热修复发布模式。 */
    public static final String HOTFIX = "HOTFIX";
    /** 首期热修复固定生效范围。 */
    public static final String ACTIVE_AND_FUTURE = "ACTIVE_AND_FUTURE";
    private static final String HOTFIX_PATCH = "PATCH";
    private static final String HOTFIX_FULL_SNAPSHOT = "FULL_SNAPSHOT";
    private static final int MAX_FORM_DEPTH = 8;
    private static final Set<String> FORM_NODE_TYPES = Set.of(
            "SECTION", "GRID", "TAB_SET", "TAB", "COLLAPSE",
            "TEXT", "FIELD", "SUB_FORM", "REPEATER", "ACTION_SLOT");
    private static final Set<String> FORM_CONTAINER_TYPES = Set.of(
            "SECTION", "GRID", "TAB_SET", "TAB", "COLLAPSE",
            "SUB_FORM", "REPEATER");
    private static final Set<String> STANDARD_CONTAINER_CHILD_TYPES = Set.of(
            "SECTION", "GRID", "TAB_SET", "COLLAPSE",
            "TEXT", "FIELD", "SUB_FORM", "REPEATER", "ACTION_SLOT");
    private static final Map<String, Set<String>> ALLOWED_CHILD_TYPES = Map.of(
            "SECTION", STANDARD_CONTAINER_CHILD_TYPES,
            "GRID", STANDARD_CONTAINER_CHILD_TYPES,
            "TAB_SET", Set.of("TAB"),
            "TAB", STANDARD_CONTAINER_CHILD_TYPES,
            "COLLAPSE", STANDARD_CONTAINER_CHILD_TYPES,
            "SUB_FORM", STANDARD_CONTAINER_CHILD_TYPES,
            "REPEATER", STANDARD_CONTAINER_CHILD_TYPES);
    private final UiConfigReleaseMapper releaseMapper;
    private final UiConfigHotfixTargetMapper hotfixTargetMapper;
    private final UiConfigReleaseAuditMapper releaseAuditMapper;
    private final UiConfigInterfaceReferenceValidator dataSourceValidator;
    private final UiEventBindingSnapshotService eventBindingSnapshotService;
    private final UiConfigSnapshotSupport snapshotSupport;
    private final UiComponentTemplateMapper templateMapper;
    private final UiComponentTemplateVersionMapper templateVersionMapper;
    private final EntityFormMapper formMapper;
    private final EntityListConfigMapper listConfigMapper;
    private final EntityDefinitionMapper entityDefinitionMapper;
    private final EntityFormService formService;
    private final EntityFormNodeService formNodeService;
    private final EntityFormConfigurationValidator formConfigurationValidator;
    private final UiExtensionDefinitionService extensionDefinitionService;
    private final EntityListConfigService listConfigService;
    private final EntityListConfigurationValidator listConfigurationValidator;
    private final UiConfigSemanticPatchService semanticPatchService;
    private final UiHotfixProcessImpactPort processImpactPort;
    private final UiConfigurationAccessService configurationAccessService;
    private final UiReleaseResolutionTokenService resolutionTokenService;
    private final FormSubmissionTraceService traceService;
    private final JsonDocumentCodec codec;
    private final ObjectMapper objectMapper;
    private final MigrationAssetPort migrationAssetHandler;
    private EntityListActionConfigService listActionConfigService;
    private UiHotfixGovernanceService hotfixGovernanceService;
    private UiViewCompositionService viewCompositionService;
    private SystemAuditPort auditPort;

    /**
     * 治理服务采用 setter 注入，避免改变大量纯单元测试的显式构造签名；
     * 生产 Spring 容器中该依赖为必需，HOTFIX 发布缺失时会 fail-closed。
     *
     * @param hotfixGovernanceService 热修复治理服务，供本方法设置热修复治理服务时使用
     */
    @Autowired
    void setHotfixGovernanceService(
            UiHotfixGovernanceService hotfixGovernanceService) {
        this.hotfixGovernanceService = hotfixGovernanceService;
    }

    /**
     * 注入列表按钮规则校验与发布规范化服务。
     *
     * @param listActionConfigService 列表动作配置服务，供本方法设置列表动作配置服务时使用
     */
    @Autowired
    void setListActionConfigService(
            EntityListActionConfigService listActionConfigService) {
        this.listActionConfigService = listActionConfigService;
    }

    /**
     * 关联内容是宿主草稿的一部分，但使用独立表保存。采用 setter 注入以兼容
     * 现有显式构造的单元测试；生产环境由 Spring 注入后参与完整发布生命周期。
     *
     * @param viewCompositionService 视图组合服务，供本方法设置视图组合服务时使用
     */
    @Autowired
    void setViewCompositionService(
            @Lazy UiViewCompositionService viewCompositionService) {
        this.viewCompositionService = viewCompositionService;
    }

    /**
     * 统一审计通过 setter 注入以保持已有纯单元测试的显式构造签名稳定。
     * 生产容器中该端口由 workflow-admin 提供。
     *
     * @param auditPort 审计端口，供本方法设置审计端口时使用
     */
    @Autowired
    void setAuditPort(SystemAuditPort auditPort) {
        this.auditPort = auditPort;
    }

    /**
     * 查询指定配置的所有发布历史记录。
     *
     * @param configType 配置类型（FORM 或 LIST）
     * @param configId   配置ID
     * @return 发布记录列表
     */
    public List<UiConfigRelease> releases(String configType, String configId) {
        requireType(configType);
        List<UiConfigRelease> releases =
                releaseMapper.findReleases(configType, configId);
        releases.stream()
                .filter(release -> HOTFIX.equals(
                        release.getReleaseMode()))
                .forEach(release -> release.setRolloutStatus(
                        resolveRolloutStatus(release)));
        return FORM.equals(normalize(configType))
                ? releases.stream()
                        .map(this::formManagementRelease)
                        .toList()
                : releases;
    }

    /**
     * FORM 发布管理响应的最小脱敏边界。
     *
     * <p>完整操作快照只用于服务端执行与完整性验证；仅具备表单配置权限的用户
     * 不一定具备接口服务查看权限，因此出站副本从整个 FORM 快照树递归移除
     * {@code executableSnapshot}，并不返回可能携带同一配置的语义补丁。这样也覆盖
     * 历史扩展把 Provider 快照嵌入 viewCompositions 等位置的情况；数据库实体和
     * LIST 既有响应保持不变。</p>
     *
     * @param configType 配置类型标识，决定后续管理发布版本采用的处理分支
     * @param release 发布版本，供本方法处理管理发布版本时使用
     * @return 处理后的管理发布版本结果，供调用方继续处理
     */
    private UiConfigRelease managementRelease(
            String configType,
            UiConfigRelease release) {
        return release == null || !FORM.equals(normalize(configType))
                ? release : formManagementRelease(release);
    }

    /**
     * 处理表单管理发布版本，并将结果传给后续步骤。
     *
     * @param release 发布版本，作为 {@code objectMapper.convertValue} 的输入影响后续处理
     * @return 处理后的表单管理发布版本结果，供调用方继续处理
     */
    private UiConfigRelease formManagementRelease(UiConfigRelease release) {
        UiConfigRelease result = objectMapper.convertValue(
                release, UiConfigRelease.class);
        result.setPatchDocument(null);
        if (!StringUtils.hasText(release.getSnapshotDocument())) {
            return result;
        }
        try {
            Map<String, Object> snapshot = codec.readObject(
                    release.getSnapshotDocument(),
                    "表单发布管理快照");
            removeExecutableSnapshot(snapshot);
            result.setSnapshotDocument(codec.write(
                    snapshot, "表单发布管理脱敏快照"));
        } catch (RuntimeException exception) {
            // 旧损坏快照也不能因管理查询而泄露原始 Provider 配置。
            result.setSnapshotDocument(null);
            log.warn(
                    "表单发布管理快照脱敏失败，已隐藏快照文档: releaseId={}, failureType={}",
                    LogValue.safe(release.getId()),
                    LogValue.failureType(exception));
        }
        return result;
    }

    /**
     * 移除{@code executable}快照；后续读取或执行将使用更新后的状态。
     *
     * @param value 待移除{@code executable}快照的原始输入，结果供调用方继续使用
     */
    @SuppressWarnings("unchecked")
    private void removeExecutableSnapshot(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<Object, Object> map = (Map<Object, Object>) raw;
            map.remove("executableSnapshot");
            new ArrayList<>(map.values())
                    .forEach(this::removeExecutableSnapshot);
            return;
        }
        if (value instanceof Collection<?> collection) {
            collection.forEach(this::removeExecutableSnapshot);
        }
    }

    /**
     * 分页查询发布历史摘要，避免历史页一次传输全部快照文档。
     *
     * @param configType 配置类型标识，决定后续发布版本{@code summaries}采用的处理分支
     * @param configId 配置ID，后续用于处理发布版本{@code summaries}时定位或关联目标
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @return 处理后的发布版本{@code summaries}结果，供调用方继续处理
     */
    public PageResult<UiConfigReleaseSummaryDTO> releaseSummaries(
            String configType,
            String configId,
            long pageNum,
            int pageSize) {
        requireType(configType);
        long safePageNum = Math.max(1, pageNum);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        List<UiConfigReleaseSummaryDTO> records =
                releaseMapper.findReleaseSummaries(
                        configType,
                        configId,
                        (safePageNum - 1) * safePageSize,
                        safePageSize);
        return new PageResult<>(
                records,
                releaseMapper.countReleases(configType, configId),
                safePageNum,
                safePageSize);
    }

    /**
     * 解析灰度发布状态；输出作为后续校验或处理的输入。
     *
     * @param release 发布版本，供本方法解析灰度发布状态时使用
     * @return 解析后的灰度发布状态文本，供调用方比较或展示
     */
    private String resolveRolloutStatus(UiConfigRelease release) {
        return resolveRolloutStatus(
                release.getId(),
                release.getStatus());
    }

    /**
     * 解析灰度发布状态；输出作为后续校验或处理的输入。
     *
     * @param releaseId 发布版本ID，后续用于解析灰度发布状态时定位或关联目标
     * @param status 状态标识，决定后续灰度发布状态采用的处理分支
     * @return 解析后的灰度发布状态文本，供调用方比较或展示
     */
    private String resolveRolloutStatus(
            String releaseId,
            String status) {
        List<UiConfigHotfixTarget> targets =
                hotfixTargetMapper.findByHotfixReleaseId(
                        releaseId);
        if (targets.stream().anyMatch(target ->
                "ACTIVE".equals(target.getStatus()))) {
            return "ACTIVE";
        }
        if (targets.stream().anyMatch(target ->
                "SUPERSEDED".equals(target.getStatus()))) {
            return "SUPERSEDED";
        }
        if (targets.stream().anyMatch(target ->
                "ROLLED_BACK".equals(target.getStatus()))
                || hasRollbackAudit(releaseId)) {
            return "ROLLED_BACK";
        }
        return "ACTIVE".equals(status)
                ? "ACTIVE" : "SUPERSEDED";
    }

    /**
     * 判断是否具有回滚审计；判断结果决定调用方的后续分支。
     *
     * @param releaseId 发布版本ID，后续用于判断是否具有回滚审计时定位或关联目标
     * @return 回滚审计条件成立时为 true，否则为 false
     */
    private boolean hasRollbackAudit(String releaseId) {
        Long count = releaseAuditMapper.selectCount(
                new LambdaQueryWrapper<UiConfigReleaseAudit>()
                        .eq(
                                UiConfigReleaseAudit::getReleaseId,
                                releaseId)
                        .eq(
                                UiConfigReleaseAudit::getOperation,
                                "ROLLBACK_HOTFIX"));
        return count != null && count > 0;
    }

    /**
     * 查询指定配置当前激活的发布记录。
     *
     * @param configType 配置类型
     * @param configId   配置ID
     * @return 激活的发布记录，不存在返回 null
     */
    public UiConfigRelease active(String configType, String configId) {
        requireType(configType);
        return releaseMapper.findActive(configType, configId);
    }

    /**
     * 读取当前激活发布版本的快照 Map。
     *
     * @param configType 配置类型
     * @param configId   配置ID
     * @return 快照 Map，不存在激活版本返回 null
     */
    public Map<String, Object> activeSnapshot(String configType, String configId) {
        UiConfigRelease release = active(configType, configId);
        return release == null
                ? null
                : verifiedSnapshot(release);
    }

    /**
     * 解析表单运行时发布版本，返回发布元信息与已校验快照文档。
     *
     * @param formId          表单ID
     * @param releaseId       发布记录ID，为空取当前激活版本
     * @param expectedVersion 期望版本号，为空跳过校验
     * @return 包含 id、configId、version、contentHash、snapshotDocument 的 Map
     * @throws IllegalArgumentException 发布版本不存在或版本号不一致时抛出
     */
    public Map<String, Object> runtimeFormRelease(
            String formId,
            String releaseId,
            Integer expectedVersion) {
        return runtimeFormRelease(
                formId,
                releaseId,
                expectedVersion,
                null);
    }

    /**
     * 使用签名上下文令牌解析嵌套表单的有效发布快照。
     *
     * @param formId 表单ID，后续用于处理运行时表单发布版本时定位或关联目标
     * @param releaseId 发布版本ID，后续用于处理运行时表单发布版本时定位或关联目标
     * @param expectedVersion 预期版本，作为 {@code runtimeFormReleaseInternal} 的输入影响后续处理
     * @param releaseResolutionToken 发布版本解析令牌，后续用于授权校验、关联或幂等去重
     * @return 运行时表单发布版本键值结果，供调用方继续处理
     */
    public Map<String, Object> runtimeFormRelease(
            String formId,
            String releaseId,
            Integer expectedVersion,
            String releaseResolutionToken) {
        try {
            Map<String, Object> result = runtimeFormReleaseInternal(
                    formId,
                    releaseId,
                    expectedVersion,
                    releaseResolutionToken);
            Object effectiveReleaseId = result.get(
                    "effectiveReleaseId");
            recordHotfixMetricSafely(
                    effectiveReleaseId == null
                            ? null : String.valueOf(effectiveReleaseId),
                    "FORM_LOAD",
                    true,
                    null);
            return result;
        } catch (RuntimeException exception) {
            recordHotfixConfigMetricSafely(
                    FORM,
                    formId,
                    "FORM_LOAD",
                    false,
                    exception.getMessage());
            throw exception;
        }
    }

    /**
     * 整理运行时表单发布版本内部数据，供调用方遍历或继续处理。
     *
     * @param formId 表单ID，后续用于处理运行时表单发布版本内部时定位或关联目标
     * @param releaseId 发布版本ID，后续用于处理运行时表单发布版本内部时定位或关联目标
     * @param expectedVersion 预期版本，作为 {@code resolveRuntimeFormRelease} 的输入影响后续处理
     * @param releaseResolutionToken 发布版本解析令牌，后续用于授权校验、关联或幂等去重
     * @return 运行时表单发布版本内部键值结果，供调用方继续处理
     */
    private Map<String, Object> runtimeFormReleaseInternal(
            String formId,
            String releaseId,
            Integer expectedVersion,
            String releaseResolutionToken) {
        log.info(
                "开始解析表单运行时快照: formId={}, requestedReleaseId={}, requestedVersion={}, tokenPresent={}",
                LogValue.safe(formId),
                LogValue.safe(releaseId),
                expectedVersion,
                StringUtils.hasText(releaseResolutionToken));
        if (StringUtils.hasText(releaseResolutionToken)) {
            UiReleaseResolutionTokenService.Claims claims =
                    resolutionTokenService.verify(
                            releaseResolutionToken);
            if (matchesAuthorizedFormRelease(
                    claims,
                    formId,
                    releaseId,
                    expectedVersion)) {
                ResolvedEntityFormRelease authorized =
                        resolveRuntimeFormRelease(
                                claims.parentFormId(),
                                claims.parentReleaseId(),
                                claims.parentReleaseVersion(),
                                claims.context());
                Map<String, Object> result = runtimeReleaseResult(
                        authorized,
                        runtimeSnapshot(authorized.form()));
                result.put(
                        "releaseResolutionToken",
                        resolutionTokenService.issue(
                                claims.context(),
                                formId,
                                authorized.releaseId(),
                                authorized.releaseVersion(),
                                claims.depth() + 1,
                                Instant.ofEpochSecond(
                                        claims.expiresAt())));
                return result;
            }
            log.info(
                    "使用签名上下文解析子表单: parentFormId={}, parentReleaseId={}, parentVersion={}, childFormId={}, childReleaseId={}, childVersion={}, purpose={}, historyId={}, nodeId={}, depth={}",
                    LogValue.safe(claims.parentFormId()),
                    LogValue.safe(claims.parentReleaseId()),
                    claims.parentReleaseVersion(),
                    LogValue.safe(formId),
                    LogValue.safe(releaseId),
                    expectedVersion,
                    LogValue.safe(claims.purpose()),
                    LogValue.safe(claims.processVersionHistoryId()),
                    LogValue.safe(claims.nodeId()),
                    claims.depth());
            ResolvedEntityFormRelease parent =
                    resolveRuntimeFormRelease(
                            claims.parentFormId(),
                            claims.parentReleaseId(),
                            claims.parentReleaseVersion(),
                            claims.context());
            if (!referencesChildRelease(
                    parent.form(),
                    formId,
                    releaseId,
                    expectedVersion)) {
                log.info(
                        "子表单发布引用校验失败: parentFormId={}, parentReleaseId={}, parentVersion={}, childFormId={}, childReleaseId={}, childVersion={}, reason=NOT_REFERENCED",
                        LogValue.safe(claims.parentFormId()),
                        LogValue.safe(parent.releaseId()),
                        parent.releaseVersion(),
                        LogValue.safe(formId),
                        LogValue.safe(releaseId),
                        expectedVersion);
                throw new BusinessForbiddenException(
                        "CHILD_FORM_RELEASE_NOT_REFERENCED",
                        "请求的子表单发布版本不属于父表单有效快照");
            }
            ResolvedEntityFormRelease child =
                    resolveRuntimeFormRelease(
                            formId,
                            releaseId,
                            expectedVersion,
                            claims.context());
            Map<String, Object> result = runtimeReleaseResult(
                    child,
                    runtimeSnapshot(child.form()));
            result.put(
                    "releaseResolutionToken",
                    resolutionTokenService.issue(
                            claims.context(),
                            formId,
                            child.releaseId(),
                            child.releaseVersion(),
                            claims.depth() + 1,
                            Instant.ofEpochSecond(
                                    claims.expiresAt())));
            log.info(
                    "子表单运行时快照解析完成: parentFormId={}, parentReleaseId={}, childFormId={}, childReleaseId={}, childVersion={}, effectiveReleaseId={}, hotfixApplied={}, depth={}",
                    LogValue.safe(claims.parentFormId()),
                    LogValue.safe(parent.releaseId()),
                    LogValue.safe(formId),
                    LogValue.safe(child.releaseId()),
                    child.releaseVersion(),
                    LogValue.safe(child.effectiveReleaseId()),
                    child.hotfixApplied(),
                    claims.depth() + 1);
            return result;
        }
        UiConfigRelease release = releaseMapper.findActive(FORM, formId);
        if (release == null
                || !FORM.equals(release.getConfigType())
                || !Objects.equals(formId, release.getConfigId())) {
            log.info(
                    "表单运行时快照解析失败: formId={}, requestedReleaseId={}, requestedVersion={}, actualConfigType={}, actualConfigId={}, reason=RELEASE_NOT_FOUND",
                    LogValue.safe(formId),
                    LogValue.safe(releaseId),
                    expectedVersion,
                    LogValue.safe(
                            release == null
                                    ? null : release.getConfigType()),
                    LogValue.safe(
                            release == null
                                    ? null : release.getConfigId()));
            throw new IllegalArgumentException("表单运行时发布版本不存在");
        }
        if ((StringUtils.hasText(releaseId)
                && !Objects.equals(releaseId, release.getId()))
                || (expectedVersion != null
                && !Objects.equals(expectedVersion, release.getVersion()))) {
            log.info(
                    "表单运行时快照解析失败: formId={}, requestedReleaseId={}, actualReleaseId={}, expectedVersion={}, actualVersion={}, reason=ACTIVE_RELEASE_MISMATCH",
                    LogValue.safe(formId),
                    LogValue.safe(releaseId),
                    LogValue.safe(release.getId()),
                    expectedVersion,
                    release.getVersion());
            throw new BusinessConflictException(
                    "FORM_RELEASE_CONFLICT",
                    "页面表单版本已过期，请刷新后重试");
        }
        ResolvedEntityFormRelease resolved =
                resolvedRuntimeForm(
                        release,
                        false);
        Map<String, Object> result = runtimeReleaseResult(
                resolved,
                // 运行端只需要渲染态表单；完整发布制品中的事件步骤现已包含
                // Provider 配置与策略快照，绝不能返回给普通页面调用方。
                runtimeSnapshot(resolved.form()));
        log.info(
                "表单运行时快照解析完成: formId={}, releaseId={}, releaseVersion={}, effectiveReleaseId={}, hotfixApplied={}, source={}",
                LogValue.safe(formId),
                LogValue.safe(resolved.releaseId()),
                resolved.releaseVersion(),
                LogValue.safe(resolved.effectiveReleaseId()),
                resolved.hotfixApplied(),
                "ACTIVE");
        return result;
    }

    /**
     * 解析客户端运行时表单提交使用的版本。签名令牌可读取精确固定版本，
     * 无令牌时只能使用当前 ACTIVE 版本。
     *
     * @param formId 表单ID，后续用于解析已授权运行时表单发布版本时定位或关联目标
     * @param releaseId 发布版本ID，后续用于解析已授权运行时表单发布版本时定位或关联目标
     * @param expectedVersion 预期版本，供本方法解析已授权运行时表单发布版本时使用
     * @param releaseResolutionToken 发布版本解析令牌，后续用于授权校验、关联或幂等去重
     * @return 解析后的已授权运行时表单发布版本结果，供调用方继续处理
     */
    public ResolvedEntityFormRelease resolveAuthorizedRuntimeFormRelease(
            String formId,
            String releaseId,
            Integer expectedVersion,
            String releaseResolutionToken) {
        if (StringUtils.hasText(releaseResolutionToken)) {
            UiReleaseResolutionTokenService.Claims claims =
                    resolutionTokenService.verify(
                            releaseResolutionToken);
            if (!matchesAuthorizedFormRelease(
                    claims,
                    formId,
                    releaseId,
                    expectedVersion)) {
                throw new BusinessForbiddenException(
                        "FORM_RELEASE_CONTEXT_MISMATCH",
                        "提交的表单发布版本与运行时授权不一致");
            }
            return resolveRuntimeFormRelease(
                    claims.parentFormId(),
                    claims.parentReleaseId(),
                    claims.parentReleaseVersion(),
                    claims.context());
        }
        ResolvedEntityFormRelease active =
                resolveRuntimeFormRelease(formId);
        if ((StringUtils.hasText(releaseId)
                && !Objects.equals(releaseId, active.releaseId()))
                || (expectedVersion != null
                && !Objects.equals(
                        expectedVersion,
                        active.releaseVersion()))) {
            throw new BusinessConflictException(
                    "FORM_RELEASE_CONFLICT",
                    "页面表单版本已过期，请刷新后重试");
        }
        return active;
    }

    /**
     * 判断是否匹配已授权表单发布版本；判断结果决定调用方的后续分支。
     *
     * @param claims 声明集合，供本方法判断是否匹配已授权表单发布版本时使用
     * @param formId 表单ID，后续用于判断是否匹配已授权表单发布版本时定位或关联目标
     * @param releaseId 发布版本ID，后续用于判断是否匹配已授权表单发布版本时定位或关联目标
     * @param expectedVersion 预期版本，供本方法判断是否匹配已授权表单发布版本时使用
     * @return 已授权表单发布版本条件成立时为 true，否则为 false
     */
    private boolean matchesAuthorizedFormRelease(
            UiReleaseResolutionTokenService.Claims claims,
            String formId,
            String releaseId,
            Integer expectedVersion) {
        return claims != null
                && Objects.equals(formId, claims.parentFormId())
                && (!StringUtils.hasText(releaseId)
                || Objects.equals(
                        releaseId,
                        claims.parentReleaseId()))
                && (expectedVersion == null
                || Objects.equals(
                        expectedVersion,
                        claims.parentReleaseVersion()));
    }

    /**
     * 校验审批按钮使用的表单发布令牌确实属于服务端回查到的活动待办。
     *
     * <p>普通表单解析令牌只负责固定发布版本；审批执行额外要求 ACTIVE_TASK、
     * 精确流程历史与节点均和当前待办一致。这样 NEW_INSTANCE 令牌或历史任务
     * 携带当前 ACTIVE 表单坐标都不能执行审批按钮。</p>
     *
     * @param releaseResolutionToken 发布版本解析令牌，后续用于授权校验、关联或幂等去重
     * @param formId 表单ID，后续用于校验并获取活动任务发布版本令牌时定位或关联目标
     * @param releaseId 发布版本ID，后续用于校验并获取活动任务发布版本令牌时定位或关联目标
     * @param releaseVersion 发布版本，供本方法校验并获取活动任务发布版本令牌时使用
     * @param processVersionHistoryId 流程版本历史ID，后续用于校验并获取活动任务发布版本令牌时定位或关联目标
     * @param nodeId 节点ID，后续用于校验并获取活动任务发布版本令牌时定位或关联目标
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     */
    public void requireActiveTaskReleaseToken(
            String releaseResolutionToken,
            String formId,
            String releaseId,
            Integer releaseVersion,
            String processVersionHistoryId,
            String nodeId,
            String taskId,
            String processInstanceId,
            String entityCode,
            String recordId) {
        if (!StringUtils.hasText(releaseResolutionToken)
                || !StringUtils.hasText(formId)
                || !StringUtils.hasText(releaseId)
                || releaseVersion == null
                || !StringUtils.hasText(processVersionHistoryId)
                || !StringUtils.hasText(nodeId)
                || !StringUtils.hasText(taskId)
                || !StringUtils.hasText(processInstanceId)
                || !StringUtils.hasText(entityCode)
                || !StringUtils.hasText(recordId)) {
            throw new BusinessForbiddenException(
                    "UI_EVENT_APPROVAL_RELEASE_CONTEXT_REQUIRED",
                    "审批表单按钮缺少完整的活动任务发布上下文");
        }
        UiReleaseResolutionTokenService.Claims claims =
                resolutionTokenService.verify(releaseResolutionToken);
        if (claims.purpose() != UiRuntimePurpose.ACTIVE_TASK
                || !Objects.equals(formId, claims.parentFormId())
                || !Objects.equals(releaseId, claims.parentReleaseId())
                || !Objects.equals(
                        releaseVersion,
                        claims.parentReleaseVersion())
                || !Objects.equals(
                        processVersionHistoryId,
                        claims.processVersionHistoryId())
                || !Objects.equals(nodeId, claims.nodeId())
                || !Objects.equals(taskId, claims.taskId())
                || !Objects.equals(
                        processInstanceId,
                        claims.processInstanceId())
                || !Objects.equals(entityCode, claims.entityCode())
                || !Objects.equals(recordId, claims.recordId())) {
            throw new BusinessForbiddenException(
                    "UI_EVENT_APPROVAL_RELEASE_CONTEXT_MISMATCH",
                    "审批表单按钮与当前活动任务的发布上下文不一致");
        }
    }

    /**
     * 提取已验签且与本次发布快照一致的流程只读上下文。
     *
     * <p>没有流程历史/节点绑定的普通表单仍走实体权限。返回上下文仅证明表单来源，
     * 调用方必须继续校验当前用户的实例读取权限及记录绑定，不能将发布令牌视为数据权限。</p>
     *
     * @param releaseResolutionToken 发布版本解析令牌，后续用于授权校验、关联或幂等去重
     * @param formId 表单ID，后续用于查询流程读取上下文时定位或关联目标
     * @param releaseId 发布版本ID，后续用于查询流程读取上下文时定位或关联目标
     * @param releaseVersion 发布版本，供本方法查询流程读取上下文时使用
     * @return 流程表单上下文；无令牌或普通独立/新建表单返回空
     * @throws BusinessForbiddenException 令牌无效或发布坐标不匹配
     */
    public java.util.Optional<UiRuntimeResolutionContext> findProcessReadContext(
            String releaseResolutionToken, String formId, String releaseId, Integer releaseVersion) {
        if (!StringUtils.hasText(releaseResolutionToken)) {
            return java.util.Optional.empty();
        }
        var claims = resolutionTokenService.verify(releaseResolutionToken);
        if (!Objects.equals(formId, claims.parentFormId())
                || !Objects.equals(releaseId, claims.parentReleaseId())
                || !Objects.equals(releaseVersion, claims.parentReleaseVersion())) {
            throw new BusinessForbiddenException("UI_EVENT_RELEASE_CONTEXT_MISMATCH",
                    "查看表单的发布版本与运行时上下文不一致");
        }
        if ((claims.purpose() != UiRuntimePurpose.HISTORICAL
                && claims.purpose() != UiRuntimePurpose.ACTIVE_TASK)
                || !StringUtils.hasText(claims.processVersionHistoryId())
                || !StringUtils.hasText(claims.nodeId())) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(claims.context());
    }

    /**
     * 解析表单事件运行时必须使用的精确发布快照。
     *
     * <p>流程表单携带服务端签名令牌时，事件绑定与字段定义必须从流程当前
     * 有效快照读取，不能退回全局 ACTIVE 版本。</p>
     *
     * @param formId 表单ID，后续用于解析运行时事件快照时定位或关联目标
     * @param releaseId 发布版本ID，后续用于解析运行时事件快照时定位或关联目标
     * @param expectedVersion 预期版本，供本方法解析运行时事件快照时使用
     * @param releaseResolutionToken 发布版本解析令牌，后续用于授权校验、关联或幂等去重
     * @return 解析后的运行时事件快照结果，供调用方继续处理
     */
    public ResolvedUiEventSnapshot resolveRuntimeEventSnapshot(
            String formId,
            String releaseId,
            Integer expectedVersion,
            String releaseResolutionToken) {
        log.info(
                "开始解析表单事件快照: formId={}, requestedReleaseId={}, requestedVersion={}, tokenPresent={}",
                LogValue.safe(formId),
                LogValue.safe(releaseId),
                expectedVersion,
                StringUtils.hasText(releaseResolutionToken));
        if (StringUtils.hasText(releaseResolutionToken)) {
            UiReleaseResolutionTokenService.Claims claims =
                    resolutionTokenService.verify(
                            releaseResolutionToken);
            if (!Objects.equals(formId, claims.parentFormId())
                    || (StringUtils.hasText(releaseId)
                    && !Objects.equals(
                            releaseId,
                            claims.parentReleaseId()))
                    || (expectedVersion != null
                    && !Objects.equals(
                            expectedVersion,
                            claims.parentReleaseVersion()))) {
                log.info(
                        "表单事件上下文校验失败: formId={}, requestedReleaseId={}, requestedVersion={}, tokenFormId={}, tokenReleaseId={}, tokenVersion={}, reason=CONTEXT_MISMATCH",
                        LogValue.safe(formId),
                        LogValue.safe(releaseId),
                        expectedVersion,
                        LogValue.safe(claims.parentFormId()),
                        LogValue.safe(claims.parentReleaseId()),
                        claims.parentReleaseVersion());
                throw new BusinessForbiddenException(
                        "UI_EVENT_RELEASE_CONTEXT_MISMATCH",
                        "事件请求的表单发布版本与运行时上下文不一致");
            }
            ResolvedEntityFormRelease resolved =
                    resolveRuntimeFormRelease(
                            claims.parentFormId(),
                            claims.parentReleaseId(),
                            claims.parentReleaseVersion(),
                            claims.context());
            Map<String, Object> snapshot;
            if (resolved.hotfixApplied()) {
                UiConfigHotfixTarget target =
                        hotfixTargetMapper.selectById(
                                resolved.hotfixTargetId());
                if (target == null
                        || !"ACTIVE".equals(target.getStatus())) {
                    throw new IllegalStateException(
                            "表单热修复运行时目标不存在或已失效");
                }
                snapshot = verifiedEffectiveTargetSnapshot(target);
            } else {
                UiConfigRelease base =
                        releaseMapper.selectById(
                                resolved.releaseId());
                if (base == null) {
                    throw new IllegalArgumentException(
                            "表单事件发布版本不存在");
                }
                snapshot = verifiedSnapshot(base);
            }
            log.info(
                    "表单事件快照解析完成: formId={}, releaseId={}, releaseVersion={}, effectiveReleaseId={}, hotfixApplied={}, source=SIGNED_CONTEXT",
                    LogValue.safe(formId),
                    LogValue.safe(resolved.releaseId()),
                    resolved.releaseVersion(),
                    LogValue.safe(resolved.effectiveReleaseId()),
                    resolved.hotfixApplied());
            return new ResolvedUiEventSnapshot(
                    snapshot,
                    resolved.releaseId(),
                    resolved.releaseVersion(),
                    resolved.effectiveReleaseId(),
                    resolved.hotfixApplied(),
                    resolved.effectiveContentHash());
        }

        UiConfigRelease release =
                releaseMapper.findActive(FORM, formId);
        if (release == null
                || !FORM.equals(release.getConfigType())
                || !Objects.equals(formId, release.getConfigId())) {
            log.info(
                    "表单事件快照解析失败: formId={}, requestedReleaseId={}, requestedVersion={}, reason=ACTIVE_RELEASE_NOT_FOUND",
                    LogValue.safe(formId),
                    LogValue.safe(releaseId),
                    expectedVersion);
            throw new IllegalArgumentException(
                    "表单事件运行时发布版本不存在");
        }
        if (StringUtils.hasText(releaseId)
                && !Objects.equals(releaseId, release.getId())) {
            log.info(
                    "表单事件快照版本冲突: formId={}, requestedReleaseId={}, activeReleaseId={}, requestedVersion={}, activeVersion={}, reason=RELEASE_ID_MISMATCH",
                    LogValue.safe(formId),
                    LogValue.safe(releaseId),
                    LogValue.safe(release.getId()),
                    expectedVersion,
                    release.getVersion());
            throw new BusinessConflictException(
                    "UI_EVENT_RELEASE_CONFLICT",
                    "页面配置版本已过期，请刷新后重试");
        }
        if (expectedVersion != null
                && !Objects.equals(
                        expectedVersion,
                        release.getVersion())) {
            log.info(
                    "表单事件快照版本冲突: formId={}, requestedReleaseId={}, activeReleaseId={}, requestedVersion={}, activeVersion={}, reason=RELEASE_VERSION_MISMATCH",
                    LogValue.safe(formId),
                    LogValue.safe(releaseId),
                    LogValue.safe(release.getId()),
                    expectedVersion,
                    release.getVersion());
            throw new BusinessConflictException(
                    "UI_EVENT_RELEASE_CONFLICT",
                    "页面配置版本已过期，请刷新后重试");
        }
        log.info(
                "表单事件快照解析完成: formId={}, releaseId={}, releaseVersion={}, effectiveReleaseId={}, hotfixApplied=false, source=ACTIVE",
                LogValue.safe(formId),
                LogValue.safe(release.getId()),
                release.getVersion(),
                LogValue.safe(release.getId()));
        return new ResolvedUiEventSnapshot(
                verifiedSnapshot(release),
                release.getId(),
                release.getVersion(),
                release.getId(),
                false,
                release.getContentHash());
    }

    /**
     * 整理运行时发布版本结果数据，供调用方遍历或继续处理。
     *
     * @param resolved 已解析，作为 {@code result.put} 的输入影响后续处理
     * @param snapshot 快照，作为 {@code result.put} 的输入影响后续处理
     * @return 运行时发布版本结果键值结果，供调用方继续处理
     */
    private Map<String, Object> runtimeReleaseResult(
            ResolvedEntityFormRelease resolved,
            Map<String, Object> snapshot) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", resolved.releaseId());
        result.put("configId", resolved.form().getId());
        result.put("version", resolved.releaseVersion());
        result.put("contentHash", resolved.effectiveContentHash());
        result.put(
                "effectiveReleaseId",
                resolved.effectiveReleaseId());
        result.put("hotfixApplied", resolved.hotfixApplied());
        result.put("snapshotDocument", snapshot);
        return result;
    }

    /**
     * 整理运行时快照数据，供调用方遍历或继续处理。
     *
     * @param form 表单，作为 {@code objectMapper.convertValue} 的输入影响后续处理
     * @return 运行时快照键值结果，供调用方继续处理
     */
    private Map<String, Object> runtimeSnapshot(EntityForm form) {
        Map<String, Object> formDocument = objectMapper.convertValue(
                form,
                new TypeReference<Map<String, Object>>() {});
        formDocument.remove("fields");
        formDocument.remove("nodes");
        formDocument.remove("viewCompositions");
        formDocument.remove("entity");
        formDocument.remove("runtimeReleaseId");
        formDocument.remove("runtimeReleaseVersion");
        formDocument.remove("effectiveReleaseId");
        formDocument.remove("hotfixApplied");
        formDocument.remove("releaseResolutionToken");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("configType", FORM);
        snapshot.put("form", formDocument);
        snapshot.put(
                "nodes",
                form.getNodes() == null
                        ? List.of() : form.getNodes());
        snapshot.put(
                "legacyFields",
                form.getFields() == null
                        ? List.of() : form.getFields());
        snapshot.put(
                "viewCompositions",
                form.getViewCompositions() == null
                        ? List.of() : form.getViewCompositions());
        Map<String, Object> outbound = objectMapper.convertValue(
                snapshot,
                new TypeReference<Map<String, Object>>() {});
        // 运行态表单只消费渲染配置；历史关联内容中若残留 Provider
        // 可执行快照，也必须在普通页面出站边界递归剥离。
        removeExecutableSnapshot(outbound);
        return outbound;
    }

    /**
     * 判断引用子级发布版本条件是否成立，供调用方选择后续分支。
     *
     * @param parent 父级，供本方法处理引用子级发布版本时使用
     * @param childFormId 子级表单ID，后续用于处理引用子级发布版本时定位或关联目标
     * @param childReleaseId 子级发布版本ID，后续用于处理引用子级发布版本时定位或关联目标
     * @param childReleaseVersion 子级发布版本，供本方法处理引用子级发布版本时使用
     * @return 引用子级发布版本条件成立时为 true，否则为 false
     */
    private boolean referencesChildRelease(
            EntityForm parent,
            String childFormId,
            String childReleaseId,
            Integer childReleaseVersion) {
        if (parent == null
                || !StringUtils.hasText(childFormId)
                || !StringUtils.hasText(childReleaseId)
                || childReleaseVersion == null) {
            return false;
        }
        for (EntityFormNode node : parent.getNodes() == null
                ? List.<EntityFormNode>of()
                : parent.getNodes()) {
            if (matchesChildReference(
                    documentValue(node.getPropsDocument()),
                    childFormId,
                    childReleaseId,
                    childReleaseVersion)) {
                return true;
            }
        }
        for (EntityFormField field : parent.getFields() == null
                ? List.<EntityFormField>of()
                : parent.getFields()) {
            if (matchesChildReference(
                    documentValue(field.getComponentProps()),
                    childFormId,
                    childReleaseId,
                    childReleaseVersion)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 读取指定不可变表单发布中固定的直接子表单引用。
     *
     * @param formId 表单ID，后续用于处理子级表单引用时定位或关联目标
     * @param releaseId 发布版本ID，后续用于处理子级表单引用时定位或关联目标
     * @param releaseVersion 发布版本，作为 {@code resolveRuntimeFormRelease} 的输入影响后续处理
     * @return 界面已发布表单引用集合，供调用方遍历或展示
     */
    public List<UiPublishedFormReference> childFormReferences(
            String formId,
            String releaseId,
            Integer releaseVersion) {
        EntityForm form = resolveRuntimeFormRelease(
                formId,
                releaseId,
                releaseVersion,
                UiRuntimeResolutionContext.historical(
                        null,
                        null)).form();
        Set<UiPublishedFormReference> references =
                new LinkedHashSet<>();
        for (EntityFormNode node : form.getNodes() == null
                ? List.<EntityFormNode>of()
                : form.getNodes()) {
            collectChildReferences(
                    documentValue(node.getPropsDocument()),
                    references);
        }
        for (EntityFormField field : form.getFields() == null
                ? List.<EntityFormField>of()
                : form.getFields()) {
            collectChildReferences(
                    documentValue(field.getComponentProps()),
                    references);
        }
        return List.copyOf(references);
    }

    /**
     * 收集子级引用；结果供调用方的后续步骤使用。
     *
     * @param value 待收集子级引用的原始输入，结果供调用方继续使用
     * @param references 引用，作为 {@code list.forEach} 的输入影响后续处理
     */
    private void collectChildReferences(
            Object value,
            Set<UiPublishedFormReference> references) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> map = new LinkedHashMap<>();
            source.forEach((key, child) ->
                    map.put(String.valueOf(key), child));
            String formId = firstOptionalText(
                    map.get("childFormId"),
                    map.get("refFormId"),
                    map.get("publishedFormId"));
            String releaseId = firstOptionalText(
                    map.get("childFormReleaseId"),
                    map.get("refFormReleaseId"),
                    map.get("publishedFormReleaseId"));
            Integer releaseVersion = firstOptionalInteger(
                    map.get("childFormReleaseVersion"),
                    map.get("refFormReleaseVersion"),
                    map.get("publishedFormReleaseVersion"));
            if (StringUtils.hasText(formId)
                    && StringUtils.hasText(releaseId)
                    && releaseVersion != null) {
                references.add(new UiPublishedFormReference(
                        formId,
                        releaseId,
                        releaseVersion));
            }
            map.values().forEach(child ->
                    collectChildReferences(child, references));
            return;
        }
        if (value instanceof List<?> list) {
            list.forEach(child ->
                    collectChildReferences(child, references));
        }
    }

    /**
     * 处理文档值，并将结果传给后续步骤。
     *
     * @param document 文档，作为 {@code codec.read} 的输入影响后续处理
     * @return 处理后的文档值结果，供调用方继续处理
     */
    private Object documentValue(String document) {
        if (!StringUtils.hasText(document)) {
            return Map.of();
        }
        try {
            return codec.read(
                    document,
                    "子表单引用配置");
        } catch (IllegalArgumentException exception) {
            return Map.of();
        }
    }

    /**
     * 判断是否匹配子级引用；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否匹配子级引用的原始输入，结果供调用方继续使用
     * @param childFormId 子级表单ID，后续用于判断是否匹配子级引用时定位或关联目标
     * @param childReleaseId 子级发布版本ID，后续用于判断是否匹配子级引用时定位或关联目标
     * @param childReleaseVersion 子级发布版本，供本方法判断是否匹配子级引用时使用
     * @return 子级引用条件成立时为 true，否则为 false
     */
    private boolean matchesChildReference(
            Object value,
            String childFormId,
            String childReleaseId,
            Integer childReleaseVersion) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> map = new LinkedHashMap<>();
            source.forEach((key, child) ->
                    map.put(String.valueOf(key), child));
            String referencedFormId = firstOptionalText(
                    map.get("childFormId"),
                    map.get("refFormId"),
                    map.get("publishedFormId"));
            String referencedReleaseId = firstOptionalText(
                    map.get("childFormReleaseId"),
                    map.get("refFormReleaseId"),
                    map.get("publishedFormReleaseId"));
            Integer referencedVersion = firstOptionalInteger(
                    map.get("childFormReleaseVersion"),
                    map.get("refFormReleaseVersion"),
                    map.get("publishedFormReleaseVersion"));
            if (Objects.equals(childFormId, referencedFormId)
                    && Objects.equals(
                            childReleaseId,
                            referencedReleaseId)
                    && Objects.equals(
                            childReleaseVersion,
                            referencedVersion)) {
                return true;
            }
            return map.values().stream().anyMatch(child ->
                    matchesChildReference(
                            child,
                            childFormId,
                            childReleaseId,
                            childReleaseVersion));
        }
        if (value instanceof List<?> list) {
            return list.stream().anyMatch(child ->
                    matchesChildReference(
                            child,
                            childFormId,
                            childReleaseId,
                            childReleaseVersion));
        }
        return false;
    }

    /**
     * 生成首个可选文本文本，供后续匹配或展示。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个可选文本文本，供调用方比较或展示
     */
    private String firstOptionalText(Object... values) {
        for (Object value : values) {
            String candidate = text(value);
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 处理首个可选整数，并将结果传给后续步骤。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个可选整数结果，供调用方继续处理
     */
    private Integer firstOptionalInteger(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return nullableInteger(value);
            }
        }
        return null;
    }

    /**
     * 构建配置的草稿快照（不落库），用于差异比对与发布预览。
     *
     * @param configType 配置类型
     * @param configId   配置ID
     * @return 草稿快照 Map
     * @throws IllegalArgumentException 配置不存在时抛出
     */
    public Map<String, Object> draftSnapshot(String configType, String configId) {
        Map<String, Object> snapshot = buildDraftSnapshot(
                configType, configId, false);
        if (!FORM.equals(normalize(configType))) {
            return snapshot;
        }
        Map<String, Object> outbound = objectMapper.convertValue(
                snapshot,
                new TypeReference<Map<String, Object>>() {});
        // 草稿接口是管理出站边界，不参与发布制品生成。关联内容快照可能已固定
        // Provider 配置，必须在副本上递归剥离，不能把密钥随表单设计权限下发。
        removeExecutableSnapshot(outbound);
        return outbound;
    }

    /**
     * 比较草稿快照与当前激活发布快照的差异。
     *
     * @param configType 配置类型
     * @param configId   配置ID
     * @return 差异 DTO，包含是否变化、变化区块与明细
     */
    public UiConfigDiffDTO diff(String configType, String configId) {
        Map<String, Object> draft = buildDraftSnapshot(configType, configId);
        String draftDocument = snapshotSupport.canonical(draft);
        String draftHash = snapshotSupport.hash(draftDocument);
        UiConfigRelease active = active(configType, configId);
        Map<String, Object> activeSnapshot = active == null
                ? Map.of()
                : normalizeReleaseComparisonSnapshot(
                        configType,
                        configId,
                        draft,
                        snapshotSupport.stableMap(
                                verifiedSnapshot(active)));
        String activeHash = active == null
                ? null
                : active.getContentHash();
        String activeComparisonHash = active == null
                ? null
                : snapshotSupport.hash(
                        snapshotSupport.canonical(activeSnapshot));
        boolean changed = active == null
                || !semanticPatchService.build(
                        configType,
                        activeSnapshot,
                        draft).operations().isEmpty();
        DraftDiscardAssessment discardAssessment = active == null
                ? DraftDiscardAssessment.unavailable(
                        "当前配置尚未发布，不能撤销到发布基线")
                : assessDraftDiscard(
                        configType,
                        configId,
                        draft,
                        activeSnapshot,
                        activeComparisonHash);
        List<String> changedSections = new ArrayList<>();
        if (changed) {
            for (String key : draft.keySet()) {
                if (!snapshotSupport.equivalent(
                        draft.get(key),
                        activeSnapshot.get(key))) {
                    changedSections.add(key);
                }
            }
        }
        return UiConfigDiffDTO.builder()
                .configType(configType)
                .configId(configId)
                .draftHash(draftHash)
                .activeHash(activeHash)
                .changed(changed)
                .discardableChanged(
                        discardAssessment.discardableChanged())
                .canDiscardDraft(
                        discardAssessment.canDiscardDraft())
                .dependencyChanged(
                        discardAssessment.dependencyChanged())
                .discardBlockedReason(
                        discardAssessment.blockedReason())
                .changedSections(changedSections)
                .changedItems(changed
                        ? detailedChanges(
                                configType,
                                draft,
                                activeSnapshot,
                                changedSections)
                        : List.of())
                .build();
    }

    /**
     * 预览当前 ACTIVE 与待激活历史版本之间的差异。
     *
     * @param configType 配置类型标识，决定后续{@code activation}预览采用的处理分支
     * @param configId 配置ID，后续用于处理{@code activation}预览时定位或关联目标
     * @param releaseId 发布版本ID，后续用于处理{@code activation}预览时定位或关联目标
     * @return 处理后的{@code activation}预览结果，供调用方继续处理
     */
    public UiConfigActivationPreviewDTO activationPreview(
            String configType,
            String configId,
            String releaseId) {
        requireType(configType);
        UiConfigRelease target = releaseMapper.selectById(releaseId);
        if (target == null
                || !configType.equals(target.getConfigType())
                || !configId.equals(target.getConfigId())) {
            throw new IllegalArgumentException("发布版本不存在");
        }
        if (HOTFIX.equals(target.getReleaseMode())) {
            throw new BusinessConflictException(
                    "HOTFIX_ACTIVATE_NOT_ALLOWED",
                    "热修复必须通过撤回入口按发布时间逆序回滚");
        }
        Map<String, Object> targetSnapshot =
                verifiedSnapshot(target);
        // 预览阶段即执行与真正激活相同的完整校验，使失效挂载点或固定依赖
        // 在用户确认切换版本前被准确指出，而不是等状态更新事务才暴露。
        validateSnapshotForActivation(
                configType, configId, targetSnapshot);
        UiConfigRelease current = releaseMapper.findActive(
                configType,
                configId);
        Map<String, Object> currentSnapshot = current == null
                ? Map.of() : verifiedSnapshot(current);
        UiConfigSemanticPatchService.PatchAnalysis patch =
                semanticPatchService.build(
                        configType,
                        currentSnapshot,
                        targetSnapshot);
        boolean changed = !patch.operations().isEmpty();
        List<String> changedSections = new ArrayList<>();
        Set<String> sections = new LinkedHashSet<>();
        sections.addAll(currentSnapshot.keySet());
        sections.addAll(targetSnapshot.keySet());
        for (String section : sections) {
            if (!snapshotSupport.equivalent(
                    targetSnapshot.get(section),
                    currentSnapshot.get(section))) {
                changedSections.add(section);
            }
        }
        return UiConfigActivationPreviewDTO.builder()
                .configType(configType)
                .configId(configId)
                .currentReleaseId(current == null
                        ? null : current.getId())
                .currentVersion(current == null
                        ? null : current.getVersion())
                .targetReleaseId(target.getId())
                .targetVersion(target.getVersion())
                .riskLevel(patch.riskLevel())
                .riskItems(patch.riskItems())
                .changed(changed)
                .changedSections(changedSections)
                .changedItems(changed
                        ? detailedChanges(
                                configType,
                                targetSnapshot,
                                currentSnapshot,
                                changedSections)
                        : List.of())
                .build();
    }

    /**
     * 整理{@code detailed}变更集合数据，供调用方遍历或继续处理。
     *
     * @param configType 配置类型标识，决定后续{@code detailed}变更集合采用的处理分支
     * @param draft 草稿，作为 {@code appendObjectChange} 的输入影响后续处理
     * @param active 活动，作为 {@code mapValue} 的输入影响后续处理
     * @param changedSections 已变更区段集合，作为 {@code appendFallbackChange} 的输入影响后续处理
     * @return 界面配置差异条目集合，供调用方遍历或展示
     */
    private List<UiConfigDiffItemDTO> detailedChanges(
            String configType,
            Map<String, Object> draft,
            Map<String, Object> active,
            List<String> changedSections) {
        List<UiConfigDiffItemDTO> changes = new ArrayList<>();
        if (FORM.equals(configType)) {
            appendObjectChange(
                    changes,
                    "form",
                    "form",
                    "表单设置",
                    mapValue(draft.get("form")),
                    mapValue(active.get("form")));
            appendCollectionChanges(
                    changes,
                    "nodes",
                    "节点",
                    mapList(draft.get("nodes")),
                    mapList(active.get("nodes")),
                    List.of("id", "nodeKey"),
                    List.of("label", "fieldLabel", "fieldName", "nodeKey"),
                    true);
            appendViewCompositionChanges(changes, draft, active);
            appendEventBindingChanges(
                    changes,
                    draft,
                    active);
            appendFallbackChange(
                    changes,
                    changedSections,
                    "form",
                    "表单发布快照");
            return changes;
        }

        Map<String, Object> draftList = mapValue(draft.get("list"));
        Map<String, Object> activeList = mapValue(active.get("list"));
        appendObjectChange(
                changes,
                "list",
                "list",
                "列表设置",
                withoutKeys(draftList, Set.of(
                        "fields", "toolbarConfig", "rowActionConfig")),
                withoutKeys(activeList, Set.of(
                        "fields", "toolbarConfig", "rowActionConfig")));
        appendCollectionChanges(
                changes,
                "fields",
                "列表字段",
                mapList(draftList.get("fields")),
                mapList(activeList.get("fields")),
                List.of("id", "fieldCode"),
                List.of("fieldLabel", "fieldName", "fieldCode"),
                true);
        appendCollectionChanges(
                changes,
                "toolbarActions",
                "工具栏按钮",
                mapList(draftList.get("toolbarConfig")),
                mapList(activeList.get("toolbarConfig")),
                List.of("id", "key", "actionCode"),
                List.of("label", "name", "key", "actionCode"),
                true);
        appendCollectionChanges(
                changes,
                "rowActions",
                "行按钮",
                mapList(draftList.get("rowActionConfig")),
                mapList(activeList.get("rowActionConfig")),
                List.of("id", "key", "actionCode"),
                List.of("label", "name", "key", "actionCode"),
                true);
        appendViewCompositionChanges(changes, draft, active);
        appendEventBindingChanges(
                changes,
                draft,
                active);
        appendFallbackChange(
                changes,
                changedSections,
                "list",
                "列表发布快照");
        return changes;
    }

    /**
     * 追加事件绑定变更集合；结果供后续流程传递或持久化。
     *
     * @param changes 变更集合，作为 {@code appendCollectionChanges} 的输入影响后续处理
     * @param draft 草稿，作为 {@code appendCollectionChanges} 的输入影响后续处理
     * @param active 活动，供本方法追加事件绑定变更集合时使用
     */
    private void appendEventBindingChanges(
            List<UiConfigDiffItemDTO> changes,
            Map<String, Object> draft,
            Map<String, Object> active) {
        appendCollectionChanges(
                changes,
                "eventBindings",
                "事件绑定",
                mapList(draft.get("eventBindings")),
                mapList(active.get("eventBindings")),
                List.of("id"),
                List.of("eventCode", "targetKey"),
                false);
    }

    /**
     * 追加视图组合变更集合；结果供后续流程传递或持久化。
     *
     * @param changes 变更集合，作为 {@code appendCollectionChanges} 的输入影响后续处理
     * @param draft 草稿，作为 {@code appendCollectionChanges} 的输入影响后续处理
     * @param active 活动，供本方法追加视图组合变更集合时使用
     */
    private void appendViewCompositionChanges(
            List<UiConfigDiffItemDTO> changes,
            Map<String, Object> draft,
            Map<String, Object> active) {
        appendCollectionChanges(
                changes,
                "viewCompositions",
                "关联内容",
                mapList(draft.get("viewCompositions")),
                mapList(active.get("viewCompositions")),
                List.of("id", "compositionKey"),
                List.of("compositionKey"),
                false);
    }

    /**
     * 追加兜底变更；结果供后续流程传递或持久化。
     *
     * @param changes 变更集合，供本方法追加兜底变更时使用
     * @param changedSections 已变更区段集合，作为 {@code changedFields} 的输入影响后续处理
     * @param section 区段，供本方法追加兜底变更时使用
     * @param label 标签，后续用于追加兜底变更时匹配或展示
     */
    private void appendFallbackChange(
            List<UiConfigDiffItemDTO> changes,
            List<String> changedSections,
            String section,
            String label) {
        if (changes.isEmpty() && !changedSections.isEmpty()) {
            changes.add(UiConfigDiffItemDTO.builder()
                    .section(section)
                    .id(section)
                    .label(label)
                    .changeType("UPDATED")
                    .changedFields(changedSections)
                    .build());
        }
    }

    /**
     * 追加对象变更；结果供后续流程传递或持久化。
     *
     * @param changes 变更集合，供本方法追加对象变更时使用
     * @param section 区段，供本方法追加对象变更时使用
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param label 标签，后续用于追加对象变更时匹配或展示
     * @param draft 草稿，作为 {@code changedFields} 的输入影响后续处理
     * @param active 活动，作为 {@code changedFields} 的输入影响后续处理
     */
    private void appendObjectChange(
            List<UiConfigDiffItemDTO> changes,
            String section,
            String id,
            String label,
            Map<String, Object> draft,
            Map<String, Object> active) {
        if (snapshotSupport.equivalent(draft, active)) {
            return;
        }
        changes.add(UiConfigDiffItemDTO.builder()
                .section(section)
                .id(id)
                .label(label)
                .changeType(active.isEmpty() ? "ADDED" : "UPDATED")
                .changedFields(changedKeys(draft, active))
                .build());
    }

    /**
     * 追加集合变更集合；结果供后续流程传递或持久化。
     *
     * @param changes 变更集合，供本方法追加集合变更集合时使用
     * @param section 区段，作为 {@code changes.add} 的输入影响后续处理
     * @param defaultLabel 默认标签，后续用于追加集合变更集合时匹配或展示
     * @param draftItems 草稿条目，作为 {@code indexByStableId} 的输入影响后续处理
     * @param activeItems 活动条目，作为 {@code indexByStableId} 的输入影响后续处理
     * @param idKeys ID键集合，作为 {@code indexByStableId} 的输入影响后续处理
     * @param labelKeys 标签键集合，作为 {@code changes.add} 的输入影响后续处理
     * @param supportsMove {@code supports}{@code move}，供本方法追加集合变更集合时使用
     */
    private void appendCollectionChanges(
            List<UiConfigDiffItemDTO> changes,
            String section,
            String defaultLabel,
            List<Map<String, Object>> draftItems,
            List<Map<String, Object>> activeItems,
            List<String> idKeys,
            List<String> labelKeys,
            boolean supportsMove) {
        Map<String, Map<String, Object>> draftById =
                indexByStableId(draftItems, idKeys);
        Map<String, Map<String, Object>> activeById =
                indexByStableId(activeItems, idKeys);
        for (Map.Entry<String, Map<String, Object>> entry : draftById.entrySet()) {
            String id = entry.getKey();
            Map<String, Object> draft = entry.getValue();
            Map<String, Object> active = activeById.remove(id);
            if (active == null) {
                changes.add(itemChange(
                        section, id, itemLabel(draft, labelKeys, defaultLabel),
                        "ADDED", List.of()));
                continue;
            }
            if (snapshotSupport.equivalent(draft, active)) {
                continue;
            }
            List<String> changedFields = changedKeys(draft, active);
            boolean moved = supportsMove
                    && !changedFields.isEmpty()
                    && changedFields.stream().allMatch(field ->
                            "parentId".equals(field) || "orderKey".equals(field));
            changes.add(itemChange(
                    section,
                    id,
                    itemLabel(draft, labelKeys, defaultLabel),
                    moved ? "MOVED" : "UPDATED",
                    changedFields));
        }
        activeById.forEach((id, active) -> changes.add(itemChange(
                section,
                id,
                itemLabel(active, labelKeys, defaultLabel),
                "REMOVED",
                List.of())));
    }

    /**
     * 处理条目变更，并将结果传给后续步骤。
     *
     * @param section 区段，供本方法处理条目变更时使用
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param label 标签，后续用于处理条目变更时匹配或展示
     * @param changeType 变更类型标识，决定后续条目变更采用的处理分支
     * @param changedFields 已变更字段，供本方法处理条目变更时使用
     * @return 处理后的条目变更结果，供调用方继续处理
     */
    private UiConfigDiffItemDTO itemChange(
            String section,
            String id,
            String label,
            String changeType,
            List<String> changedFields) {
        return UiConfigDiffItemDTO.builder()
                .section(section)
                .id(id)
                .label(label)
                .changeType(changeType)
                .changedFields(changedFields)
                .build();
    }

    /**
     * 整理索引稳定ID数据，供调用方遍历或继续处理。
     *
     * @param items 条目，供本方法处理索引稳定ID时使用
     * @param idKeys ID键集合，作为 {@code firstText} 的输入影响后续处理
     * @return 索引稳定ID键值结果，供调用方继续处理
     */
    private Map<String, Map<String, Object>> indexByStableId(
            List<Map<String, Object>> items,
            List<String> idKeys) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String id = firstText(idKeys.stream()
                    .map(item::get)
                    .map(this::text)
                    .toArray(String[]::new));
            if (StringUtils.hasText(id)) {
                result.put(id, item);
            }
        }
        return result;
    }

    /**
     * 生成条目标签文本，供后续匹配或展示。
     *
     * @param item 条目，供本方法处理条目标签时使用
     * @param labelKeys 标签键集合，供本方法处理条目标签时使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的条目标签文本，供调用方比较或展示
     */
    private String itemLabel(
            Map<String, Object> item,
            List<String> labelKeys,
            String fallback) {
        List<String> labels = new ArrayList<>(labelKeys.stream()
                .map(item::get)
                .map(this::text)
                .toList());
        labels.add(fallback);
        return firstText(labels.toArray(String[]::new));
    }

    /**
     * 整理已变更键集合数据，供调用方遍历或继续处理。
     *
     * @param draft 草稿，作为 {@code keys.addAll} 的输入影响后续处理
     * @param active 活动，作为 {@code keys.addAll} 的输入影响后续处理
     * @return 界面配置发布版本集合，供调用方遍历或展示
     */
    private List<String> changedKeys(
            Map<String, Object> draft,
            Map<String, Object> active) {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(draft.keySet());
        keys.addAll(active.keySet());
        return keys.stream()
                .filter(key -> !snapshotSupport.equivalent(
                        draft.get(key),
                        active.get(key)))
                .sorted()
                .toList();
    }

    /**
     * 整理{@code without}键集合数据，供调用方遍历或继续处理。
     *
     * @param source 待处理{@code without}键集合的原始输入，结果供调用方继续使用
     * @param ignoredKeys {@code ignored}键集合，供本方法处理{@code without}键集合时使用
     * @return {@code without}键集合键值结果，供调用方继续处理
     */
    private Map<String, Object> withoutKeys(
            Map<String, Object> source,
            Set<String> ignoredKeys) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        ignoredKeys.forEach(result::remove);
        return result;
    }

    /**
     * 将动态值转换为键值映射，供后续字段读取和校验。
     *
     * @param source 待处理映射值的原始输入，结果供调用方继续使用
     * @return 映射值键值结果，供调用方继续处理
     */
    private Map<String, Object> mapValue(Object source) {
        if (!(source instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    /**
     * 整理映射列表数据，供调用方遍历或继续处理。
     *
     * @param source 待处理映射列表的原始输入，结果供调用方继续使用
     * @return 界面配置发布版本集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> mapList(Object source) {
        if (!(source instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> value = mapValue(item);
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }

    /**
     * 汇总发布时会被关联内容精确固定的业务依赖。
     *
     * <p>该清单只用于发布前的人类确认，不参与权限或运行时解析；运行时仍以
     * 不可变快照中的 ID、版本和哈希为权威。按稳定引用去重，避免同一目标被
     * 多个关联内容使用时在发布窗口重复展示。</p>
     *
     * @param snapshot 快照，供本方法发布依赖集合时使用
     * @return 界面配置发布版本集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> publishDependencies(
            Map<String, Object> snapshot) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> item : mapList(
                snapshot.get("viewCompositions"))) {
            Map<String, Object> config = mapValue(item.get("config"));
            String compositionName = firstNonBlank(
                    config.get("name"),
                    item.get("compositionKey"),
                    "关联内容");
            Map<String, Object> source = mapValue(config.get("source"));
            Map<String, Object> target = mapValue(config.get("target"));

            // 关系解析依赖实体发布定义。把来源和目标实体历史一并展示，
            // 避免用户只确认了目标页面版本，却不知道字段/关系语义也已冻结。
            Map<String, Object> entitySnapshots = mapValue(
                    config.get("entitySnapshots"));
            addEntitySchemaDependency(
                    result,
                    seen,
                    mapValue(entitySnapshots.get("source")),
                    source,
                    compositionName);
            addEntitySchemaDependency(
                    result,
                    seen,
                    mapValue(entitySnapshots.get("target")),
                    target,
                    compositionName);

            String targetType = text(target.get("contentType"));
            String targetId = text(target.get("contentId"));
            if (StringUtils.hasText(targetType)
                    && StringUtils.hasText(targetId)) {
                Map<String, Object> dependency = new LinkedHashMap<>();
                dependency.put("type", targetType);
                dependency.put("name", firstNonBlank(
                        target.get("contentName"),
                        target.get("contentKey"),
                        targetId));
                dependency.put("key", target.get("contentKey"));
                dependency.put("version", target.get("releaseVersion"));
                dependency.put("releaseId", target.get("releaseId"));
                dependency.put("usedBy", compositionName);
                addPublishDependency(
                        result,
                        seen,
                        targetType + ":" + targetId + ":"
                                + text(target.get("releaseId")),
                        dependency);
            }

            Map<String, Object> special = mapValue(
                    config.get("specialHandling"));
            addInterfaceDependency(
                    result,
                    seen,
                    mapValue(special.get("interfaceService")),
                    compositionName);
            // 一个关联内容可声明多个动作接口；发布确认必须完整展示，不能只看读取接口。
            for (Map<String, Object> actionService : mapList(
                    special.get("actionServices"))) {
                addInterfaceDependency(
                        result, seen, actionService, compositionName);
            }

            Map<String, Object> component = mapValue(
                    special.get("customComponent"));
            String componentName = text(component.get("name"));
            if (StringUtils.hasText(componentName)) {
                Map<String, Object> dependency = new LinkedHashMap<>();
                dependency.put("type", "CUSTOM_COMPONENT");
                dependency.put("name", firstNonBlank(
                        component.get("displayName"), componentName));
                dependency.put("key", componentName);
                dependency.put("version", component.get("version"));
                dependency.put("usedBy", compositionName);
                addPublishDependency(
                        result,
                        seen,
                        "COMPONENT:" + componentName + ":"
                                + text(component.get("version")),
                        dependency);
            }
        }
        return List.copyOf(result);
    }

    /**
     * 汇总单接口扩展依赖；历史 serviceId + operationCode 仅用于旧快照兼容。
     *
     * @param result 结果，作为 {@code addPublishDependency} 的输入影响后续处理
     * @param seen 已见，作为 {@code addPublishDependency} 的输入影响后续处理
     * @param reference 引用，作为 {@code text} 的输入影响后续处理
     * @param compositionName 组合名称，后续用于添加接口依赖时匹配或展示
     */
    private void addInterfaceDependency(
            List<Map<String, Object>> result,
            Set<String> seen,
            Map<String, Object> reference,
            String compositionName) {
        String extensionId = text(reference.get("extensionId"));
        if (StringUtils.hasText(extensionId)) {
            String extensionKey = text(reference.get("extensionKey"));
            Object extensionRevision = reference.get("extensionRevision");
            Map<String, Object> dependency = new LinkedHashMap<>();
            // 保留既有预览 DTO 类型值，前端已将它展示为“扩展接口”。
            dependency.put("type", "INTERFACE_SERVICE");
            dependency.put("name", firstNonBlank(
                    reference.get("interfaceName"),
                    reference.get("extensionName"),
                    extensionKey,
                    extensionId));
            dependency.put("key", firstNonBlank(extensionKey, extensionId));
            dependency.put("version", extensionRevision);
            dependency.put("usedBy", compositionName);
            addPublishDependency(
                    result,
                    seen,
                    "INTERFACE:" + extensionId + ":"
                            + text(extensionRevision),
                    dependency);
            return;
        }
        String legacyServiceId = text(reference.get("serviceId"));
        String operationCode = text(reference.get("operationCode"));
        if (!StringUtils.hasText(legacyServiceId)
                || !StringUtils.hasText(operationCode)) {
            return;
        }
        Map<String, Object> dependency = new LinkedHashMap<>();
        dependency.put("type", "INTERFACE_SERVICE");
        dependency.put("name", firstNonBlank(
                reference.get("serviceName"),
                reference.get("sourceCode"),
                legacyServiceId));
        dependency.put("key", operationCode);
        dependency.put("version", reference.get("serviceRevision"));
        dependency.put("usedBy", compositionName);
        addPublishDependency(
                result,
                seen,
                "LEGACY_INTERFACE:" + legacyServiceId + ":"
                        + operationCode + ":"
                        + text(reference.get("serviceRevision")),
                dependency);
    }

    /**
     * 添加实体结构依赖；结果供后续流程传递或持久化。
     *
     * @param result 结果，作为 {@code addPublishDependency} 的输入影响后续处理
     * @param seen 已见，作为 {@code addPublishDependency} 的输入影响后续处理
     * @param pinned 固定，作为 {@code text} 的输入影响后续处理
     * @param entity 实体，作为 {@code dependency.put} 的输入影响后续处理
     * @param compositionName 组合名称，后续用于添加实体结构依赖时匹配或展示
     */
    private void addEntitySchemaDependency(
            List<Map<String, Object>> result,
            Set<String> seen,
            Map<String, Object> pinned,
            Map<String, Object> entity,
            String compositionName) {
        String historyId = text(pinned.get("historyId"));
        if (!StringUtils.hasText(historyId)) {
            return;
        }
        Map<String, Object> dependency = new LinkedHashMap<>();
        dependency.put("type", "ENTITY_SCHEMA");
        dependency.put("name", firstNonBlank(
                entity.get("entityName"),
                pinned.get("entityCode"),
                pinned.get("entityId")));
        dependency.put("key", pinned.get("entityCode"));
        dependency.put("version", pinned.get("version"));
        dependency.put("releaseId", historyId);
        dependency.put("usedBy", compositionName);
        addPublishDependency(
                result,
                seen,
                "ENTITY_SCHEMA:" + historyId,
                dependency);
    }

    /**
     * 添加发布依赖；结果供后续流程传递或持久化。
     *
     * @param result 结果，供本方法添加发布依赖时使用
     * @param seen 已见，供本方法添加发布依赖时使用
     * @param identity 身份，供本方法添加发布依赖时使用
     * @param dependency 依赖，作为 {@code result.add} 的输入影响后续处理
     */
    private void addPublishDependency(
            List<Map<String, Object>> result,
            Set<String> seen,
            String identity,
            Map<String, Object> dependency) {
        if (seen.add(identity)) {
            result.add(Collections.unmodifiableMap(dependency));
        }
    }

    /**
     * 按候选顺序取首个非空白值，供后续处理使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个非空白文本，供调用方比较或展示
     */
    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            String candidate = text(value);
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    /**
     * 发布预检。普通发布返回草稿差异；热修复同时执行风险分级、流程影响分析和逐版本试算。
     *
     * @param configType 配置类型标识，决定后续预览采用的处理分支
     * @param configId 配置ID，后续用于发布预览时定位或关联目标
     * @param request 本次请求，后续经校验后用于发布预览
     * @return 发布后的预览结果，供调用方继续处理
     */
    public UiConfigPublishPreviewDTO publishPreview(
            String configType,
            String configId,
            UiConfigPublishRequest request) {
        String releaseMode = releaseMode(request);
        if (HOTFIX.equals(releaseMode)) {
            requireHotfixSupported(configType);
            configurationAccessService.requireHotfixAccess(false);
            return prepareHotfix(configType, configId, request).preview();
        }
        Map<String, Object> draft = buildDraftSnapshot(configType, configId);
        validateForPublish(configType, configId, draft);
        String draftHash = snapshotSupport.hash(
                snapshotSupport.canonical(draft));
        UiConfigRelease current = releaseMapper.findActive(configType, configId);
        Map<String, Object> activeSnapshot = current == null
                ? Map.of() : verifiedSnapshot(current);
        UiConfigSemanticPatchService.PatchAnalysis patch =
                semanticPatchService.build(
                        configType,
                        activeSnapshot,
                        draft);
        UiConfigDiffDTO diff = diff(configType, configId);
        String targetHash = "STANDARD:"
                + (current == null ? "NONE" : current.getId());
        return UiConfigPublishPreviewDTO.builder()
                .configType(configType)
                .configId(configId)
                .releaseMode(STANDARD)
                .rolloutScope(null)
                .draftHash(draftHash)
                .activeReleaseId(current == null ? null : current.getId())
                .activeVersion(current == null ? null : current.getVersion())
                .targetHash(targetHash)
                .impactToken(impactToken(
                        configType,
                        configId,
                        STANDARD,
                        draftHash,
                        current == null ? null : current.getId(),
                        targetHash,
                        patch.riskLevel()))
                .riskLevel(patch.riskLevel())
                .changed(diff.isChanged())
                .requiresOverride(false)
                .canPublish(diff.isChanged())
                .processVersionCount(0)
                .activeInstanceCount(0L)
                .skippedHistoricalInstanceCount(0L)
                .changedItems(diff.getChangedItems())
                .riskItems(patch.riskItems())
                .targets(List.of())
                .dependencies(publishDependencies(draft))
                .blockers(diff.isChanged()
                        ? List.of() : List.of("当前草稿与已发布版本一致"))
                .build();
    }

    /**
     * 兼容旧调用方的普通发布入口。
     *
     * @param configType 配置类型标识，决定后续界面配置发布版本采用的处理分支
     * @param configId 配置ID，后续用于发布界面配置发布版本时定位或关联目标
     * @param description 描述，作为 {@code request.setDescription} 的输入影响后续处理
     * @return 发布后的界面配置发布版本结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public UiConfigRelease publish(
            String configType,
            String configId,
            String description) {
        UiConfigPublishRequest request = new UiConfigPublishRequest();
        request.setDescription(description);
        request.setReleaseMode(STANDARD);
        return publish(configType, configId, request);
    }

    /**
     * 按发布请求执行普通发布；仅表单支持兼容热修复。
     *
     * @param configType 配置类型标识，决定后续界面配置发布版本采用的处理分支
     * @param configId 配置ID，后续用于发布界面配置发布版本时定位或关联目标
     * @param request 本次请求，后续经校验后用于发布界面配置发布版本
     * @return 发布后的界面配置发布版本结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public UiConfigRelease publish(
            String configType,
            String configId,
            UiConfigPublishRequest request) {
        String releaseMode = releaseMode(request);
        log.info(
                "开始发布UI配置: configType={}, configId={}, releaseMode={}, expectedDraftHashPresent={}, expectedActiveReleaseId={}, impactTokenPresent={}, operatorId={}",
                LogValue.safe(configType),
                LogValue.safe(configId),
                LogValue.safe(releaseMode),
                request != null
                        && StringUtils.hasText(
                                request.getExpectedDraftHash()),
                LogValue.safe(
                        request == null
                                ? null
                                : request.getExpectedActiveReleaseId()),
                request != null
                        && StringUtils.hasText(
                                request.getImpactToken()),
                LogValue.safe(UserContext.getUserId()));
        if (HOTFIX.equals(releaseMode)) {
            requireHotfixSupported(configType);
            return managementRelease(
                    configType,
                    publishHotfix(configType, configId, request));
        }
        return managementRelease(
                configType,
                publishStandard(configType, configId, request));
    }

    /**
     * 发布标准；后续由接收方或异步任务继续处理。
     *
     * @param configType 配置类型标识，决定后续标准采用的处理分支
     * @param configId 配置ID，后续用于发布标准时定位或关联目标
     * @param request 本次请求，后续经校验后用于发布标准
     * @return 发布后的标准结果，供调用方继续处理
     */
    private UiConfigRelease publishStandard(
            String configType,
            String configId,
            UiConfigPublishRequest request) {
        lockOwner(configType, configId);
        Map<String, Object> snapshot = buildDraftSnapshot(configType, configId);
        validateForPublish(configType, configId, snapshot);
        String document = snapshotSupport.canonical(snapshot);
        String contentHash = snapshotSupport.hash(document);
        UiConfigRelease active = releaseMapper.findActive(
                configType,
                configId);
        Map<String, Object> activeSnapshot = active == null
                ? Map.of() : verifiedSnapshot(active);
        UiConfigSemanticPatchService.PatchAnalysis patch =
                semanticPatchService.build(
                        configType,
                        activeSnapshot,
                        snapshot);
        verifyExpectedState(
                request,
                contentHash,
                active == null ? null : active.getId(),
                false,
                null);
        if (active != null
                && Objects.equals(contentHash, active.getContentHash())) {
            activateOnOwner(
                    configType,
                    configId,
                    active,
                    active.getContentHash());
            recordEntityUiAsset(
                    configType, configId, active, request);
            log.info(
                    "UI配置发布复用现有版本: configType={}, configId={}, releaseId={}, releaseVersion={}, releaseMode=STANDARD, reason=CONTENT_UNCHANGED",
                    LogValue.safe(configType),
                    LogValue.safe(configId),
                    LogValue.safe(active.getId()),
                    active.getVersion());
            return active;
        }
        int nextVersion = Math.max(
                releaseMapper.findMaxVersion(
                        configType,
                        configId),
                active == null || active.getVersion() == null
                        ? 0 : active.getVersion()) + 1;
        deactivate(configType, configId);

        UiConfigRelease release = new UiConfigRelease();
        release.setConfigType(configType);
        release.setConfigId(configId);
        release.setVersion(nextVersion);
        release.setSnapshotDocument(document);
        release.setContentHash(contentHash);
        release.setStatus("ACTIVE");
        release.setDescription(blankToNull(
                request == null ? null : request.getDescription()));
        release.setReleaseMode(STANDARD);
        release.setRiskLevel(patch.riskLevel());
        release.setOverrideRisk(0);
        release.setPublishedBy(UserContext.getUserId());
        release.setPublishedAt(LocalDateTime.now());
        releaseMapper.insert(release);
        activateOnOwner(configType, configId, release, contentHash);
        recordAudit(
                configType,
                configId,
                release.getId(),
                "PUBLISH_STANDARD",
                patch.riskLevel(),
                request == null ? null : request.getDescription(),
                Map.of(
                        "version", release.getVersion(),
                        "contentHash", contentHash,
                        "riskItems", patch.riskItems()));
        recordEntityUiAsset(
                configType, configId, release, request);
        log.info(
                "UI配置标准发布完成: configType={}, configId={}, releaseId={}, releaseVersion={}, previousReleaseId={}, contentHash={}, operatorId={}",
                LogValue.safe(configType),
                LogValue.safe(configId),
                LogValue.safe(release.getId()),
                release.getVersion(),
                LogValue.safe(
                        active == null ? null : active.getId()),
                LogValue.safe(contentHash),
                LogValue.safe(UserContext.getUserId()));
        return release;
    }

    /**
     * 发布热修复；后续由接收方或异步任务继续处理。
     *
     * @param configType 配置类型标识，决定后续热修复采用的处理分支
     * @param configId 配置ID，后续用于发布热修复时定位或关联目标
     * @param request 本次请求，后续经校验后用于发布热修复
     * @return 发布后的热修复结果，供调用方继续处理
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
    private UiConfigRelease publishHotfix(
            String configType,
            String configId,
            UiConfigPublishRequest request) {
        configurationAccessService.requireHotfixAccess(false);
        lockOwner(configType, configId);
        HotfixPreparation preparation =
                prepareHotfix(configType, configId, request);
        log.info(
                "UI配置热发布预检完成: configType={}, configId={}, activeReleaseId={}, activeVersion={}, riskLevel={}, targetCount={}, processVersionCount={}, activeInstanceCount={}, canPublish={}",
                LogValue.safe(configType),
                LogValue.safe(configId),
                LogValue.safe(
                        preparation.preview()
                                .getActiveReleaseId()),
                preparation.preview().getActiveVersion(),
                LogValue.safe(
                        preparation.preview().getRiskLevel()),
                preparation.targets().size(),
                preparation.preview().getProcessVersionCount(),
                preparation.preview().getActiveInstanceCount(),
                preparation.preview().isCanPublish());
        verifyExpectedState(
                request,
                preparation.preview().getDraftHash(),
                preparation.preview().getActiveReleaseId(),
                true,
                preparation.preview().getImpactToken());
        if (!preparation.preview().isCanPublish()) {
            throw new BusinessConflictException(
                    "HOTFIX_NOT_COMPATIBLE",
                    String.join("；", preparation.preview().getBlockers()));
        }

        String governanceRequestId =
                requireHotfixGovernance().beginDirectPublish(
                        request,
                        preparation.preview());

        UiConfigRelease active = preparation.active();
        int nextVersion = Math.max(
                releaseMapper.findMaxVersion(
                        configType,
                        configId),
                active.getVersion() == null
                        ? 0 : active.getVersion()) + 1;
        deactivate(configType, configId);

        UiConfigRelease release = new UiConfigRelease();
        release.setConfigType(configType);
        release.setConfigId(configId);
        release.setVersion(nextVersion);
        release.setSnapshotDocument(preparation.draftDocument());
        release.setContentHash(preparation.preview().getDraftHash());
        release.setStatus("ACTIVE");
        release.setDescription(blankToNull(request.getDescription()));
        release.setReleaseMode(HOTFIX);
        release.setBaseReleaseId(active.getId());
        release.setRiskLevel(preparation.preview().getRiskLevel());
        release.setRolloutScope(ACTIVE_AND_FUTURE);
        release.setPatchDocument(semanticPatchService.writePatch(
                preparation.patch().operations()));
        release.setOverrideRisk(0);
        release.setOverrideReason(null);
        release.setPublishedBy(UserContext.getUserId());
        release.setPublishedAt(LocalDateTime.now());
        releaseMapper.insert(release);

        for (PreparedHotfixTarget prepared : preparation.targets()) {
            UiConfigHotfixTarget previous = prepared.previous();
            if (previous != null) {
                UpdateWrapper<UiConfigHotfixTarget> previousUpdate =
                        new UpdateWrapper<>();
                previousUpdate.eq("id", previous.getId())
                        .eq("status", "ACTIVE")
                        .set("status", "SUPERSEDED");
                if (hotfixTargetMapper.update(null, previousUpdate) != 1) {
                    throw new BusinessConflictException(
                            "HOTFIX_IMPACT_CHANGED",
                            "热修复目标已发生变化，请重新预检");
                }
            }
            UiConfigHotfixTarget target = new UiConfigHotfixTarget();
            target.setHotfixReleaseId(release.getId());
            target.setConfigType(configType);
            target.setConfigId(configId);
            target.setProcessVersionHistoryId(
                    prepared.target().processVersionHistoryId());
            target.setPinnedReleaseId(
                    prepared.target().pinnedReleaseId());
            target.setPinnedReleaseVersion(
                    prepared.target().pinnedReleaseVersion());
            target.setPreviousTargetId(
                    prepared.restorablePreviousTargetId());
            target.setEffectiveSnapshotDocument(
                    prepared.effectiveDocument());
            target.setEffectiveContentHash(
                    prepared.effectiveHash());
            target.setStatus("ACTIVE");
            target.setActivatedBy(UserContext.getUserId());
            target.setActivatedAt(LocalDateTime.now());
            hotfixTargetMapper.insert(target);
        }

        activateOnOwner(
                configType,
                configId,
                release,
                preparation.preview().getDraftHash());
        recordAudit(
                configType,
                configId,
                release.getId(),
                "PUBLISH_HOTFIX",
                preparation.preview().getRiskLevel(),
                request.getDescription(),
                auditDetail(preparation.preview()));
        requireHotfixGovernance().markPublished(
                governanceRequestId,
                release.getId());
        log.info(
                "UI配置热发布完成: configType={}, configId={}, releaseId={}, releaseVersion={}, baseReleaseId={}, riskLevel={}, targetCount={}, operatorId={}",
                LogValue.safe(configType),
                LogValue.safe(configId),
                LogValue.safe(release.getId()),
                release.getVersion(),
                LogValue.safe(release.getBaseReleaseId()),
                LogValue.safe(release.getRiskLevel()),
                preparation.targets().size(),
                LogValue.safe(UserContext.getUserId()));
        return release;
    }

    /**
     * 准备热修复；结果供调用方的后续步骤使用。
     *
     * @param configType 配置类型标识，决定后续热修复采用的处理分支
     * @param configId 配置ID，后续用于准备热修复时定位或关联目标
     * @param request 本次请求，后续经校验后用于准备热修复
     * @return 准备后的热修复结果，供调用方继续处理
     */
    private HotfixPreparation prepareHotfix(
            String configType,
            String configId,
            UiConfigPublishRequest request) {
        requireType(configType);
        Map<String, Object> draft =
                buildDraftSnapshot(configType, configId);
        validateForPublish(configType, configId, draft);
        String draftDocument = snapshotSupport.canonical(draft);
        String draftHash = snapshotSupport.hash(draftDocument);
        UiConfigRelease active =
                releaseMapper.findActive(configType, configId);
        UiConfigDiffDTO diff = diff(configType, configId);
        List<String> blockers = new ArrayList<>();
        if (!diff.isChanged()) {
            blockers.add("当前草稿与已发布版本一致");
        }
        if (active == null) {
            blockers.add("兼容热修复必须存在可追溯的激活基线版本");
        }
        Map<String, Object> activeSnapshot = active == null
                ? Map.of() : verifiedSnapshot(active);
        UiConfigSemanticPatchService.PatchAnalysis patch =
                semanticPatchService.build(
                        configType,
                        activeSnapshot,
                        draft);
        patch = enforceExtensionHotfixCapabilities(draft, patch);
        String effectiveRisk = patch.riskLevel();
        UiHotfixProcessImpact impact = FORM.equals(configType)
                && active != null
                ? processImpactPort.analyzeFormImpact(configId)
                : UiHotfixProcessImpact.empty();
        List<PreparedHotfixTarget> preparedTargets = new ArrayList<>();
        List<UiConfigHotfixTargetPreviewDTO> targetPreviews =
                new ArrayList<>();
        for (UiHotfixProcessTarget target : impact.targets()) {
            List<String> targetBlockers = new ArrayList<>();
            List<String> targetReviewNotes = new ArrayList<>();
            UiConfigHotfixTarget previous = null;
            String effectiveDocument = null;
            String effectiveHash = null;
            String applicationMode = HOTFIX_PATCH;
            String restorablePreviousTargetId = null;
            if (!StringUtils.hasText(target.pinnedReleaseId())
                    || target.pinnedReleaseVersion() == null) {
                targetBlockers.add(
                        "同一流程版本引用了不一致或缺失的表单发布版本");
            } else {
                previous = hotfixTargetMapper.findActiveTarget(
                        configType,
                        configId,
                        target.processVersionHistoryId());
                Map<String, Object> baseSnapshot;
                if (previous == null) {
                    baseSnapshot = pinnedSnapshot(
                            configType,
                            configId,
                            target.pinnedReleaseId(),
                            target.pinnedReleaseVersion(),
                            targetBlockers);
                } else {
                    List<String> previousSnapshotProblems =
                            new ArrayList<>();
                    baseSnapshot = verifiedTargetSnapshot(
                            previous,
                            previousSnapshotProblems);
                    if (baseSnapshot == null) {
                        targetReviewNotes.addAll(
                                previousSnapshotProblems);
                        applicationMode = HOTFIX_FULL_SNAPSHOT;
                        effectiveRisk = maxRisk(
                                effectiveRisk,
                                UiConfigSemanticPatchService.REVIEW);
                        // 仍需确认原始钉定版本可用，保证强制发布可安全撤回。
                        pinnedSnapshot(
                                configType,
                                configId,
                                target.pinnedReleaseId(),
                                target.pinnedReleaseVersion(),
                                targetBlockers);
                    } else {
                        restorablePreviousTargetId =
                                previous.getId();
                    }
                }
                if (HOTFIX_FULL_SNAPSHOT.equals(applicationMode)
                        && targetBlockers.isEmpty()) {
                    EffectiveHotfixSnapshot effective =
                            prepareFullSnapshotFallback(
                                    configType,
                                    configId,
                                    draft,
                                    targetReviewNotes,
                                    targetBlockers);
                    if (effective != null) {
                        effectiveDocument = effective.document();
                        effectiveHash = effective.hash();
                    }
                } else if (baseSnapshot != null
                        && targetBlockers.isEmpty()) {
                    UiConfigSemanticPatchService.PatchApplication application =
                            semanticPatchService.apply(
                                    baseSnapshot,
                                    patch.operations(),
                                    true);
                    if (application.diverged()) {
                        effectiveRisk = maxRisk(
                                effectiveRisk,
                                UiConfigSemanticPatchService.REVIEW);
                    }
                    if (application.compatible()) {
                        try {
                            EffectiveHotfixSnapshot effective =
                                    validatedEffectiveSnapshot(
                                            configType,
                                            configId,
                                            application.snapshot());
                            effectiveDocument = effective.document();
                            effectiveHash = effective.hash();
                        } catch (RuntimeException exception) {
                            targetReviewNotes.add(
                                    "增量合成快照校验失败，已改为完整快照覆盖："
                                            + exception.getMessage());
                            applicationMode =
                                    HOTFIX_FULL_SNAPSHOT;
                        }
                    } else {
                        targetReviewNotes.add(
                                "增量补丁无法对齐旧版本，已改为完整快照覆盖："
                                        + String.join(
                                                "；",
                                                application.blockers()));
                        applicationMode = HOTFIX_FULL_SNAPSHOT;
                    }
                    if (HOTFIX_FULL_SNAPSHOT.equals(applicationMode)) {
                        effectiveRisk = maxRisk(
                                effectiveRisk,
                                UiConfigSemanticPatchService.REVIEW);
                        EffectiveHotfixSnapshot effective =
                                prepareFullSnapshotFallback(
                                        configType,
                                        configId,
                                        draft,
                                        targetReviewNotes,
                                        targetBlockers);
                        if (effective != null) {
                            effectiveDocument = effective.document();
                            effectiveHash = effective.hash();
                        }
                    }
                }
            }
            if (targetBlockers.isEmpty()) {
                preparedTargets.add(new PreparedHotfixTarget(
                        target,
                        previous,
                        restorablePreviousTargetId,
                        effectiveDocument,
                        effectiveHash));
            } else {
                targetBlockers.forEach(blocker -> blockers.add(
                        target.processKey()
                                + "@v"
                                + target.processVersion()
                                + "："
                                + blocker));
            }
            targetPreviews.add(
                    UiConfigHotfixTargetPreviewDTO.builder()
                            .processVersionHistoryId(
                                    target.processVersionHistoryId())
                            .processConfigId(target.processConfigId())
                            .processKey(target.processKey())
                            .processName(target.processName())
                            .processVersion(target.processVersion())
                            .pinnedReleaseId(target.pinnedReleaseId())
                            .pinnedReleaseVersion(
                                    target.pinnedReleaseVersion())
                            .nodeIds(target.nodeIds())
                            .currentStartable(target.currentStartable())
                            .activeInstanceCount(
                                    target.activeInstanceCount())
                            .skippedHistoricalInstanceCount(
                                    target.completedInstanceCount())
                            .compatible(targetBlockers.isEmpty())
                            .applicationMode(applicationMode)
                            .reviewNotes(List.copyOf(targetReviewNotes))
                            .blockers(List.copyOf(targetBlockers))
                            .build());
        }
        String targetHash = FORM.equals(configType)
                ? impact.targetHash()
                : "GLOBAL_ACTIVE:"
                        + (active == null ? "NONE" : active.getId());
        String token = impactToken(
                configType,
                configId,
                HOTFIX,
                draftHash,
                active == null ? null : active.getId(),
                targetHash,
                effectiveRisk);
        UiConfigPublishPreviewDTO preview =
                UiConfigPublishPreviewDTO.builder()
                        .configType(configType)
                        .configId(configId)
                        .releaseMode(HOTFIX)
                        .rolloutScope(ACTIVE_AND_FUTURE)
                        .draftHash(draftHash)
                        .activeReleaseId(
                                active == null ? null : active.getId())
                        .activeVersion(
                                active == null ? null : active.getVersion())
                        .targetHash(targetHash)
                        .impactToken(token)
                        .riskLevel(effectiveRisk)
                        .changed(diff.isChanged())
                        .requiresOverride(false)
                        .canPublish(blockers.isEmpty())
                        .processVersionCount(impact.processVersionCount())
                        .activeInstanceCount(impact.activeInstanceCount())
                        .skippedHistoricalInstanceCount(
                                impact.skippedHistoricalInstanceCount())
                        .changedItems(diff.getChangedItems())
                        .riskItems(patch.riskItems())
                        .targets(List.copyOf(targetPreviews))
                        .dependencies(publishDependencies(draft))
                        .blockers(List.copyOf(blockers))
                        .build();
        return new HotfixPreparation(
                active,
                draftDocument,
                patch,
                List.copyOf(preparedTargets),
                preview);
    }

    /**
     * 准备{@code full}快照兜底；结果供调用方的后续步骤使用。
     *
     * @param configType 配置类型标识，决定后续{@code full}快照兜底采用的处理分支
     * @param configId 配置ID，后续用于准备{@code full}快照兜底时定位或关联目标
     * @param draft 草稿，作为 {@code validatedEffectiveSnapshot} 的输入影响后续处理
     * @param reviewNotes {@code review}{@code notes}，供本方法准备{@code full}快照兜底时使用
     * @param blockers 阻断项，供本方法准备{@code full}快照兜底时使用
     * @return 准备后的{@code full}快照兜底结果，供调用方继续处理
     */
    private EffectiveHotfixSnapshot prepareFullSnapshotFallback(
            String configType,
            String configId,
            Map<String, Object> draft,
            List<String> reviewNotes,
            List<String> blockers) {
        try {
            EffectiveHotfixSnapshot effective =
                    validatedEffectiveSnapshot(
                            configType,
                            configId,
                            draft);
            reviewNotes.add(
                    "该流程版本将使用当前草稿的完整快照强制覆盖，"
                            + "发布后不再依赖异常或无法对齐的旧目标快照");
            return effective;
        } catch (RuntimeException exception) {
            blockers.add(
                    "完整快照覆盖校验失败："
                            + exception.getMessage());
            return null;
        }
    }

    /**
     * 处理已校验有效快照，并将结果传给后续步骤。
     *
     * @param configType 配置类型标识，决定后续已校验有效快照采用的处理分支
     * @param configId 配置ID，后续用于处理已校验有效快照时定位或关联目标
     * @param snapshot 快照，作为 {@code validateSnapshotForActivation} 的输入影响后续处理
     * @return 处理后的已校验有效快照结果，供调用方继续处理
     */
    private EffectiveHotfixSnapshot validatedEffectiveSnapshot(
            String configType,
            String configId,
            Map<String, Object> snapshot) {
        validateSnapshotForActivation(
                configType,
                configId,
                snapshot);
        String document = snapshotSupport.canonical(snapshot);
        return new EffectiveHotfixSnapshot(
                document,
                snapshotSupport.hash(document));
    }

    /**
     * 处理{@code enforce}扩展热修复能力集合，并将结果传给后续步骤。
     *
     * @param draft 草稿，作为 {@code indexByStableId} 的输入影响后续处理
     * @param patch 补丁，供本方法处理{@code enforce}扩展热修复能力集合时使用
     * @return 处理后的{@code enforce}扩展热修复能力集合结果，供调用方继续处理
     */
    private UiConfigSemanticPatchService.PatchAnalysis
            enforceExtensionHotfixCapabilities(
                    Map<String, Object> draft,
                    UiConfigSemanticPatchService.PatchAnalysis patch) {
        Map<String, Map<String, Object>> nodes = indexByStableId(
                mapList(draft.get("nodes")),
                List.of("id", "nodeKey"));
        List<UiConfigSemanticPatchOperation> operations =
                new ArrayList<>();
        for (UiConfigSemanticPatchOperation source
                : patch.operations()) {
            UiConfigSemanticPatchOperation operation =
                    objectMapper.convertValue(
                            source,
                            UiConfigSemanticPatchOperation.class);
            if ("form".equals(operation.getSection())
                    && "/customComponentVersion".equals(
                            operation.getPath())) {
                Map<String, Object> form =
                        mapValue(draft.get("form"));
                String componentName =
                        text(form.get("customComponent"));
                Integer componentVersion = nullableInteger(
                        form.get("customComponentVersion"));
                boolean compatible = extensionSupportsHotfix(
                        "FORM",
                        componentName,
                        componentVersion);
                operation.setRiskLevel(
                        UiConfigSemanticPatchService.REVIEW);
                operation.setReason(compatible
                        ? "自定义表单组件声明兼容热修复，版本变更需要风险确认"
                        : "自定义表单组件未声明热修复兼容能力，按高风险变更复核");
            } else if ("nodes".equals(operation.getSection())
                    && operation.getPath().endsWith(
                            "/componentVersion")) {
                Map<String, Object> node =
                        nodes.get(operation.getItemId());
                String componentName = node == null
                        ? null : text(node.get("componentName"));
                if (StringUtils.hasText(componentName)) {
                    Integer componentVersion = nullableInteger(
                            node.get("componentVersion"));
                    boolean compatible = extensionSupportsHotfix(
                            nodeExtensionType(node),
                            componentName,
                            componentVersion);
                    if (!compatible) {
                        operation.setRiskLevel(
                                UiConfigSemanticPatchService.REVIEW);
                        operation.setReason(
                                "自定义组件未声明热修复兼容能力，按高风险变更复核");
                    }
                }
            }
            operations.add(operation);
        }
        String riskLevel = operations.stream()
                .map(UiConfigSemanticPatchOperation::getRiskLevel)
                .reduce(
                        UiConfigSemanticPatchService.SAFE,
                        this::maxRisk);
        List<UiConfigHotfixRiskItemDTO> risks = operations.stream()
                .map(item -> UiConfigHotfixRiskItemDTO.builder()
                        .section(item.getSection())
                        .itemId(item.getItemId())
                        .path(item.getPath())
                        .riskLevel(item.getRiskLevel())
                        .reason(item.getReason())
                        .build())
                .toList();
        return new UiConfigSemanticPatchService.PatchAnalysis(
                List.copyOf(operations),
                riskLevel,
                risks);
    }

    /**
     * 判断扩展{@code supports}热修复条件是否成立，供调用方选择后续分支。
     *
     * @param extensionType 扩展类型标识，决定后续扩展{@code supports}热修复采用的处理分支
     * @param componentName 组件名称，后续用于处理扩展{@code supports}热修复时匹配或展示
     * @param componentVersion 组件版本，供本方法处理扩展{@code supports}热修复时使用
     * @return 扩展{@code supports}热修复条件成立时为 true，否则为 false
     */
    private boolean extensionSupportsHotfix(
            String extensionType,
            String componentName,
            Integer componentVersion) {
        if (!StringUtils.hasText(componentName)) {
            return false;
        }
        try {
            return extensionDefinitionService.supportsHotfix(
                    extensionDefinitionService.requireActive(
                            extensionType,
                            componentName,
                            componentVersion));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 整理固定快照数据，供调用方遍历或继续处理。
     *
     * @param configType 配置类型标识，决定后续固定快照采用的处理分支
     * @param configId 配置ID，后续用于处理固定快照时定位或关联目标
     * @param releaseId 发布版本ID，后续用于处理固定快照时定位或关联目标
     * @param version 版本，供本方法处理固定快照时使用
     * @param blockers 阻断项，供本方法处理固定快照时使用
     * @return 固定快照键值结果，供调用方继续处理
     */
    private Map<String, Object> pinnedSnapshot(
            String configType,
            String configId,
            String releaseId,
            Integer version,
            List<String> blockers) {
        UiConfigRelease pinned = releaseMapper.selectById(releaseId);
        if (pinned == null
                || !Objects.equals(configType, pinned.getConfigType())
                || !Objects.equals(configId, pinned.getConfigId())
                || !Objects.equals(version, pinned.getVersion())) {
            blockers.add("流程钉定的原始发布版本不存在或不属于当前配置");
            return null;
        }
        try {
            return verifiedSnapshot(pinned);
        } catch (RuntimeException exception) {
            blockers.add("原始发布快照完整性校验失败");
            return null;
        }
    }

    /**
     * 整理已验证目标快照数据，供调用方遍历或继续处理。
     *
     * @param target 目标，作为 {@code codec.readObject} 的输入影响后续处理
     * @param blockers 阻断项，供本方法处理已验证目标快照时使用
     * @return 已验证目标快照键值结果，供调用方继续处理
     */
    private Map<String, Object> verifiedTargetSnapshot(
            UiConfigHotfixTarget target,
            List<String> blockers) {
        try {
            Map<String, Object> snapshot = codec.readObject(
                    target.getEffectiveSnapshotDocument(),
                    "热修复目标有效快照");
            String actualHash = snapshotSupport.hash(
                    snapshotSupport.canonical(snapshot));
            if (!Objects.equals(
                    actualHash,
                    target.getEffectiveContentHash())) {
                blockers.add("上一有效热修复快照完整性校验失败");
                return null;
            }
            return snapshot;
        } catch (RuntimeException exception) {
            blockers.add("上一有效热修复快照无法解析");
            return null;
        }
    }

    /**
     * 验证预期状态；不满足约束时阻止后续处理。
     *
     * @param request 本次请求，后续经校验后用于验证预期状态
     * @param actualDraftHash 实际草稿哈希，供本方法验证预期状态时使用
     * @param actualActiveReleaseId 实际活动发布版本ID，后续用于验证预期状态时定位或关联目标
     * @param requireImpactToken {@code require}影响令牌，后续用于授权校验、关联或幂等去重
     * @param actualImpactToken 实际影响令牌，后续用于授权校验、关联或幂等去重
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
    private void verifyExpectedState(
            UiConfigPublishRequest request,
            String actualDraftHash,
            String actualActiveReleaseId,
            boolean requireImpactToken,
            String actualImpactToken) {
        if (request == null) {
            if (requireImpactToken) {
                throw new BusinessConflictException(
                        "HOTFIX_PREVIEW_REQUIRED",
                        "兼容热修复必须先执行发布预检");
            }
            return;
        }
        if (StringUtils.hasText(request.getExpectedDraftHash())
                && !Objects.equals(
                        request.getExpectedDraftHash(),
                        actualDraftHash)) {
            throw new BusinessConflictException(
                    "HOTFIX_IMPACT_CHANGED",
                    "草稿已发生变化，请重新预检");
        }
        if (StringUtils.hasText(request.getExpectedActiveReleaseId())
                && !Objects.equals(
                        request.getExpectedActiveReleaseId(),
                        actualActiveReleaseId)) {
            throw new BusinessConflictException(
                    "HOTFIX_IMPACT_CHANGED",
                    "当前激活发布版本已变化，请重新预检");
        }
        if (requireImpactToken
                && (!StringUtils.hasText(request.getImpactToken())
                || !Objects.equals(
                        request.getImpactToken(),
                        actualImpactToken))) {
            throw new BusinessConflictException(
                    "HOTFIX_IMPACT_CHANGED",
                    "热修复影响范围已变化，请重新预检");
        }
    }

    /**
     * 生成影响令牌文本，供后续匹配或展示。
     *
     * @param configType 配置类型标识，决定后续影响令牌采用的处理分支
     * @param configId 配置ID，后续用于处理影响令牌时定位或关联目标
     * @param releaseMode 发布版本模式标识，决定后续影响令牌采用的处理分支
     * @param draftHash 草稿哈希，作为 {@code nullToEmpty} 的输入影响后续处理
     * @param activeReleaseId 活动发布版本ID，后续用于处理影响令牌时定位或关联目标
     * @param targetHash 目标哈希，供本方法处理影响令牌时使用
     * @param riskLevel 风险层级，供本方法处理影响令牌时使用
     * @return 处理后的影响令牌文本，供调用方比较或展示
     */
    private String impactToken(
            String configType,
            String configId,
            String releaseMode,
            String draftHash,
            String activeReleaseId,
            String targetHash,
            String riskLevel) {
        return snapshotSupport.hash(String.join(
                "|",
                nullToEmpty(configType),
                nullToEmpty(configId),
                nullToEmpty(releaseMode),
                nullToEmpty(draftHash),
                nullToEmpty(activeReleaseId),
                nullToEmpty(targetHash),
                nullToEmpty(riskLevel)));
    }

    /**
     * 生成发布版本模式文本，供后续匹配或展示。
     *
     * @param request 本次请求，后续经校验后用于处理发布版本模式
     * @return 处理后的发布版本模式文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String releaseMode(UiConfigPublishRequest request) {
        String value = request == null
                ? STANDARD : normalize(request.getReleaseMode());
        if (!StringUtils.hasText(value)) {
            return STANDARD;
        }
        if (!Set.of(STANDARD, HOTFIX).contains(value)) {
            throw new IllegalArgumentException("发布模式仅支持 STANDARD 或 HOTFIX");
        }
        if (HOTFIX.equals(value)
                && StringUtils.hasText(request.getRolloutScope())
                && !ACTIVE_AND_FUTURE.equals(
                        normalize(request.getRolloutScope()))) {
            throw new IllegalArgumentException(
                    "热修复生效范围仅支持 ACTIVE_AND_FUTURE");
        }
        return value;
    }

    /**
     * 校验并获取热修复{@code supported}；不满足约束时阻止后续处理。
     *
     * @param configType 配置类型标识，决定后续热修复{@code supported}采用的处理分支
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
    private void requireHotfixSupported(String configType) {
        if (LIST.equals(configType)) {
            throw new BusinessConflictException(
                    "LIST_HOTFIX_NOT_SUPPORTED",
                    "列表运行时统一使用当前激活版本，请使用普通发布和历史版本激活");
        }
    }

    /**
     * 生成空值截止空文本，供后续匹配或展示。
     *
     * @param value 待处理空值截止空的原始输入，结果供调用方继续使用
     * @return 处理后的空值截止空文本，供调用方比较或展示
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 校验并获取热修复治理；不满足约束时阻止后续处理。
     *
     * @return 校验并获取后的热修复治理结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private UiHotfixGovernanceService requireHotfixGovernance() {
        if (hotfixGovernanceService == null) {
            throw new IllegalStateException(
                    "UI HOTFIX 治理服务未初始化，已拒绝发布或回滚");
        }
        return hotfixGovernanceService;
    }

    /**
     * 记录热修复指标{@code safely}；供后续追溯或审计使用。
     *
     * @param releaseId 发布版本ID，后续用于记录热修复指标{@code safely}时定位或关联目标
     * @param metricCode 指标编码，后续用于记录热修复指标{@code safely}时定位或关联目标
     * @param successful 成功，作为 {@code hotfixGovernanceService.recordReleaseMetric} 的输入影响后续处理
     * @param errorMessage 错误消息，作为 {@code hotfixGovernanceService.recordReleaseMetric} 的输入影响后续处理
     */
    private void recordHotfixMetricSafely(
            String releaseId,
            String metricCode,
            boolean successful,
            String errorMessage) {
        if (hotfixGovernanceService == null) {
            return;
        }
        try {
            hotfixGovernanceService.recordReleaseMetric(
                    releaseId,
                    metricCode,
                    successful,
                    errorMessage);
        } catch (RuntimeException metricException) {
            log.warn(
                    "记录 UI HOTFIX 观察指标失败: releaseId={}, metricCode={}, failureType={}",
                    LogValue.safe(releaseId),
                    LogValue.safe(metricCode),
                    LogValue.failureType(metricException));
        }
    }

    /**
     * 记录热修复配置指标{@code safely}；供后续追溯或审计使用。
     *
     * @param configType 配置类型标识，决定后续热修复配置指标{@code safely}采用的处理分支
     * @param configId 配置ID，后续用于记录热修复配置指标{@code safely}时定位或关联目标
     * @param metricCode 指标编码，后续用于记录热修复配置指标{@code safely}时定位或关联目标
     * @param successful 成功，作为 {@code hotfixGovernanceService.recordConfigMetric} 的输入影响后续处理
     * @param errorMessage 错误消息，作为 {@code hotfixGovernanceService.recordConfigMetric} 的输入影响后续处理
     */
    private void recordHotfixConfigMetricSafely(
            String configType,
            String configId,
            String metricCode,
            boolean successful,
            String errorMessage) {
        if (hotfixGovernanceService == null) {
            return;
        }
        try {
            hotfixGovernanceService.recordConfigMetric(
                    configType,
                    configId,
                    metricCode,
                    successful,
                    errorMessage);
        } catch (RuntimeException metricException) {
            log.warn(
                    "按配置记录 UI HOTFIX 观察指标失败: configType={}, configId={}, metricCode={}, failureType={}",
                    LogValue.safe(configType),
                    LogValue.safe(configId),
                    LogValue.safe(metricCode),
                    LogValue.failureType(metricException));
        }
    }

    /**
     * 生成最大风险文本，供后续匹配或展示。
     *
     * @param left 左侧，供本方法处理最大风险时使用
     * @param right 右侧，作为 {@code contains} 的输入影响后续处理
     * @return 处理后的最大风险文本，供调用方比较或展示
     */
    private String maxRisk(String left, String right) {
        if (Set.of(
                UiConfigSemanticPatchService.REVIEW,
                UiConfigSemanticPatchService.BLOCKED).contains(left)
                || Set.of(
                        UiConfigSemanticPatchService.REVIEW,
                        UiConfigSemanticPatchService.BLOCKED).contains(right)) {
            return UiConfigSemanticPatchService.REVIEW;
        }
        return UiConfigSemanticPatchService.SAFE;
    }

    /**
     * 按最后发布顺序撤回热修复，不修改不可变发布记录。
     *
     * @param configType 配置类型标识，决定后续回滚热修复采用的处理分支
     * @param configId 配置ID，后续用于处理回滚热修复时定位或关联目标
     * @param releaseId 发布版本ID，后续用于处理回滚热修复时定位或关联目标
     * @param reason 原因，供本方法处理回滚热修复时使用
     * @return 处理后的回滚热修复结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public UiConfigRelease rollbackHotfix(
            String configType,
            String configId,
            String releaseId,
            String reason) {
        log.info(
                "开始撤回UI配置热发布: configType={}, configId={}, releaseId={}, reasonPresent={}, operatorId={}",
                LogValue.safe(configType),
                LogValue.safe(configId),
                LogValue.safe(releaseId),
                StringUtils.hasText(reason),
                LogValue.safe(UserContext.getUserId()));
        requireHotfixGovernance().authorizeRollback(
                releaseId,
                reason);
        lockOwner(configType, configId);
        UiConfigRelease release = releaseMapper.selectById(releaseId);
        if (release == null
                || !Objects.equals(configType, release.getConfigType())
                || !Objects.equals(configId, release.getConfigId())
                || !HOTFIX.equals(release.getReleaseMode())) {
            throw new IllegalArgumentException("热修复发布版本不存在");
        }
        List<UiConfigHotfixTarget> targets =
                hotfixTargetMapper.findByHotfixReleaseId(releaseId);
        UiConfigRelease current = releaseMapper.findActive(
                configType,
                configId);
        if (targets.stream().anyMatch(target ->
                "SUPERSEDED".equals(target.getStatus()))) {
            throw new BusinessConflictException(
                    "HOTFIX_ROLLBACK_ORDER_CONFLICT",
                    "存在更新的热修复，必须按发布时间逆序撤回");
        }
        boolean hasActiveTargets = targets.stream().anyMatch(target ->
                "ACTIVE".equals(target.getStatus()));
        if ((!targets.isEmpty() && !hasActiveTargets)
                || (targets.isEmpty()
                        && (current == null
                                || !Objects.equals(
                                        current.getId(),
                                        releaseId)))) {
            throw new BusinessConflictException(
                    "HOTFIX_ROLLBACK_ORDER_CONFLICT",
                    "该热修复已失效或存在更新发布，不能重复或越序撤回");
        }
        for (UiConfigHotfixTarget target : targets) {
            if (!"ACTIVE".equals(target.getStatus())) {
                continue;
            }
            UpdateWrapper<UiConfigHotfixTarget> rollback =
                    new UpdateWrapper<>();
            rollback.eq("id", target.getId())
                    .eq("status", "ACTIVE")
                    .set("status", "ROLLED_BACK")
                    .set("rolled_back_by", UserContext.getUserId())
                    .set("rolled_back_at", LocalDateTime.now());
            if (hotfixTargetMapper.update(null, rollback) != 1) {
                throw new BusinessConflictException(
                        "HOTFIX_IMPACT_CHANGED",
                        "热修复目标已发生变化，请刷新后重试");
            }
            if (StringUtils.hasText(target.getPreviousTargetId())) {
                UpdateWrapper<UiConfigHotfixTarget> restore =
                        new UpdateWrapper<>();
                restore.eq("id", target.getPreviousTargetId())
                        .eq("status", "SUPERSEDED")
                        .set("status", "ACTIVE");
                if (hotfixTargetMapper.update(null, restore) != 1) {
                    throw new BusinessConflictException(
                            "HOTFIX_ROLLBACK_ORDER_CONFLICT",
                            "上一热修复目标无法恢复");
                }
            }
        }
        if (current != null
                && Objects.equals(current.getId(), releaseId)
                && StringUtils.hasText(release.getBaseReleaseId())) {
            UiConfigRelease base =
                    releaseMapper.selectById(release.getBaseReleaseId());
            if (base == null
                    || !Objects.equals(configType, base.getConfigType())
                    || !Objects.equals(configId, base.getConfigId())) {
                throw new BusinessConflictException(
                        "HOTFIX_BASE_RELEASE_MISSING",
                        "热修复基线版本不存在，无法回滚");
            }
            // 只有内容哈希仍可验证的不可变发布快照才允许成为回滚目标。
            verifiedSnapshot(base);
            deactivate(configType, configId);
            UpdateWrapper<UiConfigRelease> activateBase =
                    new UpdateWrapper<>();
            activateBase.eq("id", base.getId())
                    .set("status", "ACTIVE");
            releaseMapper.update(null, activateBase);
            base.setStatus("ACTIVE");
            switchActiveReleaseOnOwner(
                    configType,
                    configId,
                    base);
            release.setStatus("INACTIVE");
        }
        recordAudit(
                configType,
                configId,
                releaseId,
                "ROLLBACK_HOTFIX",
                release.getRiskLevel(),
                reason,
                Map.of("targetCount", targets.size()));
        requireHotfixGovernance().markRolledBack(
                releaseId,
                reason);
        log.info(
                "UI配置热发布撤回完成: configType={}, configId={}, releaseId={}, baseReleaseId={}, targetCount={}, resultingStatus={}, operatorId={}",
                LogValue.safe(configType),
                LogValue.safe(configId),
                LogValue.safe(releaseId),
                LogValue.safe(release.getBaseReleaseId()),
                targets.size(),
                LogValue.safe(release.getStatus()),
                LogValue.safe(UserContext.getUserId()));
        return managementRelease(configType, release);
    }

    /**
     * 激活指定历史发布版本，校验快照完整性后切换激活状态。
     *
     * @param configType 配置类型
     * @param configId   配置ID
     * @param releaseId  要激活的发布记录ID
     * @return 激活的发布记录
     * @throws IllegalArgumentException 发布版本不存在或完整性校验失败时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public UiConfigRelease activate(
            String configType,
            String configId,
            String releaseId) {
        return managementRelease(
                configType,
                activateInternal(
                        configType,
                        configId,
                        releaseId,
                        null,
                        null));
    }

    /**
     * 激活界面配置发布版本；结果供调用方的后续步骤使用。
     *
     * @param configType 配置类型标识，决定后续界面配置发布版本采用的处理分支
     * @param configId 配置ID，后续用于激活界面配置发布版本时定位或关联目标
     * @param releaseId 发布版本ID，后续用于激活界面配置发布版本时定位或关联目标
     * @param reason 原因，供本方法激活界面配置发布版本时使用
     * @return 激活后的界面配置发布版本结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public UiConfigRelease activate(
            String configType,
            String configId,
            String releaseId,
            String reason) {
        return activate(
                configType,
                configId,
                releaseId,
                reason,
                null);
    }

    /**
     * 激活界面配置发布版本；结果供调用方的后续步骤使用。
     *
     * @param configType 配置类型标识，决定后续界面配置发布版本采用的处理分支
     * @param configId 配置ID，后续用于激活界面配置发布版本时定位或关联目标
     * @param releaseId 发布版本ID，后续用于激活界面配置发布版本时定位或关联目标
     * @param reason 原因，作为 {@code requireOperationReason} 的输入影响后续处理
     * @param expectedActiveReleaseId 预期活动发布版本ID，后续用于激活界面配置发布版本时定位或关联目标
     * @return 激活后的界面配置发布版本结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public UiConfigRelease activate(
            String configType,
            String configId,
            String releaseId,
            String reason,
            String expectedActiveReleaseId) {
        requireOperationReason(reason, "激活原因不能为空");
        return managementRelease(
                configType,
                activateInternal(
                        configType,
                        configId,
                        releaseId,
                        reason,
                        expectedActiveReleaseId));
    }

    /**
     * 激活内部；结果供调用方的后续步骤使用。
     *
     * @param configType 配置类型标识，决定后续内部采用的处理分支
     * @param configId 配置ID，后续用于激活内部时定位或关联目标
     * @param releaseId 发布版本ID，后续用于激活内部时定位或关联目标
     * @param reason 原因，供本方法激活内部时使用
     * @param expectedActiveReleaseId 预期活动发布版本ID，后续用于激活内部时定位或关联目标
     * @return 激活后的内部结果，供调用方继续处理
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private UiConfigRelease activateInternal(
            String configType,
            String configId,
            String releaseId,
            String reason,
            String expectedActiveReleaseId) {
        log.info(
                "开始激活UI配置历史版本: configType={}, configId={}, releaseId={}, operatorId={}",
                LogValue.safe(configType),
                LogValue.safe(configId),
                LogValue.safe(releaseId),
                LogValue.safe(UserContext.getUserId()));
        lockOwner(configType, configId);
        UiConfigRelease previous = releaseMapper.findActive(
                configType,
                configId);
        if (StringUtils.hasText(expectedActiveReleaseId)
                && !Objects.equals(
                        expectedActiveReleaseId,
                        previous == null ? null : previous.getId())) {
            throw new BusinessConflictException(
                    "UI_CONFIG_ACTIVE_RELEASE_CHANGED",
                    "当前激活版本已变化，请重新预览后再激活");
        }
        UiConfigRelease release = releaseMapper.selectById(releaseId);
        if (release == null
                || !configType.equals(release.getConfigType())
                || !configId.equals(release.getConfigId())) {
            throw new IllegalArgumentException("发布版本不存在");
        }
        if (HOTFIX.equals(release.getReleaseMode())) {
            throw new BusinessConflictException(
                    "HOTFIX_ACTIVATE_NOT_ALLOWED",
                    "热修复必须通过撤回入口按发布时间逆序回滚");
        }
        Map<String, Object> snapshot = verifiedSnapshot(release);
        validateSnapshotForActivation(configType, configId, snapshot);
        Map<String, Object> previousSnapshot = previous == null
                ? Map.of() : verifiedSnapshot(previous);
        UiConfigSemanticPatchService.PatchAnalysis activationPatch =
                semanticPatchService.build(
                        configType,
                        previousSnapshot,
                        snapshot);
        deactivate(configType, configId);
        UpdateWrapper<UiConfigRelease> releaseUpdate = new UpdateWrapper<>();
        releaseUpdate.eq("id", releaseId)
                .eq("config_type", configType)
                .eq("config_id", configId)
                .set("status", "ACTIVE");
        if (releaseMapper.update(null, releaseUpdate) != 1) {
            throw new BusinessConflictException(
                    "UI_CONFIG_RELEASE_ACTIVATE_CONFLICT",
                    "历史版本状态已变化，请刷新后重试");
        }
        release.setStatus("ACTIVE");
        switchActiveReleaseOnOwner(
                configType,
                configId,
                release);
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("previousReleaseId", previous == null
                ? null : previous.getId());
        audit.put("previousVersion", previous == null
                ? null : previous.getVersion());
        audit.put("activatedReleaseId", release.getId());
        audit.put("activatedVersion", release.getVersion());
        audit.put("riskItems", activationPatch.riskItems());
        recordAudit(
                configType,
                configId,
                release.getId(),
                "ACTIVATE_RELEASE",
                activationPatch.riskLevel(),
                reason,
                audit);
        recordEntityUiAsset(
                configType, configId, release, null);
        log.info(
                "UI配置历史版本激活完成: configType={}, configId={}, releaseId={}, releaseVersion={}, contentHash={}, operatorId={}",
                LogValue.safe(configType),
                LogValue.safe(configId),
                LogValue.safe(release.getId()),
                release.getVersion(),
                LogValue.safe(release.getContentHash()),
                LogValue.safe(UserContext.getUserId()));
        return release;
    }

    /**
     * 将列表历史发布快照恢复为可编辑草稿，不改变当前 ACTIVE 版本。
     *
     * @param configId 配置ID，后续用于恢复列表草稿时定位或关联目标
     * @param releaseId 发布版本ID，后续用于恢复列表草稿时定位或关联目标
     * @param reason 原因，作为 {@code requireOperationReason} 的输入影响后续处理
     * @return 恢复后的列表草稿结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityListConfigDTO restoreListDraft(
            String configId,
            String releaseId,
            String reason) {
        requireOperationReason(reason, "恢复原因不能为空");
        lockOwner(LIST, configId);
        UiConfigRelease release = requireListRelease(
                configId,
                releaseId,
                null);
        Map<String, Object> snapshot = verifiedSnapshot(release);
        Map<String, Object> currentDraft =
                buildDraftSnapshot(LIST, configId);
        Map<String, Object> restoredDraft =
                restoredListDraftSnapshot(
                        configId,
                        currentDraft,
                        snapshot);
        UiConfigSemanticPatchService.PatchAnalysis restorePatch =
                semanticPatchService.build(
                        LIST,
                        currentDraft,
                        restoredDraft);
        EntityListConfigDTO list = runtimeList(snapshot, configId);
        list.setId(configId);
        EntityListConfigDTO restored =
                listConfigService.saveConfigForImport(list);
        restoreViewCompositions(
                LIST,
                configId,
                mapList(snapshot.get("viewCompositions")));
        eventBindingSnapshotService.restoreLocalBindings(
                LIST,
                configId,
                mapList(snapshot.get("eventBindings")));
        recordAudit(
                LIST,
                configId,
                releaseId,
                "RESTORE_DRAFT",
                restorePatch.riskLevel(),
                reason,
                Map.of(
                        "sourceReleaseId", releaseId,
                        "sourceVersion", release.getVersion(),
                        "riskItems", restorePatch.riskItems()));
        return restored;
    }

    /**
     * 撤销表单或列表当前已保存但尚未发布的修改，恢复为当前 ACTIVE 发布快照。
     *
     * <p>该操作以 owner 行锁、revision、草稿 canonical hash 和 ACTIVE release ID
     * 共同作为并发前置条件。事件绑定不递增 FORM/LIST revision，因此会额外锁定
     * 本地及实体继承绑定，并在锁内重算 hash。恢复结果必须与预演的“ACTIVE 本地
     * 内容 + 当前依赖”快照一致；继承配置或外部发布引用漂移会被保留为剩余差异，
     * 不会被误当成本地草稿覆盖。</p>
     *
     * @param configType FORM 或 LIST
     * @param configId 表单或列表配置 ID
     * @param request 用户确认撤销时读取到的并发前置条件
     * @return 恢复后的 revision 与对齐哈希
     */
    @Transactional(rollbackFor = Exception.class)
    public UiConfigDraftDiscardResultDTO discardDraft(
            String configType,
            String configId,
            UiConfigDraftDiscardRequest request) {
        requireDraftDiscardRequest(request);
        Object owner = lockOwner(configType, configId);
        int previousRevision = ownerRevision(owner);
        if (!Objects.equals(
                request.getExpectedRevision(),
                previousRevision)) {
            throw new RevisionConflictException(
                    "配置已被其他人修改，请刷新后重试",
                    owner);
        }

        lockDraftComponents(configType, configId, owner);
        UiConfigRelease active = releaseMapper.findActive(
                configType,
                configId);
        if (active == null) {
            throw new BusinessConflictException(
                    "UI_CONFIG_ACTIVE_RELEASE_REQUIRED",
                    "当前配置尚未发布，不能撤销到发布基线");
        }
        String ownerActiveReleaseId = ownerActiveReleaseId(owner);
        if (!Objects.equals(ownerActiveReleaseId, active.getId())) {
            throw new BusinessConflictException(
                    "UI_CONFIG_RELEASE_STATE_CONFLICT",
                    "当前配置与激活发布状态不一致，请刷新后重试");
        }
        if (!Objects.equals(
                request.getExpectedActiveReleaseId(),
                active.getId())) {
            throw new BusinessConflictException(
                    "UI_CONFIG_ACTIVE_RELEASE_CHANGED",
                    "当前激活发布版本已变化，请重新确认撤销");
        }

        // 历史发布可能仍携带 revision 等易变字段；完整性校验后必须与 diff()
        // 使用相同的稳定化基线，避免预览可撤销而执行阶段无法重建同一快照。
        Map<String, Object> rawActiveSnapshot = snapshotSupport.stableMap(
                verifiedSnapshot(active));
        Map<String, Object> currentDraft = buildDraftSnapshot(
                configType,
                configId);
        Map<String, Object> activeSnapshot =
                normalizeReleaseComparisonSnapshot(
                        configType,
                        configId,
                        currentDraft,
                        rawActiveSnapshot);
        String activeComparisonHash = snapshotSupport.hash(
                snapshotSupport.canonical(activeSnapshot));
        String currentDraftHash = snapshotSupport.hash(
                snapshotSupport.canonical(currentDraft));
        if (!Objects.equals(
                request.getExpectedDraftHash(),
                currentDraftHash)) {
            throw new BusinessConflictException(
                    "UI_CONFIG_DRAFT_CHANGED",
                    "草稿已发生变化，请重新确认撤销");
        }

        Map<String, Object> projectedDraft = projectedDraftAfterDiscard(
                configType,
                configId,
                currentDraft,
                activeSnapshot);
        DraftDiscardAssessment assessment = assessDraftDiscard(
                configType,
                currentDraft,
                activeSnapshot,
                projectedDraft,
                activeComparisonHash);
        if (!assessment.discardableChanged()) {
            throw new BusinessConflictException(
                    "UI_CONFIG_NO_DISCARDABLE_DRAFT",
                    assessment.blockedReason());
        }
        if (!assessment.canDiscardDraft()) {
            throw new BusinessConflictException(
                    "UI_CONFIG_DISCARD_BASELINE_DRIFT",
                    assessment.blockedReason());
        }

        UiConfigSemanticPatchService.PatchAnalysis discardPatch =
                semanticPatchService.build(
                        configType,
                        currentDraft,
                        projectedDraft);
        int restoredRevision;
        if (FORM.equals(configType)) {
            EntityForm publishedForm = restorableForm(activeSnapshot);
            publishedForm.setId(configId);
            EntityForm restored = formService.restoreFormForRelease(
                    publishedForm,
                    previousRevision);
            formNodeService.restorePublishedNodes(
                    configId,
                    publishedForm.getNodes());
            restoredRevision = ownerRevision(restored);
        } else {
            EntityListConfigDTO publishedList = runtimeList(
                    activeSnapshot,
                    configId);
            publishedList.setId(configId);
            EntityListConfigDTO restored =
                    listConfigService.restoreConfigForRelease(
                            publishedList,
                            previousRevision);
            restoredRevision = restored.getRevision() == null
                    ? 0 : restored.getRevision();
        }
        if (restoredRevision != previousRevision + 1) {
            throw new BusinessConflictException(
                    "UI_CONFIG_RESTORE_REVISION_INVALID",
                    "草稿恢复后的修订号不符合预期，操作已回滚");
        }

        restoreViewCompositions(
                configType,
                configId,
                mapList(activeSnapshot.get("viewCompositions")));

        eventBindingSnapshotService.restoreLocalBindingsForRelease(
                configType,
                configId,
                mapList(activeSnapshot.get("eventBindings")));

        Map<String, Object> restoredDraft = buildDraftSnapshot(
                configType,
                configId);
        String restoredHash = snapshotSupport.hash(
                snapshotSupport.canonical(restoredDraft));
        if (!Objects.equals(
                restoredHash,
                assessment.projectedHash())
                || !semanticPatchService.build(
                        configType,
                        projectedDraft,
                        restoredDraft).operations().isEmpty()) {
            // 稳定 ID 或已锁定的继承配置在恢复期间漂移时必须整体回滚。
            throw new BusinessConflictException(
                    "UI_CONFIG_DISCARD_BASELINE_DRIFT",
                    "草稿依赖在恢复期间发生变化，无法安全完成撤销");
        }
        alignOwnerDraftHash(
                configType,
                configId,
                active,
                restoredRevision,
                restoredHash);
        boolean remainingChanged = !Objects.equals(
                restoredHash,
                activeComparisonHash)
                || !semanticPatchService.build(
                        configType,
                        activeSnapshot,
                        restoredDraft).operations().isEmpty();

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("activeReleaseId", active.getId());
        audit.put("activeVersion", active.getVersion());
        audit.put("previousRevision", previousRevision);
        audit.put("revision", restoredRevision);
        audit.put("discardedDraftHash", currentDraftHash);
        audit.put("publishedHash", active.getContentHash());
        audit.put("restoredDraftHash", restoredHash);
        audit.put("remainingChanged", remainingChanged);
        audit.put("dependencyChanged", assessment.dependencyChanged());
        audit.put("riskItems", discardPatch.riskItems());
        recordAudit(
                configType,
                configId,
                active.getId(),
                "DISCARD_DRAFT",
                discardPatch.riskLevel(),
                request.getReason(),
                audit);
        log.info(
                "UI配置未发布修改已撤销: configType={}, configId={}, activeReleaseId={}, previousRevision={}, revision={}, discardedDraftHash={}, publishedHash={}, operatorId={}",
                LogValue.safe(configType),
                LogValue.safe(configId),
                LogValue.safe(active.getId()),
                previousRevision,
                restoredRevision,
                LogValue.safe(currentDraftHash),
                LogValue.safe(active.getContentHash()),
                LogValue.safe(UserContext.getUserId()));
        return UiConfigDraftDiscardResultDTO.builder()
                .configType(configType)
                .configId(configId)
                .discardedDraftHash(currentDraftHash)
                .draftHash(restoredHash)
                .publishedHash(active.getContentHash())
                .activeReleaseId(active.getId())
                .activeVersion(active.getVersion())
                .previousRevision(previousRevision)
                .revision(restoredRevision)
                .remainingChanged(remainingChanged)
                .dependencyChanged(assessment.dependencyChanged())
                .build();
    }

    /**
     * 整理{@code restored}列表草稿快照数据，供调用方遍历或继续处理。
     *
     * @param configId 配置ID，后续用于处理{@code restored}列表草稿快照时定位或关联目标
     * @param currentDraft 当前草稿，作为 {@code mapList} 的输入影响后续处理
     * @param sourceRelease 来源发布版本，作为 {@code restored.put} 的输入影响后续处理
     * @return {@code restored}列表草稿快照键值结果，供调用方继续处理
     */
    private Map<String, Object> restoredListDraftSnapshot(
            String configId,
            Map<String, Object> currentDraft,
            Map<String, Object> sourceRelease) {
        Map<String, Object> restored =
                new LinkedHashMap<>(currentDraft);
        restored.put("list", sourceRelease.get("list"));
        restored.put(
                "viewCompositions",
                normalizeViewCompositionsForCurrentDependencies(
                        mapList(sourceRelease.get("viewCompositions"))));
        List<Map<String, Object>> bindings = new ArrayList<>();
        mapList(currentDraft.get("eventBindings")).stream()
                .filter(binding -> !isLocalListBinding(
                        binding,
                        configId))
                .forEach(bindings::add);
        mapList(sourceRelease.get("eventBindings")).stream()
                .filter(binding -> isLocalListBinding(
                        binding,
                        configId))
                .forEach(bindings::add);
        restored.put("eventBindings", bindings);
        return restored;
    }

    /**
     * 生成发布快照的恢复比较副本，统一处理旧列表按钮缺失的关系型默认值。
     *
     * <p>快照完整性始终针对原始不可变文档校验；这里只在校验后构造稳定副本，
     * 同时供 diff、projected hash 和物理恢复使用。当前草稿只参与缺失按钮 ID 的
     * 回填，且调用方已通过 draft hash/revision 建立并发边界。</p>
     *
     * @param configType 配置类型标识，决定后续发布版本比较快照采用的处理分支
     * @param configId 配置ID，后续用于规范化发布版本比较快照时定位或关联目标
     * @param currentDraft 当前草稿，作为 {@code runtimeList} 的输入影响后续处理
     * @param activeSnapshot 活动快照，作为 {@code restorableForm} 的输入影响后续处理
     * @return 发布版本比较快照键值结果，供调用方继续处理
     */
    private Map<String, Object> normalizeReleaseComparisonSnapshot(
            String configType,
            String configId,
            Map<String, Object> currentDraft,
            Map<String, Object> activeSnapshot) {
        if (FORM.equals(configType)) {
            // 草稿已统一为节点；旧发布也必须按同一规则补齐后比较，避免把格式转换当作配置变更。
            EntityForm published = restorableForm(activeSnapshot);
            Map<String, Object> normalized = new LinkedHashMap<>(activeSnapshot);
            normalized.put("nodes", snapshotSupport.stableValue(published.getNodes()));
            normalized.put("legacyFields", snapshotSupport.stableValue(
                    deriveRuntimeFields(published, published.getNodes())));
            return normalized;
        }
        if (!LIST.equals(configType)) {
            return activeSnapshot;
        }
        EntityListConfigDTO publishedList = runtimeList(
                activeSnapshot,
                configId);
        EntityListConfigDTO currentList = runtimeList(
                currentDraft,
                configId);
        requireListActionConfigService()
                .normalizePublishedActionsForRestore(
                publishedList,
                currentList);
        Map<String, Object> normalized = new LinkedHashMap<>(
                activeSnapshot);
        normalized.put(
                "list",
                snapshotSupport.stableValue(publishedList));
        return normalized;
    }

    /**
     * 发布、激活和恢复统一要求列表按钮规则服务已完成容器装配。
     *
     * @return 校验并获取后的列表动作配置服务结果，供调用方继续处理
     */
    private EntityListActionConfigService requireListActionConfigService() {
        if (listActionConfigService == null) {
            throw new IllegalStateException(
                    "列表按钮发布规范化服务未配置");
        }
        return listActionConfigService;
    }

    /**
     * 处理{@code assess}草稿丢弃，并将结果传给后续步骤。
     *
     * @param configType 配置类型标识，决定后续{@code assess}草稿丢弃采用的处理分支
     * @param configId 配置ID，后续用于处理{@code assess}草稿丢弃时定位或关联目标
     * @param currentDraft 当前草稿，作为 {@code projectedDraftAfterDiscard} 的输入影响后续处理
     * @param activeSnapshot 活动快照，作为 {@code projectedDraftAfterDiscard} 的输入影响后续处理
     * @param activeHash 活动哈希，供本方法处理{@code assess}草稿丢弃时使用
     * @return 处理后的{@code assess}草稿丢弃结果，供调用方继续处理
     */
    private DraftDiscardAssessment assessDraftDiscard(
            String configType,
            String configId,
            Map<String, Object> currentDraft,
            Map<String, Object> activeSnapshot,
            String activeHash) {
        Map<String, Object> projected = projectedDraftAfterDiscard(
                configType,
                configId,
                currentDraft,
                activeSnapshot);
        return assessDraftDiscard(
                configType,
                currentDraft,
                activeSnapshot,
                projected,
                activeHash);
    }

    /**
     * 处理{@code assess}草稿丢弃，并将结果传给后续步骤。
     *
     * @param configType 配置类型标识，决定后续{@code assess}草稿丢弃采用的处理分支
     * @param currentDraft 当前草稿，作为 {@code semanticPatchService.build} 的输入影响后续处理
     * @param activeSnapshot 活动快照，供本方法处理{@code assess}草稿丢弃时使用
     * @param projected {@code projected}，作为 {@code semanticPatchService.build} 的输入影响后续处理
     * @param activeHash 活动哈希，供本方法处理{@code assess}草稿丢弃时使用
     * @return 处理后的{@code assess}草稿丢弃结果，供调用方继续处理
     */
    private DraftDiscardAssessment assessDraftDiscard(
            String configType,
            Map<String, Object> currentDraft,
            Map<String, Object> activeSnapshot,
            Map<String, Object> projected,
            String activeHash) {
        boolean localChanged = !semanticPatchService.build(
                configType,
                currentDraft,
                projected).operations().isEmpty();
        String projectedHash = snapshotSupport.hash(
                snapshotSupport.canonical(projected));
        boolean dependencyChanged = !Objects.equals(
                projectedHash,
                activeHash)
                || !semanticPatchService.build(
                        configType,
                        activeSnapshot,
                        projected).operations().isEmpty();
        if (!localChanged) {
            return new DraftDiscardAssessment(
                    false,
                    false,
                    dependencyChanged,
                    "当前没有可撤销的本地未发布修改；差异可能来自继承配置或外部发布引用",
                    projectedHash);
        }
        return new DraftDiscardAssessment(
                true,
                true,
                dependencyChanged,
                null,
                projectedHash);
    }

    /**
     * 模拟只恢复当前配置本地内容后的 canonical 草稿。
     *
     * <p>实体继承事件继续使用当前值，子列表和目标表单引用按当前 ACTIVE 重新钉定，
     * 因而纯外部漂移不会被错误标记为本地可撤销修改。</p>
     *
     * @param configType 配置类型标识，决定后续{@code projected}草稿之后丢弃采用的处理分支
     * @param configId 配置ID，后续用于处理{@code projected}草稿之后丢弃时定位或关联目标
     * @param currentDraft 当前草稿，供本方法处理{@code projected}草稿之后丢弃时使用
     * @param activeSnapshot 活动快照，作为 {@code restorableForm} 的输入影响后续处理
     * @return {@code projected}草稿之后丢弃键值结果，供调用方继续处理
     */
    private Map<String, Object> projectedDraftAfterDiscard(
            String configType,
            String configId,
            Map<String, Object> currentDraft,
            Map<String, Object> activeSnapshot) {
        Map<String, Object> projected = new LinkedHashMap<>(
                activeSnapshot);
        if (FORM.equals(configType)) {
            EntityForm form = restorableForm(activeSnapshot);
            List<EntityFormNode> nodes = pinSubListReleases(
                    form.getNodes());
            projected.put(
                    "nodes",
                    snapshotSupport.stableValue(nodes));
            projected.put(
                    "legacyFields",
                    snapshotSupport.stableValue(
                            deriveRuntimeFields(form, nodes)));
        } else {
            EntityListConfigDTO list = runtimeList(
                    activeSnapshot,
                    configId);
            pinListTargetFormReleases(list);
            projected.put(
                    "list",
                    snapshotSupport.stableValue(list));
        }
        projected.put(
                "viewCompositions",
                snapshotSupport.stableValue(
                        normalizeViewCompositionsForCurrentDependencies(
                                mapList(activeSnapshot.get(
                                        "viewCompositions")))));
        projected.put(
                "eventBindings",
                projectedEventBindings(
                        configType,
                        configId,
                        currentDraft,
                        activeSnapshot));
        return projected;
    }

    /**
     * 整理{@code projected}事件绑定集合数据，供调用方遍历或继续处理。
     *
     * @param configType 配置类型标识，决定后续{@code projected}事件绑定集合采用的处理分支
     * @param configId 配置ID，后续用于处理{@code projected}事件绑定集合时定位或关联目标
     * @param currentDraft 当前草稿，作为 {@code mapList} 的输入影响后续处理
     * @param activeSnapshot 活动快照，作为 {@code mapList} 的输入影响后续处理
     * @return 界面配置发布版本集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> projectedEventBindings(
            String configType,
            String configId,
            Map<String, Object> currentDraft,
            Map<String, Object> activeSnapshot) {
        List<Map<String, Object>> bindings = new ArrayList<>();
        mapList(currentDraft.get("eventBindings")).stream()
                .filter(binding -> !isLocalBinding(
                        binding,
                        configType,
                        configId))
                .forEach(bindings::add);
        mapList(activeSnapshot.get("eventBindings")).stream()
                .filter(binding -> isLocalBinding(
                        binding,
                        configType,
                        configId))
                .forEach(bindings::add);
        return bindings;
    }

    /**
     * 判断是否本地绑定；判断结果决定调用方的后续分支。
     *
     * @param binding 绑定，作为 {@code configType.equals} 的输入影响后续处理
     * @param configType 配置类型标识，决定后续本地绑定采用的处理分支
     * @param configId 配置ID，后续用于判断是否本地绑定时定位或关联目标
     * @return 本地绑定条件成立时为 true，否则为 false
     */
    private boolean isLocalBinding(
            Map<String, Object> binding,
            String configType,
            String configId) {
        return configType.equals(normalize(text(
                binding.get("ownerType"))))
                && Objects.equals(
                        configId,
                        text(binding.get("ownerId")));
    }

    /**
     * 判断是否本地列表绑定；判断结果决定调用方的后续分支。
     *
     * @param binding 绑定，作为 {@code isLocalBinding} 的输入影响后续处理
     * @param configId 配置ID，后续用于判断是否本地列表绑定时定位或关联目标
     * @return 本地列表绑定条件成立时为 true，否则为 false
     */
    private boolean isLocalListBinding(
            Map<String, Object> binding,
            String configId) {
        return isLocalBinding(binding, LIST, configId);
    }

    /**
     * 封装草稿丢弃{@code assessment}的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param discardableChanged {@code discardable}已变更，保存在对象中供后续校验、查询或展示
     * @param canDiscardDraft {@code can}丢弃草稿，保存在对象中供后续校验、查询或展示
     * @param dependencyChanged 依赖已变更，保存在对象中供后续校验、查询或展示
     * @param blockedReason {@code blocked}原因，保存在对象中供后续校验、查询或展示
     * @param projectedHash {@code projected}哈希，保存在对象中供后续校验、查询或展示
     */
    private record DraftDiscardAssessment(
            boolean discardableChanged,
            boolean canDiscardDraft,
            boolean dependencyChanged,
            String blockedReason,
            String projectedHash) {

        /**
         * 构造服务不可用异常，供调用方区分失败原因。
         *
         * @param reason 原因，作为 {@code DraftDiscardAssessment} 的输入影响后续处理
         * @return 处理后的不可用结果，供调用方继续处理
         */
        private static DraftDiscardAssessment unavailable(
                String reason) {
            return new DraftDiscardAssessment(
                    false,
                    false,
                    false,
                    reason,
                    null);
        }
    }

    /**
     * 校验并获取操作原因；不满足约束时阻止后续处理。
     *
     * @param reason 原因，供本方法校验并获取操作原因时使用
     * @param message 消息，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void requireOperationReason(
            String reason,
            String message) {
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 记录实体界面资产；供后续追溯或审计使用。
     *
     * @param configType 配置类型标识，决定后续实体界面资产采用的处理分支
     * @param configId 配置ID，后续用于记录实体界面资产时定位或关联目标
     * @param release 发布版本，作为 {@code migrationRequest.setVersionDescription} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于记录实体界面资产
     */
    private void recordEntityUiAsset(
            String configType,
            String configId,
            UiConfigRelease release,
            UiConfigPublishRequest request) {
        EntityDefinition entity = ownerEntity(
                configType, configId);
        if (entity == null) {
            return;
        }
        ConfigMigrationPublishRequest migrationRequest =
                new ConfigMigrationPublishRequest();
        migrationRequest.setVersionDescription(
                request == null
                        ? release.getDescription()
                        : request.getDescription());
        migrationRequest.setMarkForExport(true);
        if (entity.getStorageMode()
                == EntityDefinition.StorageMode.SYSTEM) {
            migrationAssetHandler.recordSystemEntityUi(
                    entity.getId(),
                    release.getId(),
                    migrationRequest);
        } else {
            // 自定义实体的 UI 发布同样会改变可迁移配置，必须刷新完整实体快照。
            migrationAssetHandler.recordEntityUi(
                    entity.getId(),
                    release.getId(),
                    migrationRequest);
        }
    }

    /**
     * 校验并获取草稿丢弃请求；不满足约束时阻止后续处理。
     *
     * @param request 本次请求，后续经校验后用于校验并获取草稿丢弃请求
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void requireDraftDiscardRequest(
            UiConfigDraftDiscardRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("撤销草稿请求不能为空");
        }
        if (request.getExpectedRevision() == null) {
            throw new IllegalArgumentException(
                    "expectedRevision 不能为空");
        }
        if (!StringUtils.hasText(request.getExpectedDraftHash())) {
            throw new IllegalArgumentException(
                    "expectedDraftHash 不能为空");
        }
        if (!StringUtils.hasText(
                request.getExpectedActiveReleaseId())) {
            throw new IllegalArgumentException(
                    "expectedActiveReleaseId 不能为空");
        }
        request.setExpectedDraftHash(
                request.getExpectedDraftHash().trim());
        request.setExpectedActiveReleaseId(
                request.getExpectedActiveReleaseId().trim());
    }

    /**
     * 锁定草稿{@code components}；避免后续并发处理覆盖状态。
     *
     * @param configType 配置类型标识，决定后续草稿{@code components}采用的处理分支
     * @param configId 配置ID，后续用于锁定草稿{@code components}时定位或关联目标
     * @param owner 归属方，作为 {@code eventBindingSnapshotService.lockOwnerBindings} 的输入影响后续处理
     */
    private void lockDraftComponents(
            String configType,
            String configId,
            Object owner) {
        if (FORM.equals(configType)) {
            formNodeService.lockDraftNodesForRelease(configId);
        } else {
            listConfigService.lockDraftChildrenForRelease(configId);
        }
        eventBindingSnapshotService.lockOwnerBindings(
                configType,
                configId);
        eventBindingSnapshotService.lockOwnerBindings(
                "ENTITY",
                ownerEntityId(owner));
        requireViewCompositionService().lockByOwner(configType, configId);
    }

    /**
     * 处理归属方修订版本，并将结果传给后续步骤。
     *
     * @param owner 归属方，供本方法处理归属方修订版本时使用
     * @return 处理后的归属方修订版本结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private int ownerRevision(Object owner) {
        Integer revision;
        if (owner instanceof EntityForm form) {
            revision = form.getRevision();
        } else if (owner instanceof EntityListConfig list) {
            revision = list.getRevision();
        } else {
            throw new IllegalArgumentException("不支持的UI配置所有者");
        }
        return revision == null ? 0 : revision;
    }

    /**
     * 生成归属方活动发布版本ID文本，供后续匹配或展示。
     *
     * @param owner 归属方，供本方法处理归属方活动发布版本ID时使用
     * @return 处理后的归属方活动发布版本ID文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String ownerActiveReleaseId(Object owner) {
        if (owner instanceof EntityForm form) {
            return form.getActiveReleaseId();
        }
        if (owner instanceof EntityListConfig list) {
            return list.getActiveReleaseId();
        }
        throw new IllegalArgumentException("不支持的UI配置所有者");
    }

    /**
     * 生成归属方实体ID文本，供后续匹配或展示。
     *
     * @param owner 归属方，供本方法处理归属方实体ID时使用
     * @return 处理后的归属方实体ID文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String ownerEntityId(Object owner) {
        if (owner instanceof EntityForm form) {
            return form.getEntityId();
        }
        if (owner instanceof EntityListConfig list) {
            return list.getEntityId();
        }
        throw new IllegalArgumentException("不支持的UI配置所有者");
    }

    /**
     * 处理{@code align}归属方草稿哈希，并将结果传给后续步骤。
     *
     * @param configType 配置类型标识，决定后续{@code align}归属方草稿哈希采用的处理分支
     * @param configId 配置ID，后续用于处理{@code align}归属方草稿哈希时定位或关联目标
     * @param active 活动，供本方法处理{@code align}归属方草稿哈希时使用
     * @param revision 修订版本，供本方法处理{@code align}归属方草稿哈希时使用
     * @param publishedHash 已发布哈希，作为 {@code set} 的输入影响后续处理
     */
    private void alignOwnerDraftHash(
            String configType,
            String configId,
            UiConfigRelease active,
            int revision,
            String publishedHash) {
        if (FORM.equals(configType)) {
            UpdateWrapper<EntityForm> update = new UpdateWrapper<>();
            update.eq("id", configId)
                    .eq("deleted", 0)
                    .eq("revision", revision)
                    .eq("active_release_id", active.getId())
                    .set("draft_hash", publishedHash)
                    .set("update_time", LocalDateTime.now());
            if (formMapper.update(null, update) != 1) {
                throw new RevisionConflictException(
                        "表单已被其他人修改，请刷新后重试",
                        formService.getById(configId));
            }
            return;
        }
        UpdateWrapper<EntityListConfig> update = new UpdateWrapper<>();
        update.eq("id", configId)
                .eq("deleted", 0)
                .eq("revision", revision)
                .eq("active_release_id", active.getId())
                .set("draft_hash", publishedHash)
                .set("update_time", LocalDateTime.now());
        if (listConfigMapper.update(null, update) != 1) {
            throw new RevisionConflictException(
                    "列表配置已被其他人修改，请刷新后重试",
                    listConfigService.findById(configId));
        }
    }

    /**
     * 处理归属方实体，并将结果传给后续步骤。
     *
     * @param configType 配置类型标识，决定后续归属方实体采用的处理分支
     * @param configId 配置ID，后续用于处理归属方实体时定位或关联目标
     * @return 处理后的归属方实体结果，供调用方继续处理
     */
    private EntityDefinition ownerEntity(
            String configType,
            String configId) {
        String entityId;
        if (FORM.equals(configType)) {
            EntityForm form = formMapper.selectById(configId);
            entityId = form == null ? null : form.getEntityId();
        } else {
            EntityListConfig list =
                    listConfigMapper.selectById(configId);
            entityId = list == null ? null : list.getEntityId();
        }
        return StringUtils.hasText(entityId)
                ? entityDefinitionMapper.selectById(entityId)
                : null;
    }

    /**
     * 锁定归属方；避免后续并发处理覆盖状态。
     *
     * @param configType 配置类型标识，决定后续归属方采用的处理分支
     * @param configId 配置ID，后续用于锁定归属方时定位或关联目标
     * @return 锁定后的归属方结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private Object lockOwner(String configType, String configId) {
        requireType(configType);
        if (FORM.equals(configType)) {
            EntityForm form = formMapper.selectByIdForUpdate(configId);
            if (form == null) {
                throw new IllegalArgumentException("表单不存在");
            }
            return form;
        }
        EntityListConfig list =
                listConfigMapper.selectByIdForUpdate(configId);
        if (list == null) {
            throw new IllegalArgumentException("列表配置不存在");
        }
        return list;
    }

    /**
     * 流程发布读取表单 ACTIVE 版本前锁定同一配置行，
     * 与热修复发布形成共同的串行化边界。
     *
     * @param formId 表单ID，后续用于锁定表单流程发布时定位或关联目标
     */
    public void lockFormForProcessPublish(String formId) {
        if (!StringUtils.hasText(formId)
                || formMapper.selectByIdForUpdate(formId) == null) {
            throw new IllegalArgumentException(
                    "流程节点表单不存在: " + formId);
        }
    }

    /**
     * 解析表单运行时发布版本对应的表单对象（取当前激活版本）。
     *
     * @param formId 表单ID
     * @return 运行时表单对象
     */
    public EntityForm resolveRuntimeForm(String formId) {
        return resolveRuntimeFormRelease(formId).form();
    }

    /**
     * 解析表单运行时发布版本信息（取当前激活版本，非钉定）。
     *
     * @param formId 表单ID
     * @return 解析后的表单发布版本信息
     */
    public ResolvedEntityFormRelease resolveRuntimeFormRelease(
            String formId) {
        UiConfigRelease release = active(FORM, formId);
        if (release == null) {
            log.info(
                    "表单运行时使用草稿配置: formId={}, reason=NO_ACTIVE_RELEASE",
                    LogValue.safe(formId));
            return new ResolvedEntityFormRelease(
                    formService.getById(formId),
                    null,
                    null);
        }
        ResolvedEntityFormRelease resolved =
                resolvedRuntimeForm(release, false);
        log.info(
                "表单运行时使用激活发布版本: formId={}, releaseId={}, releaseVersion={}",
                LogValue.safe(formId),
                LogValue.safe(resolved.releaseId()),
                resolved.releaseVersion());
        return resolved;
    }

    /**
     * 解析指定发布版本的表单运行时对象，支持版本号一致性校验。
     *
     * @param formId          表单ID
     * @param releaseId       发布记录ID，为空取当前激活版本
     * @param expectedVersion 期望版本号，为空跳过校验
     * @return 运行时表单对象
     * @throws IllegalArgumentException 发布版本不存在或版本号不一致时抛出
     */
    public EntityForm resolveRuntimeForm(
            String formId,
            String releaseId,
            Integer expectedVersion) {
        return resolveRuntimeFormRelease(
                formId,
                releaseId,
                expectedVersion).form();
    }

    /**
     * 解析指定发布版本的表单运行时发布版本信息，支持版本号一致性校验。
     *
     * @param formId          表单ID
     * @param releaseId       发布记录ID，为空取当前激活版本
     * @param expectedVersion 期望版本号，为空跳过校验
     * @return 解析后的表单发布版本信息（钉定发布时 pinned 为 true）
     * @throws IllegalArgumentException 发布版本不存在或版本号不一致时抛出
     */
    public ResolvedEntityFormRelease resolveRuntimeFormRelease(
            String formId,
            String releaseId,
            Integer expectedVersion) {
        UiRuntimeResolutionContext context =
                StringUtils.hasText(releaseId)
                        ? UiRuntimeResolutionContext.historical(
                                null,
                                null)
                        : UiRuntimeResolutionContext.standalone();
        return resolveRuntimeFormRelease(
                formId,
                releaseId,
                expectedVersion,
                context);
    }

    /**
     * 按服务端可信流程上下文解析原始钉定或有效热修复表单。
     *
     * @param formId 表单ID，后续用于解析运行时表单发布版本时定位或关联目标
     * @param releaseId 发布版本ID，后续用于解析运行时表单发布版本时定位或关联目标
     * @param expectedVersion 预期版本，供本方法解析运行时表单发布版本时使用
     * @param context 执行上下文，向后续运行时表单发布版本步骤传递身份、配置或状态
     * @return 解析后的运行时表单发布版本结果，供调用方继续处理
     */
    public ResolvedEntityFormRelease resolveRuntimeFormRelease(
            String formId,
            String releaseId,
            Integer expectedVersion,
            UiRuntimeResolutionContext context) {
        UiRuntimePurpose purpose = context == null
                || context.purpose() == null
                ? UiRuntimePurpose.STANDALONE
                : context.purpose();
        if (!StringUtils.hasText(releaseId)) {
            return resolveRuntimeFormRelease(formId);
        }
        log.info(
                "开始解析固定表单版本: formId={}, releaseId={}, expectedVersion={}, purpose={}, historyId={}, nodeId={}",
                LogValue.safe(formId),
                LogValue.safe(releaseId),
                expectedVersion,
                LogValue.safe(purpose),
                LogValue.safe(
                        context == null
                                ? null
                                : context.processVersionHistoryId()),
                LogValue.safe(
                        context == null ? null : context.nodeId()));
        UiConfigRelease release = releaseMapper.selectById(releaseId);
        if (release == null
                || !FORM.equals(release.getConfigType())
                || !Objects.equals(formId, release.getConfigId())) {
            log.info(
                    "固定表单版本解析失败: formId={}, releaseId={}, expectedVersion={}, actualConfigType={}, actualConfigId={}, reason=RELEASE_NOT_FOUND",
                    LogValue.safe(formId),
                    LogValue.safe(releaseId),
                    expectedVersion,
                    LogValue.safe(
                            release == null
                                    ? null : release.getConfigType()),
                    LogValue.safe(
                            release == null
                                    ? null : release.getConfigId()));
            throw new IllegalArgumentException("表单发布版本不存在或不属于当前表单");
        }
        if (expectedVersion != null
                && !Objects.equals(expectedVersion, release.getVersion())) {
            log.info(
                    "固定表单版本解析失败: formId={}, releaseId={}, expectedVersion={}, actualVersion={}, reason=VERSION_MISMATCH",
                    LogValue.safe(formId),
                    LogValue.safe(releaseId),
                    expectedVersion,
                    release.getVersion());
            throw new IllegalArgumentException("表单发布版本号与流程快照不一致");
        }
        if (Set.of(
                        UiRuntimePurpose.NEW_INSTANCE,
                        UiRuntimePurpose.ACTIVE_TASK)
                .contains(purpose)
                && context != null
                && StringUtils.hasText(
                        context.processVersionHistoryId())) {
            UiConfigHotfixTarget target =
                    hotfixTargetMapper.findActiveTarget(
                            FORM,
                            formId,
                            context.processVersionHistoryId());
            if (target != null) {
                if (!Objects.equals(
                                release.getId(),
                                target.getPinnedReleaseId())
                        || !Objects.equals(
                                release.getVersion(),
                                target.getPinnedReleaseVersion())) {
                    log.info(
                            "热修复表单版本解析失败: formId={}, historyId={}, targetId={}, requestedReleaseId={}, requestedVersion={}, targetPinnedReleaseId={}, targetPinnedVersion={}, reason=PINNED_RELEASE_MISMATCH",
                            LogValue.safe(formId),
                            LogValue.safe(
                                    context.processVersionHistoryId()),
                            LogValue.safe(target.getId()),
                            LogValue.safe(release.getId()),
                            release.getVersion(),
                            LogValue.safe(target.getPinnedReleaseId()),
                            target.getPinnedReleaseVersion());
                    throw new IllegalStateException(
                            "热修复目标与流程钉定表单版本不一致");
                }
                try {
                    Map<String, Object> snapshot =
                            verifiedEffectiveTargetSnapshot(target);
                    ResolvedEntityFormRelease result =
                            new ResolvedEntityFormRelease(
                            runtimeForm(snapshot),
                            release.getId(),
                            release.getVersion(),
                            true,
                            target.getHotfixReleaseId(),
                            target.getEffectiveContentHash(),
                            target.getId(),
                            purpose);
                    log.info(
                            "热修复表单版本解析完成: formId={}, pinnedReleaseId={}, pinnedVersion={}, effectiveReleaseId={}, hotfixTargetId={}, historyId={}, nodeId={}, purpose={}",
                            LogValue.safe(formId),
                            LogValue.safe(result.releaseId()),
                            result.releaseVersion(),
                            LogValue.safe(result.effectiveReleaseId()),
                            LogValue.safe(target.getId()),
                            LogValue.safe(
                                    context.processVersionHistoryId()),
                            LogValue.safe(context.nodeId()),
                            LogValue.safe(purpose));
                    return result;
                } catch (RuntimeException exception) {
                    log.error(
                            "热修复运行时解析失败: "
                                    + "formId={}, historyId={}, targetId={}, error={}",
                            LogValue.safe(formId),
                            LogValue.safe(context.processVersionHistoryId()),
                            LogValue.safe(target.getId()),
                            LogValue.failureType(exception));
                    throw new IllegalStateException(
                            "热修复运行时快照解析失败",
                            exception);
                }
            }
        }
        ResolvedEntityFormRelease pinned =
                resolvedRuntimeForm(release, true);
        ResolvedEntityFormRelease result =
                new ResolvedEntityFormRelease(
                        pinned.form(),
                        pinned.releaseId(),
                        pinned.releaseVersion(),
                        true,
                        pinned.releaseId(),
                        release.getContentHash(),
                        null,
                        purpose);
        log.info(
                "固定表单版本解析完成: formId={}, releaseId={}, releaseVersion={}, effectiveReleaseId={}, hotfixApplied=false, purpose={}, historyId={}, nodeId={}",
                LogValue.safe(formId),
                LogValue.safe(result.releaseId()),
                result.releaseVersion(),
                LogValue.safe(result.effectiveReleaseId()),
                LogValue.safe(purpose),
                LogValue.safe(
                        context == null
                                ? null
                                : context.processVersionHistoryId()),
                LogValue.safe(
                        context == null ? null : context.nodeId()));
        return result;
    }

    /**
     * 按提交阶段已经解析出的可信身份重新读取同一份表单有效快照。
     *
     * <p>热修复发布自身的 snapshot/patch 不是某个流程钉定版本应用补丁后的最终快照，
     * 因此终检不能只凭 {@code effectiveReleaseId} 重新读取发布记录。存在
     * {@code hotfixTargetId} 时，本方法校验基础发布、热修复发布、目标记录和内容哈希
     * 的完整关联，并直接读取目标记录中已经固化的 effective snapshot。目标即使在
     * 表单处理完成后被回滚，也仍按本次提交已经采用的目标快照完成事务终检，避免
     * 同一次提交前后切换规则。</p>
     *
     * @param formId 表单ID
     * @param releaseId 基础发布ID
     * @param releaseVersion 基础发布版本
     * @param effectiveReleaseId 实际生效发布ID；非热修复时等于基础发布ID
     * @param effectiveContentHash 实际有效快照哈希
     * @param hotfixTargetId 热修复目标ID；非热修复时为空
     * @return 与提交处理阶段完全一致的已验证发布表单
     */
    public ResolvedEntityFormRelease resolveTrustedEffectiveFormRelease(
            String formId,
            String releaseId,
            Integer releaseVersion,
            String effectiveReleaseId,
            String effectiveContentHash,
            String hotfixTargetId) {
        if (!StringUtils.hasText(formId)
                || !StringUtils.hasText(releaseId)
                || releaseVersion == null) {
            throw new IllegalArgumentException(
                    "可信表单发布身份不完整");
        }
        UiConfigRelease baseRelease = requireFormRelease(
                formId,
                releaseId,
                releaseVersion,
                "基础表单发布身份无效");
        if (!StringUtils.hasText(hotfixTargetId)) {
            String normalizedEffectiveReleaseId =
                    StringUtils.hasText(effectiveReleaseId)
                            ? effectiveReleaseId : releaseId;
            if (!Objects.equals(
                    releaseId,
                    normalizedEffectiveReleaseId)) {
                throw new IllegalArgumentException(
                        "热修复有效发布身份缺少目标记录");
            }
            if (StringUtils.hasText(effectiveContentHash)
                    && !Objects.equals(
                            effectiveContentHash,
                            baseRelease.getContentHash())) {
                throw new IllegalArgumentException(
                        "表单有效快照哈希与基础发布不一致");
            }
            return new ResolvedEntityFormRelease(
                    runtimeForm(verifiedSnapshot(baseRelease)),
                    releaseId,
                    releaseVersion,
                    true,
                    releaseId,
                    baseRelease.getContentHash(),
                    null,
                    UiRuntimePurpose.HISTORICAL);
        }
        if (!StringUtils.hasText(effectiveReleaseId)
                || !StringUtils.hasText(effectiveContentHash)) {
            throw new IllegalArgumentException(
                    "热修复表单发布身份缺少有效发布或快照哈希");
        }

        UiConfigHotfixTarget target =
                hotfixTargetMapper.selectById(hotfixTargetId);
        if (target == null
                || !FORM.equals(target.getConfigType())
                || !Objects.equals(formId, target.getConfigId())
                || !Objects.equals(
                        releaseId,
                        target.getPinnedReleaseId())
                || !Objects.equals(
                        releaseVersion,
                        target.getPinnedReleaseVersion())
                || !Objects.equals(
                        effectiveReleaseId,
                        target.getHotfixReleaseId())
                || !Objects.equals(
                        effectiveContentHash,
                        target.getEffectiveContentHash())) {
            throw new IllegalArgumentException(
                    "热修复目标与可信表单发布身份不一致");
        }
        UiConfigRelease hotfixRelease = requireFormRelease(
                formId,
                effectiveReleaseId,
                null,
                "热修复表单发布身份无效");
        if (!HOTFIX.equals(hotfixRelease.getReleaseMode())) {
            throw new IllegalArgumentException(
                    "有效发布不是表单热修复版本");
        }
        // 两条发布记录同样属于不可变审计链；即使最终规则来自 target，也要先验完整性。
        verifiedSnapshot(baseRelease);
        verifiedSnapshot(hotfixRelease);

        // 不检查 target.status：提交处理后发生回滚不能改变同一事务终检所用规则。
        Map<String, Object> effectiveSnapshot =
                verifiedEffectiveTargetSnapshot(target);
        EntityForm form = runtimeForm(effectiveSnapshot);
        if (form == null
                || !Objects.equals(formId, form.getId())) {
            throw new IllegalArgumentException(
                    "热修复有效快照不属于当前表单");
        }
        return new ResolvedEntityFormRelease(
                form,
                releaseId,
                releaseVersion,
                true,
                effectiveReleaseId,
                effectiveContentHash,
                hotfixTargetId,
                UiRuntimePurpose.HISTORICAL);
    }

    /**
     * 校验发布记录确实属于指定表单和基础版本。
     *
     * @param formId 表单ID，后续用于校验并获取表单发布版本时定位或关联目标
     * @param releaseId 发布版本ID，后续用于校验并获取表单发布版本时定位或关联目标
     * @param expectedVersion 预期版本，供本方法校验并获取表单发布版本时使用
     * @param message 消息，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @return 校验并获取后的表单发布版本结果，供调用方继续处理
     */
    private UiConfigRelease requireFormRelease(
            String formId,
            String releaseId,
            Integer expectedVersion,
            String message) {
        UiConfigRelease release = releaseMapper.selectById(releaseId);
        if (release == null
                || !FORM.equals(release.getConfigType())
                || !Objects.equals(formId, release.getConfigId())
                || expectedVersion != null
                && !Objects.equals(
                        expectedVersion,
                        release.getVersion())) {
            throw new IllegalArgumentException(message);
        }
        return release;
    }

    /**
     * 处理已解析运行时表单，并将结果传给后续步骤。
     *
     * @param release 发布版本，作为 {@code ResolvedEntityFormRelease} 的输入影响后续处理
     * @param pinned 固定，供本方法处理已解析运行时表单时使用
     * @return 处理后的已解析运行时表单结果，供调用方继续处理
     */
    private ResolvedEntityFormRelease resolvedRuntimeForm(
            UiConfigRelease release,
            boolean pinned) {
        return new ResolvedEntityFormRelease(
                runtimeForm(verifiedSnapshot(release)),
                release.getId(),
                release.getVersion(),
                pinned,
                release.getId(),
                release.getContentHash(),
                null,
                pinned
                        ? UiRuntimePurpose.HISTORICAL
                        : UiRuntimePurpose.STANDALONE);
    }

    /**
     * 判断当前全局激活发布是否为该流程版本已批准的热修复。
     *
     * @param formId 表单ID，后续用于判断是否{@code approved}热修复时定位或关联目标
     * @param pinnedReleaseId 固定发布版本ID，后续用于判断是否{@code approved}热修复时定位或关联目标
     * @param pinnedReleaseVersion 固定发布版本，供本方法判断是否{@code approved}热修复时使用
     * @param processVersionHistoryId 流程版本历史ID，后续用于判断是否{@code approved}热修复时定位或关联目标
     * @param activeReleaseId 活动发布版本ID，后续用于判断是否{@code approved}热修复时定位或关联目标
     * @return {@code approved}热修复条件成立时为 true，否则为 false
     */
    public boolean isApprovedHotfix(
            String formId,
            String pinnedReleaseId,
            Integer pinnedReleaseVersion,
            String processVersionHistoryId,
            String activeReleaseId) {
        if (!StringUtils.hasText(processVersionHistoryId)
                || !StringUtils.hasText(activeReleaseId)) {
            return false;
        }
        UiConfigHotfixTarget target =
                hotfixTargetMapper.findActiveTarget(
                        FORM,
                        formId,
                        processVersionHistoryId);
        return target != null
                && Objects.equals(
                        pinnedReleaseId,
                        target.getPinnedReleaseId())
                && Objects.equals(
                        pinnedReleaseVersion,
                        target.getPinnedReleaseVersion())
                && Objects.equals(
                        activeReleaseId,
                        target.getHotfixReleaseId());
    }

    /**
     * 整理已验证有效目标快照数据，供调用方遍历或继续处理。
     *
     * @param target 目标，作为 {@code codec.readObject} 的输入影响后续处理
     * @return 已验证有效目标快照键值结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private Map<String, Object> verifiedEffectiveTargetSnapshot(
            UiConfigHotfixTarget target) {
        Map<String, Object> snapshot = codec.readObject(
                target.getEffectiveSnapshotDocument(),
                "热修复运行时有效快照");
        String actualHash = snapshotSupport.hash(
                snapshotSupport.canonical(snapshot));
        if (!StringUtils.hasText(target.getEffectiveContentHash())
                || !Objects.equals(
                        target.getEffectiveContentHash(),
                        actualHash)) {
            throw new IllegalArgumentException(
                    "热修复有效快照完整性校验失败");
        }
        return snapshot;
    }

    /**
     * 整理已验证快照数据，供调用方遍历或继续处理。
     *
     * @param release 发布版本，作为 {@code codec.readObject} 的输入影响后续处理
     * @return 已验证快照键值结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private Map<String, Object> verifiedSnapshot(UiConfigRelease release) {
        Map<String, Object> snapshot = codec.readObject(
                release.getSnapshotDocument(), "UI发布快照");
        String actualHash = snapshotSupport.hash(
                snapshotSupport.canonical(snapshot));
        if (!StringUtils.hasText(release.getContentHash())
                || !Objects.equals(release.getContentHash(), actualHash)) {
            throw new IllegalArgumentException("发布快照完整性校验失败，内容可能已被篡改");
        }
        return snapshot;
    }

    /**
     * 校验并返回发布版本的快照 Map，确保内容哈希一致。
     *
     * @param release 发布记录，不能为空
     * @return 已校验的快照 Map
     * @throws IllegalArgumentException 发布记录为空或完整性校验失败时抛出
     */
    public Map<String, Object> verifiedReleaseSnapshot(
            UiConfigRelease release) {
        if (release == null) {
            throw new IllegalArgumentException("UI发布版本不能为空");
        }
        return verifiedSnapshot(release);
    }

    /**
     * 校验同一次运行时解析得到的有效快照仍与其可信内容哈希一致。
     *
     * <p>该方法不会回读 ACTIVE 或基础发布记录，专供已经通过
     * {@link #resolveRuntimeEventSnapshot(String, String, Integer, String)}
     * 得到的表单按钮和字段事件链使用，同时覆盖标准发布与流程热修复的有效快照。</p>
     *
     * @param snapshot 已解析的完整有效快照
     * @param expectedHash 解析时验证过的有效内容哈希
     * @throws BusinessConflictException 快照缺失、被修改或哈希不完整时抛出
     */
    public void verifyResolvedEventSnapshot(
            Map<String, Object> snapshot,
            String expectedHash) {
        if (snapshot == null || snapshot.isEmpty()
                || !StringUtils.hasText(expectedHash)) {
            throw new BusinessConflictException(
                    "UI_EVENT_EFFECTIVE_SNAPSHOT_REQUIRED",
                    "表单事件执行缺少可信有效快照或内容哈希");
        }
        String actualHash = snapshotSupport.hash(
                snapshotSupport.canonical(snapshot));
        if (!Objects.equals(expectedHash, actualHash)) {
            throw new BusinessConflictException(
                    "UI_EVENT_EFFECTIVE_SNAPSHOT_TAMPERED",
                    "表单事件有效快照完整性校验失败");
        }
    }

    /**
     * 封装已解析界面事件快照的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param snapshot 快照，保存在对象中供后续校验、查询或展示
     * @param releaseId 发布版本 ID，后续用于解析固定配置
     * @param releaseVersion 发布版本号，后续用于校验快照一致性
     * @param effectiveReleaseId 有效发布版本ID，后续用于处理已解析界面事件快照时定位或关联目标
     * @param hotfixApplied 热修复{@code applied}，保存在对象中供后续校验、查询或展示
     * @param effectiveContentHash 有效内容哈希，保存在对象中供后续校验、查询或展示
     */
    public record ResolvedUiEventSnapshot(
            Map<String, Object> snapshot,
            String releaseId,
            Integer releaseVersion,
            String effectiveReleaseId,
            boolean hotfixApplied,
            String effectiveContentHash) {

        /**
         * 保留旧调用方构造兼容；运行时生产解析始终提供有效内容哈希。
         *
         * @param snapshot 快照，保存在对象中供后续校验、查询或展示
         * @param releaseId 发布版本 ID，后续用于解析固定配置
         * @param releaseVersion 发布版本号，后续用于校验快照一致性
         * @param effectiveReleaseId 有效发布版本ID，后续用于初始化已解析界面事件快照时定位或关联目标
         * @param hotfixApplied 热修复{@code applied}，保存在对象中供后续校验、查询或展示
         */
        public ResolvedUiEventSnapshot(
                Map<String, Object> snapshot,
                String releaseId,
                Integer releaseVersion,
                String effectiveReleaseId,
                boolean hotfixApplied) {
            this(
                    snapshot,
                    releaseId,
                    releaseVersion,
                    effectiveReleaseId,
                    hotfixApplied,
                    null);
        }
    }

    /**
     * 撤销草稿时补齐旧发布的节点配置，运行时读取仍保持原快照结构。
     *
     * @param snapshot 快照，作为 {@code runtimeForm} 的输入影响后续处理
     * @return 处理后的{@code restorable}表单结果，供调用方继续处理
     */
    private EntityForm restorableForm(Map<String, Object> snapshot) {
        EntityForm form = runtimeForm(snapshot);
        form.setNodes(new com.workflow.entity.form.application.EntityFormFieldProjection(codec)
                .materialize(form.getId(), form.getFields(), form.getNodes()));
        return form;
    }

    /**
     * 处理运行时表单，并将结果传给后续步骤。
     *
     * @param snapshot 快照，作为 {@code objectMapper.convertValue} 的输入影响后续处理
     * @return 处理后的运行时表单结果，供调用方继续处理
     */
    private EntityForm runtimeForm(Map<String, Object> snapshot) {
        EntityForm form = objectMapper.convertValue(
                snapshot.get("form"), EntityForm.class);
        form.setFields(objectMapper.convertValue(
                snapshot.getOrDefault("legacyFields", List.of()),
                new TypeReference<List<EntityFormField>>() {}));
        form.setNodes(objectMapper.convertValue(
                snapshot.getOrDefault("nodes", List.of()),
                new TypeReference<List<EntityFormNode>>() {}));
        // 只从已验证的发布快照填充，避免运行时误读关联内容草稿。
        form.setViewCompositions(objectMapper.convertValue(
                snapshot.getOrDefault("viewCompositions", List.of()),
                new TypeReference<List<Map<String, Object>>>() {}));
        return form;
    }

    /**
     * 解析列表当前激活的运行时配置。
     *
     * @param listConfigId 列表配置ID
     * @return 运行时列表配置 DTO，无激活版本时返回 null
     */
    public EntityListConfigDTO resolveRuntimeList(String listConfigId) {
        return resolveRuntimeListRelease(
                listConfigId,
                null,
                null,
                null).list();
    }

    /**
     * 解析列表运行时发布版本。无签名上下文时只能读取当前 ACTIVE；
     * 携带父表单签名上下文时，允许读取父表单快照中精确固定的列表版本。
     *
     * @param listConfigId 列表配置ID，后续用于解析运行时列表发布版本时定位或关联目标
     * @param releaseId 发布版本ID，后续用于解析运行时列表发布版本时定位或关联目标
     * @param expectedVersion 预期版本，作为 {@code requireListRelease} 的输入影响后续处理
     * @param releaseResolutionToken 发布版本解析令牌，后续用于授权校验、关联或幂等去重
     * @return 解析后的运行时列表发布版本结果，供调用方继续处理
     */
    public ResolvedEntityListRelease resolveRuntimeListRelease(
            String listConfigId,
            String releaseId,
            Integer expectedVersion,
            String releaseResolutionToken) {
        if (!StringUtils.hasText(listConfigId)) {
            throw new IllegalArgumentException("列表配置ID不能为空");
        }
        boolean pinned = StringUtils.hasText(releaseResolutionToken);
        UiConfigRelease release;
        if (pinned) {
            if (!StringUtils.hasText(releaseId)
                    || expectedVersion == null) {
                throw new BusinessForbiddenException(
                        "LIST_RELEASE_CONTEXT_REQUIRED",
                        "父表单固定列表版本时必须提供完整的发布标识");
            }
            release = requireListRelease(
                    listConfigId,
                    releaseId,
                    expectedVersion);
            Map<String, Object> snapshot =
                    verifiedSnapshot(release);
            EntityListConfigDTO list = runtimeList(
                    snapshot,
                    listConfigId);
            if (resolutionTokenService.isEmbedListToken(
                    releaseResolutionToken)) {
                UiReleaseResolutionTokenService.EmbedListClaims claims =
                        resolutionTokenService.verifyEmbedList(
                                releaseResolutionToken);
                EmbedDelegatedRequestContext.SessionCoordinates current =
                        EmbedDelegatedRequestContext.currentSession()
                                .orElse(null);
                if (!Objects.equals(
                        claims.listConfigId(), listConfigId)
                        || !Objects.equals(claims.releaseId(), releaseId)
                        || !Objects.equals(
                        claims.releaseVersion(), expectedVersion)
                        || !Objects.equals(
                        claims.entityCode(), list.getEntityCode())
                        || current == null
                        || !Objects.equals(
                                claims.sessionId(),
                                current.sessionId())
                        || !Objects.equals(
                                claims.viewReleaseId(),
                                current.viewReleaseId())) {
                    throw new BusinessForbiddenException(
                            "EMBED_LIST_RELEASE_CONTEXT_MISMATCH",
                            "Embed 列表发布版本与当前会话固定坐标不一致");
                }
                log.info(
                        "Embed 列表固定发布快照解析完成: entityCode={}, listId={}, listKey={}, releaseId={}, releaseVersion={}, source=EMBED_SESSION",
                        LogValue.safe(claims.entityCode()),
                        LogValue.safe(listConfigId),
                        LogValue.safe(list.getListKey()),
                        LogValue.safe(release.getId()),
                        release.getVersion());
                return new ResolvedEntityListRelease(
                        list,
                        release.getId(),
                        release.getVersion(),
                        true,
                        snapshot);
            }
            UiReleaseResolutionTokenService.Claims claims =
                    resolutionTokenService.verify(
                            releaseResolutionToken);
            ResolvedEntityFormRelease parent =
                    resolveRuntimeFormRelease(
                            claims.parentFormId(),
                            claims.parentReleaseId(),
                            claims.parentReleaseVersion(),
                            claims.context());
            if (!referencesListRelease(
                    parent.form(),
                    listConfigId,
                    releaseId,
                    expectedVersion)) {
                throw new BusinessForbiddenException(
                        "CHILD_LIST_RELEASE_NOT_REFERENCED",
                        "请求的列表发布版本不属于父表单有效快照");
            }
            log.info(
                    "列表钉定发布快照解析完成: parentFormId={}, parentReleaseId={}, listId={}, listKey={}, releaseId={}, releaseVersion={}, source=SIGNED_CONTEXT",
                    LogValue.safe(claims.parentFormId()),
                    LogValue.safe(parent.releaseId()),
                    LogValue.safe(listConfigId),
                    LogValue.safe(list.getListKey()),
                    LogValue.safe(release.getId()),
                    release.getVersion());
            return new ResolvedEntityListRelease(
                    list,
                    release.getId(),
                    release.getVersion(),
                    true,
                    snapshot);
        }

        release = releaseMapper.findActive(LIST, listConfigId);
        if (release == null) {
            throw new BusinessConflictException(
                    "LIST_RELEASE_REQUIRED",
                    "列表尚未发布或没有激活版本");
        }
        EntityListConfig owner = listConfigMapper.selectById(
                listConfigId);
        if (owner == null
                || !Objects.equals(
                        owner.getActiveReleaseId(),
                        release.getId())) {
            throw new BusinessConflictException(
                    "LIST_RELEASE_STATE_CONFLICT",
                    "列表发布状态不一致，请重新发布或激活版本");
        }
        // schema 等读路径是只读事务，不能在这里回写 published_version。
        // 该字段若曾被数据范围发布误改，以激活的界面发布为准，不拦截加载。
        if (StringUtils.hasText(releaseId)
                && !Objects.equals(releaseId, release.getId())) {
            throw new BusinessConflictException(
                    "LIST_RELEASE_CONFLICT",
                    "页面列表版本已过期，请刷新后重试");
        }
        if (expectedVersion != null
                && !Objects.equals(
                        expectedVersion,
                        release.getVersion())) {
            throw new BusinessConflictException(
                    "LIST_RELEASE_CONFLICT",
                    "页面列表版本已过期，请刷新后重试");
        }
        Map<String, Object> snapshot = verifiedSnapshot(release);
        EntityListConfigDTO list = runtimeList(
                snapshot,
                listConfigId);
        log.info(
                "列表激活发布快照解析完成: listId={}, listKey={}, releaseId={}, releaseVersion={}, source=ACTIVE",
                LogValue.safe(listConfigId),
                LogValue.safe(list.getListKey()),
                LogValue.safe(release.getId()),
                release.getVersion());
        return new ResolvedEntityListRelease(
                list,
                release.getId(),
                release.getVersion(),
                false,
                snapshot);
    }

    /**
     * 使用服务端已认证的坐标解析精确 LIST Release。
     *
     * <p>只供 Embed 等内部适配器在签发浏览器令牌前使用；公开 Controller
     * 不得把该方法映射为任意历史版本读取接口。</p>
     *
     * @param listConfigId 列表配置ID，后续用于解析{@code server}固定运行时列表发布版本时定位或关联目标
     * @param releaseId 发布版本ID，后续用于解析{@code server}固定运行时列表发布版本时定位或关联目标
     * @param expectedVersion 预期版本，作为 {@code requireListRelease} 的输入影响后续处理
     * @return 解析后的{@code server}固定运行时列表发布版本结果，供调用方继续处理
     */
    public ResolvedEntityListRelease resolveServerPinnedRuntimeListRelease(
            String listConfigId,
            String releaseId,
            Integer expectedVersion) {
        if (!StringUtils.hasText(listConfigId)
                || !StringUtils.hasText(releaseId)
                || expectedVersion == null || expectedVersion < 1) {
            throw new IllegalArgumentException("列表固定发布坐标不完整");
        }
        UiConfigRelease release = requireListRelease(
                listConfigId, releaseId, expectedVersion);
        Map<String, Object> snapshot = verifiedSnapshot(release);
        EntityListConfigDTO list = runtimeList(snapshot, listConfigId);
        return new ResolvedEntityListRelease(
                list,
                release.getId(),
                release.getVersion(),
                true,
                snapshot);
    }

    /**
     * 校验并获取列表发布版本；不满足约束时阻止后续处理。
     *
     * @param listConfigId 列表配置ID，后续用于校验并获取列表发布版本时定位或关联目标
     * @param releaseId 发布版本ID，后续用于校验并获取列表发布版本时定位或关联目标
     * @param expectedVersion 预期版本，供本方法校验并获取列表发布版本时使用
     * @return 校验并获取后的列表发布版本结果，供调用方继续处理
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
    private UiConfigRelease requireListRelease(
            String listConfigId,
            String releaseId,
            Integer expectedVersion) {
        UiConfigRelease release = releaseMapper.selectById(releaseId);
        if (release == null
                || !LIST.equals(release.getConfigType())
                || !Objects.equals(
                        listConfigId,
                        release.getConfigId())
                || (expectedVersion != null
                && !Objects.equals(
                        expectedVersion,
                        release.getVersion()))) {
            throw new BusinessConflictException(
                    "LIST_PINNED_RELEASE_CONFLICT",
                    "父表单固定的列表发布版本不存在或不一致");
        }
        return release;
    }

    /**
     * 处理运行时列表，并将结果传给后续步骤。
     *
     * @param snapshot 快照，供本方法处理运行时列表时使用
     * @param expectedListId 预期列表ID，后续用于处理运行时列表时定位或关联目标
     * @return 处理后的运行时列表结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntityListConfigDTO runtimeList(
            Map<String, Object> snapshot,
            String expectedListId) {
        Object listDocument = snapshot.get("list");
        if (listDocument == null) {
            throw new IllegalArgumentException(
                    "列表发布快照缺少 list 文档");
        }
        EntityListConfigDTO list = objectMapper.convertValue(
                listDocument,
                EntityListConfigDTO.class);
        if (!StringUtils.hasText(list.getId())) {
            throw new IllegalArgumentException(
                    "列表发布快照缺少列表ID");
        }
        if (!Objects.equals(expectedListId, list.getId())) {
            throw new IllegalArgumentException(
                    "列表发布快照与发布记录归属不一致");
        }
        return list;
    }

    /**
     * 判断引用列表发布版本条件是否成立，供调用方选择后续分支。
     *
     * @param parent 父级，供本方法处理引用列表发布版本时使用
     * @param listConfigId 列表配置ID，后续用于处理引用列表发布版本时定位或关联目标
     * @param listReleaseId 列表发布版本ID，后续用于处理引用列表发布版本时定位或关联目标
     * @param listReleaseVersion 列表发布版本，供本方法处理引用列表发布版本时使用
     * @return 引用列表发布版本条件成立时为 true，否则为 false
     */
    private boolean referencesListRelease(
            EntityForm parent,
            String listConfigId,
            String listReleaseId,
            Integer listReleaseVersion) {
        if (parent == null) {
            return false;
        }
        for (EntityFormNode node : parent.getNodes() == null
                ? List.<EntityFormNode>of()
                : parent.getNodes()) {
            if (matchesListReference(
                    documentValue(node.getPropsDocument()),
                    listConfigId,
                    listReleaseId,
                    listReleaseVersion)) {
                return true;
            }
        }
        for (EntityFormField field : parent.getFields() == null
                ? List.<EntityFormField>of()
                : parent.getFields()) {
            if (matchesListReference(
                    documentValue(field.getComponentProps()),
                    listConfigId,
                    listReleaseId,
                    listReleaseVersion)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否匹配列表引用；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否匹配列表引用的原始输入，结果供调用方继续使用
     * @param listConfigId 列表配置ID，后续用于判断是否匹配列表引用时定位或关联目标
     * @param listReleaseId 列表发布版本ID，后续用于判断是否匹配列表引用时定位或关联目标
     * @param listReleaseVersion 列表发布版本，作为 {@code firstOptionalInteger} 的输入影响后续处理
     * @return 列表引用条件成立时为 true，否则为 false
     */
    private boolean matchesListReference(
            Object value,
            String listConfigId,
            String listReleaseId,
            Integer listReleaseVersion) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> map = new LinkedHashMap<>();
            source.forEach((key, child) ->
                    map.put(String.valueOf(key), child));
            String referencedListId = firstOptionalText(
                    map.get("listId"),
                    map.get("refListId"),
                    map.get("publishedListId"));
            String referencedReleaseId = firstOptionalText(
                    map.get("listReleaseId"),
                    map.get("refListReleaseId"),
                    map.get("publishedListReleaseId"));
            Integer referencedVersion = firstOptionalInteger(
                    map.get("listReleaseVersion"),
                    map.get("refListReleaseVersion"),
                    map.get("publishedListReleaseVersion"));
            if (Objects.equals(listConfigId, referencedListId)
                    && Objects.equals(
                            listReleaseId,
                            referencedReleaseId)
                    && Objects.equals(
                            listReleaseVersion,
                            referencedVersion)) {
                return true;
            }
            return map.values().stream().anyMatch(child ->
                    matchesListReference(
                            child,
                            listConfigId,
                            listReleaseId,
                            listReleaseVersion));
        }
        if (value instanceof List<?> list) {
            return list.stream().anyMatch(child ->
                    matchesListReference(
                            child,
                            listConfigId,
                            listReleaseId,
                            listReleaseVersion));
        }
        return false;
    }

    /**
     * 封装已解析实体列表发布版本的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param list 列表，保存在对象中供后续校验、查询或展示
     * @param releaseId 发布版本 ID，后续用于解析固定配置
     * @param releaseVersion 发布版本号，后续用于校验快照一致性
     * @param pinned 固定，保存在对象中供后续校验、查询或展示
     * @param snapshot 快照，保存在对象中供后续校验、查询或展示
     */
    public record ResolvedEntityListRelease(
            EntityListConfigDTO list,
            String releaseId,
            Integer releaseVersion,
            boolean pinned,
            Map<String, Object> snapshot) {
    }

    /**
     * 构建草稿快照；结果供后续流程传递或持久化。
     *
     * @param configType 配置类型标识，决定后续草稿快照采用的处理分支
     * @param configId 配置ID，后续用于构建草稿快照时定位或关联目标
     * @return 草稿快照键值结果，供调用方继续处理
     */
    private Map<String, Object> buildDraftSnapshot(
            String configType,
            String configId) {
        return buildDraftSnapshot(configType, configId, true);
    }

    /**
     * 构建草稿快照；结果供后续流程传递或持久化。
     *
     * @param configType 配置类型标识，决定后续草稿快照采用的处理分支
     * @param configId 配置ID，后续用于构建草稿快照时定位或关联目标
     * @param pinRuntimeReferences 固定运行时引用，供本方法构建草稿快照时使用
     * @return 草稿快照键值结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private Map<String, Object> buildDraftSnapshot(
            String configType,
            String configId,
            boolean pinRuntimeReferences) {
        requireType(configType);
        if (FORM.equals(configType)) {
            EntityForm form = formService.getById(configId);
            if (form == null) {
                throw new IllegalArgumentException("表单不存在");
            }
            // 发布、比较和撤销使用同一节点表示；字段只补充实体元数据和旧快照中的属性。
            List<EntityFormNode> normalizedNodes =
                    new com.workflow.entity.form.application.EntityFormFieldProjection(codec)
                            .materialize(form.getId(), form.getFields(), form.getNodes());
            List<EntityFormNode> publishedNodes = pinRuntimeReferences
                    ? pinSubListReleases(normalizedNodes) : normalizedNodes;
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("schemaVersion", 1);
            snapshot.put("configType", FORM);
            snapshot.put(
                    "form",
                    snapshotSupport.stableValue(formMetadata(form)));
            snapshot.put("nodes", snapshotSupport.stableValue(
                    publishedNodes));
            snapshot.put(
                    "legacyFields",
                    snapshotSupport.stableValue(
                            deriveRuntimeFields(form, publishedNodes)));
            snapshot.put(
                    "eventBindings",
                    snapshotSupport.stableValue(
                            eventBindingSnapshotService.snapshotForm(
                                    configId,
                                    form.getEntityId(),
                                    publishedNodes,
                                    pinRuntimeReferences)));
            snapshot.put(
                    "viewCompositions",
                    snapshotSupport.stableValue(
                            snapshotViewCompositions(FORM, configId)));
            return snapshot;
        }
        EntityListConfigDTO list = listConfigService.findById(configId);
        if (list == null) {
            throw new IllegalArgumentException("列表配置不存在");
        }
        pinListTargetFormReleases(list);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 1);
        snapshot.put("configType", LIST);
        snapshot.put("list", snapshotSupport.stableValue(list));
        snapshot.put(
                "eventBindings",
                snapshotSupport.stableValue(
                        eventBindingSnapshotService.snapshot(
                                LIST,
                                configId,
                                list.getEntityId(),
                                pinRuntimeReferences)));
        snapshot.put(
                "viewCompositions",
                snapshotSupport.stableValue(
                        snapshotViewCompositions(LIST, configId)));
        return snapshot;
    }

    /**
     * 整理快照视图{@code compositions}数据，供调用方遍历或继续处理。
     *
     * @param configType 配置类型标识，决定后续快照视图{@code compositions}采用的处理分支
     * @param configId 配置ID，后续用于处理快照视图{@code compositions}时定位或关联目标
     * @return 界面配置发布版本集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> snapshotViewCompositions(
            String configType,
            String configId) {
        return requireViewCompositionService().snapshot(configType, configId);
    }

    /**
     * 规范化视图{@code compositions}当前依赖集合；输出作为后续校验或处理的输入。
     *
     * @param items 条目，供本方法规范化视图{@code compositions}当前依赖集合时使用
     * @return 界面配置发布版本集合，供调用方遍历或展示
     */
    private List<Map<String, Object>>
            normalizeViewCompositionsForCurrentDependencies(
                    List<Map<String, Object>> items) {
        return requireViewCompositionService()
                .normalizeSnapshotForCurrentDependencies(items);
    }

    /**
     * 恢复视图{@code compositions}；结果供调用方的后续步骤使用。
     *
     * @param configType 配置类型标识，决定后续视图{@code compositions}采用的处理分支
     * @param configId 配置ID，后续用于恢复视图{@code compositions}时定位或关联目标
     * @param items 条目，供本方法恢复视图{@code compositions}时使用
     */
    private void restoreViewCompositions(
            String configType,
            String configId,
            List<Map<String, Object>> items) {
        requireViewCompositionService().restoreForRelease(
                configType,
                configId,
                items == null ? List.of() : items);
    }

    /**
     * 关联内容属于发布快照和恢复事务的强制组成部分。服务未装配时静默返回
     * 空集合会永久丢失配置，跳过恢复也会造成草稿与发布哈希伪对齐，因此所有
     * 发布生命周期入口都必须 fail-closed。
     *
     * @return 校验并获取后的视图组合服务结果，供调用方继续处理
     */
    private UiViewCompositionService requireViewCompositionService() {
        if (viewCompositionService == null) {
            throw new IllegalStateException(
                    "关联内容服务未装配，无法安全处理UI配置发布快照");
        }
        return viewCompositionService;
    }

    /**
     * 整理表单元数据数据，供调用方遍历或继续处理。
     *
     * @param form 表单，作为 {@code metadata.put} 的输入影响后续处理
     * @return 表单元数据键值结果，供调用方继续处理
     */
    private Map<String, Object> formMetadata(EntityForm form) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("id", form.getId());
        metadata.put("entityId", form.getEntityId());
        metadata.put("formName", form.getFormName());
        metadata.put("formKey", form.getFormKey());
        metadata.put("description", form.getDescription());
        metadata.put("layoutType", form.getLayoutType());
        metadata.put("isDefault", form.getIsDefault());
        metadata.put("status", form.getStatus());
        metadata.put("customComponent", form.getCustomComponent());
        metadata.put(
                "customComponentVersion",
                form.getCustomComponentVersion());
        metadata.put(
                "customComponentSnapshotVersion",
                form.getCustomComponentSnapshotVersion());
        metadata.put(
                "dataSourceBindingsDocument",
                form.getDataSourceBindingsDocument());
        metadata.put("viewConfig", form.getViewConfig());
        return metadata;
    }

    /**
     * 整理{@code derive}运行时字段数据，供调用方遍历或继续处理。
     *
     * @param form 表单，供本方法处理{@code derive}运行时字段时使用
     * @param publishedNodes 已发布节点集合，供本方法处理{@code derive}运行时字段时使用
     * @return 实体表单字段集合，供调用方遍历或展示
     */
    private List<EntityFormField> deriveRuntimeFields(
            EntityForm form, List<EntityFormNode> publishedNodes) {
        return new com.workflow.entity.form.application.EntityFormFieldProjection(codec)
                .derive(form, publishedNodes);
    }

    /**
     * 整理固定子级列表{@code releases}数据，供调用方遍历或继续处理。
     *
     * @param nodes 节点集合，供本方法处理固定子级列表{@code releases}时使用
     * @return 实体表单节点集合，供调用方遍历或展示
     */
    private List<EntityFormNode> pinSubListReleases(
            List<EntityFormNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        List<EntityFormNode> result = new ArrayList<>(nodes.size());
        for (EntityFormNode source : nodes) {
            EntityFormNode node = new EntityFormNode();
            BeanUtils.copyProperties(source, node);
            if (!"FIELD".equals(normalize(node.getNodeType()))
                    || !StringUtils.hasText(node.getPropsDocument())) {
                result.add(node);
                continue;
            }
            Map<String, Object> props = new LinkedHashMap<>(
                    codec.readObject(
                            node.getPropsDocument(),
                            "子列表发布节点属性"));
            if (!isSubListNode(props)) {
                result.add(node);
                continue;
            }
            Map<String, Object> componentProps = new LinkedHashMap<>(
                    mapValue(props.get("componentProps")));
            Map<String, Object> config = new LinkedHashMap<>(
                    mapValue(componentProps.get("subListConfig")));
            String targetEntityId = text(config.get("targetEntityId"));
            String targetEntityCode = text(config.get("targetEntityCode"));
            String listKey = text(config.get("listKey"));
            EntityListConfig list = requireSubListOwner(
                    targetEntityId,
                    targetEntityCode,
                    listKey,
                    nodeLabel(node));
            ResolvedEntityListRelease resolved =
                    resolveRuntimeListRelease(
                            list.getId(),
                            null,
                            null,
                            null);
            config.put("listId", list.getId());
            config.put("listReleaseId", resolved.releaseId());
            config.put(
                    "listReleaseVersion",
                    resolved.releaseVersion());
            componentProps.put("subListConfig", config);
            props.put("componentProps", componentProps);
            node.setPropsDocument(codec.write(
                    props,
                    "固定子列表发布版本"));
            result.add(node);
        }
        return List.copyOf(result);
    }

    /**
     * 新发布统一使用关系组件；历史展示字段不能再形成第二套关联规则。
     *
     * @param snapshot 快照，供本方法校验统一关系{@code components}时使用
     */
    private void validateUnifiedRelationComponents(Map<String, Object> snapshot) {
        for (EntityFormNode node : snapshotNodes(snapshot)) {
            Map<String, Object> props = StringUtils.hasText(node.getPropsDocument())
                    ? codec.readObject(node.getPropsDocument(), "关系组件属性") : Map.of();
            if (isSubListNode(props)) {
                throw new IllegalArgumentException("子列表实体字段已停用，请删除该节点并从关联内容中选择实体关系和列表");
            }
            if (Set.of("SUB_FORM", "REPEATER").contains(normalize(node.getNodeType()))
                    && (!"RELATION".equals(node.getBindingType()) || !StringUtils.hasText(node.getBindingRef()))) {
                throw new IllegalArgumentException("子表单和明细编辑必须绑定组成关系，请从实体关系重新添加组件");
            }
        }
    }

    /**
     * 校验发布；不满足约束时阻止后续处理。
     *
     * @param configType 配置类型标识，决定后续发布采用的处理分支
     * @param configId 配置ID，后续用于校验发布时定位或关联目标
     * @param snapshot 快照，作为 {@code validateUnifiedRelationComponents} 的输入影响后续处理
     */
    private void validateForPublish(
            String configType,
            String configId,
            Map<String, Object> snapshot) {
        if (FORM.equals(configType)) {
            formNodeService.validateTree(configId);
            validateUnifiedRelationComponents(snapshot);
            formConfigurationValidator.validateForm(runtimeForm(snapshot));
            validateSubListReferences(snapshot);
            validateFormActions(snapshot);
            validateExtensionReferences(snapshot);
            Map<String, Object> referenceSnapshot =
                    eventBindingSnapshotService
                            .activationReferenceSnapshot(snapshot);
            // 部分纯 Mock 调用方可能未定义新方法返回值；生产实现始终返回深拷贝。
            dataSourceValidator.validate(referenceSnapshot == null
                    ? snapshot : referenceSnapshot);
            requireViewCompositionService().validateReleaseSnapshot(
                    configType, configId, snapshot);
            return;
        }
        EntityListConfigDTO list = objectMapper.convertValue(
                snapshot.get("list"), EntityListConfigDTO.class);
        listConfigurationValidator.validate(list);
        requireListActionConfigService()
                .validateAvailabilityRules(list);
        validatePinnedListTargetForms(list);
        validateListTemplateReferences(list);
        dataSourceValidator.validate(snapshot);
        requireViewCompositionService().validateReleaseSnapshot(
                configType, configId, snapshot);
    }

    /**
     * 处理固定列表目标表单{@code releases}，并将结果传给后续步骤。
     *
     * @param list 列表，作为 {@code list.setToolbarConfig} 的输入影响后续处理
     */
    private void pinListTargetFormReleases(EntityListConfigDTO list) {
        list.setToolbarConfig(pinListTargetFormReleases(
                list,
                "TOOLBAR",
                list.getToolbarConfig()));
        list.setRowActionConfig(pinListTargetFormReleases(
                list,
                "ROW",
                list.getRowActionConfig()));
    }

    /**
     * 整理固定列表目标表单{@code releases}数据，供调用方遍历或继续处理。
     *
     * @param list 列表，作为 {@code requireTargetListForm} 的输入影响后续处理
     * @param position 位置，作为 {@code validateTargetFormButtonSemantics} 的输入影响后续处理
     * @param buttons 按钮集合，供本方法处理固定列表目标表单{@code releases}时使用
     * @return 界面配置发布版本集合，供调用方遍历或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private List<Map<String, Object>> pinListTargetFormReleases(
            EntityListConfigDTO list,
            String position,
            List<Map<String, Object>> buttons) {
        if (buttons == null) {
            return List.of();
        }
        List<Map<String, Object>> pinned = new ArrayList<>();
        for (Map<String, Object> source : buttons) {
            Map<String, Object> button = new LinkedHashMap<>(
                    source == null ? Map.of() : source);
            String targetFormId = text(button.get("targetFormId"));
            if (!StringUtils.hasText(targetFormId)) {
                button.remove("targetFormReleaseId");
                button.remove("targetFormReleaseVersion");
            } else {
                validateTargetFormButtonSemantics(button, position);
                EntityForm form = requireTargetListForm(list, targetFormId);
                UiConfigRelease release =
                        releaseMapper.findActive(FORM, targetFormId);
                if (release == null
                        || !Objects.equals(
                                form.getActiveReleaseId(),
                                release.getId())) {
                    throw new IllegalArgumentException(
                            "列表按钮目标表单没有可用的激活发布版本: "
                                    + targetFormId);
                }
                button.put("targetFormReleaseId", release.getId());
                button.put(
                        "targetFormReleaseVersion",
                        release.getVersion());
            }
            pinOpenListTargetRelease(button, position);
            pinned.add(button);
        }
        return pinned;
    }

    /**
     * 把 open-list 按钮当时指向的 ACTIVE List Release 写入宿主
     * 列表快照。列表标识仍用于导航展示，运行时不得再用它
     * 重新查 ACTIVE。
     *
     * @param button 按钮，作为 {@code text} 的输入影响后续处理
     * @param position 位置，作为 {@code IllegalArgumentException} 的输入影响后续处理
     */
    private void pinOpenListTargetRelease(
            Map<String, Object> button,
            String position) {
        if (!"open-list".equalsIgnoreCase(
                text(button.get("customMode")))) {
            button.remove("targetListId");
            button.remove("targetListReleaseId");
            button.remove("targetListReleaseVersion");
            return;
        }
        String entityCode = text(button.get("targetEntityCode"));
        String listKey = text(button.get("targetListKey"));
        if (!StringUtils.hasText(entityCode)
                || !StringUtils.hasText(listKey)) {
            throw new IllegalArgumentException(
                    position + " open-list 按钮必须配置目标实体和列表");
        }
        EntityListConfig target =
                listConfigMapper.findByEntityCodeAndListKey(
                        entityCode, listKey);
        if (target == null) {
            throw new IllegalArgumentException(
                    "open-list 目标列表不存在: "
                            + entityCode + "/" + listKey);
        }
        UiConfigRelease release = releaseMapper.findActive(
                LIST, target.getId());
        if (release == null
                || !Objects.equals(
                        target.getActiveReleaseId(), release.getId())) {
            throw new IllegalArgumentException(
                    "open-list 目标列表没有可用的激活发布版本: "
                            + entityCode + "/" + listKey);
        }
        EntityListConfigDTO published = runtimeList(
                verifiedSnapshot(release), target.getId());
        if (!Objects.equals(entityCode, published.getEntityCode())
                || !Objects.equals(listKey, published.getListKey())) {
            throw new IllegalArgumentException(
                    "open-list 目标列表发布快照归属不一致: "
                            + entityCode + "/" + listKey);
        }
        button.put("targetListId", target.getId());
        button.put("targetListReleaseId", release.getId());
        button.put("targetListReleaseVersion", release.getVersion());
    }

    /**
     * 校验固定列表目标表单集合；不满足约束时阻止后续处理。
     *
     * @param list 列表，供本方法校验固定列表目标表单集合时使用
     */
    private void validatePinnedListTargetForms(EntityListConfigDTO list) {
        validatePinnedListTargetForms(
                list,
                "TOOLBAR",
                list.getToolbarConfig());
        validatePinnedListTargetForms(
                list,
                "ROW",
                list.getRowActionConfig());
    }

    /**
     * 校验固定列表目标表单集合；不满足约束时阻止后续处理。
     *
     * @param list 列表，作为 {@code requireTargetListForm} 的输入影响后续处理
     * @param position 位置，作为 {@code validateTargetFormButtonSemantics} 的输入影响后续处理
     * @param buttons 按钮集合，供本方法校验固定列表目标表单集合时使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validatePinnedListTargetForms(
            EntityListConfigDTO list,
            String position,
            List<Map<String, Object>> buttons) {
        for (Map<String, Object> button :
                buttons == null ? List.<Map<String, Object>>of() : buttons) {
            String targetFormId = text(button.get("targetFormId"));
            if (StringUtils.hasText(targetFormId)) {
                validateTargetFormButtonSemantics(button, position);
                requireTargetListForm(list, targetFormId);
                String releaseId = text(button.get("targetFormReleaseId"));
                Integer releaseVersion =
                        nullableInteger(button.get("targetFormReleaseVersion"));
                if (!StringUtils.hasText(releaseId)
                        || releaseVersion == null) {
                    throw new IllegalArgumentException(
                            "列表按钮目标表单未固定发布版本: "
                                    + targetFormId);
                }
                UiConfigRelease release = releaseMapper.selectById(releaseId);
                if (release == null
                        || !FORM.equals(release.getConfigType())
                        || !Objects.equals(
                                targetFormId, release.getConfigId())
                        || !Objects.equals(
                                releaseVersion, release.getVersion())) {
                    throw new IllegalArgumentException(
                            "列表按钮目标表单发布版本不存在或不匹配: "
                                    + targetFormId);
                }
            }
            validatePinnedOpenListTarget(button, position);
        }
    }

    /**
     * 验证 open-list 快照中的目标列表精确坐标与归属。
     *
     * @param button 按钮，作为 {@code text} 的输入影响后续处理
     * @param position 位置，作为 {@code IllegalArgumentException} 的输入影响后续处理
     */
    private void validatePinnedOpenListTarget(
            Map<String, Object> button,
            String position) {
        if (!"open-list".equalsIgnoreCase(
                text(button.get("customMode")))) {
            return;
        }
        String entityCode = text(button.get("targetEntityCode"));
        String listKey = text(button.get("targetListKey"));
        String listId = text(button.get("targetListId"));
        String releaseId = text(button.get("targetListReleaseId"));
        Integer releaseVersion = nullableInteger(
                button.get("targetListReleaseVersion"));
        if (!StringUtils.hasText(entityCode)
                || !StringUtils.hasText(listKey)
                || !StringUtils.hasText(listId)
                || !StringUtils.hasText(releaseId)
                || releaseVersion == null
                || releaseVersion < 1) {
            throw new IllegalArgumentException(
                    position + " open-list 按钮未固定目标列表发布版本");
        }
        EntityListConfig target =
                listConfigMapper.findByEntityCodeAndListKey(
                        entityCode, listKey);
        UiConfigRelease release = releaseMapper.selectById(releaseId);
        if (target == null
                || !Objects.equals(listId, target.getId())
                || release == null
                || !LIST.equals(release.getConfigType())
                || !Objects.equals(listId, release.getConfigId())
                || !Objects.equals(releaseVersion, release.getVersion())) {
            throw new IllegalArgumentException(
                    position + " open-list 目标列表发布坐标不存在或不匹配");
        }
        EntityListConfigDTO published = runtimeList(
                verifiedSnapshot(release), listId);
        if (!Objects.equals(entityCode, published.getEntityCode())
                || !Objects.equals(listKey, published.getListKey())) {
            throw new IllegalArgumentException(
                    position + " open-list 目标列表发布快照归属不一致");
        }
    }

    /**
     * 校验并获取目标列表表单；不满足约束时阻止后续处理。
     *
     * @param list 列表，供本方法校验并获取目标列表表单时使用
     * @param targetFormId 目标表单ID，后续用于校验并获取目标列表表单时定位或关联目标
     * @return 校验并获取后的目标列表表单结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntityForm requireTargetListForm(
            EntityListConfigDTO list,
            String targetFormId) {
        EntityForm form = formMapper.selectById(targetFormId);
        if (form == null) {
            throw new IllegalArgumentException(
                    "列表按钮目标表单不存在: " + targetFormId);
        }
        if (!Objects.equals(list.getEntityId(), form.getEntityId())) {
            throw new IllegalArgumentException(
                    "列表按钮目标表单必须属于当前列表实体: "
                            + targetFormId);
        }
        if (!Objects.equals(form.getStatus(), 1)) {
            throw new IllegalArgumentException(
                    "列表按钮目标表单未启用: " + targetFormId);
        }
        return form;
    }

    /**
     * 校验目标表单按钮{@code semantics}；不满足约束时阻止后续处理。
     *
     * @param button 按钮，作为 {@code text} 的输入影响后续处理
     * @param position 位置，供本方法校验目标表单按钮{@code semantics}时使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateTargetFormButtonSemantics(
            Map<String, Object> button,
            String position) {
        String buttonType = text(button.get("type"));
        String buttonKey = text(button.get("key"));
        String customMode = text(button.get("customMode"));
        boolean customOpenForm =
                "custom".equalsIgnoreCase(buttonType)
                        && "open-form".equalsIgnoreCase(customMode);
        boolean builtInTargetForm =
                "built-in".equalsIgnoreCase(buttonType)
                        && ("TOOLBAR".equals(position)
                                ? "create".equals(buttonKey)
                                : Set.of("view", "edit", "approve", "restartProcess")
                                        .contains(buttonKey));
        if (!customOpenForm && !builtInTargetForm) {
            throw new IllegalArgumentException(
                    "当前列表按钮不支持配置打开表单: "
                            + buttonKey);
        }
        if (!customOpenForm) {
            return;
        }
        String mode = text(button.get("targetFormMode"));
        mode = StringUtils.hasText(mode)
                ? mode.toUpperCase(Locale.ROOT)
                : ("TOOLBAR".equals(position) ? "CREATE" : "VIEW");
        if ("TOOLBAR".equals(position) && !"CREATE".equals(mode)) {
            throw new IllegalArgumentException(
                    "工具栏打开表单按钮仅支持新增模式");
        }
        if ("ROW".equals(position)
                && !Set.of("VIEW", "EDIT").contains(mode)) {
            throw new IllegalArgumentException(
                    "行打开表单按钮仅支持查看或编辑模式");
        }
        button.put("targetFormMode", mode);
    }

    /**
     * 校验快照{@code activation}；不满足约束时阻止后续处理。
     *
     * @param configType 配置类型标识，决定后续快照{@code activation}采用的处理分支
     * @param configId 配置ID，后续用于校验快照{@code activation}时定位或关联目标
     * @param snapshot 快照，作为 {@code validateFormSnapshotTree} 的输入影响后续处理
     */
    private void validateSnapshotForActivation(
            String configType,
            String configId,
            Map<String, Object> snapshot) {
        requireType(configType);
        if (FORM.equals(configType)) {
            validateFormSnapshotTree(configId, snapshot);
            EntityForm snapshotForm = runtimeForm(snapshot);
            formConfigurationValidator.validateForm(snapshotForm);
            formNodeService.validateSnapshotSubFormParameterContracts(
                    snapshotForm);
            validateSubListReferences(snapshot);
            validateFormActions(snapshot);
            validateExtensionReferences(snapshot);
            Map<String, Object> referenceSnapshot =
                    eventBindingSnapshotService
                            .activationReferenceSnapshot(snapshot);
            // 完整 v1 步骤按不可变制品校验；未钉版历史步骤仍回读当前定义。
            dataSourceValidator.validate(referenceSnapshot == null
                    ? snapshot : referenceSnapshot);
            requireViewCompositionService().validateReleaseSnapshot(
                    configType, configId, snapshot);
            return;
        }
        EntityListConfigDTO list = objectMapper.convertValue(
                snapshot.get("list"), EntityListConfigDTO.class);
        listConfigurationValidator.validate(list);
        requireListActionConfigService()
                .validateAvailabilityRules(list);
        validatePinnedListTargetForms(list);
        validateListTemplateReferences(list);
        dataSourceValidator.validate(snapshot);
        requireViewCompositionService().validateReleaseSnapshot(
                configType, configId, snapshot);
    }

    /**
     * 校验扩展引用；不满足约束时阻止后续处理。
     *
     * @param snapshot 快照，作为 {@code objectMapper.convertValue} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateExtensionReferences(Map<String, Object> snapshot) {
        EntityForm form = objectMapper.convertValue(
                snapshot.get("form"), EntityForm.class);
        List<EntityFormNode> nodes = objectMapper.convertValue(
                snapshot.getOrDefault("nodes", List.of()),
                new TypeReference<List<EntityFormNode>>() {});
        if (StringUtils.hasText(form.getCustomComponent())) {
            var definition = extensionDefinitionService.requireActive(
                    "FORM",
                    form.getCustomComponent(),
                    form.getCustomComponentVersion());
            EntityDefinition entity =
                    entityDefinitionMapper.selectById(form.getEntityId());
            if (entity == null) {
                throw new IllegalArgumentException(
                        "表单所属实体不存在: " + form.getEntityId());
            }
            extensionDefinitionService.validateEntityScope(
                    definition,
                    entity.getEntityCode());
            extensionDefinitionService.validateCompatibility(
                    definition,
                    null,
                    null,
                    null,
                    form.getCustomComponentSnapshotVersion());
        }
        for (EntityFormNode node : nodes) {
            if (!StringUtils.hasText(node.getComponentName())) {
                continue;
            }
            var definition = extensionDefinitionService.requireActive(
                    nodeExtensionType(node),
                    node.getComponentName(),
                    node.getComponentVersion());
            extensionDefinitionService.validateCompatibility(
                    definition,
                    null,
                    node.getNodeType(),
                    node.getBindingType(),
                    node.getSnapshotVersion());
        }
    }

    /**
     * 生成节点扩展类型文本，供后续匹配或展示。
     *
     * @param node 节点，作为 {@code UiExtensionReferencePolicy.resolveNodeExtensionType} 的输入影响后续处理
     * @return 处理后的节点扩展类型文本，供调用方比较或展示
     */
    private String nodeExtensionType(EntityFormNode node) {
        Map<String, Object> props =
                node != null
                        && StringUtils.hasText(node.getPropsDocument())
                        ? codec.readObject(
                                node.getPropsDocument(),
                                "表单节点扩展属性")
                        : Map.of();
        return UiExtensionReferencePolicy.resolveNodeExtensionType(
                node == null ? null : node.getNodeType(),
                props);
    }

    /**
     * 生成节点扩展类型文本，供后续匹配或展示。
     *
     * @param node 节点，作为 {@code mapValue} 的输入影响后续处理
     * @return 处理后的节点扩展类型文本，供调用方比较或展示
     */
    private String nodeExtensionType(Map<String, Object> node) {
        if (node == null) {
            return UiExtensionReferencePolicy.NODE;
        }
        Map<String, Object> props = mapValue(node.get("props"));
        if (props.isEmpty()
                && StringUtils.hasText(
                        text(node.get("propsDocument")))) {
            props = codec.readObject(
                    text(node.get("propsDocument")),
                    "表单节点扩展属性");
        }
        return UiExtensionReferencePolicy.resolveNodeExtensionType(
                text(node.get("nodeType")),
                props);
    }

    /**
     * 子列表只允许绑定真实存在且已经发布的实体列表。
     *
     * <p>运行时按实体编码和 listKey 解析列表，不能信任客户端提交的任意标识，
     * 因此在发布和激活时再次校验目标实体、实体编码以及列表发布状态。</p>
     *
     * @param snapshot 快照，供本方法校验子级列表引用时使用
     */
    private void validateSubListReferences(Map<String, Object> snapshot) {
        for (EntityFormNode node : snapshotNodes(snapshot)) {
            if (!"FIELD".equals(normalize(node.getNodeType()))) {
                continue;
            }
            Map<String, Object> props =
                    StringUtils.hasText(node.getPropsDocument())
                            ? codec.readObject(
                                    node.getPropsDocument(),
                                    "子列表节点属性")
                            : Map.of();
            if (!isSubListNode(props)) {
                continue;
            }
            Map<String, Object> componentProps =
                    mapValue(props.get("componentProps"));
            Map<String, Object> config =
                    mapValue(componentProps.get("subListConfig"));
            String targetEntityId = text(config.get("targetEntityId"));
            String targetEntityCode = text(config.get("targetEntityCode"));
            String listKey = text(config.get("listKey"));
            String listId = text(config.get("listId"));
            String listReleaseId = text(config.get("listReleaseId"));
            Integer listReleaseVersion = nullableInteger(
                    config.get("listReleaseVersion"));
            String label = nodeLabel(node);
            EntityListConfig list = requireSubListOwner(
                    targetEntityId,
                    targetEntityCode,
                    listKey,
                    label);
            if (!StringUtils.hasText(listId)
                    || !StringUtils.hasText(listReleaseId)
                    || listReleaseVersion == null
                    || listReleaseVersion <= 0) {
                throw new IllegalArgumentException(
                        "子列表发布快照缺少固定列表版本，请恢复为草稿后重新发布: "
                                + label);
            }
            if (!Objects.equals(list.getId(), listId)) {
                throw new IllegalArgumentException(
                        "子列表固定的列表 ID 与目标列表不一致: " + label);
            }
            UiConfigRelease listRelease = requireListRelease(
                    listId,
                    listReleaseId,
                    listReleaseVersion);
            EntityListConfigDTO publishedList = runtimeList(
                    verifiedSnapshot(listRelease),
                    listId);
            if (!Objects.equals(targetEntityId, publishedList.getEntityId())
                    || !Objects.equals(
                            targetEntityCode,
                            publishedList.getEntityCode())
                    || !Objects.equals(listKey, publishedList.getListKey())) {
                throw new IllegalArgumentException(
                        "子列表固定版本与目标实体或列表编码不一致: " + label);
            }
        }
    }

    /**
     * 判断是否子级列表节点；判断结果决定调用方的后续分支。
     *
     * @param props 属性，作为 {@code normalize} 的输入影响后续处理
     * @return 子级列表节点条件成立时为 true，否则为 false
     */
    private boolean isSubListNode(Map<String, Object> props) {
        String fieldType = normalize(text(props.get("fieldType")));
        String componentType = text(props.get("componentType"));
        return "SUB_LIST".equals(fieldType)
                || "sub_list".equalsIgnoreCase(componentType);
    }

    /**
     * 校验并获取子级列表归属方；不满足约束时阻止后续处理。
     *
     * @param targetEntityId 目标实体ID，后续用于校验并获取子级列表归属方时定位或关联目标
     * @param targetEntityCode 目标实体编码，后续用于校验并获取子级列表归属方时定位或关联目标
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param label 标签，后续用于校验并获取子级列表归属方时匹配或展示
     * @return 校验并获取后的子级列表归属方结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntityListConfig requireSubListOwner(
            String targetEntityId,
            String targetEntityCode,
            String listKey,
            String label) {
        if (!StringUtils.hasText(targetEntityId)
                || !StringUtils.hasText(targetEntityCode)
                || !StringUtils.hasText(listKey)) {
            throw new IllegalArgumentException(
                    "子列表必须配置目标实体和已发布列表: " + label);
        }
        EntityDefinition target =
                entityDefinitionMapper.selectById(targetEntityId);
        if (target == null) {
            throw new IllegalArgumentException(
                    "子列表目标实体不存在: " + label);
        }
        if (!targetEntityCode.equals(target.getEntityCode())) {
            throw new IllegalArgumentException(
                    "子列表目标实体编码与实体 ID 不一致: " + label);
        }
        EntityListConfig list =
                listConfigMapper.findByEntityIdAndListKey(
                        targetEntityId,
                        listKey);
        if (list == null) {
            throw new IllegalArgumentException(
                    "子列表引用的列表不存在: "
                            + targetEntityCode + "/" + listKey);
        }
        return list;
    }

    /**
     * 校验表单动作集合；不满足约束时阻止后续处理。
     *
     * @param snapshot 快照，作为 {@code objectMapper.convertValue} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateFormActions(Map<String, Object> snapshot) {
        EntityForm form = objectMapper.convertValue(
                snapshot.get("form"),
                EntityForm.class);
        EntityDefinition definition =
                entityDefinitionMapper.selectById(form.getEntityId());
        Set<String> actionSlotKeys = snapshotNodes(snapshot).stream()
                .filter(node -> "ACTION_SLOT".equals(
                        normalize(node.getNodeType())))
                .map(EntityFormNode::getNodeKey)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        List<Map<String, Object>> eventBindings = mapList(
                snapshot.get("eventBindings"));
        Map<String, Object> viewConfig =
                StringUtils.hasText(form.getViewConfig())
                        ? codec.readObject(
                                form.getViewConfig(),
                                "表单视图配置")
                        : Map.of();
        EntityFormActionConfigPolicy actionPolicy =
                new EntityFormActionConfigPolicy();
        boolean systemEntity = definition != null
                && definition.getStorageMode()
                == EntityDefinition.StorageMode.SYSTEM;
        // 先完成按钮本体与动作插槽的结构校验；事件主处理约束需要在下方按
        // ENTITY OWNER → FORM OWNER → BUTTON 合并最终链后单独判断。
        actionPolicy.validate(
                viewConfig,
                systemEntity,
                actionSlotKeys,
                true,
                Set.of(),
                false);
        List<Map<String, Object>> customButtons = mapList(
                actionPolicy.actionBar(viewConfig)
                        .get("customButtons"));
        Set<String> customButtonKeys = customButtons.stream()
                .map(button -> text(button.get("key")))
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> orphanBindingKeys = eventBindings.stream()
                .filter(binding -> "BUTTON".equals(
                        normalize(text(binding.get("targetType")))))
                .filter(binding -> UiDataSourceUsages
                        .FORM_BUTTON_CLICK.equals(
                        normalize(text(binding.get("eventCode")))))
                // DISABLE 继承覆盖也是按钮身份声明，不能成为
                // 绕过按钮目录的孤儿绑定，因此这里不按模式过滤。
                .map(binding -> text(binding.get("targetKey")))
                .filter(key -> !customButtonKeys.contains(key))
                .collect(java.util.stream.Collectors.toCollection(
                        java.util.LinkedHashSet::new));
        if (!orphanBindingKeys.isEmpty()) {
            throw new IllegalArgumentException(
                    "FORM_BUTTON_CLICK 绑定必须引用 actionBar.customButtons: "
                            + String.join(", ", orphanBindingKeys));
        }
        Map<String, Integer> invalidMainStepCounts = new LinkedHashMap<>();
        Set<String> conditionalMainStepKeys = new LinkedHashSet<>();
        customButtons.stream()
                .filter(button -> !Boolean.FALSE.equals(
                        button.get("enabled")))
                .map(button -> text(button.get("key")))
                .filter(StringUtils::hasText)
                .forEach(key -> {
                    List<Map<String, Object>> mainSteps =
                            effectiveFormButtonMainSteps(
                                    eventBindings, form, key);
                    int count = mainSteps.size();
                    if (count != 1) {
                        invalidMainStepCounts.put(key, count);
                    } else if (hasExecutionCondition(mainSteps.get(0))) {
                        conditionalMainStepKeys.add(key);
                    }
                });
        if (!invalidMainStepCounts.isEmpty()) {
            String details = invalidMainStepCounts.entrySet().stream()
                    .map(entry -> entry.getKey() + "（"
                            + entry.getValue() + " 个）")
                    .collect(java.util.stream.Collectors.joining(", "));
            throw new BusinessConflictException(
                    "UI_EVENT_FORM_BUTTON_MAIN_STEP_REQUIRED",
                    "启用的表单自定义按钮最终有效链必须且只能包含一个主处理步骤："
                            + details);
        }
        if (!conditionalMainStepKeys.isEmpty()) {
            throw new BusinessConflictException(
                    "UI_EVENT_FORM_BUTTON_MAIN_STEP_CONDITION_UNSUPPORTED",
                    "表单自定义按钮的主处理步骤必须无条件执行，请移除主处理的执行条件："
                            + String.join(", ", conditionalMainStepKeys));
        }
    }

    /**
     * 按运行时 ENTITY OWNER → FORM OWNER → BUTTON 的继承顺序解析有效步骤。
     * 空 INHERIT 可以复用默认链；DISABLE/REPLACE 与运行时保持完全一致。
     *
     * @param bindings 绑定集合，作为 {@code applyFormButtonLevel} 的输入影响后续处理
     * @param form 表单，作为 {@code applyFormButtonLevel} 的输入影响后续处理
     * @param buttonKey 按钮键，后续用于授权校验、关联或幂等去重
     * @return 界面配置发布版本集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> effectiveFormButtonMainSteps(
            List<Map<String, Object>> bindings,
            EntityForm form,
            String buttonKey) {
        List<Map<String, Object>> effective = new ArrayList<>();
        applyFormButtonLevel(
                effective,
                findFormButtonBinding(
                        bindings,
                        "ENTITY",
                        form.getEntityId(),
                        "OWNER",
                        null));
        applyFormButtonLevel(
                effective,
                findFormButtonBinding(
                        bindings,
                        FORM,
                        form.getId(),
                        "OWNER",
                        null));
        applyFormButtonLevel(
                effective,
                findFormButtonBinding(
                        bindings,
                        FORM,
                        form.getId(),
                        "BUTTON",
                        buttonKey));
        // BEFORE/AFTER 可按需配置多个；只有 REPLACE 在 FORM_BUTTON_CLICK 中
        // 承担唯一主处理职责，缺失或重复都会让按钮结果语义不确定。
        return effective.stream()
                .filter(step -> "REPLACE".equals(normalize(text(
                        step.get("strategy")))))
                .toList();
    }

    /**
     * 空条件对象等同于未配置；非空条件会让主处理存在被跳过的路径。
     *
     * @param step 步骤，供本方法判断是否具有执行条件时使用
     * @return 执行条件条件成立时为 true，否则为 false
     */
    private boolean hasExecutionCondition(Map<String, Object> step) {
        return step != null
                && step.get("condition") instanceof Map<?, ?> condition
                && !condition.isEmpty();
    }

    /**
     * 查询表单按钮绑定；查询结果供调用方展示或继续处理。
     *
     * @param bindings 绑定集合，供本方法查询表单按钮绑定时使用
     * @param ownerType 归属方类型标识，决定后续表单按钮绑定采用的处理分支
     * @param ownerId 归属方ID，后续用于查询表单按钮绑定时定位或关联目标
     * @param targetType 目标类型标识，决定后续表单按钮绑定采用的处理分支
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     * @return 表单按钮绑定键值结果，供调用方继续处理
     */
    private Map<String, Object> findFormButtonBinding(
            List<Map<String, Object>> bindings,
            String ownerType,
            String ownerId,
            String targetType,
            String targetKey) {
        return bindings.stream()
                .filter(binding -> !Boolean.FALSE.equals(
                        binding.get("enabled")))
                .filter(binding -> ownerType.equals(normalize(text(
                        binding.get("ownerType")))))
                .filter(binding -> Objects.equals(
                        ownerId, text(binding.get("ownerId"))))
                .filter(binding -> targetType.equals(normalize(text(
                        binding.getOrDefault("targetType", "OWNER")))))
                .filter(binding -> Objects.equals(
                        normalizedTargetKey(targetKey),
                        normalizedTargetKey(text(binding.get("targetKey")))))
                .filter(binding -> UiDataSourceUsages.FORM_BUTTON_CLICK.equals(
                        normalize(text(binding.get("eventCode")))))
                .findFirst()
                .orElse(null);
    }

    /**
     * 应用表单按钮层级，并将结果传给后续步骤。
     *
     * @param effective 有效，供本方法应用表单按钮层级时使用
     * @param binding 绑定，作为 {@code normalize} 的输入影响后续处理
     */
    private void applyFormButtonLevel(
            List<Map<String, Object>> effective,
            Map<String, Object> binding) {
        if (binding == null) {
            return;
        }
        String mode = normalize(text(binding.getOrDefault(
                "inheritanceMode", "INHERIT")));
        if ("DISABLE".equals(mode)) {
            effective.clear();
            return;
        }
        if ("REPLACE".equals(mode)) {
            effective.clear();
        }
        effective.addAll(mapList(binding.get("steps")));
    }

    /**
     * 校验列表模板引用；不满足约束时阻止后续处理。
     *
     * @param list 列表，作为 {@code validateListActionTemplateReferences} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateListTemplateReferences(EntityListConfigDTO list) {
        if (list == null) {
            throw new IllegalArgumentException("列表发布快照不能为空");
        }
        for (EntityListField field : list.getFields() == null
                ? List.<EntityListField>of()
                : list.getFields()) {
            validateTemplateBinding(
                    field.getTemplateId(),
                    field.getTemplateVersion(),
                    "LIST_COLUMN_GROUP",
                    "列表字段 " + firstText(field.getFieldCode(), field.getId()));
        }
        validateListActionTemplateReferences(
                list.getToolbarConfig(),
                "工具栏按钮");
        validateListActionTemplateReferences(
                list.getRowActionConfig(),
                "行按钮");
    }

    /**
     * 校验列表动作模板引用；不满足约束时阻止后续处理。
     *
     * @param actions 动作集合，供本方法校验列表动作模板引用时使用
     * @param positionLabel 位置标签，后续用于校验列表动作模板引用时匹配或展示
     */
    private void validateListActionTemplateReferences(
            List<Map<String, Object>> actions,
            String positionLabel) {
        for (Map<String, Object> action : actions == null
                ? List.<Map<String, Object>>of()
                : actions) {
            validateTemplateBinding(
                    text(action.get("templateId")),
                    nullableInteger(action.get("templateVersion")),
                    "BUTTON_GROUP",
                    positionLabel
                            + " "
                            + firstText(
                                    text(action.get("key")),
                                    text(action.get("label"))));
        }
    }

    /**
     * 校验模板绑定；不满足约束时阻止后续处理。
     *
     * @param templateId 模板ID，后续用于校验模板绑定时定位或关联目标
     * @param templateVersion 模板版本，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param requiredType 必填类型标识，决定后续模板绑定采用的处理分支
     * @param referenceLabel 引用标签，后续用于校验模板绑定时匹配或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateTemplateBinding(
            String templateId,
            Integer templateVersion,
            String requiredType,
            String referenceLabel) {
        boolean hasTemplateId = StringUtils.hasText(templateId);
        boolean hasTemplateVersion = templateVersion != null;
        if (!hasTemplateId && !hasTemplateVersion) {
            return;
        }
        if (!hasTemplateId || templateVersion == null || templateVersion < 1) {
            throw new IllegalArgumentException(
                    referenceLabel
                            + " 必须同时锁定 templateId 和 templateVersion");
        }
        UiComponentTemplate template = templateMapper.selectById(templateId);
        if (template == null
                || Integer.valueOf(1).equals(template.getDeleted())
                || !"ACTIVE".equalsIgnoreCase(template.getStatus())) {
            throw new IllegalArgumentException(
                    referenceLabel
                            + " 引用的组件模板不存在或未启用: "
                            + templateId);
        }
        if (!requiredType.equals(normalize(template.getTemplateType()))) {
            throw new IllegalArgumentException(
                    referenceLabel
                            + " 必须绑定 "
                            + requiredType
                            + " 模板，实际为 "
                            + template.getTemplateType());
        }
        UiComponentTemplateVersion version = templateVersionMapper.selectOne(
                new LambdaQueryWrapper<UiComponentTemplateVersion>()
                        .eq(UiComponentTemplateVersion::getTemplateId, templateId)
                        .eq(UiComponentTemplateVersion::getVersion, templateVersion));
        if (version == null) {
            throw new IllegalArgumentException(
                    referenceLabel
                            + " 引用的组件模板版本不存在: "
                            + templateId
                            + "@"
                            + templateVersion);
        }
        verifyTemplateVersionIntegrity(version, referenceLabel);
    }

    /**
     * 验证模板版本{@code integrity}；不满足约束时阻止后续处理。
     *
     * @param version 版本，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param referenceLabel 引用标签，后续用于验证模板版本{@code integrity}时匹配或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void verifyTemplateVersionIntegrity(
            UiComponentTemplateVersion version,
            String referenceLabel) {
        if (!StringUtils.hasText(version.getSnapshotDocument())
                || !StringUtils.hasText(version.getContentHash())
                || !Objects.equals(
                        version.getContentHash(),
                        snapshotSupport.hash(
                                version.getSnapshotDocument()))) {
            throw new IllegalArgumentException(
                    referenceLabel
                            + " 引用的组件模板版本完整性校验失败: "
                            + version.getTemplateId()
                            + "@"
                            + version.getVersion());
        }
        codec.readObject(
                version.getSnapshotDocument(),
                "组件模板版本快照");
    }

    /**
     * 校验表单快照树；不满足约束时阻止后续处理。
     *
     * @param formId 表单ID，后续用于校验表单快照树时定位或关联目标
     * @param snapshot 快照，作为 {@code snapshotNodes} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateFormSnapshotTree(
            String formId,
            Map<String, Object> snapshot) {
        List<EntityFormNode> nodes = snapshotNodes(snapshot);
        Map<String, EntityFormNode> byId = new HashMap<>();
        Set<String> nodeKeys = new HashSet<>();
        for (EntityFormNode node : nodes) {
            if (!StringUtils.hasText(node.getId())) {
                throw new IllegalArgumentException("发布快照中的表单节点缺少稳定 ID");
            }
            if (byId.put(node.getId(), node) != null) {
                throw new IllegalArgumentException(
                        "发布快照中的表单节点 ID 重复: " + node.getId());
            }
            if (!StringUtils.hasText(node.getNodeKey())
                    || !nodeKeys.add(node.getNodeKey())) {
                throw new IllegalArgumentException(
                        "发布快照中的表单节点编码为空或重复: " + node.getNodeKey());
            }
            String nodeType = normalize(node.getNodeType());
            if (!FORM_NODE_TYPES.contains(nodeType)) {
                throw new IllegalArgumentException(
                        "发布快照包含不支持的表单节点类型: " + node.getNodeType());
            }
            node.setNodeType(nodeType);
        }
        for (EntityFormNode node : nodes) {
            EntityFormNode parent = StringUtils.hasText(node.getParentId())
                    ? byId.get(node.getParentId())
                    : null;
            if (StringUtils.hasText(node.getParentId()) && parent == null) {
                throw new IllegalArgumentException(
                        "发布快照中的表单节点父级不存在: " + nodeLabel(node));
            }
            validateSnapshotParentChild(node, parent);
            int depth = 1;
            Set<String> visited = new HashSet<>();
            String parentId = node.getParentId();
            while (StringUtils.hasText(parentId)) {
                if (!visited.add(parentId) || Objects.equals(parentId, node.getId())) {
                    throw new IllegalArgumentException(
                            "发布快照中的表单节点存在循环引用: " + nodeLabel(node));
                }
                EntityFormNode ancestor = byId.get(parentId);
                if (ancestor == null) {
                    throw new IllegalArgumentException(
                            "发布快照中的表单节点父级不存在: " + nodeLabel(node));
                }
                if (!FORM_CONTAINER_TYPES.contains(ancestor.getNodeType())) {
                    throw new IllegalArgumentException(
                            "发布快照中的非容器节点不能包含子节点: "
                                    + nodeLabel(ancestor));
                }
                parentId = ancestor.getParentId();
                if (++depth > MAX_FORM_DEPTH) {
                    throw new IllegalArgumentException(
                            "发布快照表单嵌套层级不能超过 "
                                    + MAX_FORM_DEPTH
                                    + " 层");
                }
            }
        }
        Map<String, List<String>> referenceCache = new HashMap<>();
        referenceCache.put(formId, referencedFormIds(nodes));
        validatePublishedFormGraph(
                formId, 1, new LinkedHashSet<>(), referenceCache);
    }

    /**
     * 校验快照父级子级；不满足约束时阻止后续处理。
     *
     * @param child 子级，供本方法校验快照父级子级时使用
     * @param parent 父级，作为 {@code ALLOWED_CHILD_TYPES.get} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateSnapshotParentChild(
            EntityFormNode child,
            EntityFormNode parent) {
        if (parent == null) {
            if ("TAB".equals(child.getNodeType())) {
                throw new IllegalArgumentException("TAB 节点只能位于 TAB_SET 下");
            }
            return;
        }
        Set<String> allowedChildren = ALLOWED_CHILD_TYPES.get(parent.getNodeType());
        if (allowedChildren == null || !allowedChildren.contains(child.getNodeType())) {
            if ("TAB".equals(child.getNodeType())) {
                throw new IllegalArgumentException("TAB 节点只能位于 TAB_SET 下");
            }
            if ("TAB_SET".equals(parent.getNodeType())) {
                throw new IllegalArgumentException("TAB_SET 的直接子节点只能是 TAB");
            }
            throw new IllegalArgumentException(
                    parent.getNodeType()
                            + " 节点不能直接包含 "
                            + child.getNodeType()
                            + " 节点");
        }
    }

    /**
     * 校验已发布表单图；不满足约束时阻止后续处理。
     *
     * @param formId 表单ID，后续用于校验已发布表单图时定位或关联目标
     * @param depth 深度，供本方法校验已发布表单图时使用
     * @param path 路径，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param referenceCache 引用缓存，供本方法校验已发布表单图时使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validatePublishedFormGraph(
            String formId,
            int depth,
            LinkedHashSet<String> path,
            Map<String, List<String>> referenceCache) {
        if (!path.add(formId)) {
            throw new IllegalArgumentException(
                    "子表单发布引用存在循环: "
                            + String.join(" -> ", path)
                            + " -> "
                            + formId);
        }
        for (String referencedFormId : referenceCache.computeIfAbsent(
                formId, this::activePublishedFormReferences)) {
            if (path.contains(referencedFormId)) {
                throw new IllegalArgumentException(
                        "子表单发布引用存在循环: "
                                + String.join(" -> ", path)
                                + " -> "
                                + referencedFormId);
            }
            if (depth >= MAX_FORM_DEPTH) {
                throw new IllegalArgumentException(
                        "跨表单嵌套层级不能超过 " + MAX_FORM_DEPTH + " 层");
            }
            validatePublishedFormGraph(
                    referencedFormId,
                    depth + 1,
                    path,
                    referenceCache);
        }
        path.remove(formId);
    }

    /**
     * 整理活动已发布表单引用数据，供调用方遍历或继续处理。
     *
     * @param formId 表单ID，后续用于处理活动已发布表单引用时定位或关联目标
     * @return 界面配置发布版本集合，供调用方遍历或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private List<String> activePublishedFormReferences(String formId) {
        UiConfigRelease release = releaseMapper.findActive(FORM, formId);
        if (release == null || !StringUtils.hasText(release.getSnapshotDocument())) {
            throw new IllegalArgumentException("子表单引用的表单尚未发布: " + formId);
        }
        Map<String, Object> snapshot = codec.readObject(
                release.getSnapshotDocument(), "子表单发布快照");
        return referencedFormIds(snapshotNodes(snapshot));
    }

    /**
     * 整理已引用表单ID 集合数据，供调用方遍历或继续处理。
     *
     * @param nodes 节点集合，供本方法处理已引用表单ID 集合时使用
     * @return 界面配置发布版本集合，供调用方遍历或展示
     */
    private List<String> referencedFormIds(List<EntityFormNode> nodes) {
        List<String> references = new ArrayList<>();
        for (EntityFormNode node : nodes) {
            if (!Set.of("SUB_FORM", "REPEATER").contains(node.getNodeType())
                    || !StringUtils.hasText(node.getPropsDocument())) {
                continue;
            }
            Map<String, Object> props = codec.readObject(
                    node.getPropsDocument(), "子表单发布节点属性");
            Object publishedFormId = props.get("publishedFormId");
            if (publishedFormId != null
                    && StringUtils.hasText(String.valueOf(publishedFormId))) {
                String referencedFormId = String.valueOf(publishedFormId).trim();
                if (!references.contains(referencedFormId)) {
                    references.add(referencedFormId);
                }
            }
        }
        return references;
    }

    /**
     * 整理快照节点集合数据，供调用方遍历或继续处理。
     *
     * @param snapshot 快照，作为 {@code objectMapper.convertValue} 的输入影响后续处理
     * @return 实体表单节点集合，供调用方遍历或展示
     */
    private List<EntityFormNode> snapshotNodes(Map<String, Object> snapshot) {
        return objectMapper.convertValue(
                snapshot.getOrDefault("nodes", List.of()),
                new TypeReference<List<EntityFormNode>>() {});
    }

    /**
     * 生成节点标签文本，供后续匹配或展示。
     *
     * @param node 节点，供本方法处理节点标签时使用
     * @return 处理后的节点标签文本，供调用方比较或展示
     */
    private String nodeLabel(EntityFormNode node) {
        return StringUtils.hasText(node.getNodeKey())
                ? node.getNodeKey()
                : node.getId();
    }

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param value 待规范化界面配置发布版本的原始输入，结果供调用方继续使用
     * @return 规范化后的界面配置发布版本文本，供调用方比较或展示
     */
    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : null;
    }

    /**
     * 审计详情；供后续追溯或审计使用。
     *
     * @param preview 预览，作为 {@code objectMapper.convertValue} 的输入影响后续处理
     * @return 详情键值结果，供调用方继续处理
     */
    private Map<String, Object> auditDetail(
            UiConfigPublishPreviewDTO preview) {
        return objectMapper.convertValue(
                preview,
                new TypeReference<Map<String, Object>>() {});
    }

    /**
     * 记录审计；供后续追溯或审计使用。
     *
     * @param configType 配置类型标识，决定后续审计采用的处理分支
     * @param configId 配置ID，后续用于记录审计时定位或关联目标
     * @param releaseId 发布版本ID，后续用于记录审计时定位或关联目标
     * @param operation 操作标识，决定后续审计采用的处理分支
     * @param riskLevel 风险层级，作为 {@code audit.setRiskLevel} 的输入影响后续处理
     * @param reason 原因，作为 {@code audit.setReason} 的输入影响后续处理
     * @param detail 详情，作为 {@code audit.setDetailDocument} 的输入影响后续处理
     */
    private void recordAudit(
            String configType,
            String configId,
            String releaseId,
            String operation,
            String riskLevel,
            String reason,
            Object detail) {
        UiConfigReleaseAudit audit = new UiConfigReleaseAudit();
        audit.setConfigType(configType);
        audit.setConfigId(configId);
        audit.setReleaseId(releaseId);
        audit.setOperation(operation);
        audit.setRiskLevel(riskLevel);
        audit.setActorId(UserContext.getUserId());
        audit.setActorName(UserContext.getUsername());
        audit.setReason(blankToNull(reason));
        FormSubmissionExecutionContext traceContext =
                traceService.current(
                        operation,
                        null,
                        Map.of(
                                "configType", configType,
                                "configId", configId));
        audit.setTraceId(traceContext == null
                ? "srv_" + UUID.randomUUID()
                : traceContext.businessTraceKey());
        audit.setDetailDocument(detail == null
                ? null : codec.write(detail, "UI发布审计明细"));
        audit.setCreateTime(LocalDateTime.now());
        releaseAuditMapper.insert(audit);
        recordUnifiedReleaseAudit(audit);
    }

    /**
     * 将既有 UI 发布审计行投影到统一时间线。投影只保存审计行指针，发布快照、
     * 风险明细和原因仍由 UI 发布模块自己的接口鉴权读取。
     *
     * @param audit 审计，作为 {@code AuditEventIds.stable} 的输入影响后续处理
     */
    private void recordUnifiedReleaseAudit(
            UiConfigReleaseAudit audit) {
        if (auditPort == null) {
            // 仅允许非 Spring 的纯单元测试缺少端口；生产环境 setter 为必需注入。
            return;
        }
        OperationContext inherited =
                OperationContextHolder.current().orElse(null);
        String operationId = inherited == null
                ? AuditEventIds.stable(
                        "ui-release-operation",
                        audit.getId(),
                        audit.getOperation())
                : inherited.operationId();
        String traceId = inherited != null
                && StringUtils.hasText(inherited.traceId())
                ? inherited.traceId()
                : audit.getTraceId();
        AuditSourcePointer source = new AuditSourcePointer(
                "ENTITY_UI_RELEASE",
                "UI_CONFIG_RELEASE_AUDIT",
                audit.getId(),
                audit.getId());
        try {
            auditPort.record(SystemAuditEvent.builder()
                    .eventId(AuditEventIds.stable(
                            "ui-release-audit", audit.getId()))
                    .operationContext(new OperationContext(
                            operationId,
                            traceId,
                            inherited == null
                                    ? null
                                    : inherited.parentOperationId(),
                            source))
                    .module(AuditModule.ENTITY)
                    .action(releaseAuditAction(audit.getOperation()))
                    .operationName("UI配置发布审计："
                            + audit.getOperation())
                    .riskLevel(releaseAuditRisk(audit.getRiskLevel()))
                    .result(AuditResult.SUCCESS)
                    .operatorId(audit.getActorId())
                    .operatorName(audit.getActorName())
                    .targetType("UI_CONFIG_RELEASE")
                    .targetId(audit.getReleaseId())
                    .summary("UI配置审计已记录："
                            + audit.getConfigType()
                            + "/" + audit.getConfigId()
                            + "/" + audit.getOperation())
                    .build());
        } catch (RuntimeException exception) {
            // 权威 ui_config_release_audit 已写入当前事务；统一只读投影故障
            // 由审计基础设施告警，不应改变发布本身的业务语义。
            log.warn(
                    "UI发布统一审计投影失败: auditId={}, releaseId={}, exceptionType={}",
                    audit.getId(),
                    audit.getReleaseId(),
                    exception.getClass().getName());
        }
    }

    /**
     * 处理发布版本审计动作，并将结果传给后续步骤。
     *
     * @param operation 操作标识，决定后续发布版本审计动作采用的处理分支
     * @return 处理后的发布版本审计动作结果，供调用方继续处理
     */
    private AuditAction releaseAuditAction(String operation) {
        String normalized = normalize(operation);
        if (normalized != null
                && normalized.startsWith("PUBLISH")) {
            return AuditAction.PUBLISH;
        }
        if ("ROLLBACK_HOTFIX".equals(normalized)
                || "RESTORE_DRAFT".equals(normalized)
                || "DISCARD_DRAFT".equals(normalized)) {
            return AuditAction.ROLLBACK;
        }
        if ("ACTIVATE_RELEASE".equals(normalized)) {
            return AuditAction.ENABLE;
        }
        return AuditAction.CONFIGURE;
    }

    /**
     * 处理发布版本审计风险，并将结果传给后续步骤。
     *
     * @param value 待处理发布版本审计风险的原始输入，结果供调用方继续使用
     * @return 处理后的发布版本审计风险结果，供调用方继续处理
     */
    private AuditRiskLevel releaseAuditRisk(String value) {
        String normalized = normalize(value);
        if (UiConfigSemanticPatchService.SAFE.equals(normalized)) {
            return AuditRiskLevel.LOW;
        }
        if (UiConfigSemanticPatchService.REVIEW.equals(normalized)) {
            return AuditRiskLevel.HIGH;
        }
        if (UiConfigSemanticPatchService.BLOCKED.equals(normalized)) {
            return AuditRiskLevel.CRITICAL;
        }
        try {
            return AuditRiskLevel.valueOf(normalized);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return AuditRiskLevel.MEDIUM;
        }
    }

    /**
     * 处理{@code deactivate}，并将结果传给后续步骤。
     *
     * @param configType 配置类型标识，决定后续{@code deactivate}采用的处理分支
     * @param configId 配置ID，后续用于处理{@code deactivate}时定位或关联目标
     */
    private void deactivate(String configType, String configId) {
        UpdateWrapper<UiConfigRelease> update = new UpdateWrapper<>();
        update.eq("config_type", configType)
                .eq("config_id", configId)
                .eq("status", "ACTIVE")
                .set("status", "INACTIVE");
        releaseMapper.update(null, update);
    }

    /**
     * 激活归属方；结果供调用方的后续步骤使用。
     *
     * @param configType 配置类型标识，决定后续归属方采用的处理分支
     * @param configId 配置ID，后续用于激活归属方时定位或关联目标
     * @param release 发布版本，作为 {@code switchActiveReleaseOnOwner} 的输入影响后续处理
     * @param contentHash 内容哈希，作为 {@code switchActiveReleaseOnOwner} 的输入影响后续处理
     */
    private void activateOnOwner(
            String configType,
            String configId,
            UiConfigRelease release,
            String contentHash) {
        switchActiveReleaseOnOwner(
                configType,
                configId,
                release,
                contentHash);
    }

    /**
     * 处理{@code switch}活动发布版本归属方，并将结果传给后续步骤。
     *
     * @param configType 配置类型标识，决定后续{@code switch}活动发布版本归属方采用的处理分支
     * @param configId 配置ID，后续用于处理{@code switch}活动发布版本归属方时定位或关联目标
     * @param release 发布版本，供本方法处理{@code switch}活动发布版本归属方时使用
     */
    private void switchActiveReleaseOnOwner(
            String configType,
            String configId,
            UiConfigRelease release) {
        switchActiveReleaseOnOwner(
                configType,
                configId,
                release,
                null);
    }

    /**
     * 处理{@code switch}活动发布版本归属方，并将结果传给后续步骤。
     *
     * @param configType 配置类型标识，决定后续{@code switch}活动发布版本归属方采用的处理分支
     * @param configId 配置ID，后续用于处理{@code switch}活动发布版本归属方时定位或关联目标
     * @param release 发布版本，供本方法处理{@code switch}活动发布版本归属方时使用
     * @param draftHash 草稿哈希，作为 {@code update.set} 的输入影响后续处理
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
    private void switchActiveReleaseOnOwner(
            String configType,
            String configId,
            UiConfigRelease release,
            String draftHash) {
        if (FORM.equals(configType)) {
            UpdateWrapper<EntityForm> update = new UpdateWrapper<>();
            update.eq("id", configId)
                    .set("active_release_id", release.getId())
                    .set("update_time", LocalDateTime.now());
            if (draftHash != null) {
                update.set("draft_hash", draftHash);
            }
            if (formMapper.update(null, update) != 1) {
                throw new BusinessConflictException(
                        "UI_CONFIG_OWNER_UPDATE_CONFLICT",
                        "表单发布状态已变化，请刷新后重试");
            }
            return;
        }
        UpdateWrapper<EntityListConfig> update = new UpdateWrapper<>();
        update.eq("id", configId)
                .set("active_release_id", release.getId())
                .set("published_version", release.getVersion())
                .set("update_time", LocalDateTime.now());
        if (draftHash != null) {
            update.set("draft_hash", draftHash);
        }
        if (listConfigMapper.update(null, update) != 1) {
            throw new BusinessConflictException(
                    "UI_CONFIG_OWNER_UPDATE_CONFLICT",
                    "列表发布状态已变化，请刷新后重试");
        }
    }

    /**
     * 校验并获取类型；不满足约束时阻止后续处理。
     *
     * @param configType 配置类型标识，决定后续类型采用的处理分支
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void requireType(String configType) {
        if (!FORM.equals(configType) && !LIST.equals(configType)) {
            throw new IllegalArgumentException("配置类型只能是 FORM 或 LIST");
        }
    }

    /**
     * 把空白文本转为 null，避免后续把空字符串当作有效配置。
     *
     * @param value 待处理空白截止空值的原始输入，结果供调用方继续使用
     * @return 处理后的空白截止空值文本，供调用方比较或展示
     */
    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 生成规范化目标键文本，供后续匹配或展示。
     *
     * @param value 待处理规范化目标键的原始输入，结果供调用方继续使用
     * @return 处理后的规范化目标键文本，供调用方比较或展示
     */
    private String normalizedTargetKey(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
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
                return value;
            }
        }
        return "未命名项";
    }

    /**
     * 处理可空整数，并将结果传给后续步骤。
     *
     * @param value 待处理可空整数的原始输入，结果供调用方继续使用
     * @return 处理后的可空整数结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private Integer nullableInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("模板版本必须是整数", exception);
        }
    }

    /**
     * 将输入解析为整数，供后续范围校验或计算使用。
     *
     * @param value 待处理整数的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的整数结果，供调用方继续处理
     */
    private Integer integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /**
     * 处理布尔值{@code flag}，并将结果传给后续步骤。
     *
     * @param value 待处理布尔值{@code flag}的原始输入，结果供调用方继续使用
     * @return 处理后的布尔值{@code flag}结果，供调用方继续处理
     */
    private Integer booleanFlag(Object value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    /**
     * 封装已准备热修复目标的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param target 目标，保存在对象中供后续校验、查询或展示
     * @param previous 上一项，保存在对象中供后续校验、查询或展示
     * @param restorablePreviousTargetId {@code restorable}上一项目标ID，后续用于处理已准备热修复目标时定位或关联目标
     * @param effectiveDocument 有效文档，保存在对象中供后续校验、查询或展示
     * @param effectiveHash 有效哈希，保存在对象中供后续校验、查询或展示
     */
    private record PreparedHotfixTarget(
            UiHotfixProcessTarget target,
            UiConfigHotfixTarget previous,
            String restorablePreviousTargetId,
            String effectiveDocument,
            String effectiveHash) {
    }

    /**
     * 封装有效热修复快照的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param document 文档，保存在对象中供后续校验、查询或展示
     * @param hash 哈希，保存在对象中供后续校验、查询或展示
     */
    private record EffectiveHotfixSnapshot(
            String document,
            String hash) {
    }

    /**
     * 封装热修复准备的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param active 活动，保存在对象中供后续校验、查询或展示
     * @param draftDocument 草稿文档，保存在对象中供后续校验、查询或展示
     * @param patch 补丁，保存在对象中供后续校验、查询或展示
     * @param targets 目标集合，保存在对象中供后续校验、查询或展示
     * @param preview 预览，保存在对象中供后续校验、查询或展示
     */
    private record HotfixPreparation(
            UiConfigRelease active,
            String draftDocument,
            UiConfigSemanticPatchService.PatchAnalysis patch,
            List<PreparedHotfixTarget> targets,
            UiConfigPublishPreviewDTO preview) {
    }
}
