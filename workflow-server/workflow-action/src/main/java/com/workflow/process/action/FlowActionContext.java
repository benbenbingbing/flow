package com.workflow.process.action;

import com.workflow.dto.EntityDataDTO;
import lombok.Data;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;

import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

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

    /** 触发时机编码，对应 {@link FlowActionTriggerTiming} */
    private String triggerTiming;
    /** 作用域类型：PROCESS、NODE、SEQUENCE_FLOW */
    private String scopeType;
    /** 绑定的 BPMN 元素 ID；流程级为空 */
    private String elementId;
    /** BPMN 元素名称 */
    private String elementName;
    /** BPMN 元素类型，如 userTask、sequenceFlow */
    private String elementType;
    /** Flowable 流程定义 ID */
    private String processDefinitionId;
    /** Flowable 执行实例 ID */
    private String executionId;
    /** 任务 ID（任务级事件携带） */
    private String taskId;
    /** 任务名称 */
    private String taskName;
    /** 任务办理人 */
    private String taskAssignee;
    /** 操作人 ID */
    private String operatorId;
    /** 审批动作，如同意/拒绝 */
    private String approvalAction;
    /** 流程结束原因（撤回、终止等） */
    private String endReason;
    /** 幂等键，防止同一动作重复执行 */
    private String idempotencyKey;
    /** 触发时的流程变量快照 */
    private Map<String, Object> variablesSnapshot;

    /**
     * 前端 paramsJson 解析后的业务参数
     */
    private Map<String, Object> customParams;

    /**
     * Handler 可选写入的业务执行结果，最终展示在超级管理员执行日志中。
     */
    private Object executionResult;

    /**
     * Handler 可选追加的业务步骤；平台步骤会与这里的内容合并保存。
     */
    private List<Map<String, Object>> executionTrace = new ArrayList<>();

    /**
     * 内部查询辅助器，不对外序列化
     */
    private transient FlowActionHelper helper;

    /**
     * 获取流程变量快照；快照为空时回退到运行时查询。
     *
     * @return 流程变量 map
     */
    public Map<String, Object> getVariables() {
        if (variablesSnapshot != null && !variablesSnapshot.isEmpty()) {
            return variablesSnapshot;
        }
        return helper.getVariables(processInstanceId);
    }

    /**
     * 获取单个流程变量值，优先读快照。
     *
     * @param name 变量名
     * @return 变量值；不存在返回 null
     */
    public Object getVariable(String name) {
        if (variablesSnapshot != null && variablesSnapshot.containsKey(name)) {
            return variablesSnapshot.get(name);
        }
        return helper.getVariable(processInstanceId, name);
    }

    /**
     * 获取运行中的流程实例。
     *
     * @return 运行中的流程实例；不存在返回 null
     */
    public ProcessInstance getProcessInstance() {
        return helper.getProcessInstance(processInstanceId);
    }

    /**
     * 获取历史流程实例（含已结束的流程）。
     *
     * @return 历史流程实例；不存在返回 null
     */
    public HistoricProcessInstance getHistoricProcessInstance() {
        return helper.getHistoricProcessInstance(processInstanceId);
    }

    /**
     * 获取流程实例当前活动中的第一个待办任务。
     *
     * @return 当前待办任务；无则返回 null
     */
    public Task getCurrentTask() {
        return helper.getCurrentTask(processInstanceId);
    }

    /**
     * 获取触发该动作的任务（按 taskId 查询）。
     *
     * @return 触发任务；无则返回 null
     */
    public Task getTriggerTask() {
        return helper.getTask(taskId);
    }

    /**
     * 获取当前实体数据。
     *
     * @return 实体数据 DTO
     */
    public EntityDataDTO getEntityData() {
        return helper.getEntityData(entityCode, entityDataId);
    }

    /** 获取内部查询辅助器 */
    public FlowActionHelper getHelper() {
        return helper;
    }

    /**
     * 追加一条执行轨迹（无详情）。
     *
     * @param stage   阶段标识
     * @param message 阶段说明
     */
    public void addExecutionTrace(String stage, String message) {
        addExecutionTrace(stage, message, null);
    }

    /**
     * 追加一条执行轨迹（含详情）。
     *
     * @param stage   阶段标识
     * @param message 阶段说明
     * @param details 阶段详情；可为 null
     */
    public void addExecutionTrace(String stage, String message, Object details) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("stage", stage);
        item.put("message", message);
        if (details != null) {
            item.put("details", details);
        }
        executionTrace.add(item);
    }
}
