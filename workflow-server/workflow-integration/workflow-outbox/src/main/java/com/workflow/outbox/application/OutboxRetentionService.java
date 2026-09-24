package com.workflow.outbox.application;

import com.workflow.outbox.infrastructure.persistence.mapper.OutboxRecordMapper;
import com.workflow.core.database.BoundedRetentionRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 清理超过保留期的已完成 Outbox 事件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRetentionService {

    private final OutboxRecordMapper mapper;
    private final BoundedRetentionRunner retentionRunner;

    @Value("${workflow.outbox.retention-days:7}")
    private int retentionDays = 7;

    @Value("${workflow.outbox.retention-batch-size:1000}")
    private int batchSize = 1_000;
    @Value("${workflow.outbox.retention-max-rows:10000}")
    private int maxRows = 10_000;
    @Value("${workflow.outbox.retention-max-seconds:10}")
    private int maxSeconds = 10;

    /**
     * 与其他实例互斥，每批独立提交，只清理超过保留期的 PROCESSED 事件。
     */
    @Scheduled(cron = "${workflow.outbox.retention-cron:0 15 3 * * *}")
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, retentionDays));
        int deleted = retentionRunner.run("outbox", batchSize, maxRows, maxSeconds,
                limit -> mapper.deleteProcessedBatchBefore(cutoff, limit));
        if (deleted > 0) {
            log.info("清理已完成 Outbox 事件: count={}", deleted);
        }
    }
}
