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
import com.workflow.entity.ui.api.request.UiHotfixObservationMetricRequest;
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
import java.util.Set;
import java.util.UUID;

/**
 * UI HOTFIX 的直接发布审计、观察窗口与回滚治理服务。
 * 发布记录绑定草稿、基线和影响预览摘要，并与实际发布在同一事务中创建。
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

    /** 发布后的指标观察窗口，至少保留一分钟。 */
    @Value("${workflow.ui.hotfix.observation-minutes:60}")
    private int observationMinutes = 60;

    /**
     * 在发布事务内创建直接发布记录。
     *
     * <p>调用方必须先锁定配置归属记录并完成技术预检。方法会关闭同配置遗留的
     * 待复核或已批准记录，但不会抢占已经进入发布阶段的记录。</p>
     *
     * @return 新建的 HOTFIX 治理记录 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public String beginDirectPublish(
            UiConfigPublishRequest publishRequest,
            UiConfigPublishPreviewDTO preview) {
        accessService.requireHotfixAccess(false);
        requireDirectPublishPreview(preview);
        requireConfigAccess(preview.getConfigType(), preview.getConfigId());
        LocalDateTime now = LocalDateTime.now();

        // 人工审核链路退役后，旧开放申请不再具有发布授权意义；关闭后重新绑定本次预检。
        requestMapper.update(
                null,
                new LambdaUpdateWrapper<>(UiConfigHotfixRequest.class)
                        .eq(UiConfigHotfixRequest::getConfigType,
                                preview.getConfigType())
                        .eq(UiConfigHotfixRequest::getConfigId,
                                preview.getConfigId())
                        .in(UiConfigHotfixRequest::getStatus,
                                "PENDING_REVIEW", "APPROVED")
                        .isNull(UiConfigHotfixRequest::getReleaseId)
                        .set(UiConfigHotfixRequest::getStatus, "CANCELLED")
                        .set(UiConfigHotfixRequest::getCancelledBy,
                                currentUserId())
                        .set(UiConfigHotfixRequest::getCancelledAt, now)
                        .set(UiConfigHotfixRequest::getCancelReason,
                                "人工审核链路已退役，由直接发布替代")
                        .set(UiConfigHotfixRequest::getUpdatedAt, now));

        UiConfigHotfixRequest record = new UiConfigHotfixRequest();
        record.setConfigType(preview.getConfigType());
        record.setConfigId(preview.getConfigId());
        record.setDraftHash(preview.getDraftHash());
        record.setActiveReleaseId(preview.getActiveReleaseId());
        record.setTargetHash(preview.getTargetHash());
        record.setImpactTokenHash(sha256(preview.getImpactToken()));
        record.setRiskLevel(preview.getRiskLevel());
        record.setReason(publishReason(publishRequest));
        record.setTicketRef("");
        record.setImpactDocument(writeJson(preview));
        record.setApplicantId(currentUserId());
        record.setApplicantName(UserContext.getUsername());
        record.setWindowStart(now);
        record.setWindowEnd(now.plusMinutes(1));
        record.setReviewRequired(0);
        record.setReviewerId(null);
        record.setReviewerName(null);
        record.setReviewComment(null);
        record.setReviewedAt(null);
        record.setStatus("PUBLISHING");
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        try {
            requestMapper.insert(record);
        } catch (DuplicateKeyException exception) {
            throw new BusinessConflictException(
                    "UI_HOTFIX_PUBLISH_STATE_CONFLICT",
                    "该配置已有 HOTFIX 正在发布");
        }
        if (!StringUtils.hasText(record.getId())) {
            throw new IllegalStateException("HOTFIX 直接发布记录ID生成失败");
        }
        return record.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void markPublished(String requestId, String releaseId) {
        LocalDateTime now = LocalDateTime.now();
        int updated = requestMapper.update(
                null,
                new LambdaUpdateWrapper<>(UiConfigHotfixRequest.class)
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
                new LambdaUpdateWrapper<>(UiConfigHotfixRequest.class)
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
                        new LambdaQueryWrapper<>(UiConfigHotfixRequest.class)
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
                new LambdaUpdateWrapper<>(UiConfigHotfixRequest.class)
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

    private void requireDirectPublishPreview(
            UiConfigPublishPreviewDTO preview) {
        if (preview == null || !"HOTFIX".equals(preview.getReleaseMode())) {
            throw new IllegalArgumentException("必须基于 HOTFIX 预检直接发布");
        }
        if (!preview.isCanPublish()
                || "BLOCKED".equals(preview.getRiskLevel())) {
            throw new BusinessConflictException(
                    "UI_HOTFIX_PREVIEW_BLOCKED",
                    "当前 HOTFIX 预检未通过，不能直接发布");
        }
    }

    private String publishReason(UiConfigPublishRequest request) {
        String reason = request != null
                && StringUtils.hasText(request.getDescription())
                ? request.getDescription().trim()
                : "直接发布热修复";
        // 兼容既有 VARCHAR(1000) 列，避免直接发布因过长说明回滚整个事务。
        return reason.substring(0, Math.min(reason.length(), 1000));
    }

    private UiConfigHotfixRequest requireRequest(String id) {
        UiConfigHotfixRequest record = requestMapper.selectById(id);
        if (record == null) {
            throw new IllegalArgumentException("HOTFIX 记录不存在: " + id);
        }
        return record;
    }

    private UiConfigHotfixRequest findByReleaseId(String releaseId) {
        return requestMapper.selectOne(
                new LambdaQueryWrapper<>(UiConfigHotfixRequest.class)
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

}
