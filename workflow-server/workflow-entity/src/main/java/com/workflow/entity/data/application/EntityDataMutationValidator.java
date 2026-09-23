package com.workflow.entity.data.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityFieldFileItem;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.EntityFieldValidationRuleService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态实体写入前的发布字段和流程约束校验。
 */
@Component
@RequiredArgsConstructor
public class EntityDataMutationValidator {

    private final EntityDataDynamicMapper dynamicMapper;
    private final DynamicTableService dynamicTableService;
    private final EntityPublishedSnapshotService snapshotService;
    private final EntityRuntimeRecordMapper recordMapper;
    private final EntityFieldValidationRuleService fieldValidationRuleService;
    private final ObjectMapper objectMapper;
    @Autowired(required = false)
    private EntityUniqueValueService uniqueValueService;

    /**
     * 校验流程启动；不满足约束时阻止后续处理。
     *
     * @param requested 请求，供本方法校验流程启动时使用
     * @param definition 定义，供本方法校验流程启动时使用
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
    public void validateProcessStart(
            boolean requested,
            EntityDefinition definition) {
        if (!requested) {
            return;
        }
        if (definition.getLifecycleMode()
                != EntityDefinition.LifecycleMode.WORKFLOW) {
            throw new BusinessConflictException(
                    "ENTITY_WORKFLOW_NOT_SUPPORTED",
                    "独立业务实体不支持发起流程");
        }
        if (!StringUtils.hasText(
                definition.getProcessDefinitionId())) {
            throw new BusinessConflictException(
                    "ENTITY_WORKFLOW_NOT_READY",
                    "流程实体尚未绑定流程，不能发起");
        }
    }

    /**
     * 校验已发布字段；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param storageData 存储数据，作为 {@code validateRequired} 的输入影响后续处理
     * @param excludeId 排除ID，后续用于校验已发布字段时定位或关联目标
     */
    public void validatePublishedFields(
            String entityCode,
            Map<String, Object> storageData,
            String excludeId) {
        EntityPublishedSnapshot snapshot =
                snapshotService.getLatestByEntityCode(
                        entityCode);
        if (snapshot.getFields() == null
                || snapshot.getFields().isEmpty()) {
            return;
        }
        validateRequired(snapshot, storageData);
        validateRules(snapshot, storageData);
        validateUnique(
                entityCode,
                snapshot,
                storageData,
                excludeId);
    }

    /**
     * 校验必填；不满足约束时阻止后续处理。
     *
     * @param snapshot 快照，供本方法校验必填时使用
     * @param storageData 存储数据，供本方法校验必填时使用
     */
    private void validateRequired(
            EntityPublishedSnapshot snapshot,
            Map<String, Object> storageData) {
        for (EntityField field : snapshot.getFields()) {
            if (isRelationField(field)) {
                continue;
            }
            String columnName =
                    recordMapper.toColumnName(
                            field.getFieldCode());
            Object value = storageData.get(columnName);
            Object normalizedValue = isAttachmentField(field)
                    ? parseAttachmentValue(value)
                    : value;
            if (Boolean.TRUE.equals(field.getIsRequired())
                    && (isAttachmentField(field)
                            ? !hasAttachmentValue(normalizedValue)
                            : isBlank(normalizedValue))) {
                throw requiredFailure(
                        "字段必填: "
                                + field.getFieldName());
            }
            validateRequiredAttachmentItems(field, normalizedValue);
        }
    }

    /**
     * 校验必填附件条目；不满足约束时阻止后续处理。
     *
     * @param field 字段，作为 {@code throwMissingAttachmentItem} 的输入影响后续处理
     * @param value 待校验必填附件条目的原始输入，结果供调用方继续使用
     */
    private void validateRequiredAttachmentItems(
            EntityField field,
            Object value) {
        if (!isAttachmentField(field)) {
            return;
        }
        List<EntityFieldFileItem> fileItems =
                field.getFileItems() == null
                        ? List.of()
                        : field.getFileItems();
        List<Integer> requiredIndexes = new ArrayList<>();
        for (int index = 0; index < fileItems.size(); index++) {
            if (Boolean.TRUE.equals(
                    fileItems.get(index).getRequired())) {
                requiredIndexes.add(index);
            }
        }
        if (requiredIndexes.isEmpty()) {
            return;
        }
        if (!(value instanceof Map<?, ?>)) {
            if (requiredIndexes.size() == 1
                    && hasAttachmentFileValue(value)) {
                return;
            }
            throwMissingAttachmentItem(
                    field,
                    fileItems.get(requiredIndexes.get(0)));
        }
        Map<?, ?> groupedValue = (Map<?, ?>) value;
        for (Integer index : requiredIndexes) {
            EntityFieldFileItem item = fileItems.get(index);
            if (!hasAttachmentFileValue(
                    attachmentItemValue(
                            groupedValue,
                            item,
                            index))) {
                throwMissingAttachmentItem(field, item);
            }
        }
    }

    /**
     * 处理附件条目值，并将结果传给后续步骤。
     *
     * @param groupedValue {@code grouped}值，供本方法处理附件条目值时使用
     * @param item 条目，作为 {@code keys.add} 的输入影响后续处理
     * @param index 索引，作为 {@code keys.add} 的输入影响后续处理
     * @return 处理后的附件条目值结果，供调用方继续处理
     */
    private Object attachmentItemValue(
            Map<?, ?> groupedValue,
            EntityFieldFileItem item,
            int index) {
        List<String> keys = new ArrayList<>();
        if (StringUtils.hasText(item.getItemName())) {
            keys.add(item.getItemName());
        }
        if (StringUtils.hasText(item.getNameAliases())) {
            try {
                List<String> aliases = objectMapper.readValue(
                        item.getNameAliases(),
                        objectMapper.getTypeFactory()
                                .constructCollectionType(
                                        List.class,
                                        String.class));
                keys.addAll(aliases);
            } catch (Exception ignored) {
                // 历史别名损坏时继续尝试当前名称和稳定标识。
            }
        }
        if (StringUtils.hasText(item.getItemKey())) {
            keys.add(item.getItemKey());
        }
        keys.add("附件项" + (index + 1));
        for (String key : keys) {
            if (StringUtils.hasText(key)
                    && groupedValue.containsKey(key)) {
                return groupedValue.get(key);
            }
        }
        return null;
    }

    /**
     * 解析附件值；输出作为后续校验或处理的输入。
     *
     * @param value 待解析附件值的原始输入，结果供调用方继续使用
     * @return 解析后的附件值结果，供调用方继续处理
     */
    private Object parseAttachmentValue(Object value) {
        if (!(value instanceof String text)) {
            return value;
        }
        String normalized = text.trim();
        if (!normalized.startsWith("{")
                && !normalized.startsWith("[")) {
            return value;
        }
        try {
            return objectMapper.readValue(
                    normalized,
                    Object.class);
        } catch (Exception exception) {
            return value;
        }
    }

    /**
     * 判断是否具有附件值；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否具有附件值的原始输入，结果供调用方继续使用
     * @return 附件值条件成立时为 true，否则为 false
     */
    private boolean hasAttachmentValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return StringUtils.hasText(text);
        }
        if (value instanceof Iterable<?> values) {
            for (Object item : values) {
                if (hasAttachmentValue(item)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Map<?, ?> map) {
            List<String> fileUrlKeys = List.of(
                    "url",
                    "path",
                    "fileUrl");
            if (fileUrlKeys.stream()
                    .anyMatch(map::containsKey)) {
                return fileUrlKeys.stream()
                        .filter(map::containsKey)
                        .map(map::get)
                        .anyMatch(this::hasAttachmentValue);
            }
            if (List.of(
                    "name",
                    "originalName",
                    "size",
                    "type",
                    "uid",
                    "status").stream()
                    .anyMatch(map::containsKey)) {
                return false;
            }
            return map.values().stream()
                    .anyMatch(this::hasAttachmentValue);
        }
        return false;
    }

    /**
     * 判断是否具有附件文件值；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否具有附件文件值的原始输入，结果供调用方继续使用
     * @return 附件文件值条件成立时为 true，否则为 false
     */
    private boolean hasAttachmentFileValue(Object value) {
        Object parsed = parseAttachmentValue(value);
        if (parsed == null) {
            return false;
        }
        if (parsed instanceof String text) {
            return StringUtils.hasText(text);
        }
        if (parsed instanceof Iterable<?> values) {
            for (Object item : values) {
                if (hasAttachmentFileValue(item)) {
                    return true;
                }
            }
            return false;
        }
        if (parsed instanceof Map<?, ?> map) {
            return List.of("url", "path", "fileUrl").stream()
                    .filter(map::containsKey)
                    .map(map::get)
                    .anyMatch(this::hasAttachmentFileValue);
        }
        return false;
    }

    /**
     * 处理{@code throw}缺失附件条目，并将结果传给后续步骤。
     *
     * @param field 字段，作为 {@code requiredFailure} 的输入影响后续处理
     * @param item 条目，供本方法处理{@code throw}缺失附件条目时使用
     */
    private void throwMissingAttachmentItem(
            EntityField field,
            EntityFieldFileItem item) {
        String itemName = StringUtils.hasText(item.getItemName())
                ? item.getItemName()
                : "附件项";
        throw requiredFailure(
                field.getFieldName()
                        + "缺少必填附件项: "
                        + itemName);
    }

    /**
     * 构造必填失败异常，供调用方区分失败原因。
     *
     * @param message 消息，作为 {@code BusinessConflictException} 的输入影响后续处理
     * @return 处理后的必填失败结果，供调用方继续处理
     */
    private BusinessConflictException requiredFailure(
            String message) {
        return new BusinessConflictException(
                "FORM_REQUIRED_VALIDATION_FAILED",
                message);
    }

    /**
     * 校验唯一；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param snapshot 快照，作为 {@code EntityQueryConditions.fromPublishedFields} 的输入影响后续处理
     * @param storageData 存储数据，供本方法校验唯一时使用
     * @param excludeId 排除ID，后续用于校验唯一时定位或关联目标
     */
    private void validateUnique(
            String entityCode,
            EntityPublishedSnapshot snapshot,
            Map<String, Object> storageData,
            String excludeId) {
        String tableName =
                dynamicTableService.getTableName(entityCode);
        Map<String, Object> uniqueValues = new HashMap<>();
        for (EntityField field : snapshot.getFields()) {
            if (!Boolean.TRUE.equals(
                    field.getIsUnique())
                    || isRelationField(field)) {
                continue;
            }
            String columnName =
                    recordMapper.toColumnName(
                            field.getFieldCode());
            Object value = storageData.get(columnName);
            if (isBlank(value)) {
                continue;
            }
            uniqueValues.put(field.getFieldCode(), value);
            Map<String, Object> condition =
                    new HashMap<>();
            condition.put(columnName, value);
            condition.put(columnName + "_op", "EQ");
            if (StringUtils.hasText(excludeId)) {
                condition.put("id", excludeId);
                condition.put("id_op", "NE");
            }
            if (dynamicMapper.countByCondition(
                    tableName,
                    // EQ/NE 保持精确唯一性比较；数字和日期字符串按发布类型绑定，
                    // 避免唯一值预检依赖数据库隐式转换，与普通动态查询共用规则。
                    EntityQueryConditions.fromPublishedFields(condition, snapshot.getFields())) > 0) {
                throw new RuntimeException(
                        "字段值已存在: "
                                + field.getFieldName());
            }
        }
        if (uniqueValueService != null && StringUtils.hasText(excludeId)) {
            uniqueValueService.replace(entityCode, excludeId, uniqueValues);
        }
    }

    /**
     * 校验规则集合；不满足约束时阻止后续处理。
     *
     * @param snapshot 快照，供本方法校验规则集合时使用
     * @param storageData 存储数据，作为 {@code fieldValidationRuleService.validateValue} 的输入影响后续处理
     */
    private void validateRules(
            EntityPublishedSnapshot snapshot,
            Map<String, Object> storageData) {
        for (EntityField field : snapshot.getFields()) {
            if (isRelationField(field)) {
                continue;
            }
            String columnName =
                    recordMapper.toColumnName(
                            field.getFieldCode());
            fieldValidationRuleService.validateValue(
                    field,
                    storageData.get(columnName));
        }
    }

    /**
     * 判断是否关系字段；判断结果决定调用方的后续分支。
     *
     * @param field 字段，供本方法判断是否关系字段时使用
     * @return 关系字段条件成立时为 true，否则为 false
     */
    public boolean isRelationField(EntityField field) {
        return field.getFieldType()
                == EntityField.FieldType.SUB_FORM
                || field.getFieldType()
                == EntityField.FieldType.SUB_LIST;
    }

    /**
     * 判断是否附件字段；判断结果决定调用方的后续分支。
     *
     * @param field 字段，供本方法判断是否附件字段时使用
     * @return 附件字段条件成立时为 true，否则为 false
     */
    private boolean isAttachmentField(EntityField field) {
        return field.getFieldType() == EntityField.FieldType.FILE
                || field.getFieldType() == EntityField.FieldType.IMAGE;
    }

    /**
     * 判断是否空白；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否空白的原始输入，结果供调用方继续使用
     * @return 空白条件成立时为 true，否则为 false
     */
    private boolean isBlank(Object value) {
        return value == null
                || value instanceof String text
                && text.isBlank();
    }
}
