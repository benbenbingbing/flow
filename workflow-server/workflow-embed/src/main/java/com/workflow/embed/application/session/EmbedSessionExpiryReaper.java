package com.workflow.embed.application.session;

import com.workflow.embed.application.port.EmbedSessionPersistencePort;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics.Surface;
import com.workflow.embed.application.audit.EmbedAuditCorrelation;
import com.workflow.embed.domain.EmbedSessionTermination;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 小批量回收无人继续访问的过期 Session，防止活跃会话配额永久泄漏。 */
@Service
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class EmbedSessionExpiryReaper {

    private static final int BATCH_SIZE = 200;

    private final EmbedSessionPersistencePort persistencePort;
    private final EmbedSessionTerminationService terminationService;
    private final Clock clock;

    public EmbedSessionExpiryReaper(
            EmbedSessionPersistencePort persistencePort,
            EmbedSessionTerminationService terminationService,
            @Qualifier("embedClock") Clock clock) {
        this.persistencePort = persistencePort;
        this.terminationService = terminationService;
        this.clock = clock;
    }

    /**
     * 扫描一批已过期 Session，并复用统一终止原语释放 Counter。
     *
     * <p>多个 Pod 可以同时扫描相同行；{@code slot_released=0} 条件与 Counter -> Session
     * 锁顺序保证只扣减一次，因此无需持有跨批次的大事务或分布式锁。
     *
     * @return 本批真正完成 ACTIVE -> EXPIRED 状态流转的数量
     */
    @Scheduled(fixedDelayString = "${workflow.embed.session-expiry-scan-ms:60000}")
    public int expireBatch() {
        Instant now = clock.instant();
        List<String> candidates = persistencePort.findExpiredTokenDigests(now, BATCH_SIZE);
        int expired = 0;
        for (String tokenDigest : candidates) {
            EmbedSessionTermination outcome = terminationService.terminateByTokenDigest(
                    tokenDigest, "EXPIRED", null, now, Surface.EXPIRY,
                    EmbedAuditCorrelation.none()).outcome();
            if (outcome == EmbedSessionTermination.TERMINATED) {
                expired++;
            }
        }
        return expired;
    }
}
