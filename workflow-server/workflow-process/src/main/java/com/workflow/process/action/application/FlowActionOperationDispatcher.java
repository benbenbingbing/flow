package com.workflow.process.action.application;

import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.process.action.model.FlowActionTriggerTiming;
import com.workflow.contracts.process.model.ProcessValues;
import com.workflow.process.action.domain.FlowActionTriggerEvent;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.HistoryService;
import org.flowable.task.api.Task;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 业务操作的动作边界。由共用事务服务在实际步骤处触发，HTTP、SDK 和加签后台收口共用。
 * 人工操作由业务服务明确标记，原生发起和流程结束按引擎结果收口；提交后动作保留该阶段的输入快照。
 */
@Component
@RequiredArgsConstructor
public class FlowActionOperationDispatcher {
    private final FlowActionDispatcher dispatcher;
    private final RuntimeService runtime;
    private final HistoryService history;

    /** 构造任务操作上下文；本地子任务沿用源节点绑定及统一审批时机，以独立任务 ID、taskKind 和源任务标识区分。 */
    public FlowActionTriggerEvent task(Task source, String taskId, String operation, String operator,
                                       Map<String, Object> form, Map<String, Object> parameters) {
        var event = process(source.getProcessDefinitionId(), source.getProcessInstanceId(), operation, operator, null, parameters);
        event.setScopeType("NODE");
        event.setElementId(source.getTaskDefinitionKey());
        event.setElementName(source.getName());
        event.setElementType("userTask");
        event.setExecutionId(source.getExecutionId());
        event.setTaskId(taskId);
        event.setTaskName(source.getName());
        event.setTaskAssignee(source.getAssignee());
        boolean child = !java.util.Objects.equals(taskId, source.getId());
        event.setTaskKind(child ? "LOCAL_ADD_SIGN" : "ENGINE");
        event.setSourceTaskId(child ? source.getId() : null);
        event.setSubmittedFormData(ProcessValues.snapshot(form));
        return event;
    }

    /** 发起前实例 ID 为空；撤回/终止须在删除实例前取得变量，结束后继续使用最后的有效快照。 */
    public FlowActionTriggerEvent process(String definitionId, String instanceId, String operation, String operator,
                                          Map<String, Object> variables, Map<String, Object> parameters) {
        var event = new FlowActionTriggerEvent();
        event.setProcessDefinitionId(definitionId);
        event.setProcessInstanceId(instanceId);
        event.setScopeType("PROCESS");
        event.setBusinessOperation(operation);
        boolean currentActor = operator != null && (operator.equals(UserContext.getUserId())
                || operator.equals(UserContext.getUsername()));
        event.setOperatorId(currentActor && UserContext.getUserId() != null ? UserContext.getUserId() : operator);
        // 加签后台收口沿用原提交人，不能把当前子任务办理人的用户名配给原提交人 ID。
        event.setOperatorName(currentActor ? UserContext.getUsername() : operator);
        event.setVariables(new LinkedHashMap<>(variables == null ? Map.of() : variables));
        event.setOperationParameters(ProcessValues.snapshot(parameters));
        return event;
    }

    /**
     * 在调用点同步分发。前置时机被约束为事务内回滚；后置时机可选择同事务处理或 Outbox 通知。
     * 发起准备阶段共享尚未提交引擎的变量容器，其他阶段按当前运行态重新取得独立快照。
     */
    public void fire(FlowActionTriggerEvent operation, FlowActionTriggerTiming timing) {
        if (timing == FlowActionTriggerTiming.PROCESS_START_EXECUTED
                && TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            var pending = lifecycleActions();
            // 业务入口与原生启动登记共用一次发起后；先标记，避免动作重入或重复回调再次执行。
            if (!pending.started.add(operation.getProcessInstanceId())) return;
            pending.starts.remove(operation.getProcessInstanceId());
        }
        if (operation.getProcessInstanceId() != null) {
            try {
                operation.setVariables(new LinkedHashMap<>(runtime.getVariables(operation.getProcessInstanceId())));
            } catch (FlowableObjectNotFoundException ended) {
                // 最后任务完成/实例删除后读取最终根变量；任务本地和会签分支同名变量不能覆盖根值。
                var variables = new LinkedHashMap<String, Object>();
                history.createHistoricVariableInstanceQuery().processInstanceId(operation.getProcessInstanceId()).list()
                        .stream().filter(value -> value.getTaskId() == null
                                && operation.getProcessInstanceId().equals(value.getExecutionId()))
                        .forEach(value -> variables.put(value.getVariableName(), value.getValue()));
                operation.setVariables(variables);
            }
        }
        var event = new FlowActionTriggerEvent();
        BeanUtils.copyProperties(operation, event);
        event.setTriggerTiming(timing.name());
        if (operation.getProcessInstanceId() != null) {
            event.setVariables(new LinkedHashMap<>(operation.getVariables()));
        }
        event.setEntityCode(string(event.getVariables().get("entityCode")));
        event.setEntityDataId(string(event.getVariables().get("entityDataId")));
        dispatcher.dispatch(event);
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && (timing == FlowActionTriggerTiming.PROCESS_WITHDRAWING
                || timing == FlowActionTriggerTiming.PROCESS_TERMINATING)) {
            // 保留显式命令的实际操作人及原始输入，不能在结束时误取最后审批人的身份。
            lifecycleActions().operations.put(operation.getProcessInstanceId(), operation);
        }
    }

    /**
     * 登记真实引擎发起，覆盖不经过发起命令服务的调用子流程。
     * 普通发起在关联和待办同步后由 fire 消费此登记；其他实例在提交前补齐，不提前到首节点执行前。
     */
    public void processStarted(FlowActionTriggerEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("流程发起动作必须在业务事务内登记");
        }
        var pending = lifecycleActions();
        if (!pending.started.contains(event.getProcessInstanceId())) {
            pending.starts.putIfAbsent(event.getProcessInstanceId(), event);
        }
    }

    /**
     * 登记真实的流程结束事件；人工命令、系统删除及 BPMN 自动结束共用唯一后置出口。
     * 在外层事务提交前执行，等待引擎调用后的日志、镜像和其他同步业务写入完成。
     * 同步处理器采用回滚策略时异常仍会回滚整个事务，AFTER_COMMIT 只在此时同事务入队。
     */
    public void processEnded(FlowActionTriggerEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("流程结束动作必须在业务事务内登记");
        }
        var pending = lifecycleActions();
        // terminate/error 等结束事件可能同时伴随普通完成事件；同一实例只保留最终结束类型。
        var existing = pending.events.get(event.getProcessInstanceId());
        if (existing == null || !FlowActionTriggerTiming.PROCESS_COMPLETED.name().equals(event.getTriggerTiming())) {
            pending.events.put(event.getProcessInstanceId(), event);
        }
    }

    /** 使用事务同步列表定位收口器；REQUIRES_NEW 挂起外层同步，避免跨事务串用上下文。 */
    private LifecycleActions lifecycleActions() {
        for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            if (synchronization instanceof LifecycleActions pending && pending.owner == this) return pending;
        }
        var pending = new LifecycleActions();
        TransactionSynchronizationManager.registerSynchronization(pending);
        return pending;
    }

    /** 每个事务、每个实例的发起后和结束后分别只分发一次；先补齐发起后，再收口结束。 */
    private final class LifecycleActions implements TransactionSynchronization {
        private final FlowActionOperationDispatcher owner = FlowActionOperationDispatcher.this;
        private final Map<String, FlowActionTriggerEvent> starts = new LinkedHashMap<>();
        private final Set<String> started = new HashSet<>();
        private final Map<String, FlowActionTriggerEvent> events = new LinkedHashMap<>();
        private final Map<String, FlowActionTriggerEvent> operations = new LinkedHashMap<>();
        private final Set<String> dispatched = new HashSet<>();

        @Override
        public void beforeCommit(boolean readOnly) {
            while (!starts.isEmpty() || !events.isEmpty()) {
                if (!starts.isEmpty()) {
                    var start = starts.entrySet().iterator().next();
                    starts.remove(start.getKey());
                    start.getValue().setBusinessOperation("START");
                    fire(start.getValue(), FlowActionTriggerTiming.PROCESS_START_EXECUTED);
                    continue;
                }
                var entry = events.entrySet().iterator().next();
                events.remove(entry.getKey());
                if (dispatched.contains(entry.getKey())) continue;
                // 局部作用域终止不能当成整个实例结束；后续真正结束仍可重新登记该实例。
                if (runtime.createProcessInstanceQuery().processInstanceId(entry.getKey()).count() > 0) continue;
                dispatched.add(entry.getKey());
                var event = entry.getValue();
                var timing = FlowActionTriggerTiming.valueOf(event.getTriggerTiming());
                var operation = operations.get(entry.getKey());
                if (operation == null) {
                    operation = event;
                    operation.setBusinessOperation(switch (timing) {
                        case PROCESS_WITHDRAWN -> "WITHDRAW";
                        case PROCESS_TERMINATED -> "TERMINATE";
                        default -> "COMPLETE";
                    });
                    var parameters = new LinkedHashMap<String, Object>();
                    parameters.put("reason", event.getEndReason());
                    operation.setOperationParameters(parameters);
                }
                operation.setEndReason(event.getEndReason());
                // 此时运行实例已退出，fire 读取最终历史根变量，不使用引擎回调中的中间快照。
                fire(operation, timing);
            }
        }
    }

    private String string(Object value) { return value == null ? null : String.valueOf(value); }
}
