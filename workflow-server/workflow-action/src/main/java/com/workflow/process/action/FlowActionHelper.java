package com.workflow.process.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.EntityDataDTO;
import com.workflow.service.EntityDataDynamicService;
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
public class FlowActionHelper {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final EntityDataDynamicService entityDataDynamicService;
    private final ObjectMapper objectMapper;

    public Map<String, Object> getVariables(String processInstanceId) {
        try {
            Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
            if (variables != null && !variables.isEmpty()) {
                return variables;
            }
        } catch (Exception ignored) {
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .list()
                .forEach(variable -> variables.put(variable.getVariableName(), variable.getValue()));
        return variables;
    }

    public Object getVariable(String processInstanceId, String name) {
        Map<String, Object> variables = getVariables(processInstanceId);
        return variables.get(name);
    }

    public ProcessInstance getProcessInstance(String processInstanceId) {
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
    }

    public HistoricProcessInstance getHistoricProcessInstance(String processInstanceId) {
        return historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
    }

    public Task getCurrentTask(String processInstanceId) {
        return taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .list()
                .stream()
                .findFirst()
                .orElse(null);
    }

    public Task getTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        return taskService.createTaskQuery().taskId(taskId).singleResult();
    }

    public EntityDataDTO getEntityData(String entityCode, String entityDataId) {
        return entityDataDynamicService.findById(entityCode, entityDataId);
    }

    public <T> T convertParams(Map<String, Object> customParams, Class<T> targetType) {
        if (customParams == null) {
            return objectMapper.convertValue(new java.util.HashMap<>(), targetType);
        }
        return objectMapper.convertValue(customParams, targetType);
    }
}
