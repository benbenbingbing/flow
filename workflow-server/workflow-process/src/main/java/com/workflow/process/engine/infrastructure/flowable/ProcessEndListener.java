package com.workflow.process.engine.infrastructure.flowable;

import com.workflow.contracts.entity.mutation.EntityChangeTargetApplyCommand;
import com.workflow.contracts.entity.mutation.EntityChangeTargetPort;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

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
        private final ObjectProvider<EntityChangeTargetPort> changeTargetPortProvider;
        private final ProcessStatusSyncPublisher statusSyncPublisher;

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
                        String entityCode = getHistoricVariable(
                                        processInstanceId,
                                        "entityCode");
                        String entityDataId = getHistoricVariable(
                                        processInstanceId,
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
                        if ("COMPLETED".equals(statusCategory)
                                        && processInstance != null) {
                                applyChangeTargets(
                                                processInstance,
                                                historicInstance,
                                                entityCode,
                                                entityDataId,
                                                idempotencyKey);
                        }
                        statusSyncPublisher.publishProcessEnd(
                                        processInstanceId,
                                        entityCode,
                                        entityDataId,
                                        statusCategory,
                                        defaultEndStatus(statusCategory));
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

        private void applyChangeTargets(
                        ProcessInstance processInstance,
                        HistoricProcessInstance historicInstance,
                        String entityCode,
                        String entityDataId,
                        String processEndIdempotencyKey) {
                EntityChangeTargetPort port = changeTargetPortProvider.getIfAvailable();
                if (port == null) {
                        return;
                }
                port.apply(new EntityChangeTargetApplyCommand(
                                entityCode,
                                entityDataId,
                                processInstance.getProcessDefinitionId(),
                                processInstance.getId(),
                                null,
                                historicInstance == null
                                                ? null
                                                : historicInstance.getStartUserId(),
                                null,
                                EntityMutationSourceType.PROCESS_RUNTIME,
                                "CHANGE_EFFECTIVE",
                                "变更审批生效",
                                processEndIdempotencyKey
                                                + ":change-targets",
                                Map.of()));
        }

        private String defaultEndStatus(String category) {
                if ("WITHDRAWN".equals(category)) {
                        return "WITHDRAWN";
                }
                return "TERMINATED".equals(category)
                                ? "TERMINATED"
                                : "APPROVED";
        }

        private String getHistoricVariable(
                        String processInstanceId,
                        String variableName) {
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
