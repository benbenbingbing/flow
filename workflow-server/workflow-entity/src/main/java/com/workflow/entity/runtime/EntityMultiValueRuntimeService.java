package com.workflow.entity.runtime;

import com.workflow.common.UserContext;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.service.DynamicTableService;
import com.workflow.service.EntityPhysicalTableResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 每实体多值引用表运行时。
 */
@Service
@RequiredArgsConstructor
public class EntityMultiValueRuntimeService {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final JdbcTemplate jdbcTemplate;
    private final EntityFieldMapper fieldMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final DynamicTableService dynamicTableService;
    private final EntityPhysicalTableResolver tableResolver;

    public Map<String, List<String>> extractConfiguredValues(
            EntityDefinition definition,
            Map<String, Object> data) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        if (definition == null || data == null) {
            return values;
        }
        for (EntityField field : multiValueFields(definition.getId())) {
            Object raw = data.remove(field.getFieldCode());
            if (raw == null) {
                raw = data.remove(toSnakeCase(field.getFieldCode()));
            }
            if (raw != null) {
                values.put(field.getFieldCode(), normalizeValues(raw));
            }
        }
        return values;
    }

    public void validateScalarDictValues(
            EntityDefinition definition,
            Map<String, Object> data) {
        if (definition == null || data == null || data.isEmpty()) {
            return;
        }
        for (EntityField field : fieldMapper.findByEntityId(definition.getId())) {
            if (!StringUtils.hasText(field.getDictType())
                    || field.getFieldType() == EntityField.FieldType.MULTI_SELECT
                    || field.getFieldType() == EntityField.FieldType.CHECKBOX) {
                continue;
            }
            Object value = data.get(field.getFieldCode());
            if (value == null) {
                value = data.get(toSnakeCase(field.getFieldCode()));
            }
            if (value == null || !StringUtils.hasText(String.valueOf(value))) {
                continue;
            }
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_dict_item WHERE dict_code = ?"
                            + " AND item_code = ? AND status = '0' AND deleted = 0",
                    Integer.class,
                    field.getDictType(),
                    String.valueOf(value));
            if (count == null || count == 0) {
                throw new IllegalArgumentException(
                        "字段 " + field.getFieldName() + " 包含无效代码值: " + value);
            }
        }
    }

    @Transactional
    public void save(
            EntityDefinition definition,
            String recordId,
            Map<String, List<String>> values) {
        if (definition == null || !StringUtils.hasText(recordId) || values == null || values.isEmpty()) {
            return;
        }
        Map<String, EntityField> fields = new LinkedHashMap<>();
        for (EntityField field : multiValueFields(definition.getId())) {
            fields.put(field.getFieldCode(), field);
        }
        String multiTable = dynamicTableService.getMultiValueTableName(definition.getEntityCode());
        for (Map.Entry<String, List<String>> entry : values.entrySet()) {
            EntityField field = fields.get(entry.getKey());
            if (field == null) {
                continue;
            }
            jdbcTemplate.update(
                    "DELETE FROM " + multiTable + " WHERE record_id = ? AND field_code = ?",
                    recordId,
                    field.getFieldCode());
            List<String> targetIds = resolveTargetRecordIds(field, entry.getValue());
            for (int index = 0; index < targetIds.size(); index++) {
                jdbcTemplate.update(
                        "INSERT INTO " + multiTable
                                + " (id, record_id, field_code, target_entity_id, target_record_id,"
                                + " sort_order, deleted, create_by, create_time, update_time)"
                                + " VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?)",
                        UUID.randomUUID().toString().replace("-", ""),
                        recordId,
                        field.getFieldCode(),
                        targetEntityId(field),
                        targetIds.get(index),
                        index,
                        UserContext.getUserId(),
                        LocalDateTime.now(),
                        LocalDateTime.now());
            }
        }
    }

    public void enrich(EntityDefinition definition, Collection<com.workflow.dto.EntityDataDTO> records) {
        if (definition == null || records == null || records.isEmpty()) {
            return;
        }
        List<String> recordIds = records.stream()
                .map(com.workflow.dto.EntityDataDTO::getId)
                .filter(StringUtils::hasText)
                .toList();
        if (recordIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(recordIds.size(), "?"));
        String multiTable = dynamicTableService.getMultiValueTableName(definition.getEntityCode());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT record_id, field_code, target_entity_id, target_record_id, sort_order FROM " + multiTable
                        + " WHERE deleted = 0 AND record_id IN (" + placeholders + ")"
                        + " ORDER BY record_id, field_code, sort_order",
                recordIds.toArray());
        Map<String, EntityField> fields = new LinkedHashMap<>();
        for (EntityField field : multiValueFields(definition.getId())) {
            fields.put(field.getFieldCode(), field);
        }
        Map<String, Map<String, List<String>>> valuesByRecord = new LinkedHashMap<>();
        Map<String, Map<String, List<Map<String, Object>>>> optionsByRecord = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String recordId = String.valueOf(row.get("record_id"));
            String fieldCode = String.valueOf(row.get("field_code"));
            EntityField field = fields.get(fieldCode);
            if (field == null) {
                continue;
            }
            String targetId = String.valueOf(row.get("target_record_id"));
            String apiValue = resolveApiValue(field, targetId);
            Map<String, Object> option = resolveOption(field, targetId);
            valuesByRecord
                    .computeIfAbsent(recordId, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(fieldCode, ignored -> new ArrayList<>())
                    .add(apiValue);
            optionsByRecord
                    .computeIfAbsent(recordId, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(fieldCode, ignored -> new ArrayList<>())
                    .add(option);
        }
        for (com.workflow.dto.EntityDataDTO record : records) {
            Map<String, List<String>> recordValues = valuesByRecord.get(record.getId());
            if (recordValues == null) {
                continue;
            }
            if (record.getData() == null) {
                record.setData(new LinkedHashMap<>());
            }
            record.getData().putAll(recordValues);
            if (record.getExtData() == null) {
                record.setExtData(new LinkedHashMap<>());
            }
            Map<String, List<Map<String, Object>>> recordOptions = optionsByRecord.get(record.getId());
            if (recordOptions != null) {
                for (Map.Entry<String, List<Map<String, Object>>> entry : recordOptions.entrySet()) {
                    record.getExtData().put(entry.getKey() + "Options", entry.getValue());
                    record.getExtData().put(
                            entry.getKey() + "Display",
                            entry.getValue().stream()
                                    .map(option -> String.valueOf(option.get("label")))
                                    .toList());
                }
            }
            enrichScalarDictOptions(definition, record);
        }
    }

    public PreparedConditions prepareConditions(
            EntityDefinition definition,
            Map<String, Object> sourceCondition) {
        Map<String, Object> condition = sourceCondition == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(sourceCondition);
        if (definition == null || condition.isEmpty()) {
            return new PreparedConditions(condition, null);
        }
        String businessTable = dynamicTableService.getTableName(definition.getEntityCode());
        String multiTable = dynamicTableService.getMultiValueTableName(definition.getEntityCode());
        List<String> fragments = new ArrayList<>();
        int sequence = 0;
        for (EntityField field : multiValueFields(definition.getId())) {
            String fieldKey = condition.containsKey(field.getFieldCode())
                    ? field.getFieldCode()
                    : toSnakeCase(field.getFieldCode());
            String labelKey = field.getFieldCode() + "_label";
            if (!condition.containsKey(labelKey)) {
                String snakeLabelKey = toSnakeCase(field.getFieldCode()) + "_label";
                if (condition.containsKey(snakeLabelKey)) {
                    labelKey = snakeLabelKey;
                }
            }
            Object rawValue = condition.remove(fieldKey);
            String operation = text(condition.remove(fieldKey + "_op"));
            Object rawLabel = condition.remove(labelKey);
            String labelOperation = text(condition.remove(labelKey + "_op"));
            if (rawValue == null && rawLabel == null && !"EMPTY".equalsIgnoreCase(operation)) {
                continue;
            }
            String entityParam = "__multi_entity_" + sequence;
            String fieldParam = "__multi_field_" + sequence;
            condition.put(entityParam, targetEntityId(field));
            condition.put(fieldParam, field.getFieldCode());
            String base = "SELECT 1 FROM " + multiTable + " mv"
                    + " WHERE mv.record_id = " + businessTable + ".id"
                    + " AND mv.field_code = #{condition." + fieldParam + "}"
                    + " AND mv.target_entity_id = #{condition." + entityParam + "}"
                    + " AND mv.deleted = 0";
            if (rawLabel != null) {
                String labelParam = "__multi_label_" + sequence;
                condition.put(labelParam, rawLabel);
                fragments.add(buildLabelExists(
                        field,
                        multiTable,
                        base,
                        labelParam,
                        labelOperation));
            } else if ("EMPTY".equalsIgnoreCase(operation)) {
                fragments.add("NOT EXISTS (" + base + ")");
            } else {
                List<String> targetIds = resolveTargetRecordIds(field, normalizeValues(rawValue));
                if (targetIds.isEmpty()) {
                    fragments.add("1=0");
                } else {
                    List<String> placeholders = new ArrayList<>();
                    for (int index = 0; index < targetIds.size(); index++) {
                        String valueParam = "__multi_value_" + sequence + "_" + index;
                        condition.put(valueParam, targetIds.get(index));
                        placeholders.add("#{condition." + valueParam + "}");
                    }
                    String exists = "EXISTS (" + base
                            + " AND mv.target_record_id IN (" + String.join(",", placeholders) + "))";
                    if ("NOT_CONTAINS".equalsIgnoreCase(operation)
                            || "NE".equalsIgnoreCase(operation)) {
                        exists = "NOT " + exists;
                    }
                    fragments.add(exists);
                }
            }
            sequence++;
        }
        return new PreparedConditions(
                condition,
                fragments.isEmpty() ? null : String.join(" AND ", fragments));
    }

    @Transactional
    public void delete(String entityCode, String recordId) {
        jdbcTemplate.update(
                "DELETE FROM " + dynamicTableService.getMultiValueTableName(entityCode)
                        + " WHERE record_id = ?",
                recordId);
    }

    private List<EntityField> multiValueFields(String entityId) {
        return fieldMapper.findByEntityId(entityId).stream()
                .filter(this::isConfiguredMultiValue)
                .toList();
    }

    private boolean isConfiguredMultiValue(EntityField field) {
        if (field.getFieldType() == EntityField.FieldType.MULTI_REFERENCE) {
            return StringUtils.hasText(field.getRefEntityId());
        }
        return (field.getFieldType() == EntityField.FieldType.MULTI_SELECT
                || field.getFieldType() == EntityField.FieldType.CHECKBOX)
                && StringUtils.hasText(field.getDictType());
    }

    private String targetEntityId(EntityField field) {
        if (StringUtils.hasText(field.getDictType())) {
            return definitionMapper.findByEntityCode("sys_dict_item")
                    .map(EntityDefinition::getId)
                    .orElseThrow(() -> new IllegalStateException("系统字典明细尚未同步为实体"));
        }
        if (!StringUtils.hasText(field.getRefEntityId())) {
            throw new IllegalArgumentException("多值字段未配置目标实体: " + field.getFieldCode());
        }
        return field.getRefEntityId();
    }

    private List<String> resolveTargetRecordIds(EntityField field, List<String> values) {
        if (!StringUtils.hasText(field.getDictType())) {
            return values;
        }
        List<String> targetIds = new ArrayList<>();
        for (String value : values) {
            List<String> ids = jdbcTemplate.queryForList(
                    "SELECT id FROM sys_dict_item WHERE dict_code = ?"
                            + " AND (id = ? OR item_code = ? OR item_value = ?)"
                            + " AND status = '0' AND deleted = 0 LIMIT 1",
                    String.class,
                    field.getDictType(),
                    value,
                    value,
                    value);
            if (ids.isEmpty()) {
                throw new IllegalArgumentException(
                        "字段 " + field.getFieldName() + " 包含无效代码值: " + value);
            }
            targetIds.add(ids.get(0));
        }
        return targetIds;
    }

    private String resolveApiValue(EntityField field, String targetRecordId) {
        if (StringUtils.hasText(field.getDictType())) {
            List<String> codes = jdbcTemplate.queryForList(
                    "SELECT item_code FROM sys_dict_item WHERE id = ? AND deleted = 0 LIMIT 1",
                    String.class,
                    targetRecordId);
            return codes.isEmpty() ? targetRecordId : codes.get(0);
        }
        return targetRecordId;
    }

    private void enrichScalarDictOptions(
            EntityDefinition definition,
            com.workflow.dto.EntityDataDTO record) {
        if (record.getData() == null) {
            return;
        }
        for (EntityField field : fieldMapper.findByEntityId(definition.getId())) {
            if (!StringUtils.hasText(field.getDictType())
                    || field.getFieldType() == EntityField.FieldType.MULTI_SELECT
                    || field.getFieldType() == EntityField.FieldType.CHECKBOX) {
                continue;
            }
            Object rawValue = record.getData().get(field.getFieldCode());
            if (rawValue == null) {
                continue;
            }
            List<Map<String, Object>> items = jdbcTemplate.queryForList(
                    "SELECT id, item_code, item_label, status FROM sys_dict_item"
                            + " WHERE dict_code = ? AND item_code = ? AND deleted = 0 LIMIT 1",
                    field.getDictType(),
                    String.valueOf(rawValue));
            if (items.isEmpty()) {
                continue;
            }
            Map<String, Object> item = items.get(0);
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("id", item.get("id"));
            option.put("value", item.get("item_code"));
            option.put("label", item.get("item_label"));
            option.put("active", "0".equals(String.valueOf(item.get("status"))));
            if (record.getExtData() == null) {
                record.setExtData(new LinkedHashMap<>());
            }
            record.getExtData().put(field.getFieldCode() + "Options", List.of(option));
            record.getExtData().put(field.getFieldCode() + "Display", item.get("item_label"));
        }
    }

    private Map<String, Object> resolveOption(EntityField field, String targetRecordId) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("id", targetRecordId);
        if (StringUtils.hasText(field.getDictType())) {
            List<Map<String, Object>> items = jdbcTemplate.queryForList(
                    "SELECT item_code, item_label, status FROM sys_dict_item"
                            + " WHERE id = ? AND deleted = 0 LIMIT 1",
                    targetRecordId);
            if (!items.isEmpty()) {
                Map<String, Object> item = items.get(0);
                option.put("value", item.get("item_code"));
                option.put("label", item.get("item_label"));
                option.put("active", "0".equals(String.valueOf(item.get("status"))));
                return option;
            }
        } else {
            EntityDefinition target = definitionMapper.selectById(field.getRefEntityId());
            if (target != null) {
                String tableName = tableResolver.resolve(target);
                String displayColumn = resolveDisplayColumn(field, target);
                List<Map<String, Object>> targets = jdbcTemplate.queryForList(
                        "SELECT `" + displayColumn + "` AS display_name FROM " + tableName
                                + " WHERE id = ? LIMIT 1",
                        targetRecordId);
                if (!targets.isEmpty()) {
                    option.put("value", targetRecordId);
                    option.put("label", targets.get(0).get("display_name"));
                    option.put("active", true);
                    return option;
                }
            }
        }
        option.put("value", targetRecordId);
        option.put("label", targetRecordId);
        option.put("active", false);
        return option;
    }

    private List<String> normalizeValues(Object raw) {
        Set<String> values = new LinkedHashSet<>();
        if (raw instanceof Collection<?> collection) {
            for (Object value : collection) {
                addValue(values, value);
            }
        } else if (raw.getClass().isArray()) {
            for (Object value : (Object[]) raw) {
                addValue(values, value);
            }
        } else {
            String text = String.valueOf(raw).trim();
            if (text.startsWith("[") && text.endsWith("]")) {
                text = text.substring(1, text.length() - 1);
            }
            for (String value : text.split(",")) {
                addValue(values, value.replace("\"", ""));
            }
        }
        return new ArrayList<>(values);
    }

    private void addValue(Set<String> values, Object raw) {
        if (raw == null) {
            return;
        }
        String value = String.valueOf(raw).trim();
        if (!value.isEmpty()) {
            values.add(value);
        }
    }

    private String toSnakeCase(String value) {
        return value.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }

    private String resolveDisplayColumn(EntityField field, EntityDefinition target) {
        String column = field.getRefFieldCode();
        if (!StringUtils.hasText(column)) {
            Set<String> fieldCodes = fieldMapper.findByEntityId(target.getId()).stream()
                    .map(EntityField::getFieldCode)
                    .collect(java.util.stream.Collectors.toSet());
            column = List.of("name", "item_label", "display_name", "real_name", "username",
                            "org_name", "role_name", "group_name", "dict_name", "code", "id")
                    .stream()
                    .filter(fieldCodes::contains)
                    .findFirst()
                    .orElse("id");
        }
        if (!IDENTIFIER.matcher(column).matches()) {
            throw new IllegalArgumentException("目标显示字段不合法: " + column);
        }
        return toSnakeCase(column);
    }

    private String buildLabelExists(
            EntityField field,
            String multiTable,
            String base,
            String labelParam,
            String operation) {
        String operator = "EQ".equalsIgnoreCase(operation) ? "=" : "LIKE";
        String valueExpression = "=".equals(operator)
                ? "#{condition." + labelParam + "}"
                : "CONCAT('%', #{condition." + labelParam + "}, '%')";
        if (StringUtils.hasText(field.getDictType())) {
            return "EXISTS (SELECT 1 FROM " + multiTable + " mv JOIN sys_dict_item target"
                    + " ON target.id = mv.target_record_id AND target.deleted = 0"
                    + base.substring(base.indexOf(" WHERE"))
                    + " AND target.item_label " + operator + " " + valueExpression + ")";
        }
        EntityDefinition target = definitionMapper.selectById(field.getRefEntityId());
        if (target == null) {
            return "1=0";
        }
        String targetTable = tableResolver.resolve(target);
        String displayColumn = resolveDisplayColumn(field, target);
        return "EXISTS (SELECT 1 FROM " + multiTable + " mv JOIN " + targetTable + " target"
                + " ON target.id = mv.target_record_id"
                + base.substring(base.indexOf(" WHERE"))
                + " AND target.`" + displayColumn + "` " + operator + " " + valueExpression + ")";
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    public record PreparedConditions(Map<String, Object> condition, String sqlCondition) {
    }
}
