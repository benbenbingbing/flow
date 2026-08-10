package com.workflow.entity.ui.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.workflow.core.error.RevisionConflictException;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.api.request.UiComponentTemplateSaveRequest;
import com.workflow.entity.ui.api.request.UiComponentTemplateUpgradeRequest;
import com.workflow.entity.ui.infrastructure.persistence.record.UiComponentTemplate;
import com.workflow.entity.ui.infrastructure.persistence.record.UiComponentTemplateVersion;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiComponentTemplateMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiComponentTemplateVersionMapper;
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
            "FIELD_GROUP", "FORM_SECTION", "SUB_FORM",
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
        return snapshot(templateId, currentRevision);
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
            versionMapper.insert(version);
        } catch (DuplicateKeyException exception) {
            throw new RevisionConflictException(
                    "组件模板版本已被其他请求更新，请刷新后重试",
                    templateMapper.selectById(template.getId()));
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
                    templateMapper.selectById(template.getId()));
        }
        template.setCurrentVersion(version.getVersion());
        template.setUpdatedAt(updatedAt);
        return version;
    }

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

    private UiComponentTemplateVersion findVersion(
            String templateId,
            int version) {
        return versionMapper.selectOne(
                new LambdaQueryWrapper<UiComponentTemplateVersion>()
                        .eq(UiComponentTemplateVersion::getTemplateId, templateId)
                        .eq(UiComponentTemplateVersion::getVersion, version));
    }

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

    private UiComponentTemplate requireTemplate(String id) {
        UiComponentTemplate template = templateMapper.selectById(id);
        if (template == null || Integer.valueOf(1).equals(template.getDeleted())) {
            throw new IllegalArgumentException("组件模板不存在");
        }
        return template;
    }

    private UiComponentTemplate requireTemplateForUpdate(String id) {
        UiComponentTemplate template = templateMapper.selectByIdForUpdate(id);
        if (template == null || Integer.valueOf(1).equals(template.getDeleted())) {
            throw new IllegalArgumentException("组件模板不存在");
        }
        return template;
    }

    private void requireVersionedTemplate(UiComponentTemplate template) {
        if (isInitializationOnlyTemplate(template)) {
            throw new IllegalArgumentException(
                    "列表列模板仅用于一次性初始化，不能创建业务版本或升级已配置列");
        }
    }

    private boolean isInitializationOnlyTemplate(
            UiComponentTemplate template) {
        return template != null
                && "LIST_COLUMN_GROUP".equalsIgnoreCase(
                        template.getTemplateType());
    }

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
        for (String key : LIST_COLUMN_IDENTITY_KEYS) {
            if (field.containsKey(key)) {
                throw new IllegalArgumentException(
                        "列表列模板不得包含具体字段身份或排序属性: " + key);
            }
        }
        validateObjectDocument(field.get("dataSourceConfig"), "数据源配置");
        validateObjectDocument(field.get("queryConfig"), "查询配置");
        validateObjectDocument(field.get("columnConfig"), "列展示配置");
        validateObjectDocument(field.get("renderConfig"), "渲染配置");
    }

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

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("计算模板哈希失败", exception);
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
