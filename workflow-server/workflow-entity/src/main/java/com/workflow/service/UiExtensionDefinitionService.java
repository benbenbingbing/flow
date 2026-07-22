package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.workflow.common.RevisionConflictException;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.dto.UiExtensionDefinitionSaveRequest;
import com.workflow.entity.UiExtensionDefinition;
import com.workflow.mapper.UiExtensionDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UiExtensionDefinitionService {

    private static final Set<String> TYPES =
            Set.of("FORM", "NODE", "FIELD", "LIST");
    private static final Set<String> STATUSES =
            Set.of("ACTIVE", "DISABLED");
    private static final Set<String> MODES =
            Set.of("CREATE", "EDIT", "APPROVE", "VIEW");
    private static final Pattern KEY =
            Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,99}");

    private final UiExtensionDefinitionMapper mapper;
    private final JsonDocumentCodec codec;

    public List<UiExtensionDefinition> list(
            String extensionType,
            String extensionKey,
            String status) {
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
        return mapper.selectList(query
                .eq(UiExtensionDefinition::getDeleted, 0)
                .orderByAsc(UiExtensionDefinition::getExtensionType)
                .orderByAsc(UiExtensionDefinition::getExtensionKey)
                .orderByDesc(UiExtensionDefinition::getVersion));
    }

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

    @Transactional(rollbackFor = Exception.class)
    public UiExtensionDefinition save(
            UiExtensionDefinitionSaveRequest request) {
        validate(request);
        UiExtensionDefinition current =
                StringUtils.hasText(request.getId())
                        ? mapper.selectById(request.getId())
                        : null;
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

    private void validate(UiExtensionDefinitionSaveRequest request) {
        if (request == null
                || !TYPES.contains(normalize(request.getExtensionType()))) {
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
    }

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

    private List<String> normalizeList(List<String> values) {
        return values == null
                ? List.of()
                : values.stream()
                        .filter(StringUtils::hasText)
                        .map(this::normalize)
                        .distinct()
                        .toList();
    }

    private String write(Object value, String label) {
        if (value == null
                || value instanceof Map<?, ?> map && map.isEmpty()
                || value instanceof List<?> list && list.isEmpty()) {
            return null;
        }
        return codec.write(value, label);
    }

    private String normalize(String value) {
        return value == null
                ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
