package com.workflow.process.engine.infrastructure.flowable;

import com.workflow.process.status.application.ProcessEndReason;

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

        /**
         * 响应事件阶段的回调，并将结果传给后续处理。
         *
         * @param event 事件，作为 {@code processInstance} 的输入影响后续处理
         */
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
                        String statusCategory = ProcessEndReason.category(deleteReason);
                        if ("COMPLETED".equals(statusCategory) && eventType.contains("_WITH_")) {
                                statusCategory = "TERMINATED";
                        }

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

        /**
         * 处理实例，并将结果传给后续步骤。
         *
         * @param event 事件，供本方法处理实例时使用
         * @return 处理后的实例结果，供调用方继续处理
         */
        private ProcessInstance processInstance(FlowableEvent event) {
                if (event instanceof FlowableEntityEventImpl entityEvent
                                && entityEvent.getEntity()
                                                instanceof ProcessInstance instance) {
                        return instance;
                }
                return null;
        }

        /**
         * 生成实例ID文本，供后续匹配或展示。
         *
         * @param event 事件，供本方法处理实例ID时使用
         * @return 处理后的实例ID文本，供调用方比较或展示
         */
        private String processInstanceId(FlowableEvent event) {
                return event instanceof FlowableEngineEvent engineEvent
                                ? engineEvent.getProcessInstanceId()
                                : null;
        }

        /**
         * 生成默认结束状态文本，供后续匹配或展示。
         *
         * @param category 类别，决定后续状态或结果的归类
         * @return 处理后的默认结束状态文本，供调用方比较或展示
         */
        private String defaultEndStatus(String category) {
                if ("WITHDRAWN".equals(category)) {
                        return "WITHDRAWN";
                }
                return "TERMINATED".equals(category)
                                ? "TERMINATED"
                                : "APPROVED";
        }

        /**
         * 读取实体变量；查询结果供调用方展示或继续处理。
         *
         * @param instance 实例，供本方法读取实体变量时使用
         * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
         * @param variableName 变量名称，后续用于读取实体变量时匹配或展示
         * @return 读取后的实体变量文本，供调用方比较或展示
         */
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

        /**
         * 判断是否失败异常；判断结果决定调用方的后续分支。
         *
         * @return 失败异常条件成立时为 true，否则为 false
         */
        @Override
        public boolean isFailOnException() {
                return true;
        }

        /**
         * 判断是否{@code fire}事务生命周期事件；判断结果决定调用方的后续分支。
         *
         * @return {@code fire}事务生命周期事件条件成立时为 true，否则为 false
         */
        @Override
        public boolean isFireOnTransactionLifecycleEvent() {
                return false;
        }

        /**
         * 读取事务；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的事务文本，供调用方比较或展示
         */
        @Override
        public String getOnTransaction() {
                return null;
        }
}
