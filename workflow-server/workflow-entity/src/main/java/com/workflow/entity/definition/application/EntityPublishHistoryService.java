package com.workflow.entity.definition.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.result.PageResult;
import com.workflow.core.result.PageRequest;
import com.workflow.entity.definition.api.response.EntityFieldDTO;
import com.workflow.entity.definition.api.response.EntityPublishHistoryDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityPublishHistory;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityPublishHistoryMapper;
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
        return createVersion(
                entity,
                fields,
                tableDdl,
                publishType,
                changesDesc,
                userId,
                userName,
                null,
                null);
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
        return createVersion(
                entity,
                fields,
                tableDdl,
                publishType,
                changesDesc,
                userId,
                userName,
                versionDescription,
                null);
    }

    /**
     * 创建发布版本记录，同时冻结与字段定义解耦的实体关系。
     *
     * @param entity 实体，作为 {@code historyMapper.getLatestVersion} 的输入影响后续处理
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @param tableDdl 表DDL，作为 {@code history.setTableDdl} 的输入影响后续处理
     * @param publishType 发布类型标识，决定后续版本采用的处理分支
     * @param changesDesc 变更集合{@code desc}，作为 {@code history.setChangesDescription} 的输入影响后续处理
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param userName 用户名称，后续用于身份匹配或操作展示
     * @param versionDescription 版本描述，作为 {@code history.setVersionDescription} 的输入影响后续处理
     * @param relations 当前实体已启用的关系草稿；空列表表示明确发布空关系，
     *                  null 仅供旧调用兼容使用
     * @return 创建后的版本结果，供调用方继续处理
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
            String versionDescription,
            List<EntityRelation> relations) {

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

        String relationsSnapshot = null;
        if (relations != null) {
            try {
                relationsSnapshot = objectMapper.writeValueAsString(relations);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(
                        "实体关系快照序列化失败: " + entity.getEntityCode(),
                        e);
            }
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
        history.setRelationsSnapshot(relationsSnapshot);
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
     *
     * @param entityId 实体ID，后续用于读取版本历史时定位或关联目标
     * @return 实体发布历史集合，供调用方遍历或展示
     */
    public List<EntityPublishHistoryDTO> getVersionHistory(String entityId) {
        List<EntityPublishHistory> list = historyMapper.findByEntityId(entityId);
        return list.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    /**
     * 分页获取实体发布历史，供历史弹窗按滚动位置逐页加载。
     *
     * <p>页码最小为 1，单页最多 50 条，避免历史字段快照一次性返回导致弹窗打开变慢。</p>
     *
     * @param entityId         实体定义 ID
     * @param requestedPageNum 请求页码
     * @param requestedPageSize 请求条数
     * @return 按版本号倒序排列的历史分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<EntityPublishHistoryDTO> getVersionHistoryPage(
            String entityId,
            Integer requestedPageNum,
            Integer requestedPageSize) {
        PageRequest page = PageRequest.normalize(
                requestedPageNum, requestedPageSize, 5, 50);
        long total = historyMapper.countByEntityId(entityId);
        List<EntityPublishHistoryDTO> records = historyMapper
                .findPageByEntityId(
                        entityId,
                        page.offset(),
                        page.pageSize())
                .stream()
                .map(this::convertToDTO)
                .toList();
        return new PageResult<>(
                records, total, page.pageNumber(), page.pageSize());
    }

    /**
     * 获取实体的指定发布版本，供相邻版本差异比较点查使用。
     *
     * @param entityId 实体定义 ID
     * @param version  发布版本号
     * @return 指定版本；不存在时返回 null
     */
    @Transactional(readOnly = true)
    public EntityPublishHistoryDTO getVersion(
            String entityId,
            Integer version) {
        EntityPublishHistory history = historyMapper
                .findByEntityIdAndVersion(entityId, version);
        return history == null ? null : convertToDTO(history);
    }

    /**
     * 获取实体的最新版本
     *
     * @param entityId 实体ID，后续用于读取最新版本时定位或关联目标
     * @return 符合条件的实体发布历史结果，供调用方继续处理
     */
    public EntityPublishHistoryDTO getLatestVersion(String entityId) {
        EntityPublishHistory history = historyMapper.findLatestByEntityId(entityId);
        return history != null ? convertToDTO(history) : null;
    }

    /**
     * 获取指定版本的详情
     *
     * @param historyId 历史ID，后续用于读取版本详情时定位或关联目标
     * @return 符合条件的实体发布历史结果，供调用方继续处理
     */
    public EntityPublishHistoryDTO getVersionDetail(String historyId) {
        EntityPublishHistory history = historyMapper.selectById(historyId);
        return history != null ? convertToDTO(history) : null;
    }

    /**
     * 比较两个版本的差异
     *
     * @param historyId1 历史{@code id1}，作为 {@code historyMapper.selectById} 的输入影响后续处理
     * @param historyId2 历史{@code id2}，作为 {@code historyMapper.selectById} 的输入影响后续处理
     * @return 比较后的{@code versions}文本，供调用方比较或展示
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

    /**
     * 转换截止DTO；输出作为后续校验或处理的输入。
     *
     * @param history 历史，作为 {@code dto.setId} 的输入影响后续处理
     * @return 转换后的截止DTO结果，供调用方继续处理
     */
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
        dto.setRelationsSnapshot(history.getRelationsSnapshot());
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

        if (history.getRelationsSnapshot() != null) {
            try {
                dto.setRelations(objectMapper.readValue(
                        history.getRelationsSnapshot(),
                        objectMapper.getTypeFactory()
                                .constructCollectionType(
                                        List.class,
                                        EntityRelation.class)));
            } catch (JsonProcessingException e) {
                log.error("关系快照反序列化失败", e);
            }
        }

        return dto;
    }

    /**
     * 转换字段截止DTO；输出作为后续校验或处理的输入。
     *
     * @param field 字段，作为 {@code dto.setId} 的输入影响后续处理
     * @return 转换后的字段截止DTO结果，供调用方继续处理
     */
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
        dto.setFileTypes(field.getFileTypes());
        dto.setFileMaxSize(field.getFileMaxSize());
        dto.setFileMaxCount(field.getFileMaxCount());
        dto.setFileItems(field.getFileItems());
        return dto;
    }
}
