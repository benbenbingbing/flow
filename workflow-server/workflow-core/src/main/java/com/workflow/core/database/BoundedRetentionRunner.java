package com.workflow.core.database;

import com.workflow.core.database.port.DatabaseLockPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.TimeUnit;
import java.util.function.IntUnaryOperator;

/** 保留期清理共用的跨实例互斥与事务边界；每批提交，避免把整轮删除变成长事务。 */
@Component
public class BoundedRetentionRunner {
    private final DatabaseLockPort locks;
    private final PlatformTransactionManager transactions;

    public BoundedRetentionRunner(DatabaseLockPort locks, PlatformTransactionManager transactions) {
        this.locks = locks;
        this.transactions = transactions;
    }

    /**
     * 在稳定的任务键下执行有界清理，未抢到锁直接跳过。本轮在行数或时间耗尽后的批次边界停止。
     * deleteBatch 必须按传入上限选取稳定排序的 ID，并在删除时再次验证保留期/状态。
     * 某批失败只回滚该批，异常向外传播；已提交批次不再占锁或事务日志。
     */
    public int run(String task, int batchSize, int maxRows, int maxSeconds, IntUnaryOperator deleteBatch) {
        var acquired = locks.tryAcquire("retention", task);
        if (acquired.isEmpty()) return 0;
        int limit = Math.max(1, Math.min(1_000, batchSize));
        int rowBudget = Math.max(1, maxRows);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(1, Math.min(60, maxSeconds)));
        try (var ignored = acquired.get()) {
            int total = 0;
            while (total < rowBudget && System.nanoTime() < deadline) {
                int currentLimit = Math.min(limit, rowBudget - total);
                var transaction = new TransactionTemplate(transactions);
                transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                long remaining = Math.max(1, deadline - System.nanoTime());
                transaction.setTimeout((int) Math.max(1, (remaining + 999_999_999L) / 1_000_000_000L));
                Integer deleted = transaction.execute(status -> {
                    int result = deleteBatch.applyAsInt(currentLimit);
                    if (result < 0 || result > currentLimit) throw new IllegalStateException("清理批次超过删除上限");
                    return result;
                });
                if (deleted == null || deleted == 0) break;
                total += deleted;
                if (deleted < currentLimit) break;
            }
            return total;
        }
    }
}
