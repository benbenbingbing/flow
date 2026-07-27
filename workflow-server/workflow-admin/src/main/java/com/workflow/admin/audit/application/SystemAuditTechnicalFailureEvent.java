package com.workflow.admin.audit.application;

import java.time.LocalDateTime;

/**
 * 普通审计记录失败时发布的技术监控事件。
 */
public record SystemAuditTechnicalFailureEvent(
        String eventId,
        String operationName,
        String phase,
        String exceptionType,
        LocalDateTime occurredAt) {
}
