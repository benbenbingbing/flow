package com.workflow.admin.audit.application;

import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.AuditSourcePointer;
import com.workflow.contracts.audit.OperationContext;
import com.workflow.contracts.audit.OperationContextHolder;
import com.workflow.admin.audit.domain.AuditLogPayload;
import com.workflow.admin.audit.infrastructure.AuditDiffCalculator;
import com.workflow.admin.audit.infrastructure.AuditPayloadSanitizer;
import com.workflow.admin.audit.infrastructure.AuditRequestMetadataProvider;
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
        String eventId = defaultValue(event.eventId(), newId());
        OperationContext operationContext =
                OperationContextHolder.current().orElse(null);
        AuditSourcePointer source = sourcePointer(
                event, operationContext);
        AuditPayloadSanitizer.SanitizedPayload before = sanitizer.sanitize(event.beforeData());
        AuditPayloadSanitizer.SanitizedPayload after = sanitizer.sanitize(event.afterData());
        Object changedFields = event.changedFields() != null
                ? event.changedFields()
                : diffCalculator.calculate(event.beforeData(), event.afterData());
        AuditPayloadSanitizer.SanitizedPayload changed = sanitizer.sanitize(changedFields);
        return new AuditLogPayload(
                eventId,
                truncate(defaultValue(
                        event.operationId(),
                        operationContext == null
                                ? eventId
                                : operationContext.operationId()), 128),
                truncate(defaultValue(
                        event.traceId(),
                        operationContext == null
                                ? metadata.traceId()
                                : defaultValue(
                                        operationContext.traceId(),
                                        metadata.traceId())), 64),
                truncate(defaultValue(
                        event.parentOperationId(),
                        operationContext == null
                                ? null
                                : operationContext.parentOperationId()), 128),
                truncate(source == null ? null : source.sourceSystem(), 32),
                truncate(source == null ? null : source.sourceType(), 64),
                truncate(source == null ? null : source.sourceId(), 128),
                truncate(source == null ? null : source.sourceEventId(), 128),
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

    /**
     * 显式来源优先，其次继承调用链来源；最后只使用目标稳定标识构造指针，
     * 绝不把请求参数或业务载荷复制进来源字段。
     */
    private AuditSourcePointer sourcePointer(
            SystemAuditEvent event,
            OperationContext operationContext) {
        if (event.sourcePointer() != null) {
            return event.sourcePointer();
        }
        if (operationContext != null
                && operationContext.sourcePointer() != null) {
            return operationContext.sourcePointer();
        }
        if (!StringUtils.hasText(event.targetType())
                && !StringUtils.hasText(event.targetId())) {
            return null;
        }
        return new AuditSourcePointer(
                event.module().name(),
                event.targetType(),
                event.targetId(),
                null);
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
