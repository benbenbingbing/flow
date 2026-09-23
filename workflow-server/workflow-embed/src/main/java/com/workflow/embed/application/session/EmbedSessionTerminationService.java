package com.workflow.embed.application.session;

import com.workflow.embed.application.audit.EmbedAuditCorrelation;
import com.workflow.embed.application.audit.EmbedLifecycleAudit;
import com.workflow.embed.application.audit.EmbedLifecycleAudit.Operator;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics.Surface;
import com.workflow.embed.application.port.EmbedSessionPersistencePort;
import com.workflow.embed.domain.EmbedSessionTerminationResult;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Session 统一终止用例：在同一小事务内维护 Counter/Session 状态并写 required 审计。
 *
 * <p>批量调用方必须逐条调用本服务，禁止在外层开启覆盖整批的大事务。</p>
 */
@Service
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class EmbedSessionTerminationService {

    private final EmbedSessionPersistencePort persistencePort;
    private final EmbedLifecycleAudit audit;

    /**
     * 初始化嵌入式会话终止服务，保存构造参数供后续方法使用。
     *
     * @param persistencePort 持久化端口依赖，保存到当前对象供后续业务方法调用
     * @param audit 审计依赖，保存到当前对象供后续业务方法调用
     */
    public EmbedSessionTerminationService(
            EmbedSessionPersistencePort persistencePort,
            EmbedLifecycleAudit audit) {
        this.persistencePort = persistencePort;
        this.audit = audit;
    }

    /**
     * 终止令牌摘要；后续读取或执行将使用更新后的状态。
     *
     * @param tokenDigest 令牌摘要，作为 {@code persistencePort.terminateDetailed} 的输入影响后续处理
     * @param terminalStatus 终态状态标识，决定后续令牌摘要采用的处理分支
     * @param reason 原因，作为 {@code persistencePort.terminateDetailed} 的输入影响后续处理
     * @param now 当前时间，作为 {@code persistencePort.terminateDetailed} 的输入影响后续处理
     * @param surface 界面，作为 {@code audit.sessionTerminated} 的输入影响后续处理
     * @param correlation 关联，作为 {@code audit.sessionTerminated} 的输入影响后续处理
     * @return 终止后的令牌摘要结果，供调用方继续处理
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public EmbedSessionTerminationResult terminateByTokenDigest(
            String tokenDigest,
            String terminalStatus,
            String reason,
            Instant now,
            Surface surface,
            EmbedAuditCorrelation correlation) {
        EmbedSessionTerminationResult result = persistencePort.terminateDetailed(
                tokenDigest, terminalStatus, reason, now);
        audit.sessionTerminated(result, surface, null, correlation);
        return result;
    }

    /**
     * 终止ID；后续读取或执行将使用更新后的状态。
     *
     * @param sessionId 会话ID，后续用于终止ID时定位或关联目标
     * @param terminalStatus 终态状态标识，决定后续ID采用的处理分支
     * @param reason 原因，供本方法终止ID时使用
     * @param now 当前时间，供本方法终止ID时使用
     * @param surface 界面，作为 {@code audit.sessionTerminated} 的输入影响后续处理
     * @param operator 操作人，作为 {@code audit.sessionTerminated} 的输入影响后续处理
     * @param correlation 关联，作为 {@code audit.sessionTerminated} 的输入影响后续处理
     * @return 终止后的ID结果，供调用方继续处理
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public EmbedSessionTerminationResult terminateById(
            String sessionId,
            String terminalStatus,
            String reason,
            Instant now,
            Surface surface,
            Operator operator,
            EmbedAuditCorrelation correlation) {
        EmbedSessionTerminationResult result = persistencePort.terminateById(
                sessionId, terminalStatus, reason, now);
        audit.sessionTerminated(result, surface, operator, correlation);
        return result;
    }
}
