package com.workflow.entity.version.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDictItem;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.organization.application.SysOrganizationService;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldOptionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityFieldOption;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 生成业务实体不可变完整快照，并冻结当时的中文显示值。
 */
@Service
@RequiredArgsConstructor
public class EntityRecordSnapshotService {

    private static final List<SystemField> SYSTEM_FIELDS = List.of(
            new SystemField("id", "数据ID", "STRING"),
            new SystemField("dataNo", "业务编号", "STRING"),
            new SystemField("title", "标题", "STRING"),
            new SystemField("name", "名称", "STRING"),
            new SystemField("code", "编码", "STRING"),
            new SystemField("status", "实体状态", "STATUS"),
            new SystemField("processInstanceId", "流程实例ID", "STRING"),
            new SystemField("processStartTime", "流程开始时间", "DATETIME"),
            new SystemField("processEndTime", "流程结束时间", "DATETIME"),
            new SystemField("currentTaskId", "当前任务ID", "STRING"),
            new SystemField("currentTaskName", "当前任务名称", "STRING"),
            new SystemField("currentTaskAssignee", "当前任务办理人", "USER"),
            new SystemField("submitterId", "提交人ID", "USER"),
            new SystemField("submitterName", "提交人", "STRING"),
            new SystemField("deptId", "所属部门ID", "DEPT"),
            new SystemField("deptName", "所属部门", "STRING"),
            new SystemField("submitTime", "提交时间", "DATETIME"),
            new SystemField("createdAt", "创建时间", "DATETIME"),
            new SystemField("updatedAt", "更新时间", "DATETIME"),
            new SystemField("createdBy", "创建人", "USER"),
            new SystemField("updatedBy", "更新人", "USER"));

    private final EntityPublishedSnapshotService publishedSnapshotService;
    private final EntityFieldOptionMapper optionMapper;
    private final EntityStatusMapper statusMapper;
    private final SysDictItemService dictItemService;
    private final SysUserService userService;
    private final SysOrganizationService organizationService;
    private final ObjectMapper objectMapper;

    public SnapshotCapture capture(
            String entityCode,
            String recordId,
            Map<String, Object> aggregateRecord,
            boolean deletedSnapshot) {
        EntityPublishedSnapshot published =
                publishedSnapshotService
                        .getLatestByEntityCode(entityCode);
        Map<String, Object> record =
                deepCopy(aggregateRecord);
        Map<String, Object> customData =
                map(record.get("data"));
        List<Map<String, Object>> systemFields =
                captureSystemFields(
                        entityCode,
                        record);
        List<Map<String, Object>> businessFields =
                new ArrayList<>();
        List<Map<String, Object>> relationFields =
                new ArrayList<>();
        List<EntityField> publishedFields =
                published.getFields() == null
                        ? List.of()
                        : published.getFields();
        for (EntityField field : publishedFields) {
            Map<String, Object> item =
                    captureBusinessField(field, customData);
            if (isRelation(field)) {
                relationFields.add(item);
            } else {
                businessFields.add(item);
            }
        }
        List<Map<String, Object>> allFields =
                new ArrayList<>();
        allFields.addAll(systemFields);
        allFields.addAll(businessFields);
        allFields.addAll(relationFields);

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("entityId", published.getEntityId());
        entity.put("entityCode", published.getEntityCode());
        entity.put("entityName", published.getEntityName());
        entity.put("releaseId", published.getHistoryId());
        entity.put("releaseVersion", published.getVersion());

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("schemaVersion", 1);
        document.put("entity", entity);
        document.put("recordId", recordId);
        document.put("deletedSnapshot", deletedSnapshot);
        document.put("capturedAt", LocalDateTime.now());
        document.put("record", record);
        document.put("systemFields", systemFields);
        document.put("businessFields", businessFields);
        document.put("relationFields", relationFields);
        document.put("fields", allFields);

        Map<String, Object> hashMaterial =
                new LinkedHashMap<>();
        hashMaterial.put("entity", entity);
        hashMaterial.put("record", record);
        hashMaterial.put("fields", allFields);
        hashMaterial.put("deletedSnapshot", deletedSnapshot);
        return new SnapshotCapture(
                document,
                hash(hashMaterial),
                published.getHistoryId(),
                published.getVersion());
    }

    private List<Map<String, Object>> captureSystemFields(
            String entityCode,
            Map<String, Object> record) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SystemField field : SYSTEM_FIELDS) {
            Object value = record.get(field.code());
            result.add(field(
                    field.code(),
                    field.name(),
                    field.type(),
                    value,
                    displaySystemValue(
                            entityCode,
                            field.type(),
                            value),
                    "SYSTEM",
                    null));
        }
        return result;
    }

    private Map<String, Object> captureBusinessField(
            EntityField field,
            Map<String, Object> customData) {
        Object value = customData.get(field.getFieldCode());
        String group = switch (field.getFieldType()) {
            case SUB_FORM, SUB_FORM_LIST -> "SUBFORM";
            case REFERENCE, MULTI_REFERENCE -> "RELATION";
            default -> "BUSINESS";
        };
        return field(
                field.getFieldCode(),
                field.getFieldName(),
                field.getFieldType() == null
                        ? "UNKNOWN"
                        : field.getFieldType().name(),
                value,
                displayFieldValue(field, value),
                group,
                field.getSortOrder());
    }

    private Map<String, Object> field(
            String code,
            String name,
            String type,
            Object value,
            Object displayValue,
            String group,
            Integer sortOrder) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fieldCode", code);
        result.put("fieldName", name);
        result.put("fieldType", type);
        result.put("value", value);
        result.put("displayValue", displayValue);
        result.put("group", group);
        result.put("sortOrder", sortOrder);
        return result;
    }

    private Object displaySystemValue(
            String entityCode,
            String type,
            Object value) {
        if (value == null) {
            return null;
        }
        if ("STATUS".equals(type)) {
            EntityStatus status = statusMapper.findByEntityAndCode(
                    entityCode,
                    String.valueOf(value));
            return status == null
                    ? value : status.getStatusName();
        }
        if ("USER".equals(type)) {
            return displayUsers(value);
        }
        if ("DEPT".equals(type)) {
            return displayDepartments(value);
        }
        return value;
    }

    private Object displayFieldValue(
            EntityField field,
            Object value) {
        if (value == null) {
            return null;
        }
        if (field.getRefEntityType()
                == EntityField.RefEntityType.USER
                || field.getFieldType()
                == EntityField.FieldType.USER) {
            return displayUsers(value);
        }
        if (field.getRefEntityType()
                == EntityField.RefEntityType.DEPT
                || field.getFieldType()
                == EntityField.FieldType.DEPT) {
            return displayDepartments(value);
        }
        if (StringUtils.hasText(field.getDictType())) {
            return displayDictionary(
                    field.getDictType(),
                    value);
        }
        if (isOptionField(field)) {
            return displayOptions(field, value);
        }
        if (field.getFieldType()
                == EntityField.FieldType.BOOLEAN) {
            return Boolean.parseBoolean(
                    String.valueOf(value))
                    ? "是" : "否";
        }
        if (isRelation(field)) {
            return relationDisplay(value);
        }
        return value;
    }

    private Object displayUsers(Object value) {
        List<String> values = stringValues(value);
        if (values.isEmpty()) {
            return value;
        }
        return values.size() == 1
                ? userService.getDisplayName(values.get(0))
                : userService.getDisplayNames(values);
    }

    private Object displayDepartments(Object value) {
        List<String> values = stringValues(value);
        if (values.isEmpty()) {
            return value;
        }
        List<String> names = values.stream()
                .map(organizationService::getById)
                .filter(Objects::nonNull)
                .map(SysOrganization::getOrgName)
                .toList();
        return names.isEmpty()
                ? value : String.join(",", names);
    }

    private Object displayDictionary(
            String dictCode,
            Object value) {
        Map<String, String> labels = new LinkedHashMap<>();
        flattenDictItems(
                dictItemService.getItemTreeByDictCode(dictCode),
                labels);
        return displayMapped(value, labels);
    }

    private Object displayOptions(
            EntityField field,
            Object value) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (StringUtils.hasText(field.getId())) {
            for (EntityFieldOption option
                    : optionMapper.findByFieldId(field.getId())) {
                labels.put(
                        option.getOptionValue(),
                        option.getOptionLabel());
            }
        }
        if (labels.isEmpty()
                && StringUtils.hasText(field.getOptionsJson())) {
            for (Map<String, Object> option
                    : readOptions(field.getOptionsJson())) {
                String optionValue = firstText(
                        option.get("value"),
                        option.get("optionValue"),
                        option.get("code"));
                String optionLabel = firstText(
                        option.get("label"),
                        option.get("optionLabel"),
                        option.get("name"));
                if (optionValue != null) {
                    labels.put(optionValue,
                            optionLabel == null
                                    ? optionValue
                                    : optionLabel);
                }
            }
        }
        return displayMapped(value, labels);
    }

    private Object displayMapped(
            Object value,
            Map<String, String> labels) {
        List<String> values = stringValues(value);
        if (values.isEmpty()) {
            return value;
        }
        List<String> result = values.stream()
                .map(item -> labels.getOrDefault(item, item))
                .toList();
        return result.size() == 1
                ? result.get(0)
                : String.join(",", result);
    }

    private Object relationDisplay(Object value) {
        if (value instanceof Collection<?> values) {
            return values.stream()
                    .map(this::relationDisplay)
                    .toList();
        }
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of(
                    "displayName", "label", "name",
                    "title", "code", "id")) {
                if (map.get(key) != null) {
                    return map.get(key);
                }
            }
        }
        return value;
    }

    private boolean isRelation(EntityField field) {
        return switch (field.getFieldType()) {
            case REFERENCE, MULTI_REFERENCE,
                    SUB_FORM, SUB_FORM_LIST -> true;
            default -> false;
        };
    }

    private boolean isOptionField(EntityField field) {
        return switch (field.getFieldType()) {
            case SELECT, MULTI_SELECT, RADIO, CHECKBOX -> true;
            default -> false;
        };
    }

    private void flattenDictItems(
            List<SysDictItem> items,
            Map<String, String> labels) {
        if (items == null) {
            return;
        }
        for (SysDictItem item : items) {
            if (item.getItemValue() != null) {
                labels.put(item.getItemValue(),
                        item.getItemLabel());
            }
            if (item.getItemCode() != null) {
                labels.putIfAbsent(item.getItemCode(),
                        item.getItemLabel());
            }
            flattenDictItems(item.getChildren(), labels);
        }
    }

    private List<String> stringValues(Object value) {
        if (value instanceof Collection<?> values) {
            return values.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .toList();
        }
        if (value != null && value.getClass().isArray()) {
            return objectMapper.convertValue(
                    value,
                    new TypeReference<>() {
                    });
        }
        return value == null
                ? List.of()
                : List.of(String.valueOf(value));
    }

    private List<Map<String, Object>> readOptions(
            String json) {
        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<>() {
                    });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> source) {
            return (Map<String, Object>) source;
        }
        return Map.of();
    }

    private Map<String, Object> deepCopy(
            Map<String, Object> value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        return objectMapper.convertValue(
                value,
                new TypeReference<>() {
                });
    }

    private String hash(Object material) {
        try {
            String canonical = objectMapper.writer()
                    .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(material);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(
                                    StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "实体版本快照哈希生成失败",
                    exception);
        }
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value != null
                    && StringUtils.hasText(
                            String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private record SystemField(
            String code,
            String name,
            String type) {
    }

    public record SnapshotCapture(
            Map<String, Object> document,
            String hash,
            String entityReleaseId,
            Integer entityReleaseVersion) {
    }
}
