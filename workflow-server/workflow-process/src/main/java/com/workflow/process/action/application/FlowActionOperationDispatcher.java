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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务操作的动作边界。由共用事务服务在实际步骤处触发，HTTP、SDK 和加签后台收口共用。
 * 不从引擎事件猜测业务操作；每阶段生成独立事件，提交后动作保留该阶段的输入快照。
 */
@Component
@RequiredArgsConstructor
public class FlowActionOperationDispatcher {
    private final FlowActionDispatcher dispatcher;
    private final RuntimeService runtime;
    private final HistoryService history;

    /** 构造任务操作上下文；本地子任务沿用源节点绑定，但拥有独立任务 ID 和专用时机。 */
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
        if ("VALIDATE".equals(timing.getOperationPhase())) {
            event.setVariables(ProcessValues.snapshot(event.getVariables()));
        }
        event.setEntityCode(string(event.getVariables().get("entityCode")));
        event.setEntityDataId(string(event.getVariables().get("entityDataId")));
        dispatcher.dispatch(event);
    }

    private String string(Object value) { return value == null ? null : String.valueOf(value); }
}
