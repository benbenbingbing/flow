package com.workflow.process.action.infrastructure.flowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.process.action.port.FlowActionRuntimePort;
import com.workflow.contracts.entity.model.EntityRecordData;
import com.workflow.contracts.entity.port.EntityRecordQueryPort;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 流程动作查询辅助器。
 *
 * <p>封装根据流程实例 ID、实体编码等核心标识查询运行时数据的常用方法。</p>
 */
@Component
@RequiredArgsConstructor
public class FlowActionRuntimeAdapter implements FlowActionRuntimePort {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final EntityRecordQueryPort entityDataDynamicService;
    private final ObjectMapper objectMapper;

    /**
     * 查询流程实例全部变量，优先从运行时获取，运行实例已结束时回退到历史变量。
     *
     * @param processInstanceId 流程实例 ID
     * @return 流程变量 map
     */
    @Override
    public Map<String, Object> getVariables(String processInstanceId) {
        try {
            Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
            if (variables != null && !variables.isEmpty()) {
                return variables;
            }
        } catch (Exception ignored) {
        }
        // 运行实例已结束或查询失败，回退到历史变量
        Map<String, Object> variables = new LinkedHashMap<>();
        historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .list()
                .forEach(variable -> variables.put(variable.getVariableName(), variable.getValue()));
        return variables;
    }

    /**
     * 查询单个流程变量值。
     *
     * @param processInstanceId 流程实例 ID
     * @param name              变量名
     * @return 变量值；不存在返回 null
     */
    @Override
    public Object getVariable(String processInstanceId, String name) {
        Map<String, Object> variables = getVariables(processInstanceId);
        return variables.get(name);
    }

    /**
     * 设置变量；后续读取或执行将使用更新后的状态。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param name 名称，后续用于设置变量时匹配或展示
     * @param value 待设置变量的原始输入，结果供调用方继续使用
     */
    @Override
    public void setVariable(
            String processInstanceId,
            String name,
            Object value) {
        runtimeService.setVariable(
                processInstanceId, name, value);
    }

    /**
     * 设置流程变量；后续读取或执行将使用更新后的状态。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param variables 流程变量，后续传给流程引擎或规则求值器使用
     */
    @Override
    public void setVariables(
            String processInstanceId,
            Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return;
        }
        runtimeService.setVariables(
                processInstanceId, variables);
    }

    /**
     * 查询运行中的流程实例。
     *
     * @param processInstanceId 流程实例 ID
     * @return 运行中的流程实例；不存在返回 null
     */
    @Override
    public ProcessInstance getProcessInstance(String processInstanceId) {
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
    }

    /**
     * 查询历史流程实例（含已结束的流程）。
     *
     * @param processInstanceId 流程实例 ID
     * @return 历史流程实例；不存在返回 null
     */
    @Override
    public HistoricProcessInstance getHistoricProcessInstance(String processInstanceId) {
        return historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
    }

    /**
     * 查询流程实例当前活动中的第一个待办任务。
     *
     * @param processInstanceId 流程实例 ID
     * @return 当前待办任务；无则返回 null
     */
    @Override
    public Task getCurrentTask(String processInstanceId) {
        return taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .list()
                .stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * 按任务 ID 查询任务详情。
     *
     * @param taskId 任务 ID
     * @return 任务对象；ID 为空或不存在返回 null
     */
    @Override
    public Task getTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        return taskService.createTaskQuery().taskId(taskId).singleResult();
    }

    /**
     * 按实体编码与数据 ID 查询实体数据。
     *
     * @param entityCode   实体编码
     * @param entityDataId 实体数据 ID
     * @return 经宿主读取策略校验后的业务记录投影，不含 HTTP 表单令牌和按钮能力
     */
    @Override
    public EntityRecordData getEntityData(String entityCode, String entityDataId) {
        return entityDataDynamicService.findById(entityCode, entityDataId);
    }

    /**
     * 将业务参数 map 转换为目标类型实例。
     *
     * @param <T>         目标类型
     * @param extraParams 业务参数 map；为 null 时使用空 map
     * @param targetType  目标类型 Class
     * @return 转换后的参数实例
     */
    @Override
    public <T> T convertParams(Map<String, Object> extraParams, Class<T> targetType) {
        if (extraParams == null) {
            return objectMapper.convertValue(new java.util.HashMap<>(), targetType);
        }
        return objectMapper.convertValue(extraParams, targetType);
    }
}
