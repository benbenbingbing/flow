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

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.contracts.migration.ConfigMigrationPublishRequest;
import com.workflow.contracts.migration.MigrationAssetHandler;
import com.workflow.contracts.ui.hotfix.UiHotfixProcessImpact;
import com.workflow.contracts.ui.hotfix.UiHotfixProcessImpactPort;
import com.workflow.contracts.ui.hotfix.UiHotfixProcessTarget;
import com.workflow.contracts.ui.UiDataSourceUsages;
import com.workflow.contracts.ui.runtime.UiRuntimePurpose;
import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;
import com.workflow.contracts.ui.runtime.UiPublishedFormReference;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.ui.api.response.UiConfigDiffDTO;
import com.workflow.entity.ui.api.response.UiConfigDiffItemDTO;
import com.workflow.entity.ui.api.response.UiConfigHotfixRiskItemDTO;
import com.workflow.entity.ui.api.response.UiConfigHotfixTargetPreviewDTO;
import com.workflow.entity.ui.api.response.UiConfigPublishPreviewDTO;
import com.workflow.entity.ui.api.request.UiConfigPublishRequest;
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
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private static final Map<String, Set<String>> TEMPLATE_NODE_TYPES = Map.of(
            "FIELD_GROUP", Set.of("SECTION", "GRID", "TAB", "COLLAPSE"),
            "FORM_SECTION", Set.of(
                    "SECTION", "GRID", "TAB_SET", "TAB", "COLLAPSE"),
            "SUB_FORM", Set.of("SUB_FORM", "REPEATER"));
    private final UiConfigReleaseMapper releaseMapper;
    private final UiConfigHotfixTargetMapper hotfixTargetMapper;
    private final UiConfigReleaseAuditMapper releaseAuditMapper;
    private final UiConfigDataSourceReferenceValidator dataSourceValidator;
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
    private final MigrationAssetHandler migrationAssetHandler;

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
        return releases;
    }

    private String resolveRolloutStatus(UiConfigRelease release) {
        List<UiConfigHotfixTarget> targets =
                hotfixTargetMapper.findByHotfixReleaseId(
                        release.getId());
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
                || hasRollbackAudit(release.getId())) {
            return "ROLLED_BACK";
        }
        return "ACTIVE".equals(release.getStatus())
                ? "ACTIVE" : "SUPERSEDED";
    }

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
                : codec.readObject(release.getSnapshotDocument(), "UI发布快照");
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
     */
    public Map<String, Object> runtimeFormRelease(
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
                            claims.depth() + 1));
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
        UiConfigRelease release = StringUtils.hasText(releaseId)
                ? releaseMapper.selectById(releaseId)
                : releaseMapper.findActive(FORM, formId);
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
        if (expectedVersion != null
                && !Objects.equals(expectedVersion, release.getVersion())) {
            log.info(
                    "表单运行时快照解析失败: formId={}, releaseId={}, expectedVersion={}, actualVersion={}, reason=VERSION_MISMATCH",
                    LogValue.safe(formId),
                    LogValue.safe(release.getId()),
                    expectedVersion,
                    release.getVersion());
            throw new IllegalArgumentException("表单运行时发布版本号不一致");
        }
        ResolvedEntityFormRelease resolved =
                resolvedRuntimeForm(
                        release,
                        StringUtils.hasText(releaseId));
        Map<String, Object> result = runtimeReleaseResult(
                resolved,
                verifiedSnapshot(release));
        log.info(
                "表单运行时快照解析完成: formId={}, releaseId={}, releaseVersion={}, effectiveReleaseId={}, hotfixApplied={}, source={}",
                LogValue.safe(formId),
                LogValue.safe(resolved.releaseId()),
                resolved.releaseVersion(),
                LogValue.safe(resolved.effectiveReleaseId()),
                resolved.hotfixApplied(),
                StringUtils.hasText(releaseId)
                        ? "PINNED" : "ACTIVE");
        return result;
    }

    /**
     * 解析表单事件运行时必须使用的精确发布快照。
     *
     * <p>流程表单携带服务端签名令牌时，事件绑定与字段定义必须从流程当前
     * 有效快照读取，不能退回全局 ACTIVE 版本。</p>
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
                    resolved.hotfixApplied());
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
                false);
    }

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

    private Map<String, Object> runtimeSnapshot(EntityForm form) {
        Map<String, Object> formDocument = objectMapper.convertValue(
                form,
                new TypeReference<Map<String, Object>>() {});
        formDocument.remove("fields");
        formDocument.remove("nodes");
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
        return snapshot;
    }

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

    private String firstOptionalText(Object... values) {
        for (Object value : values) {
            String candidate = text(value);
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

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
        return buildDraftSnapshot(configType, configId);
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
                : snapshotSupport.stableMap(codec.readObject(
                        active.getSnapshotDocument(), "UI发布快照"));
        String activeHash = active == null
                ? null
                : snapshotSupport.hash(
                        snapshotSupport.canonical(activeSnapshot));
        boolean changed = active == null
                || !semanticPatchService.build(
                        configType,
                        activeSnapshot,
                        draft).operations().isEmpty();
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
                        "fields", "toolbarConfig", "rowActionConfig", "allowedScenes")),
                withoutKeys(activeList, Set.of(
                        "fields", "toolbarConfig", "rowActionConfig", "allowedScenes")));
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
        appendValueCollectionChanges(
                changes,
                "allowedScenes",
                "列表场景",
                draftList.get("allowedScenes"),
                activeList.get("allowedScenes"));
        if (changes.isEmpty() && !changedSections.isEmpty()) {
            changes.add(UiConfigDiffItemDTO.builder()
                    .section("list")
                    .id("list")
                    .label("列表草稿")
                    .changeType("UPDATED")
                    .changedFields(changedSections)
                    .build());
        }
        return changes;
    }

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

    private void appendValueCollectionChanges(
            List<UiConfigDiffItemDTO> changes,
            String section,
            String label,
            Object draft,
            Object active) {
        Set<String> draftValues = textSet(draft);
        Set<String> activeValues = textSet(active);
        for (String value : draftValues) {
            if (!activeValues.remove(value)) {
                changes.add(itemChange(
                        section, value, value, "ADDED", List.of()));
            }
        }
        activeValues.forEach(value -> changes.add(itemChange(
                section, value, value, "REMOVED", List.of())));
    }

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

    private Map<String, Object> withoutKeys(
            Map<String, Object> source,
            Set<String> ignoredKeys) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        ignoredKeys.forEach(result::remove);
        return result;
    }

    private Map<String, Object> mapValue(Object source) {
        if (!(source instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

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

    private Set<String> textSet(Object source) {
        Set<String> result = new LinkedHashSet<>();
        if (!(source instanceof List<?> list)) {
            return result;
        }
        for (Object value : list) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                result.add(text);
            }
        }
        return result;
    }

    /**
     * 发布预检。普通发布返回草稿差异；热修复同时执行风险分级、流程影响分析和逐版本试算。
     */
    public UiConfigPublishPreviewDTO publishPreview(
            String configType,
            String configId,
            UiConfigPublishRequest request) {
        String releaseMode = releaseMode(request);
        if (HOTFIX.equals(releaseMode)) {
            configurationAccessService.requireHotfixAccess(false);
            return prepareHotfix(configType, configId, request).preview();
        }
        Map<String, Object> draft = buildDraftSnapshot(configType, configId);
        validateForPublish(configType, configId, draft);
        String draftHash = snapshotSupport.hash(
                snapshotSupport.canonical(draft));
        UiConfigRelease current = releaseMapper.findActive(configType, configId);
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
                        UiConfigSemanticPatchService.SAFE))
                .riskLevel(UiConfigSemanticPatchService.SAFE)
                .changed(diff.isChanged())
                .requiresOverride(false)
                .canPublish(diff.isChanged())
                .processVersionCount(0)
                .activeInstanceCount(0L)
                .skippedHistoricalInstanceCount(0L)
                .changedItems(diff.getChangedItems())
                .riskItems(List.of())
                .targets(List.of())
                .blockers(diff.isChanged()
                        ? List.of() : List.of("当前草稿与已发布版本一致"))
                .build();
    }

    /**
     * 兼容旧调用方的普通发布入口。
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
     * 按发布请求执行普通发布或兼容热修复。
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
            return publishHotfix(configType, configId, request);
        }
        return publishStandard(configType, configId, request);
    }

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
            recordSystemEntityUiAsset(
                    configType, configId, active, request);
            log.info(
                    "UI配置发布复用现有版本: configType={}, configId={}, releaseId={}, releaseVersion={}, releaseMode=STANDARD, reason=CONTENT_UNCHANGED",
                    LogValue.safe(configType),
                    LogValue.safe(configId),
                    LogValue.safe(active.getId()),
                    active.getVersion());
            return active;
        }
        List<UiConfigRelease> releases = releaseMapper.findReleases(configType, configId);
        int nextVersion = releases.isEmpty() ? 1 : releases.get(0).getVersion() + 1;
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
        release.setRiskLevel(UiConfigSemanticPatchService.SAFE);
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
                UiConfigSemanticPatchService.SAFE,
                request == null ? null : request.getDescription(),
                Map.of(
                        "version", release.getVersion(),
                        "contentHash", contentHash));
        recordSystemEntityUiAsset(
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

        UiConfigRelease active = preparation.active();
        List<UiConfigRelease> releases =
                releaseMapper.findReleases(configType, configId);
        int nextVersion =
                releases.isEmpty() ? 1 : releases.get(0).getVersion() + 1;
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
                        .blockers(List.copyOf(blockers))
                        .build();
        return new HotfixPreparation(
                active,
                draftDocument,
                patch,
                List.copyOf(preparedTargets),
                preview);
    }

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

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

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
        configurationAccessService.requireHotfixAccess(false);
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
            deactivate(configType, configId);
            UpdateWrapper<UiConfigRelease> activateBase =
                    new UpdateWrapper<>();
            activateBase.eq("id", base.getId())
                    .set("status", "ACTIVE");
            releaseMapper.update(null, activateBase);
            base.setStatus("ACTIVE");
            activateOnOwner(
                    configType,
                    configId,
                    base,
                    base.getContentHash());
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
        log.info(
                "UI配置热发布撤回完成: configType={}, configId={}, releaseId={}, baseReleaseId={}, targetCount={}, resultingStatus={}, operatorId={}",
                LogValue.safe(configType),
                LogValue.safe(configId),
                LogValue.safe(releaseId),
                LogValue.safe(release.getBaseReleaseId()),
                targets.size(),
                LogValue.safe(release.getStatus()),
                LogValue.safe(UserContext.getUserId()));
        return release;
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
        log.info(
                "开始激活UI配置历史版本: configType={}, configId={}, releaseId={}, operatorId={}",
                LogValue.safe(configType),
                LogValue.safe(configId),
                LogValue.safe(releaseId),
                LogValue.safe(UserContext.getUserId()));
        lockOwner(configType, configId);
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
        Map<String, Object> snapshot = codec.readObject(
                release.getSnapshotDocument(), "待激活UI发布快照");
        String actualHash = snapshotSupport.hash(
                snapshotSupport.canonical(snapshot));
        if (!StringUtils.hasText(release.getContentHash())
                || !Objects.equals(release.getContentHash(), actualHash)) {
            throw new IllegalArgumentException("发布快照完整性校验失败，内容可能已被篡改");
        }
        validateSnapshotForActivation(configType, configId, snapshot);
        deactivate(configType, configId);
        UpdateWrapper<UiConfigRelease> releaseUpdate = new UpdateWrapper<>();
        releaseUpdate.eq("id", releaseId).set("status", "ACTIVE");
        releaseMapper.update(null, releaseUpdate);
        release.setStatus("ACTIVE");
        activateOnOwner(configType, configId, release, release.getContentHash());
        recordSystemEntityUiAsset(
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

    private void recordSystemEntityUiAsset(
            String configType,
            String configId,
            UiConfigRelease release,
            UiConfigPublishRequest request) {
        EntityDefinition entity = ownerEntity(
                configType, configId);
        if (entity == null
                || entity.getStorageMode()
                != EntityDefinition.StorageMode.SYSTEM) {
            return;
        }
        ConfigMigrationPublishRequest migrationRequest =
                new ConfigMigrationPublishRequest();
        migrationRequest.setVersionDescription(
                request == null
                        ? release.getDescription()
                        : request.getDescription());
        migrationRequest.setMarkForExport(true);
        migrationAssetHandler.recordSystemEntityUi(
                entity.getId(),
                release.getId(),
                migrationRequest);
    }

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

    private void lockOwner(String configType, String configId) {
        requireType(configType);
        if (FORM.equals(configType)) {
            if (formMapper.selectByIdForUpdate(configId) == null) {
                throw new IllegalArgumentException("表单不存在");
            }
            return;
        }
        if (listConfigMapper.selectByIdForUpdate(configId) == null) {
            throw new IllegalArgumentException("列表配置不存在");
        }
    }

    /**
     * 流程发布读取表单 ACTIVE 版本前锁定同一配置行，
     * 与热修复发布形成共同的串行化边界。
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

    public record ResolvedUiEventSnapshot(
            Map<String, Object> snapshot,
            String releaseId,
            Integer releaseVersion,
            String effectiveReleaseId,
            boolean hotfixApplied) {
    }

    private EntityForm runtimeForm(Map<String, Object> snapshot) {
        EntityForm form = objectMapper.convertValue(
                snapshot.get("form"), EntityForm.class);
        form.setFields(objectMapper.convertValue(
                snapshot.getOrDefault("legacyFields", List.of()),
                new TypeReference<List<EntityFormField>>() {}));
        form.setNodes(objectMapper.convertValue(
                snapshot.getOrDefault("nodes", List.of()),
                new TypeReference<List<EntityFormNode>>() {}));
        return form;
    }

    /**
     * 解析列表运行时配置，优先取激活发布快照，回退到数据库草稿配置。
     *
     * @param listConfigId 列表配置ID
     * @return 运行时列表配置 DTO
     */
    public EntityListConfigDTO resolveRuntimeList(String listConfigId) {
        Map<String, Object> snapshot = activeSnapshot(LIST, listConfigId);
        return snapshot == null
                ? listConfigService.findById(listConfigId)
                : objectMapper.convertValue(
                        snapshot.get("list"), EntityListConfigDTO.class);
    }

    private Map<String, Object> buildDraftSnapshot(
            String configType,
            String configId) {
        requireType(configType);
        if (FORM.equals(configType)) {
            EntityForm form = formService.getById(configId);
            if (form == null) {
                throw new IllegalArgumentException("表单不存在");
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("schemaVersion", 1);
            snapshot.put("configType", FORM);
            snapshot.put(
                    "form",
                    snapshotSupport.stableValue(formMetadata(form)));
            snapshot.put("nodes", snapshotSupport.stableValue(
                    form.getNodes() == null ? List.of() : form.getNodes()));
            snapshot.put(
                    "legacyFields",
                    snapshotSupport.stableValue(
                            deriveRuntimeFields(form)));
            snapshot.put(
                    "eventBindings",
                    snapshotSupport.stableValue(
                            eventBindingSnapshotService.snapshot(
                                    FORM,
                                    configId,
                                    form.getEntityId())));
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
                                list.getEntityId())));
        return snapshot;
    }

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
        metadata.put("initConfig", form.getInitConfig());
        metadata.put(
                "dataSourceBindingsDocument",
                form.getDataSourceBindingsDocument());
        metadata.put("viewConfig", form.getViewConfig());
        return metadata;
    }

    private List<EntityFormField> deriveRuntimeFields(EntityForm form) {
        List<EntityFormField> existing =
                form.getFields() == null ? List.of() : form.getFields();
        Map<String, EntityFormField> byId = new HashMap<>();
        Map<String, EntityFormField> byCode = new HashMap<>();
        existing.forEach(field -> {
            byId.put(field.getId(), field);
            if (StringUtils.hasText(field.getFieldCode())) {
                byCode.put(field.getFieldCode(), field);
            }
        });
        List<EntityFormField> runtimeFields = new ArrayList<>();
        int sortOrder = 0;
        for (EntityFormNode node : form.getNodes() == null
                ? List.<EntityFormNode>of()
                : form.getNodes()) {
            if (!Set.of("FIELD", "SUB_FORM", "REPEATER")
                    .contains(node.getNodeType())) {
                continue;
            }
            Map<String, Object> props = StringUtils.hasText(node.getPropsDocument())
                    ? codec.readObject(node.getPropsDocument(), "发布表单节点属性")
                    : Map.of();
            String fieldCode = text(props.getOrDefault("fieldCode", node.getNodeKey()));
            EntityFormField field = new EntityFormField();
            EntityFormField previous = byId.get(node.getId());
            if (previous == null) {
                previous = byCode.get(fieldCode);
            }
            if (previous != null) {
                BeanUtils.copyProperties(previous, field);
            }
            field.setId(node.getId());
            field.setFormId(form.getId());
            if (props.containsKey("fieldId")) {
                field.setFieldId(text(props.get("fieldId")));
            }
            field.setFieldCode(fieldCode);
            if (props.containsKey("fieldName")) {
                field.setFieldName(text(props.get("fieldName")));
            }
            if (!StringUtils.hasText(field.getFieldName())) {
                field.setFieldName(text(props.get("label")));
            }
            field.setFieldLabel(text(props.getOrDefault("label", field.getFieldName())));
            if (props.containsKey("fieldType")) {
                field.setFieldType(text(props.get("fieldType")));
            }
            if (!StringUtils.hasText(field.getFieldType())) {
                field.setFieldType(
                        Set.of("SUB_FORM", "REPEATER")
                                .contains(node.getNodeType())
                                ? "SUB_FORM"
                                : node.getNodeType());
            }
            if (props.containsKey("componentType")) {
                field.setComponentType(text(props.get("componentType")));
            }
            if (!StringUtils.hasText(field.getComponentType())) {
                field.setComponentType(
                        Set.of("SUB_FORM", "REPEATER")
                                .contains(node.getNodeType())
                                ? "sub_form"
                                : node.getNodeType().toLowerCase());
            }
            if (props.containsKey("placeholder")) {
                field.setPlaceholder(text(props.get("placeholder")));
            }
            if (props.containsKey("defaultValue")) {
                field.setDefaultValue(text(props.get("defaultValue")));
            }
            if (props.containsKey("gridSpan")) {
                field.setGridSpan(integer(props.get("gridSpan"), 24));
            } else if (field.getGridSpan() == null) {
                field.setGridSpan(24);
            }
            if (props.containsKey("required")) {
                field.setIsRequired(booleanFlag(props.get("required")));
            }
            if (props.containsKey("readonly")) {
                field.setIsReadonly(booleanFlag(props.get("readonly")));
            }
            if (props.containsKey("hidden")) {
                field.setIsHidden(booleanFlag(props.get("hidden")));
            }
            field.setSortOrder(sortOrder++);
            if (props.containsKey("componentProps")) {
                Object componentProps = props.get("componentProps");
                field.setComponentProps(componentProps == null
                        ? null : codec.write(componentProps, "发布字段组件属性"));
            }
            Map<String, Object> rules = StringUtils.hasText(node.getRulesDocument())
                    ? codec.readObject(node.getRulesDocument(), "发布表单节点规则")
                    : Map.of();
            if (rules.containsKey("validation")) {
                Object validation = rules.get("validation");
                field.setValidationRules(validation == null
                        ? null : codec.write(validation, "发布字段校验规则"));
            }
            if (rules.containsKey("extension")) {
                Object extension = rules.get("extension");
                field.setExtensionConfig(extension == null
                        ? null : codec.write(extension, "发布字段扩展配置"));
            }
            if (StringUtils.hasText(node.getDataSourceBindingsDocument())) {
                field.setDataSourceBindings(codec.readObject(
                        node.getDataSourceBindingsDocument(),
                        "发布字段数据源绑定"));
            }
            runtimeFields.add(field);
        }
        return runtimeFields.isEmpty() ? existing : runtimeFields;
    }

    private void validateForPublish(
            String configType,
            String configId,
            Map<String, Object> snapshot) {
        if (FORM.equals(configType)) {
            formNodeService.validateTree(configId);
            formConfigurationValidator.validateForm(runtimeForm(snapshot));
            validateSubListReferences(snapshot);
            validateFormActions(snapshot);
            validateTemplateReferences(snapshot);
            validateExtensionReferences(snapshot);
            dataSourceValidator.validate(snapshot);
            return;
        }
        EntityListConfigDTO list = objectMapper.convertValue(
                snapshot.get("list"), EntityListConfigDTO.class);
        listConfigurationValidator.validate(list);
        validatePinnedListTargetForms(list);
        validateListTemplateReferences(list);
        dataSourceValidator.validate(snapshot);
    }

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
                pinned.add(button);
                continue;
            }
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
            button.put("targetFormReleaseVersion", release.getVersion());
            pinned.add(button);
        }
        return pinned;
    }

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

    private void validatePinnedListTargetForms(
            EntityListConfigDTO list,
            String position,
            List<Map<String, Object>> buttons) {
        for (Map<String, Object> button :
                buttons == null ? List.<Map<String, Object>>of() : buttons) {
            String targetFormId = text(button.get("targetFormId"));
            if (!StringUtils.hasText(targetFormId)) {
                continue;
            }
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
                    || !Objects.equals(targetFormId, release.getConfigId())
                    || !Objects.equals(releaseVersion, release.getVersion())) {
                throw new IllegalArgumentException(
                        "列表按钮目标表单发布版本不存在或不匹配: "
                                + targetFormId);
            }
        }
    }

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
                                : Set.of("view", "edit", "approve")
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
            validateTemplateReferences(snapshot);
            validateExtensionReferences(snapshot);
            dataSourceValidator.validate(snapshot);
            return;
        }
        EntityListConfigDTO list = objectMapper.convertValue(
                snapshot.get("list"), EntityListConfigDTO.class);
        listConfigurationValidator.validate(list);
        validatePinnedListTargetForms(list);
        validateListTemplateReferences(list);
        dataSourceValidator.validate(snapshot);
    }

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
            String fieldType = normalize(text(props.get("fieldType")));
            String componentType = text(props.get("componentType"));
            if (!"SUB_LIST".equals(fieldType)
                    && !"sub_list".equalsIgnoreCase(componentType)) {
                continue;
            }
            Map<String, Object> componentProps =
                    mapValue(props.get("componentProps"));
            Map<String, Object> config =
                    mapValue(componentProps.get("subListConfig"));
            String targetEntityId = text(config.get("targetEntityId"));
            String targetEntityCode = text(config.get("targetEntityCode"));
            String listKey = text(config.get("listKey"));
            String label = nodeLabel(node);
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
            if (list == null
                    || !StringUtils.hasText(list.getActiveReleaseId())
                    || list.getPublishedVersion() == null
                    || list.getPublishedVersion() <= 0) {
                throw new IllegalArgumentException(
                        "子列表引用的列表不存在或尚未发布: "
                                + targetEntityCode + "/" + listKey);
            }
            if (!supportsEmbeddedScene(list)) {
                throw new IllegalArgumentException(
                        "子列表引用的列表未开放 EMBEDDED 场景: "
                                + targetEntityCode + "/" + listKey);
            }
        }
    }

    private boolean supportsEmbeddedScene(EntityListConfig list) {
        UiConfigRelease active =
                releaseMapper.selectById(list.getActiveReleaseId());
        if (active == null
                || !LIST.equals(active.getConfigType())
                || !Objects.equals(list.getId(), active.getConfigId())) {
            return false;
        }
        Map<String, Object> snapshot = codec.readObject(
                active.getSnapshotDocument(),
                "子列表发布快照");
        Object configured =
                mapValue(snapshot.get("list")).get("allowedScenes");
        List<String> scenes = sceneValues(configured);
        if (scenes.isEmpty() && StringUtils.hasText(list.getAllowedScenes())) {
            scenes = sceneValues(codec.read(
                    list.getAllowedScenes(),
                    "子列表允许场景"));
        }
        return scenes.isEmpty()
                || scenes.stream().anyMatch(
                        "EMBEDDED"::equalsIgnoreCase);
    }

    private List<String> sceneValues(Object source) {
        if (!(source instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(this::text)
                .filter(StringUtils::hasText)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .toList();
    }

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
        Set<String> boundButtonKeys = mapList(
                snapshot.get("eventBindings")).stream()
                .filter(binding -> "BUTTON".equals(
                        normalize(text(binding.get("targetType")))))
                .filter(binding -> UiDataSourceUsages
                        .FORM_BUTTON_CLICK.equals(
                        normalize(text(binding.get("eventCode")))))
                .filter(binding -> !Boolean.FALSE.equals(
                        binding.get("enabled")))
                .map(binding -> text(binding.get("targetKey")))
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, Object> viewConfig =
                StringUtils.hasText(form.getViewConfig())
                        ? codec.readObject(
                                form.getViewConfig(),
                                "表单视图配置")
                        : Map.of();
        new EntityFormActionConfigPolicy().validate(
                viewConfig,
                definition != null
                        && definition.getStorageMode()
                        == EntityDefinition.StorageMode.SYSTEM,
                actionSlotKeys,
                true,
                boundButtonKeys,
                true);
    }

    private void validateTemplateReferences(Map<String, Object> snapshot) {
        List<EntityFormNode> nodes = snapshotNodes(snapshot);
        for (EntityFormNode node : nodes) {
            boolean hasTemplateId = StringUtils.hasText(node.getTemplateId());
            boolean hasTemplateVersion = node.getTemplateVersion() != null;
            if (!hasTemplateId && !hasTemplateVersion) {
                continue;
            }
            if (!hasTemplateId
                    || node.getTemplateVersion() == null
                    || node.getTemplateVersion() < 1) {
                throw new IllegalArgumentException(
                        "节点模板必须同时锁定 templateId 和 templateVersion: "
                                + nodeLabel(node));
            }
            UiComponentTemplate template =
                    templateMapper.selectById(node.getTemplateId());
            if (template == null
                    || Integer.valueOf(1).equals(template.getDeleted())
                    || !"ACTIVE".equalsIgnoreCase(template.getStatus())) {
                throw new IllegalArgumentException(
                        "节点引用的组件模板不存在或未启用: " + node.getTemplateId());
            }
            String templateType = normalize(template.getTemplateType());
            Set<String> compatibleTypes = TEMPLATE_NODE_TYPES.get(templateType);
            if (compatibleTypes == null
                    || !compatibleTypes.contains(normalize(node.getNodeType()))) {
                throw new IllegalArgumentException(
                        "组件模板类型 "
                                + templateType
                                + " 与节点类型 "
                                + node.getNodeType()
                                + " 不兼容: "
                                + nodeLabel(node));
            }
            UiComponentTemplateVersion version = templateVersionMapper.selectOne(
                    new LambdaQueryWrapper<UiComponentTemplateVersion>()
                            .eq(UiComponentTemplateVersion::getTemplateId,
                                    node.getTemplateId())
                            .eq(UiComponentTemplateVersion::getVersion,
                                    node.getTemplateVersion()));
            if (version == null) {
                throw new IllegalArgumentException(
                        "节点引用的组件模板版本不存在: "
                                + node.getTemplateId()
                                + "@"
                                + node.getTemplateVersion());
            }
            verifyTemplateVersionIntegrity(
                    version,
                    "节点 " + nodeLabel(node));
        }
    }

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

    private List<String> activePublishedFormReferences(String formId) {
        UiConfigRelease release = releaseMapper.findActive(FORM, formId);
        if (release == null || !StringUtils.hasText(release.getSnapshotDocument())) {
            throw new IllegalArgumentException("子表单引用的表单尚未发布: " + formId);
        }
        Map<String, Object> snapshot = codec.readObject(
                release.getSnapshotDocument(), "子表单发布快照");
        return referencedFormIds(snapshotNodes(snapshot));
    }

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

    private List<EntityFormNode> snapshotNodes(Map<String, Object> snapshot) {
        return objectMapper.convertValue(
                snapshot.getOrDefault("nodes", List.of()),
                new TypeReference<List<EntityFormNode>>() {});
    }

    private String nodeLabel(EntityFormNode node) {
        return StringUtils.hasText(node.getNodeKey())
                ? node.getNodeKey()
                : node.getId();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : null;
    }

    private Map<String, Object> auditDetail(
            UiConfigPublishPreviewDTO preview) {
        return objectMapper.convertValue(
                preview,
                new TypeReference<Map<String, Object>>() {});
    }

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
    }

    private void deactivate(String configType, String configId) {
        UpdateWrapper<UiConfigRelease> update = new UpdateWrapper<>();
        update.eq("config_type", configType)
                .eq("config_id", configId)
                .eq("status", "ACTIVE")
                .set("status", "INACTIVE");
        releaseMapper.update(null, update);
    }

    private void activateOnOwner(
            String configType,
            String configId,
            UiConfigRelease release,
            String contentHash) {
        if (FORM.equals(configType)) {
            UpdateWrapper<EntityForm> update = new UpdateWrapper<>();
            update.eq("id", configId)
                    .set("active_release_id", release.getId())
                    .set("draft_hash", contentHash)
                    .set("update_time", LocalDateTime.now());
            formMapper.update(null, update);
            return;
        }
        UpdateWrapper<EntityListConfig> update = new UpdateWrapper<>();
        update.eq("id", configId)
                .set("active_release_id", release.getId())
                .set("published_version", release.getVersion())
                .set("draft_hash", contentHash)
                .set("update_time", LocalDateTime.now());
        listConfigMapper.update(null, update);
    }

    private void requireType(String configType) {
        if (!FORM.equals(configType) && !LIST.equals(configType)) {
            throw new IllegalArgumentException("配置类型只能是 FORM 或 LIST");
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "未命名项";
    }

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

    private Integer booleanFlag(Object value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    private record PreparedHotfixTarget(
            UiHotfixProcessTarget target,
            UiConfigHotfixTarget previous,
            String restorablePreviousTargetId,
            String effectiveDocument,
            String effectiveHash) {
    }

    private record EffectiveHotfixSnapshot(
            String document,
            String hash) {
    }

    private record HotfixPreparation(
            UiConfigRelease active,
            String draftDocument,
            UiConfigSemanticPatchService.PatchAnalysis patch,
            List<PreparedHotfixTarget> targets,
            UiConfigPublishPreviewDTO preview) {
    }
}
