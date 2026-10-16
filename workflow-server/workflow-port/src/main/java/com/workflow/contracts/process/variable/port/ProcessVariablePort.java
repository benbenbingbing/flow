package com.workflow.contracts.process.variable.port;

import com.workflow.contracts.process.model.CommandMeta;
import com.workflow.contracts.process.model.ProcessCommandReceipt;
import com.workflow.contracts.process.variable.model.*;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 业务变量的统一读取和原子补丁入口。执行身份由宿主提供，调用方不能通过请求声明操作人。
 * 所有失败均向外抛出异常；不得捕获后继续提交依赖这些变量的业务操作。
 * 公共调用不改写引擎或平台编排保留变量，也不会自动同步实体字段。
 */
public interface ProcessVariablePort {
    /**
     * 读取根流程作用域的只读快照，排除任务本地/执行分支同名变量和内部编排字段。
     * @param processInstanceId 真实流程实例标识；当前身份须有该实例读取权限
     * @param readMode 明确读取运行态、历史或仅在结束后回退历史；技术异常不触发回退
     * @return 深度只读快照，包含值为 null 的业务变量；空集合不是故障兜底
     * @throws com.workflow.contracts.process.error.ProcessOperationException 不存在、无权限或读取模式与生命周期不符
     */
    Map<String, Object> getProcessVariables(String processInstanceId, VariableReadMode readMode);
    /**
     * 与批量读取共用作用域、过滤和授权。缺失或被隐藏的变量返回 present=false，
     * 已存在的 null 值返回 present=true/value=null；实例不存在仍抛异常。
     * @param name 非空变量名，不能用 null 表示“读取全部”
     */
    VariableValue getProcessVariable(String processInstanceId, String name, VariableReadMode readMode);
    /**
     * 读取真实引擎任务的本地变量，先从任务解析所属实例再授权。
     * @param taskId 引擎任务标识；平台本地加签没有此作用域，抛 VARIABLE_SCOPE_UNSUPPORTED
     * @param readMode 完成任务可显式读取历史，RUNTIME_ONLY 则抛 TASK_ALREADY_COMPLETED
     * @return 深度只读快照，不包含父执行或流程变量
     */
    Map<String, Object> getTaskLocalVariables(String taskId, VariableReadMode readMode);
    /** 按任务本地作用域查询变量，不隐式读取流程变量。 */
    VariableValue getTaskLocalVariable(String taskId, String name, VariableReadMode readMode);
    /**
     * 同一事务设置和删除根流程业务变量，任一校验/写入失败则全部回滚。
     * @param command setValues 中的 null 是赋值，removeNames 才表示删除；同名键不能同时出现
     * @return 持久化回执；replayed=true 表示复用首次成功结果，外层事务存在时返回并不代表已提交
     * @throws com.workflow.contracts.process.error.ProcessOperationException 保留变量、结束/挂起状态、无写权限或幂等载荷冲突
     */
    ProcessCommandReceipt patchProcessVariables(ProcessVariablePatch command);
    /**
     * 同一事务设置和删除任务本地变量；只读参与者无写权限，完成任务和本地加签任务拒写。
     * 重试必须复用 command.meta.requestId；不能以换一个 requestId 绕过一次真实业务失败。
     * @return 与流程变量补丁相同语义的回执，写变量本身不完成或认领任务
     */
    ProcessCommandReceipt patchTaskLocalVariables(TaskVariablePatch command);
    /** 单变量便捷入口仍进入相同补丁管道，允许显式写入 null。 */
    default ProcessCommandReceipt setProcessVariable(CommandMeta meta, String instanceId, String name, Object value) {
        return patchProcessVariables(new ProcessVariablePatch(meta, instanceId, Collections.singletonMap(name, value), Set.of()));
    }
}
