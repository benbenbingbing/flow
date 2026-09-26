package com.workflow.entity.ui.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.workflow.core.error.RevisionConflictException;
import com.workflow.core.database.jdbc.JdbcWriteAttempt;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.api.request.UiComponentTemplateSaveRequest;
import com.workflow.entity.ui.api.request.UiComponentTemplateUpgradeRequest;
import com.workflow.entity.ui.infrastructure.persistence.record.UiComponentTemplate;
import com.workflow.entity.ui.infrastructure.persistence.record.UiComponentTemplateVersion;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiComponentTemplateMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiComponentTemplateVersionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * UI 组件模板服务，负责模板的创建、修订存储、完整性校验和三向合并升级。
 *
 * <p>模板以 key 唯一标识，每次保存生成不可变快照版本并计算内容哈希，
 * 升级时对基线、本地与目标版本执行三向合并，输出冲突列表供人工确认。
 * 列表列模板是一次性初始化模板，只允许读取当前快照，不暴露版本历史，
 * 也不允许执行升级。</p>
 */
@Service
@RequiredArgsConstructor
public class UiComponentTemplateService {

    /** 允许的模板类型。 */
    private static final Set<String> TEMPLATE_TYPES = Set.of(
            "LIST_COLUMN_GROUP", "BUTTON_GROUP");

    /** 列模板不得携带具体列表字段身份与排序信息。 */
    private static final Set<String> LIST_COLUMN_IDENTITY_KEYS = Set.of(
            "id",
            "fieldId",
            "fieldCode",
            "fieldName",
            "sortOrder",
            "orderKey",
            "revision",
            "templateId",
            "templateVersion",
            "localOverridesDocument");

    private final UiComponentTemplateMapper templateMapper;
    private final UiComponentTemplateVersionMapper versionMapper;
    private final JsonDocumentCodec codec;
    /** 只用于把不可变历史列模板中的 service + operation 引用解析为新扩展 ID。 */
    private final UiExtensionDefinitionMapper extensionMapper;
    private final JdbcWriteAttempt writeAttempt;

    /**
     * 按类型查询模板列表。
     *
     * @param templateType 模板类型，为空查询全部
     * @return 模板列表
     */
    public List<UiComponentTemplate> list(String templateType) {
        LambdaQueryWrapper<UiComponentTemplate> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(templateType)) {
            query.eq(UiComponentTemplate::getTemplateType,
                    templateType.trim().toUpperCase());
        }
        query.eq(UiComponentTemplate::getDeleted, 0)
                .orderByAsc(UiComponentTemplate::getTemplateKey);
        return templateMapper.selectList(query);
    }

    /**
     * 查询模板的所有版本并校验每个版本的完整性。
     *
     * @param templateId 模板ID
     * @return 版本列表
     */
    public List<UiComponentTemplateVersion> versions(String templateId) {
        UiComponentTemplate template = requireTemplate(templateId);
        if (isInitializationOnlyTemplate(template)) {
            throw new IllegalArgumentException(
                    "列表列模板仅用于一次性初始化，不提供版本历史");
        }
        List<UiComponentTemplateVersion> versions =
                versionMapper.findByTemplateId(templateId);
        versions.forEach(this::verifyVersionIntegrity);
        return versions;
    }

    /**
     * 读取模板当前快照，不向调用方暴露内部修订号。
     *
     * @param templateId 模板ID
     * @return 当前模板快照
     */
    public Map<String, Object> currentSnapshot(String templateId) {
        UiComponentTemplate template = requireTemplate(templateId);
        Integer currentRevision = template.getCurrentVersion();
        if (currentRevision == null || currentRevision < 1) {
            throw new IllegalArgumentException("模板当前快照不存在");
        }
        Map<String, Object> current = snapshot(templateId, currentRevision);
        if (!isInitializationOnlyTemplate(template)) {
            return current;
        }
        // 先校验原文哈希、再转换返回副本；数据库快照及 content_hash 始终保持不变。
        return normalizeLegacyInterfaceReferences(current);
    }

    /**
     * 将历史列表列模板中的“服务 + 操作”二级引用转换为单一接口扩展 ID。
     *
     * <p>该方法仅作用于通过完整性校验后反序列化出的返回值，绝不更新历史
     * {@code snapshot_document}，也不重算 {@code content_hash}。</p>
     *
     * @param snapshot 快照，作为 {@code stringMap} 的输入影响后续处理
     * @return 旧版接口引用键值结果，供调用方继续处理
     */
    private Map<String, Object> normalizeLegacyInterfaceReferences(
            Map<String, Object> snapshot) {
        return stringMap(normalizeLegacyInterfaceValue(snapshot));
    }

    /**
     * 规范化旧版接口值；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化旧版接口值的原始输入，结果供调用方继续使用
     * @return 规范化后的旧版接口值结果，供调用方继续处理
     */
    private Object normalizeLegacyInterfaceValue(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, child) -> result.put(
                    String.valueOf(key),
                    normalizeLegacyInterfaceValue(child)));
            normalizeLegacyPair(result,
                    "dataSourceId", "dataSourceOperationCode",
                    "interfaceExtensionId");
            normalizeLegacyPair(result,
                    "queryDataSourceId", "queryOperationCode",
                    "queryInterfaceExtensionId");
            normalizeLegacyPair(result,
                    "serviceId", "operationCode", "extensionId");
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(this::normalizeLegacyInterfaceValue)
                    .toList();
        }
        return value;
    }

    /**
     * 规范化旧版{@code pair}；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化旧版{@code pair}的原始输入，结果供调用方继续使用
     * @param legacyIdKey 旧版ID键，后续用于授权校验、关联或幂等去重
     * @param legacyOperationKey 旧版操作键，后续用于授权校验、关联或幂等去重
     * @param extensionIdKey 扩展ID键，后续用于授权校验、关联或幂等去重
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void normalizeLegacyPair(
            Map<String, Object> value,
            String legacyIdKey,
            String legacyOperationKey,
            String extensionIdKey) {
        String legacyId = text(value.get(legacyIdKey));
        String operationCode = text(value.get(legacyOperationKey));
        if (!StringUtils.hasText(legacyId)
                || !StringUtils.hasText(operationCode)) {
            return;
        }
        UiExtensionDefinition definition = extensionMapper.selectById(
                legacyId.trim());
        if (!isMatchingInterface(definition, operationCode)) {
            definition = extensionMapper.selectOne(
                    new LambdaQueryWrapper<UiExtensionDefinition>()
                            .eq(UiExtensionDefinition::getExtensionType,
                                    "INTERFACE")
                            .eq(UiExtensionDefinition::getLegacyServiceId,
                                    legacyId.trim())
                            .eq(UiExtensionDefinition::getProviderOperationCode,
                                    operationCode.trim())
                            .eq(UiExtensionDefinition::getDeleted, 0));
        }
        if (!isMatchingInterface(definition, operationCode)) {
            throw new IllegalArgumentException(
                    "历史列表列模板引用的接口扩展未完成迁移: "
                            + legacyId + "/" + operationCode);
        }
        value.put(extensionIdKey, definition.getId());
        value.remove(legacyIdKey);
        value.remove(legacyOperationKey);
    }

    /**
     * 判断是否{@code matching}接口；判断结果决定调用方的后续分支。
     *
     * @param definition 定义，作为 {@code equalsIgnoreCase} 的输入影响后续处理
     * @param operationCode 操作编码，后续用于判断是否{@code matching}接口时定位或关联目标
     * @return {@code matching}接口条件成立时为 true，否则为 false
     */
    private boolean isMatchingInterface(
            UiExtensionDefinition definition,
            String operationCode) {
        return definition != null
                && "INTERFACE".equalsIgnoreCase(
                        definition.getExtensionType())
                && !Integer.valueOf(1).equals(definition.getDeleted())
                && (!StringUtils.hasText(
                        definition.getProviderOperationCode())
                    || definition.getProviderOperationCode().equals(
                            operationCode.trim()));
    }

    /**
     * 将输入映射的键规范为字符串，供后续序列化和字段读取。
     *
     * @param value 待处理字符串映射的原始输入，结果供调用方继续使用
     * @return 字符串映射键值结果，供调用方继续处理
     */
    private Map<String, Object> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, child) -> result.put(
                String.valueOf(key), child));
        return result;
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
     * 新增或更新模板并创建一个新版本快照。
     *
     * @param request 保存请求
     * @return 保存后的模板
     * @throws IllegalArgumentException 模板编码、名称、类型或快照不合法时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public UiComponentTemplate save(UiComponentTemplateSaveRequest request) {
        validate(request);
        UiComponentTemplate template = StringUtils.hasText(request.getId())
                ? requireTemplateForUpdate(request.getId())
                : new UiComponentTemplate();
        boolean created = template.getId() == null;
        template.setTemplateKey(request.getTemplateKey().trim());
        template.setTemplateName(request.getTemplateName().trim());
        template.setTemplateType(request.getTemplateType().trim().toUpperCase());
        template.setStatus("ACTIVE");
        template.setDeleted(0);
        template.setUpdatedAt(LocalDateTime.now());
        if (created) {
            template.setCurrentVersion(0);
            template.setCreatedAt(LocalDateTime.now());
            templateMapper.insert(template);
        } else {
            templateMapper.updateById(template);
        }
        createVersion(template, request.getSnapshot(), request.getDescription());
        return templateMapper.selectById(template.getId());
    }

    /**
     * 为已存在的模板创建新版本快照，内容未变化时返回当前版本。
     *
     * @param templateId  模板ID
     * @param snapshot    模板快照
     * @param description 版本描述
     * @return 新创建或复用的版本
     * @throws IllegalArgumentException     快照为空时抛出
     * @throws RevisionConflictException    版本被并发更新时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public UiComponentTemplateVersion createVersion(
            String templateId,
            Map<String, Object> snapshot,
            String description) {
        UiComponentTemplate template = requireTemplateForUpdate(templateId);
        requireVersionedTemplate(template);
        return createVersion(template, snapshot, description);
    }

    /**
     * 执行模板版本升级的三向合并，输出合并快照和冲突列表。
     *
     * @param templateId 模板ID
     * @param request    升级请求，指定来源和目标版本及本地覆盖
     * @return 合并结果，包含 mergedSnapshot、conflicts 和 requiresConfirmation
     */
    public Map<String, Object> upgrade(
            String templateId,
            UiComponentTemplateUpgradeRequest request) {
        UiComponentTemplate template = requireTemplate(templateId);
        requireVersionedTemplate(template);
        int fromVersion = request.getFromVersion() == null
                ? template.getCurrentVersion() : request.getFromVersion();
        int toVersion = request.getToVersion() == null
                ? template.getCurrentVersion() : request.getToVersion();
        Map<String, Object> base = snapshot(templateId, fromVersion);
        Map<String, Object> incoming = snapshot(templateId, toVersion);
        Map<String, Object> local = request.getCurrentSnapshot() == null
                ? base : request.getCurrentSnapshot();
        List<String> conflicts = new ArrayList<>();
        Object merged = merge(base, local, incoming, "", conflicts);
        if (request.getLocalOverrides() != null) {
            merged = overlay(merged, request.getLocalOverrides());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("templateId", templateId);
        result.put("fromVersion", fromVersion);
        result.put("toVersion", toVersion);
        result.put("mergedSnapshot", merged);
        result.put("conflicts", conflicts);
        result.put("requiresConfirmation", !conflicts.isEmpty());
        return result;
    }

    /**
     * 创建版本；结果供后续流程传递或持久化。
     *
     * @param template 模板，作为 {@code validateSnapshot} 的输入影响后续处理
     * @param snapshot 快照，作为 {@code codec.canonicalize} 的输入影响后续处理
     * @param description 描述，作为 {@code version.setDescription} 的输入影响后续处理
     * @return 创建后的版本结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private UiComponentTemplateVersion createVersion(
            UiComponentTemplate template,
            Map<String, Object> snapshot,
            String description) {
        if (snapshot == null || snapshot.isEmpty()) {
            throw new IllegalArgumentException("模板快照不能为空");
        }
        validateSnapshot(template.getTemplateType(), snapshot);
        String document = codec.canonicalize(
                codec.write(snapshot, "组件模板快照"), "组件模板快照");
        String contentHash = hash(document);
        int currentVersion = template.getCurrentVersion() == null
                ? 0
                : template.getCurrentVersion();
        UiComponentTemplateVersion current =
                currentVersion < 1
                        ? null
                        : findVersion(template.getId(), currentVersion);
        if (current != null) {
            verifyVersionIntegrity(current);
            if (Objects.equals(contentHash, current.getContentHash())) {
                return current;
            }
        }
        UiComponentTemplateVersion version = new UiComponentTemplateVersion();
        version.setTemplateId(template.getId());
        version.setVersion(currentVersion + 1);
        version.setSnapshotDocument(document);
        version.setContentHash(contentHash);
        version.setDescription(blankToNull(description));
        version.setCreatedBy(UserContext.getUserId());
        version.setCreatedAt(LocalDateTime.now());
        try {
            // 恢复失败语句后才能查询冲突快照；PostgreSQL 等数据库不会自动恢复事务。
            writeAttempt.execute(() -> versionMapper.insert(version));
        } catch (DuplicateKeyException exception) {
            throw new RevisionConflictException(
                    "组件模板版本已被其他请求更新，请刷新后重试",
                    templateMapper.selectByIdForUpdate(template.getId()));
        }
        LocalDateTime updatedAt = LocalDateTime.now();
        UpdateWrapper<UiComponentTemplate> update = new UpdateWrapper<>();
        update.eq("id", template.getId())
                .eq("deleted", 0)
                .set("current_version", version.getVersion())
                .set("update_time", updatedAt);
        if (template.getCurrentVersion() == null) {
            update.isNull("current_version");
        } else {
            update.eq("current_version", currentVersion);
        }
        if (templateMapper.update(null, update) != 1) {
            throw new RevisionConflictException(
                    "组件模板版本已被其他请求更新，请刷新后重试",
                    templateMapper.selectByIdForUpdate(template.getId()));
        }
        template.setCurrentVersion(version.getVersion());
        template.setUpdatedAt(updatedAt);
        return version;
    }

    /**
     * 整理快照数据，供调用方遍历或继续处理。
     *
     * @param templateId 模板ID，后续用于处理快照时定位或关联目标
     * @param version 版本，作为 {@code findVersion} 的输入影响后续处理
     * @return 快照键值结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private Map<String, Object> snapshot(String templateId, int version) {
        UiComponentTemplateVersion found = findVersion(templateId, version);
        if (found == null) {
            throw new IllegalArgumentException("模板版本不存在: " + version);
        }
        verifyVersionIntegrity(found);
        return codec.read(
                found.getSnapshotDocument(),
                new TypeReference<Map<String, Object>>() {},
                "组件模板快照");
    }

    /**
     * 查询版本；查询结果供调用方展示或继续处理。
     *
     * @param templateId 模板ID，后续用于查询版本时定位或关联目标
     * @param version 版本，作为 {@code eq} 的输入影响后续处理
     * @return 符合条件的界面组件模板版本结果，供调用方继续处理
     */
    private UiComponentTemplateVersion findVersion(
            String templateId,
            int version) {
        return versionMapper.selectOne(
                new LambdaQueryWrapper<UiComponentTemplateVersion>()
                        .eq(UiComponentTemplateVersion::getTemplateId, templateId)
                        .eq(UiComponentTemplateVersion::getVersion, version));
    }

    /**
     * 验证版本{@code integrity}；不满足约束时阻止后续处理。
     *
     * @param version 版本，作为 {@code codec.read} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void verifyVersionIntegrity(UiComponentTemplateVersion version) {
        if (version == null
                || !StringUtils.hasText(version.getSnapshotDocument())
                || !StringUtils.hasText(version.getContentHash())
                || !Objects.equals(
                        version.getContentHash(),
                        hash(version.getSnapshotDocument()))) {
            String label = version == null
                    ? "未知模板版本"
                    : version.getTemplateId() + "@" + version.getVersion();
            throw new IllegalArgumentException(
                    "组件模板版本完整性校验失败: " + label);
        }
        codec.read(
                version.getSnapshotDocument(),
                new TypeReference<Map<String, Object>>() {},
                "组件模板快照");
    }

    /**
     * 合并界面组件模板；结果供后续流程传递或持久化。
     *
     * @param base 基础，供本方法合并界面组件模板时使用
     * @param local 本地，供本方法合并界面组件模板时使用
     * @param incoming {@code incoming}，供本方法合并界面组件模板时使用
     * @param path 路径，作为 {@code conflicts.add} 的输入影响后续处理
     * @param conflicts {@code conflicts}，供本方法合并界面组件模板时使用
     * @return 合并后的界面组件模板结果，供调用方继续处理
     */
    private Object merge(
            Object base,
            Object local,
            Object incoming,
            String path,
            List<String> conflicts) {
        if (Objects.equals(local, base)) {
            return incoming;
        }
        if (Objects.equals(incoming, base) || Objects.equals(local, incoming)) {
            return local;
        }
        if (base instanceof Map<?, ?> baseMap
                && local instanceof Map<?, ?> localMap
                && incoming instanceof Map<?, ?> incomingMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            Set<String> keys = new java.util.LinkedHashSet<>();
            baseMap.keySet().forEach(key -> keys.add(String.valueOf(key)));
            localMap.keySet().forEach(key -> keys.add(String.valueOf(key)));
            incomingMap.keySet().forEach(key -> keys.add(String.valueOf(key)));
            for (String key : keys) {
                String childPath = path.isEmpty() ? key : path + "." + key;
                result.put(key, merge(
                        baseMap.get(key),
                        localMap.get(key),
                        incomingMap.get(key),
                        childPath,
                        conflicts));
            }
            return result;
        }
        conflicts.add(path.isEmpty() ? "$" : path);
        return local;
    }

    /**
     * 处理{@code overlay}，并将结果传给后续步骤。
     *
     * @param target 目标，供本方法处理{@code overlay}时使用
     * @param overrides {@code overrides}，供本方法处理{@code overlay}时使用
     * @return 处理后的{@code overlay}结果，供调用方继续处理
     */
    private Object overlay(Object target, Object overrides) {
        if (target instanceof Map<?, ?> targetMap
                && overrides instanceof Map<?, ?> overrideMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            targetMap.forEach((key, value) -> result.put(String.valueOf(key), value));
            overrideMap.forEach((key, value) -> {
                String name = String.valueOf(key);
                result.put(name, overlay(result.get(name), value));
            });
            return result;
        }
        return overrides;
    }

    /**
     * 校验并获取模板；不满足约束时阻止后续处理。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 校验并获取后的模板结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private UiComponentTemplate requireTemplate(String id) {
        UiComponentTemplate template = templateMapper.selectById(id);
        if (template == null || Integer.valueOf(1).equals(template.getDeleted())) {
            throw new IllegalArgumentException("组件模板不存在");
        }
        return template;
    }

    /**
     * 校验并获取模板更新；不满足约束时阻止后续处理。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 校验并获取后的模板更新结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private UiComponentTemplate requireTemplateForUpdate(String id) {
        UiComponentTemplate template = templateMapper.selectByIdForUpdate(id);
        if (template == null || Integer.valueOf(1).equals(template.getDeleted())) {
            throw new IllegalArgumentException("组件模板不存在");
        }
        return template;
    }

    /**
     * 校验并获取{@code versioned}模板；不满足约束时阻止后续处理。
     *
     * @param template 模板，供本方法校验并获取{@code versioned}模板时使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void requireVersionedTemplate(UiComponentTemplate template) {
        if (isInitializationOnlyTemplate(template)) {
            throw new IllegalArgumentException(
                    "列表列模板仅用于一次性初始化，不能创建业务版本或升级已配置列");
        }
    }

    /**
     * 判断是否{@code initialization}仅模板；判断结果决定调用方的后续分支。
     *
     * @param template 模板，作为 {@code equalsIgnoreCase} 的输入影响后续处理
     * @return {@code initialization}仅模板条件成立时为 true，否则为 false
     */
    private boolean isInitializationOnlyTemplate(
            UiComponentTemplate template) {
        return template != null
                && "LIST_COLUMN_GROUP".equalsIgnoreCase(
                        template.getTemplateType());
    }

    /**
     * 校验界面组件模板；不满足约束时阻止后续处理。
     *
     * @param request 本次请求，后续经校验后用于校验界面组件模板
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validate(UiComponentTemplateSaveRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getTemplateKey())
                || !StringUtils.hasText(request.getTemplateName())
                || !StringUtils.hasText(request.getTemplateType())) {
            throw new IllegalArgumentException("模板编码、名称和类型不能为空");
        }
        String type = request.getTemplateType().trim().toUpperCase();
        if (!TEMPLATE_TYPES.contains(type)) {
            throw new IllegalArgumentException("不支持的模板类型: " + type);
        }
        validateSnapshot(type, request.getSnapshot());
    }

    /**
     * 校验快照；不满足约束时阻止后续处理。
     *
     * @param templateType 模板类型标识，决定后续快照采用的处理分支
     * @param snapshot 快照，作为 {@code snapshot.getOrDefault} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateSnapshot(
            String templateType,
            Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            throw new IllegalArgumentException("模板快照不能为空");
        }
        if (!"LIST_COLUMN_GROUP".equalsIgnoreCase(templateType)) {
            return;
        }
        Object fieldValue = snapshot.getOrDefault("field", snapshot);
        if (!(fieldValue instanceof Map<?, ?> field)) {
            throw new IllegalArgumentException("列表列模板快照必须包含 field 对象");
        }
        // 模板复制也必须遵守数据库分页约束，不能再生成需要全量内存过滤的虚拟查询列。
        if (Boolean.TRUE.equals(field.get("isQuery"))
                && ((field.get("dataSourceType") != null
                    && !"ENTITY_FIELD".equalsIgnoreCase(String.valueOf(field.get("dataSourceType")).trim()))
                    || (field.get("interfaceExtensionId") != null
                        && StringUtils.hasText(String.valueOf(field.get("interfaceExtensionId")))))) {
            throw new IllegalArgumentException("虚拟列模板不能作为查询条件");
        }
        for (String key : LIST_COLUMN_IDENTITY_KEYS) {
            if (field.containsKey(key)) {
                throw new IllegalArgumentException(
                        "列表列模板不得包含具体字段身份或排序属性: " + key);
            }
        }
        String legacyReferencePath = legacyInterfaceReferencePath(
                snapshot, "$");
        if (legacyReferencePath != null) {
            // 历史快照只在读取副本时转换；新版本绝不能继续制造二级服务引用。
            throw new IllegalArgumentException(
                    "新列表列模板只能使用 interfaceExtensionId，不能保存历史服务操作引用: "
                            + legacyReferencePath);
        }
        validateObjectDocument(field.get("dataSourceConfig"), "数据源配置");
        validateObjectDocument(field.get("queryConfig"), "查询配置");
        validateObjectDocument(field.get("columnConfig"), "列展示配置");
        validateObjectDocument(field.get("renderConfig"), "渲染配置");
    }

    /**
     * 查找新模板中仍存在的历史 service/operation pair，不误伤普通业务字段。
     *
     * @param value 待处理旧版接口引用路径的原始输入，结果供调用方继续使用
     * @param path 路径，供本方法处理旧版接口引用路径时使用
     * @return 处理后的旧版接口引用路径文本，供调用方比较或展示
     */
    private String legacyInterfaceReferencePath(Object value, String path) {
        if (value instanceof Map<?, ?> map) {
            boolean legacyColumn = map.containsKey("dataSourceId")
                    && map.containsKey("dataSourceOperationCode");
            boolean legacyQuery = map.containsKey("queryDataSourceId")
                    && map.containsKey("queryOperationCode");
            boolean legacyService = map.containsKey("serviceId")
                    && map.containsKey("operationCode");
            boolean secondaryOperation = map.containsKey("extensionId")
                    && map.containsKey("operationCode");
            if (legacyColumn || legacyQuery || legacyService
                    || secondaryOperation) {
                return path;
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String found = legacyInterfaceReferencePath(
                        entry.getValue(), path + "." + entry.getKey());
                if (found != null) {
                    return found;
                }
            }
        } else if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                String found = legacyInterfaceReferencePath(
                        list.get(index), path + "[" + index + "]");
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * 校验对象文档；不满足约束时阻止后续处理。
     *
     * @param value 待校验对象文档的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于校验对象文档时匹配或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateObjectDocument(Object value, String label) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            return;
        }
        if (value instanceof Map<?, ?>) {
            return;
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(label + "必须是 JSON 对象");
        }
        Object parsed = codec.read(
                text,
                new TypeReference<Object>() {},
                label);
        if (!(parsed instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(label + "必须是 JSON 对象");
        }
    }

    /**
     * 生成哈希文本，供后续匹配或展示。
     *
     * @param value 待处理哈希的原始输入，结果供调用方继续使用
     * @return 处理后的哈希文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("计算模板哈希失败", exception);
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
}
