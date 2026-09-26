package com.workflow.entity.form.application.validation;

import com.workflow.entity.form.application.PublishedFormConditionEvaluator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityFieldFileItemMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityFieldFileItem;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 已发布表单条件必填校验，在提交前处理完成后执行。
 */
@Component
@RequiredArgsConstructor
public class PublishedFormRequiredValidator {

    public static final String ERROR_CODE =
            "FORM_REQUIRED_VALIDATION_FAILED";

    private final EntityDataDynamicService dataService;
    private final EntityFieldMapper entityFieldMapper;
    private final EntityFieldFileItemMapper fileItemMapper;
    private final PublishedFormConditionEvaluator conditionEvaluator;
    private final JsonDocumentCodec codec;
    private final ObjectMapper objectMapper;

    /**
     * 合并旧记录与本次提交结果后校验整字段和附件项逻辑必填。
     *
     * @param form 表单，作为 {@code currentAttachmentItems} 的输入影响后续处理
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param mode 模式标识，决定后续已发布表单必填采用的处理分支
     * @param submittedData 已提交数据，作为 {@code mergedRecord} 的输入影响后续处理
     */
    public void validate(
            EntityForm form,
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData) {
        if (form == null || form.getFields() == null
                || form.getFields().isEmpty()
                || !hasConditionalRequiredRules(form.getFields())) {
            return;
        }
        Map<String, Object> record = mergedRecord(
                entityCode,
                recordId,
                submittedData);
        Map<String, List<EntityFieldFileItem>> currentItems =
                currentAttachmentItems(form);
        for (EntityFormField field : form.getFields()) {
            validateField(
                    field,
                    mode,
                    record,
                    currentItems.getOrDefault(
                            field.getFieldCode(),
                            List.of()));
        }
    }

    /**
     * 判断是否具有{@code conditional}必填规则集合；判断结果决定调用方的后续分支。
     *
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @return {@code conditional}必填规则集合条件成立时为 true，否则为 false
     */
    private boolean hasConditionalRequiredRules(
            List<EntityFormField> fields) {
        for (EntityFormField field : fields) {
            if (field == null) {
                continue;
            }
            if (Integer.valueOf(1).equals(field.getIsRequired())) {
                return true;
            }
            if (!StringUtils.hasText(field.getComponentProps())) continue;
            Map<String, Object> componentProps = readObject(
                    field.getComponentProps(),
                    "已发布字段组件配置");
            Map<String, Object> linkageRules = mapValue(
                    componentProps.get("linkageRules"));
            List<?> fileItems = componentProps.get("fileItems")
                    instanceof List<?> list ? list : List.of();
            if (linkageRules.containsKey("requiredConditionConfig")
                    || StringUtils.hasText(text(
                            linkageRules.get("requiredRule")))
                    || componentProps.containsKey(
                            "attachmentItemRequiredRules")
                    || linkageRules.containsKey(
                            "attachmentItemRequiredRules")
                    || fileItems.stream().anyMatch(value ->
                            value instanceof Map<?, ?> map
                                    && booleanValue(map.get("required")))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验字段；不满足约束时阻止后续处理。
     *
     * @param field 字段，作为 {@code readObject} 的输入影响后续处理
     * @param mode 模式标识，决定后续字段采用的处理分支
     * @param record 记录，作为 {@code visible} 的输入影响后续处理
     * @param currentItems 当前条目，供本方法校验字段时使用
     */
    private void validateField(
            EntityFormField field,
            String mode,
            Map<String, Object> record,
            List<EntityFieldFileItem> currentItems) {
        if (field == null || !StringUtils.hasText(field.getFieldCode())) {
            return;
        }
        Map<String, Object> componentProps = readObject(
                field.getComponentProps(),
                "已发布字段组件配置");
        Map<String, Object> linkageRules = mapValue(
                componentProps.get("linkageRules"));
        boolean fieldVisible = visible(
                field,
                mode,
                linkageRules,
                record);
        Object value = parseJsonValue(record.get(field.getFieldCode()));
        if (fieldVisible) {
            boolean required = Integer.valueOf(1).equals(
                    field.getIsRequired())
                    || conditionEvaluator.evaluate(
                            linkageRules.get(
                                    "requiredConditionConfig"),
                            text(linkageRules.get("requiredRule")),
                            record,
                            false);
            if (required
                    && !hasFieldValue(field, value)) {
                throw failure(fieldLabel(field) + "为必填项");
            }
        }

        Object attachmentRules = componentProps.containsKey(
                "attachmentItemRequiredRules")
                ? componentProps.get("attachmentItemRequiredRules")
                : linkageRules.get("attachmentItemRequiredRules");
        validateAttachmentItems(
                field,
                componentProps,
                attachmentRules,
                value,
                record,
                fieldVisible,
                currentItems);
    }

    /**
     * 判断可见条件是否成立，供调用方选择后续分支。
     *
     * @param field 字段，作为 {@code readObject} 的输入影响后续处理
     * @param mode 模式标识，决定后续可见采用的处理分支
     * @param linkageRules {@code linkage}规则集合，作为 {@code conditionEvaluator.evaluate} 的输入影响后续处理
     * @param record 记录，供本方法处理可见时使用
     * @return 可见条件成立时为 true，否则为 false
     */
    private boolean visible(
            EntityFormField field,
            String mode,
            Map<String, Object> linkageRules,
            Map<String, Object> record) {
        if (Integer.valueOf(1).equals(field.getIsHidden())) {
            return false;
        }
        Map<String, Object> extension = readObject(
                field.getExtensionConfig(),
                "已发布字段扩展配置");
        Map<String, Object> modes = mapValue(extension.get("modes"));
        Map<String, Object> access = mapValue(modes.get(
                StringUtils.hasText(mode)
                        ? mode.trim().toLowerCase()
                        : "edit"));
        if (Boolean.FALSE.equals(access.get("visible"))) {
            return false;
        }
        return conditionEvaluator.evaluate(
                linkageRules.get("visibilityConditionConfig"),
                text(linkageRules.get("visibilityRule")),
                record,
                true);
    }

    /**
     * 校验附件条目；不满足约束时阻止后续处理。
     *
     * @param field 字段，作为 {@code failure} 的输入影响后续处理
     * @param componentProps 组件属性，供本方法校验附件条目时使用
     * @param configured 已配置，作为 {@code mapValue} 的输入影响后续处理
     * @param fieldValue 字段值，作为 {@code attachmentItemValue} 的输入影响后续处理
     * @param record 记录，供本方法校验附件条目时使用
     * @param fieldVisible 字段可见，供本方法校验附件条目时使用
     * @param currentItems 当前条目，作为 {@code attachmentItemValue} 的输入影响后续处理
     */
    private void validateAttachmentItems(
            EntityFormField field,
            Map<String, Object> componentProps,
            Object configured,
            Object fieldValue,
            Map<String, Object> record,
            boolean fieldVisible,
            List<EntityFieldFileItem> currentItems) {
        Map<String, Object> rules = mapValue(configured);
        if (configured != null && integerValue(rules.get("version")) != 1) {
            throw failure(fieldLabel(field)
                    + "附件项逻辑必填配置版本无效");
        }
        List<?> configuredItems = rules.get("items") instanceof List<?> list
                ? list : List.of();
        List<?> fileItems = componentProps.get("fileItems") instanceof List<?> list
                ? list : List.of();
        Map<String, Map<String, Object>> fileItemsByKey =
                new LinkedHashMap<>();
        for (Object value : fileItems) {
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> item = stringMap(map);
                String itemKey = text(item.get("itemKey"));
                if (StringUtils.hasText(itemKey)) {
                    fileItemsByKey.put(itemKey, item);
                }
            }
        }
        Map<String, Map<String, Object>> rulesByKey =
                new LinkedHashMap<>();
        for (Object value : configuredItems) {
            if (!(value instanceof Map<?, ?> configuredItemValue)) {
                throw failure(fieldLabel(field) + "附件项逻辑必填配置无效");
            }
            Map<String, Object> configuredItem =
                    stringMap(configuredItemValue);
            String itemKey = text(configuredItem.get("itemKey"));
            Map<String, Object> item = fileItemsByKey.get(itemKey);
            if (item == null) {
                throw failure(fieldLabel(field)
                        + "附件项逻辑必填引用已失效: " + itemKey);
            }
            rulesByKey.put(itemKey, configuredItem);
        }
        for (int itemIndex = 0; itemIndex < fileItems.size(); itemIndex++) {
            Object snapshotValue = fileItems.get(itemIndex);
            if (!(snapshotValue instanceof Map<?, ?> snapshotMap)) {
                continue;
            }
            Map<String, Object> item = stringMap(snapshotMap);
            String itemKey = text(item.get("itemKey"));
            Map<String, Object> configuredItem = rulesByKey.get(itemKey);
            boolean required = booleanValue(item.get("required"))
                    || fieldVisible
                    && configuredItem != null
                    && conditionEvaluator.evaluateStructured(
                            configuredItem.get(
                                    "requiredConditionConfig"),
                            record);
            if (!required) {
                continue;
            }
            Object itemValue = attachmentItemValue(
                    fieldValue,
                    item,
                    itemIndex,
                    currentItems);
            if (!hasAttachmentFileValue(itemValue)) {
                throw failure(fieldLabel(field)
                        + "的附件项“"
                        + itemName(item, itemIndex)
                        + "”至少需要上传一份文件");
            }
        }
    }

    /**
     * 整理{@code merged}记录数据，供调用方遍历或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param submittedData 已提交数据，作为 {@code flattenSubmitted} 的输入影响后续处理
     * @return {@code merged}记录键值结果，供调用方继续处理
     */
    private Map<String, Object> mergedRecord(
            String entityCode,
            String recordId,
            Map<String, Object> submittedData) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (StringUtils.hasText(entityCode)
                && StringUtils.hasText(recordId)) {
            EntityDataDTO existing = dataService.findById(
                    entityCode,
                    recordId);
            Map<String, Object> standard = objectMapper.convertValue(
                    existing,
                    new TypeReference<Map<String, Object>>() {});
            Object customData = standard.remove("data");
            result.putAll(standard);
            if (customData instanceof Map<?, ?> map) {
                result.putAll(stringMap(map));
            }
        }
        Map<String, Object> submitted = flattenSubmitted(submittedData);
        // Entity updates are patches at field level. Omitted fields keep their
        // database value, while a submitted field replaces that value as a
        // whole. Validation must follow the same rule as persistence.
        submitted.forEach((key, value) ->
                result.put(key, parseJsonValue(value)));
        return result;
    }

    /**
     * 整理{@code flatten}已提交数据，供调用方遍历或继续处理。
     *
     * @param submittedData 已提交数据，作为 {@code result.putAll} 的输入影响后续处理
     * @return {@code flatten}已提交键值结果，供调用方继续处理
     */
    private Map<String, Object> flattenSubmitted(
            Map<String, Object> submittedData) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (submittedData == null) {
            return result;
        }
        result.putAll(submittedData);
        Object nested = result.remove("data");
        if (nested instanceof Map<?, ?> map) {
            result.putAll(stringMap(map));
        }
        return result;
    }

    /**
     * 处理附件条目值，并将结果传给后续步骤。
     *
     * @param value 待处理附件条目值的原始输入，结果供调用方继续使用
     * @param item 条目，作为 {@code findCurrentItem} 的输入影响后续处理
     * @param index 索引，作为 {@code keys.add} 的输入影响后续处理
     * @param currentItems 当前条目，作为 {@code findCurrentItem} 的输入影响后续处理
     * @return 处理后的附件条目值结果，供调用方继续处理
     */
    private Object attachmentItemValue(
            Object value,
            Map<String, Object> item,
            int index,
            List<EntityFieldFileItem> currentItems) {
        Object parsed = parseJsonValue(value);
        if (!(parsed instanceof Map<?, ?> map)) {
            return parsed;
        }
        List<String> keys = new ArrayList<>();
        EntityFieldFileItem currentItem = findCurrentItem(
                item,
                currentItems);
        if (currentItem != null) {
            keys.add(currentItem.getItemName());
            keys.addAll(aliases(currentItem.getNameAliases()));
        }
        keys.add(text(item.get("itemName")));
        keys.addAll(aliases(item.get("nameAliases")));
        keys.add(text(item.get("itemKey")));
        keys.add("附件项" + (index + 1));
        for (String key : keys) {
            if (StringUtils.hasText(key) && map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    /**
     * 整理当前附件条目数据，供调用方遍历或继续处理。
     *
     * @param form 表单，作为 {@code entityFieldMapper.findByEntityId} 的输入影响后续处理
     * @return 当前附件条目键值结果，供调用方继续处理
     */
    private Map<String, List<EntityFieldFileItem>> currentAttachmentItems(
            EntityForm form) {
        if (form == null || !StringUtils.hasText(form.getEntityId())) {
            return Map.of();
        }
        Set<String> attachmentFieldCodes = new LinkedHashSet<>();
        if (form.getFields() != null) {
            for (EntityFormField field : form.getFields()) {
                if (field != null
                        && StringUtils.hasText(field.getFieldCode())
                        && ("FILE".equalsIgnoreCase(field.getFieldType())
                        || "IMAGE".equalsIgnoreCase(field.getFieldType()))) {
                    attachmentFieldCodes.add(field.getFieldCode());
                }
            }
        }
        if (attachmentFieldCodes.isEmpty()) {
            return Map.of();
        }
        List<EntityField> fields = entityFieldMapper.findByEntityId(
                form.getEntityId());
        if (fields == null || fields.isEmpty()) {
            return Map.of();
        }
        Map<String, List<EntityFieldFileItem>> result =
                new LinkedHashMap<>();
        for (EntityField field : fields) {
            if (field != null && StringUtils.hasText(field.getId())
                    && attachmentFieldCodes.contains(
                            field.getFieldCode())) {
                List<EntityFieldFileItem> items =
                        fileItemMapper.findByFieldId(field.getId());
                result.put(
                        field.getFieldCode(),
                        items == null ? List.of() : items);
            }
        }
        return result;
    }

    /**
     * 查询当前条目；查询结果供调用方展示或继续处理。
     *
     * @param snapshotItem 快照条目，作为 {@code text} 的输入影响后续处理
     * @param currentItems 当前条目，供本方法查询当前条目时使用
     * @return 符合条件的实体字段文件条目结果，供调用方继续处理
     */
    private EntityFieldFileItem findCurrentItem(
            Map<String, Object> snapshotItem,
            List<EntityFieldFileItem> currentItems) {
        String itemKey = text(snapshotItem.get("itemKey"));
        for (EntityFieldFileItem current : currentItems) {
            if (current != null && StringUtils.hasText(itemKey)
                    && itemKey.equals(current.getItemKey())) {
                return current;
            }
        }
        Set<String> snapshotNames = new LinkedHashSet<>();
        snapshotNames.add(text(snapshotItem.get("itemName")));
        snapshotNames.addAll(aliases(snapshotItem.get("nameAliases")));
        for (EntityFieldFileItem current : currentItems) {
            if (current == null) {
                continue;
            }
            Set<String> names = new LinkedHashSet<>();
            names.add(current.getItemName());
            names.addAll(aliases(current.getNameAliases()));
            if (snapshotNames.stream()
                    .filter(StringUtils::hasText)
                    .anyMatch(names::contains)) {
                return current;
            }
        }
        return null;
    }

    /**
     * 整理{@code aliases}数据，供调用方遍历或继续处理。
     *
     * @param value 待处理{@code aliases}的原始输入，结果供调用方继续使用
     * @return 已发布表单必填校验器集合，供调用方遍历或展示
     */
    private List<String> aliases(Object value) {
        Object parsed = parseJsonValue(value);
        if (!(parsed instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(this::text)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    /**
     * 判断是否具有字段值；判断结果决定调用方的后续分支。
     *
     * @param field 字段，供本方法判断是否具有字段值时使用
     * @param value 待判断是否具有字段值的原始输入，结果供调用方继续使用
     * @return 字段值条件成立时为 true，否则为 false
     */
    private boolean hasFieldValue(
            EntityFormField field,
            Object value) {
        String type = String.valueOf(field.getFieldType())
                .trim()
                .toUpperCase();
        return List.of("FILE", "IMAGE").contains(type)
                ? hasAttachmentValue(value)
                : !isEmpty(value);
    }

    /**
     * 判断是否具有附件值；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否具有附件值的原始输入，结果供调用方继续使用
     * @return 附件值条件成立时为 true，否则为 false
     */
    private boolean hasAttachmentValue(Object value) {
        Object parsed = parseJsonValue(value);
        if (parsed == null) {
            return false;
        }
        if (parsed instanceof String text) {
            return StringUtils.hasText(text);
        }
        if (parsed instanceof Collection<?> values) {
            return values.stream().anyMatch(this::hasAttachmentValue);
        }
        if (parsed instanceof Map<?, ?> map) {
            List<String> urlKeys = List.of("url", "path", "fileUrl");
            if (urlKeys.stream().anyMatch(map::containsKey)) {
                return urlKeys.stream()
                        .filter(map::containsKey)
                        .map(map::get)
                        .anyMatch(this::hasAttachmentValue);
            }
            if (List.of("name", "originalName", "size", "type",
                    "uid", "status").stream().anyMatch(map::containsKey)) {
                return false;
            }
            return map.values().stream().anyMatch(this::hasAttachmentValue);
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
        Object parsed = parseJsonValue(value);
        if (parsed == null) {
            return false;
        }
        if (parsed instanceof String text) {
            return StringUtils.hasText(text);
        }
        if (parsed instanceof Collection<?> values) {
            return values.stream().anyMatch(this::hasAttachmentFileValue);
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
     * 判断是否空；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否空的原始输入，结果供调用方继续使用
     * @return 空条件成立时为 true，否则为 false
     */
    private boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String text) return text.trim().isEmpty();
        if (value instanceof Collection<?> values) return values.isEmpty();
        if (value instanceof Map<?, ?> map) return map.isEmpty();
        return value.getClass().isArray() && Array.getLength(value) == 0;
    }

    /**
     * 解析JSON值；输出作为后续校验或处理的输入。
     *
     * @param value 待解析JSON值的原始输入，结果供调用方继续使用
     * @return 解析后的JSON值结果，供调用方继续处理
     */
    private Object parseJsonValue(Object value) {
        if (!(value instanceof String text)) {
            return value;
        }
        String normalized = text.trim();
        if ((!normalized.startsWith("{") || !normalized.endsWith("}"))
                && (!normalized.startsWith("[") || !normalized.endsWith("]"))) {
            return value;
        }
        try {
            return objectMapper.readValue(normalized, Object.class);
        } catch (Exception exception) {
            return value;
        }
    }

    /**
     * 生成条目名称文本，供后续匹配或展示。
     *
     * @param item 条目，作为 {@code text} 的输入影响后续处理
     * @param index 索引，供本方法处理条目名称时使用
     * @return 处理后的条目名称文本，供调用方比较或展示
     */
    private String itemName(Map<String, Object> item, int index) {
        String name = text(item.get("itemName"));
        return StringUtils.hasText(name) ? name : "附件项" + (index + 1);
    }

    /**
     * 生成字段标签文本，供后续匹配或展示。
     *
     * @param field 字段，供本方法处理字段标签时使用
     * @return 处理后的字段标签文本，供调用方比较或展示
     */
    private String fieldLabel(EntityFormField field) {
        String label = StringUtils.hasText(field.getFieldLabel())
                ? field.getFieldLabel()
                : StringUtils.hasText(field.getFieldName())
                        ? field.getFieldName()
                        : field.getFieldCode();
        return "字段“" + label + "”";
    }

    /**
     * 读取对象；查询结果供调用方展示或继续处理。
     *
     * @param document 文档，供本方法读取对象时使用
     * @param label 标签，后续用于读取对象时匹配或展示
     * @return 对象键值结果，供调用方继续处理
     */
    private Map<String, Object> readObject(
            String document,
            String label) {
        return StringUtils.hasText(document)
                ? codec.readObject(document, label) : Map.of();
    }

    /**
     * 将动态值转换为键值映射，供后续字段读取和校验。
     *
     * @param value 待处理映射值的原始输入，结果供调用方继续使用
     * @return 映射值键值结果，供调用方继续处理
     */
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map
                ? stringMap(map) : Map.of();
    }

    /**
     * 将输入映射的键规范为字符串，供后续序列化和字段读取。
     *
     * @param source 待处理字符串映射的原始输入，结果供调用方继续使用
     * @return 字符串映射键值结果，供调用方继续处理
     */
    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) ->
                result.put(String.valueOf(key), value));
        return result;
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 将输入解析为布尔值，供后续条件判断使用。
     *
     * @param value 待处理布尔值值的原始输入，结果供调用方继续使用
     * @return 布尔值值条件成立时为 true，否则为 false
     */
    private boolean booleanValue(Object value) {
        return Boolean.TRUE.equals(value)
                || Integer.valueOf(1).equals(value)
                || "1".equals(String.valueOf(value));
    }

    /**
     * 处理整数值，并将结果传给后续步骤。
     *
     * @param value 待处理整数值的原始输入，结果供调用方继续使用
     * @return 处理后的整数值结果，供调用方继续处理
     */
    private int integerValue(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    /**
     * 构造失败异常，供调用方区分失败原因。
     *
     * @param message 消息，作为 {@code BusinessConflictException} 的输入影响后续处理
     * @return 处理后的失败结果，供调用方继续处理
     */
    private BusinessConflictException failure(String message) {
        return new BusinessConflictException(ERROR_CODE, message);
    }
}
