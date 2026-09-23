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

/** 定时认领到期的 SLA 事件，并把每个事件交给独立事务处理器执行。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskSlaEventWorker {

    private final ProcessTaskSlaEventMapper eventMapper;
    private final ProcessTaskSlaMapper slaMapper;
    private final TaskSlaEventProcessor processor;
    private final TaskSlaRuntimeService runtimeService;
    /** 本工作器实例的租约身份，认领及写回必须保持一致。 */
    private final String ownerId = "task-sla-" + UUID.randomUUID();

    @Value("${workflow.task-sla.batch-size:50}")
    private int batchSize;

    @Value("${workflow.task-sla.lease-seconds:120}")
    private int leaseSeconds;

    /**
 * 每轮先恢复达到暂停上限的任务，再回收过期租约并认领待执行事件。
 * claim 失败代表其他实例抢先取得租约，不应重复执行动作。
 */
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

    /** 分批恢复超出暂停上限的任务，单条失败只记录日志，不阻断其他任务和事件。 */
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
