package com.workflow.entity.definition.application;

import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityPublishHistory;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityPublishHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 实体发布快照读取。
 */
@Service
@RequiredArgsConstructor
public class EntityPublishedSnapshotService {

    private static final Map<String, String> LEGACY_FIELD_TYPES =
            Map.of("SUB_FORM_LIST", "SUB_LIST");

    private final EntityPublishHistoryMapper historyMapper;
    private final ObjectMapper objectMapper;

    /**
     * 按实体ID获取最新发布快照。
     *
     * @param entityId 实体定义ID
     * @return 最新发布快照
     * @throws RuntimeException 该实体尚未发布时抛出
     */
    @Transactional(readOnly = true)
    public EntityPublishedSnapshot getLatestByEntityId(String entityId) {
        EntityPublishHistory history = historyMapper.findLatestByEntityId(entityId);
        if (history == null) {
            throw new RuntimeException("实体未发布: " + entityId);
        }
        return toSnapshot(history);
    }

    /**
     * 读取实体当前最新的可钉定发布定义。
     *
     * <p>指纹覆盖历史 ID、实体身份、版本、字段文档和关系文档。
     * 调用方应同时保存 historyId 与 schemaHash：前者防止运行时追随
     * latest，后者防止历史行被非预期改写后静默生效。</p>
     *
     * @param entityId 实体定义 ID
     * @return 最新已发布定义及其完整性指纹
     */
    @Transactional(readOnly = true)
    public PinnedEntitySnapshot getLatestPinnedByEntityId(
            String entityId) {
        EntityPublishHistory history = historyMapper.findLatestByEntityId(
                entityId);
        if (history == null) {
            throw new RuntimeException("实体未发布: " + entityId);
        }
        return pinned(history);
    }

    /**
     * 按历史 ID 精确读取钉定的实体定义，不回退到最新发布。
     *
     * @param historyId 实体发布历史 ID
     * @return 指定历史定义及指纹
     * @throws RuntimeException 历史已缺失时抛出，由上层 fail-closed
     */
    @Transactional(readOnly = true)
    public PinnedEntitySnapshot getPinnedByHistoryId(
            String historyId) {
        if (historyId == null || historyId.isBlank()) {
            throw new IllegalArgumentException("实体发布历史ID不能为空");
        }
        EntityPublishHistory history = historyMapper.selectById(
                historyId.trim());
        if (history == null) {
            throw new RuntimeException("实体发布历史不存在: " + historyId);
        }
        return pinned(history);
    }

    /**
     * 按实体编码获取最新发布快照。
     *
     * @param entityCode 实体编码
     * @return 最新发布快照
     * @throws RuntimeException 该实体尚未发布时抛出
     */
    @Transactional(readOnly = true)
    public EntityPublishedSnapshot getLatestByEntityCode(String entityCode) {
        EntityPublishedSnapshot snapshot =
                findLatestByEntityCode(entityCode);
        if (snapshot == null) {
            throw new RuntimeException("实体未发布: " + entityCode);
        }
        return snapshot;
    }

    /**
     * 按实体编码查找最新发布快照，不存在时返回 null。
     */
    @Transactional(readOnly = true)
    public EntityPublishedSnapshot findLatestByEntityCode(
            String entityCode) {
        EntityPublishHistory history = historyMapper.findLatestByEntityCode(entityCode);
        if (history == null) {
            return null;
        }
        return toSnapshot(history);
    }

    /**
     * 将发布历史记录转换为发布快照对象。
     *
     * @param history 发布历史记录
     * @return 发布快照
     */
    private EntityPublishedSnapshot toSnapshot(EntityPublishHistory history) {
        EntityPublishedSnapshot snapshot = new EntityPublishedSnapshot();
        snapshot.setHistoryId(history.getId());
        snapshot.setEntityId(history.getEntityId());
        snapshot.setEntityCode(history.getEntityCode());
        snapshot.setEntityName(history.getEntityName());
        snapshot.setProcessDefinitionId(history.getProcessDefinitionId());
        snapshot.setLifecycleMode(history.getLifecycleMode());
        snapshot.setTeamVisibilityEnabled(Boolean.TRUE.equals(history.getTeamVisibilityEnabled()));
        snapshot.setTeamVisibilityLevel(history.getTeamVisibilityLevel() == null
                ? com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition.TeamVisibilityLevel.ADDITIVE
                : history.getTeamVisibilityLevel());
        snapshot.setVersion(history.getVersion());
        snapshot.setFields(parseFields(history));
        snapshot.setRelationsSnapshotAvailable(
                history.getRelationsSnapshot() != null);
        snapshot.setRelations(parseRelations(history));
        return snapshot;
    }

    private PinnedEntitySnapshot pinned(
            EntityPublishHistory history) {
        return new PinnedEntitySnapshot(
                toSnapshot(history), schemaHash(history));
    }

    /**
     * 对影响字段/关系解析的原始历史内容做长度分隔编码。
     * 使用原始文档而非当前 POJO 再序列化，可避免后续代码属性
     * 顺序调整导致旧宿主版本指纹无意失效。
     */
    private String schemaHash(EntityPublishHistory history) {
        StringBuilder material = new StringBuilder();
        appendFingerprintPart(material, history.getId());
        appendFingerprintPart(material, history.getEntityId());
        appendFingerprintPart(material, history.getEntityCode());
        appendFingerprintPart(material, history.getVersion());
        appendFingerprintPart(material, history.getFieldsSnapshot());
        // NULL 表示旧版本没有关系快照，与明确的 [] 必须区分。
        appendFingerprintPart(material, history.getRelationsSnapshot());
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            material.toString().getBytes(
                                    StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "运行环境不支持 SHA-256", exception);
        }
    }

    private void appendFingerprintPart(
            StringBuilder target,
            Object value) {
        if (value == null) {
            target.append("-1:");
            return;
        }
        String text = String.valueOf(value);
        target.append(text.length()).append(':').append(text);
    }

    private List<EntityRelation> parseRelations(
            EntityPublishHistory history) {
        String relationsSnapshot = history.getRelationsSnapshot();
        if (relationsSnapshot == null || relationsSnapshot.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    relationsSnapshot,
                    objectMapper.getTypeFactory()
                            .constructCollectionType(
                                    List.class,
                                    EntityRelation.class));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new RuntimeException(
                    "实体发布关系快照解析失败: " + history.getEntityId(),
                    e);
        }
    }

    private List<EntityField> parseFields(EntityPublishHistory history) {
        String fieldsSnapshot = history.getFieldsSnapshot();
        if (fieldsSnapshot == null || fieldsSnapshot.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> fields = objectMapper.readValue(
                    fieldsSnapshot,
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            List<Map<String, Object>> normalized = fields.stream()
                    .map(this::normalizeLegacyField)
                    .toList();
            return objectMapper.convertValue(
                    normalized,
                    objectMapper.getTypeFactory()
                            .constructCollectionType(
                                    List.class,
                                    EntityField.class));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("实体发布快照解析失败: " + history.getEntityId(), e);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("实体发布快照解析失败: " + history.getEntityId(), e);
        }
    }

    private Map<String, Object> normalizeLegacyField(
            Map<String, Object> source) {
        Map<String, Object> field = new LinkedHashMap<>(source);
        Object fieldType = field.get("fieldType");
        if (fieldType == null) {
            return field;
        }
        String normalizedType = String.valueOf(fieldType)
                .trim()
                .toUpperCase(Locale.ROOT);
        field.put(
                "fieldType",
                LEGACY_FIELD_TYPES.getOrDefault(
                        normalizedType,
                        normalizedType));
        return field;
    }

    /** 实体发布历史与其不可变完整性指纹。 */
    public record PinnedEntitySnapshot(
            EntityPublishedSnapshot snapshot,
            String schemaHash) {
    }
}
