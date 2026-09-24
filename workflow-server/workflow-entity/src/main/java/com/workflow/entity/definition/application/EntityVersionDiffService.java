package com.workflow.entity.definition.application;

import com.workflow.entity.data.application.DynamicTableService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.definition.api.response.EntityFieldDTO;
import com.workflow.entity.definition.api.response.EntityPublishHistoryDTO;
import com.workflow.entity.definition.api.response.EntityVersionDiffDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 实体版本差异比较服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityVersionDiffService {

    private final EntityDefinitionMapper entityDefinitionMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityPublishHistoryService publishHistoryService;
    private final DynamicTableService dynamicTableService;
    private final ObjectMapper objectMapper;
    @Autowired(required = false)
    private EntitySchemaOperationService schemaOperationService;

    /**
     * 获取即将发布的版本差异
     * 比较当前已发布的版本和即将发布的新版本之间的差异
     *
     * @param entityId 实体ID
     * @return 版本差异信息
     */
    public EntityVersionDiffDTO getPendingPublishDiff(String entityId) {
        EntityDefinition entity = entityDefinitionMapper.selectById(entityId);
        if (entity == null) {
            throw new RuntimeException("实体不存在: " + entityId);
        }

        EntityVersionDiffDTO diff = new EntityVersionDiffDTO();
        diff.setEntityId(entityId);
        diff.setEntityCode(entity.getEntityCode());
        diff.setEntityName(entity.getEntityName());

        // 获取已发布的最新版本
        EntityPublishHistoryDTO latestHistory = publishHistoryService.getLatestVersion(entityId);

        // 获取当前所有字段
        List<EntityField> currentFields = fieldMapper.findByEntityId(entityId);

        if (latestHistory == null) {
            // 首次发布
            diff.setIsFirstPublish(true);
            diff.setCurrentVersion(0);
            diff.setNextVersion(1);
            diff.setChangeSummary("首次发布，将创建数据表并包含 " + currentFields.size() + " 个字段");

            // 所有字段都是新增
            for (EntityField field : currentFields) {
                EntityVersionDiffDTO.FieldDiff fieldDiff = convertToFieldDiff(field, EntityVersionDiffDTO.FieldDiff.ChangeType.ADD);
                diff.getAddedFields().add(fieldDiff);
            }

            // 生成DDL预览
            String ddl = dynamicTableService.buildCreateTableSqlPreview(entity.getEntityCode(), currentFields, entity.getEntityName());
            diff.getPendingDdls().add(ddl);

        } else {
            // 非首次发布，比较差异
            diff.setIsFirstPublish(false);
            diff.setCurrentVersion(latestHistory.getVersion());
            diff.setNextVersion(latestHistory.getVersion() + 1);

            // 解析历史版本的字段
            List<EntityFieldDTO> publishedFields = latestHistory.getFields();
            Map<String, EntityFieldDTO> publishedFieldMap = publishedFields.stream()
                    .collect(Collectors.toMap(EntityFieldDTO::getFieldCode, f -> f));

            // 比较字段
            for (EntityField field : currentFields) {
                if (publishedFieldMap.containsKey(field.getFieldCode())) {
                    // 字段已存在
                    EntityFieldDTO publishedField = publishedFieldMap.get(field.getFieldCode());
                    EntityVersionDiffDTO.FieldDiff fieldDiff = compareField(publishedField, field);

                    if (fieldDiff.getChangeType() == EntityVersionDiffDTO.FieldDiff.ChangeType.UNCHANGED) {
                        diff.getUnchangedFields().add(fieldDiff);
                    } else {
                        diff.getModifiedFields().add(fieldDiff);
                    }
                } else {
                    // 新增字段
                    EntityVersionDiffDTO.FieldDiff fieldDiff = convertToFieldDiff(field, EntityVersionDiffDTO.FieldDiff.ChangeType.ADD);
                    diff.getAddedFields().add(fieldDiff);
                }
            }

            // 检查是否有删除的字段（理论上已发布字段不会删除，但还是要检查一下）
            Set<String> currentFieldCodes = currentFields.stream()
                    .map(EntityField::getFieldCode)
                    .collect(Collectors.toSet());
            for (EntityFieldDTO publishedField : publishedFields) {
                if (!currentFieldCodes.contains(publishedField.getFieldCode()) && !Boolean.TRUE.equals(publishedField.getIsSystem())) {
                    EntityVersionDiffDTO.FieldDiff fieldDiff = convertToFieldDiff(publishedField, EntityVersionDiffDTO.FieldDiff.ChangeType.REMOVE);
                    diff.getRemovedFields().add(fieldDiff);
                }
            }

            // 生成变更摘要
            diff.setChangeSummary(buildChangeSummary(diff));

            // 生成DDL预览（只包含新增字段）
            if (!diff.getAddedFields().isEmpty()) {
                Set<String> addedFieldCodes = diff.getAddedFields().stream()
                        .map(EntityVersionDiffDTO.FieldDiff::getFieldCode)
                        .collect(Collectors.toSet());
                // DDL 必须使用完整字段定义。差异 DTO 只供界面展示，反向转换会丢失默认值和物理列名。
                List<EntityField> newFields = currentFields.stream()
                        .filter(field -> addedFieldCodes.contains(field.getFieldCode()))
                        .collect(Collectors.toList());
                List<String> ddls = dynamicTableService.buildAddColumnSqlPreviews(entity.getEntityCode(), newFields);
                diff.getPendingDdls().addAll(ddls);
            }
        }

        if (schemaOperationService != null) {
            diff.setSchemaOperation(schemaOperationService.preview(
                    entity,
                    currentFields,
                    diff.getPendingDdls()));
        }
        return diff;
    }

    /**
     * 比较两个版本的差异
     *
     * @param entityId    实体ID
     * @param versionFrom 起始版本号
     * @param versionTo   目标版本号
     * @return 版本差异信息
     */
    public EntityVersionDiffDTO compareVersions(String entityId, Integer versionFrom, Integer versionTo) {
        // 历史可能包含大量字段快照；差异比较只点查目标两版，不能回退到全量历史解析。
        EntityPublishHistoryDTO fromHistory = publishHistoryService
                .getVersion(entityId, versionFrom);
        if (fromHistory == null) {
            throw new RuntimeException("版本不存在: " + versionFrom);
        }
        EntityPublishHistoryDTO toHistory = publishHistoryService
                .getVersion(entityId, versionTo);
        if (toHistory == null) {
            throw new RuntimeException("版本不存在: " + versionTo);
        }

        EntityVersionDiffDTO diff = new EntityVersionDiffDTO();
        diff.setEntityId(entityId);
        diff.setEntityCode(fromHistory.getEntityCode());
        diff.setEntityName(fromHistory.getEntityName());
        diff.setCurrentVersion(versionFrom);
        diff.setNextVersion(versionTo);
        diff.setIsFirstPublish(versionFrom == 0);

        // 解析两个版本的字段
        List<EntityFieldDTO> fromFields = fromHistory.getFields();
        List<EntityFieldDTO> toFields = toHistory.getFields();

        Map<String, EntityFieldDTO> fromFieldMap = fromFields.stream()
                .collect(Collectors.toMap(EntityFieldDTO::getFieldCode, f -> f));
        Map<String, EntityFieldDTO> toFieldMap = toFields.stream()
                .collect(Collectors.toMap(EntityFieldDTO::getFieldCode, f -> f));

        // 比较字段
        for (EntityFieldDTO toField : toFields) {
            if (fromFieldMap.containsKey(toField.getFieldCode())) {
                EntityFieldDTO fromField = fromFieldMap.get(toField.getFieldCode());
                EntityVersionDiffDTO.FieldDiff fieldDiff = compareField(fromField, toField);

                if (fieldDiff.getChangeType() == EntityVersionDiffDTO.FieldDiff.ChangeType.UNCHANGED) {
                    diff.getUnchangedFields().add(fieldDiff);
                } else {
                    diff.getModifiedFields().add(fieldDiff);
                }
            } else {
                EntityVersionDiffDTO.FieldDiff fieldDiff = convertToFieldDiff(toField, EntityVersionDiffDTO.FieldDiff.ChangeType.ADD);
                diff.getAddedFields().add(fieldDiff);
            }
        }

        // 检查删除的字段
        for (EntityFieldDTO fromField : fromFields) {
            if (!toFieldMap.containsKey(fromField.getFieldCode()) && !Boolean.TRUE.equals(fromField.getIsSystem())) {
                EntityVersionDiffDTO.FieldDiff fieldDiff = convertToFieldDiff(fromField, EntityVersionDiffDTO.FieldDiff.ChangeType.REMOVE);
                diff.getRemovedFields().add(fieldDiff);
            }
        }

        diff.setChangeSummary(buildChangeSummary(diff));

        return diff;
    }

    /**
     * 比较单个字段的变化
     *
     * @param oldField 旧字段，作为 {@code changes.add} 的输入影响后续处理
     * @param newField 新字段，作为 {@code diff.setFieldId} 的输入影响后续处理
     * @return 比较后的字段结果，供调用方继续处理
     */
    private EntityVersionDiffDTO.FieldDiff compareField(EntityFieldDTO oldField, EntityField newField) {
        return compareField(oldField, EntityFieldViewMapper.fromField(newField));
    }

    /**
     * 比较字段；结果供调用方的后续步骤使用。
     *
     * @param oldField 旧字段，作为 {@code changes.add} 的输入影响后续处理
     * @param newField 新字段，作为 {@code diff.setFieldId} 的输入影响后续处理
     * @return 比较后的字段结果，供调用方继续处理
     */
    private EntityVersionDiffDTO.FieldDiff compareField(EntityFieldDTO oldField, EntityFieldDTO newField) {
        EntityVersionDiffDTO.FieldDiff diff = new EntityVersionDiffDTO.FieldDiff();
        diff.setFieldId(newField.getId());
        diff.setFieldCode(newField.getFieldCode());
        diff.setFieldName(newField.getFieldName());
        diff.setFieldType(newField.getFieldType() != null ? newField.getFieldType().name() : null);
        diff.setDbType(newField.getDbType());
        diff.setFieldLength(newField.getFieldLength());
        diff.setFieldPrecision(newField.getFieldPrecision());
        diff.setDbColumnName(newField.getDbColumnName());
        diff.setIsRequired(newField.getIsRequired());
        diff.setIsPublished(Boolean.TRUE.equals(newField.getIsPublished()));
        diff.setIsSystem(Boolean.TRUE.equals(newField.getIsSystem()));

        // 检查是否有变化
        List<String> changes = new ArrayList<>();

        if (!Objects.equals(oldField.getFieldName(), newField.getFieldName())) {
            changes.add("字段名称: " + oldField.getFieldName() + " → " + newField.getFieldName());
        }
        if (!Objects.equals(oldField.getIsRequired(), newField.getIsRequired())) {
            changes.add("必填: " + oldField.getIsRequired() + " → " + newField.getIsRequired());
        }
        if (!Objects.equals(oldField.getIsUnique(), newField.getIsUnique())) {
            changes.add("唯一: " + oldField.getIsUnique() + " → " + newField.getIsUnique());
        }
        if (!Objects.equals(oldField.getDefaultValue(), newField.getDefaultValue())) {
            changes.add("默认值: " + oldField.getDefaultValue() + " → " + newField.getDefaultValue());
        }
        // 字段长度/精度/列名：只有两边都不为null才比较，避免历史版本缺失这些数据导致误判
        if (oldField.getFieldLength() != null && newField.getFieldLength() != null
                && !Objects.equals(oldField.getFieldLength(), newField.getFieldLength())) {
            changes.add("字段长度: " + oldField.getFieldLength() + " → " + newField.getFieldLength());
        }
        if (oldField.getFieldPrecision() != null && newField.getFieldPrecision() != null
                && !Objects.equals(oldField.getFieldPrecision(), newField.getFieldPrecision())) {
            changes.add("小数位数: " + oldField.getFieldPrecision() + " → " + newField.getFieldPrecision());
        }
        if (oldField.getDbColumnName() != null && newField.getDbColumnName() != null
                && !Objects.equals(oldField.getDbColumnName(), newField.getDbColumnName())) {
            changes.add("数据库列名: " + oldField.getDbColumnName() + " → " + newField.getDbColumnName());
        }

        if (changes.isEmpty()) {
            diff.setChangeType(EntityVersionDiffDTO.FieldDiff.ChangeType.UNCHANGED);
            diff.setChangeDescription("无变化");
        } else {
            diff.setChangeType(EntityVersionDiffDTO.FieldDiff.ChangeType.MODIFY);
            diff.setChangeDescription(String.join("; ", changes));
        }

        return diff;
    }

    /**
     * 转换截止字段差异；输出作为后续校验或处理的输入。
     *
     * @param field 字段，作为 {@code diff.setFieldId} 的输入影响后续处理
     * @param changeType 变更类型标识，决定后续截止字段差异采用的处理分支
     * @return 转换后的截止字段差异结果，供调用方继续处理
     */
    private EntityVersionDiffDTO.FieldDiff convertToFieldDiff(EntityField field, EntityVersionDiffDTO.FieldDiff.ChangeType changeType) {
        return convertToFieldDiff(EntityFieldViewMapper.fromField(field), changeType);
    }

    /**
     * 转换截止字段差异；输出作为后续校验或处理的输入。
     *
     * @param field 字段，作为 {@code diff.setFieldId} 的输入影响后续处理
     * @param changeType 变更类型标识，决定后续截止字段差异采用的处理分支
     * @return 转换后的截止字段差异结果，供调用方继续处理
     */
    private EntityVersionDiffDTO.FieldDiff convertToFieldDiff(EntityFieldDTO field, EntityVersionDiffDTO.FieldDiff.ChangeType changeType) {
        EntityVersionDiffDTO.FieldDiff diff = new EntityVersionDiffDTO.FieldDiff();
        diff.setFieldId(field.getId());
        diff.setFieldCode(field.getFieldCode());
        diff.setFieldName(field.getFieldName());
        diff.setFieldType(field.getFieldType() != null ? field.getFieldType().name() : null);
        diff.setDbType(field.getDbType());
        diff.setFieldLength(field.getFieldLength());
        diff.setFieldPrecision(field.getFieldPrecision());
        diff.setDbColumnName(field.getDbColumnName());
        diff.setIsRequired(field.getIsRequired());
        diff.setIsPublished(Boolean.TRUE.equals(field.getIsPublished()));
        diff.setIsSystem(Boolean.TRUE.equals(field.getIsSystem()));
        diff.setChangeType(changeType);

        switch (changeType) {
            case ADD:
                diff.setChangeDescription("新增字段");
                break;
            case REMOVE:
                diff.setChangeDescription("删除字段");
                break;
            default:
                diff.setChangeDescription("");
        }

        return diff;
    }

    /**
     * 构建变更摘要；结果供后续流程传递或持久化。
     *
     * @param diff 差异，供本方法构建变更摘要时使用
     * @return 构建后的变更摘要文本，供调用方比较或展示
     */
    private String buildChangeSummary(EntityVersionDiffDTO diff) {
        StringBuilder summary = new StringBuilder();

        if (diff.getIsFirstPublish()) {
            summary.append("首次发布，创建数据表");
        } else {
            int addCount = diff.getAddedFields().size();
            int modifyCount = diff.getModifiedFields().size();
            int removeCount = diff.getRemovedFields().size();

            if (addCount > 0) {
                summary.append("新增 ").append(addCount).append(" 个字段");
            }
            if (modifyCount > 0) {
                if (summary.length() > 0) summary.append("，");
                summary.append("修改 ").append(modifyCount).append(" 个字段");
            }
            if (removeCount > 0) {
                if (summary.length() > 0) summary.append("，");
                summary.append("删除 ").append(removeCount).append(" 个字段");
            }
            if (summary.length() == 0) {
                summary.append("无字段变更");
            }
        }

        return summary.toString();
    }
}
