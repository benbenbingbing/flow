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

    @Transactional(readOnly = true)
    public EntityPublishedSnapshot getLatestByEntityId(String entityId) {
        EntityPublishHistory history = historyMapper.findLatestByEntityId(entityId);
        if (history == null) {
            throw new RuntimeException("实体未发布: " + entityId);
        }
        return toSnapshot(history);
    }

    @Transactional(readOnly = true)
    public EntityPublishedSnapshot getLatestByEntityCode(String entityCode) {
        EntityPublishHistory history = historyMapper.findLatestByEntityCode(entityCode);
        if (history == null) {
            throw new RuntimeException("实体未发布: " + entityCode);
        }
        return toSnapshot(history);
    }

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
