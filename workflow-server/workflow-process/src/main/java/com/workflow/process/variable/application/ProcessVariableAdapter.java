package com.workflow.process.variable.application;

import com.workflow.port.process.error.ProcessOperationException;
import com.workflow.port.process.model.ProcessCommandReceipt;
import com.workflow.port.process.model.variable.*;
import com.workflow.port.process.ProcessVariablePort;
import com.workflow.process.command.application.ProcessCommandExecutor;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 公共变量端口；读取按引擎作用域区分，写入统一进入命令事务和回执管道。 */
@Component
@RequiredArgsConstructor
public class ProcessVariableAdapter implements ProcessVariablePort {
    private final ProcessVariableReader reader;
    private final ProcessCommandExecutor commands;
    private final RuntimeService runtime;
    private final TaskService tasks;

    /** 返回根流程变量快照，不把内部控制字段暴露为业务变量。 */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getProcessVariables(String instanceId, VariableReadMode mode) {
        var snapshot = reader.process(instanceId, mode);
        return ProcessVariablePolicy.visibleSnapshot(snapshot.values());
    }

    /** 使用 containsKey 保留变量不存在和 null 值之间的区别。 */
    @Override
    @Transactional(readOnly = true)
    public VariableValue getProcessVariable(String instanceId, String name, VariableReadMode mode) {
        com.workflow.port.process.model.ProcessValues.required(name, "变量名");
        var values = getProcessVariables(instanceId, mode);
        return new VariableValue(values.containsKey(name), values.get(name));
    }

    /** 从真实引擎任务读取本地变量，调用方负责目标任务授权。 */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getTaskLocalVariables(String taskId, VariableReadMode mode) {
        var snapshot = reader.task(taskId, mode);
        return ProcessVariablePolicy.visibleSnapshot(snapshot.values());
    }

    /** 单变量读取与批量读取共用作用域和过滤，null 值通过 present 明确区分。 */
    @Override
    @Transactional(readOnly = true)
    public VariableValue getTaskLocalVariable(String taskId, String name, VariableReadMode mode) {
        com.workflow.port.process.model.ProcessValues.required(name, "变量名");
        var values = getTaskLocalVariables(taskId, mode);
        return new VariableValue(values.containsKey(name), values.get(name));
    }

    /** 写时重验实例状态；预先读取到的 RUNNING 状态不构成写入保证。 */
    @Override
    public ProcessCommandReceipt patchProcessVariables(ProcessVariablePatch command) {
        validate(command.setValues(), command.removeNames(), command.processInstanceId());
        return commands.execute(command.meta(), "PROCESS_VARIABLE_PATCH", command, Boolean.class, () -> {
            var instance = runtime.createProcessInstanceQuery().processInstanceId(command.processInstanceId()).singleResult();
            if (instance == null) reader.process(command.processInstanceId(), VariableReadMode.RUNTIME_ONLY);
            if (instance == null || instance.isSuspended()) throw new ProcessOperationException(
                    "VARIABLE_NOT_WRITABLE", "流程不处于可写运行状态", command.processInstanceId());
            Map<String, Object> before = runtime.getVariablesLocal(instance.getId());
            boolean changed = changed(before, command.setValues(), command.removeNames());
            runtime.setVariablesLocal(instance.getId(), command.setValues());
            runtime.removeVariablesLocal(instance.getId(), command.removeNames());
            return new ProcessCommandExecutor.Outcome<>(instance.getId(), null, changed, changed);
        }).receipt();
    }

    /** 本地任务补丁仅写入活动引擎任务的本地作用域。 */
    @Override
    public ProcessCommandReceipt patchTaskLocalVariables(TaskVariablePatch command) {
        validate(command.setValues(), command.removeNames(), command.taskId());
        return commands.execute(command.meta(), "TASK_VARIABLE_PATCH", command, Boolean.class, () -> {
            reader.requireEngineTaskScope(command.taskId());
            var task = tasks.createTaskQuery().taskId(command.taskId()).singleResult();
            if (task == null) reader.task(command.taskId(), VariableReadMode.RUNTIME_ONLY);
            if (task == null) throw new ProcessOperationException("TASK_ALREADY_COMPLETED", "任务已结束", command.taskId());
            if (task.isSuspended()) throw new ProcessOperationException("VARIABLE_NOT_WRITABLE", "任务已挂起", command.taskId());
            Map<String, Object> before = tasks.getVariablesLocal(task.getId());
            boolean changed = changed(before, command.setValues(), command.removeNames());
            tasks.setVariablesLocal(task.getId(), command.setValues());
            tasks.removeVariablesLocal(task.getId(), command.removeNames());
            return new ProcessCommandExecutor.Outcome<>(task.getProcessInstanceId(), task.getId(), changed, changed);
        }).receipt();
    }


    private static void validate(Map<String, Object> values, Set<String> removals, String targetId) {
        ProcessVariablePolicy.requireWritable(values.keySet(), targetId);
        ProcessVariablePolicy.requireWritable(removals, targetId);
    }

    /** presence 参与比较：新增 null 值也是变更，删除一个原本不存在的键则不是。 */
    private static boolean changed(Map<String, Object> before, Map<String, Object> values, Set<String> removals) {
        return values.entrySet().stream().anyMatch(e -> !before.containsKey(e.getKey()) || !Objects.equals(before.get(e.getKey()), e.getValue()))
                || removals.stream().anyMatch(before::containsKey);
    }
}
