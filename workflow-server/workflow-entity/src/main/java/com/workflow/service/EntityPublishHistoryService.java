package com.workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.EntityFieldDTO;
import com.workflow.dto.EntityPublishHistoryDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityPublishHistory;
import com.workflow.mapper.EntityPublishHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 实体发布版本历史服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityPublishHistoryService {

    private final EntityPublishHistoryMapper historyMapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建发布版本记录
     *
     * @param entity        实体定义
     * @param fields        字段列表
     * @param tableDdl      表结构DDL
     * @param publishType   发布类型
     * @param changesDesc   变更描述
     * @param userId        发布人ID
     * @param userName      发布人姓名
     * @return 创建的版本记录
     */
    @Transactional
    public EntityPublishHistory createVersion(
            EntityDefinition entity,
            List<EntityField> fields,
            String tableDdl,
            EntityPublishHistory.PublishType publishType,
            String changesDesc,
            String userId,
            String userName) {
        return createVersion(entity, fields, tableDdl, publishType, changesDesc, userId, userName, null);
    }

    /**
     * 创建发布版本记录（带版本描述）。
     *
     * @param entity            实体定义
     * @param fields            字段列表
     * @param tableDdl          表结构DDL
     * @param publishType       发布类型
     * @param changesDesc       变更描述
     * @param userId            发布人ID
     * @param userName          发布人姓名
     * @param versionDescription 版本描述
     * @return 创建的版本记录
     */
    @Transactional
    public EntityPublishHistory createVersion(
            EntityDefinition entity,
            List<EntityField> fields,
            String tableDdl,
            EntityPublishHistory.PublishType publishType,
            String changesDesc,
            String userId,
            String userName,
            String versionDescription) {

        // 获取下一个版本号
        Integer latestVersion = historyMapper.getLatestVersion(entity.getId());
        int nextVersion = (latestVersion == null) ? 1 : latestVersion + 1;

        // 将字段列表转为JSON
        String fieldsSnapshot;
        try {
            fieldsSnapshot = objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            log.error("字段快照序列化失败", e);
            fieldsSnapshot = "[]";
        }

        EntityPublishHistory history = new EntityPublishHistory();
        history.setEntityId(entity.getId());
        history.setEntityCode(entity.getEntityCode());
        history.setEntityName(entity.getEntityName());
        history.setProcessDefinitionId(entity.getProcessDefinitionId());
        history.setLifecycleMode(entity.getLifecycleMode());
        history.setTeamVisibilityEnabled(Boolean.TRUE.equals(entity.getTeamVisibilityEnabled()));
        history.setTeamVisibilityLevel(entity.getTeamVisibilityLevel() == null
                ? EntityDefinition.TeamVisibilityLevel.ADDITIVE
                : entity.getTeamVisibilityLevel());
        history.setVersion(nextVersion);
        history.setVersionDescription(StringUtils.hasText(versionDescription)
                ? versionDescription.trim()
                : (publishType == EntityPublishHistory.PublishType.CREATE
                    ? "首次发布" : (changesDesc != null ? changesDesc : "字段变更")));
        history.setFieldsSnapshot(fieldsSnapshot);
        history.setTableDdl(tableDdl);
        history.setPublishType(publishType);
        history.setChangesDescription(changesDesc);
        history.setPublishedAt(LocalDateTime.now());
        history.setPublishedBy(userId);
        history.setPublishedByName(userName);
        history.setStatus(EntityPublishHistory.Status.ACTIVE);

        historyMapper.insert(history);
        log.info("实体 [{}] 发布版本 {} 已记录", entity.getEntityCode(), nextVersion);
        return history;
    }

    /**
     * 获取实体的版本历史列表
     */
    public List<EntityPublishHistoryDTO> getVersionHistory(String entityId) {
        List<EntityPublishHistory> list = historyMapper.findByEntityId(entityId);
        return list.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    /**
     * 获取实体的最新版本
     */
    public EntityPublishHistoryDTO getLatestVersion(String entityId) {
        EntityPublishHistory history = historyMapper.findLatestByEntityId(entityId);
        return history != null ? convertToDTO(history) : null;
    }

    /**
     * 获取指定版本的详情
     */
    public EntityPublishHistoryDTO getVersionDetail(String historyId) {
        EntityPublishHistory history = historyMapper.selectById(historyId);
        return history != null ? convertToDTO(history) : null;
    }

    /**
     * 比较两个版本的差异
     */
    public String compareVersions(String historyId1, String historyId2) {
        EntityPublishHistory v1 = historyMapper.selectById(historyId1);
        EntityPublishHistory v2 = historyMapper.selectById(historyId2);

        if (v1 == null || v2 == null) {
            return "版本不存在";
        }

        // 简单的版本比较描述
        StringBuilder diff = new StringBuilder();
        diff.append("版本 ").append(v1.getVersion()).append(" → ").append(v2.getVersion()).append("\n");
        
        if (v1.getChangesDescription() != null) {
            diff.append("变更: ").append(v1.getChangesDescription()).append("\n");
        }
        
        return diff.toString();
    }

    private EntityPublishHistoryDTO convertToDTO(EntityPublishHistory history) {
        EntityPublishHistoryDTO dto = new EntityPublishHistoryDTO();
        dto.setId(history.getId());
        dto.setEntityId(history.getEntityId());
        dto.setEntityCode(history.getEntityCode());
        dto.setEntityName(history.getEntityName());
        dto.setProcessDefinitionId(history.getProcessDefinitionId());
        dto.setLifecycleMode(history.getLifecycleMode());
        dto.setTeamVisibilityEnabled(Boolean.TRUE.equals(history.getTeamVisibilityEnabled()));
        dto.setTeamVisibilityLevel(history.getTeamVisibilityLevel());
        dto.setVersion(history.getVersion());
        dto.setVersionDescription(history.getVersionDescription());
        dto.setFieldsSnapshot(history.getFieldsSnapshot());
        dto.setTableDdl(history.getTableDdl());
        dto.setPublishType(history.getPublishType());
        dto.setChangesDescription(history.getChangesDescription());
        dto.setPublishedAt(history.getPublishedAt());
        dto.setPublishedBy(history.getPublishedBy());
        dto.setPublishedByName(history.getPublishedByName());
        dto.setStatus(history.getStatus());

        // 解析字段快照
        if (history.getFieldsSnapshot() != null) {
            try {
                List<EntityField> fields = objectMapper.readValue(
                        history.getFieldsSnapshot(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, EntityField.class));
                dto.setFields(fields.stream().map(this::convertFieldToDTO).collect(Collectors.toList()));
            } catch (JsonProcessingException e) {
                log.error("字段快照反序列化失败", e);
            }
        }

        return dto;
    }

    private EntityFieldDTO convertFieldToDTO(EntityField field) {
        EntityFieldDTO dto = new EntityFieldDTO();
        dto.setId(field.getId());
        dto.setFieldCode(field.getFieldCode());
        dto.setFieldName(field.getFieldName());
        dto.setFieldType(field.getFieldType());
        dto.setDbType(field.getDbType());
        dto.setFieldLength(field.getFieldLength());
        dto.setIsRequired(field.getIsRequired());
        dto.setIsUnique(field.getIsUnique());
        dto.setDefaultValue(field.getDefaultValue());
        dto.setOptionsJson(field.getOptionsJson());
        dto.setIsSystem(field.getIsSystem());
        dto.setEditable(field.getEditable());
        dto.setSortOrder(field.getSortOrder());
        return dto;
    }
}
