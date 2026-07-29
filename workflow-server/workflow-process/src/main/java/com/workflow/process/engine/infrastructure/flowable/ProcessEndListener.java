package com.workflow.process.engine.infrastructure.flowable;

import com.workflow.contracts.entity.mutation.EntityChangeTargetApplyCommand;
import com.workflow.contracts.entity.mutation.EntityChangeTargetPort;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationPort;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.contracts.entity.mutation.EntityMutationSystemFields;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.HistoryService;
import org.flowable.engine.delegate.event.impl.FlowableEntityEventImpl;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 流程结束监听器。
 *
 * <p>监听流程完成、取消与终止事件，并通过实体运行时端口回写流程结束时间和状态。
 * 实体状态保留与替换规则由实体模块统一判断。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessEndListener implements FlowableEventListener {

    private final HistoryService historyService;
    private final EntityMutationPort entityMutationPort;
    private final ObjectProvider<EntityChangeTargetPort>
            changeTargetPortProvider;

    @Override
    public void onEvent(FlowableEvent event) {
        if (!(event instanceof FlowableEntityEventImpl entityEvent)) {
            return;
        }

        String eventType = entityEvent.getType() == null
                ? ""
                : entityEvent.getType().name();
        if (!"PROCESS_COMPLETED".equals(eventType)
                && !"PROCESS_CANCELLED".equals(eventType)) {
            return;
        }
        if (!(entityEvent.getEntity()
                instanceof ProcessInstance processInstance)) {
            return;
        }

        String processInstanceId = processInstance.getId();
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
            boolean withdrawn = deleteReason != null
                    && deleteReason.startsWith("发起人撤回");
            boolean terminated = deleteReason != null
                    && !deleteReason.isEmpty();
            String statusCategory = withdrawn
                    ? "WITHDRAWN"
                    : (terminated ? "TERMINATED" : "COMPLETED");

            String intentCode =
                    "COMPLETED".equals(statusCategory)
                            ? "INITIAL_EFFECTIVE"
                            : ("WITHDRAWN".equals(statusCategory)
                                    ? "PROCESS_WITHDRAWN"
                                    : "PROCESS_TERMINATED");
            String intentName =
                    "COMPLETED".equals(statusCategory)
                            ? "初始审批生效"
                            : ("WITHDRAWN".equals(statusCategory)
                                    ? "流程撤回"
                                    : "流程终止");
            String idempotencyKey = String.join(
                    ":",
                    "process-end",
                    processInstanceId,
                    statusCategory);
            entityMutationPort.execute(
                    new EntityMutationCommand(
                            idempotencyKey,
                            entityCode,
                            entityDataId,
                            EntityMutationOperationType.STATUS_CHANGE,
                            Map.of(
                                    EntityMutationSystemFields.MODE_KEY,
                                    EntityMutationSystemFields.PROCESS_END,
                                    "statusCategory",
                                    statusCategory,
                                    "fallbackStatus",
                                    defaultEndStatus(
                                            statusCategory)),
                            EntityMutationContext.builder(
                                            EntityMutationSourceType.PROCESS_RUNTIME,
                                            intentCode,
                                            intentName)
                                    .sourceId(eventType)
                                    .sourceRecord(
                                            entityCode,
                                            entityDataId)
                                    .process(
                                            processInstance
                                                    .getProcessDefinitionId(),
                                            processInstanceId,
                                            null)
                                    .trace(
                                            processInstanceId,
                                            idempotencyKey)
                                    .build()));
            if ("COMPLETED".equals(statusCategory)) {
                applyChangeTargets(
                        processInstance,
                        historicInstance,
                        entityCode,
                        entityDataId,
                        idempotencyKey);
            }
            log.info(
                    "流程结束，已更新实体数据: entityCode={}, entityDataId={}, "
                            + "processInstanceId={}, statusCategory={}",
                    entityCode,
                    entityDataId,
                    processInstanceId,
                    statusCategory);
        } catch (Exception exception) {
            log.error(
                    "更新流程结束状态失败: processInstanceId={}",
                    processInstanceId,
                    exception);
        }
    }

    private void applyChangeTargets(
            ProcessInstance processInstance,
            HistoricProcessInstance historicInstance,
            String entityCode,
            String entityDataId,
            String processEndIdempotencyKey) {
        EntityChangeTargetPort port =
                changeTargetPortProvider.getIfAvailable();
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
        try {
            var variable = historyService
                    .createHistoricVariableInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .variableName(variableName)
                    .singleResult();
            return variable == null ? null : (String) variable.getValue();
        } catch (Exception exception) {
            log.warn(
                    "查询历史变量失败: processInstanceId={}, variableName={}",
                    processInstanceId,
                    variableName,
                    exception);
            return null;
        }
    }

    @Override
    public boolean isFailOnException() {
        return false;
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
