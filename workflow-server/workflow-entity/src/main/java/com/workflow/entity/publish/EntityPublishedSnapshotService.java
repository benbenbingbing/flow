package com.workflow.entity.publish;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityPublishHistory;
import com.workflow.mapper.EntityPublishHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 实体发布快照读取。
 */
@Service
@RequiredArgsConstructor
public class EntityPublishedSnapshotService {

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
        EntityPublishHistory history = historyMapper.findLatestByEntityCode(entityCode);
        if (history == null) {
            throw new RuntimeException("实体未发布: " + entityCode);
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
                ? com.workflow.entity.EntityDefinition.TeamVisibilityLevel.ADDITIVE
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
            return objectMapper.readValue(
                    fieldsSnapshot,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, EntityField.class));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("实体发布快照解析失败: " + history.getEntityId(), e);
        }
    }
}
