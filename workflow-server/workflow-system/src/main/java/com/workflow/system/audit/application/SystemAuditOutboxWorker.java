package com.workflow.system.audit.application;

import com.workflow.system.audit.domain.SystemAuditOutbox;
import com.workflow.system.audit.infrastructure.SystemAuditOutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 定时领取并投递待处理系统审计事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemAuditOutboxWorker {

    private final SystemAuditOutboxMapper outboxMapper;
    private final SystemAuditOutboxProcessor processor;

    @Value("${workflow.audit.outbox-batch-size:100}")
    private int batchSize;

    @Value("${workflow.audit.processing-timeout-minutes:10}")
    private int processingTimeoutMinutes;

    @Scheduled(fixedDelayString = "${workflow.audit.outbox-delay-ms:3000}")
    public void dispatchReady() {
        int recovered = outboxMapper.recoverStaleProcessing(
                LocalDateTime.now().minusMinutes(Math.max(1, processingTimeoutMinutes)));
        if (recovered > 0) {
            log.warn("回收超时系统审计 Outbox: count={}", recovered);
        }
        for (SystemAuditOutbox outbox : outboxMapper.findReady(Math.max(1, batchSize))) {
            if (outboxMapper.claim(outbox.getId()) > 0) {
                processor.process(outbox.getId());
            }
        }
    }
}
