package com.workflow.embed.application.port;

import java.time.Instant;
import java.util.List;

/**
 * Embed 持久化数据的小批量维护端口。
 *
 * <p>所有修改操作必须可重入，并由适配器放在彼此独立的小事务中；Counter 对账只读，
 * 不允许通过本端口自动纠偏。</p>
 */
public interface EmbedMaintenancePort {

    /** 删除 {@code expires_at <= now} 的一批防重放记录。 */
    int deleteExpiredAssertionReplays(Instant now, int limit);

    /** 原子把一批已到期的 ISSUED Launch 标记为 EXPIRED。 */
    int expireIssuedLaunches(Instant now, int limit);

    /** 擦除在 cutoff 前进入终态的一批 Session Context 密文及关联摘要。 */
    int eraseTerminalSessionContexts(Instant cutoff, Instant now, int limit);

    /** 按 Counter 复合主键 keyset 读取一页存量计数和真实活跃数。 */
    List<CounterObservation> inspectStoredCounterPage(CounterCursor after, int limit);

    /** 按 Session 复合键 keyset 读取一页活跃组合，用于发现缺失的 Counter 行。 */
    List<CounterObservation> inspectActiveSessionPairPage(CounterCursor after, int limit);

    /** 删除超过保留期且对应 integration 幂等记录已不存在的一批回执。 */
    int deleteOrphanOperationReceipts(Instant cutoff, int limit);

    /** 按 FK 清理顺序删除一批超过保留期的终态 Session。 */
    int deleteTerminalSessions(Instant cutoff, int limit);

    /** 删除一批超过保留期、处于终态且已无 Session 引用的 Launch。 */
    int deleteUnreferencedTerminalLaunches(Instant cutoff, int limit);

    /** Grant/Flow 用户复合主键的内部 keyset 游标，不得进入日志或审计载荷。 */
    record CounterCursor(String grantId, String flowUserId) {

        public CounterCursor {
            if (grantId == null || grantId.isBlank()
                    || flowUserId == null || flowUserId.isBlank()) {
                throw new IllegalArgumentException("counter cursor keys are required");
            }
        }
    }

    /**
     * 一条 Counter 与真实 ACTIVE、未释放 slot 的 Session 数量观测。
     * {@code storedCount == null} 表示 Session 对应的 Counter 行缺失。
     */
    record CounterObservation(
            String grantId,
            String flowUserId,
            Integer storedCount,
            long actualCount) {

        public CounterObservation {
            if (grantId == null || grantId.isBlank()
                    || flowUserId == null || flowUserId.isBlank()) {
                throw new IllegalArgumentException("counter observation keys are required");
            }
            if ((storedCount != null && storedCount < 0) || actualCount < 0) {
                throw new IllegalArgumentException("counter values cannot be negative");
            }
        }

        public long effectiveStoredCount() {
            return storedCount == null ? 0L : storedCount.longValue();
        }

        public boolean drifted() {
            return storedCount == null || effectiveStoredCount() != actualCount;
        }

        public CounterCursor cursor() {
            return new CounterCursor(grantId, flowUserId);
        }
    }
}
