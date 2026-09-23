package com.workflow.admin.audit.application;

import com.workflow.contracts.audit.model.SystemAuditEvent;
import com.workflow.contracts.audit.model.AuditSourcePointer;
import com.workflow.contracts.audit.context.OperationContext;
import com.workflow.contracts.audit.context.OperationContextHolder;
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

    /**
     * 创建审计日志载荷工厂；结果供后续流程传递或持久化。
     *
     * @param event 事件，作为 {@code defaultValue} 的输入影响后续处理
     * @return 创建后的审计日志载荷工厂结果，供调用方继续处理
     */
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
     *
     * @param event 事件，作为 {@code AuditSourcePointer} 的输入影响后续处理
     * @param operationContext 执行上下文，向后续来源指针步骤传递身份、配置或状态
     * @return 处理后的来源指针结果，供调用方继续处理
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

    /**
     * 生成默认值文本，供后续匹配或展示。
     *
     * @param preferred {@code preferred}，供本方法处理默认值时使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的默认值文本，供调用方比较或展示
     */
    private String defaultValue(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    /**
     * 生成{@code truncate}文本，供后续匹配或展示。
     *
     * @param value 待处理{@code truncate}的原始输入，结果供调用方继续使用
     * @param maxLength 最大长度，作为 {@code value.substring} 的输入影响后续处理
     * @return 处理后的{@code truncate}文本，供调用方比较或展示
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 生成新ID文本，供后续匹配或展示。
     *
     * @return 处理后的新ID文本，供调用方比较或展示
     */
    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
