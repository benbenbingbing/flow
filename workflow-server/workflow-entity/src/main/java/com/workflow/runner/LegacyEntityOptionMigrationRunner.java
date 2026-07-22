package com.workflow.runner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.SysDict;
import com.workflow.entity.SysDictItem;
import com.workflow.entity.runtime.EntityMultiValueRuntimeService;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.service.DynamicTableService;
import com.workflow.service.EntityFieldOptionService;
import com.workflow.service.SysDictService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将旧内嵌选项和主表多值列迁移到代码表及每实体多值表。
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class LegacyEntityOptionMigrationRunner implements ApplicationRunner {

    private final EntityDefinitionMapper definitionMapper;
    private final EntityFieldMapper fieldMapper;
    private final SysDictService dictService;
    private final DynamicTableService dynamicTableService;
    private final EntityMultiValueRuntimeService multiValueRuntimeService;
    private final EntityFieldOptionService fieldOptionService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) {
        for (EntityDefinition definition : definitionMapper.selectList(null)) {
            if (definition.getStorageMode() != EntityDefinition.StorageMode.DYNAMIC) {
                continue;
            }
            for (EntityField field : fieldMapper.findByEntityId(definition.getId())) {
                try {
                    migrateOptionDefinition(definition, field);
                    migrateMultiValues(definition, field);
                } catch (Exception exception) {
                    log.error(
                            "迁移旧实体选项失败: entityCode={}, fieldCode={}",
                            definition.getEntityCode(),
                            field.getFieldCode(),
                            exception);
                }
            }
        }
    }

    private void migrateOptionDefinition(EntityDefinition definition, EntityField field) throws Exception {
        if (!isOptionField(field)
                || StringUtils.hasText(field.getDictType())
                || !StringUtils.hasText(field.getOptionsJson())) {
            return;
        }
        List<Map<String, Object>> options = objectMapper.readValue(
                field.getOptionsJson(),
                new TypeReference<>() {
                });
        if (options.isEmpty()) {
            return;
        }
        fieldOptionService.replace(field.getId(), options);
        String dictCode = normalizedDictCode(
                definition.getEntityCode() + "_" + field.getFieldCode());
        SysDict dict = dictService.getByCode(dictCode);
        if (dict == null) {
            dict = new SysDict();
            dict.setDictCode(dictCode);
            dict.setDictName(definition.getEntityName() + "-" + field.getFieldName());
            List<SysDictItem> items = new ArrayList<>();
            for (Map<String, Object> option : options) {
                String itemCode = text(option.get("value"));
                if (!StringUtils.hasText(itemCode)) {
                    continue;
                }
                SysDictItem item = new SysDictItem();
                item.setItemCode(itemCode);
                item.setItemLabel(StringUtils.hasText(text(option.get("label")))
                        ? text(option.get("label"))
                        : itemCode);
                item.setItemValue(itemCode);
                items.add(item);
            }
            if (items.isEmpty()) {
                return;
            }
            dictService.createWithItems(dict, items);
        }
        field.setDictType(dictCode);
        fieldMapper.updateById(field);
        log.info(
                "旧内嵌选项已绑定代码表: entityCode={}, fieldCode={}, dictCode={}",
                definition.getEntityCode(),
                field.getFieldCode(),
                dictCode);
    }

    private void migrateMultiValues(EntityDefinition definition, EntityField field) {
        if (!isPotentialMultiField(field)
                || definition.getStatus() != EntityDefinition.Status.PUBLISHED
                || !dynamicTableService.tableExists(definition.getEntityCode())) {
            return;
        }
        String columnName = StringUtils.hasText(field.getDbColumnName())
                ? field.getDbColumnName()
                : toSnakeCase(field.getFieldCode());
        boolean columnExists = dynamicTableService.getTableColumns(definition.getEntityCode())
                .stream()
                .anyMatch(column -> columnName.equalsIgnoreCase(column.getName()));
        if (!columnExists) {
            return;
        }
        dynamicTableService.ensureEntityMultiValueTable(definition.getEntityCode());
        String tableName = dynamicTableService.getTableName(definition.getEntityCode());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, `" + columnName + "` AS legacy_value FROM " + tableName
                        + " WHERE `" + columnName + "` IS NOT NULL"
                        + " AND CAST(`" + columnName + "` AS CHAR) <> ''");
        if (!resolveMissingTarget(field, rows)) {
            if (rows.isEmpty()) {
                log.warn(
                        "旧多值列没有数据但目标实体未配置，保留为可移植文本列等待管理员修复: entityCode={}, fieldCode={}",
                        definition.getEntityCode(),
                        field.getFieldCode());
            } else {
                log.error(
                        "旧多值列目标实体无法识别，保留原列: entityCode={}, fieldCode={}",
                        definition.getEntityCode(),
                        field.getFieldCode());
            }
            return;
        }
        boolean verified = true;
        for (Map<String, Object> row : rows) {
            List<String> values = normalizeValues(row.get("legacy_value"));
            if (!values.isEmpty()) {
                String recordId = String.valueOf(row.get("id"));
                multiValueRuntimeService.save(
                        definition,
                        recordId,
                        Map.of(field.getFieldCode(), values));
                String multiTable = dynamicTableService.getMultiValueTableName(
                        definition.getEntityCode());
                Integer migrated = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM " + multiTable
                                + " WHERE record_id = ? AND field_code = ? AND deleted = 0",
                        Integer.class,
                        recordId,
                        field.getFieldCode());
                if (migrated == null || migrated != values.size()) {
                    verified = false;
                    log.error(
                            "旧多值列迁移数量不一致: entityCode={}, fieldCode={}, recordId={}, expected={}, actual={}",
                            definition.getEntityCode(),
                            field.getFieldCode(),
                            recordId,
                            values.size(),
                            migrated);
                }
            }
        }
        if (!verified) {
            return;
        }
        if (!rows.isEmpty()) {
            log.info(
                    "旧多值列迁移完成: entityCode={}, fieldCode={}, records={}",
                    definition.getEntityCode(),
                    field.getFieldCode(),
                    rows.size());
        }
        field.setValueStorage("MULTI_TABLE");
        fieldMapper.updateById(field);
        dynamicTableService.dropColumn(definition.getEntityCode(), columnName);
        log.info(
                "旧多值列已删除: entityCode={}, fieldCode={}, column={}",
                definition.getEntityCode(),
                field.getFieldCode(),
                columnName);
    }

    private boolean isOptionField(EntityField field) {
        return field.getFieldType() == EntityField.FieldType.SELECT
                || field.getFieldType() == EntityField.FieldType.RADIO
                || field.getFieldType() == EntityField.FieldType.MULTI_SELECT
                || field.getFieldType() == EntityField.FieldType.CHECKBOX;
    }

    private boolean isPotentialMultiField(EntityField field) {
        if (field.getFieldType() == EntityField.FieldType.MULTI_REFERENCE) {
            return true;
        }
        return (field.getFieldType() == EntityField.FieldType.MULTI_SELECT
                || field.getFieldType() == EntityField.FieldType.CHECKBOX)
                && StringUtils.hasText(field.getDictType());
    }

    private boolean resolveMissingTarget(
            EntityField field,
            List<Map<String, Object>> rows) {
        if (StringUtils.hasText(field.getDictType())
                || StringUtils.hasText(field.getRefEntityId())) {
            return true;
        }
        List<String> values = rows.stream()
                .flatMap(row -> normalizeValues(row.get("legacy_value")).stream())
                .distinct()
                .toList();
        if (values.isEmpty()) {
            return false;
        }
        String placeholders = String.join(
                ",", java.util.Collections.nCopies(values.size(), "?"));
        Integer userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND id IN ("
                        + placeholders + ")",
                Integer.class,
                values.toArray());
        if (userCount != null && userCount == values.size()) {
            EntityDefinition userEntity = definitionMapper.findByEntityCode("sys_user")
                    .orElse(null);
            if (userEntity != null) {
                field.setRefEntityId(userEntity.getId());
                field.setRefEntityType(EntityField.RefEntityType.USER);
                fieldMapper.updateById(field);
                log.info(
                        "根据历史值识别多值目标为系统用户: fieldCode={}",
                        field.getFieldCode());
                return true;
            }
        }
        return false;
    }

    private List<String> normalizeValues(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).filter(StringUtils::hasText).distinct().toList();
        }
        String text = String.valueOf(raw).trim();
        if (text.startsWith("[")) {
            try {
                return objectMapper.readValue(text, new TypeReference<>() {
                });
            } catch (Exception ignored) {
            }
        }
        Map<String, Boolean> values = new LinkedHashMap<>();
        for (String value : text.split(",")) {
            String normalized = value.trim();
            if (!normalized.isEmpty()) {
                values.put(normalized, true);
            }
        }
        return new ArrayList<>(values.keySet());
    }

    private String normalizedDictCode(String value) {
        String normalized = value.toLowerCase()
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_");
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

    private String toSnakeCase(String value) {
        return value.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
