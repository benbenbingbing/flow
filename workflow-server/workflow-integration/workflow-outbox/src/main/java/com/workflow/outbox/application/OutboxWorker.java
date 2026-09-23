package com.workflow.outbox.application;

import com.workflow.outbox.infrastructure.persistence.mapper.OutboxRecordMapper;
import com.workflow.outbox.infrastructure.persistence.record.OutboxRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * 初始化待发送事件{@code worker}，保存构造参数供后续方法使用。
     *
     * @param mapper 映射器依赖，保存到当前对象供后续业务方法调用
     * @param processor {@code processor}依赖，保存到当前对象供后续业务方法调用
     * @param executor 执行器依赖，保存到当前对象供后续业务方法调用
     */
    @Autowired
    public OutboxWorker(
            OutboxRecordMapper mapper,
            OutboxProcessor processor,
            @Qualifier("outboxTaskExecutor") Executor executor) {
        this.mapper = mapper;
        this.processor = processor;
        this.executor = executor;
    }

    /**
     * 初始化待发送事件{@code worker}，保存构造参数供后续方法使用。
     *
     * @param mapper 持久层映射器，后续用于读取或写入对应业务数据
     * @param processor {@code processor}，保存在对象中供后续校验、查询或展示
     */
    OutboxWorker(OutboxRecordMapper mapper, OutboxProcessor processor) {
        this(mapper, processor, Runnable::run);
    }

    /**
     * 分发就绪；后续由接收方或异步任务继续处理。
     */
    @Scheduled(fixedDelayString = "${workflow.outbox.delay-ms:3000}")
    public void dispatchReady() {
        int recovered = mapper.recoverExpiredLeases();
        if (recovered > 0) {
            log.warn("回收超时 Outbox 事件: count={}", recovered);
        }
        int effectiveLeaseSeconds = Math.max(10, leaseSeconds);
        String batchOwnerId = ownerId + ":" + UUID.randomUUID();
        if (mapper.claimBatch(
                batchOwnerId,
                effectiveLeaseSeconds,
                Math.max(1, batchSize)) == 0) {
            return;
        }
        for (OutboxRecord claimed : mapper.selectClaimedBatch(
                batchOwnerId)) {
            if (claimed.getLeaseToken() == null) {
                continue;
            }
            try {
                executor.execute(() -> processor.process(
                        claimed.getId(),
                        batchOwnerId,
                        claimed.getLeaseToken(),
                        effectiveLeaseSeconds));
            } catch (RejectedExecutionException exception) {
                mapper.releaseClaim(
                        claimed.getId(),
                        batchOwnerId,
                        claimed.getLeaseToken());
                log.error(
                        "Outbox 执行队列已满，释放租约: id={}, topic={}",
                        claimed.getId(),
                        claimed.getTopic());
            }
        }
    }
}
