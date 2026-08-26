package com.workflow.admin.audit.domain;

import java.time.LocalDateTime;

/**
 * 已完成脱敏和长度限制、可安全写入 Outbox 的审计载荷。
 */
public record AuditLogPayload(
        String eventId,
        String operationId,
        String traceId,
        String parentOperationId,
        String sourceSystem,
        String sourceType,
        String sourceId,
        String sourceEventId,
        String moduleCode,
        String operationCode,
        String operationName,
        String riskLevel,
        String result,
        String operatorId,
        String operatorName,
        String operatorIp,
        String userAgent,
        String requestMethod,
        String requestPath,
        String targetType,
        String targetId,
        String targetName,
        String summary,
        String beforeJson,
        String afterJson,
        String changedFieldsJson,
        boolean payloadTruncated,
        String errorCode,
        String errorMessage,
        Long durationMs,
        LocalDateTime createTime) {
}
