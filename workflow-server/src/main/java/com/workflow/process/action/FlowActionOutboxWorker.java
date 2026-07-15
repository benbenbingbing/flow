package com.workflow.process.action;

import com.workflow.entity.FlowActionExecution;
import com.workflow.service.FlowActionExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlowActionOutboxWorker {

    private final FlowActionExecutionService executionService;
    private final FlowActionOutboxProcessor processor;

    @Scheduled(fixedDelayString = "${workflow.flow-action.outbox-delay-ms:5000}")
    public void poll() {
        int recovered = executionService.recoverStale();
        if (recovered > 0) {
            log.warn("恢复 {} 条中断的流程动作执行记录", recovered);
        }
        for (FlowActionExecution execution : executionService.findReady(50)) {
            if (executionService.claim(execution.getId())) {
                processor.process(execution.getId());
            }
        }
    }
}
