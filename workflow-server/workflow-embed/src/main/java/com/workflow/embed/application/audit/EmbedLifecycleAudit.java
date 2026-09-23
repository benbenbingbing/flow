package com.workflow.embed.application.audit;

import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditResult;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.model.SystemAuditEvent;
import com.workflow.contracts.audit.port.SystemAuditPort;
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

    /**
     * 初始化嵌入式生命周期审计，保存构造参数供后续方法使用。
     *
     * @param auditPort 审计端口依赖，保存到当前对象供后续业务方法调用
     * @param metrics 指标集合依赖，保存到当前对象供后续业务方法调用
     * @param clock 时钟依赖，保存到当前对象供后续业务方法调用
     */
    public EmbedLifecycleAudit(
            SystemAuditPort auditPort,
            EmbedLifecycleMetrics metrics,
            @Qualifier("embedClock") Clock clock) {
        this.auditPort = auditPort;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * 处理启动记录已签发，并将结果传给后续步骤。
     *
     * @param launchId 启动记录ID，后续用于处理启动记录已签发时定位或关联目标
     * @param correlation 关联，作为 {@code recordSuccess} 的输入影响后续处理
     */
    public void launchIssued(
            String launchId,
            EmbedAuditCorrelation correlation) {
        recordSuccess(Surface.LAUNCH, AuditAction.CREATE, "签发 Embed Launch",
                "EMBED_LAUNCH", launchId, null, null, Reason.NONE, correlation);
    }

    /**
     * 处理启动记录已拒绝，并将结果传给后续步骤。
     *
     * @param errorCode 错误编码，后续用于处理启动记录已拒绝时定位或关联目标
     * @param correlation 关联，供本方法处理启动记录已拒绝时使用
     */
    public void launchRejected(
            EmbedErrorCode errorCode,
            EmbedAuditCorrelation correlation) {
        recordFailure(Surface.LAUNCH, AuditAction.CREATE, "拒绝 Embed Launch 签发",
                errorCode, Reason.from(errorCode), correlation);
    }

    /**
     * 处理交换{@code succeeded}，并将结果传给后续步骤。
     *
     * @param sessionId 会话ID，后续用于处理交换{@code succeeded}时定位或关联目标
     * @param correlation 关联，作为 {@code recordSuccess} 的输入影响后续处理
     */
    public void exchangeSucceeded(
            String sessionId,
            EmbedAuditCorrelation correlation) {
        recordSuccess(Surface.EXCHANGE, AuditAction.LOGIN, "兑换 Embed Session",
                "EMBED_SESSION", sessionId, null, null, Reason.NONE, correlation);
    }

    /**
     * 处理交换已拒绝，并将结果传给后续步骤。
     *
     * @param errorCode 错误编码，后续用于处理交换已拒绝时定位或关联目标
     * @param correlation 关联，供本方法处理交换已拒绝时使用
     */
    public void exchangeRejected(
            EmbedErrorCode errorCode,
            EmbedAuditCorrelation correlation) {
        recordFailure(Surface.EXCHANGE, AuditAction.LOGIN, "拒绝 Embed Session 兑换",
                errorCode, Reason.from(errorCode), correlation);
    }

    /**
     * 处理认证已拒绝，并将结果传给后续步骤。
     *
     * @param errorCode 错误编码，后续用于处理认证已拒绝时定位或关联目标
     * @param correlation 关联，供本方法处理认证已拒绝时使用
     */
    public void authenticationRejected(
            EmbedErrorCode errorCode,
            EmbedAuditCorrelation correlation) {
        recordFailure(Surface.AUTH, AuditAction.LOGIN, "拒绝 Embed Session 认证",
                errorCode, Reason.from(errorCode), correlation);
    }

    /**
     * 只对真实状态流转写 required 成功审计；幂等重试只计低基数指标。
     *
     * @param result 结果，作为 {@code equals} 的输入影响后续处理
     * @param surface 界面，作为 {@code metrics.record} 的输入影响后续处理
     * @param operator 操作人，供本方法处理会话{@code terminated}时使用
     * @param correlation 关联，供本方法处理会话{@code terminated}时使用
     */
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

    /**
     * 处理启动记录已撤销，并将结果传给后续步骤。
     *
     * @param launchId 启动记录ID，后续用于处理启动记录已撤销时定位或关联目标
     * @param operator 操作人，作为 {@code recordSuccess} 的输入影响后续处理
     * @param correlation 关联，供本方法处理启动记录已撤销时使用
     */
    public void launchRevoked(
            String launchId,
            Operator operator,
            EmbedAuditCorrelation correlation) {
        recordSuccess(Surface.ADMIN_REVOKE, AuditAction.TERMINATE,
                "撤销 Embed Launch", "EMBED_LAUNCH", launchId,
                operator == null ? null : operator.id(),
                operator == null ? null : operator.name(), Reason.REVOKED, correlation);
    }

    /**
     * 记录成功；供后续追溯或审计使用。
     *
     * @param surface 界面，作为 {@code auditPort.record} 的输入影响后续处理
     * @param action 动作，写入活动历史供后续审计或展示
     * @param operation 操作标识，决定后续成功采用的处理分支
     * @param targetType 目标类型标识，决定后续成功采用的处理分支
     * @param targetId 目标ID，后续用于记录成功时定位或关联目标
     * @param operatorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param operatorName 用户名称，后续用于身份匹配或操作展示
     * @param reason 原因，作为 {@code metrics.record} 的输入影响后续处理
     * @param correlation 关联，供本方法记录成功时使用
     */
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

    /**
     * 记录失败；供后续追溯或审计使用。
     *
     * @param surface 界面，作为 {@code metrics.record} 的输入影响后续处理
     * @param action 动作，写入活动历史供后续审计或展示
     * @param operation 操作标识，决定后续失败采用的处理分支
     * @param errorCode 错误编码，后续用于记录失败时定位或关联目标
     * @param reason 原因，作为 {@code metrics.record} 的输入影响后续处理
     * @param correlation 关联，供本方法记录失败时使用
     */
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

    /**
     * 处理事件，并将结果传给后续步骤。
     *
     * @param action 动作标识，决定后续事件采用的处理分支
     * @param operation 操作标识，决定后续事件采用的处理分支
     * @param result 结果，供本方法处理事件时使用
     * @param required 必填，供本方法处理事件时使用
     * @param targetType 目标类型标识，决定后续事件采用的处理分支
     * @param targetId 目标ID，后续用于处理事件时定位或关联目标
     * @param operatorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param operatorName 用户名称，后续用于身份匹配或操作展示
     * @param surface 界面，作为 {@code attributes.put} 的输入影响后续处理
     * @param outcome 结果，作为 {@code attributes.put} 的输入影响后续处理
     * @param reason 原因，作为 {@code attributes.put} 的输入影响后续处理
     * @param errorCode 错误编码，后续用于处理事件时定位或关联目标
     * @param correlation 关联，供本方法处理事件时使用
     * @return 处理后的事件结果，供调用方继续处理
     */
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

    /**
     * 生成操作名称文本，供后续匹配或展示。
     *
     * @param status 状态标识，决定后续操作名称采用的处理分支
     * @return 处理后的操作名称文本，供调用方比较或展示
     */
    private static String operationName(String status) {
        return switch (status) {
            case "LOGGED_OUT" -> "登出 Embed Session";
            case "EXPIRED" -> "过期 Embed Session";
            case "REVOKED" -> "撤销 Embed Session";
            default -> "终止 Embed Session";
        };
    }

    /**
     * 封装操作人的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param name 展示名称，供界面或日志识别
     */
    public record Operator(String id, String name) {
    }
}
