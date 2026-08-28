package com.workflow.embed.application.maintenance;

import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditResult;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
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

    private int mutate(MaintenanceTask task, IntSupplier operation) {
        try {
            return operation.getAsInt();
        } catch (RuntimeException error) {
            maintenanceFailure(task, error);
            return 0;
        }
    }

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

    /** 审计基础设施故障不能阻断后续清理；这里也只记录异常类型，不输出异常消息。 */
    private void safeAudit(SystemAuditEvent event) {
        try {
            auditPort.record(event);
        } catch (RuntimeException error) {
            log.error("Embed maintenance audit failed: exceptionType={}",
                    error.getClass().getSimpleName());
        }
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

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

    private static CounterCursor nextCursor(List<CounterObservation> page, int limit) {
        return page.size() < limit ? null : page.get(page.size() - 1).cursor();
    }

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

    private static long absoluteDelta(CounterObservation observation) {
        return Math.abs(observation.effectiveStoredCount() - observation.actualCount());
    }

    enum MaintenanceTask {
        ASSERTION_REPLAY_CLEANUP("assertion_replay_cleanup"),
        LAUNCH_EXPIRY("launch_expiry"),
        SESSION_CONTEXT_ERASURE("session_context_erasure"),
        SESSION_COUNTER_RECONCILIATION("session_counter_reconciliation"),
        OPERATION_RECEIPT_CLEANUP("operation_receipt_cleanup"),
        TERMINAL_SESSION_CLEANUP("terminal_session_cleanup"),
        TERMINAL_LAUNCH_CLEANUP("terminal_launch_cleanup");

        private final String tag;

        MaintenanceTask(String tag) {
            this.tag = tag;
        }
    }

    private record CounterKey(String grantId, String flowUserId) {
        private static CounterKey of(CounterObservation observation) {
            return new CounterKey(observation.grantId(), observation.flowUserId());
        }
    }

    public record ReconciliationSummary(
            int mismatchCount,
            int counterGreaterCount,
            int sessionGreaterCount,
            int missingCounterCount,
            long maxAbsoluteDelta,
            boolean scanContinues) {

        private static ReconciliationSummary empty() {
            return new ReconciliationSummary(0, 0, 0, 0, 0, false);
        }
    }

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
