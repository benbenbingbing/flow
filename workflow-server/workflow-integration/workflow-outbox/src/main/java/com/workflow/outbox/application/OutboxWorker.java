package com.workflow.outbox.application;

import com.workflow.outbox.infrastructure.persistence.mapper.OutboxRecordMapper;
import com.workflow.outbox.infrastructure.persistence.record.OutboxRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 通用 Outbox 的定时认领与投递工作器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxWorker {

    private final OutboxRecordMapper mapper;
    private final OutboxProcessor processor;

    @Value("${workflow.outbox.batch-size:100}")
    private int batchSize = 100;

    @Value("${workflow.outbox.processing-timeout-minutes:10}")
    private int processingTimeoutMinutes = 10;

    @Scheduled(fixedDelayString = "${workflow.outbox.delay-ms:3000}")
    public void dispatchReady() {
        int recovered = mapper.recoverStaleProcessing(
                LocalDateTime.now().minusMinutes(
                        Math.max(1, processingTimeoutMinutes)));
        if (recovered > 0) {
            log.warn("回收超时 Outbox 事件: count={}", recovered);
        }
        for (OutboxRecord record : mapper.findReady(
                Math.max(1, batchSize))) {
            if (mapper.claim(record.getId()) == 0) {
                continue;
            }
            try {
                processor.process(record.getId());
            } catch (RuntimeException exception) {
                log.error(
                        "Outbox 调度失败: id={}, topic={}",
                        record.getId(),
                        record.getTopic(),
                        exception);
            }
        }
    }
}
