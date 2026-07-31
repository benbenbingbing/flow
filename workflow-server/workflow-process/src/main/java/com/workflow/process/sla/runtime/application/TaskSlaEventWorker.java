package com.workflow.process.sla.runtime.application;

import com.workflow.process.sla.runtime.infrastructure.persistence.mapper.ProcessTaskSlaEventMapper;
import com.workflow.process.sla.runtime.infrastructure.persistence.mapper.ProcessTaskSlaMapper;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSlaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskSlaEventWorker {

    private final ProcessTaskSlaEventMapper eventMapper;
    private final ProcessTaskSlaMapper slaMapper;
    private final TaskSlaEventProcessor processor;
    private final TaskSlaRuntimeService runtimeService;
    private final String ownerId = "task-sla-" + UUID.randomUUID();

    @Value("${workflow.task-sla.batch-size:50}")
    private int batchSize;

    @Value("${workflow.task-sla.lease-seconds:120}")
    private int leaseSeconds;

    @Scheduled(fixedDelayString =
            "${workflow.task-sla.poll-delay-ms:5000}")
    public void poll() {
        resumeExpiredPauses();
        int recovered = eventMapper.recoverExpiredLeases();
        if (recovered > 0) {
            log.warn("恢复 {} 条中断的SLA事件", recovered);
        }
        int effectiveLease = Math.max(30, leaseSeconds);
        for (ProcessTaskSlaEvent event :
                eventMapper.findReady(Math.max(1, batchSize))) {
            if (eventMapper.claim(
                    event.getId(),
                    ownerId,
                    effectiveLease) == 0) {
                continue;
            }
            ProcessTaskSlaEvent claimed =
                    eventMapper.selectClaimed(event.getId(), ownerId);
            if (claimed != null && claimed.getLeaseToken() != null) {
                processor.process(
                        claimed.getId(),
                        ownerId,
                        claimed.getLeaseToken());
            }
        }
    }

    private void resumeExpiredPauses() {
        for (var sla : slaMapper.findPaused(Math.max(1, batchSize))) {
            try {
                runtimeService.resumeIfPauseExpired(sla.getTaskId());
            } catch (Exception exception) {
                log.warn(
                        "自动恢复达到上限的SLA暂停失败: taskId={}",
                        sla.getTaskId(),
                        exception);
            }
        }
    }
}
