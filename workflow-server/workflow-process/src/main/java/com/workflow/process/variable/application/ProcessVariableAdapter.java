package com.workflow.process.variable.application;

import com.workflow.contracts.process.error.ProcessOperationException;
import com.workflow.contracts.process.model.ProcessCommandReceipt;
import com.workflow.contracts.process.variable.model.*;
import com.workflow.contracts.process.variable.port.ProcessVariablePort;
import com.workflow.process.command.application.ProcessCommandExecutor;
import com.workflow.process.command.application.ProcessInvocationAccess;
import com.workflow.process.task.application.LocalAddSignTaskAccessService;
import com.workflow.process.task.application.TaskIdentityAccessService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 公共变量端口；读取共用作用域组件，写入统一进入命令事务和回执管道。 */
@Component
@RequiredArgsConstructor
public class ProcessVariableAdapter implements ProcessVariablePort {
    private final ProcessVariableReader reader;
    private final ProcessInvocationAccess access;
    private final ProcessCommandExecutor commands;
    private final RuntimeService runtime;
    private final TaskService tasks;
    private final TaskIdentityAccessService taskAccess;
    private final LocalAddSignTaskAccessService addSignAccess;

    /** 返回已授权根流程变量快照，不把内部控制字段暴露为业务变量。 */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getProcessVariables(String instanceId, VariableReadMode mode) {
        var snapshot = reader.process(instanceId, mode);
        access.requireRead(snapshot.instanceId());
        return ProcessVariablePolicy.visibleSnapshot(snapshot.values());
    }

    /** 使用 containsKey 保留变量不存在和 null 值之间的区别。 */
    @Override
    @Transactional(readOnly = true)
    public VariableValue getProcessVariable(String instanceId, String name, VariableReadMode mode) {
        com.workflow.contracts.process.model.ProcessValues.required(name, "变量名");
        var values = getProcessVariables(instanceId, mode);
        return new VariableValue(values.containsKey(name), values.get(name));
    }

    /** 从真实任务解析所属实例后授权，不信任调用方声明的业务关联。 */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getTaskLocalVariables(String taskId, VariableReadMode mode) {
        var snapshot = reader.task(taskId, mode);
        access.requireRead(snapshot.instanceId());
        return ProcessVariablePolicy.visibleSnapshot(snapshot.values());
    }

    /** 单变量读取与批量读取共用权限和过滤，null 值通过 present 明确区分。 */
    @Override
    @Transactional(readOnly = true)
    public VariableValue getTaskLocalVariable(String taskId, String name, VariableReadMode mode) {
        com.workflow.contracts.process.model.ProcessValues.required(name, "变量名");
        var values = getTaskLocalVariables(taskId, mode);
        return new VariableValue(values.containsKey(name), values.get(name));
    }

    /** 写时重验实例和身份；预先读取到的 RUNNING 状态不构成写入保证。 */
    @Override
    public ProcessCommandReceipt patchProcessVariables(ProcessVariablePatch command) {
        validate(command.setValues(), command.removeNames(), command.processInstanceId());
        return commands.execute(command.meta(), "PROCESS_VARIABLE_PATCH", command, Boolean.class, () -> {
            var instance = runtime.createProcessInstanceQuery().processInstanceId(command.processInstanceId()).singleResult();
            if (instance == null) reader.process(command.processInstanceId(), VariableReadMode.RUNTIME_ONLY);
            if (instance == null || instance.isSuspended()) throw new ProcessOperationException(
                    "VARIABLE_NOT_WRITABLE", "流程不处于可写运行状态", command.processInstanceId());
            requireProcessWrite(instance.getId(), instance.getStartUserId());
            Map<String, Object> before = runtime.getVariablesLocal(instance.getId());
            boolean changed = changed(before, command.setValues(), command.removeNames());
            runtime.setVariablesLocal(instance.getId(), command.setValues());
            runtime.removeVariablesLocal(instance.getId(), command.removeNames());
            return new ProcessCommandExecutor.Outcome<>(instance.getId(), null, changed, changed);
        }).receipt();
    }

    /** 本地任务作用域只允许真实引擎任务办理身份写入，不把变量写入等同于任务审批。 */
    @Override
    public ProcessCommandReceipt patchTaskLocalVariables(TaskVariablePatch command) {
        validate(command.setValues(), command.removeNames(), command.taskId());
        return commands.execute(command.meta(), "TASK_VARIABLE_PATCH", command, Boolean.class, () -> {
            reader.requireEngineTaskScope(command.taskId());
            var task = tasks.createTaskQuery().taskId(command.taskId()).singleResult();
            if (task == null) reader.task(command.taskId(), VariableReadMode.RUNTIME_ONLY);
            if (task == null) throw new ProcessOperationException("TASK_ALREADY_COMPLETED", "任务已结束", command.taskId());
            if (task.isSuspended()) throw new ProcessOperationException("VARIABLE_NOT_WRITABLE", "任务已挂起", command.taskId());
            access.requireTaskVariables(task);
            Map<String, Object> before = tasks.getVariablesLocal(task.getId());
            boolean changed = changed(before, command.setValues(), command.removeNames());
            tasks.setVariablesLocal(task.getId(), command.setValues());
            tasks.removeVariablesLocal(task.getId(), command.removeNames());
            return new ProcessCommandExecutor.Outcome<>(task.getProcessInstanceId(), task.getId(), changed, changed);
        }).receipt();
    }

    /** 写变量会影响后续路由，不能因拥有流程历史查看权就授予写权限。 */
    private void requireProcessWrite(String instanceId, String initiator) {
        if (access.isActionFor(instanceId)) return;
        var actor = access.requireActor();
        // 只读参与者（例如知会人）不能修改路由数据；发起人和当前可办理任务的身份可以。
        if (initiator != null && (initiator.equals(actor.userId()) || initiator.equals(actor.username()))) return;
        if (tasks.createTaskQuery().processInstanceId(instanceId).active().list().stream().anyMatch(taskAccess::canCurrentUserAccess)
                || addSignAccess.hasCurrentUserTaskInProcess(instanceId)) return;
        throw new ProcessOperationException("PROCESS_ACCESS_DENIED", "当前身份不能修改该流程变量", instanceId);
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
