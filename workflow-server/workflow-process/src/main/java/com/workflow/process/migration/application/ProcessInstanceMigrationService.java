package com.workflow.process.migration.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.process.migration.application.ProcessInstanceMigrationModels.CreateBatchRequest;
import com.workflow.process.migration.application.ProcessInstanceMigrationModels.ExecuteBatchRequest;
import com.workflow.process.migration.application.ProcessInstanceMigrationModels.RetryBatchRequest;
import com.workflow.process.migration.application.ProcessInstanceMigrationModels.SafetyAssessment;
import com.workflow.process.migration.application.ProcessInstanceMigrationModels.SafetyInput;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.ManagementService;
import org.flowable.engine.ProcessMigrationService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.migration.ActivityMigrationMapping;
import org.flowable.engine.migration.ProcessInstanceMigrationDocument;
import org.flowable.engine.migration.ProcessInstanceMigrationValidationResult;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 流程实例版本迁移编排服务。
 *
 * <p>创建批次后先逐实例干运行；执行采用数据库状态领取和幂等条目；失败只隔离当前实例。
 * 自动回迁仅对无多实例、作业和事件订阅的条目开放，不承诺外部副作用的全局回滚。</p>
 *
 * <p>Spring Bean 显式使用平台前缀，避免与 Flowable 自动配置的
 * {@code processInstanceMigrationService} 重名。</p>
 */
@Service("workflowProcessInstanceMigrationService")
@RequiredArgsConstructor
public class ProcessInstanceMigrationService {

    private static final int MAX_BATCH_INSTANCES = 500;
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final ManagementService managementService;
    private final ProcessMigrationService processMigrationService;
    private final ProcessMigrationSafetyPolicy safetyPolicy;
    private final ProcessMigrationExecutionPolicy executionPolicy;

    public List<Map<String, Object>> list() {
        return jdbcTemplate.queryForList("""
                SELECT * FROM process_instance_migration_batch
                ORDER BY create_time DESC LIMIT 200
                """);
    }

    public Map<String, Object> get(String id) {
        Map<String, Object> batch = new LinkedHashMap<>(requiredBatch(id));
        batch.put("items", jdbcTemplate.queryForList("""
                SELECT * FROM process_instance_migration_item
                WHERE batch_id = ? ORDER BY create_time
                """, id));
        batch.put("audit", jdbcTemplate.queryForList("""
                SELECT * FROM process_instance_migration_audit
                WHERE batch_id = ? ORDER BY create_time
                """, id));
        return batch;
    }

    /** 创建幂等迁移批次并立即执行干运行。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(CreateBatchRequest request) {
        validateCreate(request);
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT id FROM process_instance_migration_batch WHERE idempotency_key = ?",
                request.idempotencyKey());
        if (!existing.isEmpty()) return get(text(existing.get(0).get("id")));
        ProcessDefinition source = requireDefinition(request.sourceProcessDefinitionId());
        ProcessDefinition target = requireDefinition(request.targetProcessDefinitionId());
        if (!source.getKey().equals(target.getKey())) {
            throw new IllegalArgumentException("源版本和目标版本必须属于同一流程 Key");
        }
        String id = compactId();
        String actor = actor();
        jdbcTemplate.update("""
                INSERT INTO process_instance_migration_batch (
                  id, batch_name, source_process_definition_id, target_process_definition_id,
                  status, revision, idempotency_key, activity_mapping_json,
                  variable_mapping_json, form_release_mapping_json, total_count,
                  requested_by, create_time, update_time
                ) VALUES (?, ?, ?, ?, 'CREATED', 1, ?, ?, ?, ?, ?, ?,
                          CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                """, id, request.batchName(), source.getId(), target.getId(), request.idempotencyKey(),
                write(request.activityMappings()), write(request.variableOverrides()),
                write(request.formReleaseMappings()), request.processInstanceIds().size(), actor);
        for (String instanceId : new LinkedHashSet<>(request.processInstanceIds())) {
            jdbcTemplate.update("""
                    INSERT INTO process_instance_migration_item (
                      id, batch_id, process_instance_id, source_process_definition_id,
                      target_process_definition_id, status, reversible, attempt_count,
                      create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, 'PENDING', 0, 0,
                              CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                    """, compactId(), id, instanceId, source.getId(), target.getId());
        }
        audit(id, null, "CREATE", null, "CREATED", Map.of("instanceCount", request.processInstanceIds().size()));
        return dryRun(id);
    }

    /**
     * 使用平台安全策略和 Flowable 原生迁移验证器逐实例干运行，绝不改变运行实例。
     */
    public Map<String, Object> dryRun(String batchId) {
        Map<String, Object> batch = requiredBatch(batchId);
        ProcessDefinition sourceDefinition = requireDefinition(text(batch.get("source_process_definition_id")));
        ProcessDefinition targetDefinition = requireDefinition(text(batch.get("target_process_definition_id")));
        Map<String, String> mappings = readStringMap(text(batch.get("activity_mapping_json")));
        Map<String, Object> variables = readObjectMap(text(batch.get("variable_mapping_json")));
        Map<String, String> formMappings = readStringMap(text(batch.get("form_release_mapping_json")));
        List<String> formMappingBlockers = validateFormReleaseMappings(formMappings);
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "SELECT * FROM process_instance_migration_item WHERE batch_id = ? "
                        + "AND status NOT IN ('SUCCESS', 'ROLLED_BACK') ORDER BY create_time",
                batchId);
        int ready = 0;
        int blocked = 0;
        List<Map<String, Object>> report = new ArrayList<>();
        for (Map<String, Object> item : items) {
            String itemId = text(item.get("id"));
            String instanceId = text(item.get("process_instance_id"));
            List<String> blockers = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            List<String> activities = List.of();
            boolean reversible = false;
            blockers.addAll(formMappingBlockers);
            ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(instanceId).singleResult();
            if (instance == null) {
                blockers.add("流程实例不存在、已结束或不在当前运行库");
            } else {
                activities = runtimeService.getActiveActivityIds(instanceId);
                long jobs = managementService.createJobQuery().processInstanceId(instanceId).count();
                long subscriptions = runtimeService.createEventSubscriptionQuery()
                        .processInstanceId(instanceId).count();
                Map<String, Object> currentVariables = runtimeService.getVariables(instanceId);
                boolean multiInstance = currentVariables.containsKey("nrOfInstances")
                        || currentVariables.containsKey("nrOfActiveInstances");
                ProcessDefinition actualSource = requireDefinition(instance.getProcessDefinitionId());
                SafetyAssessment assessment = safetyPolicy.assess(new SafetyInput(
                        actualSource.getKey(), targetDefinition.getKey(), activities, mappings,
                        instance.isSuspended(), multiInstance, jobs, subscriptions));
                blockers.addAll(assessment.blockers());
                warnings.addAll(assessment.warnings());
                reversible = assessment.reversible()
                        && actualSource.getId().equals(sourceDefinition.getId());
                if (!actualSource.getId().equals(sourceDefinition.getId())) {
                    blockers.add("实例当前版本与批次源版本不一致");
                }
                if (blockers.isEmpty()) {
                    ProcessInstanceMigrationDocument document = document(
                            targetDefinition.getId(), mappings,
                            executionPolicy.migrationVariables(variables, formMappings));
                    ProcessInstanceMigrationValidationResult validation =
                            processMigrationService.validateMigrationForProcessInstance(instanceId, document);
                    if (validation.hasErrors()) blockers.addAll(validation.getValidationMessages());
                }
                jdbcTemplate.update("""
                        UPDATE process_instance_migration_item
                        SET variables_snapshot_json = ? WHERE id = ?
                        """, write(safeVariables(currentVariables)), itemId);
            }
            String status = blockers.isEmpty() ? "READY" : "BLOCKED";
            if (blockers.isEmpty()) ready++; else blocked++;
            Map<String, Object> validation = new LinkedHashMap<>();
            validation.put("blockers", blockers);
            validation.put("warnings", warnings);
            jdbcTemplate.update("""
                    UPDATE process_instance_migration_item
                    SET status = ?, validation_json = ?, active_activities_json = ?,
                        reversible = ?, error_message = NULL, update_time = CURRENT_TIMESTAMP(3)
                    WHERE id = ? AND status NOT IN ('SUCCESS', 'ROLLED_BACK')
                    """, status, write(validation), write(activities), reversible ? 1 : 0, itemId);
            report.add(Map.of(
                    "processInstanceId", instanceId,
                    "status", status,
                    "blockers", blockers,
                    "warnings", warnings,
                    "reversible", reversible));
        }
        String status = blocked == 0 ? "DRY_RUN_PASS" : ready == 0 ? "DRY_RUN_BLOCKED" : "PARTIAL_BLOCKED";
        jdbcTemplate.update("""
                UPDATE process_instance_migration_batch
                SET status = ?, revision = revision + 1, dry_run_report_json = ?,
                    ready_count = ?, blocked_count = ?, update_time = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND status NOT IN ('RUNNING', 'COMPLETE')
                """, status, write(report), ready, blocked, batchId);
        audit(batchId, null, "DRY_RUN", text(batch.get("status")), status,
                Map.of("ready", ready, "blocked", blocked));
        return get(batchId);
    }

    /** 分批领取 READY 条目；单实例失败被隔离，其余实例继续。 */
    public Map<String, Object> execute(String batchId, ExecuteBatchRequest request) {
        Map<String, Object> batch = requiredBatch(batchId);
        int expected = request == null || request.expectedRevision() == null
                ? number(batch.get("revision")) : request.expectedRevision();
        int batchSize = Math.max(1, Math.min(
                request == null || request.batchSize() == null ? 20 : request.batchSize(), 100));
        int claimed = jdbcTemplate.update("""
                UPDATE process_instance_migration_batch
                SET status = 'RUNNING', revision = revision + 1,
                    started_at = COALESCE(started_at, CURRENT_TIMESTAMP(3)),
                    update_time = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND revision = ?
                  AND status IN ('DRY_RUN_PASS', 'PARTIAL_BLOCKED', 'PAUSED', 'PARTIAL_FAILED')
                """, batchId, expected);
        if (claimed != 1) throw new IllegalStateException("迁移批次状态或 revision 已变化");
        batch = requiredBatch(batchId);
        ProcessDefinition target = requireDefinition(text(batch.get("target_process_definition_id")));
        Map<String, String> mappings = readStringMap(text(batch.get("activity_mapping_json")));
        Map<String, Object> variables = readObjectMap(text(batch.get("variable_mapping_json")));
        Map<String, String> formMappings = readStringMap(text(batch.get("form_release_mapping_json")));
        List<Map<String, Object>> items = jdbcTemplate.queryForList("""
                SELECT * FROM process_instance_migration_item
                WHERE batch_id = ? AND status = 'READY'
                ORDER BY create_time LIMIT ?
                """, batchId, batchSize);
        for (Map<String, Object> item : items) {
            migrateItem(batchId, item, target, mappings, variables, formMappings);
            if ("PAUSED".equals(text(requiredBatch(batchId).get("status")))) break;
        }
        refreshBatchStatus(batchId);
        return get(batchId);
    }

    public Map<String, Object> pause(String batchId) {
        int updated = jdbcTemplate.update("""
                UPDATE process_instance_migration_batch
                SET status = 'PAUSED', revision = revision + 1, update_time = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND status IN ('RUNNING', 'DRY_RUN_PASS', 'PARTIAL_BLOCKED', 'PARTIAL_FAILED')
                """, batchId);
        if (updated != 1) throw new IllegalStateException("当前迁移批次不能暂停");
        audit(batchId, null, "PAUSE", null, "PAUSED", Map.of());
        return get(batchId);
    }

    /** 将失败或阻断条目重置后重新干运行，支持按条目选择且使用批次 revision 防止并发覆盖。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> retry(String batchId, RetryBatchRequest request) {
        Map<String, Object> batch = requiredBatch(batchId);
        int expected = request == null || request.expectedRevision() == null
                ? number(batch.get("revision")) : request.expectedRevision();
        int claimed = jdbcTemplate.update("""
                UPDATE process_instance_migration_batch
                SET status = 'RETRYING', revision = revision + 1,
                    update_time = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND revision = ?
                  AND status IN ('PARTIAL_FAILED', 'DRY_RUN_BLOCKED', 'PARTIAL_BLOCKED', 'PAUSED')
                """, batchId, expected);
        if (claimed != 1) throw new IllegalStateException("迁移批次状态或 revision 已变化");
        Set<String> selected = request == null || request.itemIds() == null
                ? Set.of() : Set.copyOf(request.itemIds());
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "SELECT id, status FROM process_instance_migration_item WHERE batch_id = ?",
                batchId);
        int reset = 0;
        for (Map<String, Object> item : items) {
            String itemId = text(item.get("id"));
            String status = text(item.get("status"));
            if ((!selected.isEmpty() && !selected.contains(itemId))
                    || !executionPolicy.retryable(status)) continue;
            reset += jdbcTemplate.update("""
                    UPDATE process_instance_migration_item
                    SET status = 'PENDING', error_message = NULL, validation_json = NULL,
                        lock_token = NULL, update_time = CURRENT_TIMESTAMP(3)
                    WHERE id = ? AND status IN ('FAILED', 'BLOCKED')
                    """, itemId);
            audit(batchId, itemId, "RETRY", status, "PENDING", Map.of());
        }
        if (reset == 0) throw new IllegalStateException("没有可重试的失败或阻断条目");
        return dryRun(batchId);
    }

    /** 只对干运行标记为可逆且尚无外部副作用证据的成功条目执行回迁。 */
    public Map<String, Object> rollbackItem(String itemId) {
        Map<String, Object> item = jdbcTemplate.queryForMap(
                "SELECT * FROM process_instance_migration_item WHERE id = ?", itemId);
        if (!"SUCCESS".equals(text(item.get("status"))) || number(item.get("reversible")) != 1) {
            throw new IllegalStateException("该迁移条目不满足自动回迁边界");
        }
        Map<String, Object> batch = requiredBatch(text(item.get("batch_id")));
        Map<String, String> mappings = readStringMap(text(batch.get("activity_mapping_json")));
        Map<String, String> reverse = new LinkedHashMap<>();
        mappings.forEach((from, to) -> reverse.put(to, from));
        Map<String, String> formMappings = readStringMap(text(batch.get("form_release_mapping_json")));
        Map<String, String> reverseForms = new LinkedHashMap<>();
        formMappings.forEach((from, to) -> reverseForms.put(to, from));
        String instanceId = text(item.get("process_instance_id"));
        ProcessInstanceMigrationDocument document = document(
                text(item.get("source_process_definition_id")), reverse,
                executionPolicy.migrationVariables(Map.of(), reverseForms));
        ProcessInstanceMigrationValidationResult validation =
                processMigrationService.validateMigrationForProcessInstance(instanceId, document);
        if (validation.hasErrors()) {
            throw new IllegalStateException("回迁校验失败: " + String.join("; ", validation.getValidationMessages()));
        }
        processMigrationService.migrateProcessInstance(instanceId, document);
        runtimeService.setVariables(instanceId, readObjectMap(text(item.get("variables_snapshot_json"))));
        jdbcTemplate.update("""
                UPDATE process_instance_migration_item
                SET status = 'ROLLED_BACK', finished_at = CURRENT_TIMESTAMP(3),
                    update_time = CURRENT_TIMESTAMP(3) WHERE id = ? AND status = 'SUCCESS'
                """, itemId);
        audit(text(item.get("batch_id")), itemId, "ROLLBACK", "SUCCESS", "ROLLED_BACK", Map.of());
        return jdbcTemplate.queryForMap("SELECT * FROM process_instance_migration_item WHERE id = ?", itemId);
    }

    private void migrateItem(
            String batchId,
            Map<String, Object> item,
            ProcessDefinition target,
            Map<String, String> mappings,
            Map<String, Object> variables,
            Map<String, String> formMappings) {
        String itemId = text(item.get("id"));
        String instanceId = text(item.get("process_instance_id"));
        int claimed = jdbcTemplate.update("""
                UPDATE process_instance_migration_item
                SET status = 'MIGRATING', attempt_count = attempt_count + 1,
                    started_at = CURRENT_TIMESTAMP(3), update_time = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND status = 'READY'
                """, itemId);
        if (claimed != 1) return;
        audit(batchId, itemId, "MIGRATE", "READY", "MIGRATING", Map.of());
        String lockToken = compactId();
        if (!acquireMigrationLock(instanceId, batchId, itemId, lockToken)) {
            jdbcTemplate.update("""
                    UPDATE process_instance_migration_item
                    SET status = 'FAILED', error_message = ?, finished_at = CURRENT_TIMESTAMP(3),
                        update_time = CURRENT_TIMESTAMP(3) WHERE id = ?
                    """, "该流程实例正在被其他迁移批次处理", itemId);
            audit(batchId, itemId, "LOCK", "MIGRATING", "FAILED", Map.of("reason", "LOCK_CONFLICT"));
            return;
        }
        jdbcTemplate.update(
                "UPDATE process_instance_migration_item SET lock_token = ? WHERE id = ?",
                lockToken, itemId);
        try {
            ProcessInstance current = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(instanceId).singleResult();
            if (current == null) throw new IllegalStateException("实例已结束或不存在");
            if (!text(item.get("source_process_definition_id")).equals(current.getProcessDefinitionId())) {
                throw new IllegalStateException("实例版本在干运行后发生变化");
            }
            ProcessInstanceMigrationDocument document = document(
                    target.getId(), mappings,
                    executionPolicy.migrationVariables(variables, formMappings));
            ProcessInstanceMigrationValidationResult validation =
                    processMigrationService.validateMigrationForProcessInstance(instanceId, document);
            if (validation.hasErrors()) {
                throw new IllegalStateException(String.join("; ", validation.getValidationMessages()));
            }
            processMigrationService.migrateProcessInstance(instanceId, document);
            ProcessInstance migrated = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(instanceId).singleResult();
            if (migrated == null || !target.getId().equals(migrated.getProcessDefinitionId())) {
                throw new IllegalStateException("迁移后流程定义版本校验失败");
            }
            Map<String, Object> postValidation = new LinkedHashMap<>();
            postValidation.put("processDefinitionId", migrated.getProcessDefinitionId());
            postValidation.put("activeActivities", runtimeService.getActiveActivityIds(instanceId));
            postValidation.put("formReleaseMappings", formMappings);
            jdbcTemplate.update("""
                    UPDATE process_instance_migration_item
                    SET status = 'SUCCESS', finished_at = CURRENT_TIMESTAMP(3),
                        update_time = CURRENT_TIMESTAMP(3), error_message = NULL,
                        form_mapping_applied = ?, post_validation_json = ?, lock_token = NULL
                    WHERE id = ? AND status = 'MIGRATING'
                    """, formMappings.isEmpty() ? 0 : 1, write(postValidation), itemId);
            audit(batchId, itemId, "MIGRATE", "MIGRATING", "SUCCESS", Map.of());
        } catch (RuntimeException exception) {
            jdbcTemplate.update("""
                    UPDATE process_instance_migration_item
                    SET status = 'FAILED', error_message = ?, finished_at = CURRENT_TIMESTAMP(3),
                        update_time = CURRENT_TIMESTAMP(3), lock_token = NULL
                    WHERE id = ?
                    """, abbreviate(exception.getMessage(), 1900), itemId);
            audit(batchId, itemId, "MIGRATE", "MIGRATING", "FAILED",
                    Map.of("failure", exception.getClass().getSimpleName()));
        } finally {
            releaseMigrationLock(instanceId, lockToken);
        }
    }

    /** 使用独立锁表阻止同一实例被不同批次并发迁移，过期锁可由后续请求安全接管。 */
    private boolean acquireMigrationLock(
            String instanceId,
            String batchId,
            String itemId,
            String lockToken) {
        jdbcTemplate.update("""
                DELETE FROM process_instance_migration_lock
                WHERE process_instance_id = ? AND expires_at < CURRENT_TIMESTAMP(3)
                """, instanceId);
        try {
            jdbcTemplate.update("""
                    INSERT INTO process_instance_migration_lock (
                      process_instance_id, batch_id, item_id, lock_token,
                      acquired_at, expires_at
                    ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(3),
                              DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 30 MINUTE))
                    """, instanceId, batchId, itemId, lockToken);
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    private void releaseMigrationLock(String instanceId, String lockToken) {
        jdbcTemplate.update("""
                DELETE FROM process_instance_migration_lock
                WHERE process_instance_id = ? AND lock_token = ?
                """, instanceId, lockToken);
    }

    private List<String> validateFormReleaseMappings(Map<String, String> mappings) {
        List<String> blockers = new ArrayList<>();
        for (Map.Entry<String, String> mapping : mappings.entrySet()) {
            if (!StringUtils.hasText(mapping.getKey()) || !StringUtils.hasText(mapping.getValue())) {
                blockers.add("表单 release 映射的源和目标不能为空");
                continue;
            }
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ui_config_release WHERE id = ?",
                    Integer.class,
                    mapping.getValue());
            if (count == null || count == 0) {
                blockers.add("目标表单 release 不存在: " + mapping.getValue());
            }
        }
        return List.copyOf(blockers);
    }

    private ProcessInstanceMigrationDocument document(
            String targetDefinitionId,
            Map<String, String> mappings,
            Map<String, Object> variables) {
        var builder = processMigrationService.createProcessInstanceMigrationBuilder()
                .migrateToProcessDefinition(targetDefinitionId);
        for (Map.Entry<String, String> mapping : mappings.entrySet()) {
            if (StringUtils.hasText(mapping.getKey()) && StringUtils.hasText(mapping.getValue())
                    && !mapping.getKey().equals(mapping.getValue())) {
                builder.addActivityMigrationMapping(
                        ActivityMigrationMapping.createMappingFor(mapping.getKey(), mapping.getValue()));
            }
        }
        if (variables != null && !variables.isEmpty()) builder.withProcessInstanceVariables(variables);
        return builder.getProcessInstanceMigrationDocument();
    }

    private void refreshBatchStatus(String batchId) {
        Map<String, Object> counts = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) total_count,
                       SUM(status = 'READY') ready_count,
                       SUM(status = 'SUCCESS') success_count,
                       SUM(status = 'FAILED') failed_count,
                       SUM(status = 'BLOCKED') blocked_count
                FROM process_instance_migration_item WHERE batch_id = ?
                """, batchId);
        int ready = number(counts.get("ready_count"));
        int failed = number(counts.get("failed_count"));
        int success = number(counts.get("success_count"));
        String status = ready > 0 ? "PAUSED" : failed > 0 ? "PARTIAL_FAILED" : "COMPLETE";
        jdbcTemplate.update("""
                UPDATE process_instance_migration_batch
                SET status = ?, revision = revision + 1, ready_count = ?, success_count = ?,
                    failed_count = ?, blocked_count = ?,
                    finished_at = CASE WHEN ? = 'COMPLETE' THEN CURRENT_TIMESTAMP(3) ELSE finished_at END,
                    update_time = CURRENT_TIMESTAMP(3)
                WHERE id = ?
                """, status, ready, success, failed, number(counts.get("blocked_count")), status, batchId);
    }

    private void validateCreate(CreateBatchRequest request) {
        if (request == null || !StringUtils.hasText(request.batchName())
                || !StringUtils.hasText(request.sourceProcessDefinitionId())
                || !StringUtils.hasText(request.targetProcessDefinitionId())
                || !StringUtils.hasText(request.idempotencyKey())) {
            throw new IllegalArgumentException("批次名称、源版本、目标版本和幂等键不能为空");
        }
        if (request.processInstanceIds() == null || request.processInstanceIds().isEmpty()) {
            throw new IllegalArgumentException("迁移实例不能为空");
        }
        if (request.processInstanceIds().size() > MAX_BATCH_INSTANCES) {
            throw new IllegalArgumentException("单个迁移批次最多包含 " + MAX_BATCH_INSTANCES + " 个实例");
        }
    }

    private ProcessDefinition requireDefinition(String id) {
        ProcessDefinition definition = repositoryService.getProcessDefinition(id);
        if (definition == null) throw new IllegalArgumentException("流程定义不存在: " + id);
        return definition;
    }

    private Map<String, Object> requiredBatch(String id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM process_instance_migration_batch WHERE id = ?", id);
        if (rows.isEmpty()) throw new IllegalArgumentException("迁移批次不存在: " + id);
        return rows.get(0);
    }

    private Map<String, Object> safeVariables(Map<String, Object> variables) {
        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            try {
                objectMapper.valueToTree(entry.getValue());
                safe.put(entry.getKey(), entry.getValue());
            } catch (RuntimeException exception) {
                safe.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return safe;
    }

    private void audit(
            String batchId,
            String itemId,
            String action,
            String from,
            String to,
            Map<String, Object> detail) {
        jdbcTemplate.update("""
                INSERT INTO process_instance_migration_audit (
                  id, batch_id, item_id, action_type, from_status, to_status,
                  detail_json, actor_id, create_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3))
                """, compactId(), batchId, itemId, action, from, to, write(detail), actor());
    }

    private Map<String, String> readStringMap(String json) {
        try {
            return StringUtils.hasText(json) ? objectMapper.readValue(json, STRING_MAP) : Map.of();
        } catch (Exception exception) {
            throw new IllegalStateException("活动映射配置损坏", exception);
        }
    }

    private Map<String, Object> readObjectMap(String json) {
        try {
            return StringUtils.hasText(json) ? objectMapper.readValue(json, OBJECT_MAP) : Map.of();
        } catch (Exception exception) {
            throw new IllegalStateException("变量配置损坏", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            throw new IllegalStateException("实例迁移文档序列化失败", exception);
        }
    }

    private String actor() {
        String value = UserContext.getUserId();
        if (!StringUtils.hasText(value)) value = UserContext.getUsername();
        if (!StringUtils.hasText(value)) value = "system-migration";
        return value;
    }

    private int number(Object value) {
        if (value == null) return 0;
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String compactId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String abbreviate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
