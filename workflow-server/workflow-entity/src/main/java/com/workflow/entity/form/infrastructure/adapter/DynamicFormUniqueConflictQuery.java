package com.workflow.entity.form.infrastructure.adapter;

import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.application.port.FormUniqueConflictQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于动态实体表的表单唯一冲突查询适配器。
 *
 * <p>条件唯一允许引用多个字段，因此适配器返回字段编码视角的完整候选记录，
 * 由共享规则策略统一完成条件和规范化比较。后续如需按字段建立专用查询索引，
 * 可以替换此端口实现而无需改动表单规则语义。</p>
 */
@Component
@RequiredArgsConstructor
public class DynamicFormUniqueConflictQuery
        implements FormUniqueConflictQuery {

    private final EntityDefinitionMapper definitionMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityDataDynamicMapper dynamicMapper;
    private final DynamicTableService dynamicTableService;
    private final EntityRuntimeRecordMapper recordMapper;

    @Override
    public Map<String, Object> findRecord(
            String entityCode,
            String recordId) {
        if (!StringUtils.hasText(recordId)) {
            return Map.of();
        }
        RuntimeEntity runtime = requireRuntimeEntity(entityCode);
        Map<String, Object> row = dynamicMapper.selectById(
                dynamicTableService.getTableName(entityCode),
                recordId);
        return row == null
                ? Map.of()
                : toFieldRecord(row, runtime.fields());
    }

    @Override
    public List<Map<String, Object>> findCandidates(
            String entityCode,
            String fieldCode,
            String normalizedValue,
            String excludeRecordId) {
        return findCandidates(
                entityCode,
                fieldCode,
                normalizedValue,
                excludeRecordId,
                false);
    }

    @Override
    public List<Map<String, Object>>
            findCandidatesForAuthoritativeCheck(
                    String entityCode,
                    String fieldCode,
                    String normalizedValue,
                    String excludeRecordId) {
        return findCandidates(
                entityCode,
                fieldCode,
                normalizedValue,
                excludeRecordId,
                true);
    }

    private List<Map<String, Object>> findCandidates(
            String entityCode,
            String fieldCode,
            String normalizedValue,
            String excludeRecordId,
            boolean authoritativeCheck) {
        RuntimeEntity runtime = requireRuntimeEntity(entityCode);
        EntityField targetField = runtime.fields().stream()
                .filter(field -> field != null
                        && fieldCode.equals(field.getFieldCode()))
                .findFirst()
                .orElse(null);
        if (targetField == null) {
            // 新发布在配置边界已禁止虚拟/失效字段；这里仍对历史
            // 快照 fail closed，不能把不可信 fieldCode 拼成 SQL 列名。
            throw new IllegalArgumentException(
                    "表单唯一规则字段不存在: "
                            + fieldCode);
        }
        String targetColumn = StringUtils.hasText(
                targetField.getDbColumnName())
                ? targetField.getDbColumnName()
                : recordMapper.toColumnName(fieldCode);
        List<Map<String, Object>> rows;
        if (requiresFullScan(targetField)) {
            // 数字规范化会去除尾零；BOOLEAN 在请求中是 true/false，而 MySQL
            // 常以 1/0 返回；DATE/DATETIME 的 Java ISO 形状与 MySQL
            // CAST 文本的时间分隔符不同。这些字段直接做文本 SQL 预筛会
            // 漏报，因此保留全量回退，其他高频文本字段走数据库预筛选。
            rows = authoritativeCheck
                    ? dynamicMapper.selectListForUpdate(
                            dynamicTableService.getTableName(
                                    entityCode))
                    : dynamicMapper.selectList(
                            dynamicTableService.getTableName(
                                    entityCode));
        } else {
            rows = authoritativeCheck
                    ? dynamicMapper
                            .selectFormUniqueCandidatesForUpdate(
                                    dynamicTableService.getTableName(
                                            entityCode),
                                    targetColumn,
                                    normalizedValue,
                                    excludeRecordId)
                    : dynamicMapper.selectFormUniqueCandidates(
                            dynamicTableService.getTableName(
                                    entityCode),
                            targetColumn,
                            normalizedValue,
                            excludeRecordId);
        }
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (StringUtils.hasText(excludeRecordId)
                    && excludeRecordId.equals(
                            String.valueOf(row.get("id")))) {
                continue;
            }
            result.add(toFieldRecord(row, runtime.fields()));
        }
        return List.copyOf(result);
    }

    private boolean requiresFullScan(EntityField field) {
        return field.getFieldType() == EntityField.FieldType.INTEGER
                || field.getFieldType() == EntityField.FieldType.LONG
                || field.getFieldType() == EntityField.FieldType.DECIMAL
                || field.getFieldType() == EntityField.FieldType.BOOLEAN
                || field.getFieldType() == EntityField.FieldType.DATE
                || field.getFieldType()
                == EntityField.FieldType.DATETIME;
    }

    private RuntimeEntity requireRuntimeEntity(String entityCode) {
        EntityDefinition definition = definitionMapper
                .findByEntityCode(entityCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "实体不存在: " + entityCode));
        List<EntityField> fields = fieldMapper.findByEntityId(
                definition.getId());
        return new RuntimeEntity(
                definition,
                fields == null ? List.of() : fields);
    }

    private Map<String, Object> toFieldRecord(
            Map<String, Object> row,
            List<EntityField> fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        row.forEach((column, value) -> {
            result.put(column, value);
            result.putIfAbsent(underscoreToCamel(column), value);
        });
        for (EntityField field : fields) {
            if (field == null || !StringUtils.hasText(
                    field.getFieldCode())) {
                continue;
            }
            String column = StringUtils.hasText(
                    field.getDbColumnName())
                    ? field.getDbColumnName()
                    : recordMapper.toColumnName(
                            field.getFieldCode());
            if (row.containsKey(column)) {
                result.put(field.getFieldCode(), row.get(column));
            }
        }
        return result;
    }

    private String underscoreToCamel(String source) {
        if (!StringUtils.hasText(source)
                || !source.contains("_")) {
            return source;
        }
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (char value : source.toCharArray()) {
            if (value == '_') {
                upper = true;
            } else if (upper) {
                result.append(Character.toUpperCase(value));
                upper = false;
            } else {
                result.append(value);
            }
        }
        return result.toString();
    }

    private record RuntimeEntity(
            EntityDefinition definition,
            List<EntityField> fields) {
    }
}
