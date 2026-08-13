package com.workflow.entity.form.application;

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

    private boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String text) return text.trim().isEmpty();
        if (value instanceof Collection<?> values) return values.isEmpty();
        if (value instanceof Map<?, ?> map) return map.isEmpty();
        return value.getClass().isArray() && Array.getLength(value) == 0;
    }

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

    private String itemName(Map<String, Object> item, int index) {
        String name = text(item.get("itemName"));
        return StringUtils.hasText(name) ? name : "附件项" + (index + 1);
    }

    private String fieldLabel(EntityFormField field) {
        String label = StringUtils.hasText(field.getFieldLabel())
                ? field.getFieldLabel()
                : StringUtils.hasText(field.getFieldName())
                        ? field.getFieldName()
                        : field.getFieldCode();
        return "字段“" + label + "”";
    }

    private Map<String, Object> readObject(
            String document,
            String label) {
        return StringUtils.hasText(document)
                ? codec.readObject(document, label) : Map.of();
    }

    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map
                ? stringMap(map) : Map.of();
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) ->
                result.put(String.valueOf(key), value));
        return result;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean booleanValue(Object value) {
        return Boolean.TRUE.equals(value)
                || Integer.valueOf(1).equals(value)
                || "1".equals(String.valueOf(value));
    }

    private int integerValue(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private BusinessConflictException failure(String message) {
        return new BusinessConflictException(ERROR_CODE, message);
    }
}
