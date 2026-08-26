package com.workflow.entity.ui.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.contracts.ui.UiDataSourceUsages;
import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.PageResult;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.ui.api.request.UiDataSourceExecuteRequest;
import com.workflow.entity.ui.api.request.UiViewCompositionResolveRequest;
import com.workflow.entity.ui.api.response.UiViewCompositionResolveResponse;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 已发布关联内容的可信运行时解析服务。
 *
 * <p>本服务只负责把宿主记录解析为目标 FORM 的唯一记录，或目标 LIST 的
 * 服务端固定条件。宿主与目标发布、实体关系、字段以及接口服务绑定均从服务端
 * 发布快照读取，客户端提交的整行数据、实体编码和筛选条件不会进入解析链。</p>
 */
@Service
@RequiredArgsConstructor
public class UiViewCompositionRuntimeService {

    private static final String FORM = "FORM";
    private static final String LIST = "LIST";
    private static final Set<String> OWNER_TYPES = Set.of(FORM, LIST);
    private static final Set<String> STANDARD_RELATIONS = Set.of(
            "SAME_RECORD",
            "REFERENCE_FIELD",
            "REVERSE_REFERENCE",
            "FIELD_MATCH",
            "ENTITY_RELATION");
    private static final Set<String> FAILURE_POLICIES = Set.of(
            "ERROR", "PLACEHOLDER", "HIDE");
    /** 与动态实体和系统实体列表运行时共同支持的安全查询操作符。 */
    private static final Set<String> FILTER_OPERATORS = Set.of(
            "EQ", "NE", "LIKE", "GT", "LT", "IN", "BETWEEN");
    private static final int MAX_INTERFACE_FILTERS = 64;
    private static final int MAX_INTERFACE_IN_VALUES = 200;

    private final UiConfigReleaseMapper releaseMapper;
    private final UiConfigReleaseService releaseService;
    private final EntityFormMapper formMapper;
    private final EntityListConfigMapper listMapper;
    private final EntityDefinitionMapper entityMapper;
    private final EntityPublishedSnapshotService entitySnapshotService;
    private final EntityDataDynamicService dynamicDataService;
    private final SystemEntityReadService systemEntityReadService;
    private final EntityActionCapabilityService capabilityService;
    private final UiDataSourceService dataSourceService;
    private final UiReleaseResolutionTokenService releaseTokenService;
    private final UiViewCompositionTokenService compositionTokenService;
    private final JsonDocumentCodec codec;
    private final ObjectMapper objectMapper;

    /**
     * 解析指定关联内容。
     *
     * @param request 仅包含宿主发布身份、compositionKey 和 recordId/签名行令牌
     * @return 钉定目标发布、唯一目标记录或可信列表固定条件
     */
    public UiViewCompositionResolveResponse resolve(
            UiViewCompositionResolveRequest request) {
        ValidatedRequest validated = validateRequest(request);
        OwnerRelease owner = resolveOwnerRelease(validated);
        Map<String, Object> composition = requireComposition(
                owner.snapshot(), validated.compositionKey());
        Map<String, Object> config = map(composition.get("config"));
        // 停用项仍可能保留在历史发布快照中用于审计；运行时必须再次拒绝，
        // 不能依赖前端“不渲染”来形成访问边界。
        if (Boolean.FALSE.equals(config.get("enabled"))) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_DISABLED",
                    "关联内容已停用，不能在运行时解析");
        }
        Map<String, Object> targetConfig = map(config.get("target"));
        Map<String, Object> relationConfig = map(config.get("relation"));
        Map<String, Object> specialConfig = map(
                config.get("specialHandling"));
        validatePinnedCustomComponent(specialConfig);

        EntityDefinition sourceEntity = requireOwnerEntity(
                owner.snapshot(), validated.ownerType());
        requireOwnerRuntimeAccess(
                validated.ownerType(), owner.snapshot(), sourceEntity);
        EntityDataDTO sourceRecord = readAccessibleRecord(
                sourceEntity, validated.recordId());

        TargetAsset target = requireTargetAsset(targetConfig);
        requireTargetRuntimeAccess(target);
        PinnedSchemas schemas = requirePinnedEntitySchemas(
                config, sourceEntity, target.entity());
        EntityPublishedSnapshot sourceSchema = schemas.source();
        EntityPublishedSnapshot targetSchema = schemas.target();

        Resolution resolution = usesInterfaceService(
                relationConfig, specialConfig)
                ? resolveWithInterfaceService(
                        validated,
                        owner,
                        composition,
                        specialConfig,
                        sourceEntity,
                        sourceRecord,
                        target,
                        targetSchema)
                : resolveStandard(
                        relationConfig,
                        sourceSchema,
                        targetSchema,
                        sourceRecord,
                        target);

        String targetRecordId = null;
        Map<String, Object> fixedFilters = resolution.fixedFilters();
        boolean matchNone = resolution.matchNone();
        if (FORM.equals(target.contentType()) && !matchNone) {
            targetRecordId = resolveSingleTargetRecord(
                    target.entity(),
                    resolution.directRecordId(),
                    fixedFilters);
            matchNone = !StringUtils.hasText(targetRecordId);
            fixedFilters = Map.of();
        }

        // 没有目标时不签发下一跳权限；响应仍以 matchNone 明确告知前端展示
        // 空状态，避免空匹配令牌被重用于目标历史版本中的任意记录。
        String traversalContextToken = matchNone
                ? null : compositionTokenService.advanceTraversal(
                        validated.previousTraversalContextToken(),
                        validated.ownerType(),
                        validated.ownerId(),
                        validated.releaseId(),
                        validated.releaseVersion(),
                        validated.compositionKey(),
                        validated.recordId(),
                        target.contentType(),
                        target.contentId(),
                        target.release().getId(),
                        target.release().getVersion(),
                        FORM.equals(target.contentType())
                                ? targetRecordId : null);

        String rowToken = compositionTokenService.issueSourceRow(
                validated.ownerType(),
                validated.ownerId(),
                validated.releaseId(),
                validated.releaseVersion(),
                validated.compositionKey(),
                sourceEntity.getEntityCode(),
                validated.recordId());
        String targetReleaseToken = FORM.equals(target.contentType())
                ? releaseTokenService.issue(
                        UiRuntimeResolutionContext.standalone(),
                        target.contentId(),
                        target.release().getId(),
                        target.release().getVersion(),
                        0)
                : null;
        String listContextToken = LIST.equals(target.contentType())
                ? compositionTokenService.issueTargetList(
                        validated.ownerType(),
                        validated.ownerId(),
                        validated.releaseId(),
                        validated.releaseVersion(),
                        validated.compositionKey(),
                        sourceEntity.getEntityCode(),
                        validated.recordId(),
                        target.entity().getEntityCode(),
                        target.contentId(),
                        target.release().getId(),
                        target.release().getVersion(),
                        fixedFilters,
                        matchNone)
                : null;

        return UiViewCompositionResolveResponse.builder()
                .ownerType(validated.ownerType())
                .ownerId(validated.ownerId())
                .releaseId(validated.releaseId())
                .releaseVersion(validated.releaseVersion())
                .compositionKey(validated.compositionKey())
                .sourceEntityCode(sourceEntity.getEntityCode())
                .sourceRecordId(validated.recordId())
                .targetEntityId(target.entity().getId())
                .targetEntityCode(target.entity().getEntityCode())
                .targetContentType(target.contentType())
                .targetContentId(target.contentId())
                .targetContentKey(target.contentKey())
                .targetReleaseId(target.release().getId())
                .targetReleaseVersion(target.release().getVersion())
                .targetContentHash(target.release().getContentHash())
                .targetRecordId(targetRecordId)
                .fixedFilters(unmodifiable(fixedFilters))
                .matchNone(matchNone)
                .presentation(unmodifiable(map(config.get("presentation"))))
                .actions(stringList(config.get("actions")))
                .failurePolicy(failurePolicy(specialConfig))
                .rowContextToken(rowToken)
                .actionContextToken(rowToken)
                .targetReleaseResolutionToken(targetReleaseToken)
                .listContextToken(listContextToken)
                .traversalContextToken(traversalContextToken)
                .build();
    }

    private ValidatedRequest validateRequest(
            UiViewCompositionResolveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("关联内容解析请求不能为空");
        }
        String ownerType = normalize(request.getOwnerType());
        if (!OWNER_TYPES.contains(ownerType)
                || !StringUtils.hasText(request.getOwnerId())
                || !StringUtils.hasText(request.getReleaseId())
                || request.getReleaseVersion() == null
                || request.getReleaseVersion() < 1
                || !StringUtils.hasText(request.getCompositionKey())) {
            throw new IllegalArgumentException(
                    "关联内容解析缺少有效的宿主类型、宿主ID、发布版本或关联内容编码");
        }
        UiViewCompositionTokenService.Claims claims = null;
        String recordId = trim(request.getRecordId());
        if (StringUtils.hasText(request.getRowContextToken())) {
            claims = compositionTokenService.verifySourceRow(
                    request.getRowContextToken());
            requireTokenContext(
                    claims,
                    ownerType,
                    request.getOwnerId(),
                    request.getReleaseId(),
                    request.getReleaseVersion(),
                    request.getCompositionKey());
            if (StringUtils.hasText(recordId)
                    && !Objects.equals(
                    recordId, claims.sourceRecordId())) {
                throw new BusinessForbiddenException(
                        "VIEW_COMPOSITION_ROW_CONTEXT_MISMATCH",
                        "来源记录ID与签名行上下文不一致");
            }
            recordId = claims.sourceRecordId();
        }
        if (!StringUtils.hasText(recordId)) {
            throw new IllegalArgumentException(
                    "关联内容解析必须提供来源记录ID或签名行上下文");
        }
        boolean traversalAuthorized = StringUtils.hasText(
                request.getTraversalContextToken());
        if (traversalAuthorized) {
            // 父层令牌只允许进入它已经固定的目标资产、发布版本和（FORM）
            // 唯一记录；实体与数据权限仍在后续服务链重新校验。
            compositionTokenService.verifyTraversalEntry(
                    request.getTraversalContextToken(),
                    ownerType,
                    request.getOwnerId().trim(),
                    request.getReleaseId().trim(),
                    request.getReleaseVersion(),
                    request.getCompositionKey().trim(),
                    recordId);
        }
        return new ValidatedRequest(
                ownerType,
                request.getOwnerId().trim(),
                request.getReleaseId().trim(),
                request.getReleaseVersion(),
                request.getCompositionKey().trim(),
                recordId,
                claims != null || traversalAuthorized,
                trim(request.getReleaseResolutionToken()),
                trim(request.getTraversalContextToken()));
    }

    private void requireTokenContext(
            UiViewCompositionTokenService.Claims claims,
            String ownerType,
            String ownerId,
            String releaseId,
            Integer releaseVersion,
            String compositionKey) {
        if (claims == null
                || !Objects.equals(ownerType, claims.ownerType())
                || !Objects.equals(ownerId, claims.ownerId())
                || !Objects.equals(releaseId, claims.releaseId())
                || !Objects.equals(releaseVersion, claims.releaseVersion())
                || !Objects.equals(compositionKey, claims.compositionKey())) {
            throw new BusinessForbiddenException(
                    "VIEW_COMPOSITION_ROW_CONTEXT_MISMATCH",
                    "签名行上下文与关联内容请求不一致");
        }
    }

    private OwnerRelease resolveOwnerRelease(ValidatedRequest request) {
        if (FORM.equals(request.ownerType())
                && StringUtils.hasText(
                        request.releaseResolutionToken())) {
            // 列表按钮等已发布入口会固定一个表单发布版本。必须复用同一份
            // 服务端签名上下文解析历史快照，不能因表单后来重新发布而退回
            // 当前 ACTIVE，也不能仅凭客户端提交的 releaseId 放开历史版本。
            UiConfigReleaseService.ResolvedUiEventSnapshot resolved =
                    releaseService.resolveRuntimeEventSnapshot(
                            request.ownerId(),
                            request.releaseId(),
                            request.releaseVersion(),
                            request.releaseResolutionToken());
            return new OwnerRelease(
                    requireReleaseRecord(request),
                    resolved.snapshot());
        }
        if (!request.tokenAuthorized() && FORM.equals(request.ownerType())) {
            UiConfigReleaseService.ResolvedUiEventSnapshot resolved =
                    releaseService.resolveRuntimeEventSnapshot(
                            request.ownerId(),
                            request.releaseId(),
                            request.releaseVersion(),
                            null);
            return new OwnerRelease(
                    requireReleaseRecord(request),
                    resolved.snapshot());
        }
        UiConfigRelease release = requireReleaseRecord(request);
        if (!request.tokenAuthorized()) {
            String activeReleaseId = FORM.equals(request.ownerType())
                    ? activeFormRelease(request.ownerId())
                    : activeListRelease(request.ownerId());
            if (!Objects.equals(activeReleaseId, release.getId())
                    || !"ACTIVE".equalsIgnoreCase(release.getStatus())) {
                throw new BusinessConflictException(
                        "VIEW_COMPOSITION_RELEASE_CONFLICT",
                        "关联内容宿主版本已变化，请刷新页面后重试");
            }
        }
        return new OwnerRelease(
                release,
                releaseService.verifiedReleaseSnapshot(release));
    }

    private UiConfigRelease requireReleaseRecord(
            ValidatedRequest request) {
        UiConfigRelease release = releaseMapper.selectById(
                request.releaseId());
        if (release == null
                || !Objects.equals(
                request.ownerType(), release.getConfigType())
                || !Objects.equals(
                request.ownerId(), release.getConfigId())
                || !Objects.equals(
                request.releaseVersion(), release.getVersion())) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_RELEASE_CONFLICT",
                    "关联内容宿主发布版本不存在或不一致");
        }
        return release;
    }

    private String activeFormRelease(String ownerId) {
        EntityForm form = formMapper.selectById(ownerId);
        return form == null ? null : form.getActiveReleaseId();
    }

    private String activeListRelease(String ownerId) {
        EntityListConfig list = listMapper.selectById(ownerId);
        return list == null ? null : list.getActiveReleaseId();
    }

    private Map<String, Object> requireComposition(
            Map<String, Object> snapshot,
            String compositionKey) {
        List<Map<String, Object>> compositions = mapList(
                snapshot.get("viewCompositions"));
        return compositions.stream()
                .filter(item -> Objects.equals(
                        compositionKey,
                        trim(text(item.get("compositionKey")))))
                .findFirst()
                .orElseThrow(() -> new BusinessConflictException(
                        "VIEW_COMPOSITION_NOT_PUBLISHED",
                        "关联内容不存在于指定发布版本: "
                                + compositionKey));
    }

    private EntityDefinition requireOwnerEntity(
            Map<String, Object> snapshot,
            String ownerType) {
        Map<String, Object> owner = map(
                snapshot.get(ownerType.toLowerCase(Locale.ROOT)));
        String entityId = text(owner.get("entityId"));
        EntityDefinition entity = StringUtils.hasText(entityId)
                ? entityMapper.selectById(entityId)
                : null;
        if (entity == null) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_SOURCE_ENTITY_NOT_FOUND",
                    "关联内容宿主所属实体不存在");
        }
        return entity;
    }

    /**
     * 按宿主发布快照中的 historyId 读取两端实体定义。
     *
     * <p>旧关联内容快照若没有钉定信息则明确失败，不会回退
     * {@code getLatestByEntityCode}；同时比对当前实体身份，以防实体
     * 删除后 ID 复用或编码更改导致权限语义偏移。</p>
     */
    private PinnedSchemas requirePinnedEntitySchemas(
            Map<String, Object> config,
            EntityDefinition sourceEntity,
            EntityDefinition targetEntity) {
        Map<String, Object> snapshots = map(
                config.get("entitySnapshots"));
        if (snapshots.isEmpty()) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_ENTITY_SNAPSHOT_REQUIRED",
                    "关联内容的实体快照未钉定，请重新发布宿主配置");
        }
        EntityPublishedSnapshot source = requirePinnedEntitySchema(
                map(snapshots.get("source")),
                sourceEntity,
                "来源实体");
        EntityPublishedSnapshot target = requirePinnedEntitySchema(
                map(snapshots.get("target")),
                targetEntity,
                "目标实体");
        return new PinnedSchemas(source, target);
    }

    private EntityPublishedSnapshot requirePinnedEntitySchema(
            Map<String, Object> pin,
            EntityDefinition currentEntity,
            String label) {
        String historyId = trim(text(pin.get("historyId")));
        String entityId = trim(text(pin.get("entityId")));
        String entityCode = trim(text(pin.get("entityCode")));
        Integer version = integer(pin.get("version"));
        String expectedHash = trim(text(pin.get("schemaHash")));
        if (!StringUtils.hasText(historyId)
                || !StringUtils.hasText(entityId)
                || !StringUtils.hasText(entityCode)
                || version == null
                || version < 1
                || !StringUtils.hasText(expectedHash)) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_ENTITY_SNAPSHOT_REQUIRED",
                    label + "快照身份不完整，请重新发布宿主配置");
        }
        EntityPublishedSnapshotService.PinnedEntitySnapshot pinned;
        try {
            pinned = entitySnapshotService.getPinnedByHistoryId(
                    historyId);
        } catch (RuntimeException exception) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_ENTITY_SNAPSHOT_MISSING",
                    label + "的固定发布历史已缺失，不能继续解析");
        }
        EntityPublishedSnapshot snapshot = pinned == null
                ? null : pinned.snapshot();
        String actualHash = pinned == null
                ? null : pinned.schemaHash();
        if (snapshot == null
                || !Objects.equals(entityId, snapshot.getEntityId())
                || !Objects.equals(entityCode, snapshot.getEntityCode())
                || !Objects.equals(version, snapshot.getVersion())
                || !Objects.equals(
                normalizeHash(expectedHash), normalizeHash(actualHash))
                || currentEntity == null
                || !Objects.equals(entityId, currentEntity.getId())
                || !Objects.equals(
                entityCode, currentEntity.getEntityCode())) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_ENTITY_SNAPSHOT_CONFLICT",
                    label + "的固定发布历史身份或完整性校验失败");
        }
        return snapshot;
    }

    private void requireOwnerRuntimeAccess(
            String ownerType,
            Map<String, Object> snapshot,
            EntityDefinition entity) {
        if (entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
            systemEntityReadService.requirePermissions(
                    entity.getEntityCode());
        } else {
            capabilityService.requireStandardPermission(
                    entity.getEntityCode(),
                    LIST.equals(ownerType)
                            ? EntityPermissionAction.LIST
                            : EntityPermissionAction.VIEW);
        }
        if (LIST.equals(ownerType)) {
            Map<String, Object> list = map(snapshot.get("list"));
            requirePermission(
                    firstText(
                            list.get("accessPermissionCode"),
                            "entity:"
                                    + entity.getEntityCode()
                                            .toLowerCase(Locale.ROOT)
                                    + ":list"),
                    "没有权限访问关联内容宿主列表");
        }
    }

    private EntityDataDTO readAccessibleRecord(
            EntityDefinition entity,
            String recordId) {
        if (entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
            return systemEntityReadService.findById(
                    entity.getEntityCode(), recordId);
        }
        return dynamicDataService.findAccessibleById(
                entity.getEntityCode(), recordId, null);
    }

    private TargetAsset requireTargetAsset(
            Map<String, Object> targetConfig) {
        String entityId = trim(text(targetConfig.get("entityId")));
        String contentType = normalize(text(
                targetConfig.get("contentType")));
        String contentId = trim(text(targetConfig.get("contentId")));
        String releaseId = trim(text(targetConfig.get("releaseId")));
        Integer releaseVersion = integer(
                targetConfig.get("releaseVersion"));
        String contentHash = trim(text(
                targetConfig.get("contentHash")));
        if (!StringUtils.hasText(entityId)
                || !OWNER_TYPES.contains(contentType)
                || !StringUtils.hasText(contentId)
                || !StringUtils.hasText(releaseId)
                || releaseVersion == null
                || releaseVersion < 1
                || !StringUtils.hasText(contentHash)) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_TARGET_RELEASE_REQUIRED",
                    "关联内容发布快照缺少固定的目标表单或列表版本");
        }
        EntityDefinition entity = entityMapper.selectById(entityId);
        if (entity == null) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_TARGET_ENTITY_NOT_FOUND",
                    "关联内容目标实体不存在");
        }
        String contentKey;
        if (FORM.equals(contentType)) {
            EntityForm form = formMapper.selectById(contentId);
            if (form == null
                    || !Objects.equals(entityId, form.getEntityId())
                    || !Integer.valueOf(1).equals(form.getStatus())) {
                throw new BusinessConflictException(
                        "VIEW_COMPOSITION_TARGET_FORM_INVALID",
                        "关联内容目标表单不存在、未启用或不属于目标实体");
            }
            contentKey = form.getFormKey();
        } else {
            EntityListConfig list = listMapper.selectById(contentId);
            if (list == null
                    || !Objects.equals(entityId, list.getEntityId())) {
                throw new BusinessConflictException(
                        "VIEW_COMPOSITION_TARGET_LIST_INVALID",
                        "关联内容目标列表不存在或不属于目标实体");
            }
            contentKey = list.getListKey();
        }
        UiConfigRelease release = releaseMapper.selectById(releaseId);
        if (release == null
                || !Objects.equals(contentType, release.getConfigType())
                || !Objects.equals(contentId, release.getConfigId())
                || !Objects.equals(releaseVersion, release.getVersion())
                || !Objects.equals(contentHash, release.getContentHash())) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_TARGET_RELEASE_CONFLICT",
                    "关联内容固定的目标发布版本不存在或不一致");
        }
        Map<String, Object> targetSnapshot =
                releaseService.verifiedReleaseSnapshot(release);
        Map<String, Object> targetOwner = map(
                targetSnapshot.get(contentType.toLowerCase(Locale.ROOT)));
        if (!Objects.equals(contentId, text(targetOwner.get("id")))
                || !Objects.equals(entityId, text(
                targetOwner.get("entityId")))) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_TARGET_RELEASE_CONFLICT",
                    "关联内容目标发布快照与目标资源归属不一致");
        }
        return new TargetAsset(
                entity,
                contentType,
                contentId,
                contentKey,
                release,
                targetSnapshot,
                targetOwner);
    }

    private void requireTargetRuntimeAccess(TargetAsset target) {
        if (target.entity().getStorageMode()
                == EntityDefinition.StorageMode.SYSTEM) {
            systemEntityReadService.requirePermissions(
                    target.entity().getEntityCode());
        } else {
            capabilityService.requireStandardPermission(
                    target.entity().getEntityCode(),
                    FORM.equals(target.contentType())
                            ? EntityPermissionAction.VIEW
                            : EntityPermissionAction.LIST);
        }
        if (LIST.equals(target.contentType())) {
            String permission = firstText(
                    target.ownerSnapshot().get("accessPermissionCode"),
                    "entity:"
                            + target.entity().getEntityCode()
                                    .toLowerCase(Locale.ROOT)
                            + ":list");
            requirePermission(
                    permission,
                    "没有权限访问关联内容目标列表");
        }
    }

    private Resolution resolveStandard(
            Map<String, Object> relation,
            EntityPublishedSnapshot sourceSchema,
            EntityPublishedSnapshot targetSchema,
            EntityDataDTO sourceRecord,
            TargetAsset target) {
        String type = normalize(text(relation.get("type")));
        if (!STANDARD_RELATIONS.contains(type)) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_RELATION_UNSUPPORTED",
                    "关联内容发布快照包含不支持的数据关联方式: " + type);
        }
        Map<String, Object> filters = new LinkedHashMap<>();
        String directRecordId = null;
        boolean matchNone = false;
        switch (type) {
            case "SAME_RECORD" -> {
                if (!Objects.equals(
                        sourceSchema.getEntityId(),
                        targetSchema.getEntityId())) {
                    throw invalidRelation(
                            "使用当前记录要求来源实体与目标实体相同");
                }
                directRecordId = sourceRecord.getId();
                putEqualFilter(filters, "id", sourceRecord.getId());
            }
            case "REFERENCE_FIELD" -> {
                String sourceFieldCode = requiredFieldCode(
                        relation, "sourceField", "引用字段不能为空");
                EntityField sourceField = requireField(
                        sourceSchema, sourceFieldCode, "来源");
                if (sourceField.getFieldType()
                        != EntityField.FieldType.REFERENCE
                        || !Objects.equals(
                        sourceField.getRefEntityId(),
                        targetSchema.getEntityId())) {
                    throw invalidRelation(
                            "来源字段不是指向目标实体的单值引用字段");
                }
                Object value = scalarValue(
                        recordValue(sourceRecord, sourceFieldCode),
                        sourceFieldCode);
                if (value == null) {
                    matchNone = true;
                } else {
                    directRecordId = String.valueOf(value);
                    putEqualFilter(filters, "id", value);
                }
            }
            case "REVERSE_REFERENCE" -> {
                String targetFieldCode = requiredFieldCode(
                        relation, "targetField", "目标引用字段不能为空");
                EntityField targetField = requireField(
                        targetSchema, targetFieldCode, "目标");
                if (targetField.getFieldType()
                        != EntityField.FieldType.REFERENCE
                        || !Objects.equals(
                        targetField.getRefEntityId(),
                        sourceSchema.getEntityId())) {
                    throw invalidRelation(
                            "目标字段不是指向来源实体的单值引用字段");
                }
                putEqualFilter(
                        filters, targetFieldCode, sourceRecord.getId());
            }
            case "FIELD_MATCH" -> {
                List<Map<String, Object>> mappings = relationMappings(
                        relation);
                for (Map<String, Object> mapping : mappings) {
                    String sourceFieldCode = requiredFieldCode(
                            mapping, "sourceField", "字段匹配缺少来源字段");
                    String targetFieldCode = requiredFieldCode(
                            mapping, "targetField", "字段匹配缺少目标字段");
                    requireFieldOrId(sourceSchema, sourceFieldCode, "来源");
                    requireFieldOrId(targetSchema, targetFieldCode, "目标");
                    Object value = scalarValue(
                            recordValue(sourceRecord, sourceFieldCode),
                            sourceFieldCode);
                    if (value == null) {
                        matchNone = true;
                        filters.clear();
                        break;
                    }
                    putEqualFilter(filters, targetFieldCode, value);
                }
            }
            case "ENTITY_RELATION" -> {
                String relationCode = trim(text(
                        relation.get("relationCode")));
                EntityRelation publishedRelation =
                        sourceSchema.getRelations() == null
                                ? null
                                : sourceSchema.getRelations().stream()
                                .filter(item -> Boolean.TRUE.equals(
                                        item.getEnabled()))
                                .filter(item -> Objects.equals(
                                        relationCode,
                                        item.getRelationCode()))
                                .findFirst()
                                .orElse(null);
                if (publishedRelation == null
                        || !Objects.equals(
                        targetSchema.getEntityId(),
                        publishedRelation.getChildEntityId())
                        || !StringUtils.hasText(
                        publishedRelation.getChildRefFieldCode())) {
                    throw invalidRelation(
                            "实体关系不存在于来源实体发布快照或目标实体不匹配");
                }
                requireField(
                        targetSchema,
                        publishedRelation.getChildRefFieldCode(),
                        "目标");
                putEqualFilter(
                        filters,
                        publishedRelation.getChildRefFieldCode(),
                        sourceRecord.getId());
            }
            default -> throw invalidRelation("不支持的数据关联方式");
        }
        return new Resolution(
                directRecordId,
                unmodifiable(filters),
                matchNone);
    }

    private boolean usesInterfaceService(
            Map<String, Object> relation,
            Map<String, Object> special) {
        if ("INTERFACE_SERVICE".equals(normalize(
                text(relation.get("type"))))) {
            return true;
        }
        // 动作接口和数据解析接口共用 special.mode，但只有显式配置了后者时
        // 才能参与 resolve；动作绑定由动作端点按 actionKey 独立执行。
        Map<String, Object> service = map(special.get("interfaceService"));
        return StringUtils.hasText(text(service.get("serviceId")))
                && StringUtils.hasText(text(service.get("operationCode")));
    }

    /**
     * 运行时在把自定义组件配置交给前端之前重新校验固定定义。
     * 宿主发布哈希能防整体文档篡改，这里的独立哈希则确保组件标识
     * 与定义内容没有在解析链路中被重组或替换。
     */
    private void validatePinnedCustomComponent(
            Map<String, Object> special) {
        if (!(special.get("customComponent")
                instanceof Map<?, ?> raw)) {
            return;
        }
        Map<String, Object> component = map(raw);
        String snapshot = trim(text(
                component.get("definitionSnapshot")));
        String expectedHash = normalize(text(
                component.get("definitionHash"))).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(snapshot)
                || !expectedHash.matches("[a-f0-9]{64}")) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_COMPONENT_SNAPSHOT_REQUIRED",
                    "已发布自定义组件缺少不可变定义或哈希");
        }
        String canonical = codec.canonicalize(
                snapshot,
                "已发布自定义组件定义");
        if (!MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                sha256(canonical).getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_COMPONENT_SNAPSHOT_TAMPERED",
                    "已发布自定义组件定义完整性校验失败");
        }
        Map<String, Object> definition = codec.readObject(
                canonical,
                "已发布自定义组件定义");
        Integer schemaVersion = integer(definition.get("schemaVersion"));
        if (schemaVersion == null
                || schemaVersion != 1 && schemaVersion != 2
                || !Objects.equals(
                trim(text(component.get("name"))),
                trim(text(definition.get("extensionKey"))))
                || !Objects.equals(
                integer(component.get("version")),
                integer(definition.get("version")))
                || !Objects.equals(
                integer(component.get("snapshotVersion")),
                integer(definition.get("snapshotVersion")))
                || !Objects.equals(
                normalize(text(component.get("extensionType"))),
                normalize(text(definition.get("extensionType"))))) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_COMPONENT_SNAPSHOT_CONFLICT",
                    "已发布自定义组件定义与绑定标识不一致");
        }
        // schema v2 把前端可执行制品摘要纳入发布身份；摘要缺失或漂移时
        // 不能只依赖仍然相同的 name/version 继续加载当前实现。
        if (schemaVersion >= 2) {
            String artifactDigest = requireComponentArtifactDigest(
                    component.get("artifactDigest"));
            String pinnedDigest = requireComponentArtifactDigest(
                    definition.get("artifactDigest"));
            if (!artifactDigest.equals(pinnedDigest)) {
                throw new BusinessConflictException(
                        "VIEW_COMPOSITION_COMPONENT_ARTIFACT_CONFLICT",
                        "已发布自定义组件制品摘要与固定定义不一致");
            }
        }
    }

    /** 运行时只接受发布阶段固定的 64 位小写十六进制制品摘要。 */
    private String requireComponentArtifactDigest(Object value) {
        String digest = normalizeHash(text(value));
        if (!digest.matches("[a-f0-9]{64}")) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_COMPONENT_ARTIFACT_INVALID",
                    "已发布自定义组件制品摘要无效");
        }
        return digest;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "运行环境不支持 SHA-256",
                    exception);
        }
    }

    private Resolution resolveWithInterfaceService(
            ValidatedRequest request,
            OwnerRelease owner,
            Map<String, Object> composition,
            Map<String, Object> special,
            EntityDefinition sourceEntity,
            EntityDataDTO sourceRecord,
            TargetAsset target,
            EntityPublishedSnapshot targetSchema) {
        Map<String, Object> serviceBinding = map(
                special.get("interfaceService"));
        String serviceId = trim(text(serviceBinding.get("serviceId")));
        String operationCode = trim(text(
                serviceBinding.get("operationCode")));
        if (!StringUtils.hasText(serviceId)
                || !StringUtils.hasText(operationCode)) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_INTERFACE_BINDING_REQUIRED",
                    "关联内容特殊处理未绑定接口服务和操作");
        }
        requirePublishedReadOperation(
                request.ownerType(), serviceBinding,
                serviceId, operationCode);
        Map<String, Object> input = mapInterfaceInput(
                serviceBinding, sourceRecord);
        UiDataSourceExecuteRequest executeRequest =
                new UiDataSourceExecuteRequest();
        executeRequest.setUsage(
                UiDataSourceUsages.RELATED_CONTENT_RESOLVE);
        executeRequest.setOperationCode(operationCode);
        executeRequest.setConfigType(request.ownerType());
        executeRequest.setConfigId(request.ownerId());
        executeRequest.setReleaseId(request.releaseId());
        executeRequest.setReleaseVersion(request.releaseVersion());
        executeRequest.setEntityCode(sourceEntity.getEntityCode());
        executeRequest.setTargetType("COMPOSITION");
        executeRequest.setTargetKey(request.compositionKey());
        executeRequest.setInput(input);
        if (request.tokenAuthorized()) {
            executeRequest.setServerPinnedRelease(true);
            executeRequest.setServerIdempotencyKey(
                    "view-composition:"
                            + owner.release().getId()
                            + ":" + composition.get("id")
                            + ":" + sourceRecord.getId());
        }
        Object raw = dataSourceService.executePinnedOperation(
                text(serviceBinding.get("executableSnapshot")),
                text(serviceBinding.get("definitionHash")),
                executeRequest);
        Map<String, Object> output = mapInterfaceOutput(
                serviceBinding, raw);
        boolean matchNone = Boolean.TRUE.equals(
                output.get("matchNone"));
        String directRecordId = firstText(
                output.get("targetRecordId"),
                output.get("recordId"));
        Map<String, Object> filters = map(
                output.get("fixedFilters"));
        if (filters.isEmpty()) {
            filters = map(output.get("filters"));
        }
        filters = normalizeInterfaceFilters(filters);
        validateTargetFilters(targetSchema, filters);
        if (FORM.equals(target.contentType())) {
            if (!StringUtils.hasText(directRecordId)
                    && filters.isEmpty()
                    && !matchNone) {
                throw new BusinessConflictException(
                        "VIEW_COMPOSITION_INTERFACE_OUTPUT_INVALID",
                        "接口服务未返回目标记录、筛选条件或明确的空结果");
            }
        } else if (filters.isEmpty() && !matchNone) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_INTERFACE_OUTPUT_INVALID",
                    "接口服务不得把目标列表解析为无条件查询");
        }
        return new Resolution(
                directRecordId,
                unmodifiable(filters),
                matchNone);
    }

    private void requirePublishedReadOperation(
            String ownerType,
            Map<String, Object> serviceBinding,
            String serviceId,
            String operationCode) {
        Integer pinnedRevision = integer(
                serviceBinding.get("serviceRevision"));
        String sourceCode = trim(text(
                serviceBinding.get("sourceCode")));
        if (pinnedRevision == null
                || !StringUtils.hasText(sourceCode)) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_INTERFACE_SNAPSHOT_REQUIRED",
                    "关联内容绑定缺少已发布接口操作身份");
        }
        dataSourceService.validatePinnedReadOperation(
                text(serviceBinding.get("executableSnapshot")),
                text(serviceBinding.get("definitionHash")),
                serviceId,
                sourceCode,
                pinnedRevision,
                operationCode,
                ownerType);
    }

    private Map<String, Object> mapInterfaceInput(
            Map<String, Object> serviceBinding,
            EntityDataDTO sourceRecord) {
        List<Map<String, Object>> mappings = mapList(
                serviceBinding.get("inputMappings"));
        Map<String, Object> input = new LinkedHashMap<>();
        if (mappings.isEmpty()) {
            input.put("recordId", sourceRecord.getId());
            return input;
        }
        for (Map<String, Object> mapping : mappings) {
            String target = firstText(
                    mapping.get("target"),
                    mapping.get("targetPath"),
                    mapping.get("input"));
            String source = firstText(
                    mapping.get("source"),
                    mapping.get("sourcePath"),
                    mapping.get("sourceField"));
            if (!safePath(target)) {
                throw invalidMapping("接口输入映射的目标路径不合法");
            }
            Object value = mapping.containsKey("literal")
                    ? mapping.get("literal")
                    : interfaceSourceValue(sourceRecord, source);
            if (Boolean.TRUE.equals(mapping.get("required"))
                    && value == null) {
                throw invalidMapping(
                        "接口输入映射必填值为空: " + target);
            }
            putPath(input, target, value);
        }
        return input;
    }

    private Map<String, Object> mapInterfaceOutput(
            Map<String, Object> serviceBinding,
            Object raw) {
        Map<String, Object> source = map(raw);
        if (source.isEmpty()) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_INTERFACE_OUTPUT_INVALID",
                    "接口服务返回值必须是对象");
        }
        List<Map<String, Object>> mappings = mapList(
                serviceBinding.get("outputMappings"));
        if (mappings.isEmpty()) {
            return source;
        }
        Map<String, Object> output = new LinkedHashMap<>();
        for (Map<String, Object> mapping : mappings) {
            String sourcePath = firstText(
                    mapping.get("source"),
                    mapping.get("sourcePath"),
                    mapping.get("output"));
            String targetPath = firstText(
                    mapping.get("target"),
                    mapping.get("targetPath"));
            if (!safePath(sourcePath)
                    || !safeOutputPath(targetPath)) {
                throw invalidMapping("接口输出映射路径不合法");
            }
            putPath(output, targetPath, pathValue(source, sourcePath));
        }
        return output;
    }

    private String resolveSingleTargetRecord(
            EntityDefinition targetEntity,
            String directRecordId,
            Map<String, Object> filters) {
        if (StringUtils.hasText(directRecordId)) {
            return readAccessibleRecord(
                    targetEntity, directRecordId).getId();
        }
        PageResult<EntityDataDTO> page =
                targetEntity.getStorageMode()
                        == EntityDefinition.StorageMode.SYSTEM
                        ? systemEntityReadService.findPage(
                        targetEntity.getEntityCode(), filters, 1, 2)
                        : dynamicDataService.findPage(
                        targetEntity.getEntityCode(), null,
                        filters, 1, 2);
        if (page.getTotal() > 1) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_FORM_CARDINALITY_CONFLICT",
                    "关联内容目标表单匹配到多条记录，请改用列表或收紧关联条件");
        }
        return page.getRecords().isEmpty()
                ? null : page.getRecords().get(0).getId();
    }

    private void validateTargetFilters(
            EntityPublishedSnapshot targetSchema,
            Map<String, Object> filters) {
        if (filters.size() > MAX_INTERFACE_FILTERS) {
            throw invalidMapping(
                    "接口服务返回的目标筛选条件不能超过 "
                            + MAX_INTERFACE_FILTERS + " 项");
        }
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String base = filterBase(entry.getKey());
            requireFieldOrId(targetSchema, base, "目标");
            if (entry.getKey().endsWith("_op")) {
                String operator = normalize(text(entry.getValue()));
                if (!FILTER_OPERATORS.contains(operator)) {
                    throw invalidMapping(
                            "接口服务返回了不支持的筛选操作符: "
                                    + operator);
                }
                continue;
            }
            validateFilterValue(
                    entry.getValue(), entry.getKey());
        }
    }

    /**
     * 接口服务未显式声明操作符时按安全精确匹配补齐，避免字符串条件被底层
     * 列表默认解释为 LIKE；集合只允许转换成有界 IN，范围必须同时提供两端。
     */
    private Map<String, Object> normalizeInterfaceFilters(
            Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>(source);
        Set<String> bases = new java.util.LinkedHashSet<>();
        source.keySet().forEach(key -> bases.add(filterBase(key)));
        for (String base : bases) {
            Object value = source.get(base);
            Object start = source.get(base + "_start");
            Object end = source.get(base + "_end");
            boolean range = start != null || end != null;
            if (range && (start == null || end == null)) {
                throw invalidMapping(
                        "接口服务范围筛选必须同时返回起始值和结束值: "
                                + base);
            }
            String operator = normalize(text(
                    source.get(base + "_op")));
            if (!StringUtils.hasText(operator)) {
                operator = range
                        ? "BETWEEN"
                        : value instanceof Collection<?>
                                ? "IN" : "EQ";
                result.put(base + "_op", operator);
            }
            if (range && !"BETWEEN".equals(operator)) {
                throw invalidMapping(
                        "接口服务范围筛选只能使用 BETWEEN: " + base);
            }
            if (!range && "BETWEEN".equals(operator)) {
                throw invalidMapping(
                        "接口服务 BETWEEN 筛选必须返回起始值和结束值: "
                                + base);
            }
            if (!range && value == null) {
                throw invalidMapping(
                        "接口服务筛选条件缺少字段值: " + base);
            }
            if (value instanceof Collection<?>
                    && !"IN".equals(operator)) {
                throw invalidMapping(
                        "接口服务集合筛选只能使用 IN: " + base);
            }
            if ("IN".equals(operator)
                    && !(value instanceof Collection<?>)) {
                throw invalidMapping(
                        "接口服务 IN 筛选必须返回简单值数组: " + base);
            }
        }
        return unmodifiable(result);
    }

    private void validateFilterValue(
            Object value,
            String key) {
        if (value == null
                || value instanceof Map<?, ?>
                || value instanceof Collection<?> collection
                && collection.size() > MAX_INTERFACE_IN_VALUES) {
            throw invalidMapping(
                    "接口服务筛选值无效或数量超过限制: " + key);
        }
        if (value instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                throw invalidMapping(
                        "接口服务空集合应返回 matchNone=true: " + key);
            }
            for (Object item : collection) {
                if (!simpleFilterValue(item)) {
                    throw invalidMapping(
                            "接口服务筛选集合只能包含简单值: " + key);
                }
            }
            return;
        }
        if (value instanceof String text
                && !StringUtils.hasText(text)) {
            throw invalidMapping(
                    "接口服务筛选值不能为空字符串: " + key);
        }
        if (!simpleFilterValue(value)) {
            throw invalidMapping(
                    "接口服务筛选值只能是字符串、数字或布尔值: " + key);
        }
    }

    private boolean simpleFilterValue(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean;
    }

    private String filterBase(String key) {
        if (!StringUtils.hasText(key)) {
            throw invalidMapping("接口服务筛选字段不能为空");
        }
        for (String suffix : List.of("_start", "_end", "_op")) {
            if (key.endsWith(suffix)) {
                String base = key.substring(
                        0, key.length() - suffix.length());
                if (!StringUtils.hasText(base)) {
                    throw invalidMapping("接口服务筛选字段不能为空");
                }
                return base;
            }
        }
        return key;
    }

    private void putEqualFilter(
            Map<String, Object> filters,
            String fieldCode,
            Object value) {
        filters.put(fieldCode, value);
        filters.put(fieldCode + "_op", "EQ");
    }

    private List<Map<String, Object>> relationMappings(
            Map<String, Object> relation) {
        List<Map<String, Object>> mappings = mapList(
                relation.get("mappings"));
        if (!mappings.isEmpty()) {
            return mappings;
        }
        if (StringUtils.hasText(text(relation.get("sourceField")))
                && StringUtils.hasText(text(
                relation.get("targetField")))) {
            return List.of(Map.of(
                    "sourceField", text(relation.get("sourceField")),
                    "targetField", text(relation.get("targetField"))));
        }
        throw invalidRelation("字段匹配至少需要一组来源字段和目标字段");
    }

    private EntityField requireField(
            EntityPublishedSnapshot schema,
            String fieldCode,
            String side) {
        return schema.getFields() == null
                ? missingField(side, fieldCode)
                : schema.getFields().stream()
                .filter(field -> Objects.equals(
                        fieldCode, field.getFieldCode()))
                .findFirst()
                .orElseGet(() -> missingField(side, fieldCode));
    }

    private void requireFieldOrId(
            EntityPublishedSnapshot schema,
            String fieldCode,
            String side) {
        if (!"id".equals(fieldCode)) {
            requireField(schema, fieldCode, side);
        }
    }

    private EntityField missingField(String side, String fieldCode) {
        throw invalidRelation(
                side + "实体发布快照不存在字段: " + fieldCode);
    }

    private String requiredFieldCode(
            Map<String, Object> source,
            String key,
            String message) {
        String value = trim(text(source.get(key)));
        if (!StringUtils.hasText(value)) {
            throw invalidRelation(message);
        }
        return value;
    }

    private Object recordValue(
            EntityDataDTO record,
            String fieldCode) {
        if ("id".equals(fieldCode)) {
            return record.getId();
        }
        if (record.getData() != null
                && record.getData().containsKey(fieldCode)) {
            return record.getData().get(fieldCode);
        }
        Map<String, Object> values = objectMapper.convertValue(
                record,
                new TypeReference<Map<String, Object>>() {});
        return values.get(fieldCode);
    }

    private Object interfaceSourceValue(
            EntityDataDTO record,
            String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String normalized = path.trim();
        if (Set.of("recordId", "sourceRecordId", "record.id")
                .contains(normalized)) {
            return record.getId();
        }
        for (String prefix : List.of(
                "record.data.", "source.data.",
                "record.", "source.", "data.")) {
            if (normalized.startsWith(prefix)) {
                normalized = normalized.substring(prefix.length());
                break;
            }
        }
        if (!normalized.matches(
                "[A-Za-z][A-Za-z0-9_]{0,63}")) {
            throw invalidMapping("接口输入来源字段不合法: " + path);
        }
        return recordValue(record, normalized);
    }

    private Object scalarValue(Object value, String fieldCode) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?>
                || value instanceof Map<?, ?>) {
            throw invalidRelation(
                    "关联字段必须是单值字段: " + fieldCode);
        }
        return value;
    }

    private Object pathValue(
            Map<String, Object> source,
            String path) {
        Object current = source;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private void putPath(
            Map<String, Object> target,
            String path,
            Object value) {
        String[] segments = path.split("\\.");
        Map<String, Object> current = target;
        for (int index = 0; index < segments.length - 1; index++) {
            Object child = current.get(segments[index]);
            if (child == null) {
                Map<String, Object> created = new LinkedHashMap<>();
                current.put(segments[index], created);
                current = created;
            } else if (child instanceof Map<?, ?> map) {
                current = (Map<String, Object>) map;
            } else {
                throw invalidMapping("接口映射目标路径发生冲突: " + path);
            }
        }
        current.put(segments[segments.length - 1], value);
    }

    private boolean safePath(String path) {
        return StringUtils.hasText(path)
                && path.matches(
                "[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*){0,3}");
    }

    private boolean safeOutputPath(String path) {
        return safePath(path)
                && (Set.of(
                "targetRecordId", "recordId", "matchNone")
                .contains(path)
                || path.startsWith("filters.")
                || path.startsWith("fixedFilters."));
    }

    private String failurePolicy(Map<String, Object> special) {
        String policy = normalize(text(
                special.getOrDefault("failurePolicy", "ERROR")));
        if (!FAILURE_POLICIES.contains(policy)) {
            return "ERROR";
        }
        return policy;
    }

    private void requirePermission(
            String permission,
            String message) {
        if (StringUtils.hasText(permission)
                && !PermissionUtil.hasPermission(permission)) {
            throw new ForbiddenException(message + "：" + permission);
        }
    }

    private BusinessConflictException invalidRelation(
            String message) {
        return new BusinessConflictException(
                "VIEW_COMPOSITION_RELATION_INVALID",
                message);
    }

    private BusinessConflictException invalidMapping(
            String message) {
        return new BusinessConflictException(
                "VIEW_COMPOSITION_MAPPING_INVALID",
                message);
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, child) ->
                result.put(String.valueOf(key), child));
        return result;
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> mapped = map(item);
            if (!mapped.isEmpty()) {
                result.add(mapped);
            }
        }
        return List.copyOf(result);
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(String::valueOf)
                .toList();
    }

    private Map<String, Object> unmodifiable(
            Map<String, Object> value) {
        return value == null || value.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(
                new LinkedHashMap<>(value));
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "";
    }

    private String normalizeHash(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT)
                : "";
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String candidate = trim(text(value));
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null
                    ? null : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record ValidatedRequest(
            String ownerType,
            String ownerId,
            String releaseId,
            Integer releaseVersion,
            String compositionKey,
            String recordId,
            boolean tokenAuthorized,
            String releaseResolutionToken,
            String previousTraversalContextToken) {
    }

    private record OwnerRelease(
            UiConfigRelease release,
            Map<String, Object> snapshot) {
    }

    private record TargetAsset(
            EntityDefinition entity,
            String contentType,
            String contentId,
            String contentKey,
            UiConfigRelease release,
            Map<String, Object> snapshot,
            Map<String, Object> ownerSnapshot) {
    }

    private record PinnedSchemas(
            EntityPublishedSnapshot source,
            EntityPublishedSnapshot target) {
    }

    private record Resolution(
            String directRecordId,
            Map<String, Object> fixedFilters,
            boolean matchNone) {
    }
}
