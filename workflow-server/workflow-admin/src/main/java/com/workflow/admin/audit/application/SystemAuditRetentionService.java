package com.workflow.admin.audit.application;

import com.workflow.admin.audit.infrastructure.persistence.mapper.SystemOperationLogMapper;
import com.workflow.core.database.BoundedRetentionRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 按配置保留期清理审计日志。
 *
 * <p>通用 Outbox 的保留期由 workflow-outbox 独立维护。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemAuditRetentionService {

    private final SystemOperationLogMapper operationLogMapper;
    private final BoundedRetentionRunner retentionRunner;

    @Value("${workflow.audit.retention-days:365}")
    private int retentionDays;

    @Value("${workflow.audit.retention-batch-size:1000}")
    private int batchSize = 1_000;
    @Value("${workflow.audit.retention-max-rows:10000}")
    private int maxRows = 10_000;
    @Value("${workflow.audit.retention-max-seconds:10}")
    private int maxSeconds = 10;

    /**
     * 固定本轮截止时间，按有界批次清理；事务由 runner 每批创建，调度入口不能包裹整轮事务。
     */
    @Scheduled(cron = "${workflow.audit.retention-cron:0 30 3 * * *}")
    public void cleanup() {
        LocalDateTime logCutoff = LocalDateTime.now().minusDays(Math.max(1, retentionDays));
        int deletedLogs = retentionRunner.run("system-audit", batchSize, maxRows, maxSeconds,
                limit -> operationLogMapper.deleteExpiredBatch(logCutoff, limit));
        if (deletedLogs > 0) {
            log.info("清理系统审计日志: count={}", deletedLogs);
        }
    }
}
