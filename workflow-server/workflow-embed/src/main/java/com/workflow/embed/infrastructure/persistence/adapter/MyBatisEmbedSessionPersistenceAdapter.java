package com.workflow.embed.infrastructure.persistence.adapter;

import com.workflow.embed.application.port.EmbedSessionPersistencePort;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedSessionSecuritySnapshot;
import com.workflow.embed.domain.EmbedSessionTermination;
import com.workflow.embed.domain.EmbedSessionTerminationResult;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedSessionExchangeMapper;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedSessionPersistenceMapper;
import com.workflow.embed.infrastructure.persistence.record.EmbedSessionCounterRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedSessionSecurityRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedSessionTerminationRow;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** MyBatis session adapter with bounded writes and exactly-once slot release. */
@Repository
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class MyBatisEmbedSessionPersistenceAdapter implements EmbedSessionPersistencePort {

    private final EmbedSessionPersistenceMapper mapper;
    private final EmbedSessionExchangeMapper exchangeMapper;

    public MyBatisEmbedSessionPersistenceAdapter(
            EmbedSessionPersistenceMapper mapper,
            EmbedSessionExchangeMapper exchangeMapper) {
        this.mapper = mapper;
        this.exchangeMapper = exchangeMapper;
    }

    @Override
    public Optional<EmbedSessionSecuritySnapshot> findByTokenDigest(String tokenDigest) {
        try {
            return Optional.ofNullable(mapper.findByTokenDigest(tokenDigest)).map(this::map);
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    @Override
    public boolean touchLastSeen(String sessionId, Instant expectedLastSeen, Instant now) {
        try {
            return mapper.touchLastSeen(sessionId, local(expectedLastSeen), local(now)) == 1;
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Optional<EmbedSessionSecuritySnapshot> heartbeat(
            String sessionId,
            Instant now,
            Instant requestedIdleExpiry) {
        try {
            if (mapper.heartbeat(sessionId, local(now), local(requestedIdleExpiry)) != 1) {
                return Optional.empty();
            }
            return Optional.ofNullable(mapper.findById(sessionId)).map(this::map);
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    @Override
    public List<String> findExpiredTokenDigests(Instant now, int limit) {
        try {
            return List.copyOf(mapper.findExpiredTokenDigests(local(now), limit));
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    /**
     * Locks Counter then Session, changes an ACTIVE row only when its slot is unreleased, and
     * decrements the counter only after that guarded update succeeds. This makes all terminators
     * (logout, expiry, revocation) idempotent under races.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmbedSessionTermination terminate(
            String tokenDigest,
            String requestedTerminalStatus,
            String reason,
            Instant now) {
        return terminateDetailed(tokenDigest, requestedTerminalStatus, reason, now).outcome();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmbedSessionTerminationResult terminateDetailed(
            String tokenDigest,
            String requestedTerminalStatus,
            String reason,
            Instant now) {
        try {
            return terminateCandidate(
                    mapper.findTerminationCandidate(tokenDigest),
                    requestedTerminalStatus, reason, now);
        } catch (EmbedException expected) {
            throw expected;
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmbedSessionTerminationResult terminateById(
            String sessionId,
            String requestedTerminalStatus,
            String reason,
            Instant now) {
        try {
            return terminateCandidate(
                    mapper.findTerminationCandidateById(sessionId),
                    requestedTerminalStatus, reason, now);
        } catch (EmbedException expected) {
            throw expected;
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    private EmbedSessionTerminationResult terminateCandidate(
            EmbedSessionTerminationRow candidate,
            String requestedTerminalStatus,
            String reason,
            Instant now) {
        try {
            if (candidate == null) {
                return EmbedSessionTerminationResult.invalid();
            }
            if ("LOGGED_OUT".equals(candidate.status())) {
                return result(candidate, EmbedSessionTermination.ALREADY_LOGGED_OUT,
                        false, "LOGGED_OUT");
            }
            if ("EXPIRED".equals(candidate.status())) {
                return result(candidate, EmbedSessionTermination.EXPIRED, false, "EXPIRED");
            }
            if ("REVOKED".equals(candidate.status())) {
                return result(candidate, EmbedSessionTermination.REVOKED, false, "REVOKED");
            }

            // The counter row is created during Exchange. Missing rows fail closed instead of
            // silently terminating without maintaining the quota invariant.
            EmbedSessionCounterRow counter = exchangeMapper.lockCounter(
                    candidate.grantId(), candidate.flowUserId());
            if (counter == null) {
                throw unavailable(null);
            }
            EmbedSessionTerminationRow locked = mapper.lockSessionForTermination(candidate.id());
            if (locked == null) {
                return EmbedSessionTerminationResult.invalid();
            }
            if (!"ACTIVE".equals(locked.status()) || locked.slotReleased()) {
                return result(locked, terminalOutcome(locked.status()), false, locked.status());
            }
            String actualStatus = requestedTerminalStatus;
            if (!locked.absoluteExpiresAt().isAfter(local(now))
                    || !locked.idleExpiresAt().isAfter(local(now))) {
                actualStatus = "EXPIRED";
            }
            if (!java.util.Set.of("LOGGED_OUT", "EXPIRED", "REVOKED").contains(actualStatus)) {
                throw new IllegalArgumentException("Unsupported Embed terminal status");
            }
            String safeReason = "REVOKED".equals(actualStatus)
                    ? sanitizeReason(reason)
                    : null;
            if (mapper.terminateActive(
                    locked.id(), actualStatus, safeReason, local(now)) == 1) {
                if (mapper.decrementCounter(
                        locked.grantId(), locked.flowUserId(), local(now)) != 1) {
                    throw unavailable(null);
                }
                // Logout 到达时如果服务端时钟已判定超时，仍先原子释放 slot，但必须向调用方
                // 返回 EXPIRED，不能把逻辑上已失效的 Token 伪装成正常 Logout 成功。
                EmbedSessionTermination outcome = actualStatus.equals(requestedTerminalStatus)
                        ? EmbedSessionTermination.TERMINATED
                        : terminalOutcome(actualStatus);
                return result(locked, outcome, true, actualStatus);
            }
            EmbedSessionTerminationRow raced = mapper.lockSessionForTermination(locked.id());
            return raced == null
                    ? EmbedSessionTerminationResult.invalid()
                    : result(raced, terminalOutcome(raced.status()), false, raced.status());
        } catch (EmbedException expected) {
            throw expected;
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    private static EmbedSessionTerminationResult result(
            EmbedSessionTerminationRow row,
            EmbedSessionTermination outcome,
            boolean transitioned,
            String status) {
        return new EmbedSessionTerminationResult(
                outcome, transitioned, row.id(), row.applicationId(), row.viewId(),
                row.flowUserId(), status);
    }

    private EmbedSessionSecuritySnapshot map(EmbedSessionSecurityRow row) {
        return new EmbedSessionSecuritySnapshot(
                row.id(), row.sessionTokenDigest(), row.applicationId(), row.grantId(),
                row.viewId(), row.viewReleaseId(), row.identityProviderId(), row.flowUserId(),
                row.flowUsername(), row.identityBindingId(), row.parentOrigin(), row.channelId(),
                row.entryMode(), row.recordId(), row.contextCiphertext(),
                row.contextCipherKeyVersion(), row.capabilitySnapshotJson(),
                row.applicationVersion(), row.grantSecurityVersion(), row.viewSecurityVersion(),
                row.providerSecurityVersion(), row.bindingVersion(), row.sessionStatus(),
                row.slotReleased(), instant(row.lastSeenAt()), instant(row.idleExpiresAt()),
                instant(row.absoluteExpiresAt()), row.applicationStatus(),
                instant(row.applicationExpiresAt()), row.currentApplicationVersion(),
                row.grantStatus(), instant(row.grantExpiresAt()),
                row.currentGrantSecurityVersion(), row.viewStatus(),
                row.currentViewSecurityVersion(), row.providerStatus(),
                row.currentProviderSecurityVersion(), row.bindingStatus(),
                instant(row.bindingEffectiveAt()), instant(row.bindingExpiresAt()),
                row.currentBindingVersion(), "0".equals(row.flowUserStatus()),
                row.flowUserDeleted() != 0, row.flowUserPasswordResetRequired() != 0);
    }

    private static EmbedSessionTermination terminalOutcome(String status) {
        return switch (status) {
            case "LOGGED_OUT" -> EmbedSessionTermination.ALREADY_LOGGED_OUT;
            case "EXPIRED" -> EmbedSessionTermination.EXPIRED;
            case "REVOKED" -> EmbedSessionTermination.REVOKED;
            default -> EmbedSessionTermination.INVALID;
        };
    }

    private static String sanitizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "SECURITY_VERSION_CHANGED";
        }
        String value = reason.trim().replaceAll("[^A-Za-z0-9._:-]", "_");
        return value.substring(0, Math.min(128, value.length()));
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static LocalDateTime local(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static EmbedException unavailable(Throwable error) {
        return new EmbedException(
                503,
                EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                "Embed session persistence is unavailable",
                null,
                error);
    }
}
