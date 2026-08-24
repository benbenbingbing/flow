package com.workflow.entity.definition.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.definition.api.response.EntitySchemaOperationDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 管理实体元数据到物理表结构的一次发布操作。
 * DDL 计划按实体与计划摘要去重并保持不可变，状态写入使用独立事务，避免发布失败时丢失诊断证据。
 */
@Service
@RequiredArgsConstructor
public class EntitySchemaOperationService {

    private static final long LARGE_TABLE_ROWS = 100_000L;
    private static final long VERY_LARGE_TABLE_ROWS = 500_000L;
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DynamicTableService dynamicTableService;

    /** 构建发布前预览，不产生任何结构或状态副作用。 */
    public EntitySchemaOperationDTO preview(
            EntityDefinition entity,
            List<EntityField> fields,
            List<String> plan) {
        EntitySchemaOperationDTO dto = basePreview(entity, fields, plan);
        EntitySchemaOperationDTO existing = findByPlan(entity.getId(), dto.getPlanHash());
        if (existing != null) {
            copyExecutionState(existing, dto);
        }
        return dto;
    }

    /**
     * 固化不可变计划并进入 DDL_PENDING；相同实体和计划摘要会复用原操作以支持安全重试。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EntitySchemaOperationDTO prepare(
            EntityDefinition entity,
            List<EntityField> fields,
            List<String> plan,
            String userId) {
        EntitySchemaOperationDTO preview = basePreview(entity, fields, plan);
        if (!preview.getUniqueConflicts().isEmpty()) {
            throw new BusinessConflictException(
                    "ENTITY_UNIQUE_DATA_CONFLICT",
                    "启用唯一约束前发现重复数据: " + String.join("；", preview.getUniqueConflicts()));
        }
        String id = compactId();
        jdbcTemplate.update(
                "INSERT INTO entity_schema_operation "
                        + "(id, entity_id, entity_code, status, plan_hash, idempotency_key, plan_json, "
                        + "target_fingerprint, actual_fingerprint, drift_json, unique_conflict_json, "
                        + "risk_level, risk_reason, estimated_rows, lock_risk, release_window, created_by) "
                        + "VALUES (?, ?, ?, 'DDL_PENDING', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE id = id",
                id,
                entity.getId(),
                entity.getEntityCode(),
                preview.getPlanHash(),
                preview.getIdempotencyKey(),
                writeJson(plan),
                preview.getTargetFingerprint(),
                preview.getActualFingerprint(),
                writeJson(preview.getDrift()),
                writeJson(preview.getUniqueConflicts()),
                preview.getRiskLevel(),
                preview.getRiskReason(),
                preview.getEstimatedRows(),
                preview.getLockRisk(),
                preview.getReleaseWindow(),
                userId);
        EntitySchemaOperationDTO operation = findByPlan(entity.getId(), preview.getPlanHash());
        if (operation == null) {
            throw new IllegalStateException("实体结构操作创建失败");
        }
        if ("SCHEMA_CONSISTENT".equals(operation.getStatus())) {
            return operation;
        }
        if ("TERMINATED".equals(operation.getStatus())) {
            throw new BusinessConflictException(
                    "ENTITY_SCHEMA_OPERATION_TERMINATED",
                    "该结构计划已终止；请先修改元数据生成新计划");
        }
        transition(operation.getId(), operation.getStatus(), "DDL_PENDING", "结构计划已确认");
        return findById(operation.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunning(String operationId) {
        EntitySchemaOperationDTO operation = requireOperation(operationId);
        if ("SCHEMA_CONSISTENT".equals(operation.getStatus())) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE entity_schema_operation SET status = 'DDL_RUNNING', attempt_count = attempt_count + 1, "
                        + "started_at = NOW(), finished_at = NULL, error_message = NULL WHERE id = ?",
                operationId);
        recordEvent(operationId, operation.getStatus(), "DDL_RUNNING", "开始执行DDL计划");
    }

    /** DDL 完成后重新读取 information_schema；存在漂移时将操作标记为失败并阻止发布元数据。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(
            String operationId,
            EntityDefinition entity,
            List<EntityField> fields) {
        List<String> drift = dynamicTableService.inspectSchemaDrift(entity, fields);
        String actualFingerprint = dynamicTableService.actualSchemaFingerprint(entity.getEntityCode());
        if (!drift.isEmpty()) {
            throw new BusinessConflictException(
                    "ENTITY_SCHEMA_DRIFT_DETECTED",
                    "DDL执行后结构仍不一致: " + String.join("；", drift));
        }
        EntitySchemaOperationDTO operation = requireOperation(operationId);
        jdbcTemplate.update(
                "UPDATE entity_schema_operation SET status = 'SCHEMA_CONSISTENT', actual_fingerprint = ?, "
                        + "drift_json = ?, error_message = NULL, finished_at = NOW() WHERE id = ?",
                actualFingerprint,
                writeJson(drift),
                operationId);
        recordEvent(operationId, operation.getStatus(), "SCHEMA_CONSISTENT", "物理结构与目标结构一致");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String operationId, Throwable throwable) {
        String message = throwable == null || throwable.getMessage() == null
                ? "DDL执行失败" : abbreviate(throwable.getMessage(), 2000);
        markFailed(operationId, message, null, null);
    }

    /** 将失败操作恢复到待执行，真正的 DDL 仍由下一次实体发布在实体级锁内执行。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EntitySchemaOperationDTO retry(String entityId) {
        EntitySchemaOperationDTO operation = requireLatest(entityId);
        if (!"DDL_FAILED".equals(operation.getStatus())) {
            throw new BusinessConflictException(
                    "ENTITY_SCHEMA_OPERATION_NOT_RETRYABLE",
                    "只有失败的结构操作可以重试");
        }
        transition(operation.getId(), operation.getStatus(), "DDL_PENDING", "用户请求重试");
        return findById(operation.getId());
    }

    /** 终止尚未完成的计划；正在运行的 DDL 不支持伪装成已取消。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EntitySchemaOperationDTO terminate(String entityId) {
        EntitySchemaOperationDTO operation = requireLatest(entityId);
        if ("DDL_RUNNING".equals(operation.getStatus())) {
            throw new BusinessConflictException(
                    "ENTITY_SCHEMA_OPERATION_RUNNING",
                    "DDL正在执行，不能直接终止");
        }
        if ("SCHEMA_CONSISTENT".equals(operation.getStatus())) {
            throw new BusinessConflictException(
                    "ENTITY_SCHEMA_OPERATION_COMPLETED",
                    "已完成的结构操作不能终止");
        }
        transition(operation.getId(), operation.getStatus(), "TERMINATED", "用户终止结构计划");
        return findById(operation.getId());
    }

    public EntitySchemaOperationDTO latest(String entityId) {
        return jdbcTemplate.query(
                "SELECT * FROM entity_schema_operation WHERE entity_id = ? ORDER BY create_time DESC LIMIT 1",
                this::map,
                entityId).stream().findFirst().orElse(null);
    }

    private EntitySchemaOperationDTO basePreview(
            EntityDefinition entity,
            List<EntityField> fields,
            List<String> plan) {
        List<String> safePlan = plan == null ? List.of() : List.copyOf(plan);
        String planHash = sha256(String.join("\n-- next --\n", safePlan));
        long rows = dynamicTableService.estimateRows(entity.getEntityCode());
        List<String> drift = dynamicTableService.inspectSchemaDrift(entity, fields);
        List<String> conflicts = dynamicTableService.scanUniqueConflicts(entity, fields);
        Risk risk = assessRisk(safePlan, rows, drift, conflicts);
        EntitySchemaOperationDTO dto = new EntitySchemaOperationDTO();
        dto.setEntityId(entity.getId());
        dto.setEntityCode(entity.getEntityCode());
        dto.setStatus("METADATA_SAVED");
        dto.setPlanHash(planHash);
        dto.setIdempotencyKey("entity-schema:" + entity.getId() + ":" + planHash);
        dto.setPlan(safePlan);
        dto.setTargetFingerprint(dynamicTableService.targetSchemaFingerprint(entity, fields));
        dto.setActualFingerprint(dynamicTableService.actualSchemaFingerprint(entity.getEntityCode()));
        dto.setDrift(drift);
        dto.setUniqueConflicts(conflicts);
        dto.setRiskLevel(risk.level());
        dto.setRiskReason(risk.reason());
        dto.setEstimatedRows(rows);
        dto.setLockRisk(risk.lockRisk());
        dto.setReleaseWindow(risk.releaseWindow());
        dto.setAttemptCount(0);
        dto.setRetryable(false);
        return dto;
    }

    static Risk assessRisk(
            List<String> plan,
            long rows,
            List<String> drift,
            List<String> conflicts) {
        String joined = String.join(" ", plan).toUpperCase();
        boolean destructive = joined.contains(" DROP ") || joined.contains(" MODIFY COLUMN");
        boolean alter = joined.contains("ALTER TABLE");
        boolean high = destructive || rows >= VERY_LARGE_TABLE_ROWS || !conflicts.isEmpty();
        boolean medium = !high && (alter || rows >= LARGE_TABLE_ROWS || !drift.isEmpty());
        String level = high ? "HIGH" : medium ? "MEDIUM" : "LOW";
        String lockRisk = rows >= VERY_LARGE_TABLE_ROWS || destructive
                ? "HIGH" : rows >= LARGE_TABLE_ROWS || alter ? "MEDIUM" : "LOW";
        String reason;
        if (!conflicts.isEmpty()) {
            reason = "唯一约束数据扫描发现冲突";
        } else if (destructive) {
            reason = "计划包含可能重建或锁定数据页的字段修改";
        } else if (rows >= LARGE_TABLE_ROWS) {
            reason = "目标表数据量较大，DDL可能持有元数据锁";
        } else if (!drift.isEmpty()) {
            reason = "检测到实际结构与元数据存在漂移";
        } else if (alter) {
            reason = "计划包含在线表结构变更";
        } else {
            reason = "未发现显著结构发布风险";
        }
        String window = "HIGH".equals(lockRisk)
                ? "仅在低峰维护窗口执行，并准备回退与终止方案"
                : "MEDIUM".equals(lockRisk)
                        ? "建议在业务低峰执行并观察DDL队列"
                        : "可在常规发布窗口执行";
        return new Risk(level, reason, lockRisk, window);
    }

    private void markFailed(
            String operationId,
            String message,
            String actualFingerprint,
            List<String> drift) {
        EntitySchemaOperationDTO operation = requireOperation(operationId);
        jdbcTemplate.update(
                "UPDATE entity_schema_operation SET status = 'DDL_FAILED', actual_fingerprint = COALESCE(?, actual_fingerprint), "
                        + "drift_json = COALESCE(?, drift_json), error_message = ?, finished_at = NOW() WHERE id = ?",
                actualFingerprint,
                drift == null ? null : writeJson(drift),
                abbreviate(message, 2000),
                operationId);
        recordEvent(operationId, operation.getStatus(), "DDL_FAILED", abbreviate(message, 1000));
    }

    private void transition(String id, String from, String to, String message) {
        jdbcTemplate.update(
                "UPDATE entity_schema_operation SET status = ?, error_message = CASE WHEN ? = 'DDL_PENDING' "
                        + "THEN NULL ELSE error_message END WHERE id = ?",
                to,
                to,
                id);
        recordEvent(id, from, to, message);
    }

    private void recordEvent(String operationId, String from, String to, String message) {
        jdbcTemplate.update(
                "INSERT INTO entity_schema_operation_event "
                        + "(id, operation_id, from_status, to_status, message) VALUES (?, ?, ?, ?, ?)",
                compactId(),
                operationId,
                from,
                to,
                abbreviate(message, 1000));
    }

    private EntitySchemaOperationDTO findByPlan(String entityId, String planHash) {
        return jdbcTemplate.query(
                "SELECT * FROM entity_schema_operation WHERE entity_id = ? AND plan_hash = ? LIMIT 1",
                this::map,
                entityId,
                planHash).stream().findFirst().orElse(null);
    }

    private EntitySchemaOperationDTO findById(String id) {
        return jdbcTemplate.query(
                "SELECT * FROM entity_schema_operation WHERE id = ?",
                this::map,
                id).stream().findFirst().orElse(null);
    }

    private EntitySchemaOperationDTO requireOperation(String id) {
        EntitySchemaOperationDTO operation = findById(id);
        if (operation == null) {
            throw new IllegalArgumentException("结构操作不存在: " + id);
        }
        return operation;
    }

    private EntitySchemaOperationDTO requireLatest(String entityId) {
        EntitySchemaOperationDTO operation = latest(entityId);
        if (operation == null) {
            throw new IllegalArgumentException("实体没有结构操作记录: " + entityId);
        }
        return operation;
    }

    private EntitySchemaOperationDTO map(ResultSet rs, int rowNum) throws SQLException {
        EntitySchemaOperationDTO dto = new EntitySchemaOperationDTO();
        dto.setId(rs.getString("id"));
        dto.setEntityId(rs.getString("entity_id"));
        dto.setEntityCode(rs.getString("entity_code"));
        dto.setStatus(rs.getString("status"));
        dto.setPlanHash(rs.getString("plan_hash"));
        dto.setIdempotencyKey(rs.getString("idempotency_key"));
        dto.setPlan(readList(rs.getString("plan_json")));
        dto.setTargetFingerprint(rs.getString("target_fingerprint"));
        dto.setActualFingerprint(rs.getString("actual_fingerprint"));
        dto.setDrift(readList(rs.getString("drift_json")));
        dto.setUniqueConflicts(readList(rs.getString("unique_conflict_json")));
        dto.setRiskLevel(rs.getString("risk_level"));
        dto.setRiskReason(rs.getString("risk_reason"));
        dto.setEstimatedRows(rs.getLong("estimated_rows"));
        dto.setLockRisk(rs.getString("lock_risk"));
        dto.setReleaseWindow(rs.getString("release_window"));
        dto.setAttemptCount(rs.getInt("attempt_count"));
        dto.setErrorMessage(rs.getString("error_message"));
        dto.setRetryable("DDL_FAILED".equals(dto.getStatus()));
        dto.setStartedAt(timestamp(rs, "started_at"));
        dto.setFinishedAt(timestamp(rs, "finished_at"));
        dto.setCreatedAt(timestamp(rs, "create_time"));
        dto.setUpdatedAt(timestamp(rs, "update_time"));
        return dto;
    }

    private static LocalDateTime timestamp(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("结构操作JSON损坏", exception);
        }
    }

    private String writeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("结构操作JSON序列化失败", exception);
        }
    }

    private static void copyExecutionState(
            EntitySchemaOperationDTO source,
            EntitySchemaOperationDTO target) {
        target.setId(source.getId());
        target.setStatus(source.getStatus());
        target.setAttemptCount(source.getAttemptCount());
        target.setErrorMessage(source.getErrorMessage());
        target.setRetryable(source.getRetryable());
        target.setStartedAt(source.getStartedAt());
        target.setFinishedAt(source.getFinishedAt());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private static String compactId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    record Risk(String level, String reason, String lockRisk, String releaseWindow) { }
}
