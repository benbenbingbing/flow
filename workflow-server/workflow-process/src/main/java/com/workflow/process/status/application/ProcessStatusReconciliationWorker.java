package com.workflow.process.status.application;

import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.instance.infrastructure.persistence.record.EntityProcessLink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessStatusReconciliationWorker {

    private final EntityProcessLinkMapper entityProcessLinkMapper;
    private final HistoryService historyService;
    private final ProcessStatusSyncPublisher publisher;

    @Value("${workflow.status-sync.reconciliation-batch-size:100}")
    private int batchSize = 100;

    @Scheduled(
            initialDelayString =
                    "${workflow.status-sync.reconciliation-initial-delay-ms:60000}",
            fixedDelayString =
                    "${workflow.status-sync.reconciliation-delay-ms:60000}")
    public void reconcileEndedProcesses() {
        for (EntityProcessLink link :
                entityProcessLinkMapper.findEndedActiveForReconciliation(
                        Math.max(1, batchSize))) {
            try {
                reconcile(link);
            } catch (RuntimeException exception) {
                log.error(
                        "流程结束状态对账失败: processInstanceId={}",
                        link.getProcessInstanceId(),
                        exception);
            }
        }
    }

    private void reconcile(EntityProcessLink link) {
        HistoricProcessInstance historic = historyService
                .createHistoricProcessInstanceQuery()
                .processInstanceId(link.getProcessInstanceId())
                .finished()
                .singleResult();
        if (historic == null) {
            return;
        }
        String category = category(historic.getDeleteReason());
        publisher.republishProcessEnd(
                link.getProcessInstanceId(),
                link.getEntityCode(),
                link.getEntityRecordId(),
                category,
                fallbackStatus(category));
        log.warn("补发流程结束状态同步事件: processInstanceId={}",
                link.getProcessInstanceId());
    }

    private String category(String deleteReason) {
        if (deleteReason != null
                && deleteReason.startsWith("发起人撤回")) {
            return "WITHDRAWN";
        }
        return deleteReason != null && !deleteReason.isBlank()
                ? "TERMINATED"
                : "COMPLETED";
    }

    private String fallbackStatus(String category) {
        return switch (category) {
            case "WITHDRAWN" -> "WITHDRAWN";
            case "TERMINATED" -> "TERMINATED";
            default -> "APPROVED";
        };
    }
}
