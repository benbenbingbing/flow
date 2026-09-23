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

    /**
     * 初始化MyBatis嵌入式会话持久化适配器，保存构造参数供后续方法使用。
     *
     * @param mapper 映射器依赖，保存到当前对象供后续业务方法调用
     * @param exchangeMapper 交换映射器依赖，保存到当前对象供后续业务方法调用
     */
    public MyBatisEmbedSessionPersistenceAdapter(
            EmbedSessionPersistenceMapper mapper,
            EmbedSessionExchangeMapper exchangeMapper) {
        this.mapper = mapper;
        this.exchangeMapper = exchangeMapper;
    }

    /**
     * 按令牌摘要查询嵌入式会话安全快照；结果供后续展示或处理。
     *
     * @param tokenDigest 令牌摘要，作为 {@code Optional.ofNullable} 的输入影响后续处理
     * @return 匹配的令牌摘要；未找到时为空
     */
    @Override
    public Optional<EmbedSessionSecuritySnapshot> findByTokenDigest(String tokenDigest) {
        try {
            return Optional.ofNullable(mapper.findByTokenDigest(tokenDigest)).map(this::map);
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    /**
     * 判断更新访问时间最后已见条件是否成立，供调用方选择后续分支。
     *
     * @param sessionId 会话ID，后续用于处理更新访问时间最后已见时定位或关联目标
     * @param expectedLastSeen 预期最后已见，供本方法处理更新访问时间最后已见时使用
     * @param now 当前时间，供本方法处理更新访问时间最后已见时使用
     * @return 更新访问时间最后已见条件成立时为 true，否则为 false
     */
    @Override
    public boolean touchLastSeen(String sessionId, Instant expectedLastSeen, Instant now) {
        try {
            return mapper.touchLastSeen(sessionId, local(expectedLastSeen), local(now)) == 1;
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    /**
     * 处理心跳，并将结果传给后续步骤。
     *
     * @param sessionId 会话ID，后续用于处理心跳时定位或关联目标
     * @param now 当前时间，供本方法处理心跳时使用
     * @param requestedIdleExpiry 请求空闲{@code expiry}，供本方法处理心跳时使用
     * @return 匹配的心跳；未找到时为空
     */
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

    /**
     * 查询过期令牌摘要集合；查询结果供调用方展示或继续处理。
     *
     * @param now 当前时间，作为 {@code List.copyOf} 的输入影响后续处理
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return MyBatis嵌入式会话持久化集合，供调用方遍历或展示
     */
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
     *
     * @param tokenDigest 令牌摘要，作为 {@code terminateDetailed} 的输入影响后续处理
     * @param requestedTerminalStatus 请求终态状态标识，决定后续MyBatis嵌入式会话持久化采用的处理分支
     * @param reason 原因，作为 {@code terminateDetailed} 的输入影响后续处理
     * @param now 当前时间，作为 {@code terminateDetailed} 的输入影响后续处理
     * @return 终止后的MyBatis嵌入式会话持久化结果，供调用方继续处理
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

    /**
     * 终止{@code detailed}；后续读取或执行将使用更新后的状态。
     *
     * @param tokenDigest 令牌摘要，作为 {@code terminateCandidate} 的输入影响后续处理
     * @param requestedTerminalStatus 请求终态状态标识，决定后续{@code detailed}采用的处理分支
     * @param reason 原因，供本方法终止{@code detailed}时使用
     * @param now 当前时间，供本方法终止{@code detailed}时使用
     * @return 终止后的{@code detailed}结果，供调用方继续处理
     */
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

    /**
     * 终止ID；后续读取或执行将使用更新后的状态。
     *
     * @param sessionId 会话ID，后续用于终止ID时定位或关联目标
     * @param requestedTerminalStatus 请求终态状态标识，决定后续ID采用的处理分支
     * @param reason 原因，供本方法终止ID时使用
     * @param now 当前时间，供本方法终止ID时使用
     * @return 终止后的ID结果，供调用方继续处理
     */
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

    /**
     * 终止候选人；后续读取或执行将使用更新后的状态。
     *
     * @param candidate 候选人，后续用于判断有效期或展示该事件的发生时间
     * @param requestedTerminalStatus 请求终态状态标识，决定后续候选人采用的处理分支
     * @param reason 原因，供本方法终止候选人时使用
     * @param now 当前时间，供本方法终止候选人时使用
     * @return 终止后的候选人结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 处理结果，并将结果传给后续步骤。
     *
     * @param row 行，作为 {@code EmbedSessionTerminationResult} 的输入影响后续处理
     * @param outcome 结果，作为 {@code EmbedSessionTerminationResult} 的输入影响后续处理
     * @param transitioned {@code transitioned}，作为 {@code EmbedSessionTerminationResult} 的输入影响后续处理
     * @param status 状态标识，决定后续结果采用的处理分支
     * @return 处理后的结果，供调用方继续处理
     */
    private static EmbedSessionTerminationResult result(
            EmbedSessionTerminationRow row,
            EmbedSessionTermination outcome,
            boolean transitioned,
            String status) {
        return new EmbedSessionTerminationResult(
                outcome, transitioned, row.id(), row.applicationId(), row.viewId(),
                row.flowUserId(), status);
    }

    /**
     * 处理映射，并将结果传给后续步骤。
     *
     * @param row 行，作为 {@code EmbedSessionSecuritySnapshot} 的输入影响后续处理
     * @return 处理后的映射结果，供调用方继续处理
     */
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
                row.currentBindingApplicationId(),
                row.currentBindingIdentityProviderId(),
                row.currentBindingFlowUserId(),
                instant(row.bindingEffectiveAt()), instant(row.bindingExpiresAt()),
                row.currentBindingVersion(), "0".equals(row.flowUserStatus()),
                row.flowUserDeleted() != 0, row.flowUserPasswordResetRequired() != 0);
    }

    /**
     * 处理终态结果，并将结果传给后续步骤。
     *
     * @param status 状态标识，决定后续终态结果采用的处理分支
     * @return 处理后的终态结果，供调用方继续处理
     */
    private static EmbedSessionTermination terminalOutcome(String status) {
        return switch (status) {
            case "LOGGED_OUT" -> EmbedSessionTermination.ALREADY_LOGGED_OUT;
            case "EXPIRED" -> EmbedSessionTermination.EXPIRED;
            case "REVOKED" -> EmbedSessionTermination.REVOKED;
            default -> EmbedSessionTermination.INVALID;
        };
    }

    /**
     * 清洗原因；结果供调用方的后续步骤使用。
     *
     * @param reason 原因，供本方法清洗原因时使用
     * @return 清洗后的原因文本，供调用方比较或展示
     */
    private static String sanitizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "SECURITY_VERSION_CHANGED";
        }
        String value = reason.trim().replaceAll("[^A-Za-z0-9._:-]", "_");
        return value.substring(0, Math.min(128, value.length()));
    }

    /**
     * 处理绝对时间，并将结果传给后续步骤。
     *
     * @param value 待处理绝对时间的原始输入，结果供调用方继续使用
     * @return 处理后的绝对时间结果，供调用方继续处理
     */
    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    /**
     * 处理本地，并将结果传给后续步骤。
     *
     * @param value 待处理本地的原始输入，结果供调用方继续使用
     * @return 处理后的本地结果，供调用方继续处理
     */
    private static LocalDateTime local(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    /**
     * 构造服务不可用异常，供调用方区分失败原因。
     *
     * @param error 错误，供本方法处理不可用时使用
     * @return 处理后的不可用结果，供调用方继续处理
     */
    private static EmbedException unavailable(Throwable error) {
        return new EmbedException(
                503,
                EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                "Embed session persistence is unavailable",
                null,
                error);
    }
}
