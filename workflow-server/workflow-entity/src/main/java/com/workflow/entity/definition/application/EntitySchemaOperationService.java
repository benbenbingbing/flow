package com.workflow.entity.definition.application;

import com.workflow.integration.database.api.query.DatabaseQueryDialect;
import com.workflow.core.database.jdbc.JdbcLockedRow;
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
import java.util.LinkedHashMap;
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
    // 只渲染分页/标识符；筛选条件及业务上限仍由本服务决定。
    private final DatabaseQueryDialect queryDialect;
    private final JdbcLockedRow lockedRows;

    /**
     * 构建发布前预览，不产生任何结构或状态副作用。
     *
     * @param entity 实体，作为 {@code basePreview} 的输入影响后续处理
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @param plan 执行方案，后续决定操作步骤和校验约束
     * @return 处理后的预览结果，供调用方继续处理
     */
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
     *
     * @param entity 实体，作为 {@code basePreview} 的输入影响后续处理
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @param plan 执行方案，后续决定操作步骤和校验约束
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @return 准备后的实体结构操作结果，供调用方继续处理
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
        // 计划行承担同一摘要的互斥，初始化只补缺失行，不覆盖原计划、状态或风险证据。
        var initial = new LinkedHashMap<String, Object>();
        initial.put("id", id);
        initial.put("entity_id", entity.getId());
        initial.put("entity_code", entity.getEntityCode());
        initial.put("status", "DDL_PENDING");
        initial.put("plan_hash", preview.getPlanHash());
        initial.put("idempotency_key", preview.getIdempotencyKey());
        initial.put("plan_json", writeJson(plan));
        initial.put("target_fingerprint", preview.getTargetFingerprint());
        initial.put("actual_fingerprint", preview.getActualFingerprint());
        initial.put("drift_json", writeJson(preview.getDrift()));
        initial.put("unique_conflict_json", writeJson(preview.getUniqueConflicts()));
        initial.put("risk_level", preview.getRiskLevel());
        initial.put("risk_reason", preview.getRiskReason());
        initial.put("estimated_rows", preview.getEstimatedRows());
        initial.put("lock_risk", preview.getLockRisk());
        initial.put("release_window", preview.getReleaseWindow());
        initial.put("created_by", userId);
        lockedRows.ensureAndLock("entity_schema_operation", initial, List.of("entity_id", "plan_hash"));
        // 当前读取得等待期间刚提交的竞争者，避免 MySQL 可重复读快照看不到已锁定的行。
        EntitySchemaOperationDTO operation = findByPlanForUpdate(entity.getId(), preview.getPlanHash());
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
        // 状态原本就是 DDL_PENDING 时 UPDATE 可能没有实际改行，返回值也必须使用当前读。
        return findByPlanForUpdate(entity.getId(), preview.getPlanHash());
    }

    /**
     * 标记{@code running}；后续读取或执行将使用更新后的状态。
     *
     * @param operationId 操作ID，后续用于标记{@code running}时定位或关联目标
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunning(String operationId) {
        EntitySchemaOperationDTO operation = requireOperation(operationId);
        if ("SCHEMA_CONSISTENT".equals(operation.getStatus())) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE entity_schema_operation SET status = 'DDL_RUNNING', attempt_count = attempt_count + 1, "
                        + "started_at = CURRENT_TIMESTAMP, finished_at = NULL, error_message = NULL WHERE id = ?",
                operationId);
        recordEvent(operationId, operation.getStatus(), "DDL_RUNNING", "开始执行DDL计划");
    }

    /**
     * DDL 完成后通过结构读取端口重新检查物理表；存在漂移时将操作标记为失败并阻止发布元数据。
     *
     * @param operationId 操作ID，后续用于处理完成时定位或关联目标
     * @param entity 实体，作为 {@code dynamicTableService.inspectSchemaDrift} 的输入影响后续处理
     * @param fields 字段集合，后续逐项校验、转换或持久化
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(
            String operationId,
            EntityDefinition entity,
            List<EntityField> fields) {
        List<String> drift = dynamicTableService.inspectSchemaDrift(entity, fields);
        String actualFingerprint = dynamicTableService.actualSchemaFingerprint(entity);
        if (!drift.isEmpty()) {
            throw new BusinessConflictException(
                    "ENTITY_SCHEMA_DRIFT_DETECTED",
                    "DDL执行后结构仍不一致: " + String.join("；", drift));
        }
        EntitySchemaOperationDTO operation = requireOperation(operationId);
        jdbcTemplate.update(
                "UPDATE entity_schema_operation SET status = 'SCHEMA_CONSISTENT', actual_fingerprint = ?, "
                        + "drift_json = ?, error_message = NULL, finished_at = CURRENT_TIMESTAMP WHERE id = ?",
                actualFingerprint,
                writeJson(drift),
                operationId);
        recordEvent(operationId, operation.getStatus(), "SCHEMA_CONSISTENT", "物理结构与目标结构一致");
    }

    /**
     * 标记失败；后续读取或执行将使用更新后的状态。
     *
     * @param operationId 操作ID，后续用于标记失败时定位或关联目标
     * @param throwable {@code throwable}，供本方法标记失败时使用
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String operationId, Throwable throwable) {
        String message = throwable == null || throwable.getMessage() == null
                ? "DDL执行失败" : abbreviate(throwable.getMessage(), 2000);
        markFailed(operationId, message, null, null);
    }

    /**
     * 将失败操作恢复到待执行，真正的 DDL 仍由下一次实体发布在实体级锁内执行。
     *
     * @param entityId 实体ID，后续用于处理重试时定位或关联目标
     * @return 处理后的重试结果，供调用方继续处理
     */
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

    /**
     * 终止尚未完成的计划；正在运行的 DDL 不支持伪装成已取消。
     *
     * @param entityId 实体ID，后续用于终止实体结构操作时定位或关联目标
     * @return 终止后的实体结构操作结果，供调用方继续处理
     */
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

    /**
     * 按创建时间和主键稳定读取最新操作；分页语法跟随运行数据库。
     *
     * @param entityId 实体ID，后续用于处理最新时定位或关联目标
     * @return 处理后的最新结果，供调用方继续处理
     */
    public EntitySchemaOperationDTO latest(String entityId) {
        return jdbcTemplate.query(
                "SELECT * FROM entity_schema_operation WHERE entity_id = ? ORDER BY create_time DESC, id DESC"
                        + queryDialect.paginationClause("0", "1"),
                this::map,
                entityId).stream().findFirst().orElse(null);
    }

    /**
     * 处理基础预览，并将结果传给后续步骤。
     *
     * @param entity 实体，作为 {@code dynamicTableService.estimateRows} 的输入影响后续处理
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @param plan 执行方案，后续决定操作步骤和校验约束
     * @return 处理后的基础预览结果，供调用方继续处理
     */
    private EntitySchemaOperationDTO basePreview(
            EntityDefinition entity,
            List<EntityField> fields,
            List<String> plan) {
        List<String> safePlan = plan == null ? List.of() : List.copyOf(plan);
        String planHash = sha256(String.join("\n-- next --\n", safePlan));
        // 迁移可在外层事务内新建实体；prepare/complete 的独立事务看不到未提交元数据。
        // 所有结构检查复用已加载的定义与登记表名，避免按编码重查导致“实体不存在”。
        long rows = dynamicTableService.estimateRows(entity);
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
        dto.setActualFingerprint(dynamicTableService.actualSchemaFingerprint(entity));
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

    /**
     * 风险识别只面向方言生成的结构计划，字段类型修改在各产品上采用相同的高风险门槛。
     *
     * @param plan 执行方案，后续决定操作步骤和校验约束
     * @param rows 行，供本方法处理{@code assess}风险时使用
     * @param drift {@code drift}，供本方法处理{@code assess}风险时使用
     * @param conflicts {@code conflicts}，供本方法处理{@code assess}风险时使用
     * @return 处理后的{@code assess}风险结果，供调用方继续处理
     */
    static Risk assessRisk(
            List<String> plan,
            long rows,
            List<String> drift,
            List<String> conflicts) {
        String joined = String.join(" ", plan).toUpperCase(java.util.Locale.ROOT);
        // 只匹配已生成 DDL 的语句头与操作位置，不能把默认值或注释中的关键字当作改字段。
        // MySQL/OB MySQL、Oracle/DM/OB Oracle、PG/Kingbase 分别采用以下三种模板。
        String identifier = "(?:`[A-Z][A-Z0-9_]*`|\"[A-Z][A-Z0-9_]*\")";
        String columnModification = "(?s)ALTER TABLE " + identifier
                + " (?:MODIFY COLUMN |MODIFY \\(|ALTER COLUMN " + identifier + " TYPE ).*";
        boolean modifiesColumn = plan.stream().anyMatch(sql ->
                sql.toUpperCase(java.util.Locale.ROOT).matches(columnModification));
        boolean destructive = joined.contains(" DROP ") || modifiesColumn;
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

    /**
     * 标记失败；后续读取或执行将使用更新后的状态。
     *
     * @param operationId 操作ID，后续用于标记失败时定位或关联目标
     * @param message 消息，作为 {@code abbreviate} 的输入影响后续处理
     * @param actualFingerprint 实际指纹，供本方法标记失败时使用
     * @param drift {@code drift}，供本方法标记失败时使用
     */
    private void markFailed(
            String operationId,
            String message,
            String actualFingerprint,
            List<String> drift) {
        EntitySchemaOperationDTO operation = requireOperation(operationId);
        jdbcTemplate.update(
                "UPDATE entity_schema_operation SET status = 'DDL_FAILED', actual_fingerprint = COALESCE(?, actual_fingerprint), "
                        + "drift_json = COALESCE(?, drift_json), error_message = ?, finished_at = CURRENT_TIMESTAMP WHERE id = ?",
                actualFingerprint,
                drift == null ? null : writeJson(drift),
                abbreviate(message, 2000),
                operationId);
        recordEvent(operationId, operation.getStatus(), "DDL_FAILED", abbreviate(message, 1000));
    }

    /**
     * 处理{@code transition}，并将结果传给后续步骤。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param from 起始，作为 {@code recordEvent} 的输入影响后续处理
     * @param to 截止，作为 {@code recordEvent} 的输入影响后续处理
     * @param message 消息，作为 {@code recordEvent} 的输入影响后续处理
     */
    private void transition(String id, String from, String to, String message) {
        jdbcTemplate.update(
                "UPDATE entity_schema_operation SET status = ?, error_message = CASE WHEN ? = 'DDL_PENDING' "
                        + "THEN NULL ELSE error_message END WHERE id = ?",
                to,
                to,
                id);
        recordEvent(id, from, to, message);
    }

    /**
     * 记录事件；供后续追溯或审计使用。
     *
     * @param operationId 操作ID，后续用于记录事件时定位或关联目标
     * @param from 起始，供本方法记录事件时使用
     * @param to 截止，供本方法记录事件时使用
     * @param message 消息，作为 {@code jdbcTemplate.update} 的输入影响后续处理
     */
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

    /**
     * entity_id/plan_hash 唯一约束保证不可变计划只有一个操作记录。
     *
     * @param entityId 实体ID，后续用于查询方案时定位或关联目标
     * @param planHash 方案哈希，供本方法查询方案时使用
     * @return 符合条件的实体结构操作结果，供调用方继续处理
     */
    private EntitySchemaOperationDTO findByPlan(String entityId, String planHash) {
        return jdbcTemplate.query(
                "SELECT * FROM entity_schema_operation WHERE entity_id = ? AND plan_hash = ?",
                this::map,
                entityId,
                planHash).stream().findFirst().orElse(null);
    }

    /**
     * 已取得计划行锁后读取最新状态；调用方必须处于 prepare 的独立事务内。
     *
     * @param entityId 实体ID，后续用于查询方案更新时定位或关联目标
     * @param planHash 方案哈希，作为 {@code jdbcTemplate.query} 的输入影响后续处理
     * @return 符合条件的实体结构操作结果，供调用方继续处理
     */
    private EntitySchemaOperationDTO findByPlanForUpdate(String entityId, String planHash) {
        return jdbcTemplate.query(
                "SELECT * FROM entity_schema_operation WHERE entity_id = ? AND plan_hash = ? FOR UPDATE",
                this::map, entityId, planHash).stream().findFirst().orElse(null);
    }

    /**
     * 按ID查询实体结构操作；结果供后续展示或处理。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 符合条件的实体结构操作结果，供调用方继续处理
     */
    private EntitySchemaOperationDTO findById(String id) {
        return jdbcTemplate.query(
                "SELECT * FROM entity_schema_operation WHERE id = ?",
                this::map,
                id).stream().findFirst().orElse(null);
    }

    /**
     * 校验并获取操作；不满足约束时阻止后续处理。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 校验并获取后的操作结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntitySchemaOperationDTO requireOperation(String id) {
        EntitySchemaOperationDTO operation = findById(id);
        if (operation == null) {
            throw new IllegalArgumentException("结构操作不存在: " + id);
        }
        return operation;
    }

    /**
     * 校验并获取最新；不满足约束时阻止后续处理。
     *
     * @param entityId 实体ID，后续用于校验并获取最新时定位或关联目标
     * @return 校验并获取后的最新结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntitySchemaOperationDTO requireLatest(String entityId) {
        EntitySchemaOperationDTO operation = latest(entityId);
        if (operation == null) {
            throw new IllegalArgumentException("实体没有结构操作记录: " + entityId);
        }
        return operation;
    }

    /**
     * 处理映射，并将结果传给后续步骤。
     *
     * @param rs {@code rs}，作为 {@code dto.setId} 的输入影响后续处理
     * @param rowNum 行数量，供本方法处理映射时使用
     * @return 处理后的映射结果，供调用方继续处理
     * @throws SQLException 数据库访问或结构检查失败时抛出
     */
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

    /**
     * 处理时间戳，并将结果传给后续步骤。
     *
     * @param rs {@code rs}，供本方法处理时间戳时使用
     * @param column 列，作为 {@code rs.getTimestamp} 的输入影响后续处理
     * @return 处理后的时间戳结果，供调用方继续处理
     * @throws SQLException 数据库访问或结构检查失败时抛出
     */
    private static LocalDateTime timestamp(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    /**
     * 读取实体结构操作列表；查询结果供调用方展示或继续处理。
     *
     * @param json JSON，作为 {@code objectMapper.readValue} 的输入影响后续处理
     * @return 实体结构操作集合，供调用方遍历或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 写入JSON；后续读取或执行将使用更新后的状态。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 写入后的JSON文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private String writeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("结构操作JSON序列化失败", exception);
        }
    }

    /**
     * 复制执行状态；结果供后续流程传递或持久化。
     *
     * @param source 待复制执行状态的原始输入，结果供调用方继续使用
     * @param target 目标，供本方法复制执行状态时使用
     */
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

    /**
     * 计算输入内容的 SHA-256 摘要，供后续签名或幂等键使用。
     *
     * @param value 待处理{@code sha256}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code sha256}文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    /**
     * 生成{@code compact}ID文本，供后续匹配或展示。
     *
     * @return 处理后的{@code compact}ID文本，供调用方比较或展示
     */
    private static String compactId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 截断实体结构操作；结果供调用方的后续步骤使用。
     *
     * @param value 待截断实体结构操作的原始输入，结果供调用方继续使用
     * @param maxLength 最大长度，供本方法截断实体结构操作时使用
     * @return 截断后的实体结构操作文本，供调用方比较或展示
     */
    private static String abbreviate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * 封装风险的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param level 层级，保存在对象中供后续校验、查询或展示
     * @param reason 原因，保存在对象中供后续校验、查询或展示
     * @param lockRisk 锁定风险，保存在对象中供后续校验、查询或展示
     * @param releaseWindow 发布版本{@code window}，保存在对象中供后续校验、查询或展示
     */
    record Risk(String level, String reason, String lockRisk, String releaseWindow) { }
}
