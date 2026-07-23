package com.workflow.process.action;

import com.workflow.entity.FlowActionExecution;
import com.workflow.service.FlowActionExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 流程动作发件箱轮询器。
 *
 * <p>以固定延迟定时扫描流程动作执行队列，恢复中断的运行中记录并触发已就绪的提交后动作。
 * 通过乐观抢占（claim）保证多实例环境下同一执行记录只会被一个工作线程处理。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowActionOutboxWorker {

    private final FlowActionExecutionService executionService;
    private final FlowActionOutboxProcessor processor;

    /**
     * 定时轮询发件箱：先恢复中断记录，再批量抢占并执行就绪记录。
     */
    @Scheduled(fixedDelayString = "${workflow.flow-action.outbox-delay-ms:5000}")
    public void poll() {
        // 恢复因执行进程异常中断而停留于 RUNNING 状态的记录，使其重新可被抢占
        int recovered = executionService.recoverStale();
        if (recovered > 0) {
            log.warn("恢复 {} 条中断的流程动作执行记录", recovered);
        }
        // 每轮最多取 50 条就绪记录，抢占成功后交由处理器在新事务中执行
        for (FlowActionExecution execution : executionService.findReady(50)) {
            if (executionService.claim(execution.getId())) {
                processor.process(execution.getId());
            }
        }
    }
}
