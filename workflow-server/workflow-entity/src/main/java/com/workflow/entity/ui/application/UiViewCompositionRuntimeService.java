package com.workflow.entity.ui.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;
import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
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
import com.workflow.entity.definition.application.EntityRelationFieldPolicy;
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
import com.workflow.entity.ui.api.request.UiExtensionExecuteRequest;
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
 * 服务端固定条件。宿主与目标发布、实体关系、字段以及接口扩展绑定均从服务端
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
    private final UiInterfaceExtensionService dataSourceService;
    private final JsonDocumentCodec codec;
    private final ObjectMapper objectMapper;

    /**
     * 解析指定关联内容。
     *
     * @param request 业务根入口、来源记录和逐层关联路径，任何授权均由服务端重算
     * @return 钉定目标发布、唯一目标记录或可信列表固定条件
     */
    public UiViewCompositionResolveResponse resolve(
            UiViewCompositionResolveRequest request) {
        if (request == null) throw new IllegalArgumentException("关联内容上下文不能为空");
        if (request.getCompositionContext() != null) {
            if (request.getCompositionContext().getCompositionContext() != null)
                throw new IllegalArgumentException("关联路径必须使用展开后的业务根和 path");
            UiViewCompositionResolveResponse parent = resolve(request.getCompositionContext());
            if (!Objects.equals(normalize(request.getOwnerType()), parent.getTargetContentType())
                    || !Objects.equals(request.getOwnerId(), parent.getTargetContentId()))
                throw new BusinessForbiddenException("VIEW_COMPOSITION_PATH_MISMATCH", "关联路径目标与当前宿主不一致");
            UiViewCompositionResolveRequest expanded = objectMapper.convertValue(request.getCompositionContext(), UiViewCompositionResolveRequest.class);
            List<UiViewCompositionResolveRequest.PathEntry> nextPath = new ArrayList<>(expanded.getPath() == null ? List.of() : expanded.getPath());
            UiViewCompositionResolveRequest.PathEntry next = new UiViewCompositionResolveRequest.PathEntry();
            next.setCompositionKey(request.getCompositionKey()); next.setRecordId(request.getRecordId()); nextPath.add(next);
            expanded.setPath(nextPath); expanded.setConfigHash(null); expanded.setCompositionContext(null);
            return resolve(expanded);
        }
        List<UiViewCompositionResolveRequest.PathEntry> path = request.getPath() == null ? List.of() : request.getPath();
        if (path.size() >= 8) throw new BusinessConflictException("VIEW_COMPOSITION_RUNTIME_DEPTH_EXCEEDED", "关联内容最多允许八层导航");
        // 在读取接口型关系（可能触发外部调用）之前先检查根页面配置摘要。
        ValidatedRequest root = validateRequest(request);
        OwnerRelease rootRelease = resolveOwnerRelease(root, null);
        String configHash = FORM.equals(root.ownerType()) ? releaseService.formConfigHash(root.ownerId()) : rootRelease.release().getContentHash();
        if (StringUtils.hasText(request.getConfigHash()) && !Objects.equals(request.getConfigHash(), configHash))
            throw new BusinessConflictException("VIEW_COMPOSITION_CONFIG_CHANGED", "关联内容配置已变化，请保留输入并刷新后重试");
        java.util.Set<String> visited = new java.util.HashSet<>();
        UiViewCompositionResolveRequest current = new UiViewCompositionResolveRequest();
        current.setOwnerType(request.getOwnerType()); current.setOwnerId(request.getOwnerId());
        current.setCompositionKey(request.getCompositionKey()); current.setRecordId(request.getRecordId());
        current.setRuntimeContext(request.getRuntimeContext());
        UiConfigRelease inheritedRelease = null;
        boolean readonly = false;
        UiViewCompositionResolveResponse result = null;
        for (int index = 0; index <= path.size(); index++) {
            String identity = current.getOwnerType() + ":" + current.getOwnerId() + ":" + current.getCompositionKey() + ":" + current.getRecordId();
            if (!visited.add(identity)) throw new BusinessConflictException("VIEW_COMPOSITION_RUNTIME_REENTRY_BLOCKED", "关联内容不能循环进入同一内容和记录");
            result = resolveSingle(current, inheritedRelease);
            readonly |= result.isReadonlyContext();
            if (index == path.size()) break;
            UiViewCompositionResolveRequest.PathEntry hop = path.get(index);
            if (hop == null) throw new IllegalArgumentException("关联路径节点不能为空");
            // 来源行必须属于上一跳的真实关系。任何客户端路径都不能跳过固定筛选或改换实体。
            requireTargetMembership(result, hop.getRecordId());
            current.setOwnerType(result.getTargetContentType()); current.setOwnerId(result.getTargetContentId());
            current.setCompositionKey(hop.getCompositionKey()); current.setRecordId(hop.getRecordId());
            current.setRuntimeContext(Map.of());
            inheritedRelease = "LIST".equals(result.getTargetContentType()) ? releaseMapper.selectById(result.getTargetReleaseId()) : null;
        }
        UiViewCompositionResolveRequest context = objectMapper.convertValue(request, UiViewCompositionResolveRequest.class);
        context.setConfigHash(configHash);
        return result.toBuilder().compositionContext(context).configHash(configHash).readonlyContext(readonly).build();
    }

    /** 校验目标行同时满足关系约束和当前数据权限；也用于后续表单、列表入口复核。 */
    public void requireTargetMembership(UiViewCompositionResolveResponse result, String recordId) {
        if (result.isMatchNone() || !StringUtils.hasText(recordId)) throw new BusinessForbiddenException("VIEW_COMPOSITION_ROW_OUT_OF_SCOPE", "来源记录不属于关联范围");
        EntityDefinition entity = entityMapper.selectById(result.getTargetEntityId());
        EntityDataDTO row = LIST.equals(result.getTargetContentType()) && entity.getStorageMode()==EntityDefinition.StorageMode.DYNAMIC
                ? dynamicDataService.findAccessibleById(entity.getEntityCode(),recordId,result.getTargetContentKey())
                : readAccessibleRecord(entity, recordId);
        if (row == null || !Objects.equals(row.getId(), recordId)) throw new BusinessForbiddenException("VIEW_COMPOSITION_ROW_OUT_OF_SCOPE", "关联来源记录不可访问");
        if (FORM.equals(result.getTargetContentType())) {
            if (!Objects.equals(result.getTargetRecordId(), recordId)) throw new BusinessForbiddenException("VIEW_COMPOSITION_ROW_OUT_OF_SCOPE", "来源记录不属于关联范围");
            return;
        }
        // 固定条件逐项检查而不覆盖相同字段，避免客户端 id 或列表条件放宽关系范围。
        Map<String,Object> snapshot = releaseService.verifiedReleaseSnapshot(releaseMapper.selectById(result.getTargetReleaseId()));
        Map<String,Object> list = map(snapshot.get("list"));
        if (!matchesFilters(row, result.getFixedFilters()) || !matchesFilters(row, publishedFilters(list.get("fixedFilterConfig"))))
            throw new BusinessForbiddenException("VIEW_COMPOSITION_ROW_OUT_OF_SCOPE", "来源记录不满足关联列表固定范围");
    }

    /** 单层解析只使用服务端选出的当前表单或宿主明确引用的列表发布。 */
    private UiViewCompositionResolveResponse resolveSingle(UiViewCompositionResolveRequest request, UiConfigRelease inheritedRelease) {
        ValidatedRequest validated = validateRequest(request);

        OwnerRelease owner = resolveOwnerRelease(validated, inheritedRelease);
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
        UiRuntimeResolutionContext trusted = FORM.equals(validated.ownerType())
                ? releaseService.authorizeRuntimeContext(validated.ownerId(), request.getRuntimeContext()) : null;
        boolean delegatedRecord = trusted != null && StringUtils.hasText(trusted.processInstanceId());
        if (delegatedRecord && (!Objects.equals(trusted.recordId(), validated.recordId())
                || !Objects.equals(trusted.entityCode(), sourceEntity.getEntityCode())))
            throw new BusinessForbiddenException("VIEW_COMPOSITION_ROW_OUT_OF_SCOPE", "关联来源记录与当前任务不一致");
        if (!delegatedRecord) requireOwnerRuntimeAccess(validated.ownerType(), owner.snapshot(), sourceEntity);
        // 流程参与人可能没有独立实体权限；只有已复核任务/实例且精确绑定的来源行使用流程读取权。
        EntityDataDTO sourceRecord = delegatedRecord && sourceEntity.getStorageMode() == EntityDefinition.StorageMode.DYNAMIC
                ? dynamicDataService.findById(sourceEntity.getEntityCode(), validated.recordId())
                : LIST.equals(validated.ownerType()) && sourceEntity.getStorageMode()==EntityDefinition.StorageMode.DYNAMIC
                ? dynamicDataService.findAccessibleById(sourceEntity.getEntityCode(),validated.recordId(),text(map(owner.snapshot().get("list")).get("listKey")))
                : readAccessibleRecord(sourceEntity, validated.recordId());
        if (sourceRecord == null || !Objects.equals(sourceRecord.getId(), validated.recordId()))
            throw new BusinessForbiddenException("VIEW_COMPOSITION_ROW_OUT_OF_SCOPE", "关联来源记录不可访问");
        if (LIST.equals(validated.ownerType()) && !matchesFilters(sourceRecord, publishedFilters(map(owner.snapshot().get("list")).get("fixedFilterConfig"))))
            throw new BusinessForbiddenException("VIEW_COMPOSITION_ROW_OUT_OF_SCOPE", "来源记录不属于宿主列表固定范围");
        boolean readonly = trusted != null && trusted.purpose() == com.workflow.contracts.entity.ui.model.UiRuntimePurpose.HISTORICAL
                || Boolean.TRUE.equals(map(config.get("presentation")).get("readonly"));

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

        return UiViewCompositionResolveResponse.builder()
                .ownerType(validated.ownerType())
                .ownerId(validated.ownerId())
                .releaseId(owner.release().getId())
                .releaseVersion(owner.release().getVersion())
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
                .targetContentHash(FORM.equals(target.contentType()) ? releaseService.formConfigHash(target.contentId()) : target.release().getContentHash())
                .targetRecordId(targetRecordId)
                .fixedFilters(unmodifiable(fixedFilters))
                .matchNone(matchNone)
                .presentation(unmodifiable(map(config.get("presentation"))))
                .actions(stringList(config.get("actions")))
                .failurePolicy(failurePolicy(specialConfig))
                .configHash(FORM.equals(validated.ownerType()) ? releaseService.formConfigHash(validated.ownerId()) : owner.release().getContentHash())
                .readonlyContext(readonly)
                .build();
    }

    /** 校验根定位参数；不接受客户端指定的发布版本或授权结论。 */
    private ValidatedRequest validateRequest(UiViewCompositionResolveRequest request) {
        if (request == null || !OWNER_TYPES.contains(normalize(request.getOwnerType()))
                || !StringUtils.hasText(request.getOwnerId()) || !StringUtils.hasText(request.getCompositionKey())
                || !StringUtils.hasText(request.getRecordId())) throw new IllegalArgumentException("关联内容需要宿主、关联编码和来源记录");
        return new ValidatedRequest(normalize(request.getOwnerType()), request.getOwnerId().trim(), request.getCompositionKey().trim(), request.getRecordId().trim());
    }

    /** 根列表可使用已认证 Embed 会话的固定配置；其余根使用当前发布，子层只接受已验证父配置。 */
    private OwnerRelease resolveOwnerRelease(ValidatedRequest request, UiConfigRelease inheritedRelease) {
        var embedList = com.workflow.contracts.embed.runtime.context.EmbedDelegatedRequestContext.authorizedListTarget().orElse(null);
        UiConfigRelease release = FORM.equals(request.ownerType())
                ? currentFormRelease(request.ownerId())
                : inheritedRelease != null ? inheritedRelease
                : embedList != null ? releaseMapper.selectById(embedList.releaseId())
                : releaseMapper.findActive(LIST, request.ownerId());
        if (release == null || !Objects.equals(request.ownerId(), release.getConfigId()) || !Objects.equals(request.ownerType(), release.getConfigType()))
            throw new BusinessConflictException("VIEW_COMPOSITION_RELEASE_CONFLICT", "关联内容宿主尚未发布或已不可用");
        Map<String, Object> snapshot = releaseService.verifiedReleaseSnapshot(release);
        if (LIST.equals(request.ownerType()) && inheritedRelease == null && embedList != null) {
            Map<String, Object> list = map(snapshot.get("list"));
            // 认证过滤器保存的坐标不能被另一列表复用，版本、实体和稳定列表键必须同时匹配。
            if (!Objects.equals(embedList.releaseVersion(), release.getVersion())
                    || !Objects.equals(embedList.entityCode(), text(list.get("entityCode")))
                    || !Objects.equals(embedList.listKey(), text(list.get("listKey"))))
                throw new BusinessForbiddenException("VIEW_COMPOSITION_EMBED_LIST_MISMATCH", "关联内容与嵌入会话列表范围不一致");
        }
        return new OwnerRelease(release, snapshot);
    }

    /** 表单已无历史版本，统一使用当前发布适配记录。 */
    private UiConfigRelease currentFormRelease(String formId) {
        return releaseMapper.findActive(FORM, formId);
    }

    /**
     * 校验并获取组合；不满足约束时阻止后续处理。
     *
     * @param snapshot 快照，作为 {@code mapList} 的输入影响后续处理
     * @param compositionKey 组合键，后续用于授权校验、关联或幂等去重
     * @return 组合键值结果，供调用方继续处理
     */
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

    /**
     * 校验并获取归属方实体；不满足约束时阻止后续处理。
     *
     * @param snapshot 快照，作为 {@code map} 的输入影响后续处理
     * @param ownerType 归属方类型标识，决定后续归属方实体采用的处理分支
     * @return 校验并获取后的归属方实体结果，供调用方继续处理
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
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
     *
     * @param config 配置内容，决定后续固定实体{@code schemas}的处理规则
     * @param sourceEntity 来源实体，供本方法校验并获取固定实体{@code schemas}时使用
     * @param targetEntity 目标实体，供本方法校验并获取固定实体{@code schemas}时使用
     * @return 校验并获取后的固定实体{@code schemas}结果，供调用方继续处理
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

    /**
     * 校验并获取固定实体结构；不满足约束时阻止后续处理。
     *
     * @param pin 固定，作为 {@code trim} 的输入影响后续处理
     * @param currentEntity 当前实体，供本方法校验并获取固定实体结构时使用
     * @param label 标签，后续用于校验并获取固定实体结构时匹配或展示
     * @return 校验并获取后的固定实体结构结果，供调用方继续处理
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
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

    /**
     * 校验并获取归属方运行时访问；不满足约束时阻止后续处理。
     *
     * @param ownerType 归属方类型标识，决定后续归属方运行时访问采用的处理分支
     * @param snapshot 快照，作为 {@code map} 的输入影响后续处理
     * @param entity 实体，作为 {@code systemEntityReadService.requirePermissions} 的输入影响后续处理
     */
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

    /**
     * 读取可访问记录；查询结果供调用方展示或继续处理。
     *
     * @param entity 实体，作为 {@code systemEntityReadService.findById} 的输入影响后续处理
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @return 读取后的可访问记录结果，供调用方继续处理
     */
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

    /**
     * 校验并获取目标资产；不满足约束时阻止后续处理。
     *
     * @param targetConfig 目标配置内容，决定后续目标资产的处理规则
     * @return 校验并获取后的目标资产结果，供调用方继续处理
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
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
                || (!FORM.equals(contentType) && (!StringUtils.hasText(releaseId)
                || releaseVersion == null || releaseVersion < 1 || !StringUtils.hasText(contentHash)))) {
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
                    || !Objects.equals(entityId, form.getEntityId())) {
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
        UiConfigRelease release = FORM.equals(contentType)
                ? releaseMapper.findActive(FORM, contentId) : releaseMapper.selectById(releaseId);
        if (FORM.equals(contentType) && release != null) {
            releaseVersion = release.getVersion(); contentHash = release.getContentHash();
        }
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
        if (FORM.equals(contentType) && Integer.valueOf(0).equals(integer(targetOwner.get("status"))))
            throw new BusinessConflictException("VIEW_COMPOSITION_TARGET_FORM_INVALID", "当前发布的目标表单未启用");
        return new TargetAsset(
                entity,
                contentType,
                contentId,
                contentKey,
                release,
                targetSnapshot,
                targetOwner);
    }

    /**
     * 校验并获取目标运行时访问；不满足约束时阻止后续处理。
     *
     * @param target 目标，作为 {@code systemEntityReadService.requirePermissions} 的输入影响后续处理
     */
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

    /**
     * 解析标准；输出作为后续校验或处理的输入。
     *
     * @param relation 关系，作为 {@code normalize} 的输入影响后续处理
     * @param sourceSchema 来源结构，作为 {@code requireField} 的输入影响后续处理
     * @param targetSchema 目标结构，作为 {@code requireField} 的输入影响后续处理
     * @param sourceRecord 来源记录，作为 {@code putEqualFilter} 的输入影响后续处理
     * @param target 目标，供本方法解析标准时使用
     * @return 解析后的标准结果，供调用方继续处理
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
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
                boolean reverse = UiEntityRelationBinding.reverse(relation);
                EntityPublishedSnapshot ownerSchema = reverse ? targetSchema : sourceSchema;
                EntityPublishedSnapshot childSchema = reverse ? sourceSchema : targetSchema;
                String relationCode = trim(text(
                        relation.get("relationCode")));
                EntityRelation publishedRelation =
                        ownerSchema.getRelations() == null
                                ? null
                                : ownerSchema.getRelations().stream()
                                .filter(item -> Boolean.TRUE.equals(
                                        item.getEnabled()))
                                .filter(item -> Objects.equals(
                                        relationCode,
                                        item.getRelationCode()))
                                .findFirst()
                                .orElse(null);
                if (publishedRelation == null
                        || !Objects.equals(
                        childSchema.getEntityId(),
                        publishedRelation.getChildEntityId())
                        || !StringUtils.hasText(
                        publishedRelation.getChildRefFieldCode())) {
                    throw invalidRelation(
                            "实体关系不存在于来源实体发布快照或目标实体不匹配");
                }
                EntityField field = requireField(
                        childSchema,
                        publishedRelation.getChildRefFieldCode(),
                        "目标");
                var violation = EntityRelationFieldPolicy.violation(field, ownerSchema.getEntityId());
                if (violation != null) {
                    throw invalidRelation(violation.message());
                }
                if (reverse) {
                    Object value = scalarValue(recordValue(sourceRecord, publishedRelation.getChildRefFieldCode()), publishedRelation.getChildRefFieldCode());
                    if (value == null) matchNone = true;
                    else {
                        directRecordId = String.valueOf(value);
                        putEqualFilter(filters, "id", value);
                    }
                } else putEqualFilter(
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

    /**
     * 判断使用接口服务条件是否成立，供调用方选择后续分支。
     *
     * @param relation 关系，供本方法处理使用接口服务时使用
     * @param special {@code special}，作为 {@code map} 的输入影响后续处理
     * @return 使用接口服务条件成立时为 true，否则为 false
     */
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
        return StringUtils.hasText(firstText(
                service.get("extensionId"), service.get("serviceId")));
    }

    /**
     * 运行时在把自定义组件配置交给前端之前重新校验固定定义。
     * 宿主发布哈希能防整体文档篡改，这里的独立哈希则确保组件标识
     * 与定义内容没有在解析链路中被重组或替换。
     *
     * @param special {@code special}，供本方法校验固定自定义组件时使用
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

    /**
     * 运行时只接受发布阶段固定的 64 位小写十六进制制品摘要。
     *
     * @param value 待校验并获取组件{@code artifact}摘要的原始输入，结果供调用方继续使用
     * @return 校验并获取后的组件{@code artifact}摘要文本，供调用方比较或展示
     */
    private String requireComponentArtifactDigest(Object value) {
        String digest = normalizeHash(text(value));
        if (!digest.matches("[a-f0-9]{64}")) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_COMPONENT_ARTIFACT_INVALID",
                    "已发布自定义组件制品摘要无效");
        }
        return digest;
    }

    /**
     * 计算输入内容的 SHA-256 摘要，供配置完整性校验或幂等去重使用。
     *
     * @param value 待处理{@code sha256}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code sha256}文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 解析接口服务；输出作为后续校验或处理的输入。
     *
     * @param request 本次请求，后续经校验后用于解析接口服务
     * @param owner 归属方，作为 {@code executeRequest.setServerIdempotencyKey} 的输入影响后续处理
     * @param composition 组合，作为 {@code executeRequest.setServerIdempotencyKey} 的输入影响后续处理
     * @param special {@code special}，作为 {@code map} 的输入影响后续处理
     * @param sourceEntity 来源实体，作为 {@code executeRequest.setEntityCode} 的输入影响后续处理
     * @param sourceRecord 来源记录，作为 {@code mapInterfaceInput} 的输入影响后续处理
     * @param target 目标，供本方法解析接口服务时使用
     * @param targetSchema 目标结构，作为 {@code validateTargetFilters} 的输入影响后续处理
     * @return 解析后的接口服务结果，供调用方继续处理
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
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
        String serviceId = trim(firstText(
                serviceBinding.get("extensionId"),
                serviceBinding.get("serviceId")));
        String operationCode = trim(text(
                serviceBinding.get("operationCode")));
        if (!StringUtils.hasText(serviceId)) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_INTERFACE_BINDING_REQUIRED",
                    "关联内容特殊处理未绑定接口扩展");
        }
        requirePublishedReadOperation(
                request.ownerType(), serviceBinding,
                serviceId, operationCode);
        Map<String, Object> input = mapInterfaceInput(
                serviceBinding, sourceRecord);
        UiExtensionExecuteRequest executeRequest =
                new UiExtensionExecuteRequest();
        executeRequest.setUsage(
                UiDataSourceUsages.RELATED_CONTENT_RESOLVE);
        executeRequest.setOperationCode(operationCode);
        executeRequest.setConfigType(request.ownerType());
        executeRequest.setConfigId(request.ownerId());
        executeRequest.setReleaseId(owner.release().getId());
        executeRequest.setReleaseVersion(owner.release().getVersion());
        executeRequest.setEntityCode(sourceEntity.getEntityCode());
        executeRequest.setTargetType("COMPOSITION");
        executeRequest.setTargetKey(request.compositionKey());
        executeRequest.setInput(input);
        {
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
                        "接口扩展未返回目标记录、筛选条件或明确的空结果");
            }
        } else if (filters.isEmpty() && !matchNone) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_INTERFACE_OUTPUT_INVALID",
                    "接口扩展不得把目标列表解析为无条件查询");
        }
        return new Resolution(
                directRecordId,
                unmodifiable(filters),
                matchNone);
    }

    /**
     * 校验并获取已发布读取操作；不满足约束时阻止后续处理。
     *
     * @param ownerType 归属方类型标识，决定后续已发布读取操作采用的处理分支
     * @param serviceBinding 服务绑定，作为 {@code integer} 的输入影响后续处理
     * @param serviceId 服务ID，后续用于校验并获取已发布读取操作时定位或关联目标
     * @param operationCode 操作编码，后续用于校验并获取已发布读取操作时定位或关联目标
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
    private void requirePublishedReadOperation(
            String ownerType,
            Map<String, Object> serviceBinding,
            String serviceId,
            String operationCode) {
        boolean extensionReference = StringUtils.hasText(text(
                serviceBinding.get("extensionId")));
        Integer pinnedRevision = integer(serviceBinding.get(
                extensionReference
                        ? "extensionRevision" : "serviceRevision"));
        String sourceCode = trim(text(serviceBinding.get(
                extensionReference ? "extensionKey" : "sourceCode")));
        if (pinnedRevision == null
                || !StringUtils.hasText(sourceCode)) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_INTERFACE_SNAPSHOT_REQUIRED",
                    "关联内容绑定缺少已发布接口操作身份");
        }
        if (extensionReference) {
            dataSourceService.validatePinnedReadExtension(
                    text(serviceBinding.get("executableSnapshot")),
                    text(serviceBinding.get("definitionHash")),
                    serviceId,
                    sourceCode,
                    pinnedRevision,
                    ownerType);
        } else {
            dataSourceService.validatePinnedReadOperation(
                    text(serviceBinding.get("executableSnapshot")),
                    text(serviceBinding.get("definitionHash")),
                    serviceId,
                    sourceCode,
                    pinnedRevision,
                    operationCode,
                    ownerType);
        }
    }

    /**
     * 整理映射接口输入数据，供调用方遍历或继续处理。
     *
     * @param serviceBinding 服务绑定，作为 {@code mapList} 的输入影响后续处理
     * @param sourceRecord 来源记录，作为 {@code input.put} 的输入影响后续处理
     * @return 映射接口输入键值结果，供调用方继续处理
     */
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

    /**
     * 整理映射接口输出数据，供调用方遍历或继续处理。
     *
     * @param serviceBinding 服务绑定，作为 {@code mapList} 的输入影响后续处理
     * @param raw 待处理映射接口输出的原始输入，结果供调用方继续使用
     * @return 映射接口输出键值结果，供调用方继续处理
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
    private Map<String, Object> mapInterfaceOutput(
            Map<String, Object> serviceBinding,
            Object raw) {
        Map<String, Object> source = map(raw);
        if (source.isEmpty()) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_INTERFACE_OUTPUT_INVALID",
                    "接口扩展返回值必须是对象");
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

    /**
     * 解析{@code single}目标记录；输出作为后续校验或处理的输入。
     *
     * @param targetEntity 目标实体，作为 {@code readAccessibleRecord} 的输入影响后续处理
     * @param directRecordId {@code direct}记录ID，后续用于解析{@code single}目标记录时定位或关联目标
     * @param filters 过滤条件，供本方法解析{@code single}目标记录时使用
     * @return 解析后的{@code single}目标记录文本，供调用方比较或展示
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
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

    /**
     * 校验目标过滤条件；不满足约束时阻止后续处理。
     *
     * @param targetSchema 目标结构，作为 {@code requireFieldOrId} 的输入影响后续处理
     * @param filters 过滤条件，供本方法校验目标过滤条件时使用
     */
    private void validateTargetFilters(
            EntityPublishedSnapshot targetSchema,
            Map<String, Object> filters) {
        if (filters.size() > MAX_INTERFACE_FILTERS) {
            throw invalidMapping(
                    "接口扩展返回的目标筛选条件不能超过 "
                            + MAX_INTERFACE_FILTERS + " 项");
        }
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String base = filterBase(entry.getKey());
            requireFieldOrId(targetSchema, base, "目标");
            if (entry.getKey().endsWith("_op")) {
                String operator = normalize(text(entry.getValue()));
                if (!FILTER_OPERATORS.contains(operator)) {
                    throw invalidMapping(
                            "接口扩展返回了不支持的筛选操作符: "
                                    + operator);
                }
                continue;
            }
            validateFilterValue(
                    entry.getValue(), entry.getKey());
        }
    }

    /**
     * 接口扩展未显式声明操作符时按安全精确匹配补齐，避免字符串条件被底层
     * 列表默认解释为 LIKE；集合只允许转换成有界 IN，范围必须同时提供两端。
     *
     * @param source 待规范化接口过滤条件的原始输入，结果供调用方继续使用
     * @return 接口过滤条件键值结果，供调用方继续处理
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
                        "接口扩展范围筛选必须同时返回起始值和结束值: "
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
                        "接口扩展范围筛选只能使用 BETWEEN: " + base);
            }
            if (!range && "BETWEEN".equals(operator)) {
                throw invalidMapping(
                        "接口扩展 BETWEEN 筛选必须返回起始值和结束值: "
                                + base);
            }
            if (!range && value == null) {
                throw invalidMapping(
                        "接口扩展筛选条件缺少字段值: " + base);
            }
            if (value instanceof Collection<?>
                    && !"IN".equals(operator)) {
                throw invalidMapping(
                        "接口扩展集合筛选只能使用 IN: " + base);
            }
            if ("IN".equals(operator)
                    && !(value instanceof Collection<?>)) {
                throw invalidMapping(
                        "接口扩展 IN 筛选必须返回简单值数组: " + base);
            }
        }
        return unmodifiable(result);
    }

    /**
     * 校验过滤值；不满足约束时阻止后续处理。
     *
     * @param value 待校验过滤值的原始输入，结果供调用方继续使用
     * @param key 键，后续用于授权校验、关联或幂等去重
     */
    private void validateFilterValue(
            Object value,
            String key) {
        if (value == null
                || value instanceof Map<?, ?>
                || value instanceof Collection<?> collection
                && collection.size() > MAX_INTERFACE_IN_VALUES) {
            throw invalidMapping(
                    "接口扩展筛选值无效或数量超过限制: " + key);
        }
        if (value instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                throw invalidMapping(
                        "接口扩展空集合应返回 matchNone=true: " + key);
            }
            for (Object item : collection) {
                if (!simpleFilterValue(item)) {
                    throw invalidMapping(
                            "接口扩展筛选集合只能包含简单值: " + key);
                }
            }
            return;
        }
        if (value instanceof String text
                && !StringUtils.hasText(text)) {
            throw invalidMapping(
                    "接口扩展筛选值不能为空字符串: " + key);
        }
        if (!simpleFilterValue(value)) {
            throw invalidMapping(
                    "接口扩展筛选值只能是字符串、数字或布尔值: " + key);
        }
    }

    /**
     * 判断简要过滤值条件是否成立，供调用方选择后续分支。
     *
     * @param value 待处理简要过滤值的原始输入，结果供调用方继续使用
     * @return 简要过滤值条件成立时为 true，否则为 false
     */
    private boolean simpleFilterValue(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean;
    }

    /**
     * 生成过滤基础文本，供后续匹配或展示。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 处理后的过滤基础文本，供调用方比较或展示
     */
    private String filterBase(String key) {
        if (!StringUtils.hasText(key)) {
            throw invalidMapping("接口扩展筛选字段不能为空");
        }
        for (String suffix : List.of("_start", "_end", "_op")) {
            if (key.endsWith(suffix)) {
                String base = key.substring(
                        0, key.length() - suffix.length());
                if (!StringUtils.hasText(base)) {
                    throw invalidMapping("接口扩展筛选字段不能为空");
                }
                return base;
            }
        }
        return key;
    }

    /**
     * 写入{@code equal}过滤；后续读取或执行将使用更新后的状态。
     *
     * @param filters 过滤条件，供本方法写入{@code equal}过滤时使用
     * @param fieldCode 字段编码，后续用于写入{@code equal}过滤时定位或关联目标
     * @param value 待写入{@code equal}过滤的原始输入，结果供调用方继续使用
     */
    private void putEqualFilter(
            Map<String, Object> filters,
            String fieldCode,
            Object value) {
        filters.put(fieldCode, value);
        filters.put(fieldCode + "_op", "EQ");
    }

    /**
     * 整理关系映射集合数据，供调用方遍历或继续处理。
     *
     * @param relation 关系，作为 {@code mapList} 的输入影响后续处理
     * @return 界面视图组合集合，供调用方遍历或展示
     */
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

    /**
     * 校验并获取字段；不满足约束时阻止后续处理。
     *
     * @param schema 结构，供本方法校验并获取字段时使用
     * @param fieldCode 字段编码，后续用于校验并获取字段时定位或关联目标
     * @param side 侧，供本方法校验并获取字段时使用
     * @return 校验并获取后的字段结果，供调用方继续处理
     */
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

    /**
     * 校验并获取字段或ID；不满足约束时阻止后续处理。
     *
     * @param schema 结构，作为 {@code requireField} 的输入影响后续处理
     * @param fieldCode 字段编码，后续用于校验并获取字段或ID时定位或关联目标
     * @param side 侧，作为 {@code requireField} 的输入影响后续处理
     */
    private void requireFieldOrId(
            EntityPublishedSnapshot schema,
            String fieldCode,
            String side) {
        if (!"id".equals(fieldCode)) {
            requireField(schema, fieldCode, side);
        }
    }

    /**
     * 处理缺失字段，并将结果传给后续步骤。
     *
     * @param side 侧，作为 {@code invalidRelation} 的输入影响后续处理
     * @param fieldCode 字段编码，后续用于处理缺失字段时定位或关联目标
     * @return 处理后的缺失字段结果，供调用方继续处理
     */
    private EntityField missingField(String side, String fieldCode) {
        throw invalidRelation(
                side + "实体发布快照不存在字段: " + fieldCode);
    }

    /**
     * 生成必填字段编码文本，供后续匹配或展示。
     *
     * @param source 待处理必填字段编码的原始输入，结果供调用方继续使用
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param message 消息，作为 {@code invalidRelation} 的输入影响后续处理
     * @return 处理后的必填字段编码文本，供调用方比较或展示
     */
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

    /**
     * 记录值；供后续追溯或审计使用。
     *
     * @param record 记录，作为 {@code objectMapper.convertValue} 的输入影响后续处理
     * @param fieldCode 字段编码，后续用于记录值时定位或关联目标
     * @return 记录后的值结果，供调用方继续处理
     */
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

    /**
     * 处理接口来源值，并将结果传给后续步骤。
     *
     * @param record 记录，作为 {@code recordValue} 的输入影响后续处理
     * @param path 路径，作为 {@code invalidMapping} 的输入影响后续处理
     * @return 处理后的接口来源值结果，供调用方继续处理
     */
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

    /**
     * 处理标量值，并将结果传给后续步骤。
     *
     * @param value 待处理标量值的原始输入，结果供调用方继续使用
     * @param fieldCode 字段编码，后续用于处理标量值时定位或关联目标
     * @return 处理后的标量值结果，供调用方继续处理
     */
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

    /**
     * 处理路径值，并将结果传给后续步骤。
     *
     * @param source 待处理路径值的原始输入，结果供调用方继续使用
     * @param path 路径，供本方法处理路径值时使用
     * @return 处理后的路径值结果，供调用方继续处理
     */
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

    /**
     * 写入路径；后续读取或执行将使用更新后的状态。
     *
     * @param target 目标，供本方法写入路径时使用
     * @param path 路径，作为 {@code invalidMapping} 的输入影响后续处理
     * @param value 待写入路径的原始输入，结果供调用方继续使用
     */
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

    /**
     * 判断安全路径条件是否成立，供调用方选择后续分支。
     *
     * @param path 路径，供本方法处理安全路径时使用
     * @return 安全路径条件成立时为 true，否则为 false
     */
    private boolean safePath(String path) {
        return StringUtils.hasText(path)
                && path.matches(
                "[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*){0,3}");
    }

    /**
     * 判断安全输出路径条件是否成立，供调用方选择后续分支。
     *
     * @param path 路径，作为 {@code safePath} 的输入影响后续处理
     * @return 安全输出路径条件成立时为 true，否则为 false
     */
    private boolean safeOutputPath(String path) {
        return safePath(path)
                && (Set.of(
                "targetRecordId", "recordId", "matchNone")
                .contains(path)
                || path.startsWith("filters.")
                || path.startsWith("fixedFilters."));
    }

    /**
     * 生成失败策略文本，供后续匹配或展示。
     *
     * @param special {@code special}，作为 {@code normalize} 的输入影响后续处理
     * @return 处理后的失败策略文本，供调用方比较或展示
     */
    private String failurePolicy(Map<String, Object> special) {
        String policy = normalize(text(
                special.getOrDefault("failurePolicy", "ERROR")));
        if (!FAILURE_POLICIES.contains(policy)) {
            return "ERROR";
        }
        return policy;
    }

    /**
     * 校验并获取权限；不满足约束时阻止后续处理。
     *
     * @param permission 数据访问权限，后续与查询条件合并以限制可见记录
     * @param message 消息，作为 {@code ForbiddenException} 的输入影响后续处理
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     */
    private void requirePermission(
            String permission,
            String message) {
        if (StringUtils.hasText(permission)
                && !PermissionUtil.hasPermission(permission)) {
            throw new ForbiddenException(message + "：" + permission);
        }
    }

    /**
     * 构造无效关系异常，供调用方区分失败原因。
     *
     * @param message 消息，作为 {@code BusinessConflictException} 的输入影响后续处理
     * @return 处理后的无效关系结果，供调用方继续处理
     */
    private BusinessConflictException invalidRelation(
            String message) {
        return new BusinessConflictException(
                "VIEW_COMPOSITION_RELATION_INVALID",
                message);
    }

    /**
     * 构造无效映射异常，供调用方区分失败原因。
     *
     * @param message 消息，作为 {@code BusinessConflictException} 的输入影响后续处理
     * @return 处理后的无效映射结果，供调用方继续处理
     */
    private BusinessConflictException invalidMapping(
            String message) {
        return new BusinessConflictException(
                "VIEW_COMPOSITION_MAPPING_INVALID",
                message);
    }

    /**
     * 整理映射数据，供调用方遍历或继续处理。
     *
     * @param value 待处理映射的原始输入，结果供调用方继续使用
     * @return 映射键值结果，供调用方继续处理
     */
    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, child) ->
                result.put(String.valueOf(key), child));
        return result;
    }

    /**
     * 整理映射列表数据，供调用方遍历或继续处理。
     *
     * @param value 待处理映射列表的原始输入，结果供调用方继续使用
     * @return 界面视图组合集合，供调用方遍历或展示
     */
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

    /**
     * 整理字符串列表数据，供调用方遍历或继续处理。
     *
     * @param value 待处理字符串列表的原始输入，结果供调用方继续使用
     * @return 界面视图组合集合，供调用方遍历或展示
     */
    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(String::valueOf)
                .toList();
    }

    /**
     * 整理{@code unmodifiable}数据，供调用方遍历或继续处理。
     *
     * @param value 待处理{@code unmodifiable}的原始输入，结果供调用方继续使用
     * @return {@code unmodifiable}键值结果，供调用方继续处理
     */
    private Map<String, Object> unmodifiable(
            Map<String, Object> value) {
        return value == null || value.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(
                new LinkedHashMap<>(value));
    }

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param value 待规范化界面视图组合运行时的原始输入，结果供调用方继续使用
     * @return 规范化后的界面视图组合运行时文本，供调用方比较或展示
     */
    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "";
    }

    /**
     * 规范化哈希；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化哈希的原始输入，结果供调用方继续使用
     * @return 规范化后的哈希文本，供调用方比较或展示
     */
    private String normalizeHash(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT)
                : "";
    }

    /**
     * 清理界面视图组合运行时；后续读取或执行将使用更新后的状态。
     *
     * @param value 待清理界面视图组合运行时的原始输入，结果供调用方继续使用
     * @return 清理后的界面视图组合运行时文本，供调用方比较或展示
     */
    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
    private String firstText(Object... values) {
        for (Object value : values) {
            String candidate = trim(text(value));
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 将输入解析为整数，供后续范围校验或计算使用。
     *
     * @param value 待处理整数的原始输入，结果供调用方继续使用
     * @return 处理后的整数结果，供调用方继续处理
     */
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

    /** 历史列表快照允许对象或 JSON 文本；解析失败时拒绝，不能退化为无固定条件。 */
    private Map<String,Object> publishedFilters(Object raw) {
        if(raw==null)return Map.of();
        if(raw instanceof Map<?,?>)return map(raw);
        if(raw instanceof String text && StringUtils.hasText(text)) {
            try { return objectMapper.readValue(text,new TypeReference<Map<String,Object>>(){}); }
            catch(Exception failure) {throw new BusinessConflictException("VIEW_COMPOSITION_FILTER_INVALID","已发布列表固定条件无法解析");}
        }
        throw new BusinessConflictException("VIEW_COMPOSITION_FILTER_INVALID","已发布列表固定条件格式错误");
    }

    private boolean matchesFilters(
            EntityDataDTO record,
            Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        Set<String> bases = new java.util.LinkedHashSet<>();
        filters.keySet().forEach(key ->
                bases.add(key.replaceFirst("_(op|start|end)$", "")));
        for (String base : bases) {
            Object actual = recordValue(record, base);
            Object expected = filters.get(base);
            String operator = normalize(text(filters.getOrDefault(base + "_op", "EQ")));
            boolean matches = switch (operator) {
                case "IS_NULL" -> actual == null
                        || actual instanceof String text && text.isBlank();
                case "EQ" -> Objects.equals(text(actual), text(expected));
                case "NE" -> !Objects.equals(text(actual), text(expected));
                case "LIKE" -> actual != null && expected != null
                        && text(actual).contains(text(expected));
                case "IN" -> expected instanceof Collection<?> values
                        && values.stream().anyMatch(value ->
                        Objects.equals(text(actual), text(value)));
                case "BETWEEN" -> compareFilterValues(
                        actual, filters.get(base + "_start")) >= 0
                        && compareFilterValues(
                        actual, filters.get(base + "_end")) <= 0;
                case "GT" -> compareFilterValues(actual, expected) > 0;
                case "GE" -> compareFilterValues(actual, expected) >= 0;
                case "LT" -> compareFilterValues(actual, expected) < 0;
                case "LE" -> compareFilterValues(actual, expected) <= 0;
                default -> false;
            };
            if (!matches) {
                return false;
            }
        }
        return true;
    }

    /**
     * 比较过滤值集合；结果供调用方的后续步骤使用。
     *
     * @param left 左侧，作为 {@code java.math.BigDecimal} 的输入影响后续处理
     * @param right 右侧，供本方法比较过滤值集合时使用
     * @return 比较后的过滤值集合结果，供调用方继续处理
     */
    private int compareFilterValues(Object left, Object right) {
        if (left == null || right == null) {
            return left == right ? 0 : left == null ? -1 : 1;
        }
        if (left instanceof Number || right instanceof Number) {
            try {
                return new java.math.BigDecimal(text(left))
                        .compareTo(new java.math.BigDecimal(text(right)));
            } catch (NumberFormatException ignored) {
                // 非标准数值按数据库字符串字段常见的字典序进行安全比较。
            }
        }
        return text(left).compareTo(text(right));
    }


    private record ValidatedRequest(String ownerType, String ownerId, String compositionKey, String recordId) {}

    /**
     * 封装归属方发布版本的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param release 发布版本，保存在对象中供后续校验、查询或展示
     * @param snapshot 快照，保存在对象中供后续校验、查询或展示
     */
    private record OwnerRelease(
            UiConfigRelease release,
            Map<String, Object> snapshot) {
    }

    /**
     * 封装目标资产的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param entity 实体，保存在对象中供后续校验、查询或展示
     * @param contentType 内容类型标识，决定后续目标资产采用的处理分支
     * @param contentId 内容ID，后续用于处理目标资产时定位或关联目标
     * @param contentKey 内容键，后续用于授权校验、关联或幂等去重
     * @param release 发布版本，保存在对象中供后续校验、查询或展示
     * @param snapshot 快照，保存在对象中供后续校验、查询或展示
     * @param ownerSnapshot 归属方快照，保存在对象中供后续校验、查询或展示
     */
    private record TargetAsset(
            EntityDefinition entity,
            String contentType,
            String contentId,
            String contentKey,
            UiConfigRelease release,
            Map<String, Object> snapshot,
            Map<String, Object> ownerSnapshot) {
    }

    /**
     * 封装固定{@code schemas}的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param source 待处理固定{@code schemas}的原始输入，结果供调用方继续使用
     * @param target 目标，保存在对象中供后续校验、查询或展示
     */
    private record PinnedSchemas(
            EntityPublishedSnapshot source,
            EntityPublishedSnapshot target) {
    }

    /**
     * 封装解析的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param directRecordId {@code direct}记录ID，后续用于处理解析时定位或关联目标
     * @param fixedFilters 固定过滤条件，保存在对象中供后续校验、查询或展示
     * @param matchNone 匹配{@code none}，保存在对象中供后续校验、查询或展示
     */
    private record Resolution(
            String directRecordId,
            Map<String, Object> fixedFilters,
            boolean matchNone) {
    }
}
