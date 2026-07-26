package com.workflow.system.audit.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.system.audit.domain.SystemAuditOutbox;
import com.workflow.system.audit.domain.SystemOperationLog;
import com.workflow.system.audit.infrastructure.SystemAuditOutboxMapper;
import com.workflow.system.audit.infrastructure.SystemOperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 按配置保留期清理审计日志和已完成 Outbox。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemAuditRetentionService {

    private final SystemOperationLogMapper operationLogMapper;
    private final SystemAuditOutboxMapper outboxMapper;

    @Value("${workflow.audit.retention-days:365}")
    private int retentionDays;

    @Scheduled(cron = "${workflow.audit.retention-cron:0 30 3 * * *}")
    @Transactional(rollbackFor = Exception.class)
    public void cleanup() {
        LocalDateTime logCutoff = LocalDateTime.now().minusDays(Math.max(1, retentionDays));
        int deletedLogs = operationLogMapper.delete(
                new LambdaQueryWrapper<SystemOperationLog>()
                        .lt(SystemOperationLog::getCreateTime, logCutoff));
        int deletedOutbox = outboxMapper.delete(
                new LambdaQueryWrapper<SystemAuditOutbox>()
                        .eq(SystemAuditOutbox::getStatus, "PROCESSED")
                        .lt(SystemAuditOutbox::getProcessedTime, LocalDateTime.now().minusDays(7)));
        if (deletedLogs > 0 || deletedOutbox > 0) {
            log.info("清理系统审计数据: logs={}, outbox={}", deletedLogs, deletedOutbox);
        }
    }
}
