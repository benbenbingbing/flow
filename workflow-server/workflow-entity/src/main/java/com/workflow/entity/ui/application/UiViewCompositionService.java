package com.workflow.entity.ui.application;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.RevisionConflictException;
import com.workflow.core.result.PageResult;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.contracts.ui.UiDataSourceUsages;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
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
import com.workflow.entity.ui.api.request.UiViewCompositionSaveRequest;
import com.workflow.entity.ui.api.request.UiDataSourceExecuteRequest;
import com.workflow.entity.ui.api.response.UiViewCompositionDTO;
import com.workflow.entity.ui.api.response.UiViewCompositionMutationResultDTO;
import com.workflow.entity.ui.api.response.UiViewCompositionTestDTO;
import com.workflow.entity.ui.api.response.UiViewCompositionValidationDTO;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiDataSourceDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiViewCompositionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.ui.infrastructure.persistence.record.UiDataSourceDefinition;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import com.workflow.entity.ui.infrastructure.persistence.record.UiViewComposition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 表单和列表“关联内容”设计态服务。
 *
 * <p>关联内容本身使用独立 revision 进行 CAS；每次保存或删除还会在同一事务
 * 中递增宿主 FORM/LIST revision 并清空 draftHash，使现有发布差异、撤销和并发
 * 边界能够感知这类子配置。发布快照通过 {@link #snapshot(String, String)} 固定
 * 目标发布版本、接口操作完整可执行定义与组件定义哈希。</p>
 */
@Service
@RequiredArgsConstructor
public class UiViewCompositionService {

    public static final Set<String> OWNER_TYPES = Set.of("FORM", "LIST");
    public static final Set<String> ANCHOR_TYPES = Set.of(
            "OWNER", "FORM_NODE", "PAGE_SECTION", "ROW_EXPAND",
            "TOOLBAR_ACTION", "ROW_ACTION");

    private static final Pattern COMPOSITION_KEY =
            Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,99}");
    private static final Pattern INTERFACE_MAPPING_PATH = Pattern.compile(
            "[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*){0,3}");
    private static final Set<String> INTERFACE_FILTER_OPERATORS = Set.of(
            "EQ", "NE", "LIKE", "GT", "LT", "IN", "BETWEEN");
    private static final int MAX_INTERFACE_FILTERS = 64;
    private static final int MAX_INTERFACE_IN_VALUES = 200;
    private static final Set<String> SNAPSHOT_ITEM_KEYS = Set.of(
            "id", "compositionKey", "anchorType", "anchorKey",
            "orderKey", "config");
    private static final long ORDER_STEP = 1000L;

    private final UiViewCompositionMapper mapper;
    private final EntityFormMapper formMapper;
    private final EntityListConfigMapper listMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityRelationMapper relationMapper;
    private final EntityDataDynamicService entityDataService;
    private final EntityPublishedSnapshotService entitySnapshotService;
    private final UiConfigReleaseMapper releaseMapper;
    /** 保留定义 Mapper 作为设计态服务组件，钉版运行不依赖它。 */
    private final UiDataSourceDefinitionMapper dataSourceMapper;
    private final UiConfigurationAccessService accessService;
    private final UiDataSourceService dataSourceService;
    private final UiExtensionDefinitionService extensionService;
    private final UiViewCompositionConfigValidator configValidator;
    private final UiViewCompositionContainmentGuard containmentGuard;
    private final JsonDocumentCodec codec;

    /**
     * 读取宿主的全部活动关联内容。
     *
     * @param ownerType FORM 或 LIST
     * @param ownerId   表单或列表配置 ID
     * @return 按 orderKey 稳定排序的设计态资源
     */
    public List<UiViewCompositionDTO> list(
            String ownerType,
            String ownerId) {
        OwnerState owner = requireOwner(ownerType, ownerId, false);
        return mapper.findByOwner(owner.type(), owner.id()).stream()
                .map(item -> toDto(item, owner.revision()))
                .toList();
    }

    /**
     * 将宿主的关联内容复制到一个新宿主。
     *
     * <p>该方法供表单/列表复制流程在同一事务内调用。关联内容获得新的数据库
     * 身份和 revision；FORM_NODE 挂载点必须通过 {@code anchorKeyMapping}
     * 映射到副本节点，避免副本继续引用源表单节点。</p>
     *
     * @param ownerType        FORM 或 LIST
     * @param sourceOwnerId    源宿主 ID
     * @param targetOwnerId    新宿主 ID
     * @param anchorKeyMapping 源挂载点到新挂载点的映射
     */
    @Transactional(rollbackFor = Exception.class)
    public void copyForOwner(
            String ownerType,
            String sourceOwnerId,
            String targetOwnerId,
            Map<String, String> anchorKeyMapping) {
        String type = normalizeOwnerType(ownerType);
        String sourceId = requireOwnerId(sourceOwnerId);
        String targetId = requireOwnerId(targetOwnerId);
        if (Objects.equals(sourceId, targetId)) {
            throw new IllegalArgumentException("关联内容复制的源宿主和目标宿主不能相同");
        }
        Map<String, String> anchors = anchorKeyMapping == null
                ? Map.of() : Map.copyOf(anchorKeyMapping);
        LocalDateTime now = LocalDateTime.now();
        for (UiViewComposition source : mapper.findByOwner(type, sourceId)) {
            String anchorType = normalizeAnchorType(source.getAnchorType());
            String anchorKey = source.getAnchorKey();
            if ("FORM_NODE".equals(anchorType)) {
                anchorKey = anchors.get(anchorKey);
                if (!StringUtils.hasText(anchorKey)) {
                    throw new IllegalStateException(
                            "复制表单缺少关联内容节点映射: "
                                    + source.getAnchorKey());
                }
            }
            Map<String, Object> config = configValidator.validate(
                    readConfig(source.getConfigDocument()))
                    .normalizedConfig();
            validateAnchorPlacement(type, anchorType, config);

            UiViewComposition copied = new UiViewComposition();
            copied.setOwnerType(type);
            copied.setOwnerId(targetId);
            copied.setCompositionKey(source.getCompositionKey());
            copied.setAnchorType(anchorType);
            copied.setAnchorKey(anchorKey);
            copied.setConfigDocument(write(config));
            copied.setOrderKey(source.getOrderKey());
            copied.setRevision(1);
            copied.setCreatedAt(now);
            copied.setUpdatedAt(now);
            copied.setDeleted(0);
            mapper.insert(copied);
        }
    }

    /**
     * 新增关联内容，并在同一事务内触碰宿主草稿修订号。
     */
    @Transactional(rollbackFor = Exception.class)
    public UiViewCompositionDTO create(
            String ownerType,
            String ownerId,
            UiViewCompositionSaveRequest request) {
        OwnerState owner = requireOwner(ownerType, ownerId, true);
        ValidatedDraft draft = validateDraft(owner, request);
        UiViewComposition duplicate = mapper.findActiveByKey(
                owner.type(), owner.id(), draft.compositionKey());
        if (duplicate != null) {
            throw new BusinessConflictException(
                    "UI_VIEW_COMPOSITION_KEY_DUPLICATE",
                    "关联内容编码已存在: " + draft.compositionKey());
        }

        LocalDateTime now = LocalDateTime.now();
        UiViewComposition value = new UiViewComposition();
        value.setOwnerType(owner.type());
        value.setOwnerId(owner.id());
        value.setCompositionKey(draft.compositionKey());
        value.setAnchorType(draft.anchorType());
        value.setAnchorKey(draft.anchorKey());
        value.setConfigDocument(write(draft.config()));
        value.setOrderKey(draft.orderKey() == null
                ? nextOrderKey(owner.type(), owner.id())
                : draft.orderKey());
        value.setRevision(1);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        value.setDeleted(0);
        mapper.insert(value);

        int ownerRevision = touchOwner(owner);
        return toDto(mapper.selectById(value.getId()), ownerRevision);
    }

    /**
     * 基于关联内容 revision 更新记录，并在同一事务内递增宿主 revision。
     */
    @Transactional(rollbackFor = Exception.class)
    public UiViewCompositionDTO update(
            String ownerType,
            String ownerId,
            String id,
            UiViewCompositionSaveRequest request) {
        OwnerState owner = requireOwner(ownerType, ownerId, true);
        requireOwnerRevision(request == null
                ? null : request.getExpectedOwnerRevision(), owner);
        UiViewComposition current = requireCompositionForUpdate(
                owner, id);
        requireRevision(request == null ? null : request.getExpectedRevision(),
                current);
        ValidatedDraft draft = validateDraft(owner, request);
        UiViewComposition duplicate = mapper.findActiveByKey(
                owner.type(), owner.id(), draft.compositionKey());
        if (duplicate != null && !Objects.equals(duplicate.getId(), id)) {
            throw new BusinessConflictException(
                    "UI_VIEW_COMPOSITION_KEY_DUPLICATE",
                    "关联内容编码已存在: " + draft.compositionKey());
        }

        int nextRevision = revisionOf(current) + 1;
        UpdateWrapper<UiViewComposition> update = new UpdateWrapper<>();
        update.eq("id", current.getId())
                .eq("revision", revisionOf(current))
                .eq("deleted", 0)
                .set("composition_key", draft.compositionKey())
                .set("anchor_type", draft.anchorType())
                .set("anchor_key", draft.anchorKey())
                .set("config_document", write(draft.config()))
                .set("order_key", draft.orderKey() == null
                        ? current.getOrderKey() : draft.orderKey())
                .set("revision", nextRevision)
                .set("update_time", LocalDateTime.now());
        if (mapper.update(null, update) != 1) {
            throw compositionConflict(id);
        }
        int ownerRevision = touchOwner(owner);
        return toDto(mapper.selectById(id), ownerRevision);
    }

    /**
     * 逻辑删除关联内容，并同步触碰宿主草稿状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public UiViewCompositionMutationResultDTO delete(
            String ownerType,
            String ownerId,
            String id,
            Integer expectedRevision,
            Integer expectedOwnerRevision) {
        OwnerState owner = requireOwner(ownerType, ownerId, true);
        requireOwnerRevision(expectedOwnerRevision, owner);
        UiViewComposition current = requireCompositionForUpdate(owner, id);
        requireRevision(expectedRevision, current);
        UpdateWrapper<UiViewComposition> update = new UpdateWrapper<>();
        update.eq("id", current.getId())
                .eq("revision", revisionOf(current))
                .eq("deleted", 0)
                .set("deleted", 1)
                .set("revision", revisionOf(current) + 1)
                .set("update_time", LocalDateTime.now());
        if (mapper.update(null, update) != 1) {
            throw compositionConflict(id);
        }
        return UiViewCompositionMutationResultDTO.builder()
                .ownerRevision(touchOwner(owner))
                .build();
    }

    /**
     * 兼容只携带资源 ID 的删除请求；宿主身份始终从数据库读取，不能由前端伪造。
     */
    @Transactional(rollbackFor = Exception.class)
    public UiViewCompositionMutationResultDTO delete(
            String id,
            Integer expectedRevision,
            Integer expectedOwnerRevision) {
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("关联内容ID不能为空");
        }
        UiViewComposition current = mapper.selectById(id.trim());
        if (current == null) {
            throw new IllegalArgumentException("关联内容不存在: " + id);
        }
        return delete(
                current.getOwnerType(),
                current.getOwnerId(),
                current.getId(),
                expectedRevision,
                expectedOwnerRevision);
    }

    /**
     * 保存前执行结构、目标资产和扩展引用校验，不修改草稿。
     */
    public UiViewCompositionValidationDTO validate(
            String ownerType,
            String ownerId,
            Map<String, Object> config) {
        OwnerState owner = requireOwner(ownerType, ownerId, false);
        UiViewCompositionConfigValidator.ValidationResult validated =
                configValidator.validate(config);
        validateReferences(owner, validated.normalizedConfig());
        return UiViewCompositionValidationDTO.builder()
                .valid(true)
                .normalizedConfig(validated.normalizedConfig())
                .summary(validated.summary())
                .warnings(validated.warnings())
                .build();
    }

    /**
     * 使用当前用户可读的一条真实来源记录验证关系解析结果。
     *
     * <p>来源和目标查询都经过实体数据权限引擎。任何必填关系值缺失都会返回
     * “无法形成查询条件”，不会退化为无条件查询；响应只暴露最小记录标识。
     * 接口服务关联通过管理预览入口执行已注册 READ 操作。</p>
     */
    @Transactional(readOnly = true)
    public UiViewCompositionTestDTO test(
            String ownerType,
            String ownerId,
            Map<String, Object> config,
            String sourceRecordId) {
        OwnerState owner = requireOwner(ownerType, ownerId, false);
        UiViewCompositionConfigValidator.ValidationResult validated =
                configValidator.validate(config);
        validateReferences(owner, validated.normalizedConfig());
        Map<String, Object> normalized = validated.normalizedConfig();
        EntityDefinition sourceDefinition = requireDefinition(owner.entityId());
        EntityDataDTO source = loadAccessibleSourceSample(
                sourceDefinition.getEntityCode(), sourceRecordId);
        if (source == null) {
            return UiViewCompositionTestDTO.builder()
                    .authorized(true)
                    .matchedCount(0)
                    .summary(validated.summary())
                    .description("当前权限范围内没有可用于测试的来源数据")
                    .sourceRecord(Map.of())
                    .targetRecordIds(List.of())
                    .filters(Map.of())
                    .build();
        }

        Map<String, Object> relation = requireMap(
                normalized.get("relation"), "数据关联");
        Map<String, Object> special = requireMap(
                normalized.get("specialHandling"), "特殊处理");
        if (usesInterfaceService(relation, special)) {
            return testInterfaceService(
                    owner, normalized, source, validated.summary());
        }
        FilterResolution resolution = resolveFilters(
                owner, relation, source);
        if (!resolution.ready()) {
            return UiViewCompositionTestDTO.builder()
                    .authorized(true)
                    .matchedCount(0)
                    .summary(validated.summary())
                    .description(resolution.description())
                    .sourceRecord(minimalRecord(source))
                    .targetRecordIds(List.of())
                    .filters(Map.of())
                    .build();
        }

        Map<String, Object> target = requireMap(
                normalized.get("target"), "目标内容");
        EntityDefinition targetDefinition = requireDefinition(
                String.valueOf(target.get("entityId")));
        String listKey = "LIST".equals(target.get("contentType"))
                ? blankToNull(target.get("contentKey")) : null;
        PageResult<EntityDataDTO> page = entityDataService.findPage(
                targetDefinition.getEntityCode(),
                listKey,
                resolution.filters(),
                1,
                10);
        List<String> targetIds = page.getRecords().stream()
                .map(EntityDataDTO::getId)
                .filter(StringUtils::hasText)
                .toList();
        return UiViewCompositionTestDTO.builder()
                .authorized(true)
                .matchedCount(page.getTotal())
                .summary(validated.summary())
                .description(page.getTotal() == 0
                        ? "真实来源记录可读取，但没有匹配且有权查看的目标数据"
                        : "已按当前用户权限匹配到 " + page.getTotal() + " 条目标数据")
                .sourceRecord(minimalRecord(source))
                .targetRecordIds(targetIds)
                .filters(resolution.filters())
                .build();
    }

    /**
     * 构建宿主发布快照的 {@code viewCompositions} 数组。
     *
     * <p>返回文档只包含稳定 ID 和业务字段，不包含 revision、时间等易变值；
     * 同时以发布当下的权威记录覆盖目标 release、接口操作快照和组件定义，避免客户端
     * 传入钉定信息或宿主发布后的依赖漂移。</p>
     */
    public List<Map<String, Object>> snapshot(
            String ownerType,
            String ownerId) {
        OwnerState owner = requireOwner(ownerType, ownerId, false);
        String type = owner.type();
        List<Map<String, Object>> result = new ArrayList<>();
        for (UiViewComposition value : mapper.findByOwner(type, owner.id())) {
            String anchorType = normalizeAnchorType(value.getAnchorType());
            String anchorKey = normalizeAnchorKey(
                    anchorType, value.getAnchorKey());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", value.getId());
            item.put("compositionKey", value.getCompositionKey());
            item.put("anchorType", anchorType);
            item.put("anchorKey", anchorKey);
            item.put("orderKey", value.getOrderKey());
            Map<String, Object> config = configValidator.validate(
                    readConfig(value.getConfigDocument())).normalizedConfig();
            validateAnchorPlacement(type, anchorType, config);
            pinPublishedDependencies(config, owner.entityId());
            item.put("config", config);
            result.add(item);
        }
        return List.copyOf(result);
    }

    /**
     * 将历史发布中的关联内容投影为“按当前依赖重新钉定”的稳定比较副本。
     *
     * <p>撤销预检需要区分用户草稿变更和目标资产/接口服务的依赖漂移。本方法
     * 不写数据库，保留关联内容自身稳定字段，只把目标 ACTIVE release 和当前
     * 接口 revision 换成权威值，使 projected hash 与恢复后再次调用
     * {@link #snapshot(String, String)} 的结果一致。</p>
     *
     * @param snapshotItems 历史发布快照中的 viewCompositions
     * @return 重新钉定后的稳定比较副本
     */
    public List<Map<String, Object>> normalizeSnapshotForCurrentDependencies(
            List<Map<String, Object>> snapshotItems) {
        if (snapshotItems == null || snapshotItems.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        Set<String> seenKeys = new java.util.HashSet<>();
        Set<String> seenIds = new java.util.HashSet<>();
        for (int index = 0; index < snapshotItems.size(); index++) {
            Map<String, Object> item = snapshotItems.get(index);
            if (item == null) {
                throw new IllegalArgumentException(
                        "关联内容发布快照第 " + (index + 1) + " 项不能为空");
            }
            String id = requireText(item.get("id"), 64, "关联内容快照ID");
            String key = normalizeCompositionKey(item.get("compositionKey"));
            if (!seenIds.add(id) || !seenKeys.add(key)) {
                throw new IllegalArgumentException(
                        "关联内容发布快照包含重复 ID 或 compositionKey");
            }
            String anchorType = normalizeAnchorType(item.get("anchorType"));
            String anchorKey = normalizeAnchorKey(
                    anchorType, item.get("anchorKey"));
            long orderKey = requireOrderKey(item.get("orderKey"));
            Map<String, Object> config = configValidator.validate(requireMap(
                    item.get("config"), "关联内容发布快照配置"))
                    .normalizedConfig();
            // 历史投影没有额外宿主参数，来源身份必须取自
            // 已发布配置本身；缺失时明确失败，不猜测最新实体。
            pinPublishedDependencies(config, null);

            Map<String, Object> projected = new LinkedHashMap<>();
            projected.put("id", id);
            projected.put("compositionKey", key);
            projected.put("anchorType", anchorType);
            projected.put("anchorKey", anchorKey);
            projected.put("orderKey", orderKey);
            projected.put("config", config);
            normalized.add(projected);
        }
        return List.copyOf(normalized);
    }

    /**
     * 在宿主发布或历史版本激活前逐项校验关联内容发布快照。
     *
     * <p>设计态保存校验只能证明当时选择可用；历史激活和 HOTFIX 合成还必须
     * 验证快照内固定的目标发布版本、接口操作可执行定义和组件定义哈希。
     * 接口或组件管理端后续变更不会使旧宿主漂移，也不会仅因当前 revision
     * 不同就拒绝旧快照。本方法不会重新钉定或修改快照，因此旧宿主版本不会静默漂移
     * 到依赖的最新版本。缺失 {@code viewCompositions} 仅作为功能上线前历史
     * 快照的空集合兼容；字段存在时必须是完整、无未知字段的数组。</p>
     *
     * @param ownerType FORM 或 LIST
     * @param ownerId 宿主配置 ID
     * @param ownerSnapshot 待发布、待激活或 HOTFIX 合成后的完整宿主快照
     * @throws IllegalArgumentException 快照结构或挂载点无效
     * @throws BusinessConflictException 固定依赖不存在、漂移或不可用
     */
    public void validateReleaseSnapshot(
            String ownerType,
            String ownerId,
            Map<String, Object> ownerSnapshot) {
        String type = normalizeOwnerType(ownerType);
        String id = requireOwnerId(ownerId);
        if (ownerSnapshot == null || ownerSnapshot.isEmpty()) {
            throw new IllegalArgumentException("关联内容宿主发布快照不能为空");
        }
        String snapshotType = normalize(String.valueOf(
                ownerSnapshot.get("configType")));
        if (!type.equals(snapshotType)) {
            throw new IllegalArgumentException(
                    "关联内容宿主发布快照类型与当前配置不一致");
        }
        Map<String, Object> owner = requireMap(
                ownerSnapshot.get(type.toLowerCase(Locale.ROOT)),
                "关联内容宿主发布快照");
        if (!id.equals(String.valueOf(owner.get("id")))) {
            throw new IllegalArgumentException(
                    "关联内容宿主发布快照ID与当前配置不一致");
        }
        String ownerEntityId = requireText(
                owner.get("entityId"), 64, "关联内容宿主实体");
        Object rawItems = ownerSnapshot.get("viewCompositions");
        if (rawItems == null) {
            // 功能上线前的发布快照没有该字段，语义等同于没有关联内容。
            return;
        }
        if (!(rawItems instanceof List<?> items)) {
            throw new IllegalArgumentException(
                    "关联内容发布快照必须为数组");
        }

        Set<String> seenIds = new java.util.HashSet<>();
        Set<String> seenKeys = new java.util.HashSet<>();
        for (int index = 0; index < items.size(); index++) {
            Object rawItem = items.get(index);
            if (!(rawItem instanceof Map<?, ?> rawMap)) {
                throw new IllegalArgumentException(
                        "关联内容发布快照第 " + (index + 1) + " 项必须为对象");
            }
            Map<String, Object> item = stringMap(rawMap);
            Set<String> unknown = new java.util.HashSet<>(item.keySet());
            unknown.removeAll(SNAPSHOT_ITEM_KEYS);
            if (!unknown.isEmpty()) {
                throw new IllegalArgumentException(
                        "关联内容发布快照第 " + (index + 1)
                                + " 项包含未知字段: " + unknown);
            }
            String itemId = requireText(
                    item.get("id"), 64, "关联内容快照ID");
            String compositionKey = normalizeCompositionKey(
                    item.get("compositionKey"));
            String label = "关联内容“" + compositionKey + "”";
            if (!seenIds.add(itemId) || !seenKeys.add(compositionKey)) {
                throw new IllegalArgumentException(
                        "关联内容发布快照包含重复 ID 或 compositionKey: "
                                + label);
            }
            String anchorType = normalizeAnchorType(item.get("anchorType"));
            String anchorKey = normalizeAnchorKey(
                    anchorType, item.get("anchorKey"));
            requireOrderKey(item.get("orderKey"));
            Map<String, Object> config = configValidator.validate(requireMap(
                    item.get("config"), label + "配置"))
                    .normalizedConfig();
            validateCustomHostAnchor(type, anchorType, owner, label);
            validateAnchorPlacement(type, anchorType, config);
            validateSnapshotAnchor(
                    type, anchorType, anchorKey, ownerSnapshot, config, label);
            validateSnapshotSource(ownerEntityId, config, label);
            validatePinnedReferences(
                    type, ownerEntityId, ownerSnapshot, config, label);
        }
        // 发布、发布预览、历史激活和 HOTFIX 都复用本方法；在逐项身份与
        // 固定依赖校验通过后，再沿精确发布快照检查完整自动包含子图。
        containmentGuard.validate(type, id, ownerSnapshot);
    }

    /**
     * 锁定宿主范围内的全部关联内容，供发布/撤销事务建立串行化边界。
     *
     * <p>调用方必须已经开启事务；该方法刻意不做权限校验，避免内部发布服务
     * 在完成自身鉴权后重复读取宿主。</p>
     */
    public List<UiViewComposition> lockByOwner(
            String ownerType,
            String ownerId) {
        return mapper.findByOwnerForUpdate(
                normalizeOwnerType(ownerType),
                requireOwnerId(ownerId));
    }

    /**
     * 用不可变发布快照替换宿主的关联内容草稿。
     *
     * <p>调用方负责锁定宿主并在完整恢复结束后只递增一次宿主 revision；本方法
     * 因此不会触碰宿主，也不会校验当前依赖是否仍然激活，只校验快照结构安全性。
     * 这样撤销仍可忠实恢复历史钉定内容，依赖漂移由发布预检单独报告。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void restoreForRelease(
            String ownerType,
            String ownerId,
            List<Map<String, Object>> snapshotItems) {
        String type = normalizeOwnerType(ownerType);
        String normalizedOwnerId = requireOwnerId(ownerId);
        lockByOwner(type, normalizedOwnerId);
        mapper.deleteByOwner(type, normalizedOwnerId);
        if (snapshotItems == null || snapshotItems.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Set<String> seenKeys = new java.util.HashSet<>();
        Set<String> seenIds = new java.util.HashSet<>();
        for (int index = 0; index < snapshotItems.size(); index++) {
            Map<String, Object> item = snapshotItems.get(index);
            if (item == null) {
                throw new IllegalArgumentException(
                        "关联内容发布快照第 " + (index + 1) + " 项不能为空");
            }
            String id = requireText(item.get("id"), 64, "关联内容快照ID");
            String compositionKey = normalizeCompositionKey(
                    item.get("compositionKey"));
            if (!seenIds.add(id) || !seenKeys.add(compositionKey)) {
                throw new IllegalArgumentException(
                        "关联内容发布快照包含重复 ID 或 compositionKey");
            }
            String anchorType = normalizeAnchorType(item.get("anchorType"));
            String anchorKey = normalizeAnchorKey(
                    anchorType, item.get("anchorKey"));
            long orderKey = requireOrderKey(item.get("orderKey"));
            Map<String, Object> config = requireMap(
                    item.get("config"), "关联内容发布快照配置");
            Map<String, Object> normalizedConfig = configValidator.validate(config)
                    .normalizedConfig();
            validateAnchorPlacement(type, anchorType, normalizedConfig);

            UiViewComposition value = new UiViewComposition();
            value.setId(id);
            value.setOwnerType(type);
            value.setOwnerId(normalizedOwnerId);
            value.setCompositionKey(compositionKey);
            value.setAnchorType(anchorType);
            value.setAnchorKey(anchorKey);
            value.setConfigDocument(write(normalizedConfig));
            value.setOrderKey(orderKey);
            value.setRevision(1);
            value.setCreatedAt(now);
            value.setUpdatedAt(now);
            value.setDeleted(0);
            mapper.insert(value);
        }
    }

    /**
     * 用配置迁移包中的便携描述替换目标宿主草稿。
     *
     * <p>迁移调用方必须先把实体编码、内容编码、节点编码和接口服务编码解析为
     * 目标环境 ID。本方法会重新校验目标环境中的表单、列表、接口与组件，且始终
     * 为关联内容生成新的数据库身份；源环境 ID、revision 和发布版本均不会复用。
     * 整个宿主只递增一次 revision，便于随后重新发布一个完整版本。</p>
     *
     * @param ownerType FORM 或 LIST
     * @param ownerId 目标环境宿主 ID
     * @param portableItems 已完成目标环境引用重写的便携配置；缺少 id 为正常情况
     */
    @Transactional(rollbackFor = Exception.class)
    public void importPortableDraft(
            String ownerType,
            String ownerId,
            List<Map<String, Object>> portableItems) {
        OwnerState owner = requireOwner(ownerType, ownerId, true);
        lockByOwner(owner.type(), owner.id());
        mapper.deleteByOwner(owner.type(), owner.id());

        LocalDateTime now = LocalDateTime.now();
        Set<String> seenKeys = new java.util.HashSet<>();
        List<Map<String, Object>> items = portableItems == null
                ? List.of() : portableItems;
        for (int index = 0; index < items.size(); index++) {
            Map<String, Object> item = items.get(index);
            if (item == null) {
                throw new IllegalArgumentException(
                        "关联内容迁移配置第 " + (index + 1) + " 项不能为空");
            }
            String compositionKey = normalizeCompositionKey(
                    item.get("compositionKey"));
            if (!seenKeys.add(compositionKey)) {
                throw new IllegalArgumentException(
                        "关联内容迁移配置包含重复 compositionKey: "
                                + compositionKey);
            }
            String anchorType = normalizeAnchorType(item.get("anchorType"));
            String anchorKey = normalizeAnchorKey(
                    anchorType, item.get("anchorKey"));
            long orderKey = requireOrderKey(item.get("orderKey"));
            Map<String, Object> normalizedConfig = configValidator.validate(
                    requireMap(item.get("config"), "关联内容迁移配置"))
                    .normalizedConfig();
            validateAnchorPlacement(owner.type(), anchorType, normalizedConfig);
            validateReferences(owner, normalizedConfig);

            UiViewComposition value = new UiViewComposition();
            // ID 保持为空，由目标环境的 ASSIGN_UUID 生成器创建全新身份。
            value.setOwnerType(owner.type());
            value.setOwnerId(owner.id());
            value.setCompositionKey(compositionKey);
            value.setAnchorType(anchorType);
            value.setAnchorKey(anchorKey);
            value.setConfigDocument(write(normalizedConfig));
            value.setOrderKey(orderKey);
            value.setRevision(1);
            value.setCreatedAt(now);
            value.setUpdatedAt(now);
            value.setDeleted(0);
            mapper.insert(value);
        }
        touchOwner(owner);
    }

    private ValidatedDraft validateDraft(
            OwnerState owner,
            UiViewCompositionSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("关联内容保存请求不能为空");
        }
        String compositionKey = normalizeCompositionKey(
                request.getCompositionKey());
        String anchorType = normalizeAnchorType(request.getAnchorType());
        String anchorKey = normalizeAnchorKey(
                anchorType, request.getAnchorKey());
        Long orderKey = request.getOrderKey();
        if (orderKey != null && orderKey < 0) {
            throw new IllegalArgumentException("关联内容排序键不能小于 0");
        }
        UiViewCompositionConfigValidator.ValidationResult validated =
                configValidator.validate(request.getConfig());
        validateAnchorPlacement(owner.type(), anchorType,
                validated.normalizedConfig());
        validateReferences(owner, validated.normalizedConfig());
        return new ValidatedDraft(
                compositionKey,
                anchorType,
                anchorKey,
                validated.normalizedConfig(),
                orderKey);
    }

    private void validateReferences(
            OwnerState owner,
            Map<String, Object> config) {
        Map<String, Object> target = requireMap(
                config.get("target"), "目标内容");
        String contentType = String.valueOf(target.get("contentType"));
        String contentId = String.valueOf(target.get("contentId"));
        String targetEntityId = String.valueOf(target.get("entityId"));
        Map<String, Object> source = requireMap(
                config.get("source"), "来源内容");
        if (source.get("entityId") != null
                && StringUtils.hasText(String.valueOf(source.get("entityId")))
                && !Objects.equals(
                owner.entityId(), String.valueOf(source.get("entityId")))) {
            throw new IllegalArgumentException(
                    "来源实体与当前表单或列表所属实体不一致");
        }
        if (Objects.equals(owner.type(), contentType)
                && Objects.equals(owner.id(), contentId)) {
            throw new IllegalArgumentException(
                    "关联内容不能直接嵌入当前配置自身");
        }
        if ("FORM".equals(contentType)) {
            EntityForm targetForm = formMapper.selectById(contentId);
            if (targetForm == null
                    || !Objects.equals(targetEntityId, targetForm.getEntityId())) {
                throw new IllegalArgumentException(
                        "目标表单不存在或不属于所选实体");
            }
            activeTargetRelease(contentType, contentId);
        } else {
            EntityListConfig targetList = listMapper.selectById(contentId);
            if (targetList == null
                    || !Objects.equals(targetEntityId, targetList.getEntityId())) {
                throw new IllegalArgumentException(
                        "目标列表不存在或不属于所选实体");
            }
            activeTargetRelease(contentType, contentId);
        }

        Map<String, Object> relation = requireMap(
                config.get("relation"), "数据关联");
        if ("SAME_RECORD".equals(relation.get("type"))
                && !Objects.equals(owner.entityId(), targetEntityId)) {
            throw new IllegalArgumentException(
                    "使用当前记录时，来源实体和目标实体必须相同");
        }
        validateSpecialHandling(owner.type(), requireMap(
                config.get("specialHandling"), "特殊处理"));
    }

    private EntityDataDTO loadAccessibleSourceSample(
            String entityCode,
            String sourceRecordId) {
        if (StringUtils.hasText(sourceRecordId)) {
            return entityDataService.findAccessibleById(
                    entityCode, sourceRecordId.trim(), null);
        }
        PageResult<EntityDataDTO> page = entityDataService.findPage(
                entityCode, null, Map.of(), 1, 1);
        return page.getRecords().isEmpty() ? null : page.getRecords().get(0);
    }

    private UiViewCompositionTestDTO testInterfaceService(
            OwnerState owner,
            Map<String, Object> config,
            EntityDataDTO source,
            String summary) {
        Map<String, Object> special = requireMap(
                config.get("specialHandling"), "特殊处理");
        Map<String, Object> service = requireMap(
                special.get("interfaceService"), "接口服务");
        UiDataSourceExecuteRequest request = new UiDataSourceExecuteRequest();
        request.setUsage(UiDataSourceUsages.RELATED_CONTENT_RESOLVE);
        request.setOperationCode(String.valueOf(service.get("operationCode")));
        request.setConfigType(owner.type());
        request.setConfigId(owner.id());
        request.setEntityCode(source.getEntityCode());
        request.setTargetType("OWNER");
        request.setInput(mapInterfaceInput(service, source));
        Object response = dataSourceService.previewRelatedContentReadOperation(
                String.valueOf(service.get("serviceId")),
                String.valueOf(service.get("operationCode")),
                request);
        InterfaceTestResolution resolution = resolveInterfaceTestTarget(
                config, service, response);
        return UiViewCompositionTestDTO.builder()
                .authorized(true)
                .matchedCount(resolution.matchedCount())
                .summary(summary)
                .description(resolution.description())
                .sourceRecord(minimalRecord(source))
                .targetRecordIds(resolution.targetRecordIds())
                .filters(resolution.filters())
                .build();
    }

    private boolean usesInterfaceService(
            Map<String, Object> relation,
            Map<String, Object> special) {
        if ("INTERFACE_SERVICE".equals(normalize(
                String.valueOf(relation.get("type"))))) {
            return true;
        }
        // special.mode 同时覆盖“数据接口”和“动作接口”。只配置 actionServices
        // 时不能误走数据解析接口，否则真实数据测试会强制读取一个不存在的
        // interfaceService，并让本来有效的动作兜底无法保存或预览。
        if (!(special.get("interfaceService") instanceof Map<?, ?> raw)) {
            return false;
        }
        return StringUtils.hasText(blankToNull(raw.get("serviceId")))
                && StringUtils.hasText(blankToNull(
                raw.get("operationCode")));
    }

    /**
     * 只构造显式接口输入映射；未配置映射时仅传来源记录 ID。
     *
     * <p>设计态已经读取了完整来源记录用于关系测试，但不能因此把整行业务字段
     * 暴露给扩展。每个映射只读取一个配置允许的字段，并写入有界安全路径。</p>
     */
    private Map<String, Object> mapInterfaceInput(
            Map<String, Object> service,
            EntityDataDTO source) {
        List<Map<String, Object>> mappings = mappingList(
                service.get("inputMappings"));
        Map<String, Object> input = new LinkedHashMap<>();
        if (mappings.isEmpty()) {
            input.put("recordId", source.getId());
            return input;
        }
        for (Map<String, Object> mapping : mappings) {
            String targetPath = firstNonBlank(
                    mapping.get("target"),
                    mapping.get("targetField"));
            if (!safeInterfacePath(targetPath)) {
                throw interfaceMappingFailure(
                        "接口输入映射的目标路径不合法");
            }
            Object value = mapping.containsKey("literal")
                    ? mapping.get("literal")
                    : interfaceSourceValue(source, firstNonBlank(
                    mapping.get("source"),
                    mapping.get("sourceField")));
            if (!Boolean.FALSE.equals(mapping.get("required"))
                    && isMissing(value)) {
                throw interfaceMappingFailure(
                        "接口输入映射必填值为空: " + targetPath);
            }
            putInterfacePath(input, targetPath, value);
        }
        return input;
    }

    /**
     * 将接口返回的记录或筛选条件再次交给目标实体数据权限查询。
     * 原始接口 total、records 和 ID 均不作为已授权结果直接返回。
     */
    private InterfaceTestResolution resolveInterfaceTestTarget(
            Map<String, Object> config,
            Map<String, Object> service,
            Object response) {
        Map<String, Object> output = mapInterfaceOutput(service, response);
        Map<String, Object> target = requireMap(
                config.get("target"), "目标内容");
        String contentType = String.valueOf(target.get("contentType"));
        EntityDefinition targetDefinition = requireDefinition(
                String.valueOf(target.get("entityId")));
        String listKey = "LIST".equals(contentType)
                ? blankToNull(target.get("contentKey")) : null;
        boolean matchNone = Boolean.TRUE.equals(output.get("matchNone"));
        String directRecordId = firstNonBlank(
                output.get("targetRecordId"), output.get("recordId"));
        Map<String, Object> filters = output.get("fixedFilters")
                instanceof Map<?, ?> fixed
                ? stringMap(fixed) : output.get("filters")
                instanceof Map<?, ?> rawFilters
                ? stringMap(rawFilters) : Map.of();

        if (matchNone) {
            if (StringUtils.hasText(directRecordId) || !filters.isEmpty()) {
                throw interfaceOutputFailure(
                        "接口服务不能同时返回空结果和目标记录或筛选条件");
            }
            return new InterfaceTestResolution(
                    0, List.of(), Map.of(),
                    "接口服务已执行，并明确返回没有匹配数据");
        }

        if ("FORM".equals(contentType)
                && StringUtils.hasText(directRecordId)) {
            EntityDataDTO record = entityDataService.findAccessibleById(
                    targetDefinition.getEntityCode(), directRecordId, null);
            return new InterfaceTestResolution(
                    1,
                    List.of(record.getId()),
                    Map.of(),
                    "接口服务已执行，并在当前用户权限下匹配到 1 条目标数据");
        }

        if ("LIST".equals(contentType)
                && StringUtils.hasText(directRecordId)) {
            throw interfaceOutputFailure(
                    "目标列表必须返回筛选条件，不能只返回单条记录 ID");
        }
        if (filters.isEmpty()) {
            throw interfaceOutputFailure(
                    "接口服务未返回目标记录、筛选条件或明确的空结果，平台不会执行无条件查询");
        }

        Map<String, Object> safeFilters = normalizeInterfaceFilters(
                String.valueOf(target.get("entityId")), filters);
        PageResult<EntityDataDTO> page = entityDataService.findPage(
                targetDefinition.getEntityCode(),
                listKey,
                safeFilters,
                1,
                "FORM".equals(contentType) ? 2 : 10);
        if ("FORM".equals(contentType) && page.getTotal() > 1) {
            throw interfaceOutputFailure(
                    "接口服务筛选条件匹配到多条目标数据，请改用列表或收紧条件");
        }
        List<String> ids = page.getRecords().stream()
                .map(EntityDataDTO::getId)
                .filter(StringUtils::hasText)
                .limit(10)
                .toList();
        return new InterfaceTestResolution(
                page.getTotal(),
                ids,
                safeFilters,
                page.getTotal() == 0
                        ? "接口服务已执行，但当前用户权限下没有匹配的目标数据"
                        : "接口服务已执行，并在当前用户权限下匹配到 "
                        + page.getTotal() + " 条目标数据");
    }

    private Map<String, Object> mapInterfaceOutput(
            Map<String, Object> service,
            Object response) {
        if (!(response instanceof Map<?, ?> rawResponse)) {
            throw interfaceOutputFailure("接口服务返回值必须是对象");
        }
        Map<String, Object> source = stringMap(rawResponse);
        List<Map<String, Object>> mappings = mappingList(
                service.get("outputMappings"));
        if (mappings.isEmpty()) {
            return source;
        }
        Map<String, Object> output = new LinkedHashMap<>();
        for (Map<String, Object> mapping : mappings) {
            String sourcePath = firstNonBlank(
                    mapping.get("source"),
                    mapping.get("sourceField"));
            String targetPath = firstNonBlank(
                    mapping.get("target"),
                    mapping.get("targetField"));
            if (!safeInterfacePath(sourcePath)
                    || !safeInterfaceOutputPath(targetPath)) {
                throw interfaceMappingFailure("接口输出映射路径不合法");
            }
            Object value = interfacePathValue(source, sourcePath);
            if (!Boolean.FALSE.equals(mapping.get("required"))
                    && value == null) {
                throw interfaceMappingFailure(
                        "接口输出映射必填值为空: " + targetPath);
            }
            putInterfacePath(output, targetPath, value);
        }
        return output;
    }

    private Object interfaceSourceValue(
            EntityDataDTO source,
            String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String normalized = path.trim();
        if (Set.of("recordId", "sourceRecordId", "record.id")
                .contains(normalized)) {
            return source.getId();
        }
        for (String prefix : List.of(
                "record.data.", "source.data.",
                "record.", "source.", "data.")) {
            if (normalized.startsWith(prefix)) {
                normalized = normalized.substring(prefix.length());
                break;
            }
        }
        if (!normalized.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
            throw interfaceMappingFailure(
                    "接口输入来源字段不合法: " + path);
        }
        return sourceValue(source, normalized);
    }

    private Object interfacePathValue(
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
    private void putInterfacePath(
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
                throw interfaceMappingFailure(
                        "接口映射目标路径发生冲突: " + path);
            }
        }
        current.put(segments[segments.length - 1], value);
    }

    private boolean safeInterfacePath(String path) {
        return StringUtils.hasText(path)
                && INTERFACE_MAPPING_PATH.matcher(path).matches();
    }

    private boolean safeInterfaceOutputPath(String path) {
        return safeInterfacePath(path)
                && (Set.of("targetRecordId", "recordId", "matchNone")
                .contains(path)
                || path.startsWith("filters.")
                || path.startsWith("fixedFilters."));
    }

    /**
     * 将接口筛选限制为目标实体发布字段和平台支持的有界操作符；
     * 未声明操作符时强制补为 EQ/IN/BETWEEN，避免底层默认模糊查询。
     */
    private Map<String, Object> normalizeInterfaceFilters(
            String targetEntityId,
            Map<String, Object> source) {
        if (source.size() > MAX_INTERFACE_FILTERS) {
            throw interfaceMappingFailure(
                    "接口服务筛选条件不能超过 "
                            + MAX_INTERFACE_FILTERS + " 项");
        }
        EntityPublishedSnapshot schema = entitySnapshotService
                .getLatestPinnedByEntityId(targetEntityId)
                .snapshot();
        Map<String, Object> result = new LinkedHashMap<>(source);
        Set<String> bases = new java.util.LinkedHashSet<>();
        for (String key : source.keySet()) {
            String base = interfaceFilterBase(key);
            if (!"id".equals(base)) {
                boolean exists = schema.getFields() != null
                        && schema.getFields().stream().anyMatch(field ->
                        Objects.equals(base, field.getFieldCode()));
                if (!exists) {
                    throw interfaceMappingFailure(
                            "接口服务返回了目标实体不存在的筛选字段: " + base);
                }
            }
            bases.add(base);
        }
        for (String base : bases) {
            Object value = source.get(base);
            Object start = source.get(base + "_start");
            Object end = source.get(base + "_end");
            boolean range = start != null || end != null;
            if (range && (start == null || end == null)) {
                throw interfaceMappingFailure(
                        "接口服务范围筛选必须同时返回起始值和结束值: " + base);
            }
            String operator = normalize(String.valueOf(
                    source.getOrDefault(base + "_op", "")));
            if (!StringUtils.hasText(operator)) {
                operator = range
                        ? "BETWEEN"
                        : value instanceof Collection<?> ? "IN" : "EQ";
            }
            if (!INTERFACE_FILTER_OPERATORS.contains(operator)) {
                throw interfaceMappingFailure(
                        "接口服务返回了不支持的筛选操作符: " + operator);
            }
            result.put(base + "_op", operator);
            if (range != "BETWEEN".equals(operator)) {
                throw interfaceMappingFailure(range
                        ? "接口服务范围筛选只能使用 BETWEEN: " + base
                        : "接口服务 BETWEEN 筛选必须返回起始值和结束值: " + base);
            }
            if (!range && value == null) {
                throw interfaceMappingFailure(
                        "接口服务筛选条件缺少字段值: " + base);
            }
            if (value instanceof Collection<?>
                    && !"IN".equals(operator)) {
                throw interfaceMappingFailure(
                        "接口服务集合筛选只能使用 IN: " + base);
            }
            if ("IN".equals(operator)
                    && !(value instanceof Collection<?>)) {
                throw interfaceMappingFailure(
                        "接口服务 IN 筛选必须返回简单值数组: " + base);
            }
        }
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            if (entry.getKey().endsWith("_op")) {
                continue;
            }
            validateInterfaceFilterValue(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(result);
    }

    private void validateInterfaceFilterValue(
            String key,
            Object value) {
        if (value == null || value instanceof Map<?, ?>) {
            throw interfaceMappingFailure(
                    "接口服务筛选值无效: " + key);
        }
        if (value instanceof Collection<?> collection) {
            if (collection.isEmpty()
                    || collection.size() > MAX_INTERFACE_IN_VALUES) {
                throw interfaceMappingFailure(
                        "接口服务筛选集合为空或数量超过限制: " + key);
            }
            for (Object item : collection) {
                if (!simpleInterfaceFilterValue(item)) {
                    throw interfaceMappingFailure(
                            "接口服务筛选集合只能包含简单值: " + key);
                }
            }
            return;
        }
        if (!simpleInterfaceFilterValue(value)
                || value instanceof String text
                && !StringUtils.hasText(text)) {
            throw interfaceMappingFailure(
                    "接口服务筛选值只能是非空字符串、数字或布尔值: " + key);
        }
    }

    private boolean simpleInterfaceFilterValue(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean;
    }

    private String interfaceFilterBase(String key) {
        if (!StringUtils.hasText(key)) {
            throw interfaceMappingFailure("接口服务筛选字段不能为空");
        }
        for (String suffix : List.of("_start", "_end", "_op")) {
            if (key.endsWith(suffix)) {
                String base = key.substring(0, key.length() - suffix.length());
                if (!StringUtils.hasText(base)) {
                    throw interfaceMappingFailure("接口服务筛选字段不能为空");
                }
                return base;
            }
        }
        return key;
    }

    private BusinessConflictException interfaceMappingFailure(
            String message) {
        return new BusinessConflictException(
                "UI_VIEW_COMPOSITION_MAPPING_INVALID", message);
    }

    private BusinessConflictException interfaceOutputFailure(
            String message) {
        return new BusinessConflictException(
                "UI_VIEW_COMPOSITION_INTERFACE_OUTPUT_INVALID", message);
    }

    private FilterResolution resolveFilters(
            OwnerState owner,
            Map<String, Object> relation,
            EntityDataDTO source) {
        String type = String.valueOf(relation.get("type"));
        Map<String, Object> filters = new LinkedHashMap<>();
        switch (type) {
            case "SAME_RECORD" -> filters.put("id", source.getId());
            case "REFERENCE_FIELD" -> {
                String sourceField = String.valueOf(relation.get("sourceField"));
                Object value = sourceValue(source, sourceField);
                if (isMissing(value)) {
                    return FilterResolution.notReady(
                            "真实来源记录的“" + sourceField
                                    + "”为空，平台不会退化为无条件查询");
                }
                filters.put("id", value);
            }
            case "REVERSE_REFERENCE" -> filters.put(
                    String.valueOf(relation.get("targetField")),
                    source.getId());
            case "ENTITY_RELATION" -> {
                EntityRelation definition = relationMapper.selectByRelationCode(
                        owner.entityId(),
                        String.valueOf(relation.get("relationCode")));
                if (definition == null
                        || Integer.valueOf(1).equals(definition.getDeleted())
                        || !Boolean.TRUE.equals(definition.getEnabled())) {
                    return FilterResolution.notReady(
                            "所选实体关系不存在、已删除或未启用");
                }
                filters.put(definition.getChildRefFieldCode(), source.getId());
            }
            case "FIELD_MATCH" -> {
                List<Map<String, Object>> mappings = mappingList(
                        relation.get("mappings"));
                if (mappings.isEmpty()) {
                    Object value = sourceValue(source,
                            String.valueOf(relation.get("sourceField")));
                    if (isMissing(value)) {
                        return FilterResolution.notReady(
                                "真实来源记录的匹配字段为空，平台不会退化为无条件查询");
                    }
                    filters.put(
                            targetFieldName(String.valueOf(
                                    relation.get("targetField"))), value);
                } else {
                    for (Map<String, Object> mapping : mappings) {
                        Object value = mapping.containsKey("literal")
                                ? mapping.get("literal")
                                : sourceValue(source, firstNonBlank(
                                mapping.get("sourceField"), mapping.get("source")));
                        boolean required = !Boolean.FALSE.equals(
                                mapping.get("required"));
                        if (isMissing(value)) {
                            if (required) {
                                return FilterResolution.notReady(
                                        "真实来源记录缺少必填映射值，平台不会退化为无条件查询");
                            }
                            continue;
                        }
                        filters.put(targetFieldName(firstNonBlank(
                                mapping.get("targetField"), mapping.get("target"))), value);
                    }
                }
            }
            default -> throw new IllegalArgumentException(
                    "不支持的真实数据测试关联方式: " + type);
        }
        if (filters.isEmpty()) {
            return FilterResolution.notReady(
                    "没有形成安全的目标查询条件，平台不会执行无条件查询");
        }
        return new FilterResolution(Map.copyOf(filters), true, "");
    }

    private EntityDefinition requireDefinition(String entityId) {
        EntityDefinition definition = definitionMapper.selectById(entityId);
        if (definition == null || !StringUtils.hasText(definition.getEntityCode())) {
            throw new IllegalArgumentException("实体不存在或缺少实体编码: " + entityId);
        }
        return definition;
    }

    private Object sourceValue(EntityDataDTO source, String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String normalized = path.trim();
        if (normalized.startsWith("source.")) {
            normalized = normalized.substring("source.".length());
        }
        if (normalized.startsWith("record.")) {
            normalized = normalized.substring("record.".length());
        }
        if (normalized.startsWith("data.")) {
            normalized = normalized.substring("data.".length());
        }
        return switch (normalized) {
            case "id" -> source.getId();
            case "title" -> source.getTitle();
            case "name" -> source.getName();
            case "code" -> source.getCode();
            case "status" -> source.getStatus();
            case "dataNo" -> source.getDataNo();
            default -> nestedValue(source.getData(), normalized);
        };
    }

    private Object nestedValue(Map<String, Object> data, String path) {
        if (data == null || !StringUtils.hasText(path)) {
            return null;
        }
        Object current = data;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private String targetFieldName(String raw) {
        String field = raw == null ? "" : raw.trim();
        for (String prefix : List.of("target.", "filters.", "data.")) {
            if (field.startsWith(prefix)) {
                field = field.substring(prefix.length());
            }
        }
        if (!StringUtils.hasText(field)) {
            throw new IllegalArgumentException("目标匹配字段不能为空");
        }
        return field;
    }

    private List<Map<String, Object>> mappingList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(this::stringMap)
                .toList();
    }

    private boolean isMissing(Object value) {
        return value == null
                || value instanceof String text && !StringUtils.hasText(text)
                || value instanceof java.util.Collection<?> collection
                && collection.isEmpty();
    }

    private Map<String, Object> minimalRecord(EntityDataDTO value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value.getId());
        result.put("title", firstNonBlank(
                value.getTitle(), value.getName(), value.getCode(), value.getId()));
        result.put("entityCode", value.getEntityCode());
        return result;
    }

    private String blankToNull(Object value) {
        return value != null && StringUtils.hasText(String.valueOf(value))
                ? String.valueOf(value).trim() : null;
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private void validateSpecialHandling(
            String ownerType,
            Map<String, Object> special) {
        if (special.get("interfaceService") instanceof Map<?, ?> rawService) {
            Map<String, Object> service = stringMap(rawService);
            String serviceId = String.valueOf(service.get("serviceId"));
            String operationCode = String.valueOf(service.get("operationCode"));
            Map<String, Object> operation = dataSourceService.operations(serviceId)
                    .stream()
                    .filter(item -> Objects.equals(
                            operationCode, String.valueOf(item.get("code"))))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "接口服务操作不存在: " + operationCode));
            String kind = normalize(String.valueOf(
                    operation.getOrDefault("kind", "READ")));
            if (!"READ".equals(kind)) {
                throw new IllegalArgumentException(
                        "关联内容特殊处理只允许选择 READ 接口操作");
            }
            String contextType = normalize(String.valueOf(
                    operation.get("contextType")));
            if (!ownerType.equals(contextType)) {
                throw new IllegalArgumentException(
                        "接口操作上下文必须与当前" + ownerType + "配置一致");
            }
        }
        for (Map<String, Object> service : mappingList(
                special.get("actionServices"))) {
            UiDataSourceService.ActionOperationDescriptor operation =
                    dataSourceService.validateActionOperation(
                            String.valueOf(service.get("serviceId")),
                            String.valueOf(service.get("operationCode")),
                            ownerType);
            if ("WRITE".equals(operation.operationKind())
                    && !"ERROR".equals(normalize(String.valueOf(
                    service.getOrDefault("failurePolicy", "ERROR"))))) {
                throw new IllegalArgumentException(
                        "本地写入失败时必须停止操作，不能隐藏错误或显示占位");
            }
            if ("WRITE".equals(operation.operationKind())
                    && Set.of("VIEW", "SELECT", "CREATE", "EDIT",
                    "LINK", "UNLINK", "SAVE_WITH_FORM").contains(
                    normalize(String.valueOf(service.get("actionKey"))))) {
                throw new IllegalArgumentException(
                        "标准页面操作由平台权威执行；同名接口只能选择 READ 操作用于校验、计算或界面结果");
            }
        }
        if (special.get("customComponent") instanceof Map<?, ?> rawComponent) {
            Map<String, Object> component = stringMap(rawComponent);
            String name = String.valueOf(component.get("name"));
            int version = ((Number) component.get("version")).intValue();
            boolean registered = extensionService.list(null, name, "ACTIVE")
                    .stream()
                    .anyMatch(item -> isViewComponent(item)
                            && Objects.equals(item.getVersion(), version));
            if (!registered) {
                throw new IllegalArgumentException(
                        "自定义组件未注册、已禁用或版本不存在: "
                                + name + "@" + version);
            }
        }
    }

    private boolean isViewComponent(UiExtensionDefinition item) {
        return item != null && Set.of("NODE", "FORM", "LIST").contains(
                normalize(item.getExtensionType()));
    }

    /**
     * 防止关联内容发布到整页自定义宿主不会渲染的默认挂载点。
     *
     * <p>整页自定义表单不经过默认表单节点树，整页自定义列表也不
     * 经过默认行展开、行操作和工具栏容器。目前平台外层仅能稳定提供
     * 表单宿主入口和列表页面区块，因此未声明挂载能力时必须 fail-closed，
     * 避免配置发布成功却在运行时静默丢失。</p>
     */
    private void validateCustomHostAnchor(
            String ownerType,
            String anchorType,
            Map<String, Object> owner,
            String label) {
        if (blankToNull(owner.get("customComponent")) == null) {
            return;
        }
        if ("FORM".equals(ownerType) && "FORM_NODE".equals(anchorType)) {
            throw new IllegalArgumentException(
                    label + "不能挂载到表单节点：宿主使用整页自定义"
                            + "表单组件，默认表单节点树不会渲染。请改用表单"
                            + "宿主入口；如需节点内挂载，须由自定义组件显式支持"
                            + "后再配置");
        }
        if ("LIST".equals(ownerType) && !"PAGE_SECTION".equals(anchorType)) {
            throw new IllegalArgumentException(
                    label + "不能挂载到当前列表位置：宿主使用整页"
                            + "自定义列表组件，默认列表的行展开、行操作和"
                            + "工具栏不会渲染。请改用页面区块；如需该位置，"
                            + "须由自定义组件显式支持后再配置");
        }
    }

    /** 校验 FORM_NODE 确实存在于同一份宿主快照，列表挂载点则由类型与呈现方式约束。 */
    private void validateSnapshotAnchor(
            String ownerType,
            String anchorType,
            String anchorKey,
            Map<String, Object> ownerSnapshot,
            Map<String, Object> config,
            String label) {
        if (!"FORM".equals(ownerType)
                || !"FORM_NODE".equals(anchorType)) {
            return;
        }
        Object rawNodes = ownerSnapshot.get("nodes");
        if (!(rawNodes instanceof List<?> nodes)) {
            throw new IllegalArgumentException(
                    label + "挂载到表单节点，但宿主快照缺少节点数组");
        }
        Map<?, ?> matchedNode = nodes.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(node -> Objects.equals(anchorKey, node.get("id"))
                        || Objects.equals(anchorKey, node.get("nodeKey")))
                .findFirst()
                .orElse(null);
        if (matchedNode == null) {
            throw new IllegalArgumentException(
                    label + "挂载的表单节点不存在: " + anchorKey);
        }
        String position = normalize(String.valueOf(requireMap(
                config.get("presentation"), label + "显示方式")
                .get("position")));
        // “Tab 页”不是视觉别名：只有真正挂到 TAB 节点，运行时才会进入
        // 对应 el-tab-pane。阻止把普通节点后的卡片误发布成 Tab 内容。
        if ("TAB".equals(position)
                && !"TAB".equals(normalize(String.valueOf(
                matchedNode.get("nodeType"))))) {
            throw new IllegalArgumentException(
                    label + "选择 Tab 显示时，放置位置必须是表单中的 Tab 页");
        }
    }

    /** 校验发布快照内声明的来源实体仍属于当前宿主，避免跨实体快照拼接。 */
    private void validateSnapshotSource(
            String ownerEntityId,
            Map<String, Object> config,
            String label) {
        Map<String, Object> source = requireMap(
                config.get("source"), label + "来源内容");
        String sourceEntityId = blankToNull(source.get("entityId"));
        if (sourceEntityId != null
                && !Objects.equals(ownerEntityId, sourceEntityId)) {
            throw new IllegalArgumentException(
                    label + "来源实体与宿主所属实体不一致");
        }
    }

    /**
     * 校验不可变宿主快照内的所有固定依赖，不读取当前 ACTIVE 指针也不重新钉定。
     */
    private void validatePinnedReferences(
            String ownerType,
            String ownerEntityId,
            Map<String, Object> ownerSnapshot,
            Map<String, Object> config,
            String label) {
        Map<String, Object> target = requireMap(
                config.get("target"), label + "目标内容");
        UiConfigRelease targetRelease = validatePinnedTarget(target, label);
        PinnedSchemas schemas = validatePinnedEntitySnapshots(
                ownerEntityId,
                requireText(
                        target.get("entityId"), 64, label + "目标实体"),
                config,
                label);
        validatePinnedRelation(
                config,
                schemas.source(),
                schemas.target(),
                label);
        validatePublishedActionMappings(
                ownerType,
                ownerSnapshot,
                verifiedTargetSnapshot(targetRelease, label),
                target,
                config,
                schemas,
                label);
        Map<String, Object> special = requireMap(
                config.get("specialHandling"), label + "特殊处理");
        if (special.get("interfaceService") instanceof Map<?, ?> rawService) {
            validatePinnedInterfaceService(
                    ownerType, stringMap(rawService), label);
        }
        for (Map<String, Object> service : mappingList(
                special.get("actionServices"))) {
            UiDataSourceService.ActionOperationDescriptor operation =
                    validatePinnedActionService(
                            ownerType, service, label);
            if ("WRITE".equals(operation.operationKind())
                    && !"ERROR".equals(normalize(String.valueOf(
                    service.getOrDefault("failurePolicy", "ERROR"))))) {
                throw new BusinessConflictException(
                        "UI_VIEW_COMPOSITION_WRITE_FAILURE_POLICY_INVALID",
                        label + "本地写入失败时必须停止操作");
            }
            if ("WRITE".equals(operation.operationKind())
                    && Set.of("VIEW", "SELECT", "CREATE", "EDIT",
                    "LINK", "UNLINK", "SAVE_WITH_FORM").contains(
                    normalize(String.valueOf(service.get("actionKey"))))) {
                throw new BusinessConflictException(
                        "UI_VIEW_COMPOSITION_STANDARD_ACTION_WRITE_FORBIDDEN",
                        label + "标准页面操作不能由接口 WRITE 替代");
            }
        }
        if (special.get("customComponent") instanceof Map<?, ?> rawComponent) {
            validatePinnedCustomComponent(
                    stringMap(rawComponent), label);
        }
    }

    /**
     * 校验宿主快照中的实体历史钉定，且始终按 historyId
     * 精确读取。缺失这个结构的旧关联内容快照会 fail-closed，
     * 避免为了兼容而无提示地跟随当前最新字段或关系。
     */
    private PinnedSchemas validatePinnedEntitySnapshots(
            String ownerEntityId,
            String targetEntityId,
            Map<String, Object> config,
            String label) {
        if (!(config.get("entitySnapshots") instanceof Map<?, ?> raw)) {
            throw new BusinessConflictException(
                    "UI_VIEW_COMPOSITION_ENTITY_SNAPSHOT_REQUIRED",
                    label + "缺少实体发布快照，请重新发布宿主配置");
        }
        Map<String, Object> snapshots = stringMap(raw);
        EntityPublishedSnapshot source = validatePinnedEntitySnapshot(
                requireMap(
                        snapshots.get("source"), label + "来源实体快照"),
                ownerEntityId,
                label + "来源实体");
        EntityPublishedSnapshot target = validatePinnedEntitySnapshot(
                requireMap(
                        snapshots.get("target"), label + "目标实体快照"),
                targetEntityId,
                label + "目标实体");
        return new PinnedSchemas(source, target);
    }

    private EntityPublishedSnapshot validatePinnedEntitySnapshot(
            Map<String, Object> pin,
            String expectedEntityId,
            String label) {
        String historyId = requireText(
                pin.get("historyId"), 64, label + "历史ID");
        String entityId = requireText(
                pin.get("entityId"), 64, label + "ID");
        String entityCode = requireText(
                pin.get("entityCode"), 100, label + "编码");
        int version = positiveInteger(
                pin.get("version"), label + "发布版本");
        String schemaHash = normalizeHash(requireText(
                pin.get("schemaHash"), 64, label + "快照哈希"));
        EntityPublishedSnapshotService.PinnedEntitySnapshot resolved;
        try {
            resolved = entitySnapshotService.getPinnedByHistoryId(historyId);
        } catch (RuntimeException exception) {
            throw new BusinessConflictException(
                    "UI_VIEW_COMPOSITION_ENTITY_SNAPSHOT_MISSING",
                    label + "的固定发布历史已缺失，无法安全激活");
        }
        EntityPublishedSnapshot snapshot = resolved.snapshot();
        if (!Objects.equals(expectedEntityId, entityId)
                || !Objects.equals(entityId, snapshot.getEntityId())
                || !Objects.equals(entityCode, snapshot.getEntityCode())
                || !Objects.equals(version, snapshot.getVersion())
                || !schemaHash.equals(normalizeHash(
                        resolved.schemaHash()))) {
            throw new BusinessConflictException(
                    "UI_VIEW_COMPOSITION_ENTITY_SNAPSHOT_CONFLICT",
                    label + "的固定发布历史身份或完整性校验失败");
        }
        return snapshot;
    }

    /** 用钉定实体定义预检关联语义，不查询当前字段或关系表。 */
    private void validatePinnedRelation(
            Map<String, Object> config,
            EntityPublishedSnapshot source,
            EntityPublishedSnapshot target,
            String label) {
        Map<String, Object> relation = requireMap(
                config.get("relation"), label + "数据关联");
        String type = normalize(String.valueOf(relation.get("type")));
        switch (type) {
            case "SAME_RECORD" -> {
                if (!Objects.equals(
                        source.getEntityId(), target.getEntityId())) {
                    throw pinnedRelationInvalid(
                            label, "使用当前记录要求来源与目标实体相同");
                }
            }
            case "REFERENCE_FIELD" -> {
                EntityField field = pinnedField(
                        source,
                        String.valueOf(relation.get("sourceField")),
                        label,
                        "来源");
                if (field.getFieldType() != EntityField.FieldType.REFERENCE
                        || !Objects.equals(
                        field.getRefEntityId(), target.getEntityId())) {
                    throw pinnedRelationInvalid(
                            label, "来源引用字段未指向目标实体");
                }
            }
            case "REVERSE_REFERENCE" -> {
                EntityField field = pinnedField(
                        target,
                        String.valueOf(relation.get("targetField")),
                        label,
                        "目标");
                if (field.getFieldType() != EntityField.FieldType.REFERENCE
                        || !Objects.equals(
                        field.getRefEntityId(), source.getEntityId())) {
                    throw pinnedRelationInvalid(
                            label, "目标引用字段未指向来源实体");
                }
            }
            case "FIELD_MATCH" -> {
                List<Map<String, Object>> mappings = mappingList(
                        relation.get("mappings"));
                if (mappings.isEmpty()) {
                    requirePinnedFieldOrId(
                            source,
                            String.valueOf(relation.get("sourceField")),
                            label,
                            "来源");
                    requirePinnedFieldOrId(
                            target,
                            String.valueOf(relation.get("targetField")),
                            label,
                            "目标");
                } else {
                    for (Map<String, Object> mapping : mappings) {
                        if (!mapping.containsKey("literal")) {
                            requirePinnedFieldOrId(
                                    source,
                                    firstNonBlank(
                                            mapping.get("sourceField"),
                                            mapping.get("source")),
                                    label,
                                    "来源");
                        }
                        requirePinnedFieldOrId(
                                target,
                                firstNonBlank(
                                        mapping.get("targetField"),
                                        mapping.get("target")),
                                label,
                                "目标");
                    }
                }
            }
            case "ENTITY_RELATION" -> validatePinnedEntityRelation(
                    relation, source, target, label);
            case "INTERFACE_SERVICE" -> {
                // 接口会返回受控记录或过滤条件，字段另在运行时
                // 按同一份目标钉定快照校验。
            }
            default -> throw pinnedRelationInvalid(
                    label, "关联方式不受支持: " + type);
        }
    }

    private void validatePinnedEntityRelation(
            Map<String, Object> relation,
            EntityPublishedSnapshot source,
            EntityPublishedSnapshot target,
            String label) {
        if (!source.isRelationsSnapshotAvailable()) {
            throw new BusinessConflictException(
                    "UI_VIEW_COMPOSITION_RELATION_SNAPSHOT_REQUIRED",
                    label + "引用的旧实体版本未冻结关系，请重新发布实体和宿主配置");
        }
        String relationCode = String.valueOf(relation.get("relationCode"));
        EntityRelation definition = source.getRelations() == null
                ? null : source.getRelations().stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .filter(item -> Objects.equals(
                        relationCode, item.getRelationCode()))
                .findFirst()
                .orElse(null);
        if (definition == null
                || !Objects.equals(
                definition.getChildEntityId(), target.getEntityId())
                || !StringUtils.hasText(
                definition.getChildRefFieldCode())) {
            throw pinnedRelationInvalid(
                    label, "已钉定的来源实体版本不包含该关系");
        }
        pinnedField(
                target,
                definition.getChildRefFieldCode(),
                label,
                "目标");
    }

    private void requirePinnedFieldOrId(
            EntityPublishedSnapshot snapshot,
            String fieldCode,
            String label,
            String side) {
        if (!"id".equals(fieldCode)) {
            pinnedField(snapshot, fieldCode, label, side);
        }
    }

    private EntityField pinnedField(
            EntityPublishedSnapshot snapshot,
            String fieldCode,
            String label,
            String side) {
        return snapshot.getFields() == null ? nullField(
                label, side, fieldCode) : snapshot.getFields().stream()
                .filter(field -> Objects.equals(
                        fieldCode, field.getFieldCode()))
                .findFirst()
                .orElseGet(() -> nullField(
                        label, side, fieldCode));
    }

    private EntityField nullField(
            String label,
            String side,
            String fieldCode) {
        throw pinnedRelationInvalid(
                label, side + "实体钉定版本不存在字段: " + fieldCode);
    }

    private BusinessConflictException pinnedRelationInvalid(
            String label,
            String message) {
        return new BusinessConflictException(
                "UI_VIEW_COMPOSITION_PINNED_RELATION_INVALID",
                label + message);
    }

    private UiConfigRelease validatePinnedTarget(
            Map<String, Object> target,
            String label) {
        String entityId = requireText(
                target.get("entityId"), 64, label + "目标实体");
        String contentType = normalize(String.valueOf(
                target.get("contentType")));
        String contentId = requireText(
                target.get("contentId"), 64, label + "目标表单或列表");
        String releaseId = requireText(
                target.get("releaseId"), 64, label + "目标发布版本");
        if (!(target.get("releaseVersion") instanceof Number number)
                || number.intValue() < 1
                || number.doubleValue() != number.intValue()) {
            throw new IllegalArgumentException(
                    label + "目标发布版本号必须为正整数");
        }
        int releaseVersion = number.intValue();
        String contentHash = requireText(
                target.get("contentHash"), 64, label + "目标发布内容哈希")
                .toLowerCase(Locale.ROOT);
        if ("FORM".equals(contentType)) {
            EntityForm form = formMapper.selectById(contentId);
            if (form == null
                    || !Objects.equals(entityId, form.getEntityId())
                    || !Integer.valueOf(1).equals(form.getStatus())) {
                throw new BusinessConflictException(
                        "UI_VIEW_COMPOSITION_TARGET_FORM_INVALID",
                        label + "引用的目标表单不存在、未启用或不属于目标实体");
            }
        } else if ("LIST".equals(contentType)) {
            EntityListConfig list = listMapper.selectById(contentId);
            if (list == null
                    || !Objects.equals(entityId, list.getEntityId())) {
                throw new BusinessConflictException(
                        "UI_VIEW_COMPOSITION_TARGET_LIST_INVALID",
                        label + "引用的目标列表不存在或不属于目标实体");
            }
        } else {
            throw new IllegalArgumentException(
                    label + "目标内容类型只支持 FORM 或 LIST");
        }
        UiConfigRelease release = releaseMapper.selectById(releaseId);
        if (release == null
                || !contentType.equals(normalize(release.getConfigType()))
                || !contentId.equals(release.getConfigId())
                || !Objects.equals(releaseVersion, release.getVersion())
                || !contentHash.equals(normalizeHash(release.getContentHash()))) {
            throw new BusinessConflictException(
                    "UI_VIEW_COMPOSITION_TARGET_RELEASE_CONFLICT",
                    label + "固定的目标发布版本不存在或版本、内容哈希不一致");
        }
        verifyReleaseDocumentHash(release, label);
        return release;
    }

    /**
     * 校验第三步动作中的字段映射确实存在于宿主和目标的精确发布快照。
     *
     * <p>设计态字段目录可能在发布前发生变化；只校验实体当前字段会让配置
     * 发布成功后才在运行时变成不可用。这里同时验证钉定实体结构和 FORM/LIST
     * 发布可见、可编辑边界，使普通发布、历史激活与 HOTFIX 使用相同规则。</p>
     */
    private void validatePublishedActionMappings(
            String ownerType,
            Map<String, Object> ownerSnapshot,
            Map<String, Object> targetSnapshot,
            Map<String, Object> target,
            Map<String, Object> config,
            PinnedSchemas schemas,
            String label) {
        Map<String, Object> settings = requireMap(
                config.get("actionSettings"), label + "操作设置");
        List<String> actions = config.get("actions") instanceof List<?> values
                ? values.stream().map(String::valueOf).map(this::normalize).toList()
                : List.of();

        Map<String, Object> select = requireMap(
                settings.get("select"), label + "选择记录设置");
        if (actions.contains("SELECT")
                && "FILL_FIELDS".equals(normalize(String.valueOf(
                select.get("result"))))) {
            if (!"FORM".equals(ownerType)
                    || !"LIST".equals(normalize(String.valueOf(
                    target.get("contentType"))))) {
                throw new IllegalArgumentException(
                        label + "选择记录回填只适用于表单中的目标列表，请修改“允许做什么”");
            }
            int index = 0;
            for (Map<String, Object> mapping : mappingList(
                    select.get("mappings"))) {
                index++;
                if (mapping.containsKey("literal")) {
                    throw mappingInvalid(
                            label, "选择结果回填", index,
                            "来源必须是目标列表字段，不能使用固定值");
                }
                String targetField = firstMappingField(
                        mapping, "sourceField", "source");
                String ownerField = firstMappingField(
                        mapping, "targetField", "target");
                requirePublishedListReadableField(
                        targetSnapshot,
                        schemas.target(),
                        targetField,
                        label,
                        "选择结果回填",
                        index);
                requirePublishedFormEditableField(
                        ownerSnapshot,
                        schemas.source(),
                        ownerField,
                        "edit",
                        label,
                        "选择结果回填",
                        index,
                        true);
            }
        }

        Map<String, Object> create = requireMap(
                settings.get("create"), label + "新增记录设置");
        if (actions.contains("CREATE")) {
            if (!"FORM".equals(normalize(String.valueOf(
                    target.get("contentType"))))) {
                throw new IllegalArgumentException(
                        label + "新增记录必须使用目标表单，请修改“显示内容”");
            }
            int index = 0;
            for (Map<String, Object> mapping : mappingList(
                    create.get("initialMappings"))) {
                index++;
                if (mapping.containsKey("literal")) {
                    throw mappingInvalid(
                            label, "新增初值", index,
                            "初值必须来自当前记录字段，不能使用固定值");
                }
                String ownerField = firstMappingField(
                        mapping, "sourceField", "source");
                String targetField = firstMappingField(
                        mapping, "targetField", "target");
                requirePublishedOwnerReadableField(
                        ownerType,
                        ownerSnapshot,
                        schemas.source(),
                        ownerField,
                        label,
                        "新增初值",
                        index);
                requirePublishedFormEditableField(
                        targetSnapshot,
                        schemas.target(),
                        targetField,
                        "create",
                        label,
                        "新增初值",
                        index,
                        false);
            }
        }
    }

    private Map<String, Object> verifiedTargetSnapshot(
            UiConfigRelease release,
            String label) {
        // validatePinnedTarget 已先校验不可变发布记录与 SHA-256；这里仅解析同一文档。
        return codec.readObject(
                release.getSnapshotDocument(), label + "目标发布快照");
    }

    private void requirePublishedOwnerReadableField(
            String ownerType,
            Map<String, Object> ownerSnapshot,
            EntityPublishedSnapshot schema,
            String fieldCode,
            String label,
            String mappingLabel,
            int index) {
        EntityField field = publishedField(
                schema, fieldCode, label, mappingLabel, index, "当前记录");
        if (Boolean.FALSE.equals(field.getRuntimeReadable())) {
            throw mappingInvalid(
                    label, mappingLabel, index,
                    "当前记录字段不可用于运行时读取: " + fieldCode);
        }
        if ("FORM".equals(ownerType)) {
            Map<String, Object> published = findFormField(
                    ownerSnapshot, fieldCode);
            if (published == null || flag(published.get("isHidden"))) {
                throw mappingInvalid(
                        label, mappingLabel, index,
                        "当前记录字段未在宿主表单发布版本中显示: " + fieldCode);
            }
            return;
        }
        Map<String, Object> published = findListField(ownerSnapshot, fieldCode);
        if (published == null || !flag(published.get("showInList"))) {
            throw mappingInvalid(
                    label, mappingLabel, index,
                    "当前记录字段未在宿主列表发布版本中显示: " + fieldCode);
        }
    }

    private void requirePublishedListReadableField(
            Map<String, Object> snapshot,
            EntityPublishedSnapshot schema,
            String fieldCode,
            String label,
            String mappingLabel,
            int index) {
        EntityField field = publishedField(
                schema, fieldCode, label, mappingLabel, index, "目标");
        if (Boolean.FALSE.equals(field.getRuntimeReadable())) {
            throw mappingInvalid(
                    label, mappingLabel, index,
                    "目标字段不可用于运行时回填: " + fieldCode);
        }
        Map<String, Object> published = findListField(snapshot, fieldCode);
        if (published == null || !flag(published.get("showInList"))) {
            throw mappingInvalid(
                    label, mappingLabel, index,
                    "目标字段未在固定列表版本中显示: " + fieldCode
                            + "；请在目标列表中显示该字段后重新发布");
        }
        String sourceType = normalize(String.valueOf(
                published.get("dataSourceType")));
        if (StringUtils.hasText(sourceType)
                && !Set.of("ENTITY_FIELD", "REFERENCE").contains(sourceType)) {
            throw mappingInvalid(
                    label, mappingLabel, index,
                    "计算列或自定义列不能直接回填实体字段: " + fieldCode);
        }
    }

    private void requirePublishedFormEditableField(
            Map<String, Object> snapshot,
            EntityPublishedSnapshot schema,
            String fieldCode,
            String mode,
            String label,
            String mappingLabel,
            int index,
            boolean ownerField) {
        EntityField field = publishedField(
                schema,
                fieldCode,
                label,
                mappingLabel,
                index,
                ownerField ? "当前表单" : "目标表单");
        if (Boolean.FALSE.equals(field.getEditable())) {
            throw mappingInvalid(
                    label, mappingLabel, index,
                    (ownerField ? "当前实体字段" : "目标实体字段")
                            + "不可编辑: " + fieldCode);
        }
        Map<String, Object> published = findFormField(snapshot, fieldCode);
        if (published == null
                || flag(published.get("isHidden"))
                || flag(published.get("isReadonly"))
                || !editableInPublishedMode(published, mode)) {
            throw mappingInvalid(
                    label, mappingLabel, index,
                    (ownerField ? "当前字段" : "目标字段")
                            + "未在固定表单版本的“"
                            + ("create".equals(mode) ? "新增" : "编辑")
                            + "”模式中开放: " + fieldCode);
        }
    }

    private EntityField publishedField(
            EntityPublishedSnapshot schema,
            String fieldCode,
            String label,
            String mappingLabel,
            int index,
            String side) {
        if (!StringUtils.hasText(fieldCode)) {
            throw mappingInvalid(
                    label, mappingLabel, index,
                    side + "字段不能为空");
        }
        EntityField field = schema.getFields() == null ? null
                : schema.getFields().stream()
                .filter(item -> Objects.equals(
                        fieldCode, item.getFieldCode()))
                .findFirst()
                .orElse(null);
        if (field == null) {
            throw mappingInvalid(
                    label, mappingLabel, index,
                    side + "发布实体版本不存在字段: " + fieldCode);
        }
        return field;
    }

    private Map<String, Object> findFormField(
            Map<String, Object> snapshot,
            String fieldCode) {
        return mappingList(snapshot.get("legacyFields")).stream()
                .filter(item -> Objects.equals(
                        fieldCode, blankToNull(item.get("fieldCode"))))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> findListField(
            Map<String, Object> snapshot,
            String fieldCode) {
        Object rawList = snapshot.get("list");
        if (!(rawList instanceof Map<?, ?> list)) {
            return null;
        }
        return mappingList(list.get("fields")).stream()
                .filter(item -> Objects.equals(
                        fieldCode, blankToNull(item.get("fieldCode"))))
                .findFirst()
                .orElse(null);
    }

    private boolean editableInPublishedMode(
            Map<String, Object> field,
            String mode) {
        Object raw = field.get("extensionConfig");
        if (raw == null || raw instanceof String text
                && !StringUtils.hasText(text)) {
            return true;
        }
        Map<String, Object> extension;
        if (raw instanceof Map<?, ?> map) {
            extension = stringMap(map);
        } else {
            extension = codec.readObject(
                    String.valueOf(raw), "表单字段模式配置");
        }
        Object rawModes = extension.get("modes");
        if (!(rawModes instanceof Map<?, ?> modes)) {
            return true;
        }
        Object rawMode = modes.get(mode);
        if (!(rawMode instanceof Map<?, ?> modeConfig)) {
            return true;
        }
        Map<String, Object> normalized = stringMap(modeConfig);
        return !Boolean.FALSE.equals(normalized.get("visible"))
                && !Boolean.FALSE.equals(normalized.get("editable"));
    }

    private String firstMappingField(
            Map<String, Object> mapping,
            String primary,
            String fallback) {
        String value = blankToNull(mapping.get(primary));
        return value != null ? value : blankToNull(mapping.get(fallback));
    }

    private boolean flag(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && Set.of("1", "TRUE", "YES", "Y")
                .contains(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
    }

    private IllegalArgumentException mappingInvalid(
            String label,
            String mappingLabel,
            int index,
            String message) {
        return new IllegalArgumentException(
                label + mappingLabel + "第 " + index + " 项无效：" + message
                        + "。请返回第三步“允许做什么”修改字段映射");
    }

    private void validatePinnedInterfaceService(
            String ownerType,
            Map<String, Object> service,
            String label) {
        String serviceId = requireText(
                service.get("serviceId"), 64, label + "接口服务");
        String sourceCode = requireText(
                service.get("sourceCode"), 100, label + "接口服务编码");
        if (!(service.get("serviceRevision") instanceof Number number)
                || number.intValue() < 1
                || number.doubleValue() != number.intValue()) {
            throw new IllegalArgumentException(
                    label + "接口服务修订号必须为正整数");
        }
        int serviceRevision = number.intValue();
        String operationCode = requireText(
                service.get("operationCode"), 100, label + "接口操作");
        String snapshot = requireText(
                service.get("executableSnapshot"),
                JsonDocumentCodec.DEFAULT_MAX_LENGTH,
                label + "接口操作发布快照");
        String hash = requireText(
                service.get("definitionHash"),
                64,
                label + "接口操作哈希");
        dataSourceService.validatePinnedReadOperation(
                snapshot,
                hash,
                serviceId,
                sourceCode,
                serviceRevision,
                operationCode,
                ownerType);
    }

    private void validatePinnedCustomComponent(
            Map<String, Object> component,
            String label) {
        String name = requireText(
                component.get("name"), 100, label + "自定义组件");
        if (!(component.get("version") instanceof Number versionNumber)
                || versionNumber.intValue() < 1
                || versionNumber.doubleValue() != versionNumber.intValue()) {
            throw new IllegalArgumentException(
                    label + "自定义组件版本必须为正整数");
        }
        if (!(component.get("snapshotVersion")
                instanceof Number snapshotNumber)
                || snapshotNumber.intValue() < 1
                || snapshotNumber.doubleValue() != snapshotNumber.intValue()) {
            throw new IllegalArgumentException(
                    label + "自定义组件快照版本必须为正整数");
        }
        int version = versionNumber.intValue();
        int snapshotVersion = snapshotNumber.intValue();
        String extensionType = normalize(requireText(
                component.get("extensionType"), 40,
                label + "自定义组件类型"));
        String definitionSnapshot = requireText(
                component.get("definitionSnapshot"),
                JsonDocumentCodec.DEFAULT_MAX_LENGTH,
                label + "自定义组件定义快照");
        String expectedHash = normalizeHash(requireText(
                component.get("definitionHash"),
                64,
                label + "自定义组件定义哈希"));
        String canonical = codec.canonicalize(
                definitionSnapshot,
                label + "自定义组件定义快照");
        if (!expectedHash.equals(sha256(canonical))) {
            throw new BusinessConflictException(
                    "UI_VIEW_COMPOSITION_COMPONENT_SNAPSHOT_TAMPERED",
                    label + "固定的自定义组件定义完整性校验失败");
        }
        Map<String, Object> definition = codec.readObject(
                canonical,
                label + "自定义组件定义快照");
        Integer schemaVersion = integerValue(
                definition.get("schemaVersion"));
        if (schemaVersion == null
                || schemaVersion != 1 && schemaVersion != 2
                || !name.equals(String.valueOf(
                definition.get("extensionKey")))
                || !Objects.equals(version, integerValue(
                definition.get("version")))
                || !Objects.equals(snapshotVersion, integerValue(
                definition.get("snapshotVersion")))
                || !extensionType.equals(normalize(String.valueOf(
                definition.get("extensionType"))))
                || !"ACTIVE".equals(normalize(String.valueOf(
                definition.get("status"))))) {
            throw new BusinessConflictException(
                    "UI_VIEW_COMPOSITION_COMPONENT_VERSION_CONFLICT",
                    label + "固定的自定义组件标识、类型或快照版本不一致");
        }
        if (schemaVersion >= 2) {
            String artifactDigest = requireArtifactDigest(
                    component.get("artifactDigest"),
                    label + "自定义组件制品摘要");
            String pinnedDigest = requireArtifactDigest(
                    definition.get("artifactDigest"),
                    label + "自定义组件定义制品摘要");
            if (!artifactDigest.equals(pinnedDigest)) {
                throw new BusinessConflictException(
                        "UI_VIEW_COMPOSITION_COMPONENT_ARTIFACT_CONFLICT",
                        label + "固定的自定义组件制品摘要不一致");
            }
        }
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number
                && number.doubleValue() == number.intValue()) {
            return number.intValue();
        }
        return null;
    }

    /** 组件制品摘要只接受固定长度十六进制，不能由运行时名称推导或降级。 */
    private String requireArtifactDigest(Object value, String label) {
        String digest = requireText(value, 64, label)
                .toLowerCase(Locale.ROOT);
        if (!digest.matches("[a-f0-9]{64}")) {
            throw new BusinessConflictException(
                    "UI_VIEW_COMPOSITION_COMPONENT_ARTIFACT_INVALID",
                    label + "必须为 64 位十六进制字符串");
        }
        return digest;
    }

    private int positiveInteger(Object value, String label) {
        Integer number = integerValue(value);
        if (number == null || number < 1) {
            throw new IllegalArgumentException(label + "必须为正整数");
        }
        return number;
    }

    private void verifyReleaseDocumentHash(
            UiConfigRelease release,
            String label) {
        if (!StringUtils.hasText(release.getSnapshotDocument())
                || !StringUtils.hasText(release.getContentHash())) {
            throw new BusinessConflictException(
                    "UI_VIEW_COMPOSITION_TARGET_RELEASE_INVALID",
                    label + "目标发布记录缺少快照或内容哈希");
        }
        String canonical = codec.canonicalize(
                release.getSnapshotDocument(), label + "目标发布快照");
        if (!normalizeHash(release.getContentHash())
                .equals(sha256(canonical))) {
            throw new BusinessConflictException(
                    "UI_VIEW_COMPOSITION_TARGET_RELEASE_HASH_INVALID",
                    label + "目标发布快照完整性校验失败");
        }
    }

    private String normalizeHash(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    /**
     * 发布快照中的依赖钉定只能来自数据库权威记录，不能信任草稿内的 ID 版本组合。
     */
    private void pinPublishedDependencies(
            Map<String, Object> config,
            String ownerEntityId) {
        Map<String, Object> target = requireMutableMap(
                config.get("target"), "目标内容");
        pinEntitySnapshots(config, target, ownerEntityId);
        String contentType = String.valueOf(target.get("contentType"));
        String contentId = String.valueOf(target.get("contentId"));
        UiConfigRelease release = activeTargetRelease(contentType, contentId);
        target.put("releaseId", release.getId());
        target.put("releaseVersion", release.getVersion());
        target.put("contentHash", release.getContentHash());

        Map<String, Object> special = requireMutableMap(
                config.get("specialHandling"), "特殊处理");
        if (special.get("interfaceService") instanceof Map<?, ?> raw) {
            Map<String, Object> service = stringMap(raw);
            UiDataSourceService.PublishedOperationSnapshot operation =
                    dataSourceService.freezeOperation(
                            String.valueOf(service.get("serviceId")),
                            String.valueOf(service.get("operationCode")));
            service.put("sourceCode", operation.sourceCode());
            service.put("serviceRevision", operation.serviceRevision());
            service.put("executableSnapshot", operation.document());
            service.put("definitionHash", operation.hash());
            special.put("interfaceService", service);
        }
        if (special.get("actionServices") instanceof List<?> rawActions) {
            List<Map<String, Object>> pinnedActions = new ArrayList<>();
            for (Object rawAction : rawActions) {
                Map<String, Object> service = stringMap(
                        requireMap(rawAction, "动作接口服务"));
                UiDataSourceService.PublishedOperationSnapshot operation =
                        dataSourceService.freezeActionOperation(
                                String.valueOf(service.get("serviceId")),
                                String.valueOf(service.get("operationCode")));
                service.put("sourceCode", operation.sourceCode());
                service.put("serviceRevision", operation.serviceRevision());
                service.put("executableSnapshot", operation.document());
                service.put("definitionHash", operation.hash());
                pinnedActions.add(service);
            }
            special.put("actionServices", List.copyOf(pinnedActions));
        }
        if (special.get("customComponent") instanceof Map<?, ?> raw) {
            Map<String, Object> component = stringMap(raw);
            String name = String.valueOf(component.get("name"));
            int version = ((Number) component.get("version")).intValue();
            String artifactDigest = requireArtifactDigest(
                    component.get("artifactDigest"),
                    "自定义组件制品摘要");
            UiExtensionDefinition definition = extensionService
                    .list(null, name, "ACTIVE")
                    .stream()
                    .filter(this::isViewComponent)
                    .filter(item -> Objects.equals(item.getVersion(), version))
                    .findFirst()
                    .orElseThrow(() -> new BusinessConflictException(
                            "UI_VIEW_COMPOSITION_COMPONENT_UNAVAILABLE",
                            "关联内容引用的自定义组件未注册、已禁用或版本不存在"));
            component.put("extensionType", definition.getExtensionType());
            component.put("snapshotVersion",
                    definition.getSnapshotVersion() == null
                            ? 1 : definition.getSnapshotVersion());
            component.put("artifactDigest", artifactDigest);
            String definitionSnapshot = extensionDefinitionSnapshot(
                    definition, artifactDigest);
            component.put("definitionSnapshot", definitionSnapshot);
            component.put("definitionHash", sha256(definitionSnapshot));
            special.put("customComponent", component);
        }
    }

    private UiDataSourceService.ActionOperationDescriptor
            validatePinnedActionService(
            String ownerType,
            Map<String, Object> service,
            String label) {
        String snapshot = requireText(
                service.get("executableSnapshot"),
                1_048_576,
                label + "动作接口操作快照");
        String hash = requireText(
                service.get("definitionHash"),
                64,
                label + "动作接口操作哈希");
        Integer revision = service.get("serviceRevision") instanceof Number value
                ? value.intValue() : null;
        return dataSourceService.validatePinnedActionOperation(
                snapshot,
                hash,
                String.valueOf(service.get("serviceId")),
                String.valueOf(service.get("sourceCode")),
                revision,
                String.valueOf(service.get("operationCode")),
                ownerType);
    }

    /**
     * 发布时以宿主和目标实体的权威最新发布历史覆盖客户端
     * 传入值。快照同时保存 historyId 和内容指纹，运行时不再
     * 使用 latest 查询。
     */
    private void pinEntitySnapshots(
            Map<String, Object> config,
            Map<String, Object> target,
            String ownerEntityId) {
        Map<String, Object> source = requireMutableMap(
                config.get("source"), "来源内容");
        String sourceEntityId = StringUtils.hasText(ownerEntityId)
                ? ownerEntityId.trim()
                : blankToNull(source.get("entityId"));
        String targetEntityId = blankToNull(target.get("entityId"));
        if (!StringUtils.hasText(sourceEntityId)
                || !StringUtils.hasText(targetEntityId)) {
            throw new BusinessConflictException(
                    "UI_VIEW_COMPOSITION_ENTITY_SNAPSHOT_REQUIRED",
                    "关联内容缺少来源或目标实体，无法钉定发布定义");
        }
        EntityPublishedSnapshotService.PinnedEntitySnapshot sourcePinned =
                latestPinnedEntity(sourceEntityId, "来源实体");
        EntityPublishedSnapshotService.PinnedEntitySnapshot targetPinned =
                latestPinnedEntity(targetEntityId, "目标实体");
        EntityPublishedSnapshot sourceSnapshot = sourcePinned.snapshot();
        EntityPublishedSnapshot targetSnapshot = targetPinned.snapshot();

        // 来源身份必须以宿主归属为准，目标也以发布历史为准，
        // 避免伪造的 entityCode 进入签名令牌或权限检查。
        source.put("entityId", sourceSnapshot.getEntityId());
        source.put("entityCode", sourceSnapshot.getEntityCode());
        source.put("entityName", sourceSnapshot.getEntityName());
        target.put("entityId", targetSnapshot.getEntityId());
        target.put("entityCode", targetSnapshot.getEntityCode());
        target.put("entityName", targetSnapshot.getEntityName());
        config.put("source", source);
        config.put("target", target);

        Map<String, Object> snapshots = new LinkedHashMap<>();
        snapshots.put("source", entitySnapshotIdentity(sourcePinned));
        snapshots.put("target", entitySnapshotIdentity(targetPinned));
        config.put("entitySnapshots", snapshots);
    }

    private EntityPublishedSnapshotService.PinnedEntitySnapshot
            latestPinnedEntity(String entityId, String label) {
        try {
            EntityPublishedSnapshotService.PinnedEntitySnapshot pinned =
                    entitySnapshotService.getLatestPinnedByEntityId(entityId);
            if (pinned == null || pinned.snapshot() == null
                    || !Objects.equals(
                    entityId, pinned.snapshot().getEntityId())) {
                throw new IllegalStateException("返回身份不一致");
            }
            return pinned;
        } catch (RuntimeException exception) {
            throw new BusinessConflictException(
                    "UI_VIEW_COMPOSITION_ENTITY_UNPUBLISHED",
                    label + "尚未发布或发布历史不完整，无法发布关联内容");
        }
    }

    private Map<String, Object> entitySnapshotIdentity(
            EntityPublishedSnapshotService.PinnedEntitySnapshot pinned) {
        EntityPublishedSnapshot snapshot = pinned.snapshot();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("historyId", snapshot.getHistoryId());
        result.put("entityId", snapshot.getEntityId());
        result.put("entityCode", snapshot.getEntityCode());
        result.put("version", snapshot.getVersion());
        result.put("schemaHash", normalizeHash(pinned.schemaHash()));
        return result;
    }

    /**
     * 组件定义作为发布依赖固定，而不只保存可重复修改的
     * name/version。文档不包含组件运行代码，但覆盖平台对该版本的
     * 适用范围、Schema 和能力声明。
     */
    private String extensionDefinitionSnapshot(
            UiExtensionDefinition definition,
            String artifactDigest) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 2);
        snapshot.put("id", definition.getId());
        snapshot.put("extensionType", normalize(
                definition.getExtensionType()));
        snapshot.put("extensionKey", definition.getExtensionKey());
        snapshot.put("displayName", definition.getDisplayName());
        snapshot.put("version", definition.getVersion());
        snapshot.put("snapshotVersion",
                definition.getSnapshotVersion() == null
                        ? 1 : definition.getSnapshotVersion());
        snapshot.put("artifactDigest", artifactDigest);
        snapshot.put("visibilityScope", definition.getVisibilityScope());
        snapshot.put("entityCodesDocument", canonicalOrEmptyArray(
                definition.getEntityCodesDocument(), "组件适用实体"));
        snapshot.put("supportedModesDocument", canonicalOrEmptyArray(
                definition.getSupportedModesDocument(), "组件支持模式"));
        snapshot.put("supportedNodeTypesDocument", canonicalOrEmptyArray(
                definition.getSupportedNodeTypesDocument(), "组件支持节点"));
        snapshot.put("supportedBindingsDocument", canonicalOrEmptyArray(
                definition.getSupportedBindingsDocument(), "组件支持绑定"));
        snapshot.put("configSchemaDocument", canonicalOrEmptyObject(
                definition.getConfigSchemaDocument(), "组件配置 Schema"));
        snapshot.put("capabilitiesDocument", canonicalOrEmptyObject(
                definition.getCapabilitiesDocument(), "组件能力声明"));
        snapshot.put("status", normalize(definition.getStatus()));
        snapshot.put("revision", definition.getRevision());
        return codec.canonicalize(
                codec.write(snapshot, "自定义组件发布快照"),
                "自定义组件发布快照");
    }

    private String canonicalOrEmptyObject(
            String document,
            String label) {
        return StringUtils.hasText(document)
                ? codec.canonicalize(document, label) : "{}";
    }

    private String canonicalOrEmptyArray(
            String document,
            String label) {
        return StringUtils.hasText(document)
                ? codec.canonicalize(document, label) : "[]";
    }

    private UiConfigRelease activeTargetRelease(
            String contentType,
            String contentId) {
        String activeReleaseId;
        if ("FORM".equals(contentType)) {
            EntityForm form = formMapper.selectById(contentId);
            activeReleaseId = form == null ? null : form.getActiveReleaseId();
        } else if ("LIST".equals(contentType)) {
            EntityListConfig list = listMapper.selectById(contentId);
            activeReleaseId = list == null ? null : list.getActiveReleaseId();
        } else {
            throw new IllegalArgumentException("不支持的目标内容类型: " + contentType);
        }
        requirePublishedTarget(activeReleaseId, "关联内容目标尚未发布");
        UiConfigRelease release = releaseMapper.selectById(activeReleaseId);
        if (release == null
                || !contentType.equals(normalize(release.getConfigType()))
                || !contentId.equals(release.getConfigId())
                || release.getVersion() == null
                || !StringUtils.hasText(release.getContentHash())) {
            throw new BusinessConflictException(
                    "UI_VIEW_COMPOSITION_TARGET_RELEASE_INVALID",
                    "关联内容目标的激活发布记录不完整");
        }
        return release;
    }

    private void requirePublishedTarget(String activeReleaseId, String message) {
        if (!StringUtils.hasText(activeReleaseId)) {
            throw new BusinessConflictException(
                    "UI_VIEW_COMPOSITION_TARGET_UNPUBLISHED", message);
        }
    }

    private OwnerState requireOwner(
            String ownerType,
            String ownerId,
            boolean forUpdate) {
        String type = normalizeOwnerType(ownerType);
        String id = requireOwnerId(ownerId);
        if ("FORM".equals(type)) {
            accessService.requireFormAccess(id);
            EntityForm form = forUpdate
                    ? formMapper.selectByIdForUpdate(id)
                    : formMapper.selectById(id);
            if (form == null) {
                throw new IllegalArgumentException("表单不存在: " + id);
            }
            return new OwnerState(type, id, form.getEntityId(),
                    form.getRevision() == null ? 0 : form.getRevision());
        }
        accessService.requireListAccess(id);
        EntityListConfig list = forUpdate
                ? listMapper.selectByIdForUpdate(id)
                : listMapper.selectById(id);
        if (list == null) {
            throw new IllegalArgumentException("列表配置不存在: " + id);
        }
        return new OwnerState(type, id, list.getEntityId(),
                list.getRevision() == null ? 0 : list.getRevision());
    }

    private int touchOwner(OwnerState owner) {
        int nextRevision = owner.revision() + 1;
        if ("FORM".equals(owner.type())) {
            UpdateWrapper<EntityForm> update = new UpdateWrapper<>();
            update.eq("id", owner.id()).eq("deleted", 0);
            applyRevisionCondition(update, owner.revision());
            update.set("revision", nextRevision)
                    .set("draft_hash", null)
                    .set("update_time", LocalDateTime.now());
            if (formMapper.update(null, update) != 1) {
                throw ownerConflict(owner);
            }
        } else {
            UpdateWrapper<EntityListConfig> update = new UpdateWrapper<>();
            update.eq("id", owner.id()).eq("deleted", 0);
            applyRevisionCondition(update, owner.revision());
            update.set("revision", nextRevision)
                    .set("draft_hash", null)
                    .set("update_time", LocalDateTime.now());
            if (listMapper.update(null, update) != 1) {
                throw ownerConflict(owner);
            }
        }
        return nextRevision;
    }

    private <T> void applyRevisionCondition(
            UpdateWrapper<T> update,
            int revision) {
        if (revision == 0) {
            // 历史配置可能尚未初始化 revision；0 同时兼容 NULL 和显式 0。
            update.and(wrapper -> wrapper.isNull("revision").or().eq("revision", 0));
        } else {
            update.eq("revision", revision);
        }
    }

    private RevisionConflictException ownerConflict(OwnerState owner) {
        Object current = "FORM".equals(owner.type())
                ? formMapper.selectById(owner.id())
                : listMapper.selectById(owner.id());
        return new RevisionConflictException(
                "当前表单或列表已被其他人修改，请刷新后重试", current);
    }

    private UiViewComposition requireCompositionForUpdate(
            OwnerState owner,
            String id) {
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("关联内容ID不能为空");
        }
        UiViewComposition current = mapper.selectByIdForUpdate(id.trim());
        if (current == null) {
            throw new IllegalArgumentException("关联内容不存在: " + id);
        }
        if (!Objects.equals(owner.type(), current.getOwnerType())
                || !Objects.equals(owner.id(), current.getOwnerId())) {
            throw new IllegalArgumentException("关联内容不属于当前表单或列表");
        }
        return current;
    }

    private void requireRevision(
            Integer expectedRevision,
            UiViewComposition current) {
        if (expectedRevision == null) {
            throw new IllegalArgumentException("expectedRevision 不能为空");
        }
        if (!expectedRevision.equals(revisionOf(current))) {
            throw new RevisionConflictException(
                    "关联内容已被其他人修改，请刷新后重试",
                    toDto(current, null));
        }
    }

    /**
     * 更新和删除同时校验宿主草稿 revision，防止发布恢复造成子项 revision
     * 重用时出现 ABA 覆盖。宿主已经在读取时加锁，校验与后续写入位于同一
     * 串行化边界。
     */
    private void requireOwnerRevision(
            Integer expectedOwnerRevision,
            OwnerState owner) {
        if (expectedOwnerRevision == null) {
            throw new IllegalArgumentException(
                    "expectedOwnerRevision 不能为空");
        }
        if (!expectedOwnerRevision.equals(owner.revision())) {
            throw ownerConflict(owner);
        }
    }

    private RevisionConflictException compositionConflict(String id) {
        UiViewComposition current = mapper.selectById(id);
        return new RevisionConflictException(
                "关联内容已被其他人修改，请刷新后重试",
                current == null ? null : toDto(current, null));
    }

    private UiViewCompositionDTO toDto(
            UiViewComposition value,
            Integer ownerRevision) {
        if (value == null) {
            throw new IllegalStateException("关联内容保存后未能重新读取");
        }
        return UiViewCompositionDTO.builder()
                .id(value.getId())
                .ownerType(value.getOwnerType())
                .ownerId(value.getOwnerId())
                .compositionKey(value.getCompositionKey())
                .anchorType(value.getAnchorType())
                .anchorKey(value.getAnchorKey())
                .config(readConfig(value.getConfigDocument()))
                .orderKey(value.getOrderKey())
                .revision(value.getRevision())
                .ownerRevision(ownerRevision)
                .createdAt(value.getCreatedAt())
                .updatedAt(value.getUpdatedAt())
                .build();
    }

    private long nextOrderKey(String ownerType, String ownerId) {
        long max = mapper.findByOwner(ownerType, ownerId).stream()
                .map(UiViewComposition::getOrderKey)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);
        if (max > Long.MAX_VALUE - ORDER_STEP) {
            throw new IllegalStateException("关联内容排序键已达到上限");
        }
        return max + ORDER_STEP;
    }

    private int revisionOf(UiViewComposition value) {
        return value.getRevision() == null ? 0 : value.getRevision();
    }

    private String normalizeOwnerType(String value) {
        String normalized = normalize(value);
        if (!OWNER_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("关联内容宿主只支持 FORM 或 LIST");
        }
        return normalized;
    }

    private String requireOwnerId(String value) {
        return requireText(value, 64, "宿主ID");
    }

    private String normalizeCompositionKey(Object value) {
        String key = requireText(value, 100, "关联内容编码");
        if (!COMPOSITION_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException(
                    "关联内容编码必须以字母开头，且只能包含字母、数字、点、横线或下划线");
        }
        return key;
    }

    private String normalizeAnchorType(Object value) {
        String type = value == null || !StringUtils.hasText(String.valueOf(value))
                ? "OWNER" : normalize(String.valueOf(value));
        // 前端早期草稿使用 FORM_END；持久化时统一收敛为用户概念更少的 OWNER。
        if ("FORM_END".equals(type)) {
            return "OWNER";
        }
        if (!ANCHOR_TYPES.contains(type)) {
            throw new IllegalArgumentException("不支持的关联内容挂载位置: " + value);
        }
        return type;
    }

    private String normalizeAnchorKey(String anchorType, Object value) {
        if ("OWNER".equals(anchorType)) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                throw new IllegalArgumentException(
                        anchorType + " 挂载位置不能指定 anchorKey");
            }
            return null;
        }
        return requireText(value, 160, "挂载位置");
    }

    /**
     * 校验宿主、挂载点和页面呈现方式属于同一语义域，避免配置保存成功后在
     * 另一个设计器中无法找到挂载位置。
     */
    private void validateAnchorPlacement(
            String ownerType,
            String anchorType,
            Map<String, Object> config) {
        Map<String, Object> presentation = requireMap(
                config.get("presentation"), "显示方式");
        String position = String.valueOf(presentation.get("position"));
        if ("FORM".equals(ownerType)) {
            if (!Set.of("OWNER", "FORM_NODE").contains(anchorType)) {
                throw new IllegalArgumentException(
                        "表单关联内容只支持页面末尾或表单节点挂载位置");
            }
            if ("FORM_NODE".equals(anchorType)
                    && !Set.of("INLINE", "TAB").contains(position)) {
                throw new IllegalArgumentException(
                        "表单节点挂载只适用于嵌入当前页面或 Tab 显示");
            }
            if ("TAB".equals(position)
                    && !"FORM_NODE".equals(anchorType)) {
                throw new IllegalArgumentException(
                        "表单 Tab 显示必须选择一个实际的 Tab 页作为放置位置");
            }
            return;
        }
        if (!Set.of("PAGE_SECTION", "ROW_EXPAND",
                "TOOLBAR_ACTION", "ROW_ACTION").contains(anchorType)) {
            throw new IllegalArgumentException(
                    "列表关联内容必须选择页面区块、行展开、工具栏或行操作挂载位置");
        }
        boolean compatible = switch (anchorType) {
            case "PAGE_SECTION" -> "INLINE".equals(position);
            case "ROW_EXPAND" -> "ROW_EXPAND".equals(position);
            case "TOOLBAR_ACTION", "ROW_ACTION" ->
                    Set.of("DIALOG", "DRAWER", "PAGE").contains(position);
            default -> false;
        };
        if (!compatible) {
            throw new IllegalArgumentException(
                    "列表挂载位置与显示方式不匹配，请重新选择显示位置");
        }
    }

    private long requireOrderKey(Object value) {
        if (!(value instanceof Number number)
                || number.doubleValue() != number.longValue()
                || number.longValue() < 0) {
            throw new IllegalArgumentException("关联内容排序键必须为非负整数");
        }
        return number.longValue();
    }

    private String requireText(Object value, int maxLength, String label) {
        if (!(value instanceof String text) || !StringUtils.hasText(text)) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        String normalized = text.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    label + "长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private Map<String, Object> readConfig(String document) {
        return codec.readObject(document, "关联内容配置");
    }

    private String write(Map<String, Object> config) {
        return codec.write(config, "关联内容配置");
    }

    private Map<String, Object> requireMap(Object value, String label) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(label + "必须为对象");
        }
        return stringMap(map);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireMutableMap(Object value, String label) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(label + "必须为对象");
        }
        if (value instanceof LinkedHashMap<?, ?>) {
            return (Map<String, Object>) value;
        }
        return stringMap(map);
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private String normalize(String value) {
        return value == null ? ""
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private record OwnerState(
            String type,
            String id,
            String entityId,
            int revision) {
    }

    private record ValidatedDraft(
            String compositionKey,
            String anchorType,
            String anchorKey,
            Map<String, Object> config,
            Long orderKey) {
    }

    private record PinnedSchemas(
            EntityPublishedSnapshot source,
            EntityPublishedSnapshot target) {
    }

    private record InterfaceTestResolution(
            long matchedCount,
            List<String> targetRecordIds,
            Map<String, Object> filters,
            String description) {
    }

    private record FilterResolution(
            Map<String, Object> filters,
            boolean ready,
            String description) {

        private static FilterResolution notReady(String description) {
            return new FilterResolution(Map.of(), false, description);
        }
    }
}
