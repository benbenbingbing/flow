package com.workflow.process.variable.application;

import com.workflow.contracts.process.error.ProcessOperationException;
import com.workflow.contracts.process.model.ProcessValues;
import com.workflow.contracts.process.variable.model.VariableReadMode;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignUserMapper;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 平台内部与公共接口共用的变量作用域读取组件。不负责授权或过滤保留字段，
 * 调用方必须在返回结果前执行对应访问策略；技术异常绝不能触发历史兜底。
 */
@Component
@RequiredArgsConstructor
public class ProcessVariableReader {
    private final RuntimeService runtime;
    private final TaskService tasks;
    private final HistoryService history;
    private final ProcessTaskAddSignUserMapper addSignUsers;

    /** 读取根流程变量；只有已确认没有运行实例才允许回退历史。 */
    public VariableSnapshot process(String instanceId, VariableReadMode mode) {
        ProcessValues.required(instanceId, "processInstanceId");
        Objects.requireNonNull(mode, "readMode");
        ProcessInstance active = runtime.createProcessInstanceQuery().processInstanceId(instanceId).singleResult();
        if (active != null && mode != VariableReadMode.HISTORY_ONLY) {
            return new VariableSnapshot(instanceId, runtime.getVariablesLocal(instanceId));
        }
        var archived = history.createHistoricProcessInstanceQuery().processInstanceId(instanceId).singleResult();
        if (active == null && archived == null) throw missing("PROCESS_NOT_FOUND", "流程实例不存在", instanceId);
        if (mode == VariableReadMode.RUNTIME_ONLY) throw missing("PROCESS_ALREADY_ENDED", "流程已结束", instanceId);
        Map<String, Object> values = new LinkedHashMap<>();
        history.createHistoricVariableInstanceQuery().processInstanceId(instanceId).list().stream()
                // 同名任务本地变量、会签分支变量不得覆盖根实例变量。
                .filter(v -> v.getTaskId() == null && instanceId.equals(v.getExecutionId()))
                .forEach(v -> values.put(v.getVariableName(), v.getValue()));
        return new VariableSnapshot(instanceId, values);
    }

    /** 读取真实引擎任务的本地作用域；平台本地加签没有引擎变量空间。 */
    public VariableSnapshot task(String taskId, VariableReadMode mode) {
        ProcessValues.required(taskId, "taskId");
        Objects.requireNonNull(mode, "readMode");
        requireEngineTaskScope(taskId);
        Task active = tasks.createTaskQuery().taskId(taskId).singleResult();
        if (active != null && mode != VariableReadMode.HISTORY_ONLY) {
            return new VariableSnapshot(active.getProcessInstanceId(), tasks.getVariablesLocal(taskId));
        }
        var archived = history.createHistoricTaskInstanceQuery().taskId(taskId).singleResult();
        if (active == null && archived == null) throw missing("TASK_NOT_FOUND", "任务不存在", taskId);
        if (mode == VariableReadMode.RUNTIME_ONLY) throw missing("TASK_ALREADY_COMPLETED", "任务已结束", taskId);
        Map<String, Object> values = new LinkedHashMap<>();
        history.createHistoricVariableInstanceQuery().taskId(taskId).list()
                .forEach(v -> values.put(v.getVariableName(), v.getValue()));
        return new VariableSnapshot(active != null ? active.getProcessInstanceId() : archived.getProcessInstanceId(), values);
    }

    /** 防止把本地加签标识误当成 Flowable 任务，或悄悄改写其源任务变量。 */
    public void requireEngineTaskScope(String taskId) {
        if (addSignUsers.findByGeneratedTaskId(taskId) != null) throw missing(
                "VARIABLE_SCOPE_UNSUPPORTED", "本地加签任务没有独立引擎变量作用域", taskId);
    }

    private ProcessOperationException missing(String code, String message, String id) {
        return new ProcessOperationException(code, message, id);
    }

    /** 同时返回真实归属以供授权，不接受调用方另行声明 taskId 的所属流程。 */
    public record VariableSnapshot(String instanceId, Map<String, Object> values) {}
}
