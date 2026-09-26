package com.workflow.process.action.application;

import com.workflow.contracts.process.action.context.FlowActionFailureContext;
import com.workflow.contracts.process.action.model.*;
import com.workflow.process.action.infrastructure.persistence.record.FlowAction;
import com.workflow.process.action.infrastructure.persistence.record.FlowActionExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import java.util.*;

/** 异常已经发生后才调用策略。这里只计算决定，持久化与流程副作用由调用层承担。 */
@lombok.extern.slf4j.Slf4j
@Component
@RequiredArgsConstructor
public class FlowActionFailureCoordinator {
    private final FlowActionFailureStrategyCatalog catalog;

    /** 同时保存策略建议及平台最终决定，便于解释预算或事务约束为何覆盖建议。 */
    public record Outcome(FailureDecision requested, FailureDecision effective, String strategyError) {}

    /** 执行准备失败不属于业务处理器失败，不交给自定义策略重试，避免未消耗尝试次数的无限循环。 */
    public Outcome preflightFailure(FlowAction action, Throwable error) {
        return new Outcome(null, FailureDecision.of("IN_TRANSACTION".equalsIgnoreCase(action.getExecutionMode())
                ? FailureDisposition.ROLLBACK : FailureDisposition.MANUAL,
                "STRATEGY_ERROR", "执行前校验失败，未调用动作处理器"),
                error.getClass().getName() + ": " + safeMessage(error));
    }

    /** 在动作原调用上下文中计算失败决定；结果仅表示建议，调用者须先持久化再落实。 */
    public Outcome decide(FlowAction action, FlowActionExecution execution, Throwable error, boolean retryable) {
        boolean transactional = "IN_TRANSACTION".equalsIgnoreCase(action.getExecutionMode());
        FailureDecision requested = null;
        long started = System.nanoTime();
        try {
            var d = catalog.descriptor(action);
            var mode = FlowActionExecutionMode.valueOf(action.getExecutionMode().toUpperCase(Locale.ROOT));
            if (!d.supportedExecutionModes().contains(mode)) throw new IllegalArgumentException("策略执行方式不兼容");
            List<String> types = new ArrayList<>();
            Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Throwable t = error; t != null && types.size() < 16 && seen.add(t); t = t.getCause()) {
                types.add(t.getClass().getName());
            }
            var context = new FlowActionFailureContext(execution.getId(), action.getId(),
                    execution.getProcessInstanceId(), execution.getVersionId(), execution.getExecutionId(),
                    execution.getEntityCode(), execution.getTriggerTiming(), action.getExecutionMode(),
                    execution.getAttemptNo() == null ? 1 : Math.max(1, execution.getAttemptNo()),
                    execution.getMaxRetries() == null ? 0 : execution.getMaxRetries(), retryable,
                    types, safeMessage(error));
            requested = catalog.require(d.code(), d.version()).decide(context, catalog.configuration(action));
            if (requested == null || requested.disposition() == null
                    || !d.possibleDispositions().contains(requested.disposition())
                    || !FlowActionFailureStrategyCatalog.allowed(mode, requested.disposition())
                    || requested.reasonCode() == null || !requested.reasonCode().matches("[A-Z][A-Z0-9_]{0,63}")
                    || requested.reason() == null || requested.reason().length() > 1000) {
                throw new IllegalArgumentException("策略返回了非法决定");
            }
            if (requested.disposition() == FailureDisposition.RETRY) {
                if (requested.retryDelaySeconds() == null || requested.retryDelaySeconds() < 1
                        || requested.retryDelaySeconds() > 21600) throw new IllegalArgumentException("重试间隔须在 1 秒到 6 小时之间");
                if (!retryable) return override(requested, "HANDLER_NOT_RETRYABLE", "处理器不支持安全重放");
                if (context.exhausted()) return override(requested, "RETRY_EXHAUSTED", "额外重试次数已耗尽");
            } else if (requested.retryDelaySeconds() != null) {
                throw new IllegalArgumentException("只有重试决定可以设置等待时间");
            }
            if (requested.disposition() == FailureDisposition.CONTINUE && !canContinue(error)) {
                return new Outcome(requested, FailureDecision.of(FailureDisposition.ROLLBACK,
                        "TRANSACTION_UNUSABLE", "无法确认事务可继续，本次操作回滚"), null);
            }
            return new Outcome(requested, requested, null);
        } catch (Exception strategyError) {
            return new Outcome(requested, FailureDecision.of(transactional ? FailureDisposition.ROLLBACK
                    : FailureDisposition.MANUAL, "STRATEGY_ERROR", "失败策略不可用或返回非法结果"),
                    strategyError.getClass().getName() + ": " + safeMessage(strategyError));
        } finally {
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            if (durationMs > 100) log.warn("失败策略计算耗时较长: strategy={}, version={}, durationMs={}",
                    action.getFailureStrategyCode(), action.getFailureStrategyVersion(), durationMs);
        }
    }

    private static Outcome override(FailureDecision requested, String code, String reason) {
        return new Outcome(requested, FailureDecision.of(FailureDisposition.MANUAL, code, reason), null);
    }

    /** 事务资源错误即使被处理器捕获也可能使数据库事务失效，不能只检查 Java 是否正常返回。 */
    private static boolean canContinue(Throwable error) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return false;
        try {
            if (TransactionAspectSupport.currentTransactionStatus().isRollbackOnly()) return false;
        } catch (org.springframework.transaction.NoTransactionException missingStatus) { return false; }
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = error; t != null && seen.add(t); t = t.getCause()) {
            if (t instanceof java.sql.SQLException || t instanceof org.springframework.dao.DataAccessException
                    || t instanceof org.springframework.transaction.TransactionException) return false;
        }
        return true;
    }

    private static String safeMessage(Throwable error) {
        String message = error == null || error.getMessage() == null ? "" : error.getMessage();
        return message.substring(0, Math.min(message.length(), 1000));
    }
}
