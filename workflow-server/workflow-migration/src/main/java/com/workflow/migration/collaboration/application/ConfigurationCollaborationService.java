package com.workflow.migration.collaboration.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.migration.api.request.ReleaseCandidateExecuteRequest;
import com.workflow.migration.api.request.ReleaseCandidatePreflightRequest;
import com.workflow.migration.application.ReleaseCandidateService;
import com.workflow.migration.collaboration.application.ConfigurationCollaborationModels.BranchCreateRequest;
import com.workflow.migration.collaboration.application.ConfigurationCollaborationModels.BranchSaveRequest;
import com.workflow.migration.collaboration.application.ConfigurationCollaborationModels.CommentRequest;
import com.workflow.migration.collaboration.application.ConfigurationCollaborationModels.MergeRequest;
import com.workflow.migration.collaboration.application.ConfigurationCollaborationModels.ReviewDecisionRequest;
import com.workflow.migration.collaboration.application.ConfigurationCollaborationModels.ReviewRequest;
import com.workflow.migration.collaboration.application.ConfigurationCollaborationModels.ScheduleRequest;
import com.workflow.migration.collaboration.application.ConfigurationCollaborationModels.WorkspaceSaveRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 配置协作编排服务。
 *
 * <p>工作区和分支使用 revision CAS；评审绑定不可变内容哈希；内容变化立即使既有审批失效；
 * 定时发布绑定真实发布候选并在执行前重新预检 revision/hash。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigurationCollaborationService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ConfigurationCollaborationPolicy policy;
    private final ReleaseCandidateService releaseCandidateService;

    public List<Map<String, Object>> list() {
        return jdbcTemplate.query("""
                SELECT * FROM config_collaboration_workspace
                ORDER BY update_time DESC
                """, (rs, rowNum) -> workspaceMap(rs.getString("id"), false));
    }

    public Map<String, Object> get(String id) {
        Map<String, Object> result = workspaceMap(id, true);
        result.put("branches", jdbcTemplate.queryForList(
                "SELECT * FROM config_collaboration_branch WHERE workspace_id = ? ORDER BY update_time DESC", id));
        result.put("comments", jdbcTemplate.queryForList(
                "SELECT * FROM config_collaboration_comment WHERE workspace_id = ? ORDER BY create_time", id));
        result.put("reviews", jdbcTemplate.queryForList(
                "SELECT * FROM config_collaboration_review WHERE workspace_id = ? ORDER BY requested_at DESC", id));
        result.put("schedules", jdbcTemplate.queryForList(
                "SELECT * FROM config_scheduled_release WHERE workspace_id = ? ORDER BY scheduled_at DESC", id));
        return result;
    }

    /** 新建或 CAS 更新配置工作区；内容变化会使 PENDING/APPROVED 评审失效。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> save(WorkspaceSaveRequest request) {
        if (request == null || !StringUtils.hasText(request.assetType())
                || !StringUtils.hasText(request.assetId())) {
            throw new IllegalArgumentException("配置资产类型和稳定标识不能为空");
        }
        Map<String, Object> content = request.content() == null ? Map.of() : request.content();
        String hash = policy.hash(content);
        String actor = actor();
        String id = request.id();
        if (!StringUtils.hasText(id)) {
            id = compactId();
            jdbcTemplate.update("""
                    INSERT INTO config_collaboration_workspace (
                      id, asset_type, asset_id, asset_name, content_json, content_hash,
                      revision, status, owner_user_id, created_by, updated_by, create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, ?, 1, 'DRAFT', ?, ?, ?,
                              CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                    """, id, request.assetType().trim().toUpperCase(), request.assetId().trim(),
                    request.assetName(), write(content), hash, actor, actor, actor);
        } else {
            Map<String, Object> current = workspaceMap(id, false);
            requireOwner(current, actor);
            int expected = request.expectedRevision() == null
                    ? number(current.get("revision")) : request.expectedRevision();
            String previousHash = text(current.get("contentHash"));
            int updated = jdbcTemplate.update("""
                    UPDATE config_collaboration_workspace
                    SET asset_name = ?, content_json = ?, content_hash = ?,
                        revision = revision + 1, status = 'DRAFT', updated_by = ?,
                        update_time = CURRENT_TIMESTAMP(3)
                    WHERE id = ? AND revision = ?
                    """, request.assetName(), write(content), hash, actor, id, expected);
            if (updated != 1) throw new IllegalStateException("配置工作区已变化，请执行三方比较后重试");
            if (!hash.equals(previousHash)) invalidateReviews(id, hash);
        }
        return get(id);
    }

    /** 从工作区当前哈希创建独立分支。 */
    public Map<String, Object> createBranch(String workspaceId, BranchCreateRequest request) {
        Map<String, Object> workspace = workspaceMap(workspaceId, false);
        if (request == null || !StringUtils.hasText(request.branchKey())) {
            throw new IllegalArgumentException("分支标识不能为空");
        }
        String id = compactId();
        jdbcTemplate.update("""
                INSERT INTO config_collaboration_branch (
                  id, workspace_id, branch_key, branch_name, base_hash, content_json,
                  content_hash, revision, status, owner_user_id, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 1, 'OPEN', ?,
                          CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                """, id, workspaceId, request.branchKey().trim(),
                StringUtils.hasText(request.branchName()) ? request.branchName().trim() : request.branchKey().trim(),
                workspace.get("contentHash"), write(workspace.get("content")),
                workspace.get("contentHash"), actor());
        return branch(id);
    }

    /** CAS 保存本人分支。 */
    public Map<String, Object> saveBranch(String branchId, BranchSaveRequest request) {
        Map<String, Object> branch = branch(branchId);
        if (!actor().equals(text(branch.get("owner_user_id")))) {
            throw new ForbiddenException("只能修改本人配置分支");
        }
        int expected = request.expectedRevision() == null
                ? number(branch.get("revision")) : request.expectedRevision();
        Map<String, Object> content = request.content() == null ? Map.of() : request.content();
        int updated = jdbcTemplate.update("""
                UPDATE config_collaboration_branch
                SET content_json = ?, content_hash = ?, revision = revision + 1,
                    update_time = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND revision = ? AND status = 'OPEN'
                """, write(content), policy.hash(content), branchId, expected);
        if (updated != 1) throw new IllegalStateException("配置分支已变化，请刷新后重试");
        return branch(branchId);
    }

    /** 三方合并分支；双方均变化且无显式解决结果时返回结构化冲突而不覆盖。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> merge(String branchId, MergeRequest request) {
        Map<String, Object> branch = branch(branchId);
        String workspaceId = text(branch.get("workspace_id"));
        Map<String, Object> workspace = workspaceMap(workspaceId, false);
        int expected = request.expectedWorkspaceRevision() == null
                ? number(workspace.get("revision")) : request.expectedWorkspaceRevision();
        ConfigurationCollaborationPolicy.MergeDecision decision = policy.merge(
                text(branch.get("base_hash")),
                castMap(workspace.get("content")),
                read(text(branch.get("content_json"))),
                request.resolvedContent());
        if (decision.conflict()) {
            return Map.of(
                    "merged", false,
                    "reason", decision.reason(),
                    "baseHash", branch.get("base_hash"),
                    "targetHash", workspace.get("contentHash"),
                    "sourceHash", branch.get("content_hash"),
                    "target", workspace.get("content"),
                    "source", read(text(branch.get("content_json"))));
        }
        String hash = policy.hash(decision.content());
        int updated = jdbcTemplate.update("""
                UPDATE config_collaboration_workspace
                SET content_json = ?, content_hash = ?, revision = revision + 1,
                    status = 'DRAFT', updated_by = ?, update_time = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND revision = ?
                """, write(decision.content()), hash, actor(), workspaceId, expected);
        if (updated != 1) throw new IllegalStateException("目标工作区已变化，请重新执行三方合并");
        jdbcTemplate.update("""
                UPDATE config_collaboration_branch SET status = 'MERGED',
                    update_time = CURRENT_TIMESTAMP(3) WHERE id = ?
                """, branchId);
        invalidateReviews(workspaceId, hash);
        return Map.of("merged", true, "reason", decision.reason(), "workspace", get(workspaceId));
    }

    /** 评论绑定稳定节点或字段标识，配置重排后仍可保留上下文。 */
    public Map<String, Object> addComment(String workspaceId, CommentRequest request) {
        workspaceMap(workspaceId, false);
        if (request == null || !StringUtils.hasText(request.targetKey())
                || !StringUtils.hasText(request.content())) {
            throw new IllegalArgumentException("评论位置和内容不能为空");
        }
        String id = compactId();
        jdbcTemplate.update("""
                INSERT INTO config_collaboration_comment (
                  id, workspace_id, branch_id, target_key, content, status,
                  author_user_id, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, 'OPEN', ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                """, id, workspaceId, request.branchId(), request.targetKey().trim(),
                request.content().trim(), actor());
        return jdbcTemplate.queryForMap("SELECT * FROM config_collaboration_comment WHERE id = ?", id);
    }

    public Map<String, Object> requestReview(String workspaceId, ReviewRequest request) {
        Map<String, Object> workspace = workspaceMap(workspaceId, false);
        String hash = text(workspace.get("contentHash"));
        if (request != null && StringUtils.hasText(request.branchId())) {
            Map<String, Object> branch = branch(request.branchId());
            if (!workspaceId.equals(text(branch.get("workspace_id")))) {
                throw new IllegalArgumentException("分支不属于指定工作区");
            }
            hash = text(branch.get("content_hash"));
        }
        String id = compactId();
        jdbcTemplate.update("""
                INSERT INTO config_collaboration_review (
                  id, workspace_id, branch_id, requested_hash, status,
                  requester_user_id, requested_at
                ) VALUES (?, ?, ?, ?, 'PENDING', ?, CURRENT_TIMESTAMP(3))
                """, id, workspaceId, request == null ? null : request.branchId(), hash, actor());
        return jdbcTemplate.queryForMap("SELECT * FROM config_collaboration_review WHERE id = ?", id);
    }

    /** 评审绑定请求时哈希；自审或内容漂移均被服务端拒绝。 */
    public Map<String, Object> decideReview(String reviewId, ReviewDecisionRequest request) {
        Map<String, Object> review = jdbcTemplate.queryForMap(
                "SELECT * FROM config_collaboration_review WHERE id = ?", reviewId);
        String reviewer = actor();
        policy.requireIndependentReviewer(text(review.get("requester_user_id")), reviewer);
        Map<String, Object> workspace = workspaceMap(text(review.get("workspace_id")), false);
        String currentHash = text(workspace.get("contentHash"));
        if (StringUtils.hasText(text(review.get("branch_id")))) {
            currentHash = text(branch(text(review.get("branch_id"))).get("content_hash"));
        }
        if (!currentHash.equals(text(review.get("requested_hash")))) {
            jdbcTemplate.update("UPDATE config_collaboration_review SET status = 'STALE' WHERE id = ?", reviewId);
            throw new IllegalStateException("评审内容已变化，原审批自动失效");
        }
        String status = request != null && request.approved() ? "APPROVED" : "REJECTED";
        int updated = jdbcTemplate.update("""
                UPDATE config_collaboration_review
                SET status = ?, reviewer_user_id = ?, review_note = ?, reviewed_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND status = 'PENDING'
                """, status, reviewer, request == null ? null : request.note(), reviewId);
        if (updated != 1) throw new IllegalStateException("评审状态已变化");
        return jdbcTemplate.queryForMap("SELECT * FROM config_collaboration_review WHERE id = ?", reviewId);
    }

    /** 只允许使用当前哈希已审批的工作区绑定真实发布候选。 */
    public Map<String, Object> schedule(String workspaceId, ScheduleRequest request) {
        if (request == null || request.scheduledAt() == null
                || !StringUtils.hasText(request.releaseCandidateId())) {
            throw new IllegalArgumentException("评审、发布候选和计划时间不能为空");
        }
        Map<String, Object> workspace = workspaceMap(workspaceId, false);
        Map<String, Object> review = jdbcTemplate.queryForMap(
                "SELECT * FROM config_collaboration_review WHERE id = ? AND workspace_id = ?",
                request.reviewId(), workspaceId);
        if (!"APPROVED".equals(text(review.get("status")))) {
            throw new IllegalStateException("配置评审尚未通过");
        }
        String approvedHash = text(review.get("requested_hash"));
        if (!approvedHash.equals(text(workspace.get("contentHash")))) {
            throw new IllegalStateException("审批后配置已变化，必须重新评审");
        }
        Map<String, Object> candidate = releaseCandidateService.get(request.releaseCandidateId());
        int revision = number(candidate.get("revision"));
        String candidateHash = candidateHash(candidate);
        String idempotency = StringUtils.hasText(request.idempotencyKey())
                ? request.idempotencyKey().trim() : "SCHEDULE:" + compactId();
        String id = compactId();
        jdbcTemplate.update("""
                INSERT INTO config_scheduled_release (
                  id, workspace_id, review_id, release_candidate_id, approved_hash,
                  candidate_revision, candidate_hash, scheduled_at, status,
                  idempotency_key, created_by, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SCHEDULED', ?, ?,
                          CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                """, id, workspaceId, request.reviewId(), request.releaseCandidateId(), approvedHash,
                revision, candidateHash, Timestamp.valueOf(request.scheduledAt()), idempotency, actor());
        return jdbcTemplate.queryForMap("SELECT * FROM config_scheduled_release WHERE id = ?", id);
    }

    /** 每分钟领取到期任务；单项失败隔离，不阻塞其他发布。 */
    @Scheduled(fixedDelayString = "${workflow.configuration-collaboration.scheduler-delay-ms:60000}")
    public void executeDue() {
        List<String> ids = jdbcTemplate.queryForList("""
                SELECT id FROM config_scheduled_release
                WHERE status = 'SCHEDULED' AND scheduled_at <= CURRENT_TIMESTAMP(3)
                ORDER BY scheduled_at LIMIT 20
                """, String.class);
        for (String id : ids) {
            try {
                executeScheduled(id);
            } catch (RuntimeException exception) {
                log.error("定时配置发布失败: scheduleId={}, failureType={}",
                        id, exception.getClass().getSimpleName());
            }
        }
    }

    /** 执行前重新检查工作区哈希、候选 revision/hash，并重新运行候选预检。 */
    public Map<String, Object> executeScheduled(String id) {
        int claimed = jdbcTemplate.update("""
                UPDATE config_scheduled_release SET status = 'RUNNING', update_time = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND status = 'SCHEDULED'
                """, id);
        if (claimed != 1) return jdbcTemplate.queryForMap(
                "SELECT * FROM config_scheduled_release WHERE id = ?", id);
        Map<String, Object> schedule = jdbcTemplate.queryForMap(
                "SELECT * FROM config_scheduled_release WHERE id = ?", id);
        try {
            Map<String, Object> workspace = workspaceMap(text(schedule.get("workspace_id")), false);
            if (!text(schedule.get("approved_hash")).equals(text(workspace.get("contentHash")))) {
                throw new IllegalStateException("审批后配置内容发生漂移");
            }
            String candidateId = text(schedule.get("release_candidate_id"));
            Map<String, Object> candidate = releaseCandidateService.get(candidateId);
            if (number(candidate.get("revision")) != number(schedule.get("candidate_revision"))
                    || !candidateHash(candidate).equals(text(schedule.get("candidate_hash")))) {
                throw new IllegalStateException("发布候选 revision/hash 发生漂移");
            }
            ReleaseCandidatePreflightRequest preflight = new ReleaseCandidatePreflightRequest();
            preflight.setExpectedRevision(number(candidate.get("revision")));
            candidate = releaseCandidateService.preflight(candidateId, preflight);
            if (!"READY".equals(text(candidate.get("status")))) {
                throw new IllegalStateException("发布候选预检未通过");
            }
            ReleaseCandidateExecuteRequest execute = new ReleaseCandidateExecuteRequest();
            execute.setExpectedRevision(number(candidate.get("revision")));
            execute.setCandidateHash(candidateHash(candidate));
            execute.setIdempotencyKey(text(schedule.get("idempotency_key")));
            releaseCandidateService.publish(candidateId, execute);
            jdbcTemplate.update("""
                    UPDATE config_scheduled_release
                    SET status = 'PUBLISHED', executed_by = 'scheduler', executed_at = CURRENT_TIMESTAMP(3),
                        update_time = CURRENT_TIMESTAMP(3)
                    WHERE id = ?
                    """, id);
        } catch (RuntimeException exception) {
            jdbcTemplate.update("""
                    UPDATE config_scheduled_release
                    SET status = 'FAILED', failure_message = ?, update_time = CURRENT_TIMESTAMP(3)
                    WHERE id = ?
                    """, abbreviate(exception.getMessage(), 1900), id);
            throw exception;
        }
        return jdbcTemplate.queryForMap("SELECT * FROM config_scheduled_release WHERE id = ?", id);
    }

    private Map<String, Object> workspaceMap(String id, boolean required) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, asset_type, asset_id, asset_name, content_json, content_hash,
                       revision, status, owner_user_id, created_by, updated_by, create_time, update_time
                FROM config_collaboration_workspace WHERE id = ?
                """, id);
        if (rows.isEmpty()) {
            if (required) throw new IllegalArgumentException("配置工作区不存在: " + id);
            throw new IllegalArgumentException("配置工作区不存在: " + id);
        }
        Map<String, Object> source = rows.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", source.get("id"));
        result.put("assetType", source.get("asset_type"));
        result.put("assetId", source.get("asset_id"));
        result.put("assetName", source.get("asset_name"));
        result.put("content", read(text(source.get("content_json"))));
        result.put("contentHash", source.get("content_hash"));
        result.put("revision", source.get("revision"));
        result.put("status", source.get("status"));
        result.put("ownerUserId", source.get("owner_user_id"));
        result.put("createdAt", source.get("create_time"));
        result.put("updatedAt", source.get("update_time"));
        return result;
    }

    private Map<String, Object> branch(String id) {
        return jdbcTemplate.queryForMap("SELECT * FROM config_collaboration_branch WHERE id = ?", id);
    }

    private void invalidateReviews(String workspaceId, String currentHash) {
        jdbcTemplate.update("""
                UPDATE config_collaboration_review
                SET status = 'STALE'
                WHERE workspace_id = ? AND status IN ('PENDING', 'APPROVED')
                  AND requested_hash <> ?
                """, workspaceId, currentHash);
        jdbcTemplate.update("""
                UPDATE config_scheduled_release
                SET status = 'STALE', failure_message = '审批后配置内容发生变化',
                    update_time = CURRENT_TIMESTAMP(3)
                WHERE workspace_id = ? AND status = 'SCHEDULED' AND approved_hash <> ?
                """, workspaceId, currentHash);
    }

    private void requireOwner(Map<String, Object> workspace, String actor) {
        if (!actor.equals(text(workspace.get("ownerUserId")))) {
            throw new ForbiddenException("只能修改本人负责的配置工作区");
        }
    }

    private String candidateHash(Map<String, Object> candidate) {
        for (String key : List.of("candidateHash", "contentHash", "hash")) {
            String value = text(candidate.get(key));
            if (StringUtils.hasText(value)) return value;
        }
        throw new IllegalStateException("发布候选缺少冻结哈希");
    }

    private String actor() {
        String value = UserContext.getUserId();
        if (!StringUtils.hasText(value)) value = UserContext.getUsername();
        if (!StringUtils.hasText(value)) throw new ForbiddenException("用户未登录");
        return value;
    }

    private Map<String, Object> read(String json) {
        try {
            return StringUtils.hasText(json) ? objectMapper.readValue(json, MAP_TYPE) : Map.of();
        } catch (Exception exception) {
            throw new IllegalStateException("配置协作文档损坏", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            throw new IllegalStateException("配置协作文档序列化失败", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private int number(Object value) {
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
