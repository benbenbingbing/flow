package com.workflow.process.action;

import com.workflow.dto.EntityDataDTO;
import lombok.Data;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;

import java.util.Map;

/**
 * 流程动作执行上下文。
 *
 * <p>平台在流程、节点、任务或顺序流事件触发时自动组装此对象，并传给
 * {@link FlowActionHandler#execute(FlowActionContext)}。
 * 除了固化标识字段外，还可以通过内置方法便捷地获取流程变量、实体数据、当前任务等运行时信息。</p>
 */
@Data
public class FlowActionContext {

    /**
     * 当前 flow_action 记录 ID
     */
    private String actionId;

    /**
     * 当前 flow_action 动作名称
     */
    private String actionName;

    /**
     * 流程实例 ID
     */
    private String processInstanceId;

    /**
     * 实体编码
     */
    private String entityCode;

    /**
     * 实体数据 ID
     */
    private String entityDataId;

    /**
     * 触发该动作的顺序流元素 ID
     */
    private String sequenceFlowId;

    /**
     * 顺序流源节点 ID
     */
    private String sourceNodeId;

    /**
     * 顺序流源节点名称
     */
    private String sourceNodeName;

    /**
     * 顺序流目标节点 ID
     */
    private String targetNodeId;

    /**
     * 顺序流目标节点名称
     */
    private String targetNodeName;

    private String triggerTiming;
    private String scopeType;
    private String elementId;
    private String elementName;
    private String elementType;
    private String processDefinitionId;
    private String executionId;
    private String taskId;
    private String taskName;
    private String taskAssignee;
    private String operatorId;
    private String approvalAction;
    private String endReason;
    private String idempotencyKey;
    private Map<String, Object> variablesSnapshot;

    /**
     * 前端 paramsJson 解析后的业务参数
     */
    private Map<String, Object> customParams;

    /**
     * 内部查询辅助器，不对外序列化
     */
    private transient FlowActionHelper helper;

    public Map<String, Object> getVariables() {
        if (variablesSnapshot != null && !variablesSnapshot.isEmpty()) {
            return variablesSnapshot;
        }
        return helper.getVariables(processInstanceId);
    }

    public Object getVariable(String name) {
        if (variablesSnapshot != null && variablesSnapshot.containsKey(name)) {
            return variablesSnapshot.get(name);
        }
        return helper.getVariable(processInstanceId, name);
    }

    public ProcessInstance getProcessInstance() {
        return helper.getProcessInstance(processInstanceId);
    }

    public HistoricProcessInstance getHistoricProcessInstance() {
        return helper.getHistoricProcessInstance(processInstanceId);
    }

    public Task getCurrentTask() {
        return helper.getCurrentTask(processInstanceId);
    }

    public Task getTriggerTask() {
        return helper.getTask(taskId);
    }

    public EntityDataDTO getEntityData() {
        return helper.getEntityData(entityCode, entityDataId);
    }

    public FlowActionHelper getHelper() {
        return helper;
    }
}
