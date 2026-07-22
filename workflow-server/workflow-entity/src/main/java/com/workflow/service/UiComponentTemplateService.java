package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.workflow.common.RevisionConflictException;
import com.workflow.common.UserContext;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.dto.UiComponentTemplateSaveRequest;
import com.workflow.dto.UiComponentTemplateUpgradeRequest;
import com.workflow.entity.UiComponentTemplate;
import com.workflow.entity.UiComponentTemplateVersion;
import com.workflow.mapper.UiComponentTemplateMapper;
import com.workflow.mapper.UiComponentTemplateVersionMapper;
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

@Service
@RequiredArgsConstructor
public class UiComponentTemplateService {

    private static final Set<String> TEMPLATE_TYPES = Set.of(
            "FIELD_GROUP", "FORM_SECTION", "SUB_FORM",
            "LIST_COLUMN_GROUP", "BUTTON_GROUP");

    private final UiComponentTemplateMapper templateMapper;
    private final UiComponentTemplateVersionMapper versionMapper;
    private final JsonDocumentCodec codec;

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

    public List<UiComponentTemplateVersion> versions(String templateId) {
        requireTemplate(templateId);
        List<UiComponentTemplateVersion> versions =
                versionMapper.findByTemplateId(templateId);
        versions.forEach(this::verifyVersionIntegrity);
        return versions;
    }

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

    @Transactional(rollbackFor = Exception.class)
    public UiComponentTemplateVersion createVersion(
            String templateId,
            Map<String, Object> snapshot,
            String description) {
        return createVersion(
                requireTemplateForUpdate(templateId),
                snapshot,
                description);
    }

    public Map<String, Object> upgrade(
            String templateId,
            UiComponentTemplateUpgradeRequest request) {
        UiComponentTemplate template = requireTemplate(templateId);
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
