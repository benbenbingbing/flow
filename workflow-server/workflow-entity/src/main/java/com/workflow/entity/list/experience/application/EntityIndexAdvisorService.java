package com.workflow.entity.list.experience.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.list.experience.application.EntityListExperienceModels.IndexAnalyzeRequest;
import com.workflow.entity.list.experience.application.EntityListExperienceModels.IndexCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 字段索引向导。
 *
 * <p>建议来源只能是已发布列表字段；执行时重新校验字段并生成固定 CREATE INDEX 语句，
 * 全程写入 P0 的实体结构操作状态机，不接受调用方提供 DDL。</p>
 */
@Service
@RequiredArgsConstructor
public class EntityIndexAdvisorService {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,62}");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PublishedListFieldGuard fieldGuard;
    private final DynamicTableService dynamicTableService;

    /** 根据固定过滤、查询字段和排序字段生成可解释建议，不直接执行 DDL。 */
    public List<IndexCandidate> analyze(
            String entityCode,
            String listKey,
            IndexAnalyzeRequest request) {
        PublishedListFieldGuard.AllowedFields allowed = fieldGuard.resolve(entityCode, listKey);
        LinkedHashSet<String> filters = new LinkedHashSet<>(normalize(
                request == null ? null : request.filterFields()));
        if (filters.isEmpty()) filters.addAll(allowed.queryable());
        LinkedHashSet<String> sorts = new LinkedHashSet<>(normalize(
                request == null ? null : request.sortFields()));
        Set<String> requested = new LinkedHashSet<>(filters);
        requested.addAll(sorts);
        fieldGuard.require(entityCode, listKey, requested);
        String tableName = requireIdentifier(dynamicTableService.getTableName(entityCode));
        long estimatedRows = estimatedRows(tableName);
        Set<List<String>> existing = existingIndexes(tableName);
        Set<String> longTextColumns = longTextColumns(tableName);
        List<List<String>> candidates = buildCandidateColumns(filters, sorts);
        for (List<String> columns : candidates) {
            double selectivity = estimateSelectivity(tableName, columns, estimatedRows);
            boolean duplicate = existing.contains(columns);
            boolean longText = columns.stream().anyMatch(longTextColumns::contains);
            AdviceAssessment assessment = assessCandidate(
                    columns, estimatedRows, selectivity, duplicate, longText);
            long scanRows = estimatedScanRows(estimatedRows, selectivity);
            Map<String, Object> evidence = new java.util.LinkedHashMap<>();
            evidence.put("source", "PUBLISHED_LIST");
            evidence.put("columns", columns);
            evidence.put("tableRows", estimatedRows);
            evidence.put("selectivityMethod", "BOUNDED_SAMPLE_10000_WITH_HEURISTIC_FALLBACK");
            evidence.put("duplicateIndex", duplicate);
            evidence.put("longTextColumn", longText);
            evidence.put("beforeExplain", explain(tableName, columns));
            persist(entityCode, listKey, columns, selectivity, scanRows, assessment, evidence);
        }
        return list(entityCode, listKey);
    }

    public List<IndexCandidate> list(String entityCode, String listKey) {
        return jdbcTemplate.query("""
                SELECT * FROM entity_index_advice
                WHERE entity_code = ? AND list_key = ?
                ORDER BY create_time DESC
                """, this::map, entityCode, listKey);
    }

    /**
     * 执行已确认建议。标识符均由发布字段白名单派生，调用方无法传入 SQL。
     * 失败状态独立提交并保留在结构操作记录中，便于安全重试和人工处置。
     */
    public IndexCandidate apply(String id, int expectedRevision, boolean confirmed) {
        if (!confirmed) throw new IllegalArgumentException("执行索引计划前必须确认锁表和写入成本风险");
        IndexCandidate candidate = require(id);
        if ("APPLIED".equals(candidate.status())) return candidate;
        if (Set.of("NOT_RECOMMENDED", "REJECTED").contains(candidate.status())) {
            throw new IllegalStateException("该建议已被标记为不推荐或已拒绝，不能执行");
        }
        fieldGuard.require(candidate.entityCode(), candidate.listKey(), candidate.columns());
        int claimed = jdbcTemplate.update("""
                UPDATE entity_index_advice
                SET status = 'APPLYING', revision = revision + 1,
                    update_time = CURRENT_TIMESTAMP(3), error_message = NULL
                WHERE id = ? AND revision = ? AND status IN ('SUGGESTED', 'FAILED')
                """, id, expectedRevision);
        if (claimed != 1) throw new IllegalStateException("索引建议状态或版本已变化，请刷新后重试");

        String table = requireIdentifier(dynamicTableService.getTableName(candidate.entityCode()));
        List<String> columns = candidate.columns().stream().map(this::requireIdentifier).toList();
        String indexName = requireIdentifier(candidate.indexName());
        String ddl = "CREATE INDEX `" + indexName + "` ON `" + table + "` ("
                + columns.stream().map(column -> "`" + column + "`")
                        .reduce((left, right) -> left + ", " + right).orElseThrow() + ")";
        String operationId = compactId();
        String planHash = sha256(ddl);
        Map<String, Object> beforeExplain = explain(table, columns);
        long tableRows = longValue(candidate.evidence().get("tableRows"), candidate.estimatedRows());
        String entityId = jdbcTemplate.queryForObject(
                "SELECT CAST(id AS CHAR) FROM entity_definition WHERE entity_code = ? AND deleted = 0",
                String.class, candidate.entityCode());
        String actor = actor();
        jdbcTemplate.update("""
                INSERT INTO entity_schema_operation (
                  id, entity_id, entity_code, status, plan_hash, idempotency_key,
                  plan_json, target_fingerprint, drift_json, unique_conflict_json,
                  risk_level, risk_reason, estimated_rows, lock_risk, release_window,
                  attempt_count, created_by, create_time, update_time,
                  operation_source, source_reference_id
                ) VALUES (?, ?, ?, 'DDL_PENDING', ?, ?, ?, ?, '[]', '[]',
                          ?, ?, ?, ?, ?, 0, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                          'INDEX_ADVISOR', ?)
                """, operationId, entityId, candidate.entityCode(), planHash,
                "INDEX_ADVICE:" + id, write(List.of(ddl)), planHash,
                riskLevel(tableRows),
                "索引向导建议，执行前已验证发布字段和重复索引",
                tableRows, lockRisk(tableRows),
                releaseWindow(tableRows), actor, id);
        event(operationId, null, "DDL_PENDING", "索引建议已转为结构操作计划");
        jdbcTemplate.update("""
                UPDATE entity_schema_operation
                SET status = 'DDL_RUNNING', attempt_count = attempt_count + 1,
                    started_at = CURRENT_TIMESTAMP, update_time = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'DDL_PENDING'
                """, operationId);
        event(operationId, "DDL_PENDING", "DDL_RUNNING", "开始执行受控索引 DDL");
        try {
            if (!indexExists(table, indexName)) jdbcTemplate.execute(ddl);
            Map<String, Object> evidence = new java.util.LinkedHashMap<>(candidate.evidence());
            evidence.put("beforeExplain", beforeExplain);
            evidence.put("afterExplain", explain(table, columns));
            jdbcTemplate.update("""
                    UPDATE entity_schema_operation
                    SET status = 'SCHEMA_CONSISTENT', actual_fingerprint = ?,
                        finished_at = CURRENT_TIMESTAMP, update_time = CURRENT_TIMESTAMP
                    WHERE id = ? AND status = 'DDL_RUNNING'
                    """, planHash, operationId);
            event(operationId, "DDL_RUNNING", "SCHEMA_CONSISTENT", "索引结构已创建并确认一致");
            jdbcTemplate.update("""
                    UPDATE entity_index_advice
                    SET status = 'APPLIED', schema_operation_id = ?, applied_by = ?,
                        evidence_json = ?, update_time = CURRENT_TIMESTAMP(3)
                    WHERE id = ?
                    """, operationId, actor, write(evidence), id);
            return require(id);
        } catch (RuntimeException exception) {
            String message = abbreviate(exception.getMessage(), 1800);
            Map<String, Object> evidence = new java.util.LinkedHashMap<>(candidate.evidence());
            evidence.put("beforeExplain", beforeExplain);
            evidence.put("afterExplain", Map.of(
                    "available", false,
                    "error", message == null ? exception.getClass().getSimpleName() : message));
            jdbcTemplate.update("""
                    UPDATE entity_schema_operation
                    SET status = 'DDL_FAILED', error_message = ?,
                        finished_at = CURRENT_TIMESTAMP, update_time = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, message, operationId);
            event(operationId, "DDL_RUNNING", "DDL_FAILED", message);
            jdbcTemplate.update("""
                    UPDATE entity_index_advice
                    SET status = 'FAILED', schema_operation_id = ?, error_message = ?,
                        evidence_json = ?, update_time = CURRENT_TIMESTAMP(3)
                    WHERE id = ?
                    """, operationId, message, write(evidence), id);
            throw new IllegalStateException("索引执行失败，已进入 DDL_FAILED: " + message, exception);
        }
    }

    /** 显式拒绝一条建议，保留操作者、原因和 revision，避免建议反复进入待执行队列。 */
    public IndexCandidate reject(String id, int expectedRevision, String reason) {
        IndexCandidate candidate = require(id);
        if ("REJECTED".equals(candidate.status())) return candidate;
        int updated = jdbcTemplate.update("""
                UPDATE entity_index_advice
                SET status = 'REJECTED', revision = revision + 1,
                    rejected_by = ?, rejected_at = CURRENT_TIMESTAMP(3),
                    error_message = ?, update_time = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND revision = ? AND status IN ('SUGGESTED', 'FAILED', 'NOT_RECOMMENDED')
                """, actor(), abbreviate(StringUtils.hasText(reason) ? reason.trim() : "用户拒绝该建议", 1800),
                id, expectedRevision);
        if (updated != 1) throw new IllegalStateException("索引建议状态或版本已变化，请刷新后重试");
        return require(id);
    }

    static List<List<String>> buildCandidateColumns(Set<String> filters, Set<String> sorts) {
        List<List<String>> result = new ArrayList<>();
        filters.stream().limit(6).forEach(field -> result.add(List.of(field)));
        List<String> combined = new ArrayList<>();
        filters.stream().limit(2).forEach(combined::add);
        sorts.stream().filter(field -> !combined.contains(field)).limit(1).forEach(combined::add);
        if (combined.size() > 1) result.add(List.copyOf(combined));
        return result.stream().distinct().toList();
    }

    private void persist(
            String entityCode,
            String listKey,
            List<String> columns,
            double selectivity,
            long scanRows,
            AdviceAssessment assessment,
            Map<String, Object> evidence) {
        String adviceKey = sha256(String.join(",", columns)).substring(0, 24);
        String indexName = indexName(entityCode, columns, adviceKey);
        jdbcTemplate.update("""
                INSERT INTO entity_index_advice (
                  id, entity_code, list_key, advice_key, index_name, columns_json,
                  evidence_json, status, revision, selectivity_estimate, estimated_rows,
                  write_cost_level, recommendation, created_by, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?,
                          CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE
                  evidence_json = VALUES(evidence_json),
                  status = CASE WHEN status IN ('APPLIED', 'REJECTED') THEN status ELSE VALUES(status) END,
                  selectivity_estimate = VALUES(selectivity_estimate), estimated_rows = VALUES(estimated_rows),
                  write_cost_level = VALUES(write_cost_level), recommendation = VALUES(recommendation),
                  update_time = CURRENT_TIMESTAMP(3)
                """, compactId(), entityCode, listKey, adviceKey, indexName, write(columns),
                write(evidence), assessment.status(), selectivity, scanRows,
                assessment.writeCostLevel(), assessment.recommendation(), actor());
    }

    static AdviceAssessment assessCandidate(
            List<String> columns,
            long tableRows,
            double selectivity,
            boolean duplicate,
            boolean longText) {
        String writeCost = tableRows >= 1_000_000 ? "HIGH" : tableRows >= 100_000 ? "MEDIUM" : "LOW";
        if (duplicate) {
            return new AdviceAssessment("NOT_RECOMMENDED", writeCost,
                    "反建议：同字段顺序索引已存在，重复创建只会增加写入和存储成本");
        }
        if (longText) {
            return new AdviceAssessment("NOT_RECOMMENDED", writeCost,
                    "反建议：候选包含长文本字段，不自动生成普通 B-Tree 索引计划");
        }
        if (selectivity > 0 && selectivity < 0.05D) {
            return new AdviceAssessment("NOT_RECOMMENDED", writeCost,
                    "反建议：预估选择性低于 5%，单独建索引通常无法有效降低扫描量");
        }
        String recommendation = columns.size() > 1
                ? "联合索引按等值过滤字段在前、排序字段在后；执行后保留 EXPLAIN 对比证据"
                : "单列索引来源于已发布查询字段；执行后保留 EXPLAIN 对比证据";
        if ("HIGH".equals(writeCost)) recommendation += "；高写入成本，建议在低峰窗口观察写入延迟";
        return new AdviceAssessment("SUGGESTED", writeCost, recommendation);
    }

    record AdviceAssessment(String status, String writeCostLevel, String recommendation) {
    }

    private Set<List<String>> existingIndexes(String tableName) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) columns_csv
                    FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                    GROUP BY INDEX_NAME
                    """, tableName);
            Set<List<String>> result = new LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                String csv = String.valueOf(row.get("columns_csv"));
                result.add(List.of(csv.split(",")));
            }
            return result;
        } catch (RuntimeException ignored) {
            return Set.of();
        }
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
                """, Integer.class, tableName, indexName);
        return count != null && count > 0;
    }

    private long estimatedRows(String tableName) {
        try {
            Long rows = jdbcTemplate.queryForObject("""
                    SELECT COALESCE(TABLE_ROWS, 0) FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                    """, Long.class, tableName);
            return rows == null ? 0 : rows;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    /** 使用最多一万行的受控样本估算选择性；统计不可用时退化为字段名启发式。 */
    private double estimateSelectivity(String tableName, List<String> columns, long tableRows) {
        if (tableRows <= 0 || columns.isEmpty()) return 0D;
        String projection = columns.stream().map(column -> "`" + column + "`")
                .collect(Collectors.joining(", "));
        String fingerprint = columns.stream()
                .map(column -> "COALESCE(CAST(`" + column + "` AS CHAR), '<NULL>')")
                .collect(Collectors.joining(", "));
        try {
            Map<String, Object> sample = jdbcTemplate.queryForMap(
                    "SELECT COUNT(*) sample_rows, "
                            + "COUNT(DISTINCT CONCAT_WS(CHAR(31), " + fingerprint + ")) distinct_rows "
                            + "FROM (SELECT " + projection + " FROM `" + tableName
                            + "` LIMIT 10000) sampled");
            long sampleRows = longValue(sample.get("sample_rows"), 0);
            long distinctRows = longValue(sample.get("distinct_rows"), 0);
            if (sampleRows > 0) return roundSelectivity((double) distinctRows / sampleRows);
        } catch (RuntimeException ignored) {
            // 统计采样失败不能阻断建议分析，使用明确标注的保守启发式继续。
        }
        double missingProbability = 1D;
        for (String column : columns) {
            String normalized = column.toLowerCase();
            double fieldEstimate = normalized.endsWith("_id") || normalized.endsWith("code")
                    ? 0.85D
                    : normalized.contains("status") || normalized.contains("type")
                    ? 0.08D : 0.35D;
            missingProbability *= 1D - fieldEstimate;
        }
        return roundSelectivity(Math.min(0.98D, 1D - missingProbability));
    }

    private long estimatedScanRows(long tableRows, double selectivity) {
        if (tableRows <= 0) return 0;
        if (selectivity <= 0) return tableRows;
        long distinctValues = Math.max(1L, Math.round(tableRows * selectivity));
        return Math.max(1L, Math.min(tableRows, (long) Math.ceil((double) tableRows / distinctValues)));
    }

    private Set<String> longTextColumns(String tableName) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT COLUMN_NAME, DATA_TYPE FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                    """, tableName);
            Set<String> result = new LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                String type = String.valueOf(row.get("DATA_TYPE")).toLowerCase();
                if (type.contains("text") || type.contains("blob")) {
                    result.add(String.valueOf(row.get("COLUMN_NAME")));
                }
            }
            return result;
        } catch (RuntimeException ignored) {
            return Set.of();
        }
    }

    /** 返回结构化执行计划证据，数据库不支持 EXPLAIN 时明确记录不可用原因。 */
    private Map<String, Object> explain(String tableName, List<String> columns) {
        String predicates = columns.stream().map(column -> "`" + column + "` IS NOT NULL")
                .collect(Collectors.joining(" AND "));
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "EXPLAIN SELECT * FROM `" + tableName + "` WHERE " + predicates + " LIMIT 1");
            return Map.of("available", true, "plan", rows);
        } catch (RuntimeException exception) {
            String message = abbreviate(exception.getMessage(), 500);
            return Map.of(
                    "available", false,
                    "error", message == null ? exception.getClass().getSimpleName() : message);
        }
    }

    private IndexCandidate require(String id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM entity_index_advice WHERE id = ?", this::map, id);
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalArgumentException("索引建议不存在: " + id);
        }
    }

    private IndexCandidate map(ResultSet rs, int rowNum) throws SQLException {
        return new IndexCandidate(
                rs.getString("id"), rs.getString("entity_code"), rs.getString("list_key"),
                rs.getString("index_name"), readList(rs.getString("columns_json")),
                readMap(rs.getString("evidence_json")), rs.getString("status"), rs.getInt("revision"),
                rs.getDouble("selectivity_estimate"), rs.getLong("estimated_rows"),
                rs.getString("write_cost_level"), rs.getString("recommendation"),
                rs.getString("schema_operation_id"), rs.getString("error_message"),
                time(rs.getTimestamp("create_time")), time(rs.getTimestamp("update_time")));
    }

    private List<String> normalize(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(StringUtils::hasText).map(String::trim)
                .map(this::requireIdentifier).distinct().toList();
    }

    private String indexName(String entityCode, List<String> columns, String suffix) {
        String base = "idx_" + entityCode + "_" + String.join("_", columns);
        base = base.replaceAll("[^A-Za-z0-9_]", "_");
        if (base.length() > 54) base = base.substring(0, 54);
        return requireIdentifier(base + "_" + suffix.substring(0, 6));
    }

    private String requireIdentifier(String value) {
        if (!StringUtils.hasText(value) || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("非法数据库标识符: " + value);
        }
        return value;
    }

    private void event(String operationId, String from, String to, String message) {
        jdbcTemplate.update("""
                INSERT INTO entity_schema_operation_event
                  (id, operation_id, from_status, to_status, message, create_time)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, compactId(), operationId, from, to, abbreviate(message, 950));
    }

    private String riskLevel(long rows) {
        return rows >= 1_000_000 ? "HIGH" : rows >= 100_000 ? "MEDIUM" : "LOW";
    }

    private String lockRisk(long rows) {
        return rows >= 1_000_000 ? "HIGH" : rows >= 100_000 ? "MEDIUM" : "LOW";
    }

    private String releaseWindow(long rows) {
        return rows >= 100_000 ? "建议在低峰窗口使用在线 DDL 执行" : "可在常规发布窗口执行";
    }

    private String actor() {
        String value = UserContext.getUserId();
        if (!StringUtils.hasText(value)) value = UserContext.getUsername();
        if (!StringUtils.hasText(value)) throw new ForbiddenException("用户未登录");
        return value;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("索引建议序列化失败", exception);
        }
    }

    private List<String> readList(String json) {
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception exception) {
            throw new IllegalStateException("索引建议字段已损坏", exception);
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return StringUtils.hasText(json) ? objectMapper.readValue(json, MAP_TYPE) : Map.of();
        } catch (Exception exception) {
            throw new IllegalStateException("索引建议证据已损坏", exception);
        }
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null ? fallback : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double roundSelectivity(double value) {
        return Math.round(Math.max(0D, Math.min(1D, value)) * 10_000D) / 10_000D;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成索引建议摘要", exception);
        }
    }

    private String compactId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String abbreviate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private LocalDateTime time(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
