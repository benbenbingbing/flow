package com.workflow.admin.audit.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.admin.audit.domain.SystemOperationLog;
import com.workflow.admin.audit.infrastructure.SystemOperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Value("${workflow.audit.retention-days:365}")
    private int retentionDays;

    @Scheduled(cron = "${workflow.audit.retention-cron:0 30 3 * * *}")
    @Transactional(rollbackFor = Exception.class)
    public void cleanup() {
        LocalDateTime logCutoff = LocalDateTime.now().minusDays(Math.max(1, retentionDays));
        int deletedLogs = operationLogMapper.delete(
                new LambdaQueryWrapper<SystemOperationLog>()
                        .lt(SystemOperationLog::getCreateTime, logCutoff));
        if (deletedLogs > 0) {
            log.info("清理系统审计日志: count={}", deletedLogs);
        }
    }
}
