package com.workflow.entity.ui.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.workflow.contracts.entity.ui.model.UiExtensionCatalogItem;
import com.workflow.contracts.entity.ui.port.UiExtensionCatalogPort;
import com.workflow.core.error.RevisionConflictException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.ui.api.request.UiExtensionDefinitionSaveRequest;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * UI 扩展组件定义服务，负责扩展注册、查询、版本管理和兼容性校验。
 *
 * <p>该物理表保存 FORM、NODE、FIELD、LIST 和 INTERFACE 类型；每个扩展以
 * key+version 唯一标识。UI 组件声明运行模式、节点与绑定兼容范围，INTERFACE
 * 则由专用服务校验实现方式、作用域与输入输出契约。管理端聚合展示的流程动作、
 * 人员解析器来自各自能力目录，并不作为本表类型持久化。</p>
 */
@Service
@RequiredArgsConstructor
public class UiExtensionDefinitionService implements UiExtensionCatalogPort {

    /** 本服务负责写入的 UI 组件扩展类型；接口扩展由专用服务维护。 */
    private static final Set<String> UI_TYPES =
            Set.of("FORM", "NODE", "FIELD", "LIST");
    /** 允许的扩展状态。 */
    private static final Set<String> STATUSES =
            Set.of("ACTIVE", "DISABLED");
    /** 允许的运行模式。 */
    private static final Set<String> MODES =
            Set.of("CREATE", "EDIT", "APPROVE", "VIEW");
    /** UI 表单扩展支持的实体范围。 */
    private static final Set<String> VISIBILITY_SCOPES =
            Set.of("GLOBAL", "ENTITY");
    private static final Pattern KEY =
            Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,254}");

    private final UiExtensionDefinitionMapper mapper;
    private final EntityDefinitionMapper entityDefinitionMapper;
    private final JsonDocumentCodec codec;

    /**
     * 按类型、key 和状态查询扩展定义列表。
     *
     * @param extensionType 扩展类型，为空忽略
     * @param extensionKey  扩展 key，为空忽略
     * @param status        状态，为空忽略
     * @return 扩展定义列表
     */
    public List<UiExtensionDefinition> list(
            String extensionType,
            String extensionKey,
            String status) {
        return list(extensionType, extensionKey, status, null, null, null);
    }

    /**
     * 查询统一扩展目录；作用域和实现类型过滤仅对 INTERFACE 条目生效。
     *
     * @param extensionType 扩展类型标识，决定后续界面扩展定义采用的处理分支
     * @param extensionKey 扩展键，后续用于授权校验、关联或幂等去重
     * @param status 状态标识，决定后续界面扩展定义采用的处理分支
     * @param scopeType 作用域类型标识，决定后续界面扩展定义采用的处理分支
     * @param scopeId 作用域ID，后续用于列出界面扩展定义时定位或关联目标
     * @param implementationType 实现类型标识，决定后续界面扩展定义采用的处理分支
     * @return 界面扩展定义集合，供调用方遍历或展示
     */
    public List<UiExtensionDefinition> list(
            String extensionType,
            String extensionKey,
            String status,
            String scopeType,
            String scopeId,
            String implementationType) {
        LambdaQueryWrapper<UiExtensionDefinition> query =
                new LambdaQueryWrapper<>();
        if (StringUtils.hasText(extensionType)) {
            query.eq(
                    UiExtensionDefinition::getExtensionType,
                    normalize(extensionType));
        }
        if (StringUtils.hasText(extensionKey)) {
            query.eq(
                    UiExtensionDefinition::getExtensionKey,
                    extensionKey.trim());
        }
        if (StringUtils.hasText(status)) {
            query.eq(
                    UiExtensionDefinition::getStatus,
                    normalize(status));
        }
        if (StringUtils.hasText(scopeType)) {
            query.eq(UiExtensionDefinition::getScopeType,
                    normalize(scopeType));
        }
        if (StringUtils.hasText(scopeId)) {
            query.eq(UiExtensionDefinition::getScopeId, scopeId.trim());
        }
        if (StringUtils.hasText(implementationType)) {
            query.eq(UiExtensionDefinition::getImplementationType,
                    normalize(implementationType));
        }
        return mapper.selectList(query
                .eq(UiExtensionDefinition::getDeleted, 0)
                .orderByAsc(UiExtensionDefinition::getExtensionType)
                .orderByAsc(UiExtensionDefinition::getExtensionKey)
                .orderByDesc(UiExtensionDefinition::getVersion));
    }

    /**
     * 向管理模块提供结构化的 UI 扩展目录，不暴露实体持久化对象。
     *
     * @return 界面扩展目录条目集合，供调用方遍历或展示
     */
    @Override
    public List<UiExtensionCatalogItem> listCatalogItems() {
        return list(null, null, null).stream()
                .map(this::toCatalogItem)
                .toList();
    }

    /**
     * 校验并返回指定版本的活跃扩展定义。
     *
     * @param extensionType 扩展类型
     * @param extensionKey  扩展 key
     * @param version       扩展版本，必须大于 0
     * @return 扩展定义，key 为空返回 null
     * @throws IllegalArgumentException 版本未指定或扩展不存在、已禁用时抛出
     */
    public UiExtensionDefinition requireActive(
            String extensionType,
            String extensionKey,
            Integer version) {
        if (!StringUtils.hasText(extensionKey)) {
            return null;
        }
        if (version == null || version < 1) {
            throw new IllegalArgumentException(
                    "扩展组件必须锁定明确版本: " + extensionKey);
        }
        UiExtensionDefinition definition = mapper.selectOne(
                new LambdaQueryWrapper<UiExtensionDefinition>()
                        .eq(
                                UiExtensionDefinition::getExtensionType,
                                normalize(extensionType))
                        .eq(
                                UiExtensionDefinition::getExtensionKey,
                                extensionKey.trim())
                        .eq(UiExtensionDefinition::getVersion, version)
                        .eq(UiExtensionDefinition::getStatus, "ACTIVE")
                        .eq(UiExtensionDefinition::getDeleted, 0));
        if (definition == null) {
            throw new IllegalArgumentException(
                    "扩展组件未注册、已禁用或版本不存在: "
                            + extensionKey + "@" + version);
        }
        return definition;
    }

    /**
     * 新增或更新扩展定义，基于乐观锁更新。
     *
     * @param request 保存请求
     * @return 保存后的扩展定义
     * @throws IllegalArgumentException   类型、key、名称或版本等不合法时抛出
     * @throws RevisionConflictException  版本冲突时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public UiExtensionDefinition save(
            UiExtensionDefinitionSaveRequest request) {
        UiExtensionDefinition current = existingForUpdate(request);
        String requestedType = request == null
                ? "" : normalize(request.getExtensionType());
        if (current != null) {
            requireStableIdentity(current, requestedType,
                    request.getExtensionKey());
        }
        validate(request);
        if (current != null
                && !current.getRevision().equals(request.getExpectedRevision())) {
            throw new RevisionConflictException(
                    "扩展定义已被其他人修改，请刷新后重试",
                    current);
        }
        UiExtensionDefinition value =
                current == null ? new UiExtensionDefinition() : current;
        value.setExtensionType(normalize(request.getExtensionType()));
        value.setExtensionKey(request.getExtensionKey().trim());
        value.setDisplayName(request.getDisplayName().trim());
        value.setVersion(request.getVersion());
        value.setSnapshotVersion(
                request.getSnapshotVersion() == null
                        ? 1 : request.getSnapshotVersion());
        String visibilityScope = visibilityScope(request);
        List<String> entityCodes = resolveEntityCodes(
                request, visibilityScope);
        value.setVisibilityScope(visibilityScope);
        value.setEntityCodesDocument(write(
                entityCodes,
                "扩展适用实体"));
        value.setSupportedModesDocument(write(
                normalizeList(request.getSupportedModes()),
                "扩展支持模式"));
        value.setSupportedNodeTypesDocument(write(
                normalizeList(request.getSupportedNodeTypes()),
                "扩展支持节点类型"));
        value.setSupportedBindingsDocument(write(
                normalizeList(request.getSupportedBindings()),
                "扩展支持绑定类型"));
        value.setConfigSchemaDocument(write(
                request.getConfigSchema(), "扩展配置Schema"));
        value.setCapabilitiesDocument(write(
                request.getCapabilities(), "扩展能力"));
        value.setStatus(StringUtils.hasText(request.getStatus())
                ? normalize(request.getStatus()) : "ACTIVE");
        value.setUpdatedAt(LocalDateTime.now());
        value.setDeleted(0);
        if (current == null) {
            value.setRevision(1);
            value.setCreatedAt(LocalDateTime.now());
            mapper.insert(value);
        } else {
            int nextRevision = current.getRevision() + 1;
            UpdateWrapper<UiExtensionDefinition> update =
                    new UpdateWrapper<>();
            update.eq("id", current.getId())
                    .eq("revision", current.getRevision())
                    .eq("deleted", 0)
                    .set("extension_type", value.getExtensionType())
                    .set("extension_key", value.getExtensionKey())
                    .set("display_name", value.getDisplayName())
                    .set("version", value.getVersion())
                    .set("snapshot_version", value.getSnapshotVersion())
                    .set("visibility_scope", value.getVisibilityScope())
                    .set("entity_codes_document", value.getEntityCodesDocument())
                    .set("supported_modes_document", value.getSupportedModesDocument())
                    .set("supported_node_types_document", value.getSupportedNodeTypesDocument())
                    .set("supported_bindings_document", value.getSupportedBindingsDocument())
                    .set("config_schema_document", value.getConfigSchemaDocument())
                    .set("capabilities_document", value.getCapabilitiesDocument())
                    .set("status", value.getStatus())
                    .set("revision", nextRevision)
                    .set("update_time", value.getUpdatedAt());
            if (mapper.update(null, update) != 1) {
                throw new RevisionConflictException(
                        "扩展定义已被其他人修改，请刷新后重试",
                        mapper.selectById(current.getId()));
            }
        }
        return mapper.selectById(value.getId());
    }

    /**
     * 按请求路径 ID 读取更新目标。带 ID 的保存只能更新既有记录，不能因记录
     * 不存在而退化为新增，避免客户端使用陈旧 ID 意外创建第二条扩展。
     *
     * @param request 本次请求，后续经校验后用于处理已有更新
     * @return 处理后的已有更新结果，供调用方继续处理
     */
    private UiExtensionDefinition existingForUpdate(
            UiExtensionDefinitionSaveRequest request) {
        if (request == null || !StringUtils.hasText(request.getId())) {
            return null;
        }
        UiExtensionDefinition current = mapper.selectById(
                request.getId().trim());
        if (current == null || Integer.valueOf(1).equals(
                current.getDeleted())) {
            throw new IllegalArgumentException("扩展定义不存在");
        }
        return current;
    }

    /**
     * 扩展类型和 key 是持久化身份的一部分。更新时只允许修改展示、配置和状态，
     * 禁止借保存接口把 UI 组件转换为接口扩展，或把稳定 key 重命名。
     *
     * @param current 当前，供本方法校验并获取稳定身份时使用
     * @param requestedType 请求类型标识，决定后续稳定身份采用的处理分支
     * @param requestedKey 请求键，后续用于授权校验、关联或幂等去重
     */
    private void requireStableIdentity(
            UiExtensionDefinition current,
            String requestedType,
            String requestedKey) {
        if (!normalize(current.getExtensionType()).equals(requestedType)) {
            throw new IllegalArgumentException("更新时不能修改扩展类型");
        }
        String normalizedKey = StringUtils.hasText(requestedKey)
                ? requestedKey.trim() : "";
        if (!String.valueOf(current.getExtensionKey()).equals(
                normalizedKey)) {
            throw new IllegalArgumentException("更新时不能修改扩展注册名");
        }
    }

    /**
     * 校验扩展与运行模式、节点类型、绑定类型和快照版本的兼容性。
     *
     * @param definition     扩展定义
     * @param mode           运行模式
     * @param nodeType       节点类型
     * @param bindingType    绑定类型
     * @param snapshotVersion 配置快照版本
     * @throws IllegalArgumentException 任一维度不兼容时抛出
     */
    public void validateCompatibility(
            UiExtensionDefinition definition,
            String mode,
            String nodeType,
            String bindingType,
            Integer snapshotVersion) {
        requireSupported(
                definition.getSupportedModesDocument(),
                mode,
                "运行模式");
        requireSupported(
                definition.getSupportedNodeTypesDocument(),
                nodeType,
                "节点类型");
        requireSupported(
                definition.getSupportedBindingsDocument(),
                bindingType,
                "绑定类型");
        int configuredSnapshot =
                snapshotVersion == null ? 1 : snapshotVersion;
        if (configuredSnapshot > definition.getSnapshotVersion()) {
            throw new IllegalArgumentException(
                    "扩展配置快照版本高于服务端注册版本: "
                            + definition.getExtensionKey());
        }
    }

    /**
     * 校验表单扩展是否允许用于指定实体。
     *
     * @param definition 扩展定义
     * @param entityCode 当前表单所属实体编码
     */
    public void validateEntityScope(
            UiExtensionDefinition definition,
            String entityCode) {
        if (definition == null
                || !"ENTITY".equals(normalize(
                        definition.getVisibilityScope()))) {
            return;
        }
        Set<String> entityCodes = readStringSet(
                definition.getEntityCodesDocument(),
                "扩展适用实体");
        boolean matched = StringUtils.hasText(entityCode)
                && entityCodes.stream().anyMatch(configured ->
                        configured.equalsIgnoreCase(entityCode.trim()));
        if (!matched) {
            throw new IllegalArgumentException(
                    "扩展组件不适用于当前实体: "
                            + definition.getExtensionKey()
                            + " -> " + entityCode);
        }
    }

    /**
     * 判断扩展是否显式声明兼容热修复。
     *
     * <p>未声明、无法解析或声明为 false 时一律按不兼容处理。</p>
     *
     * @param definition 定义，作为 {@code codec.readObject} 的输入影响后续处理
     * @return 热修复条件成立时为 true，否则为 false
     */
    public boolean supportsHotfix(UiExtensionDefinition definition) {
        if (definition == null
                || !StringUtils.hasText(
                        definition.getCapabilitiesDocument())) {
            return false;
        }
        try {
            Map<String, Object> capabilities = codec.readObject(
                    definition.getCapabilitiesDocument(),
                    "扩展能力");
            if (Boolean.TRUE.equals(
                    capabilities.get("hotfixCompatible"))) {
                return true;
            }
            Object hotfix = capabilities.get("hotfix");
            return hotfix instanceof Map<?, ?> values
                    && Boolean.TRUE.equals(values.get("compatible"));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * 校验界面扩展定义；不满足约束时阻止后续处理。
     *
     * @param request 本次请求，后续经校验后用于校验界面扩展定义
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validate(UiExtensionDefinitionSaveRequest request) {
        if (request == null
                || !UI_TYPES.contains(normalize(request.getExtensionType()))) {
            throw new IllegalArgumentException("扩展类型不合法");
        }
        if (!StringUtils.hasText(request.getExtensionKey())
                || !KEY.matcher(request.getExtensionKey()).matches()) {
            throw new IllegalArgumentException("扩展注册名不合法");
        }
        if (!StringUtils.hasText(request.getDisplayName())) {
            throw new IllegalArgumentException("扩展显示名称不能为空");
        }
        if (request.getVersion() == null || request.getVersion() < 1) {
            throw new IllegalArgumentException("扩展版本必须大于 0");
        }
        if (request.getSnapshotVersion() != null
                && request.getSnapshotVersion() < 1) {
            throw new IllegalArgumentException("快照版本必须大于 0");
        }
        String status = StringUtils.hasText(request.getStatus())
                ? normalize(request.getStatus()) : "ACTIVE";
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("扩展状态不合法");
        }
        for (String mode : normalizeList(request.getSupportedModes())) {
            if (!MODES.contains(mode)) {
                throw new IllegalArgumentException(
                        "扩展运行模式不合法: " + mode);
            }
        }
        String visibilityScope = visibilityScope(request);
        if (!VISIBILITY_SCOPES.contains(visibilityScope)) {
            throw new IllegalArgumentException("扩展适用范围不合法");
        }
        if ("ENTITY".equals(visibilityScope)
                && normalizeEntityCodes(request.getEntityCodes()).isEmpty()) {
            throw new IllegalArgumentException(
                    "指定实体范围至少选择一个实体");
        }
    }

    /**
     * 生成{@code visibility}作用域文本，供后续匹配或展示。
     *
     * @param request 本次请求，后续经校验后用于处理{@code visibility}作用域
     * @return 处理后的{@code visibility}作用域文本，供调用方比较或展示
     */
    private String visibilityScope(
            UiExtensionDefinitionSaveRequest request) {
        if (!"FORM".equals(normalize(request.getExtensionType()))) {
            return "GLOBAL";
        }
        return StringUtils.hasText(request.getVisibilityScope())
                ? normalize(request.getVisibilityScope())
                : "GLOBAL";
    }

    /**
     * 解析实体编码集合；输出作为后续校验或处理的输入。
     *
     * @param request 本次请求，后续经校验后用于解析实体编码集合
     * @param visibilityScope {@code visibility}作用域，供本方法解析实体编码集合时使用
     * @return 界面扩展定义集合，供调用方遍历或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private List<String> resolveEntityCodes(
            UiExtensionDefinitionSaveRequest request,
            String visibilityScope) {
        if (!"ENTITY".equals(visibilityScope)) {
            return List.of();
        }
        return normalizeEntityCodes(request.getEntityCodes()).stream()
                .map(code -> {
                    EntityDefinition entity = entityDefinitionMapper
                            .findByEntityCode(code)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "适用实体不存在: " + code));
                    if (entity.getStatus()
                            != EntityDefinition.Status.PUBLISHED) {
                        throw new IllegalArgumentException(
                                "适用实体尚未发布: "
                                        + entity.getEntityCode());
                    }
                    EntityDefinition.StorageMode storageMode =
                            entity.getStorageMode() == null
                                    ? EntityDefinition.StorageMode.DYNAMIC
                                    : entity.getStorageMode();
                    if (storageMode != EntityDefinition.StorageMode.DYNAMIC) {
                        throw new IllegalArgumentException(
                                "系统实体不支持整表单自定义组件: "
                                        + entity.getEntityCode());
                    }
                    return entity.getEntityCode();
                })
                .toList();
    }

    /**
     * 规范化实体编码集合；输出作为后续校验或处理的输入。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 界面扩展定义集合，供调用方遍历或展示
     */
    private List<String> normalizeEntityCodes(List<String> values) {
        if (values == null) {
            return List.of();
        }
        Map<String, String> unique = new java.util.LinkedHashMap<>();
        values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(value -> unique.putIfAbsent(
                        value.toLowerCase(Locale.ROOT), value));
        return List.copyOf(unique.values());
    }

    /**
     * 校验并获取{@code supported}；不满足约束时阻止后续处理。
     *
     * @param document 文档，作为 {@code codec.read} 的输入影响后续处理
     * @param configured 已配置，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param label 标签，后续用于校验并获取{@code supported}时匹配或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void requireSupported(
            String document,
            String configured,
            String label) {
        if (!StringUtils.hasText(configured)
                || !StringUtils.hasText(document)) {
            return;
        }
        List<String> supported = codec.read(
                document,
                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {},
                "扩展兼容范围");
        if (!supported.isEmpty()
                && !supported.contains(normalize(configured))) {
            throw new IllegalArgumentException(
                    "扩展不支持当前" + label + ": " + configured);
        }
    }

    /**
     * 规范化界面扩展定义列表；输出作为后续校验或处理的输入。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 界面扩展定义集合，供调用方遍历或展示
     */
    private List<String> normalizeList(List<String> values) {
        return values == null
                ? List.of()
                : values.stream()
                        .filter(StringUtils::hasText)
                        .map(this::normalize)
                        .distinct()
                        .toList();
    }

    /**
     * 写入界面扩展定义；后续读取或执行将使用更新后的状态。
     *
     * @param value 待写入界面扩展定义的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于写入界面扩展定义时匹配或展示
     * @return 写入后的界面扩展定义文本，供调用方比较或展示
     */
    private String write(Object value, String label) {
        if (value == null
                || value instanceof Map<?, ?> map && map.isEmpty()
                || value instanceof List<?> list && list.isEmpty()) {
            return null;
        }
        return codec.write(value, label);
    }

    /**
     * 转换为目录条目；输出作为后续校验或处理的输入。
     *
     * @param definition 定义，作为 {@code UiExtensionCatalogItem} 的输入影响后续处理
     * @return 转换为后的目录条目结果，供调用方继续处理
     */
    private UiExtensionCatalogItem toCatalogItem(
            UiExtensionDefinition definition) {
        return new UiExtensionCatalogItem(
                definition.getId(),
                definition.getExtensionType(),
                definition.getExtensionKey(),
                definition.getDisplayName(),
                definition.getVersion(),
                definition.getSnapshotVersion(),
                definition.getStatus(),
                StringUtils.hasText(definition.getVisibilityScope())
                        ? normalize(definition.getVisibilityScope())
                        : "GLOBAL",
                readStringSet(
                        definition.getEntityCodesDocument(),
                        "扩展适用实体"),
                readStringSet(
                        definition.getSupportedModesDocument(),
                        "扩展支持模式"),
                readStringSet(
                        definition.getSupportedNodeTypesDocument(),
                        "扩展支持节点类型"),
                readStringSet(
                        definition.getSupportedBindingsDocument(),
                        "扩展支持绑定类型"),
                readDocument(
                        definition.getConfigSchemaDocument(),
                        "扩展配置Schema"),
                readMap(
                        definition.getCapabilitiesDocument(),
                        "扩展能力"),
                definition.getImplementationType(),
                definition.getProviderCode(),
                definition.getScopeType(),
                definition.getScopeId(),
                definition.getInterfaceKind(),
                definition.getInterfaceContextType(),
                readMap(
                        definition.getImplementationConfigDocument(),
                        "接口实现配置"),
                readMap(
                        definition.getExecutionPolicyDocument(),
                        "接口执行策略"),
                readMap(
                        definition.getInputSchemaDocument(),
                        "接口输入Schema"),
                readMap(
                        definition.getOutputSchemaDocument(),
                        "接口输出Schema"),
                definition.getRevision());
    }

    /**
     * 读取字符串设置；查询结果供调用方展示或继续处理。
     *
     * @param document 文档，作为 {@code codec.read} 的输入影响后续处理
     * @param label 标签，后续用于读取字符串设置时匹配或展示
     * @return 界面扩展定义集合，供调用方遍历或展示
     */
    private Set<String> readStringSet(String document, String label) {
        if (!StringUtils.hasText(document)) {
            return Set.of();
        }
        try {
            return new LinkedHashSet<>(codec.read(
                    document,
                    new TypeReference<List<String>>() {},
                    label));
        } catch (IllegalArgumentException exception) {
            return Set.of();
        }
    }

    /**
     * 读取文档；查询结果供调用方展示或继续处理。
     *
     * @param document 文档，作为 {@code codec.read} 的输入影响后续处理
     * @param label 标签，后续用于读取文档时匹配或展示
     * @return 读取后的文档结果，供调用方继续处理
     */
    private Object readDocument(String document, String label) {
        if (!StringUtils.hasText(document)) {
            return Map.of();
        }
        try {
            return codec.read(document, label);
        } catch (IllegalArgumentException exception) {
            return Map.of();
        }
    }

    /**
     * 读取键值配置，供后续规则或接口处理使用。
     *
     * @param document 文档，作为 {@code codec.readObject} 的输入影响后续处理
     * @param label 标签，后续用于读取映射时匹配或展示
     * @return 映射键值结果，供调用方继续处理
     */
    private Map<String, Object> readMap(String document, String label) {
        if (!StringUtils.hasText(document)) {
            return Map.of();
        }
        try {
            return codec.readObject(document, label);
        } catch (IllegalArgumentException exception) {
            return Map.of();
        }
    }

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param value 待规范化界面扩展定义的原始输入，结果供调用方继续使用
     * @return 规范化后的界面扩展定义文本，供调用方比较或展示
     */
    private String normalize(String value) {
        return value == null
                ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
