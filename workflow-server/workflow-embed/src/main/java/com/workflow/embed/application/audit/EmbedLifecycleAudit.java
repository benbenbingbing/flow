package com.workflow.embed.application.audit;

import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditResult;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics.Outcome;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics.Reason;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics.Surface;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedSessionTerminationResult;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Embed 生命周期 required 审计边界。
 *
 * <p>属性由本类从封闭枚举构造，调用方不能传任意 Map，因而不会把 code/token/JWT、Subject、
 * Context、Origin 或 User-Agent 写入审计载荷。</p>
 */
@Component
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class EmbedLifecycleAudit {

    private final SystemAuditPort auditPort;
    private final EmbedLifecycleMetrics metrics;
    private final Clock clock;

    public EmbedLifecycleAudit(
            SystemAuditPort auditPort,
            EmbedLifecycleMetrics metrics,
            @Qualifier("embedClock") Clock clock) {
        this.auditPort = auditPort;
        this.metrics = metrics;
        this.clock = clock;
    }

    public void launchIssued(
            String launchId,
            EmbedAuditCorrelation correlation) {
        recordSuccess(Surface.LAUNCH, AuditAction.CREATE, "签发 Embed Launch",
                "EMBED_LAUNCH", launchId, null, null, Reason.NONE, correlation);
    }

    public void launchRejected(
            EmbedErrorCode errorCode,
            EmbedAuditCorrelation correlation) {
        recordFailure(Surface.LAUNCH, AuditAction.CREATE, "拒绝 Embed Launch 签发",
                errorCode, Reason.from(errorCode), correlation);
    }

    public void exchangeSucceeded(
            String sessionId,
            EmbedAuditCorrelation correlation) {
        recordSuccess(Surface.EXCHANGE, AuditAction.LOGIN, "兑换 Embed Session",
                "EMBED_SESSION", sessionId, null, null, Reason.NONE, correlation);
    }

    public void exchangeRejected(
            EmbedErrorCode errorCode,
            EmbedAuditCorrelation correlation) {
        recordFailure(Surface.EXCHANGE, AuditAction.LOGIN, "拒绝 Embed Session 兑换",
                errorCode, Reason.from(errorCode), correlation);
    }

    public void authenticationRejected(
            EmbedErrorCode errorCode,
            EmbedAuditCorrelation correlation) {
        recordFailure(Surface.AUTH, AuditAction.LOGIN, "拒绝 Embed Session 认证",
                errorCode, Reason.from(errorCode), correlation);
    }

    /** 只对真实状态流转写 required 成功审计；幂等重试只计低基数指标。 */
    public void sessionTerminated(
            EmbedSessionTerminationResult result,
            Surface surface,
            Operator operator,
            EmbedAuditCorrelation correlation) {
        if (result == null || result.outcome() == null) {
            metrics.record(surface, Outcome.REJECTED, Reason.INTERNAL_ERROR);
            return;
        }
        if (!result.transitioned()) {
            metrics.record(surface, Outcome.IDEMPOTENT, Reason.ALREADY_TERMINAL);
            return;
        }
        AuditAction action = "LOGGED_OUT".equals(result.status())
                ? AuditAction.LOGOUT : AuditAction.TERMINATE;
        Reason reason = "EXPIRED".equals(result.status())
                ? Reason.EXPIRED : "REVOKED".equals(result.status())
                ? Reason.REVOKED : Reason.NONE;
        recordSuccess(surface, action, operationName(result.status()),
                "EMBED_SESSION", result.sessionId(),
                operator == null ? result.flowUserId() : operator.id(),
                operator == null ? null : operator.name(), reason, correlation);
    }

    public void launchRevoked(
            String launchId,
            Operator operator,
            EmbedAuditCorrelation correlation) {
        recordSuccess(Surface.ADMIN_REVOKE, AuditAction.TERMINATE,
                "撤销 Embed Launch", "EMBED_LAUNCH", launchId,
                operator == null ? null : operator.id(),
                operator == null ? null : operator.name(), Reason.REVOKED, correlation);
    }

    private void recordSuccess(
            Surface surface,
            AuditAction action,
            String operation,
            String targetType,
            String targetId,
            String operatorId,
            String operatorName,
            Reason reason,
            EmbedAuditCorrelation correlation) {
        auditPort.record(event(action, operation, AuditResult.SUCCESS, true,
                targetType, targetId, operatorId, operatorName, surface, Outcome.SUCCESS,
                reason, null, correlation));
        metrics.record(surface, Outcome.SUCCESS, reason);
    }

    private void recordFailure(
            Surface surface,
            AuditAction action,
            String operation,
            EmbedErrorCode errorCode,
            Reason reason,
            EmbedAuditCorrelation correlation) {
        metrics.record(surface, Outcome.REJECTED, reason);
        auditPort.record(event(action, operation, AuditResult.FAILURE, false,
                "EMBED_SECURITY_BOUNDARY", null, null, null, surface, Outcome.REJECTED,
                reason, errorCode == null ? "INTERNAL_ERROR" : errorCode.name(),
                correlation));
    }

    private SystemAuditEvent event(
            AuditAction action,
            String operation,
            AuditResult result,
            boolean required,
            String targetType,
            String targetId,
            String operatorId,
            String operatorName,
            Surface surface,
            Outcome outcome,
            Reason reason,
            String errorCode,
            EmbedAuditCorrelation correlation) {
        EmbedAuditCorrelation safeCorrelation = correlation == null
                ? EmbedAuditCorrelation.none() : correlation;
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("surface", surface.tag());
        attributes.put("outcome", outcome.tag());
        attributes.put("reason", reason.tag());
        // 统一审计表没有 request_id 独立列，只在封闭载荷中保留已规范化的请求 ID；
        // targetId 继续专门保存 Launch/Session 资源 ID，二者不得互相代替。
        if (safeCorrelation.requestId() != null) {
            attributes.put("requestId", safeCorrelation.requestId());
        }
        return SystemAuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .traceId(safeCorrelation.traceId())
                .module(AuditModule.SECURITY)
                .action(action)
                .operationName(operation)
                .riskLevel(AuditRiskLevel.HIGH)
                .result(result)
                .required(required)
                .operatorId(operatorId)
                .operatorName(operatorName)
                .targetType(targetType)
                .targetId(targetId)
                .summary(operation)
                .afterData(Collections.unmodifiableMap(attributes))
                .errorCode(errorCode)
                .createdAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .build();
    }

    private static String operationName(String status) {
        return switch (status) {
            case "LOGGED_OUT" -> "登出 Embed Session";
            case "EXPIRED" -> "过期 Embed Session";
            case "REVOKED" -> "撤销 Embed Session";
            default -> "终止 Embed Session";
        };
    }

    public record Operator(String id, String name) {
    }
}
