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

/**
 * 负责流程状态{@code reconciliation}{@code worker}的业务处理；协调校验、状态变化及后续结果传递。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessStatusReconciliationWorker {

    private final EntityProcessLinkMapper entityProcessLinkMapper;
    private final HistoryService historyService;
    private final ProcessStatusSyncPublisher publisher;
    @org.springframework.beans.factory.annotation.Autowired
    private ProcessEntityStatusPolicy statusPolicy;

    @Value("${workflow.status-sync.reconciliation-batch-size:100}")
    private int batchSize = 100;

    /**
     * 对账结束{@code processes}；结果供调用方的后续步骤使用。
     */
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

    /**
     * 对账流程状态{@code reconciliation}{@code worker}；结果供调用方的后续步骤使用。
     *
     * @param link 链接，作为 {@code publisher.republishProcessEnd} 的输入影响后续处理
     */
    private void reconcile(EntityProcessLink link) {
        HistoricProcessInstance historic = historyService
                .createHistoricProcessInstanceQuery()
                .processInstanceId(link.getProcessInstanceId())
                .finished()
                .singleResult();
        if (historic == null) {
            return;
        }
        String category = statusPolicy == null ? category(historic.getDeleteReason()) : statusPolicy.endCategory(historic);
        publisher.republishProcessEnd(
                link.getProcessInstanceId(),
                link.getEntityCode(),
                link.getEntityRecordId(),
                category,
                (historic.getDeleteReason() == null || historic.getDeleteReason().isBlank())
                        && statusPolicy != null && statusPolicy.usesTransitions(historic.getProcessDefinitionId())
                        ? null : fallbackStatus(category));
        log.warn("补发流程结束状态同步事件: processInstanceId={}",
                link.getProcessInstanceId());
    }

    /**
     * 生成类别文本，供后续匹配或展示。
     *
     * @param deleteReason 删除原因，供本方法处理类别时使用
     * @return 处理后的类别文本，供调用方比较或展示
     */
    private String category(String deleteReason) {
        if (deleteReason != null
                && deleteReason.startsWith("发起人撤回")) {
            return "WITHDRAWN";
        }
        return deleteReason != null && !deleteReason.isBlank()
                ? "TERMINATED"
                : "COMPLETED";
    }

    /**
     * 生成兜底状态文本，供后续匹配或展示。
     *
     * @param category 类别，决定后续状态或结果的归类
     * @return 处理后的兜底状态文本，供调用方比较或展示
     */
    private String fallbackStatus(String category) {
        return switch (category) {
            case "WITHDRAWN" -> "WITHDRAWN";
            case "TERMINATED" -> "TERMINATED";
            default -> "APPROVED";
        };
    }
}
