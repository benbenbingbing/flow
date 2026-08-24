package com.workflow.migration.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.migration.api.request.ReleaseCandidateCompensateRequest;
import com.workflow.migration.api.request.ReleaseCandidateCreateRequest;
import com.workflow.migration.api.request.ReleaseCandidateExecuteRequest;
import com.workflow.migration.api.request.ReleaseCandidatePreflightRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 应用级发布候选编排服务。
 *
 * <p>候选冻结真实 wfpack 导入条目的版本、哈希与迁移标记；预检构建依赖 DAG 和确定性步骤。
 * 发布与补偿不声明跨模块全局事务，而以持久化步骤日志包裹现有幂等发布/回滚服务。</p>
 */
@Service
@RequiredArgsConstructor
public class ReleaseCandidateService {

    private static final Set<String> INTERNAL_ASSET_TYPES = Set.of(
            "ENTITY", "PROCESS", "SYSTEM_ENTITY_UI", "DICTIONARY",
            "WORK_CALENDAR", "TASK_SLA_POLICY");
    private static final Set<String> PREFLIGHT_ALLOWED_STATUSES = Set.of(
            "DRAFT", "BLOCKED", "READY", "FAILED");
    private static final DateTimeFormatter NUMBER_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ReleaseCandidatePlanner planner;
    private final ReleaseCandidateExecutionEngine executionEngine;
    private final ReleaseCandidateDeploymentPort deploymentPort;
    private final ReleaseCandidateIdempotencyPolicy idempotencyPolicy;

    /** 查询候选列表。 */
    public List<Map<String, Object>> list() {
        return jdbcTemplate.query("""
                SELECT id, candidate_no, candidate_name, migration_tag, status,
                       preflight_status, revision, candidate_hash, source_import_id,
                       created_by, create_time, update_time, published_by, published_at
                FROM release_candidate
                WHERE deleted = 0
                ORDER BY update_time DESC, candidate_no DESC
                LIMIT 300
                """, (rs, rowNum) -> candidateSummary(rs));
    }

    /** 查询可作为候选来源的导入批次及其资产条目。 */
    public List<Map<String, Object>> sources() {
        List<SourcePackage> packages = jdbcTemplate.query("""
                SELECT id, package_no, source_environment, migration_tag, checksum, status
                FROM config_import_package
                WHERE deleted = 0
                ORDER BY imported_at DESC
                LIMIT 100
                """, (rs, rowNum) -> sourcePackage(rs));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SourcePackage source : packages) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", source.id());
            row.put("packageNo", source.packageNo());
            row.put("sourceEnvironment", source.sourceEnvironment());
            row.put("migrationTag", source.migrationTag());
            row.put("checksum", source.checksum());
            row.put("status", source.status());
            row.put("items", sourceItems(source.id()).stream()
                    .map(this::sourceItemMap)
                    .toList());
            result.add(row);
        }
        return result;
    }

    /** 查询候选完整视图，包括依赖、校验、执行步骤和报告编号。 */
    public Map<String, Object> get(String id) {
        CandidateState candidate = requiredCandidate(id, false);
        Map<String, Object> result = candidateMap(candidate);
        result.put("items", jdbcTemplate.query("""
                SELECT id, source_import_item_id, source_asset_id, asset_type,
                       business_key, asset_name, frozen_source_version,
                       frozen_source_hash, frozen_snapshot_hash,
                       frozen_target_version, frozen_target_hash, sort_order
                FROM release_candidate_item
                WHERE candidate_id = ?
                ORDER BY sort_order, business_key, id
                """, (rs, rowNum) -> candidateItemView(rs), id));
        result.put("dependencies", jdbcTemplate.query("""
                SELECT id, dependent_item_id, required_item_id, dependency_type,
                       dependency_key, required, resolved, source_description
                FROM release_candidate_dependency
                WHERE candidate_id = ?
                ORDER BY dependency_type, dependency_key, id
                """, (rs, rowNum) -> dependencyView(rs), id));
        result.put("validations", jdbcTemplate.query("""
                SELECT id, item_id, validation_code, severity, message, detail_json, create_time
                FROM release_candidate_validation
                WHERE candidate_id = ?
                ORDER BY FIELD(severity, 'BLOCKER', 'WARNING', 'INFO'), create_time, id
                """, (rs, rowNum) -> validationView(rs), id));
        result.put("steps", jdbcTemplate.query("""
                SELECT id, item_id, step_no, step_key, step_type, status,
                       input_json, output_json, duration_ms, operator, error_message,
                       recovery_action, attempt_count, started_at, finished_at
                FROM release_candidate_step
                WHERE candidate_id = ?
                ORDER BY step_no
                """, (rs, rowNum) -> stepView(rs), id));
        List<Map<String, Object>> reports = jdbcTemplate.query("""
                SELECT report_no, generated_by, generated_at
                FROM release_candidate_report WHERE candidate_id = ?
                """, (rs, rowNum) -> Map.of(
                "reportNo", rs.getString("report_no"),
                "generatedBy", Objects.toString(rs.getString("generated_by"), ""),
                "generatedAt", rs.getObject("generated_at")), id);
        result.put("report", reports.isEmpty() ? null : reports.get(0));
        return result;
    }

    /**
     * 创建候选并冻结所选资产。itemIds 为空时选择来源批次全部条目；允许创建不完整草稿，预检会阻断发布。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(ReleaseCandidateCreateRequest request) {
        SourcePackage source = requiredSourcePackage(request.getSourceImportId());
        List<SourceItem> allItems = sourceItems(source.id());
        if (allItems.isEmpty()) {
            throw new IllegalArgumentException("来源导入批次没有资产条目");
        }
        Set<String> requestedIds = normalizedIds(request.getItemIds());
        List<SourceItem> selected = requestedIds.isEmpty()
                ? allItems
                : allItems.stream().filter(item -> requestedIds.contains(item.id())).toList();
        Set<String> availableIds = new HashSet<>();
        allItems.forEach(item -> availableIds.add(item.id()));
        if (!availableIds.containsAll(requestedIds)) {
            Set<String> unknown = new LinkedHashSet<>(requestedIds);
            unknown.removeAll(availableIds);
            throw new IllegalArgumentException("存在不属于来源批次的资产条目：" + unknown);
        }
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("至少选择一个资产条目");
        }

        String candidateId = id();
        List<CandidateItem> frozenItems = new ArrayList<>();
        for (SourceItem item : selected) {
            SourceAsset asset = findSourceAsset(item.assetType(), item.businessKey());
            frozenItems.add(new CandidateItem(
                    id(), candidateId, item.id(), asset == null ? null : asset.id(),
                    item.assetType(), item.businessKey(), item.assetName(),
                    item.sourceVersion(), item.sourceHash(), sha256(text(item.snapshotJson())),
                    item.targetBeforeVersion(), item.targetBeforeHash(),
                    item.dependenciesJson(), ReleaseCandidatePlanner.sortOrder(item.assetType())));
        }
        String candidateHash = fingerprint(source, frozenItems);
        String actor = actor();
        String candidateNo = "RC-" + NUMBER_TIME.format(LocalDateTime.now())
                + "-" + candidateId.substring(0, 6).toUpperCase();
        jdbcTemplate.update("""
                INSERT INTO release_candidate (
                  id, candidate_no, candidate_name, description, source_import_id,
                  source_package_checksum, migration_tag, status, preflight_status,
                  revision, candidate_hash, created_by, create_time, updated_by, update_time, deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'DRAFT', 'NOT_RUN', 1, ?, ?, CURRENT_TIMESTAMP,
                          ?, CURRENT_TIMESTAMP, 0)
                """, candidateId, candidateNo, request.getCandidateName().trim(),
                trimToNull(request.getDescription()), source.id(), source.checksum(),
                source.migrationTag(), candidateHash, actor, actor);
        for (CandidateItem item : frozenItems) {
            jdbcTemplate.update("""
                    INSERT INTO release_candidate_item (
                      id, candidate_id, source_import_item_id, source_asset_id,
                      asset_type, business_key, asset_name, frozen_source_version,
                      frozen_source_hash, frozen_snapshot_hash, frozen_target_version,
                      frozen_target_hash, dependencies_json, sort_order, create_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """, item.id(), item.candidateId(), item.sourceImportItemId(),
                    item.sourceAssetId(), item.assetType(), item.businessKey(), item.assetName(),
                    item.frozenSourceVersion(), item.frozenSourceHash(), item.frozenSnapshotHash(),
                    item.frozenTargetVersion(), item.frozenTargetHash(), item.dependenciesJson(),
                    item.sortOrder());
        }
        refreshReport(candidateId);
        return get(candidateId);
    }

    /**
     * 统一预检：复核来源漂移、包级发布完整性、冲突/映射、硬依赖和 DAG 环路，并生成顺序预览。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> preflight(
            String candidateId,
            ReleaseCandidatePreflightRequest request) {
        CandidateState candidate = requiredCandidate(candidateId, true);
        if (!PREFLIGHT_ALLOWED_STATUSES.contains(candidate.status())) {
            throw new IllegalStateException("当前状态不允许重新预检：" + candidate.status());
        }
        requireRevision(candidate, request.getExpectedRevision());
        jdbcTemplate.update("DELETE FROM release_candidate_dependency WHERE candidate_id = ?", candidateId);
        jdbcTemplate.update("DELETE FROM release_candidate_validation WHERE candidate_id = ?", candidateId);
        jdbcTemplate.update("DELETE FROM release_candidate_step WHERE candidate_id = ?", candidateId);

        List<CandidateItem> items = candidateItems(candidateId);
        SourcePackage source = requiredSourcePackage(candidate.sourceImportId());
        List<SourceItem> allSourceItems = sourceItems(source.id());
        Map<String, SourceItem> currentById = new HashMap<>();
        Map<String, SourceItem> allByAssetKey = new HashMap<>();
        for (SourceItem sourceItem : allSourceItems) {
            currentById.put(sourceItem.id(), sourceItem);
            allByAssetKey.put(assetKey(sourceItem.assetType(), sourceItem.businessKey()), sourceItem);
        }
        Map<String, CandidateItem> selectedByAssetKey = new HashMap<>();
        items.forEach(item -> selectedByAssetKey.put(
                assetKey(item.assetType(), item.businessKey()), item));

        List<ValidationEntry> validations = new ArrayList<>();
        if (!"ANALYZED".equals(source.status())) {
            validations.add(blocker("IMPORT_NOT_ANALYZED", null,
                    "来源导入批次必须处于 ANALYZED，当前为 " + source.status()));
        }
        if (items.size() != allSourceItems.size()
                || !items.stream().map(CandidateItem::sourceImportItemId).collect(
                java.util.stream.Collectors.toSet()).equals(currentById.keySet())) {
            validations.add(blocker("INCOMPLETE_IMPORT_BATCH", null,
                    "当前发布器按导入批次执行，候选必须覆盖该批次全部 "
                            + allSourceItems.size() + " 个资产条目"));
        }
        validations.addAll(driftValidations(candidate, source, items, currentById));

        for (CandidateItem item : items) {
            SourceItem current = currentById.get(item.sourceImportItemId());
            if (current == null) {
                continue;
            }
            if (!"RESOLVED".equals(current.mappingStatus())) {
                validations.add(blocker("UNRESOLVED_MAPPING", item.id(),
                        item.businessKey() + " 仍有未解析的环境映射"));
            }
            if (Set.of("CONFLICT", "LOCAL_CHANGED").contains(current.comparisonStatus())) {
                validations.add(blocker("CONFIG_CONFLICT", item.id(),
                        item.businessKey() + " 存在目标环境冲突：" + current.comparisonStatus()));
            }
            if (StringUtils.hasText(current.errorMessage())) {
                validations.add(blocker("SOURCE_ITEM_ERROR", item.id(),
                        item.businessKey() + "：" + current.errorMessage()));
            }
        }

        List<DependencyLink> links = new ArrayList<>();
        for (CandidateItem item : items) {
            SourceItem current = currentById.get(item.sourceImportItemId());
            List<DependencySpec> dependencies;
            try {
                dependencies = dependencies(item);
            } catch (IllegalArgumentException error) {
                validations.add(blocker("DEPENDENCY_DOCUMENT_INVALID", item.id(), error.getMessage()));
                continue;
            }
            for (DependencySpec dependency : dependencies) {
                String key = assetKey(dependency.type(), dependency.key());
                CandidateItem target = selectedByAssetKey.get(key);
                boolean omittedInternal = INTERNAL_ASSET_TYPES.contains(dependency.type())
                        && allByAssetKey.containsKey(key) && target == null;
                boolean resolved = target != null || (!omittedInternal
                        && current != null
                        && "RESOLVED".equals(current.mappingStatus())
                        && dependency.resolved()
                        && !dependency.missing());
                DependencyLink link = new DependencyLink(
                        id(), candidateId, item.id(), target == null ? null : target.id(),
                        dependency.type(), dependency.key(), dependency.required(), resolved,
                        dependency.source());
                links.add(link);
                persistDependency(link);
                if (dependency.required() && !resolved) {
                    validations.add(blocker("MISSING_HARD_DEPENDENCY", item.id(),
                            item.businessKey() + " 缺少硬依赖 "
                                    + dependency.type() + ":" + dependency.key()));
                }
            }
        }

        ReleaseCandidatePlanner.PlanResult plan = planner.plan(
                items.stream().map(item -> new ReleaseCandidatePlanner.ItemNode(
                        item.id(), item.assetType(), item.businessKey(), item.sortOrder())).toList(),
                links.stream().filter(DependencyLink::required)
                        .map(link -> new ReleaseCandidatePlanner.DependencyEdge(
                                link.dependentItemId(), link.requiredItemId()))
                        .toList());
        if (plan.hasCycle()) {
            validations.add(blocker("DEPENDENCY_CYCLE", null,
                    "发布候选存在依赖环：" + plan.cycleItemIds()));
        }
        validations.add(new ValidationEntry(
                "ORDER_PREVIEW", "INFO", null,
                "已生成 " + plan.steps().size() + " 个确定性执行步骤",
                Map.of("orderedItemIds", plan.orderedItemIds())));
        validations.forEach(entry -> persistValidation(candidateId, entry));
        persistSteps(candidate, items, plan.steps());

        boolean blocked = validations.stream().anyMatch(entry -> "BLOCKER".equals(entry.severity()));
        String actor = actor();
        int updated = jdbcTemplate.update("""
                UPDATE release_candidate
                SET status = ?, preflight_status = ?, revision = revision + 1,
                    validated_by = ?, validated_at = CURRENT_TIMESTAMP,
                    updated_by = ?, update_time = CURRENT_TIMESTAMP, failure_step_id = NULL
                WHERE id = ? AND revision = ? AND deleted = 0
                """, blocked ? "BLOCKED" : "READY", blocked ? "FAIL" : "PASS",
                actor, actor, candidateId, candidate.revision());
        if (updated != 1) {
            throw new IllegalStateException("发布候选已被其他会话修改，请刷新后重试");
        }
        refreshReport(candidateId);
        return get(candidateId);
    }

    /** 执行首次发布。 */
    @SystemAudit(
            module = AuditModule.MIGRATION,
            action = AuditAction.PUBLISH,
            operation = "执行应用级发布候选",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "RELEASE_CANDIDATE",
            targetIdArg = 0,
            captureArguments = true,
            captureResult = true)
    public Map<String, Object> publish(
            String candidateId,
            ReleaseCandidateExecuteRequest request) {
        return execute(candidateId, request, false);
    }

    /** 从失败步骤续跑，仍需重新提交修订号、冻结哈希和幂等键。 */
    @SystemAudit(
            module = AuditModule.MIGRATION,
            action = AuditAction.PUBLISH,
            operation = "续跑应用级发布候选",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "RELEASE_CANDIDATE",
            targetIdArg = 0,
            captureArguments = true,
            captureResult = true)
    public Map<String, Object> resume(
            String candidateId,
            ReleaseCandidateExecuteRequest request) {
        return execute(candidateId, request, true);
    }

    /**
     * 对已执行候选调用真实迁移回滚；自动回滚不可用时明确进入 MANUAL_REQUIRED。
     */
    @SystemAudit(
            module = AuditModule.MIGRATION,
            action = AuditAction.ROLLBACK,
            operation = "补偿应用级发布候选",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "RELEASE_CANDIDATE",
            targetIdArg = 0,
            captureArguments = true,
            captureResult = true)
    public Map<String, Object> compensate(
            String candidateId,
            ReleaseCandidateCompensateRequest request) {
        CandidateState candidate = requiredCandidate(candidateId, false);
        if (!Set.of("PUBLISHED", "FAILED", "MANUAL_REQUIRED").contains(candidate.status())) {
            throw new IllegalStateException("当前状态不允许补偿：" + candidate.status());
        }
        String actor = actor();
        int claimed = jdbcTemplate.update("""
                UPDATE release_candidate
                SET status = 'COMPENSATING', revision = revision + 1,
                    updated_by = ?, update_time = CURRENT_TIMESTAMP
                WHERE id = ? AND revision = ? AND status = ? AND deleted = 0
                """, actor, candidateId, candidate.revision(), candidate.status());
        if (claimed != 1) {
            throw new IllegalStateException("发布候选状态已变化，请刷新后重试");
        }
        Integer maxStep = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(step_no), 0) FROM release_candidate_step WHERE candidate_id = ?",
                Integer.class, candidateId);
        String stepId = id();
        jdbcTemplate.update("""
                INSERT INTO release_candidate_step (
                  id, candidate_id, step_no, step_key, step_type, status, input_json,
                  operator, attempt_count, started_at, create_time, update_time
                ) VALUES (?, ?, ?, 'ROLLBACK_IMPORT_PACKAGE', 'ROLLBACK_IMPORT_PACKAGE',
                          'RUNNING', ?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, stepId, candidateId, (maxStep == null ? 0 : maxStep) + 1,
                writeJson(Map.of("reason", request.getReason().trim())), actor);
        long started = System.nanoTime();
        try {
            Map<String, Object> output = deploymentPort.rollbackImport(candidate.sourceImportId());
            jdbcTemplate.update("""
                    UPDATE release_candidate_step
                    SET status = 'COMPENSATED', output_json = ?, duration_ms = ?,
                        finished_at = CURRENT_TIMESTAMP, update_time = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, writeJson(output), elapsedMillis(started), stepId);
            jdbcTemplate.update("""
                    UPDATE release_candidate_step
                    SET status = 'COMPENSATED', recovery_action = '已由迁移批次回滚补偿',
                        update_time = CURRENT_TIMESTAMP
                    WHERE candidate_id = ? AND step_type = 'DEPLOY_IMPORT_PACKAGE'
                      AND status = 'COMPLETED'
                    """, candidateId);
            jdbcTemplate.update("""
                    UPDATE release_candidate
                    SET status = 'COMPENSATED', revision = revision + 1,
                        failure_step_id = NULL, updated_by = ?, update_time = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, actor, candidateId);
        } catch (RuntimeException error) {
            jdbcTemplate.update("""
                    UPDATE release_candidate_step
                    SET status = 'MANUAL_REQUIRED', error_message = ?, duration_ms = ?,
                        recovery_action = '检查迁移批次状态并按报告逐项人工恢复',
                        finished_at = CURRENT_TIMESTAMP, update_time = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, safeError(error), elapsedMillis(started), stepId);
            jdbcTemplate.update("""
                    UPDATE release_candidate
                    SET status = 'MANUAL_REQUIRED', revision = revision + 1,
                        failure_step_id = ?, updated_by = ?, update_time = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, stepId, actor, candidateId);
            refreshReport(candidateId);
            throw new IllegalStateException("自动补偿失败，候选已转为人工恢复：" + safeError(error), error);
        }
        refreshReport(candidateId);
        return get(candidateId);
    }

    /** 获取可下载的 JSON 审计报告。 */
    public ReportFile report(String candidateId) {
        requiredCandidate(candidateId, false);
        List<ReportFile> reports = jdbcTemplate.query("""
                SELECT report_no, report_json
                FROM release_candidate_report WHERE candidate_id = ?
                """, (rs, rowNum) -> new ReportFile(
                rs.getString("report_no") + ".json",
                rs.getString("report_json").getBytes(StandardCharsets.UTF_8)), candidateId);
        if (reports.isEmpty()) {
            refreshReport(candidateId);
            return report(candidateId);
        }
        return reports.get(0);
    }

    private Map<String, Object> execute(
            String candidateId,
            ReleaseCandidateExecuteRequest request,
            boolean resume) {
        CandidateState candidate = requiredCandidate(candidateId, false);
        requireRevision(candidate, request.getExpectedRevision());
        if (!Objects.equals(candidate.candidateHash(), request.getCandidateHash())) {
            throw new IllegalStateException("候选哈希不匹配，请重新预检并确认当前版本");
        }
        ReleaseCandidateIdempotencyPolicy.Decision decision = idempotencyPolicy.decide(
                candidate.status(), candidate.idempotencyKey(), request.getIdempotencyKey(), resume);
        if (decision == ReleaseCandidateIdempotencyPolicy.Decision.RETURN_EXISTING
                || decision == ReleaseCandidateIdempotencyPolicy.Decision.IN_PROGRESS) {
            return get(candidateId);
        }
        if (decision == ReleaseCandidateIdempotencyPolicy.Decision.REJECT) {
            throw new IllegalStateException("当前状态或幂等键不允许执行：" + candidate.status());
        }
        if (!"PASS".equals(candidate.preflightStatus())) {
            throw new IllegalStateException("发布候选必须先通过统一预检");
        }
        SourcePackage source = requiredSourcePackage(candidate.sourceImportId());
        if (!"ANALYZED".equals(source.status())
                && !(resume && "PUBLISHED".equals(source.status()))) {
            throw new IllegalStateException("来源导入批次状态不允许发布：" + source.status());
        }
        List<CandidateItem> items = candidateItems(candidateId);
        Map<String, SourceItem> currentById = new HashMap<>();
        sourceItems(source.id()).forEach(item -> currentById.put(item.id(), item));
        List<ValidationEntry> drift = driftValidations(candidate, source, items, currentById);
        if (!drift.isEmpty()) {
            throw new IllegalStateException("发布前检测到资产漂移：" + drift.get(0).message());
        }
        int claimed = jdbcTemplate.update("""
                UPDATE release_candidate
                SET status = 'PUBLISHING', idempotency_key = ?, revision = revision + 1,
                    failure_step_id = NULL, updated_by = ?, update_time = CURRENT_TIMESTAMP
                WHERE id = ? AND revision = ? AND candidate_hash = ? AND status = ? AND deleted = 0
                """, request.getIdempotencyKey(), actor(), candidateId, candidate.revision(),
                candidate.candidateHash(), candidate.status());
        if (claimed != 1) {
            throw new IllegalStateException("发布候选已被其他请求抢占，请刷新状态");
        }
        if (resume) {
            jdbcTemplate.update("""
                    UPDATE release_candidate_step
                    SET status = 'NOT_EXECUTED', error_message = NULL,
                        recovery_action = NULL, started_at = NULL, finished_at = NULL,
                        update_time = CURRENT_TIMESTAMP
                    WHERE candidate_id = ? AND status IN ('FAILED', 'MANUAL_REQUIRED')
                    """, candidateId);
        }
        List<ReleaseCandidateExecutionEngine.ExecutionStep> steps = jdbcTemplate.query("""
                SELECT id, step_type FROM release_candidate_step
                WHERE candidate_id = ? AND status = 'NOT_EXECUTED'
                ORDER BY step_no
                """, (rs, rowNum) -> new ReleaseCandidateExecutionEngine.ExecutionStep(
                rs.getString("id"), rs.getString("step_type")), candidateId);
        String actor = actor();
        try {
            executionEngine.execute(candidate.sourceImportId(), steps, new DatabaseStepJournal(actor));
            jdbcTemplate.update("""
                    UPDATE release_candidate
                    SET status = 'PUBLISHED', revision = revision + 1,
                        published_by = ?, published_at = CURRENT_TIMESTAMP,
                        updated_by = ?, update_time = CURRENT_TIMESTAMP, failure_step_id = NULL
                    WHERE id = ?
                    """, actor, actor, candidateId);
        } catch (ReleaseCandidateExecutionEngine.StepExecutionException error) {
            jdbcTemplate.update("""
                    UPDATE release_candidate
                    SET status = 'FAILED', revision = revision + 1,
                        failure_step_id = ?, updated_by = ?, update_time = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, error.getStepId(), actor, candidateId);
            refreshReport(candidateId);
            throw new IllegalStateException(
                    "发布候选在步骤 " + error.getStepId() + " 失败，后续步骤已停止："
                            + safeError(error), error);
        }
        refreshReport(candidateId);
        return get(candidateId);
    }

    private List<ValidationEntry> driftValidations(
            CandidateState candidate,
            SourcePackage source,
            List<CandidateItem> items,
            Map<String, SourceItem> currentById) {
        List<ValidationEntry> result = new ArrayList<>();
        if (!Objects.equals(candidate.migrationTag(), source.migrationTag())
                || !Objects.equals(candidate.sourcePackageChecksum(), source.checksum())) {
            result.add(blocker("PACKAGE_DRIFT", null,
                    "来源导入包的迁移标记或校验和已经变化"));
        }
        for (CandidateItem item : items) {
            SourceItem current = currentById.get(item.sourceImportItemId());
            if (current == null) {
                result.add(blocker("SOURCE_ITEM_REMOVED", item.id(),
                        "来源资产条目已不存在：" + item.businessKey()));
                continue;
            }
            String currentSnapshotHash = sha256(text(current.snapshotJson()));
            if (!Objects.equals(item.frozenSourceVersion(), current.sourceVersion())
                    || !Objects.equals(item.frozenSourceHash(), current.sourceHash())
                    || !Objects.equals(item.frozenSnapshotHash(), currentSnapshotHash)) {
                result.add(blocker("ASSET_DRIFT", item.id(),
                        item.businessKey() + " 的版本或内容哈希已经变化"));
            }
            if (StringUtils.hasText(item.sourceAssetId())) {
                List<SourceAsset> assets = jdbcTemplate.query("""
                        SELECT id, source_version, content_hash
                        FROM config_migration_asset WHERE id = ? AND deleted = 0
                        """, (rs, rowNum) -> new SourceAsset(
                        rs.getString("id"), rs.getInt("source_version"),
                        rs.getString("content_hash")), item.sourceAssetId());
                if (assets.isEmpty()) {
                    result.add(blocker("INDEXED_ASSET_REMOVED", item.id(),
                            item.businessKey() + " 对应的迁移资产索引已不存在"));
                }
            }
        }
        return result;
    }

    private void persistSteps(
            CandidateState candidate,
            List<CandidateItem> items,
            List<ReleaseCandidatePlanner.PlannedStep> steps) {
        Map<String, CandidateItem> byId = new HashMap<>();
        items.forEach(item -> byId.put(item.id(), item));
        for (ReleaseCandidatePlanner.PlannedStep step : steps) {
            CandidateItem item = step.itemId() == null ? null : byId.get(step.itemId());
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("migrationTag", candidate.migrationTag());
            input.put("candidateHash", candidate.candidateHash());
            input.put("assetType", step.assetType());
            input.put("businessKey", step.businessKey());
            if (item != null) {
                input.put("sourceVersion", item.frozenSourceVersion());
                input.put("sourceHash", item.frozenSourceHash());
                input.put("snapshotHash", item.frozenSnapshotHash());
                input.put("sourceAssetId", item.sourceAssetId());
            }
            jdbcTemplate.update("""
                    INSERT INTO release_candidate_step (
                      id, candidate_id, item_id, step_no, step_key, step_type,
                      status, input_json, attempt_count, create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, ?, 'NOT_EXECUTED', ?, 0,
                              CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, id(), candidate.id(), step.itemId(), step.stepNo(), step.stepKey(),
                    step.stepType(), writeJson(input));
        }
    }

    private List<DependencySpec> dependencies(CandidateItem item) {
        if (StringUtils.hasText(item.sourceAssetId())) {
            List<DependencySpec> indexed = jdbcTemplate.query("""
                    SELECT dependency_type, dependency_key, required, source_description
                    FROM config_migration_asset_dependency
                    WHERE asset_id = ?
                    ORDER BY dependency_type, dependency_key
                    """, (rs, rowNum) -> new DependencySpec(
                    normalizedType(rs.getString("dependency_type")),
                    rs.getString("dependency_key"), rs.getBoolean("required"),
                    true, false, rs.getString("source_description")), item.sourceAssetId());
            if (!indexed.isEmpty()) {
                return indexed;
            }
        }
        if (!StringUtils.hasText(item.dependenciesJson())) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(item.dependenciesJson());
            JsonNode array = root.isArray() ? root : root.path("dependencies");
            if (!array.isArray()) {
                return List.of();
            }
            List<DependencySpec> result = new ArrayList<>();
            for (JsonNode node : array) {
                String type = normalizedType(node.path("type").asText(null));
                String key = trimToNull(node.path("key").asText(null));
                if (type == null || key == null) {
                    continue;
                }
                result.add(new DependencySpec(
                        type, key,
                        !node.has("required") || node.path("required").asBoolean(true),
                        !node.has("resolved") || node.path("resolved").asBoolean(true),
                        node.path("missing").asBoolean(false),
                        node.path("source").asText(null)));
            }
            return result;
        } catch (Exception error) {
            throw new IllegalArgumentException(
                    item.businessKey() + " 的依赖文档无法解析：" + safeError(error), error);
        }
    }

    private void persistDependency(DependencyLink link) {
        jdbcTemplate.update("""
                INSERT INTO release_candidate_dependency (
                  id, candidate_id, dependent_item_id, required_item_id,
                  dependency_type, dependency_key, required, resolved,
                  source_description, create_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, link.id(), link.candidateId(), link.dependentItemId(), link.requiredItemId(),
                link.dependencyType(), link.dependencyKey(), link.required(), link.resolved(),
                link.sourceDescription());
    }

    private void persistValidation(String candidateId, ValidationEntry entry) {
        jdbcTemplate.update("""
                INSERT INTO release_candidate_validation (
                  id, candidate_id, item_id, validation_code, severity,
                  message, detail_json, create_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, id(), candidateId, entry.itemId(), entry.code(), entry.severity(),
                entry.message(), entry.detail().isEmpty() ? null : writeJson(entry.detail()));
    }

    private void refreshReport(String candidateId) {
        CandidateState candidate = requiredCandidate(candidateId, false);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportSchemaVersion", 1);
        report.put("generatedAt", LocalDateTime.now().toString());
        report.put("candidate", candidateMap(candidate));
        Map<String, Object> detail = get(candidateId);
        report.put("items", detail.get("items"));
        report.put("dependencies", detail.get("dependencies"));
        report.put("validations", detail.get("validations"));
        report.put("steps", detail.get("steps"));
        report.put("migrationTrace", Map.of(
                "sourceImportId", candidate.sourceImportId(),
                "migrationTag", candidate.migrationTag(),
                "sourcePackageChecksum", Objects.toString(candidate.sourcePackageChecksum(), ""),
                "candidateHash", candidate.candidateHash()));
        String reportNo = "RPT-" + candidate.candidateNo();
        jdbcTemplate.update("""
                INSERT INTO release_candidate_report (
                  id, candidate_id, report_no, report_json, generated_by, generated_at
                ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                  report_json = VALUES(report_json), generated_by = VALUES(generated_by),
                  generated_at = CURRENT_TIMESTAMP
                """, id(), candidateId, reportNo, writeJson(report), actor());
    }

    private CandidateState requiredCandidate(String id, boolean lock) {
        String suffix = lock ? " FOR UPDATE" : "";
        List<CandidateState> rows = jdbcTemplate.query("""
                SELECT id, candidate_no, candidate_name, description, source_import_id,
                       source_package_checksum, migration_tag, status, preflight_status,
                       revision, candidate_hash, idempotency_key, failure_step_id,
                       created_by, create_time, updated_by, update_time,
                       validated_by, validated_at, published_by, published_at
                FROM release_candidate WHERE id = ? AND deleted = 0
                """ + suffix, (rs, rowNum) -> candidateState(rs), id);
        if (rows.size() != 1) {
            throw new IllegalArgumentException("发布候选不存在：" + id);
        }
        return rows.get(0);
    }

    private SourcePackage requiredSourcePackage(String id) {
        List<SourcePackage> rows = jdbcTemplate.query("""
                SELECT id, package_no, source_environment, migration_tag, checksum, status
                FROM config_import_package WHERE id = ? AND deleted = 0
                """, (rs, rowNum) -> sourcePackage(rs), id);
        if (rows.size() != 1) {
            throw new IllegalArgumentException("配置迁移导入批次不存在：" + id);
        }
        return rows.get(0);
    }

    private List<SourceItem> sourceItems(String importId) {
        return jdbcTemplate.query("""
                SELECT id, import_package_id, asset_type, business_key, asset_name,
                       source_version, source_hash, target_before_version,
                       target_before_hash, comparison_status, mapping_status,
                       publish_status, snapshot_json, dependencies_json, error_message
                FROM config_import_item
                WHERE import_package_id = ?
                ORDER BY asset_type, business_key, id
                """, (rs, rowNum) -> new SourceItem(
                rs.getString("id"), rs.getString("import_package_id"),
                normalizedType(rs.getString("asset_type")), rs.getString("business_key"),
                rs.getString("asset_name"), nullableInt(rs, "source_version"),
                rs.getString("source_hash"), nullableInt(rs, "target_before_version"),
                rs.getString("target_before_hash"), rs.getString("comparison_status"),
                rs.getString("mapping_status"), rs.getString("publish_status"),
                rs.getString("snapshot_json"), rs.getString("dependencies_json"),
                rs.getString("error_message")), importId);
    }

    private List<CandidateItem> candidateItems(String candidateId) {
        return jdbcTemplate.query("""
                SELECT id, candidate_id, source_import_item_id, source_asset_id,
                       asset_type, business_key, asset_name, frozen_source_version,
                       frozen_source_hash, frozen_snapshot_hash, frozen_target_version,
                       frozen_target_hash, dependencies_json, sort_order
                FROM release_candidate_item
                WHERE candidate_id = ?
                ORDER BY sort_order, business_key, id
                """, (rs, rowNum) -> new CandidateItem(
                rs.getString("id"), rs.getString("candidate_id"),
                rs.getString("source_import_item_id"), rs.getString("source_asset_id"),
                normalizedType(rs.getString("asset_type")), rs.getString("business_key"),
                rs.getString("asset_name"), nullableInt(rs, "frozen_source_version"),
                rs.getString("frozen_source_hash"), rs.getString("frozen_snapshot_hash"),
                nullableInt(rs, "frozen_target_version"), rs.getString("frozen_target_hash"),
                rs.getString("dependencies_json"), rs.getInt("sort_order")), candidateId);
    }

    private SourceAsset findSourceAsset(String assetType, String businessKey) {
        List<SourceAsset> assets = jdbcTemplate.query("""
                SELECT id, source_version, content_hash
                FROM config_migration_asset
                WHERE asset_type = ? AND business_key = ? AND deleted = 0
                ORDER BY source_version DESC, create_time DESC
                LIMIT 1
                """, (rs, rowNum) -> new SourceAsset(
                rs.getString("id"), nullableInt(rs, "source_version"),
                rs.getString("content_hash")), assetType, businessKey);
        return assets.isEmpty() ? null : assets.get(0);
    }

    private String fingerprint(SourcePackage source, List<CandidateItem> items) {
        StringBuilder canonical = new StringBuilder()
                .append(source.id()).append('|')
                .append(text(source.migrationTag())).append('|')
                .append(text(source.checksum())).append('\n');
        items.stream().sorted(Comparator.comparing(CandidateItem::sourceImportItemId))
                .forEach(item -> canonical
                        .append(item.sourceImportItemId()).append('|')
                        .append(item.assetType()).append('|')
                        .append(item.businessKey()).append('|')
                        .append(item.frozenSourceVersion()).append('|')
                        .append(text(item.frozenSourceHash())).append('|')
                        .append(item.frozenSnapshotHash()).append('\n'));
        return sha256(canonical.toString());
    }

    private Map<String, Object> candidateSummary(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("candidateNo", rs.getString("candidate_no"));
        row.put("candidateName", rs.getString("candidate_name"));
        row.put("migrationTag", rs.getString("migration_tag"));
        row.put("status", rs.getString("status"));
        row.put("preflightStatus", rs.getString("preflight_status"));
        row.put("revision", rs.getInt("revision"));
        row.put("candidateHash", rs.getString("candidate_hash"));
        row.put("sourceImportId", rs.getString("source_import_id"));
        row.put("createdBy", rs.getString("created_by"));
        row.put("createdAt", rs.getObject("create_time"));
        row.put("updatedAt", rs.getObject("update_time"));
        row.put("publishedBy", rs.getString("published_by"));
        row.put("publishedAt", rs.getObject("published_at"));
        return row;
    }

    private Map<String, Object> candidateMap(CandidateState candidate) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", candidate.id());
        row.put("candidateNo", candidate.candidateNo());
        row.put("candidateName", candidate.candidateName());
        row.put("description", candidate.description());
        row.put("sourceImportId", candidate.sourceImportId());
        row.put("sourcePackageChecksum", candidate.sourcePackageChecksum());
        row.put("migrationTag", candidate.migrationTag());
        row.put("status", candidate.status());
        row.put("preflightStatus", candidate.preflightStatus());
        row.put("revision", candidate.revision());
        row.put("candidateHash", candidate.candidateHash());
        row.put("idempotencyKey", candidate.idempotencyKey());
        row.put("failureStepId", candidate.failureStepId());
        row.put("createdBy", candidate.createdBy());
        row.put("createdAt", candidate.createdAt());
        row.put("updatedBy", candidate.updatedBy());
        row.put("updatedAt", candidate.updatedAt());
        row.put("validatedBy", candidate.validatedBy());
        row.put("validatedAt", candidate.validatedAt());
        row.put("publishedBy", candidate.publishedBy());
        row.put("publishedAt", candidate.publishedAt());
        return row;
    }

    private Map<String, Object> sourceItemMap(SourceItem item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.id());
        row.put("assetType", item.assetType());
        row.put("businessKey", item.businessKey());
        row.put("assetName", item.assetName());
        row.put("sourceVersion", item.sourceVersion());
        row.put("sourceHash", item.sourceHash());
        row.put("comparisonStatus", item.comparisonStatus());
        row.put("mappingStatus", item.mappingStatus());
        row.put("publishStatus", item.publishStatus());
        row.put("errorMessage", item.errorMessage());
        return row;
    }

    private Map<String, Object> candidateItemView(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("sourceImportItemId", rs.getString("source_import_item_id"));
        row.put("sourceAssetId", rs.getString("source_asset_id"));
        row.put("assetType", rs.getString("asset_type"));
        row.put("businessKey", rs.getString("business_key"));
        row.put("assetName", rs.getString("asset_name"));
        row.put("frozenSourceVersion", nullableInt(rs, "frozen_source_version"));
        row.put("frozenSourceHash", rs.getString("frozen_source_hash"));
        row.put("frozenSnapshotHash", rs.getString("frozen_snapshot_hash"));
        row.put("frozenTargetVersion", nullableInt(rs, "frozen_target_version"));
        row.put("frozenTargetHash", rs.getString("frozen_target_hash"));
        row.put("sortOrder", rs.getInt("sort_order"));
        return row;
    }

    private Map<String, Object> dependencyView(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("dependentItemId", rs.getString("dependent_item_id"));
        row.put("requiredItemId", rs.getString("required_item_id"));
        row.put("dependencyType", rs.getString("dependency_type"));
        row.put("dependencyKey", rs.getString("dependency_key"));
        row.put("required", rs.getBoolean("required"));
        row.put("resolved", rs.getBoolean("resolved"));
        row.put("sourceDescription", rs.getString("source_description"));
        return row;
    }

    private Map<String, Object> validationView(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("itemId", rs.getString("item_id"));
        row.put("validationCode", rs.getString("validation_code"));
        row.put("severity", rs.getString("severity"));
        row.put("message", rs.getString("message"));
        row.put("detail", readJson(rs.getString("detail_json")));
        row.put("createdAt", rs.getObject("create_time"));
        return row;
    }

    private Map<String, Object> stepView(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("itemId", rs.getString("item_id"));
        row.put("stepNo", rs.getInt("step_no"));
        row.put("stepKey", rs.getString("step_key"));
        row.put("stepType", rs.getString("step_type"));
        row.put("status", rs.getString("status"));
        row.put("input", readJson(rs.getString("input_json")));
        row.put("output", readJson(rs.getString("output_json")));
        row.put("durationMs", rs.getObject("duration_ms"));
        row.put("operator", rs.getString("operator"));
        row.put("errorMessage", rs.getString("error_message"));
        row.put("recoveryAction", rs.getString("recovery_action"));
        row.put("attemptCount", rs.getInt("attempt_count"));
        row.put("startedAt", rs.getObject("started_at"));
        row.put("finishedAt", rs.getObject("finished_at"));
        return row;
    }

    private CandidateState candidateState(ResultSet rs) throws SQLException {
        return new CandidateState(
                rs.getString("id"), rs.getString("candidate_no"),
                rs.getString("candidate_name"), rs.getString("description"),
                rs.getString("source_import_id"), rs.getString("source_package_checksum"),
                rs.getString("migration_tag"), rs.getString("status"),
                rs.getString("preflight_status"), rs.getInt("revision"),
                rs.getString("candidate_hash"), rs.getString("idempotency_key"),
                rs.getString("failure_step_id"), rs.getString("created_by"),
                rs.getObject("create_time"), rs.getString("updated_by"),
                rs.getObject("update_time"), rs.getString("validated_by"),
                rs.getObject("validated_at"), rs.getString("published_by"),
                rs.getObject("published_at"));
    }

    private SourcePackage sourcePackage(ResultSet rs) throws SQLException {
        return new SourcePackage(
                rs.getString("id"), rs.getString("package_no"),
                rs.getString("source_environment"), rs.getString("migration_tag"),
                rs.getString("checksum"), rs.getString("status"));
    }

    private void requireRevision(CandidateState candidate, Integer expectedRevision) {
        if (expectedRevision == null || expectedRevision != candidate.revision()) {
            throw new IllegalStateException(
                    "发布候选修订号冲突，当前 r" + candidate.revision()
                            + "，请求 r" + expectedRevision);
        }
    }

    private Set<String> normalizedIds(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null && !result.add(normalized)) {
                throw new IllegalArgumentException("资产条目不能重复选择：" + normalized);
            }
        }
        return result;
    }

    private ValidationEntry blocker(String code, String itemId, String message) {
        return new ValidationEntry(code, "BLOCKER", itemId, message, Map.of());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("发布候选 JSON 序列化失败", error);
        }
    }

    private Object readJson(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception error) {
            return value;
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("无法计算发布候选哈希", error);
        }
    }

    private static String assetKey(String type, String key) {
        return text(normalizedType(type)) + "|" + text(key);
    }

    private static String normalizedType(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(java.util.Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String actor() {
        String username = UserContext.getUsername();
        if (StringUtils.hasText(username)) {
            return username;
        }
        String userId = UserContext.getUserId();
        return StringUtils.hasText(userId) ? userId : "system";
    }

    private static String safeError(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (!StringUtils.hasText(message)) {
            message = error == null ? "未知错误" : error.getClass().getSimpleName();
        }
        return message.length() > 1900 ? message.substring(0, 1900) : message;
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private final class DatabaseStepJournal
            implements ReleaseCandidateExecutionEngine.StepJournal {
        private final String operator;

        private DatabaseStepJournal(String operator) {
            this.operator = operator;
        }

        @Override
        public void started(ReleaseCandidateExecutionEngine.ExecutionStep step) {
            jdbcTemplate.update("""
                    UPDATE release_candidate_step
                    SET status = 'RUNNING', operator = ?, attempt_count = attempt_count + 1,
                        started_at = CURRENT_TIMESTAMP, finished_at = NULL,
                        error_message = NULL, update_time = CURRENT_TIMESTAMP
                    WHERE id = ? AND status = 'NOT_EXECUTED'
                    """, operator, step.id());
        }

        @Override
        public void completed(
                ReleaseCandidateExecutionEngine.ExecutionStep step,
                Map<String, Object> output,
                long durationMs) {
            jdbcTemplate.update("""
                    UPDATE release_candidate_step
                    SET status = 'COMPLETED', output_json = ?, duration_ms = ?,
                        finished_at = CURRENT_TIMESTAMP, update_time = CURRENT_TIMESTAMP
                    WHERE id = ? AND status = 'RUNNING'
                    """, writeJson(output), durationMs, step.id());
        }

        @Override
        public void failed(
                ReleaseCandidateExecutionEngine.ExecutionStep step,
                RuntimeException error,
                long durationMs) {
            jdbcTemplate.update("""
                    UPDATE release_candidate_step
                    SET status = 'FAILED', error_message = ?, duration_ms = ?,
                        recovery_action = '修复失败原因后重新确认修订号和候选哈希，再从本步骤续跑',
                        finished_at = CURRENT_TIMESTAMP, update_time = CURRENT_TIMESTAMP
                    WHERE id = ? AND status = 'RUNNING'
                    """, safeError(error), durationMs, step.id());
        }
    }

    public record ReportFile(String fileName, byte[] data) {
    }

    private record SourcePackage(
            String id,
            String packageNo,
            String sourceEnvironment,
            String migrationTag,
            String checksum,
            String status) {
    }

    private record SourceItem(
            String id,
            String importPackageId,
            String assetType,
            String businessKey,
            String assetName,
            Integer sourceVersion,
            String sourceHash,
            Integer targetBeforeVersion,
            String targetBeforeHash,
            String comparisonStatus,
            String mappingStatus,
            String publishStatus,
            String snapshotJson,
            String dependenciesJson,
            String errorMessage) {
    }

    private record SourceAsset(String id, Integer sourceVersion, String contentHash) {
    }

    private record CandidateItem(
            String id,
            String candidateId,
            String sourceImportItemId,
            String sourceAssetId,
            String assetType,
            String businessKey,
            String assetName,
            Integer frozenSourceVersion,
            String frozenSourceHash,
            String frozenSnapshotHash,
            Integer frozenTargetVersion,
            String frozenTargetHash,
            String dependenciesJson,
            int sortOrder) {
    }

    private record CandidateState(
            String id,
            String candidateNo,
            String candidateName,
            String description,
            String sourceImportId,
            String sourcePackageChecksum,
            String migrationTag,
            String status,
            String preflightStatus,
            int revision,
            String candidateHash,
            String idempotencyKey,
            String failureStepId,
            String createdBy,
            Object createdAt,
            String updatedBy,
            Object updatedAt,
            String validatedBy,
            Object validatedAt,
            String publishedBy,
            Object publishedAt) {
    }

    private record DependencySpec(
            String type,
            String key,
            boolean required,
            boolean resolved,
            boolean missing,
            String source) {
    }

    private record DependencyLink(
            String id,
            String candidateId,
            String dependentItemId,
            String requiredItemId,
            String dependencyType,
            String dependencyKey,
            boolean required,
            boolean resolved,
            String sourceDescription) {
    }

    private record ValidationEntry(
            String code,
            String severity,
            String itemId,
            String message,
            Map<String, Object> detail) {
    }
}
