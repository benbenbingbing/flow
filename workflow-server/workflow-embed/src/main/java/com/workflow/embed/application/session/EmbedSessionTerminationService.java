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

    public EmbedSessionTerminationService(
            EmbedSessionPersistencePort persistencePort,
            EmbedLifecycleAudit audit) {
        this.persistencePort = persistencePort;
        this.audit = audit;
    }

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
