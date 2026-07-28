package com.workflow.outbox.application;

import com.workflow.outbox.infrastructure.persistence.mapper.OutboxRecordMapper;
import com.workflow.outbox.infrastructure.persistence.record.OutboxRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 通用 Outbox 的定时认领与投递工作器。
 */
@Slf4j
@Component
public class OutboxWorker {

    private final OutboxRecordMapper mapper;
    private final OutboxProcessor processor;
    private final Executor executor;
    private final String ownerId =
            "outbox-" + UUID.randomUUID().toString();

    @Value("${workflow.outbox.batch-size:100}")
    private int batchSize = 100;

    @Value("${workflow.outbox.lease-seconds:120}")
    private int leaseSeconds = 120;

    public OutboxWorker(
            OutboxRecordMapper mapper,
            OutboxProcessor processor,
            @Qualifier("outboxTaskExecutor") Executor executor) {
        this.mapper = mapper;
        this.processor = processor;
        this.executor = executor;
    }

    OutboxWorker(OutboxRecordMapper mapper, OutboxProcessor processor) {
        this(mapper, processor, Runnable::run);
    }

    @Scheduled(fixedDelayString = "${workflow.outbox.delay-ms:3000}")
    public void dispatchReady() {
        int recovered = mapper.recoverExpiredLeases();
        if (recovered > 0) {
            log.warn("回收超时 Outbox 事件: count={}", recovered);
        }
        for (OutboxRecord record : mapper.findReady(
                Math.max(1, batchSize))) {
            int effectiveLeaseSeconds = Math.max(10, leaseSeconds);
            if (mapper.claim(
                    record.getId(), ownerId, effectiveLeaseSeconds) == 0) {
                continue;
            }
            OutboxRecord claimed = mapper.selectClaimed(
                    record.getId(), ownerId);
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
                mapper.releaseClaim(
                        claimed.getId(), ownerId, claimed.getLeaseToken());
                log.error(
                        "Outbox 执行队列已满，释放租约: id={}, topic={}",
                        claimed.getId(),
                        claimed.getTopic());
            }
        }
    }
}
