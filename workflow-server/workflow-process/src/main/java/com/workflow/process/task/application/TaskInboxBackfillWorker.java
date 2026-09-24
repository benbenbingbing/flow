package com.workflow.process.task.application;

import com.workflow.process.task.infrastructure.persistence.mapper.TaskInboxProjectionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 有界、可重跑的存量回填；未完成的用户范围仍由查询服务走旧路径，不阻塞应用启动。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskInboxBackfillWorker {
    private final TaskInboxProjectionMapper mapper;
    private final TaskInboxProjectionService projection;
    @Value("${workflow.task-inbox.backfill-enabled:true}")
    private boolean enabled = true;
    private long afterId;

    /** 每轮至多处理 100 个任务，每条独立事务；失败留在未就绪集合，下轮扫描可重试。 */
    @Scheduled(initialDelayString = "${workflow.task-inbox.backfill-initial-delay-ms:5000}",
            fixedDelayString = "${workflow.task-inbox.backfill-delay-ms:10000}")
    public synchronized void backfillBatch() {
        if (!enabled) return;
        var ids = mapper.findUnready(afterId, 100);
        if (ids.isEmpty()) { afterId = 0; return; }
        for (Long id : ids) {
            try {
                projection.backfill(id);
            } catch (RuntimeException failure) {
                log.warn("任务收件箱回填失败，保留旧查询: taskId={}, failureType={}", id, failure.getClass().getSimpleName());
            }
            afterId = id;
        }
    }
}
