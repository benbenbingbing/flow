package com.workflow.outbox.application;

import com.workflow.outbox.infrastructure.persistence.mapper.OutboxRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 清理超过保留期的已完成 Outbox 事件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRetentionService {

    private final OutboxRecordMapper mapper;

    @Value("${workflow.outbox.retention-days:7}")
    private int retentionDays = 7;

    @Scheduled(cron = "${workflow.outbox.retention-cron:0 15 3 * * *}")
    @Transactional(rollbackFor = Exception.class)
    public void cleanup() {
        int deleted = mapper.deleteProcessedBefore(
                LocalDateTime.now().minusDays(
                        Math.max(1, retentionDays)));
        if (deleted > 0) {
            log.info("清理已完成 Outbox 事件: count={}", deleted);
        }
    }
}
