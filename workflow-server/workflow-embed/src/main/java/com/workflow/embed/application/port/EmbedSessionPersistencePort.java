package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedSessionSecuritySnapshot;
import com.workflow.embed.domain.EmbedSessionTermination;
import com.workflow.embed.domain.EmbedSessionTerminationResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Session lookup, bounded heartbeat and idempotent slot-releasing termination primitives. */
public interface EmbedSessionPersistencePort {

    Optional<EmbedSessionSecuritySnapshot> findByTokenDigest(String tokenDigest);

    boolean touchLastSeen(String sessionId, Instant expectedLastSeen, Instant now);

    Optional<EmbedSessionSecuritySnapshot> heartbeat(
            String sessionId,
            Instant now,
            Instant requestedIdleExpiry);

    /** 按索引小批量查找已到 idle/absolute 截止时间但尚未释放 slot 的 ACTIVE Session。 */
    List<String> findExpiredTokenDigests(Instant now, int limit);

    EmbedSessionTermination terminate(
            String tokenDigest,
            String terminalStatus,
            String reason,
            Instant now);

    /** 与 {@link #terminate} 相同，但返回不含凭据的审计摘要。 */
    default EmbedSessionTerminationResult terminateDetailed(
            String tokenDigest,
            String terminalStatus,
            String reason,
            Instant now) {
        EmbedSessionTermination outcome = terminate(tokenDigest, terminalStatus, reason, now);
        String actualStatus = switch (outcome) {
            case TERMINATED -> terminalStatus;
            case ALREADY_LOGGED_OUT -> "LOGGED_OUT";
            case EXPIRED -> "EXPIRED";
            case REVOKED -> "REVOKED";
            case INVALID -> null;
        };
        return new EmbedSessionTerminationResult(
                outcome, outcome == EmbedSessionTermination.TERMINATED,
                null, null, null, null, actualStatus);
    }

    /** 管理端按 Session ID 撤销；实现必须与 token 入口复用同一锁序和 exactly-once 条件。 */
    default EmbedSessionTerminationResult terminateById(
            String sessionId,
            String terminalStatus,
            String reason,
            Instant now) {
        return EmbedSessionTerminationResult.invalid();
    }
}
