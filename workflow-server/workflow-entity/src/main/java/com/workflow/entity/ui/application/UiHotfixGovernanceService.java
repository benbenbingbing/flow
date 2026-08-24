package com.workflow.entity.ui.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.contracts.ui.hotfix.UiHotfixObservationPort;
import com.workflow.entity.ui.api.request.UiConfigPublishRequest;
import com.workflow.entity.ui.api.request.UiHotfixApplyRequest;
import com.workflow.entity.ui.api.request.UiHotfixCancelRequest;
import com.workflow.entity.ui.api.request.UiHotfixObservationMetricRequest;
import com.workflow.entity.ui.api.request.UiHotfixReviewRequest;
import com.workflow.entity.ui.api.response.UiConfigPublishPreviewDTO;
import com.workflow.entity.ui.api.response.UiHotfixObservationMetricDTO;
import com.workflow.entity.ui.api.response.UiHotfixRequestDTO;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigHotfixRequestMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigHotfixRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * UI HOTFIX 的申请、独立复核、发布授权与观察窗口服务。
 * 审批绑定草稿、基线和影响预览摘要，批准后任一内容漂移都会使发布失败。
 */
@Service
@RequiredArgsConstructor
public class UiHotfixGovernanceService
        implements UiHotfixObservationPort {

    private static final Set<String> METRIC_CODES = Set.of(
            "FORM_LOAD", "FORM_SUBMIT", "PROCESS_TASK");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final UiConfigHotfixRequestMapper requestMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final UiConfigurationAccessService accessService;

    /** SAFE 是否也启用双人复核，可按环境逐步收紧。 */
    @Value("${workflow.ui.hotfix.review-safe:false}")
    private boolean reviewSafe;

    /** 发布后的指标观察窗口，至少保留一分钟。 */
    @Value("${workflow.ui.hotfix.observation-minutes:60}")
    private int observationMinutes = 60;

    /** 创建绑定当前预检结果的申请；REVIEW 自动进入待复核，SAFE 自动批准。 */
    @Transactional(rollbackFor = Exception.class)
    public UiHotfixRequestDTO apply(
            UiConfigPublishPreviewDTO preview,
            UiHotfixApplyRequest request) {
        accessService.requireHotfixAccess(false);
        requireApplyRequest(preview, request);
        requireConfigAccess(preview.getConfigType(), preview.getConfigId());
        if (!preview.isCanPublish() || "BLOCKED".equals(preview.getRiskLevel())) {
            throw new BusinessConflictException(
                    "UI_HOTFIX_PREVIEW_BLOCKED",
                    "当前 HOTFIX 预检未通过，不能提交申请");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean reviewRequired = "REVIEW".equals(preview.getRiskLevel())
                || reviewSafe;
        UiConfigHotfixRequest record = new UiConfigHotfixRequest();
        record.setConfigType(preview.getConfigType());
        record.setConfigId(preview.getConfigId());
        record.setDraftHash(preview.getDraftHash());
        record.setActiveReleaseId(preview.getActiveReleaseId());
        record.setTargetHash(preview.getTargetHash());
        record.setImpactTokenHash(sha256(preview.getImpactToken()));
        record.setRiskLevel(preview.getRiskLevel());
        record.setReason(request.getReason().trim());
        record.setTicketRef(request.getTicketRef().trim());
        record.setImpactDocument(writeJson(preview));
        record.setApplicantId(currentUserId());
        record.setApplicantName(UserContext.getUsername());
        record.setWindowStart(request.getWindowStart());
        record.setWindowEnd(request.getWindowEnd());
        record.setReviewRequired(reviewRequired ? 1 : 0);
        record.setStatus(reviewRequired ? "PENDING_REVIEW" : "APPROVED");
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        try {
            requestMapper.insert(record);
        } catch (DuplicateKeyException exception) {
            throw new BusinessConflictException(
                    "UI_HOTFIX_OPEN_REQUEST_EXISTS",
                    "该配置已有待处理或观察中的 HOTFIX 申请");
        }
        return toDto(record, false);
    }

    /** REVIEW 风险必须由非申请人且拥有独立权限的用户审批。 */
    @Transactional(rollbackFor = Exception.class)
    public UiHotfixRequestDTO review(String id, UiHotfixReviewRequest review) {
        accessService.requireHotfixReviewAccess();
        UiConfigHotfixRequest record = requireRequest(id);
        requireConfigAccess(record.getConfigType(), record.getConfigId());
        if (!"PENDING_REVIEW".equals(record.getStatus())) {
            throw new BusinessConflictException(
                    "UI_HOTFIX_REVIEW_STATE_CONFLICT",
                    "HOTFIX 申请当前不处于待复核状态");
        }
        String reviewerId = currentUserId();
        if (Objects.equals(record.getApplicantId(), reviewerId)) {
            throw new BusinessConflictException(
                    "UI_HOTFIX_SELF_REVIEW_FORBIDDEN",
                    "高风险 HOTFIX 申请人不能审批自己的申请");
        }
        if (review == null || review.getApproved() == null
                || !StringUtils.hasText(review.getComment())) {
            throw new IllegalArgumentException("复核结论和意见不能为空");
        }
        String nextStatus = Boolean.TRUE.equals(review.getApproved())
                ? "APPROVED" : "REJECTED";
        LocalDateTime now = LocalDateTime.now();
        int updated = requestMapper.update(
                null,
                new LambdaUpdateWrapper<UiConfigHotfixRequest>()
                        .eq(UiConfigHotfixRequest::getId, id)
                        .eq(UiConfigHotfixRequest::getStatus, "PENDING_REVIEW")
                        .set(UiConfigHotfixRequest::getStatus, nextStatus)
                        .set(UiConfigHotfixRequest::getReviewerId, reviewerId)
                        .set(UiConfigHotfixRequest::getReviewerName, UserContext.getUsername())
                        .set(UiConfigHotfixRequest::getReviewComment, review.getComment().trim())
                        .set(UiConfigHotfixRequest::getReviewedAt, now)
                        .set(UiConfigHotfixRequest::getUpdatedAt, now));
        if (updated != 1) {
            throw new BusinessConflictException(
                    "UI_HOTFIX_REVIEW_STATE_CONFLICT",
                    "HOTFIX 申请已被其他复核人处理");
        }
        return toDto(requireRequest(id), true);
    }

    /**
     * 申请人可取消尚未发布的申请，使过期审批快照释放开放槽位。
     * 已进入发布或观察阶段的申请只能通过受控回滚处理。
     */
    @Transactional(rollbackFor = Exception.class)
    public UiHotfixRequestDTO cancel(
            String id,
            UiHotfixCancelRequest cancelRequest) {
        accessService.requireHotfixAccess(false);
        UiConfigHotfixRequest record = requireRequest(id);
        requireConfigAccess(record.getConfigType(), record.getConfigId());
        if (!Set.of("PENDING_REVIEW", "APPROVED")
                .contains(record.getStatus())) {
            throw new BusinessConflictException(
                    "UI_HOTFIX_CANCEL_STATE_CONFLICT",
                    "只有待复核或已批准且未发布的 HOTFIX 申请可以取消");
        }
        String actorId = currentUserId();
        if (!Objects.equals(record.getApplicantId(), actorId)) {
            throw new BusinessConflictException(
                    "UI_HOTFIX_CANCEL_APPLICANT_REQUIRED",
                    "只有 HOTFIX 申请人可以取消申请");
        }
        if (cancelRequest == null
                || !StringUtils.hasText(cancelRequest.getReason())) {
            throw new IllegalArgumentException("取消原因不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = requestMapper.update(
                null,
                new LambdaUpdateWrapper<UiConfigHotfixRequest>()
                        .eq(UiConfigHotfixRequest::getId, id)
                        .in(UiConfigHotfixRequest::getStatus,
                                "PENDING_REVIEW", "APPROVED")
                        .set(UiConfigHotfixRequest::getStatus, "CANCELLED")
                        .set(UiConfigHotfixRequest::getCancelledBy, actorId)
                        .set(UiConfigHotfixRequest::getCancelledAt, now)
                        .set(UiConfigHotfixRequest::getCancelReason,
                                cancelRequest.getReason().trim())
                        .set(UiConfigHotfixRequest::getUpdatedAt, now));
        if (updated != 1) {
            throw new BusinessConflictException(
                    "UI_HOTFIX_CANCEL_STATE_CONFLICT",
                    "HOTFIX 申请状态已变化，请刷新后重试");
        }
        return toDto(requireRequest(id), true);
    }

    /**
     * 发布前再次校验审批快照和窗口，并以 CAS 进入 PUBLISHING。
     * 返回已发布 releaseId 时表示同一申请的幂等重试。
     */
    @Transactional(rollbackFor = Exception.class)
    public PublishAuthorization beginPublish(
            UiConfigPublishRequest publishRequest,
            UiConfigPublishPreviewDTO preview) {
        accessService.requireHotfixAccess(false);
        String requestId = publishRequest == null
                ? null : publishRequest.getHotfixRequestId();
        if (!StringUtils.hasText(requestId)) {
            throw new BusinessConflictException(
                    "UI_HOTFIX_APPROVAL_REQUIRED",
                    "HOTFIX 发布必须携带已批准的申请ID");
        }
        UiConfigHotfixRequest record = requireRequest(requestId);
        if (StringUtils.hasText(record.getReleaseId())
                && Set.of("OBSERVING", "OBSERVED_OK", "OBSERVED_ALERT")
                        .contains(record.getStatus())) {
            return new PublishAuthorization(record.getId(), record.getReleaseId(), true);
        }
        if (!"APPROVED".equals(record.getStatus())) {
            throw new BusinessConflictException(
                    "UI_HOTFIX_APPROVAL_REQUIRED",
                    "HOTFIX 申请尚未完成独立复核");
        }
        verifyApprovedSnapshot(record, preview);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(record.getWindowStart()) || now.isAfter(record.getWindowEnd())) {
            throw new BusinessConflictException(
                    "UI_HOTFIX_OUTSIDE_RELEASE_WINDOW",
                    "当前时间不在已批准的 HOTFIX 发布窗口内");
        }
        if (Integer.valueOf(1).equals(record.getReviewRequired())
                && (!StringUtils.hasText(record.getReviewerId())
                        || Objects.equals(record.getApplicantId(), record.getReviewerId()))) {
            throw new BusinessConflictException(
                    "UI_HOTFIX_INDEPENDENT_REVIEW_REQUIRED",
                    "REVIEW 风险 HOTFIX 必须由非申请人独立复核");
        }
        int updated = requestMapper.update(
                null,
                new LambdaUpdateWrapper<UiConfigHotfixRequest>()
                        .eq(UiConfigHotfixRequest::getId, record.getId())
                        .eq(UiConfigHotfixRequest::getStatus, "APPROVED")
                        .set(UiConfigHotfixRequest::getStatus, "PUBLISHING")
                        .set(UiConfigHotfixRequest::getUpdatedAt, now));
        if (updated != 1) {
            throw new BusinessConflictException(
                    "UI_HOTFIX_PUBLISH_STATE_CONFLICT",
                    "HOTFIX 申请正在被其他请求发布");
        }
        return new PublishAuthorization(record.getId(), null, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markPublished(String requestId, String releaseId) {
        LocalDateTime now = LocalDateTime.now();
        int updated = requestMapper.update(
                null,
                new LambdaUpdateWrapper<UiConfigHotfixRequest>()
                        .eq(UiConfigHotfixRequest::getId, requestId)
                        .eq(UiConfigHotfixRequest::getStatus, "PUBLISHING")
                        .set(UiConfigHotfixRequest::getStatus, "OBSERVING")
                        .set(UiConfigHotfixRequest::getReleaseId, releaseId)
                        .set(UiConfigHotfixRequest::getPublishedAt, now)
                        .set(UiConfigHotfixRequest::getObservationStart, now)
                        .set(UiConfigHotfixRequest::getObservationEnd,
                                now.plusMinutes(Math.max(1, observationMinutes)))
                        .set(UiConfigHotfixRequest::getObservationStatus, "OBSERVING")
                        .set(UiConfigHotfixRequest::getUpdatedAt, now));
        if (updated != 1) {
            throw new BusinessConflictException(
                    "UI_HOTFIX_PUBLISH_STATE_CONFLICT",
                    "HOTFIX 发布状态无法进入观察窗口");
        }
    }

    /** 回滚要求专用权限和明确原因；无治理记录的历史 HOTFIX 仍可受控兼容回滚。 */
    public UiConfigHotfixRequest authorizeRollback(String releaseId, String reason) {
        accessService.requireHotfixRollbackAccess();
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("HOTFIX 回滚原因不能为空");
        }
        return findByReleaseId(releaseId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markRolledBack(String releaseId, String reason) {
        UiConfigHotfixRequest record = findByReleaseId(releaseId);
        if (record == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        requestMapper.update(
                null,
                new LambdaUpdateWrapper<UiConfigHotfixRequest>()
                        .eq(UiConfigHotfixRequest::getId, record.getId())
                        .set(UiConfigHotfixRequest::getStatus, "ROLLED_BACK")
                        .set(UiConfigHotfixRequest::getObservationStatus, "ALERT")
                        .set(UiConfigHotfixRequest::getRolledBackBy, currentUserId())
                        .set(UiConfigHotfixRequest::getRolledBackAt, now)
                        .set(UiConfigHotfixRequest::getRollbackReason, reason.trim())
                        .set(UiConfigHotfixRequest::getUpdatedAt, now));
    }

    /** API 适配入口；由服务端把请求对象转换为稳定观察端口参数。 */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void recordReleaseMetric(
            String releaseId,
            UiHotfixObservationMetricRequest metric) {
        if (metric == null) {
            return;
        }
        recordReleaseMetricInternal(
                releaseId,
                metric.getMetricCode(),
                Boolean.TRUE.equals(metric.getSuccessful()),
                metric.getErrorMessage());
    }

    /** 由表单加载和提交链路记录观察指标。 */
    @Override
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void recordReleaseMetric(
            String releaseId,
            String metricCode,
            boolean successful,
            String errorMessage) {
        recordReleaseMetricInternal(
                releaseId,
                metricCode,
                successful,
                errorMessage);
    }

    /** 加载失败时按配置定位处于观察期的发布。 */
    @Override
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void recordConfigMetric(
            String configType,
            String configId,
            String metricCode,
            boolean successful,
            String errorMessage) {
        if (!StringUtils.hasText(configType)
                || !StringUtils.hasText(configId)) {
            return;
        }
        List<String> releaseIds = jdbcTemplate.queryForList(
                "SELECT release_id FROM ui_config_hotfix_request "
                        + "WHERE config_type = ? AND config_id = ? "
                        + "AND status = 'OBSERVING' "
                        + "ORDER BY published_at DESC LIMIT 1",
                String.class,
                normalize(configType),
                configId);
        if (!releaseIds.isEmpty()) {
            recordReleaseMetricInternal(
                    releaseIds.get(0),
                    metricCode,
                    successful,
                    errorMessage);
        }
    }

    /** 流程任务按发布历史映射到当前生效的 HOTFIX 目标。 */
    @Override
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void recordProcessVersionMetric(
            String processVersionHistoryId,
            boolean successful,
            String errorMessage) {
        if (!StringUtils.hasText(processVersionHistoryId)) {
            return;
        }
        List<String> releaseIds = jdbcTemplate.queryForList(
                "SELECT request.release_id "
                        + "FROM ui_config_hotfix_request request "
                        + "JOIN ui_config_hotfix_target target "
                        + "ON target.hotfix_release_id = request.release_id "
                        + "WHERE target.process_version_history_id = ? "
                        + "AND target.status = 'ACTIVE' "
                        + "AND request.status = 'OBSERVING' "
                        + "ORDER BY request.published_at DESC LIMIT 1",
                String.class,
                processVersionHistoryId);
        if (!releaseIds.isEmpty()) {
            recordReleaseMetricInternal(
                    releaseIds.get(0),
                    "PROCESS_TASK",
                    successful,
                    errorMessage);
        }
    }

    private void recordReleaseMetricInternal(
            String releaseId,
            String metricCode,
            boolean successful,
            String errorMessage) {
        String code = normalize(metricCode);
        if (!StringUtils.hasText(releaseId)
                || !METRIC_CODES.contains(code)) {
            return;
        }
        UiConfigHotfixRequest record = findByReleaseId(releaseId);
        if (record == null || !"OBSERVING".equals(record.getStatus())) {
            return;
        }
        if (record.getObservationEnd() != null
                && !LocalDateTime.now().isBefore(
                        record.getObservationEnd())) {
            refreshObservation(record);
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO ui_hotfix_observation_metric "
                        + "(id, request_id, release_id, metric_code, total_count, failure_count, last_error) "
                        + "VALUES (?, ?, ?, ?, 1, ?, ?) ON DUPLICATE KEY UPDATE "
                        + "total_count = total_count + 1, failure_count = failure_count + VALUES(failure_count), "
                        + "last_error = COALESCE(VALUES(last_error), last_error), last_observed_at = NOW()",
                compactId(),
                record.getId(),
                releaseId,
                code,
                successful ? 0 : 1,
                successful ? null : abbreviate(errorMessage, 1000));
    }

    public UiHotfixRequestDTO get(String id) {
        accessService.requireHotfixAccess(false);
        UiConfigHotfixRequest record = requireRequest(id);
        requireConfigAccess(record.getConfigType(), record.getConfigId());
        refreshObservation(record);
        return toDto(requireRequest(id), true);
    }

    public List<UiHotfixRequestDTO> list(String configType, String configId) {
        accessService.requireHotfixAccess(false);
        requireConfigAccess(normalize(configType), configId);
        return requestMapper.selectList(
                        new LambdaQueryWrapper<UiConfigHotfixRequest>()
                                .eq(UiConfigHotfixRequest::getConfigType, normalize(configType))
                                .eq(UiConfigHotfixRequest::getConfigId, configId)
                                .orderByDesc(UiConfigHotfixRequest::getCreatedAt))
                .stream().map(record -> toDto(record, false)).toList();
    }

    private void refreshObservation(UiConfigHotfixRequest record) {
        if (!"OBSERVING".equals(record.getStatus())
                || record.getObservationEnd() == null
                || LocalDateTime.now().isBefore(record.getObservationEnd())) {
            return;
        }
        Long failures = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(failure_count), 0) FROM ui_hotfix_observation_metric WHERE request_id = ?",
                Long.class,
                record.getId());
        boolean alert = failures != null && failures > 0;
        requestMapper.update(
                null,
                new LambdaUpdateWrapper<UiConfigHotfixRequest>()
                        .eq(UiConfigHotfixRequest::getId, record.getId())
                        .eq(UiConfigHotfixRequest::getStatus, "OBSERVING")
                        .set(UiConfigHotfixRequest::getStatus, alert ? "OBSERVED_ALERT" : "OBSERVED_OK")
                        .set(UiConfigHotfixRequest::getObservationStatus, alert ? "ALERT" : "OK")
                        .set(UiConfigHotfixRequest::getUpdatedAt, LocalDateTime.now()));
    }

    private List<UiHotfixObservationMetricDTO> metrics(String requestId) {
        return jdbcTemplate.query(
                "SELECT metric_code, total_count, failure_count, last_error, last_observed_at "
                        + "FROM ui_hotfix_observation_metric WHERE request_id = ? ORDER BY metric_code",
                (rs, rowNum) -> {
                    UiHotfixObservationMetricDTO dto = new UiHotfixObservationMetricDTO();
                    dto.setMetricCode(rs.getString("metric_code"));
                    dto.setTotalCount(rs.getLong("total_count"));
                    dto.setFailureCount(rs.getLong("failure_count"));
                    dto.setLastError(rs.getString("last_error"));
                    Timestamp timestamp = rs.getTimestamp("last_observed_at");
                    dto.setLastObservedAt(timestamp == null ? null : timestamp.toLocalDateTime());
                    return dto;
                },
                requestId);
    }

    private void requireApplyRequest(
            UiConfigPublishPreviewDTO preview,
            UiHotfixApplyRequest request) {
        if (preview == null || !"HOTFIX".equals(preview.getReleaseMode())) {
            throw new IllegalArgumentException("必须基于 HOTFIX 预检创建申请");
        }
        if (request == null || !StringUtils.hasText(request.getReason())
                || !StringUtils.hasText(request.getTicketRef())) {
            throw new IllegalArgumentException("HOTFIX 变更原因和关联工单不能为空");
        }
        if (request.getWindowStart() == null || request.getWindowEnd() == null
                || !request.getWindowStart().isBefore(request.getWindowEnd())
                || request.getWindowEnd().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("HOTFIX 发布时间窗口无效");
        }
        if (!Objects.equals(normalize(request.getConfigType()), preview.getConfigType())
                || !Objects.equals(request.getConfigId(), preview.getConfigId())
                || !Objects.equals(request.getExpectedDraftHash(), preview.getDraftHash())
                || !Objects.equals(request.getExpectedActiveReleaseId(), preview.getActiveReleaseId())
                || !Objects.equals(request.getImpactToken(), preview.getImpactToken())) {
            throw new BusinessConflictException(
                    "UI_HOTFIX_PREVIEW_STALE",
                    "HOTFIX 申请内容与最新预检不一致，请重新预检");
        }
    }

    private void verifyApprovedSnapshot(
            UiConfigHotfixRequest record,
            UiConfigPublishPreviewDTO preview) {
        if (!Objects.equals(record.getConfigType(), preview.getConfigType())
                || !Objects.equals(record.getConfigId(), preview.getConfigId())
                || !Objects.equals(record.getDraftHash(), preview.getDraftHash())
                || !Objects.equals(record.getActiveReleaseId(), preview.getActiveReleaseId())
                || !Objects.equals(record.getTargetHash(), preview.getTargetHash())
                || !Objects.equals(record.getRiskLevel(), preview.getRiskLevel())
                || !Objects.equals(record.getImpactTokenHash(), sha256(preview.getImpactToken()))) {
            throw new BusinessConflictException(
                    "UI_HOTFIX_APPROVAL_STALE",
                    "草稿、基线或影响范围已变化，原 HOTFIX 审批已失效");
        }
    }

    private UiConfigHotfixRequest requireRequest(String id) {
        UiConfigHotfixRequest record = requestMapper.selectById(id);
        if (record == null) {
            throw new IllegalArgumentException("HOTFIX 申请不存在: " + id);
        }
        return record;
    }

    private UiConfigHotfixRequest findByReleaseId(String releaseId) {
        return requestMapper.selectOne(
                new LambdaQueryWrapper<UiConfigHotfixRequest>()
                        .eq(UiConfigHotfixRequest::getReleaseId, releaseId)
                        .last("LIMIT 1"));
    }

    private void requireConfigAccess(String configType, String configId) {
        if ("FORM".equals(normalize(configType))) {
            accessService.requireFormAccess(configId);
            return;
        }
        if ("LIST".equals(normalize(configType))) {
            accessService.requireListAccess(configId);
            return;
        }
        throw new IllegalArgumentException("配置类型只能是 FORM 或 LIST");
    }

    private UiHotfixRequestDTO toDto(UiConfigHotfixRequest record, boolean includeMetrics) {
        UiHotfixRequestDTO dto = new UiHotfixRequestDTO();
        // 使用目标 DTO 白名单复制，避免 impactTokenHash 等内部治理字段意外出站。
        BeanUtils.copyProperties(record, dto);
        try {
            dto.setImpact(objectMapper.readValue(record.getImpactDocument(), MAP_TYPE));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("HOTFIX 影响快照损坏", exception);
        }
        dto.setReviewRequired(Integer.valueOf(1).equals(record.getReviewRequired()));
        if (includeMetrics) {
            dto.setMetrics(metrics(record.getId()));
        }
        return dto;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("HOTFIX 影响快照序列化失败", exception);
        }
    }

    private static String currentUserId() {
        if (!StringUtils.hasText(UserContext.getUserId())) {
            throw new IllegalStateException("HOTFIX 操作缺少登录用户");
        }
        return UserContext.getUserId();
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static String sha256(String value) {
        if (value == null) {
            return sha256Bytes(new byte[0]);
        }
        return sha256Bytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Bytes(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private static String compactId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public record PublishAuthorization(
            String requestId,
            String existingReleaseId,
            boolean idempotent) {
    }
}
