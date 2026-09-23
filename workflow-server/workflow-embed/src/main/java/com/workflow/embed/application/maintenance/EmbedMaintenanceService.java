package com.workflow.embed.application.maintenance;

import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditResult;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.model.SystemAuditEvent;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.embed.application.port.EmbedMaintenancePort;
import com.workflow.embed.application.port.EmbedMaintenancePort.CounterCursor;
import com.workflow.embed.application.port.EmbedMaintenancePort.CounterObservation;
import com.workflow.embed.config.EmbedProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Embed 生产数据维护编排器。
 *
 * <p>每个适配器调用都是独立的小事务；任务之间故障隔离，避免单个脏批次阻断其余保留期
 * 处理。Counter 扫描使用内存 keyset 游标，只告警和审计，不在并发请求旁路自动修数。</p>
 */
@Service
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class EmbedMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(EmbedMaintenanceService.class);

    private final EmbedMaintenancePort maintenancePort;
    private final SystemAuditPort auditPort;
    private final EmbedProperties properties;
    private final Clock clock;

    // 游标只影响本 Pod 每次扫描的位置，不参与一致性决策；重启后从头扫描仍保持可重入。
    private CounterCursor storedCounterCursor;
    private CounterCursor activePairCursor;

    /**
     * 初始化嵌入式维护服务，保存构造参数供后续方法使用。
     *
     * @param maintenancePort 维护端口依赖，保存到当前对象供后续业务方法调用
     * @param auditPort 审计端口依赖，保存到当前对象供后续业务方法调用
     * @param properties 属性集合依赖，保存到当前对象供后续业务方法调用
     * @param clock 时钟依赖，保存到当前对象供后续业务方法调用
     */
    public EmbedMaintenanceService(
            EmbedMaintenancePort maintenancePort,
            SystemAuditPort auditPort,
            EmbedProperties properties,
            @Qualifier("embedClock") Clock clock) {
        this.maintenancePort = maintenancePort;
        this.auditPort = auditPort;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 运行一批可重入维护工作。
     *
     * <p>{@code synchronized} 只防止同一 Pod 重叠调度；跨 Pod 安全由 SQL 的状态条件、
     * FK/NOT EXISTS 条件和小事务保证，不依赖本地锁。</p>
     *
     * @return 处理后的{@code maintain}批次结果，供调用方继续处理
     */
    @Scheduled(fixedDelayString = "${workflow.embed.maintenance-scan-ms:60000}")
    public synchronized BatchResult maintainBatch() {
        Instant now = clock.instant();
        int limit = properties.getMaintenanceBatchSize();

        int replayClaims = mutate(MaintenanceTask.ASSERTION_REPLAY_CLEANUP,
                () -> maintenancePort.deleteExpiredAssertionReplays(now, limit));
        int expiredLaunches = mutate(MaintenanceTask.LAUNCH_EXPIRY,
                () -> maintenancePort.expireIssuedLaunches(now, limit));
        int erasedContexts = mutate(MaintenanceTask.SESSION_CONTEXT_ERASURE,
                () -> maintenancePort.eraseTerminalSessionContexts(
                        now.minusSeconds(properties.getTerminalContextRetentionSeconds()),
                        now, limit));

        ReconciliationSummary reconciliation = reconcileCounters(limit);

        int receipts = mutate(MaintenanceTask.OPERATION_RECEIPT_CLEANUP,
                () -> maintenancePort.deleteOrphanOperationReceipts(
                        now.minusSeconds(properties.getOperationReceiptRetentionSeconds()),
                        limit));
        // 按 FK 顺序先删长期终态 Session，再删已经无引用的终态 Launch。
        int sessions = mutate(MaintenanceTask.TERMINAL_SESSION_CLEANUP,
                () -> maintenancePort.deleteTerminalSessions(
                        now.minusSeconds(properties.getTerminalSessionRetentionSeconds()),
                        limit));
        int launches = mutate(MaintenanceTask.TERMINAL_LAUNCH_CLEANUP,
                () -> maintenancePort.deleteUnreferencedTerminalLaunches(
                        now.minusSeconds(properties.getTerminalLaunchRetentionSeconds()),
                        limit));

        return new BatchResult(replayClaims, expiredLaunches, erasedContexts,
                receipts, sessions, launches, reconciliation);
    }

    /**
     * 处理{@code mutate}，并将结果传给后续步骤。
     *
     * @param task 任务，作为 {@code maintenanceFailure} 的输入影响后续处理
     * @param operation 操作标识，决定后续{@code mutate}采用的处理分支
     * @return 处理后的{@code mutate}结果，供调用方继续处理
     */
    private int mutate(MaintenanceTask task, IntSupplier operation) {
        try {
            return operation.getAsInt();
        } catch (RuntimeException error) {
            maintenanceFailure(task, error);
            return 0;
        }
    }

    /**
     * 对账{@code counters}；结果供调用方的后续步骤使用。
     *
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 对账后的{@code counters}结果，供调用方继续处理
     */
    private ReconciliationSummary reconcileCounters(int limit) {
        try {
            List<CounterObservation> stored = maintenancePort.inspectStoredCounterPage(
                    storedCounterCursor, limit);
            List<CounterObservation> active = maintenancePort.inspectActiveSessionPairPage(
                    activePairCursor, limit);

            // 两个方向的分页会重叠；仅在内存按复合键去重，键绝不写入日志或审计。
            Map<CounterKey, CounterObservation> observations = new LinkedHashMap<>();
            stored.forEach(value -> observations.put(CounterKey.of(value), value));
            active.forEach(value -> observations.merge(
                    CounterKey.of(value), value, EmbedMaintenanceService::strongerDrift));

            ReconciliationSummary summary = summarize(
                    observations.values(), stored.size() == limit || active.size() == limit);
            // 两页都读取成功后才推进游标，单页失败时下一轮会完整重试当前窗口。
            storedCounterCursor = nextCursor(stored, limit);
            activePairCursor = nextCursor(active, limit);
            if (summary.mismatchCount() > 0) {
                counterDrift(summary);
            }
            return summary;
        } catch (RuntimeException error) {
            maintenanceFailure(MaintenanceTask.SESSION_COUNTER_RECONCILIATION, error);
            return ReconciliationSummary.empty();
        }
    }

    /**
     * 处理计数器{@code drift}，并将结果传给后续步骤。
     *
     * @param summary 摘要，供本方法处理计数器{@code drift}时使用
     */
    private void counterDrift(ReconciliationSummary summary) {
        log.warn("Embed session counter drift detected: mismatches={}, counterAbove={}, "
                        + "sessionAbove={}, missingCounters={}, maxDelta={}, scanContinues={}",
                summary.mismatchCount(), summary.counterGreaterCount(),
                summary.sessionGreaterCount(), summary.missingCounterCount(),
                summary.maxAbsoluteDelta(), summary.scanContinues());
        safeAudit(SystemAuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .module(AuditModule.SECURITY)
                .action(AuditAction.OTHER)
                .operationName("检测 Embed Session Counter 漂移")
                .riskLevel(AuditRiskLevel.HIGH)
                .result(AuditResult.FAILURE)
                .required(false)
                .targetType("EMBED_SESSION_COUNTER")
                .summary("Embed Session Counter 只读对账发现不一致")
                .afterData(Map.of(
                        "mismatchCount", summary.mismatchCount(),
                        "counterGreaterCount", summary.counterGreaterCount(),
                        "sessionGreaterCount", summary.sessionGreaterCount(),
                        "missingCounterCount", summary.missingCounterCount(),
                        "maxAbsoluteDelta", summary.maxAbsoluteDelta(),
                        "scanContinues", summary.scanContinues()))
                .errorCode("EMBED_SESSION_COUNTER_DRIFT")
                .createdAt(utcNow())
                .build());
    }

    /**
     * 处理维护失败，并将结果传给后续步骤。
     *
     * @param task 任务，供本方法处理维护失败时使用
     * @param error 错误，供本方法处理维护失败时使用
     */
    private void maintenanceFailure(MaintenanceTask task, RuntimeException error) {
        String exceptionType = error.getClass().getSimpleName();
        log.error("Embed maintenance step failed: task={}, exceptionType={}",
                task.tag, exceptionType);
        safeAudit(SystemAuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .module(AuditModule.SECURITY)
                .action(AuditAction.OTHER)
                .operationName("执行 Embed 数据维护")
                .riskLevel(AuditRiskLevel.HIGH)
                .result(AuditResult.FAILURE)
                .required(false)
                .targetType("EMBED_MAINTENANCE")
                .summary("Embed 数据维护步骤失败")
                .afterData(Map.of(
                        "task", task.tag,
                        "exceptionType", exceptionType))
                .errorCode("EMBED_MAINTENANCE_FAILED")
                .createdAt(utcNow())
                .build());
    }

    /**
     * 审计基础设施故障不能阻断后续清理；这里也只记录异常类型，不输出异常消息。
     *
     * @param event 事件，作为 {@code auditPort.record} 的输入影响后续处理
     */
    private void safeAudit(SystemAuditEvent event) {
        try {
            auditPort.record(event);
        } catch (RuntimeException error) {
            log.error("Embed maintenance audit failed: exceptionType={}",
                    error.getClass().getSimpleName());
        }
    }

    /**
     * 处理UTC当前时间，并将结果传给后续步骤。
     *
     * @return 处理后的UTC当前时间结果，供调用方继续处理
     */
    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /**
     * 处理{@code stronger}{@code drift}，并将结果传给后续步骤。
     *
     * @param left 左侧，供本方法处理{@code stronger}{@code drift}时使用
     * @param right 右侧，作为 {@code absoluteDelta} 的输入影响后续处理
     * @return 处理后的{@code stronger}{@code drift}结果，供调用方继续处理
     */
    private static CounterObservation strongerDrift(
            CounterObservation left,
            CounterObservation right) {
        if (!left.drifted()) {
            return right;
        }
        if (!right.drifted()) {
            return left;
        }
        return absoluteDelta(right) > absoluteDelta(left) ? right : left;
    }

    /**
     * 处理下一步游标，并将结果传给后续步骤。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 处理后的下一步游标结果，供调用方继续处理
     */
    private static CounterCursor nextCursor(List<CounterObservation> page, int limit) {
        return page.size() < limit ? null : page.get(page.size() - 1).cursor();
    }

    /**
     * 处理{@code summarize}，并将结果传给后续步骤。
     *
     * @param observations {@code observations}，供本方法处理{@code summarize}时使用
     * @param scanContinues {@code scan}{@code continues}，作为 {@code ReconciliationSummary} 的输入影响后续处理
     * @return 处理后的{@code summarize}结果，供调用方继续处理
     */
    private static ReconciliationSummary summarize(
            java.util.Collection<CounterObservation> observations,
            boolean scanContinues) {
        int mismatches = 0;
        int counterGreater = 0;
        int sessionGreater = 0;
        int missing = 0;
        long maxDelta = 0;
        for (CounterObservation observation : observations) {
            if (!observation.drifted()) {
                continue;
            }
            mismatches++;
            if (observation.storedCount() == null) {
                missing++;
            }
            long stored = observation.effectiveStoredCount();
            if (stored > observation.actualCount()) {
                counterGreater++;
            } else {
                sessionGreater++;
            }
            maxDelta = Math.max(maxDelta, absoluteDelta(observation));
        }
        return new ReconciliationSummary(
                mismatches, counterGreater, sessionGreater, missing, maxDelta, scanContinues);
    }

    /**
     * 处理绝对{@code delta}，并将结果传给后续步骤。
     *
     * @param observation 观察，作为 {@code Math.abs} 的输入影响后续处理
     * @return 处理后的绝对{@code delta}结果，供调用方继续处理
     */
    private static long absoluteDelta(CounterObservation observation) {
        return Math.abs(observation.effectiveStoredCount() - observation.actualCount());
    }

    /**
     * 定义维护任务的可选值；调用方据此选择对应的处理分支。
     */
    enum MaintenanceTask {
        ASSERTION_REPLAY_CLEANUP("assertion_replay_cleanup"),
        LAUNCH_EXPIRY("launch_expiry"),
        SESSION_CONTEXT_ERASURE("session_context_erasure"),
        SESSION_COUNTER_RECONCILIATION("session_counter_reconciliation"),
        OPERATION_RECEIPT_CLEANUP("operation_receipt_cleanup"),
        TERMINAL_SESSION_CLEANUP("terminal_session_cleanup"),
        TERMINAL_LAUNCH_CLEANUP("terminal_launch_cleanup");

        private final String tag;

        /**
         * 初始化维护任务，保存构造参数供后续方法使用。
         *
         * @param tag 标签依赖，保存到当前对象供后续业务方法调用
         */
        MaintenanceTask(String tag) {
            this.tag = tag;
        }
    }

    /**
     * 封装计数器键的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param grantId 授权ID，后续用于处理计数器键时定位或关联目标
     * @param flowUserId 流程用户ID，后续用于处理计数器键时定位或关联目标
     */
    private record CounterKey(String grantId, String flowUserId) {
        /**
         * 处理of，并将结果传给后续步骤。
         *
         * @param observation 观察，作为 {@code CounterKey} 的输入影响后续处理
         * @return 处理后的of结果，供调用方继续处理
         */
        private static CounterKey of(CounterObservation observation) {
            return new CounterKey(observation.grantId(), observation.flowUserId());
        }
    }

    /**
     * 封装{@code reconciliation}摘要的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param mismatchCount {@code mismatch}数量，保存在对象中供后续校验、查询或展示
     * @param counterGreaterCount 计数器{@code greater}数量，保存在对象中供后续校验、查询或展示
     * @param sessionGreaterCount 会话{@code greater}数量，保存在对象中供后续校验、查询或展示
     * @param missingCounterCount 缺失计数器数量，保存在对象中供后续校验、查询或展示
     * @param maxAbsoluteDelta 最大绝对{@code delta}，保存在对象中供后续校验、查询或展示
     * @param scanContinues {@code scan}{@code continues}，保存在对象中供后续校验、查询或展示
     */
    public record ReconciliationSummary(
            int mismatchCount,
            int counterGreaterCount,
            int sessionGreaterCount,
            int missingCounterCount,
            long maxAbsoluteDelta,
            boolean scanContinues) {

        /**
         * 处理空，并将结果传给后续步骤。
         *
         * @return 处理后的空结果，供调用方继续处理
         */
        private static ReconciliationSummary empty() {
            return new ReconciliationSummary(0, 0, 0, 0, 0, false);
        }
    }

    /**
     * 封装批次的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param deletedAssertionReplays 已删除断言{@code replays}，保存在对象中供后续校验、查询或展示
     * @param expiredLaunches 过期启动记录，保存在对象中供后续校验、查询或展示
     * @param erasedSessionContexts {@code erased}会话{@code contexts}，保存在对象中供后续校验、查询或展示
     * @param deletedOperationReceipts 已删除操作{@code receipts}，保存在对象中供后续校验、查询或展示
     * @param deletedTerminalSessions 已删除终态会话，保存在对象中供后续校验、查询或展示
     * @param deletedTerminalLaunches 已删除终态启动记录，保存在对象中供后续校验、查询或展示
     * @param reconciliation {@code reconciliation}，保存在对象中供后续校验、查询或展示
     */
    public record BatchResult(
            int deletedAssertionReplays,
            int expiredLaunches,
            int erasedSessionContexts,
            int deletedOperationReceipts,
            int deletedTerminalSessions,
            int deletedTerminalLaunches,
            ReconciliationSummary reconciliation) {
    }
}
