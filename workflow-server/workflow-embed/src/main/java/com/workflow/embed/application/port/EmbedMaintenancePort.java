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

    /**
     * 删除 {@code expires_at <= now} 的一批防重放记录。
     *
     * @param now 当前时间，供本方法删除过期断言{@code replays}时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 删除后的过期断言{@code replays}结果，供调用方继续处理
     */
    int deleteExpiredAssertionReplays(Instant now, int limit);

    /**
     * 原子把一批已到期的 ISSUED Launch 标记为 EXPIRED。
     *
     * @param now 当前时间，供本方法处理{@code expire}已签发启动记录时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 处理后的{@code expire}已签发启动记录结果，供调用方继续处理
     */
    int expireIssuedLaunches(Instant now, int limit);

    /**
     * 擦除在 cutoff 前进入终态的一批 Session Context 密文及关联摘要。
     *
     * @param cutoff 截止点，供本方法处理{@code erase}终态会话{@code contexts}时使用
     * @param now 当前时间，供本方法处理{@code erase}终态会话{@code contexts}时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 处理后的{@code erase}终态会话{@code contexts}结果，供调用方继续处理
     */
    int eraseTerminalSessionContexts(Instant cutoff, Instant now, int limit);

    /**
     * 按 Counter 复合主键 keyset 读取一页存量计数和真实活跃数。
     *
     * @param after 之后，供本方法检查已存储计数器分页时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 计数器观察集合，供调用方遍历或展示
     */
    List<CounterObservation> inspectStoredCounterPage(CounterCursor after, int limit);

    /**
     * 按 Session 复合键 keyset 读取一页活跃组合，用于发现缺失的 Counter 行。
     *
     * @param after 之后，供本方法检查活动会话{@code pair}分页时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 计数器观察集合，供调用方遍历或展示
     */
    List<CounterObservation> inspectActiveSessionPairPage(CounterCursor after, int limit);

    /**
     * 删除超过保留期且对应 integration 幂等记录已不存在的一批回执。
     *
     * @param cutoff 截止点，供本方法删除{@code orphan}操作{@code receipts}时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 删除后的{@code orphan}操作{@code receipts}结果，供调用方继续处理
     */
    int deleteOrphanOperationReceipts(Instant cutoff, int limit);

    /**
     * 按 FK 清理顺序删除一批超过保留期的终态 Session。
     *
     * @param cutoff 截止点，供本方法删除终态会话时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 删除后的终态会话结果，供调用方继续处理
     */
    int deleteTerminalSessions(Instant cutoff, int limit);

    /**
     * 删除一批超过保留期、处于终态且已无 Session 引用的 Launch。
     *
     * @param cutoff 截止点，供本方法删除{@code unreferenced}终态启动记录时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 删除后的{@code unreferenced}终态启动记录结果，供调用方继续处理
     */
    int deleteUnreferencedTerminalLaunches(Instant cutoff, int limit);

    /**
     * Grant/Flow 用户复合主键的内部 keyset 游标，不得进入日志或审计载荷。
     *
     * @param grantId 授权ID，后续用于处理计数器游标时定位或关联目标
     * @param flowUserId 流程用户ID，后续用于处理计数器游标时定位或关联目标
     */
    record CounterCursor(String grantId, String flowUserId) {

        /**
         * 初始化计数器游标，保存构造参数供后续方法使用。
         *
         * @param grantId 授权ID，后续用于初始化计数器游标时定位或关联目标
         * @param flowUserId 流程用户ID，后续用于初始化计数器游标时定位或关联目标
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
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
     *
     * @param grantId 授权ID，后续用于处理计数器观察时定位或关联目标
     * @param flowUserId 流程用户ID，后续用于处理计数器观察时定位或关联目标
     * @param storedCount 已存储数量，保存在对象中供后续校验、查询或展示
     * @param actualCount 实际数量，保存在对象中供后续校验、查询或展示
     */
    record CounterObservation(
            String grantId,
            String flowUserId,
            Integer storedCount,
            long actualCount) {

        /**
         * 初始化计数器观察，保存构造参数供后续方法使用。
         *
         * @param grantId 授权ID，后续用于初始化计数器观察时定位或关联目标
         * @param flowUserId 流程用户ID，后续用于初始化计数器观察时定位或关联目标
         * @param storedCount 已存储数量，保存在对象中供后续校验、查询或展示
         * @param actualCount 实际数量，保存在对象中供后续校验、查询或展示
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        public CounterObservation {
            if (grantId == null || grantId.isBlank()
                    || flowUserId == null || flowUserId.isBlank()) {
                throw new IllegalArgumentException("counter observation keys are required");
            }
            if ((storedCount != null && storedCount < 0) || actualCount < 0) {
                throw new IllegalArgumentException("counter values cannot be negative");
            }
        }

        /**
         * 处理有效已存储数量，并将结果传给后续步骤。
         *
         * @return 处理后的有效已存储数量结果，供调用方继续处理
         */
        public long effectiveStoredCount() {
            return storedCount == null ? 0L : storedCount.longValue();
        }

        /**
         * 判断{@code drifted}条件是否成立，供调用方选择后续分支。
         *
         * @return {@code drifted}条件成立时为 true，否则为 false
         */
        public boolean drifted() {
            return storedCount == null || effectiveStoredCount() != actualCount;
        }

        /**
         * 处理游标，并将结果传给后续步骤。
         *
         * @return 处理后的游标结果，供调用方继续处理
         */
        public CounterCursor cursor() {
            return new CounterCursor(grantId, flowUserId);
        }
    }
}
