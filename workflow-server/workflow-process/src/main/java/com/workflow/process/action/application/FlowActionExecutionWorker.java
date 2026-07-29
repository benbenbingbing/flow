package com.workflow.process.action.application;

import com.workflow.process.action.infrastructure.persistence.record.FlowActionExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 流程动作执行队列轮询器。
 *
 * <p>以固定延迟定时扫描流程动作执行队列，恢复中断的运行中记录并触发已就绪的提交后动作。
 * 通过乐观抢占（claim）保证多实例环境下同一执行记录只会被一个工作线程处理。</p>
 */
@Slf4j
@Component
public class FlowActionExecutionWorker {

    private final FlowActionExecutionService executionService;
    private final FlowActionExecutionProcessor processor;
    private final Executor executor;
    private final String ownerId =
            "flow-action-" + UUID.randomUUID();

    @Value("${workflow.flow-action.batch-size:50}")
    private int batchSize = 50;

    @Value("${workflow.flow-action.lease-seconds:300}")
    private int leaseSeconds = 300;

    @Autowired
    public FlowActionExecutionWorker(
            FlowActionExecutionService executionService,
            FlowActionExecutionProcessor processor,
            @Qualifier("flowActionTaskExecutor") Executor executor) {
        this.executionService = executionService;
        this.processor = processor;
        this.executor = executor;
    }

    FlowActionExecutionWorker(
            FlowActionExecutionService executionService,
            FlowActionExecutionProcessor processor) {
        this(executionService, processor, Runnable::run);
    }

    /**
     * 定时轮询发件箱：先恢复中断记录，再批量抢占并执行就绪记录。
     */
    @Scheduled(fixedDelayString = "${workflow.flow-action.execution-delay-ms:5000}")
    public void poll() {
        // 恢复数据库时间已到期的租约，使中断任务可被其他 Pod 重新抢占
        int recovered = executionService.recoverExpiredLeases();
        if (recovered > 0) {
            log.warn("恢复 {} 条中断的流程动作执行记录", recovered);
        }
        // 抢占成功后交给有界线程池执行；队列满时释放租约
        int effectiveLeaseSeconds = Math.max(30, leaseSeconds);
        for (FlowActionExecution execution :
                executionService.findReady(Math.max(1, batchSize))) {
            FlowActionExecution claimed = executionService.claim(
                    execution.getId(), ownerId, effectiveLeaseSeconds);
            if (claimed == null || claimed.getLeaseToken() == null) {
                continue;
            }
            try {
                executor.execute(() -> processor.process(
                        claimed.getId(),
                        ownerId,
                        claimed.getLeaseToken(),
                        effectiveLeaseSeconds));
            } catch (RejectedExecutionException exception) {
                executionService.releaseClaim(
                        claimed.getId(), ownerId, claimed.getLeaseToken());
                log.error("流程动作执行队列已满，释放租约: id={}", claimed.getId());
            }
        }
    }
}
