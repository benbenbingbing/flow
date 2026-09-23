package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedSessionSecuritySnapshot;
import com.workflow.embed.domain.EmbedSessionTermination;
import com.workflow.embed.domain.EmbedSessionTerminationResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Session lookup, bounded heartbeat and idempotent slot-releasing termination primitives. */
public interface EmbedSessionPersistencePort {

    /**
     * 按令牌摘要查询嵌入式会话安全快照；结果供后续展示或处理。
     *
     * @param tokenDigest 令牌摘要，供本方法查询令牌摘要时使用
     * @return 匹配的令牌摘要；未找到时为空
     */
    Optional<EmbedSessionSecuritySnapshot> findByTokenDigest(String tokenDigest);

    /**
     * 判断更新访问时间最后已见条件是否成立，供调用方选择后续分支。
     *
     * @param sessionId 会话ID，后续用于处理更新访问时间最后已见时定位或关联目标
     * @param expectedLastSeen 预期最后已见，供本方法处理更新访问时间最后已见时使用
     * @param now 当前时间，供本方法处理更新访问时间最后已见时使用
     * @return 更新访问时间最后已见条件成立时为 true，否则为 false
     */
    boolean touchLastSeen(String sessionId, Instant expectedLastSeen, Instant now);

    /**
     * 处理心跳，并将结果传给后续步骤。
     *
     * @param sessionId 会话ID，后续用于处理心跳时定位或关联目标
     * @param now 当前时间，供本方法处理心跳时使用
     * @param requestedIdleExpiry 请求空闲{@code expiry}，供本方法处理心跳时使用
     * @return 匹配的心跳；未找到时为空
     */
    Optional<EmbedSessionSecuritySnapshot> heartbeat(
            String sessionId,
            Instant now,
            Instant requestedIdleExpiry);

    /**
     * 按索引小批量查找已到 idle/absolute 截止时间但尚未释放 slot 的 ACTIVE Session。
     *
     * @param now 当前时间，供本方法查询过期令牌摘要集合时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 嵌入式会话持久化集合，供调用方遍历或展示
     */
    List<String> findExpiredTokenDigests(Instant now, int limit);

    /**
     * 终止嵌入式会话持久化；后续读取或执行将使用更新后的状态。
     *
     * @param tokenDigest 令牌摘要，供本方法终止嵌入式会话持久化时使用
     * @param terminalStatus 终态状态标识，决定后续嵌入式会话持久化采用的处理分支
     * @param reason 原因，供本方法终止嵌入式会话持久化时使用
     * @param now 当前时间，供本方法终止嵌入式会话持久化时使用
     * @return 终止后的嵌入式会话持久化结果，供调用方继续处理
     */
    EmbedSessionTermination terminate(
            String tokenDigest,
            String terminalStatus,
            String reason,
            Instant now);

    /**
     * 与 {@link #terminate} 相同，但返回不含凭据的审计摘要。
     *
     * @param tokenDigest 令牌摘要，作为 {@code terminate} 的输入影响后续处理
     * @param terminalStatus 终态状态标识，决定后续{@code detailed}采用的处理分支
     * @param reason 原因，作为 {@code terminate} 的输入影响后续处理
     * @param now 当前时间，作为 {@code terminate} 的输入影响后续处理
     * @return 终止后的{@code detailed}结果，供调用方继续处理
     */
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

    /**
     * 管理端按 Session ID 撤销；实现必须与 token 入口复用同一锁序和 exactly-once 条件。
     *
     * @param sessionId 会话ID，后续用于终止ID时定位或关联目标
     * @param terminalStatus 终态状态标识，决定后续ID采用的处理分支
     * @param reason 原因，供本方法终止ID时使用
     * @param now 当前时间，供本方法终止ID时使用
     * @return 终止后的ID结果，供调用方继续处理
     */
    default EmbedSessionTerminationResult terminateById(
            String sessionId,
            String terminalStatus,
            String reason,
            Instant now) {
        return EmbedSessionTerminationResult.invalid();
    }
}
