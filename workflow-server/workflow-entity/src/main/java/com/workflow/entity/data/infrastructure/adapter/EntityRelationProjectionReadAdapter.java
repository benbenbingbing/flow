package com.workflow.entity.data.infrastructure.adapter;

import com.workflow.contracts.entity.list.DataScopePlan;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.data.application.EntityRelationProjectionReadPort;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationProjectionMapper;
import com.workflow.entity.data.infrastructure.persistence.provider.EntityRelationProjectionSqlProvider.ColumnProjection;
import com.workflow.entity.definition.application.model.PublishedRelationPath.LinkField;
import com.workflow.entity.definition.application.model.PublishedRelationPath.LinkValueType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于动态业务表的关系图最小投影适配器。
 *
 * <p>适配器完全使用查询对象中的钉定字段描述，不调用当前字段 Mapper；
 * MULTI_REFERENCE 从实体多值表批量读取，并在返回到应用层前执行硬上限。</p>
 */
@Component
@RequiredArgsConstructor
public class EntityRelationProjectionReadAdapter
        implements EntityRelationProjectionReadPort {

    private static final int MAX_PROJECTED_FIELDS = 4;

    private final EntityRelationProjectionMapper mapper;
    private final DynamicTableService dynamicTableService;

    @Override
    public ProjectionPage readPage(ProjectionQuery query) {
        ValidatedQuery validated = validate(query);
        Map<String, Object> parameters = queryParameters(validated);
        long total = mapper.count(parameters);
        List<Map<String, Object>> rawRows = mapper.selectPage(parameters);
        List<MutableRow> rows = mapScalarRows(
                rawRows, validated.fields());
        loadMultiValues(rows, validated);
        return new ProjectionPage(
                rows.stream().map(MutableRow::freeze).toList(),
                total,
                validated.pageNum(),
                validated.pageSize());
    }

    private ValidatedQuery validate(ProjectionQuery query) {
        if (query == null || !StringUtils.hasText(query.entityCode())) {
            throw new IllegalArgumentException("关系图投影实体不能为空");
        }
        if (query.predicateType() == null) {
            throw new IllegalArgumentException("关系图投影查询方式不能为空");
        }
        List<String> values = normalizeValues(query.predicateValues());
        if (values.isEmpty() || values.size() > 200) {
            throw new IllegalArgumentException("关系图投影查询值数量必须在 1 至 200 之间");
        }
        long pageNum = query.pageNum();
        long pageSize = query.pageSize();
        if (pageNum < 1 || pageSize < 1 || pageSize > 200) {
            throw new IllegalArgumentException("关系图投影分页参数无效");
        }
        if (query.maxMultiValues() < 1 || query.maxMultiValues() > 10000) {
            throw new IllegalArgumentException("关系图多值投影上限无效");
        }
        List<LinkField> fields = uniqueFields(query.projectedFields());
        if (fields.size() > MAX_PROJECTED_FIELDS) {
            throw new IllegalArgumentException("关系图单次投影链接字段过多");
        }
        LinkField predicate = query.predicateField();
        if (query.predicateType() == PredicateType.LINK_IN) {
            validateField(predicate);
        } else if (predicate != null) {
            throw new IllegalArgumentException("按记录ID查询不能附加链接字段");
        }
        DataScopePlan scope = validateScope(query.dataScopePlan());
        return new ValidatedQuery(
                query.entityCode().trim(),
                fields,
                query.predicateType(),
                predicate,
                values,
                scope,
                pageNum,
                pageSize,
                query.maxMultiValues());
    }

    private DataScopePlan validateScope(DataScopePlan scope) {
        if (scope == null || !StringUtils.hasText(scope.sqlFragment())) {
            throw new IllegalArgumentException("关系图数据权限计划不能为空");
        }
        if (scope.requiredJoins() != null
                && !scope.requiredJoins().isEmpty()) {
            // 关系图投影不实现任意 JOIN。需要 JOIN 的权限计划必须由专用、
            // 可审计的安全适配器支持，否则按拒绝执行。
            throw new IllegalArgumentException("关系图投影暂不支持带 JOIN 的数据权限计划");
        }
        if (!scope.allowed() && !"1=0".equals(scope.sqlFragment().trim())) {
            throw new IllegalArgumentException("拒绝型数据权限计划必须使用 1=0");
        }
        return scope;
    }

    private List<LinkField> uniqueFields(List<LinkField> values) {
        Map<String, LinkField> result = new LinkedHashMap<>();
        if (values == null) {
            return List.of();
        }
        for (LinkField field : values) {
            validateField(field);
            LinkField previous = result.putIfAbsent(field.fieldCode(), field);
            if (previous != null && !previous.equals(field)) {
                throw new IllegalArgumentException("同一链接字段存在冲突的钉定描述");
            }
        }
        return List.copyOf(result.values());
    }

    private void validateField(LinkField field) {
        if (field == null
                || !StringUtils.hasText(field.fieldCode())
                || field.valueType() == null
                || !StringUtils.hasText(field.referenceEntityId())) {
            throw new IllegalArgumentException("关系图链接字段描述不完整");
        }
        if (field.valueType() == LinkValueType.SCALAR_REFERENCE
                && !StringUtils.hasText(field.storageColumn())) {
            throw new IllegalArgumentException("单值链接字段缺少钉定物理列");
        }
        if (field.valueType() == LinkValueType.MULTI_REFERENCE
                && StringUtils.hasText(field.storageColumn())) {
            throw new IllegalArgumentException("多值链接字段不能读取业务表列");
        }
    }

    private Map<String, Object> queryParameters(ValidatedQuery query) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableName", dynamicTableService.getTableName(
                query.entityCode()));
        result.put("multiTable", dynamicTableService.getMultiValueTableName(
                query.entityCode()));
        List<ColumnProjection> columns = new ArrayList<>();
        int scalarIndex = 0;
        for (LinkField field : query.fields()) {
            if (field.valueType() == LinkValueType.SCALAR_REFERENCE) {
                columns.add(new ColumnProjection(
                        field.storageColumn(), "link_" + scalarIndex));
                scalarIndex++;
            }
        }
        result.put("columns", columns);
        result.put("predicateValues", query.predicateValues());
        if (query.predicateType() == PredicateType.ID_IN) {
            result.put("predicateType", "ID_IN");
        } else if (query.predicateField().valueType()
                == LinkValueType.SCALAR_REFERENCE) {
            result.put("predicateType", "SCALAR_LINK_IN");
            result.put("predicateColumn",
                    query.predicateField().storageColumn());
        } else {
            result.put("predicateType", "MULTI_LINK_IN");
            result.put("predicateFieldCode",
                    query.predicateField().fieldCode());
            result.put("predicateTargetEntityId",
                    query.predicateField().referenceEntityId());
        }
        result.put("permissionSql",
                query.dataScopePlan().allowed()
                        ? query.dataScopePlan().sqlFragment() : "1=0");
        result.put("permissionParameters",
                query.dataScopePlan().parameters() == null
                        ? Map.of()
                        : Map.copyOf(query.dataScopePlan().parameters()));
        result.put("offset", (query.pageNum() - 1) * query.pageSize());
        result.put("pageSize", query.pageSize());
        return result;
    }

    private List<MutableRow> mapScalarRows(
            List<Map<String, Object>> rawRows,
            List<LinkField> fields) {
        List<LinkField> scalarFields = fields.stream()
                .filter(field -> field.valueType()
                        == LinkValueType.SCALAR_REFERENCE)
                .toList();
        List<MutableRow> result = new ArrayList<>();
        for (Map<String, Object> raw : safe(rawRows)) {
            String id = text(value(raw, "record_id", "recordId"));
            if (!StringUtils.hasText(id)) {
                throw invalid("关系图投影返回缺少记录ID的数据");
            }
            Map<String, Object> links = new LinkedHashMap<>();
            for (int index = 0; index < scalarFields.size(); index++) {
                Object value = value(raw, "link_" + index,
                        "link" + index);
                if (value != null) {
                    links.put(scalarFields.get(index).fieldCode(), value);
                }
            }
            result.add(new MutableRow(id, links));
        }
        return result;
    }

    private void loadMultiValues(
            List<MutableRow> rows,
            ValidatedQuery query) {
        List<LinkField> multiFields = query.fields().stream()
                .filter(field -> field.valueType()
                        == LinkValueType.MULTI_REFERENCE)
                .toList();
        if (rows.isEmpty() || multiFields.isEmpty()) {
            return;
        }
        Map<String, LinkField> fieldsByCode = new LinkedHashMap<>();
        for (LinkField field : multiFields) {
            fieldsByCode.put(field.fieldCode(), field);
        }
        Map<String, MutableRow> rowsById = new LinkedHashMap<>();
        for (MutableRow row : rows) {
            rowsById.put(row.id(), row);
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("multiTable",
                dynamicTableService.getMultiValueTableName(query.entityCode()));
        parameters.put("recordIds", List.copyOf(rowsById.keySet()));
        parameters.put("multiFieldCodes", List.copyOf(fieldsByCode.keySet()));
        parameters.put("limitPlusOne", query.maxMultiValues() + 1);
        List<Map<String, Object>> values = mapper.selectMultiValues(parameters);
        if (values.size() > query.maxMultiValues()) {
            throw invalid("关系图多值链接数量超过本次读取上限 "
                    + query.maxMultiValues());
        }
        for (Map<String, Object> value : values) {
            String recordId = text(value(value,
                    "record_id", "recordId"));
            String fieldCode = text(value(value,
                    "field_code", "fieldCode"));
            String targetEntityId = text(value(value,
                    "target_entity_id", "targetEntityId"));
            String targetRecordId = text(value(value,
                    "target_record_id", "targetRecordId"));
            MutableRow row = rowsById.get(recordId);
            LinkField field = fieldsByCode.get(fieldCode);
            if (row == null || field == null
                    || !field.referenceEntityId().equals(targetEntityId)
                    || !StringUtils.hasText(targetRecordId)) {
                throw invalid("关系图多值链接与钉定字段定义不一致");
            }
            row.addMulti(fieldCode, targetRecordId);
        }
    }

    private Object value(
            Map<String, Object> values,
            String primary,
            String fallback) {
        if (values == null) {
            return null;
        }
        return values.containsKey(primary)
                ? values.get(primary) : values.get(fallback);
    }

    private List<String> normalizeValues(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    result.add(value.trim());
                }
            }
        }
        return List.copyOf(result);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private BusinessConflictException invalid(String message) {
        return new BusinessConflictException(
                "ENTITY_RELATION_PROJECTION_INVALID", message);
    }

    private record ValidatedQuery(
            String entityCode,
            List<LinkField> fields,
            PredicateType predicateType,
            LinkField predicateField,
            List<String> predicateValues,
            DataScopePlan dataScopePlan,
            long pageNum,
            long pageSize,
            int maxMultiValues) {
    }

    private record MutableRow(
            String id,
            Map<String, Object> links) {

        @SuppressWarnings("unchecked")
        private void addMulti(String fieldCode, String targetRecordId) {
            List<String> values = (List<String>) links.computeIfAbsent(
                    fieldCode, ignored -> new ArrayList<String>());
            if (!values.contains(targetRecordId)) {
                values.add(targetRecordId);
            }
        }

        private ProjectionRow freeze() {
            Map<String, Object> frozen = new LinkedHashMap<>();
            links.forEach((key, value) -> frozen.put(
                    key,
                    value instanceof List<?> list
                            ? List.copyOf(list) : value));
            return new ProjectionRow(id, frozen);
        }
    }
}
