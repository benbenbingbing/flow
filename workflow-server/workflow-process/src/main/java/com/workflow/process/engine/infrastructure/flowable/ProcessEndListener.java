package com.workflow.process.engine.infrastructure.flowable;

import com.workflow.process.status.application.ProcessStatusSyncPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEvent;
import org.flowable.engine.HistoryService;
import org.flowable.engine.delegate.event.FlowableCancelledEvent;
import org.flowable.engine.delegate.event.impl.FlowableEntityEventImpl;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;

/**
 * 流程结束监听器。
 *
 * <p>
 * 监听流程完成、取消与终止事件，在 Flowable 事务中写入状态同步 Outbox。
 * 实体结束状态由提交后的异步处理器更新，避免与节点完成动作在同一实体行上
 * 发生跨事务锁等待；对账任务与消费端使用相同幂等键，可安全补偿重放。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessEndListener implements FlowableEventListener {

        private final HistoryService historyService;
        private final ProcessStatusSyncPublisher statusSyncPublisher;

        @org.springframework.beans.factory.annotation.Autowired
        private com.workflow.process.status.application.ProcessEntityStatusPolicy statusPolicy;

        @Override
        public void onEvent(FlowableEvent event) {
                String eventType = event.getType() == null
                                ? ""
                                : event.getType().name();
                if (!"PROCESS_COMPLETED".equals(eventType)
                                && !"PROCESS_CANCELLED".equals(eventType)
                                && !"PROCESS_COMPLETED_WITH_TERMINATE_END_EVENT".equals(eventType)
                                && !"PROCESS_COMPLETED_WITH_ERROR_END_EVENT".equals(eventType)
                                && !"PROCESS_COMPLETED_WITH_ESCALATION_END_EVENT".equals(eventType)) {
                        return;
                }
                ProcessInstance processInstance = processInstance(event);
                String processInstanceId = processInstance == null
                                ? processInstanceId(event)
                                : processInstance.getId();
                if (processInstanceId == null || processInstanceId.isBlank()) {
                        return;
                }

                try {
                        String entityCode = getEntityVariable(
                                        processInstance, processInstanceId,
                                        "entityCode");
                        String entityDataId = getEntityVariable(
                                        processInstance, processInstanceId,
                                        "entityDataId");
                        if (entityCode == null || entityDataId == null) {
                                log.debug(
                                                "流程未关联实体数据: processInstanceId={}",
                                                processInstanceId);
                                return;
                        }

                        HistoricProcessInstance historicInstance = historyService
                                        .createHistoricProcessInstanceQuery()
                                        .processInstanceId(processInstanceId)
                                        .singleResult();
                        String deleteReason = historicInstance == null
                                        ? null
                                        : historicInstance.getDeleteReason();
                        if (event instanceof FlowableCancelledEvent cancelledEvent
                                        && cancelledEvent.getCause() != null) {
                                deleteReason = String.valueOf(
                                                cancelledEvent.getCause());
                        }
                        boolean withdrawn = deleteReason != null
                                        && deleteReason.contains("撤回");
                        boolean terminated = (deleteReason != null
                                        && !deleteReason.isEmpty())
                                        || eventType.contains("_WITH_");
                        String statusCategory = withdrawn
                                        ? "WITHDRAWN"
                                        : (terminated ? "TERMINATED" : "COMPLETED");

                        String idempotencyKey = String.join(
                                        ":",
                                        "process-end",
                                        processInstanceId,
                                        statusCategory);
                        statusSyncPublisher.publishProcessEnd(
                                        processInstanceId,
                                        entityCode,
                                        entityDataId,
                                        statusCategory,
                                        // BPMN 终止/错误结束仍是经过连线的结束，不属于人工取消操作。
                                        (deleteReason == null || deleteReason.isBlank()) && statusPolicy != null && statusPolicy.usesTransitions(
                                                processInstance != null ? processInstance.getProcessDefinitionId()
                                                        : ((FlowableEngineEvent) event).getProcessDefinitionId())
                                                ? null : defaultEndStatus(statusCategory));
                        log.info(
                                        "流程结束状态同步事件已入队，等待提交后消费: entityCode={}, entityDataId={}, "
                                                        + "processInstanceId={}, statusCategory={}, idempotencyKey={}",
                                        entityCode,
                                        entityDataId,
                                        processInstanceId,
                                        statusCategory,
                                        idempotencyKey);
                } catch (Exception exception) {
                        throw new IllegalStateException(
                                        "流程结束状态同步事件入队失败: processInstanceId="
                                                        + processInstanceId,
                                        exception);
                }
        }

        private ProcessInstance processInstance(FlowableEvent event) {
                if (event instanceof FlowableEntityEventImpl entityEvent
                                && entityEvent.getEntity()
                                                instanceof ProcessInstance instance) {
                        return instance;
                }
                return null;
        }

        private String processInstanceId(FlowableEvent event) {
                return event instanceof FlowableEngineEvent engineEvent
                                ? engineEvent.getProcessInstanceId()
                                : null;
        }

        private String defaultEndStatus(String category) {
                if ("WITHDRAWN".equals(category)) {
                        return "WITHDRAWN";
                }
                return "TERMINATED".equals(category)
                                ? "TERMINATED"
                                : "APPROVED";
        }

        private String getEntityVariable(
                        ProcessInstance instance,
                        String processInstanceId,
                        String variableName) {
                // 发起后立即结束的实例，历史变量可能尚未刷入数据库；优先读取事件中的执行作用域。
                if (instance instanceof org.flowable.variable.api.delegate.VariableScope scope) {
                        Object value = scope.getVariable(variableName);
                        if (value != null) return value.toString();
                }
                var variable = historyService
                                .createHistoricVariableInstanceQuery()
                                .processInstanceId(processInstanceId)
                                .variableName(variableName)
                                .singleResult();
                return variable == null ? null : (String) variable.getValue();
        }

        @Override
        public boolean isFailOnException() {
                return true;
        }

        @Override
        public boolean isFireOnTransactionLifecycleEvent() {
                return false;
        }

        @Override
        public String getOnTransaction() {
                return null;
        }
}
