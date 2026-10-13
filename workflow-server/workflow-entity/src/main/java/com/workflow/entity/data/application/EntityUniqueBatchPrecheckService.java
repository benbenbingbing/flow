package com.workflow.entity.data.application;

import com.workflow.entity.data.application.EntityDataMutationValidator.BatchFieldError;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.integration.database.api.query.DatabaseQueryDialect;
import com.workflow.integration.database.api.schema.SchemaType;
import lombok.RequiredArgsConstructor;
import com.workflow.core.database.mybatis.ParameterizedSql;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityUniquePrecheckMapper;
import org.apache.ibatis.type.JdbcType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 已发布实体唯一字段的只读批量预检。文件内比较沿用唯一值预留的规范化规则；
 * 已有记录比较交给真实物理列和数据库排序规则，不能取回值再用 Java equals 猜测。
 */
@Service
@RequiredArgsConstructor
public class EntityUniqueBatchPrecheckService {
    static final int QUERY_BATCH_SIZE = 100;
    private final EntityUniquePrecheckMapper mapper;
    private final DynamicTableService tables;
    private final DatabaseQueryDialect dialect;
    private final EntityRuntimeRecordMapper records;

    /** 返回零基输入行索引与字段错误；所有 SQL 仅 SELECT，不触碰唯一值占位或业务记录。 */
    public List<BatchFieldError> validate(String entityCode, EntityPublishedSnapshot snapshot, List<Map<String, Object>> storageRows) {
        List<BatchFieldError> errors = new ArrayList<>();
        var metadata = EntityQueryConditions.fromPublishedFields(Map.of(), snapshot.getFields());
        String table = null;
        for (EntityField field : snapshot.getFields()) {
            if (!Boolean.TRUE.equals(field.getIsUnique()) || "code".equals(field.getFieldCode())
                    || field.getFieldType() == EntityField.FieldType.SUB_FORM || field.getFieldType() == EntityField.FieldType.SUB_LIST) continue;
            String column = records.toColumnName(field.getFieldCode());
            SchemaType.Kind kind = metadata.column(field.getFieldCode()).type().kind();
            Map<String, List<Candidate>> normalizedRows = new LinkedHashMap<>();
            for (int index = 0; index < storageRows.size(); index++) {
                Map<String, Object> row = storageRows.get(index);
                if (row == null) continue;
                Object raw = row.get(column);
                if (EntityUniqueValueService.normalize(raw) == null) continue;
                try {
                    // 与正式 EQ 查询同一类型转换，避免数字字符串/BigDecimal刻度/日期隐式转换误判。
                    Object value = EntityQueryScalarValues.scalarValue(raw, kind);
                    String normalized = EntityUniqueValueService.normalize(value);
                    normalizedRows.computeIfAbsent(normalized, ignored -> new ArrayList<>())
                            .add(new Candidate(index, value, EntityUniqueValueService.sha256(normalized)));
                } catch (IllegalArgumentException invalid) {
                    if (!EntityDataMutationValidator.addBatchError(errors, new BatchFieldError(index, field.getFieldCode(), "FIELD_TYPE", "字段值不符合已发布字段类型"))) return List.copyOf(errors);
                }
            }
            List<Candidate> pending = new ArrayList<>();
            for (List<Candidate> group : normalizedRows.values()) {
                if (group.size() > 1) {
                    // 默认值和提交前计算也可能产生相同值，所以使用最终存储数据并标记组内全部行。
                    for (Candidate candidate : group) if (!EntityDataMutationValidator.addBatchError(errors, new BatchFieldError(candidate.rowIndex(), field.getFieldCode(),
                            "DUPLICATE_IN_FILE", "唯一字段在文件中重复"))) return List.copyOf(errors);
                } else pending.add(group.get(0));
            }
            if (pending.isEmpty()) continue;
            if (table == null) table = tables.getTableName(entityCode);
            for (int start = 0; start < pending.size(); start += QUERY_BATCH_SIZE) {
                List<Candidate> batch = pending.subList(start, Math.min(pending.size(), start + QUERY_BATCH_SIZE));
                for (Integer row : databaseConflicts(entityCode, table, field.getFieldCode(), column, kind, batch)) {
                    if (!EntityDataMutationValidator.addBatchError(errors, new BatchFieldError(row, field.getFieldCode(), "UNIQUE_CONFLICT", "字段值已被占用"))) return List.copyOf(errors);
                }
            }
        }
        return List.copyOf(errors);
    }

    /**
     * 一批查询同时覆盖历史业务表与现行规范化占位。每个分支直接绑定当前候选值与物理列比较，
     * 不把参数构造成文本 IN 表再反向映射，从而保留数据库大小写、数值和日期比较语义。
     * 只投影输入索引，避免泄露占用记录；UNION ALL 分支显式携带索引，不依赖返回顺序。
     */
    private List<Integer> databaseConflicts(String entityCode, String table, String field, String column,
            SchemaType.Kind kind, List<Candidate> batch) {
        String fromDual = switch (dialect.vendor()) {
            case ORACLE, DM, OCEANBASE_ORACLE -> " FROM DUAL";
            default -> "";
        };
        String quotedTable = dialect.quoteIdentifier(table);
        String comparison = dialect.comparisonPredicate(column, kind, "=", "?");
        String claimTable = dialect.quoteIdentifier("entity_unique_value");
        String claimPredicate = dialect.quoteIdentifier("entity_code") + "=? AND "
                + dialect.quoteIdentifier("field_code") + "=? AND " + dialect.quoteIdentifier("value_hash") + "=?";
        List<Object> args = new ArrayList<>();
        List<String> branches = new ArrayList<>();
        for (Candidate candidate : batch) {
            branches.add("SELECT " + candidate.rowIndex() + " AS " + dialect.quoteAlias("row_index") + fromDual
                    + " WHERE EXISTS (SELECT 1 FROM " + quotedTable + " WHERE " + dialect.quoteIdentifier("deleted") + "=0 AND " + comparison + ")"
                    + " OR EXISTS (SELECT 1 FROM " + claimTable + " WHERE " + claimPredicate + ")");
            Object parameter = candidate.value();
            if (parameter instanceof LocalDate date) parameter = java.sql.Date.valueOf(date);
            if (parameter instanceof LocalDateTime date) parameter = java.sql.Timestamp.valueOf(date);
            JdbcType jdbcType = JdbcType.valueOf(dialect.comparisonJdbcType(kind));
            if (jdbcType == JdbcType.CLOB) parameter = String.valueOf(parameter);
            args.add(ParameterizedSql.typed(parameter, jdbcType));
            args.add(entityCode); args.add(field); args.add(candidate.hash());
        }
        return mapper.selectConflictIndexes(ParameterizedSql.of(String.join(" UNION ALL ", branches),
                args.toArray()));
    }

    private record Candidate(int rowIndex, Object value, String hash) { }
}
