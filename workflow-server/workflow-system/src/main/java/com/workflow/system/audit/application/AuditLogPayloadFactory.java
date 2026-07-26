package com.workflow.system.audit.application;

import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.system.audit.domain.AuditLogPayload;
import com.workflow.system.audit.infrastructure.AuditDiffCalculator;
import com.workflow.system.audit.infrastructure.AuditPayloadSanitizer;
import com.workflow.system.audit.infrastructure.AuditRequestMetadataProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 将跨模块审计事件补全为可持久化的安全载荷。
 */
@Component
@RequiredArgsConstructor
public class AuditLogPayloadFactory {

    private final AuditPayloadSanitizer sanitizer;
    private final AuditDiffCalculator diffCalculator;
    private final AuditRequestMetadataProvider metadataProvider;

    public AuditLogPayload create(SystemAuditEvent event) {
        AuditRequestMetadataProvider.AuditRequestMetadata metadata = metadataProvider.current();
        AuditPayloadSanitizer.SanitizedPayload before = sanitizer.sanitize(event.beforeData());
        AuditPayloadSanitizer.SanitizedPayload after = sanitizer.sanitize(event.afterData());
        Object changedFields = event.changedFields() != null
                ? event.changedFields()
                : diffCalculator.calculate(event.beforeData(), event.afterData());
        AuditPayloadSanitizer.SanitizedPayload changed = sanitizer.sanitize(changedFields);
        return new AuditLogPayload(
                defaultValue(event.eventId(), newId()),
                defaultValue(event.traceId(), metadata.traceId()),
                event.module().name(),
                event.action().name(),
                truncate(event.operationName(), 128),
                event.riskLevel().name(),
                event.result().name(),
                defaultValue(event.operatorId(), metadata.operatorId()),
                truncate(defaultValue(event.operatorName(), metadata.operatorName()), 100),
                truncate(defaultValue(event.operatorIp(), metadata.operatorIp()), 64),
                truncate(defaultValue(event.userAgent(), metadata.userAgent()), 512),
                truncate(defaultValue(event.requestMethod(), metadata.requestMethod()), 16),
                truncate(defaultValue(event.requestPath(), metadata.requestPath()), 512),
                truncate(event.targetType(), 64),
                truncate(event.targetId(), 128),
                sanitizer.sanitizeText(event.targetName(), 255),
                sanitizer.sanitizeText(event.summary(), 1000),
                before.json(),
                after.json(),
                changed.json(),
                before.truncated() || after.truncated() || changed.truncated(),
                truncate(event.errorCode(), 100),
                sanitizer.sanitizeText(event.errorMessage(), 1000),
                event.durationMs(),
                event.createdAt() == null ? LocalDateTime.now() : event.createdAt());
    }

    private String defaultValue(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
