package com.workflow.embed.infrastructure.persistence.adapter;

import com.workflow.embed.application.port.EmbedMaintenancePort;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedMaintenanceMapper;
import com.workflow.embed.infrastructure.persistence.record.EmbedSessionCounterObservationRow;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** MyBatis 维护适配器；每次调用只执行一个有界 SQL 和一个独立小事务。 */
@Repository
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class MyBatisEmbedMaintenanceAdapter implements EmbedMaintenancePort {

    private static final int MAX_BATCH_SIZE = 1_000;

    private final EmbedMaintenanceMapper mapper;

    /**
     * 初始化MyBatis嵌入式维护适配器，保存构造参数供后续方法使用。
     *
     * @param mapper 映射器依赖，保存到当前对象供后续业务方法调用
     */
    public MyBatisEmbedMaintenanceAdapter(EmbedMaintenanceMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 删除过期断言{@code replays}；后续读取或执行将使用更新后的状态。
     *
     * @param now 当前时间，供本方法删除过期断言{@code replays}时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 删除后的过期断言{@code replays}结果，供调用方继续处理
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int deleteExpiredAssertionReplays(Instant now, int limit) {
        return write(() -> mapper.deleteExpiredAssertionReplays(local(now), batch(limit)));
    }

    /**
     * 处理{@code expire}已签发启动记录，并将结果传给后续步骤。
     *
     * @param now 当前时间，供本方法处理{@code expire}已签发启动记录时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 处理后的{@code expire}已签发启动记录结果，供调用方继续处理
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int expireIssuedLaunches(Instant now, int limit) {
        return write(() -> mapper.expireIssuedLaunches(local(now), batch(limit)));
    }

    /**
     * 处理{@code erase}终态会话{@code contexts}，并将结果传给后续步骤。
     *
     * @param cutoff 截止点，供本方法处理{@code erase}终态会话{@code contexts}时使用
     * @param now 当前时间，供本方法处理{@code erase}终态会话{@code contexts}时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 处理后的{@code erase}终态会话{@code contexts}结果，供调用方继续处理
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int eraseTerminalSessionContexts(Instant cutoff, Instant now, int limit) {
        return write(() -> mapper.eraseTerminalSessionContexts(
                local(cutoff), local(now), batch(limit)));
    }

    /**
     * 检查已存储计数器分页；不满足约束时阻止后续处理。
     *
     * @param after 之后，供本方法检查已存储计数器分页时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 计数器观察集合，供调用方遍历或展示
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<CounterObservation> inspectStoredCounterPage(CounterCursor after, int limit) {
        return read(() -> mapper.inspectStoredCounterPage(
                afterGrantId(after), afterFlowUserId(after), batch(limit)));
    }

    /**
     * 检查活动会话{@code pair}分页；不满足约束时阻止后续处理。
     *
     * @param after 之后，供本方法检查活动会话{@code pair}分页时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 计数器观察集合，供调用方遍历或展示
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<CounterObservation> inspectActiveSessionPairPage(
            CounterCursor after,
            int limit) {
        return read(() -> mapper.inspectActiveSessionPairPage(
                afterGrantId(after), afterFlowUserId(after), batch(limit)));
    }

    /**
     * 删除{@code orphan}操作{@code receipts}；后续读取或执行将使用更新后的状态。
     *
     * @param cutoff 截止点，供本方法删除{@code orphan}操作{@code receipts}时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 删除后的{@code orphan}操作{@code receipts}结果，供调用方继续处理
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int deleteOrphanOperationReceipts(Instant cutoff, int limit) {
        return write(() -> mapper.deleteOrphanOperationReceipts(local(cutoff), batch(limit)));
    }

    /**
     * 删除终态会话；后续读取或执行将使用更新后的状态。
     *
     * @param cutoff 截止点，供本方法删除终态会话时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 删除后的终态会话结果，供调用方继续处理
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int deleteTerminalSessions(Instant cutoff, int limit) {
        return write(() -> mapper.deleteTerminalSessions(local(cutoff), batch(limit)));
    }

    /**
     * 删除{@code unreferenced}终态启动记录；后续读取或执行将使用更新后的状态。
     *
     * @param cutoff 截止点，供本方法删除{@code unreferenced}终态启动记录时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 删除后的{@code unreferenced}终态启动记录结果，供调用方继续处理
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int deleteUnreferencedTerminalLaunches(Instant cutoff, int limit) {
        return write(() -> mapper.deleteUnreferencedTerminalLaunches(
                local(cutoff), batch(limit)));
    }

    /**
     * 写入MyBatis嵌入式维护；后续读取或执行将使用更新后的状态。
     *
     * @param operation 操作标识，决定后续MyBatis嵌入式维护采用的处理分支
     * @return 写入后的MyBatis嵌入式维护结果，供调用方继续处理
     */
    private static int write(WriteOperation operation) {
        try {
            return operation.execute();
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    /**
     * 读取MyBatis嵌入式维护；查询结果供调用方展示或继续处理。
     *
     * @param operation 操作标识，决定后续MyBatis嵌入式维护采用的处理分支
     * @return 计数器观察集合，供调用方遍历或展示
     */
    private static List<CounterObservation> read(ReadOperation operation) {
        try {
            List<EmbedSessionCounterObservationRow> rows = operation.execute();
            return rows == null
                    ? List.of()
                    : rows.stream().map(MyBatisEmbedMaintenanceAdapter::map).toList();
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    /**
     * 处理映射，并将结果传给后续步骤。
     *
     * @param row 行，作为 {@code CounterObservation} 的输入影响后续处理
     * @return 处理后的映射结果，供调用方继续处理
     */
    private static CounterObservation map(EmbedSessionCounterObservationRow row) {
        return new CounterObservation(
                row.grantId(), row.flowUserId(), row.storedCount(), row.actualCount());
    }

    /**
     * 生成之后授权ID文本，供后续匹配或展示。
     *
     * @param after 之后，供本方法处理之后授权ID时使用
     * @return 处理后的之后授权ID文本，供调用方比较或展示
     */
    private static String afterGrantId(CounterCursor after) {
        return after == null ? "" : after.grantId();
    }

    /**
     * 生成之后流程用户ID文本，供后续匹配或展示。
     *
     * @param after 之后，供本方法处理之后流程用户ID时使用
     * @return 处理后的之后流程用户ID文本，供调用方比较或展示
     */
    private static String afterFlowUserId(CounterCursor after) {
        return after == null ? "" : after.flowUserId();
    }

    /**
     * 处理批次，并将结果传给后续步骤。
     *
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 处理后的批次结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static int batch(int limit) {
        if (limit < 1 || limit > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("maintenance batch size is out of range");
        }
        return limit;
    }

    /**
     * 处理本地，并将结果传给后续步骤。
     *
     * @param value 待处理本地的原始输入，结果供调用方继续使用
     * @return 处理后的本地结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static LocalDateTime local(Instant value) {
        if (value == null) {
            throw new IllegalArgumentException("maintenance timestamp is required");
        }
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    /**
     * 构造服务不可用异常，供调用方区分失败原因。
     *
     * @param cause 原因，作为 {@code IllegalStateException} 的输入影响后续处理
     * @return 处理后的不可用结果，供调用方继续处理
     */
    private static IllegalStateException unavailable(DataAccessException cause) {
        return new IllegalStateException("Embed maintenance persistence is unavailable", cause);
    }

    /**
     * 定义写入操作的调用契约；实现层按此提供能力，调用方无需依赖具体实现。
     */
    @FunctionalInterface
    private interface WriteOperation {
        /**
         * 执行写入操作，并将结果传给后续步骤。
         *
         * @return 执行后的写入操作结果，供调用方继续处理
         */
        int execute();
    }

    /**
     * 定义读取操作的调用契约；实现层按此提供能力，调用方无需依赖具体实现。
     */
    @FunctionalInterface
    private interface ReadOperation {
        /**
         * 执行读取操作，并将结果传给后续步骤。
         *
         * @return 嵌入式会话计数器观察行集合，供调用方遍历或展示
         */
        List<EmbedSessionCounterObservationRow> execute();
    }
}
