package com.workflow.entity.definition.application;

import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityPublishHistory;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityPublishHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return snapshot;
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
}
