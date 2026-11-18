package com.workflow.contracts.process.action.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * 流程动作触发时机。
 *
 * <p>枚举平台内置的全部标准触发时机，按作用域和常见业务路径的触发顺序声明；不同操作属于独立分支。每个时机绑定适用作用域、默认执行方式、默认失败策略
 * 以及可用的上下文信息，供前端选项展示与配置校验使用。</p>
 */
public enum FlowActionTriggerTiming {
    PROCESS_STARTING(
            "发起前", "平台检查通过后、准备启动变量及调用引擎前",
            EnumSet.of(FlowActionScopeType.PROCESS), false,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "流程定义及发布版本、实体标识、发起人、启动变量；发起前实例及任务 ID 为空", "BEFORE_EXECUTE"),
    PROCESS_START_EXECUTED(
            "发起后", "引擎启动、流程关联及待办同步完成后；引擎直接启动的子流程在事务提交前补齐，同一实例仅一次",
            EnumSet.of(FlowActionScopeType.PROCESS), false,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "流程实例、实体标识、发起人、最终流程变量快照", "AFTER_EXECUTE"),
    PROCESS_COMPLETED(
            "流程正常结束后",
            "正常结束及本次同步处理完成后、事务提交前；不包含撤回和终止",
            EnumSet.of(FlowActionScopeType.PROCESS),
            false,
            FlowActionExecutionMode.AFTER_COMMIT,
            FlowActionFailurePolicy.RETRY,
            "最终历史根变量、实体标识；当前任务为空", "AFTER_EXECUTE"),
    PROCESS_WITHDRAWING(
            "撤回前", "权限与状态检查通过后、记录活动及删除实例前",
            EnumSet.of(FlowActionScopeType.PROCESS), false,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "流程实例、实体标识、操作人、结束原因；结束后读取最终历史根变量快照", "BEFORE_EXECUTE"),
    PROCESS_WITHDRAWN(
            "撤回后",
            "实例撤回及本次日志、待办清理完成后、事务提交前",
            EnumSet.of(FlowActionScopeType.PROCESS),
            false,
            FlowActionExecutionMode.AFTER_COMMIT,
            FlowActionFailurePolicy.RETRY,
            "最终历史根变量、实体标识、操作人、撤回原因", "AFTER_EXECUTE"),
    PROCESS_TERMINATING(
            "终止前", "权限与状态检查通过后、删除实例前",
            EnumSet.of(FlowActionScopeType.PROCESS), false,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "流程实例、实体标识、操作人、结束原因；结束后读取最终历史根变量快照", "BEFORE_EXECUTE"),
    PROCESS_TERMINATED(
            "流程终止后",
            "人工、系统或 BPMN 终止及本次同步处理完成后、事务提交前",
            EnumSet.of(FlowActionScopeType.PROCESS),
            false,
            FlowActionExecutionMode.AFTER_COMMIT,
            FlowActionFailurePolicy.RETRY,
            "最终历史根变量、实体标识、操作人、终止原因", "AFTER_EXECUTE"),
    NODE_ENTERED(
            "进入节点时",
            "初始化节点变量、准备数据",
            EnumSet.of(FlowActionScopeType.NODE),
            false,
            FlowActionExecutionMode.IN_TRANSACTION,
            FlowActionFailurePolicy.ROLLBACK,
            "节点、执行实例、流程变量"),
    TASK_ASSIGNED(
            "办理人分配或变更后",
            "认领、转办通知",
            EnumSet.of(FlowActionScopeType.NODE),
            true,
            FlowActionExecutionMode.AFTER_COMMIT,
            FlowActionFailurePolicy.RETRY,
            "任务 ID、办理人、流程变量"),
    TASK_CREATED(
            "待办创建后",
            "通知下一办理人、设置任务属性",
            EnumSet.of(FlowActionScopeType.NODE),
            true,
            FlowActionExecutionMode.AFTER_COMMIT,
            FlowActionFailurePolicy.RETRY,
            "任务 ID、任务名称、办理人、流程变量"),
    TASK_SUBMITTING(
            "审批办理前", "普通审批、加签审批或原任务提交检查通过后、本次办理副作用前",
            EnumSet.of(FlowActionScopeType.NODE), true,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "实际办理任务、任务类型、源任务、操作人、审批动作、提交表单和加签类型", "BEFORE_EXECUTE"),
    TASK_FORM_SAVING(
            "审批表单保存前", "实际进入节点审批表单处理后、表单前置处理及实体写入前；不含加签输入暂存",
            EnumSet.of(FlowActionScopeType.NODE), true,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "原引擎任务、审批动作、操作人、本次提交表单只读快照、意见和流程变量", "BEFORE_FORM_SAVE"),
    TASK_ADVANCING(
            "审批流转前（表单保存后）", "实际审批表单处理完成后、下一审批人重验及引擎办理前；本次审批结果尚未汇总写入",
            EnumSet.of(FlowActionScopeType.NODE), true,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "已保存的实体数据、流程变量、审批输入；随后仍将重验下一审批人", "BEFORE_ENGINE_ADVANCE"),
    NODE_COMPLETED(
            "节点离开前",
            "当前执行实例的节点完成后、继续路由前；用户任务审批结果已写入，会签不代表全员完成",
            EnumSet.of(FlowActionScopeType.NODE),
            false,
            FlowActionExecutionMode.IN_TRANSACTION,
            FlowActionFailurePolicy.ROLLBACK,
            "节点、执行实例、最新流程变量；实际完成的引擎用户任务保留任务标识、名称和办理人"),
    TASK_SUBMITTED(
            "审批办理后", "本次审批提交及相关同步处理完成后；可能等待加签，不代表节点或流程结束",
            EnumSet.of(FlowActionScopeType.NODE), true,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "触发任务标识、最终流程变量、提交表单和操作参数；触发任务可能已结束", "AFTER_EXECUTE"),
    TASK_TRANSFERRING(
            "转办前", "权限与操作检查通过后、自动认领、表单保存及办理人变更前",
            EnumSet.of(FlowActionScopeType.NODE), true,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "原任务、转办目标、提交表单快照、意见和流程变量", "BEFORE_EXECUTE"),
    TASK_TRANSFERRED(
            "转办操作执行后", "办理人变更、新待办及转办日志同步完成后",
            EnumSet.of(FlowActionScopeType.NODE), true,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "原任务、转办目标、提交表单快照、意见和流程变量", "AFTER_EXECUTE"),
    TASK_ADD_SIGNING(
            "加签前", "权限、类型及人员检查通过后、创建加签记录和本地子任务前",
            EnumSet.of(FlowActionScopeType.NODE), true,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "源任务、加签类型、解析后的有效人员、意见和流程变量", "BEFORE_EXECUTE"),
    TASK_ADD_SIGNED(
            "加签操作执行后", "加签记录、本地子任务和操作日志创建完成后",
            EnumSet.of(FlowActionScopeType.NODE), true,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "源任务、加签类型、解析后的有效人员、意见和流程变量", "AFTER_EXECUTE"),
    TRANSITION_TAKEN(
            "分支选中、进入目标节点前",
            "审批结果处理、分支状态更新",
            EnumSet.of(FlowActionScopeType.SEQUENCE_FLOW),
            false,
            FlowActionExecutionMode.IN_TRANSACTION,
            FlowActionFailurePolicy.ROLLBACK,
            "来源节点、目标节点、流程变量");

    /** 业务操作阶段；原生节点事件为空。流程结束统一为事务提交前的 AFTER_EXECUTE。 */
    private final String operationPhase;

    /** 前置阶段必须同步阻断，不能通过提交后执行或自定义失败策略绕过准入规则。 */
    public boolean isBlockingOperationTiming() {
        return operationPhase != null && !"AFTER_EXECUTE".equals(operationPhase);
    }

    /** 保存、发布及运行时共用约束，防止错误配置将前置检查降级为通知。 */
    public void validateExecution(String mode, String policy) {
        if (isBlockingOperationTiming()
                && (!"IN_TRANSACTION".equalsIgnoreCase(mode) || !"ROLLBACK".equalsIgnoreCase(policy))) {
            throw new IllegalArgumentException(label + " 必须事务内执行且失败回滚");
        }
    }

    /** 自定义时机沿用扩展约束；平台业务时机在保存和执行时采用同一强制规则。 */
    public static void validateOperationExecution(String timing, String mode, String policy) {
        for (FlowActionTriggerTiming candidate : values()) {
            if (candidate.name().equalsIgnoreCase(timing)) {
                candidate.validateExecution(mode, policy);
                return;
            }
        }
    }

    /** 返回业务阶段；一般后置在步骤末尾触发，流程结束后置统一在外层事务 beforeCommit 收口。 */
    public String getOperationPhase() { return operationPhase; }

    /** 时机展示名称，如"流程启动时" */
    private final String label;
    /** 时机用途说明，用于前端提示 */
    private final String description;
    /** 该时机适用的作用域集合 */
    private final Set<FlowActionScopeType> scopeTypes;
    /** 是否仅限用户任务节点使用 */
    private final boolean userTaskOnly;
    /** 默认执行方式 */
    private final FlowActionExecutionMode defaultExecutionMode;
    /** 默认失败策略 */
    private final FlowActionFailurePolicy defaultFailurePolicy;
    /** 该时机下处理器可用的上下文信息描述 */
    private final String availableContext;

    /**
     * 构造触发时机枚举项。
     *
     * @param label               展示名称
     * @param description         用途说明
     * @param scopeTypes          适用作用域集合
     * @param userTaskOnly        是否仅限用户任务
     * @param defaultExecutionMode 默认执行方式
     * @param defaultFailurePolicy 默认失败策略
     * @param availableContext    可用上下文描述
     */
    FlowActionTriggerTiming(
            String label,
            String description,
            Set<FlowActionScopeType> scopeTypes,
            boolean userTaskOnly,
            FlowActionExecutionMode defaultExecutionMode,
            FlowActionFailurePolicy defaultFailurePolicy,
            String availableContext) {
        this(label, description, scopeTypes, userTaskOnly, defaultExecutionMode,
                defaultFailurePolicy, availableContext, null);
    }

    FlowActionTriggerTiming(String label, String description, Set<FlowActionScopeType> scopeTypes,
                            boolean userTaskOnly, FlowActionExecutionMode defaultExecutionMode,
                            FlowActionFailurePolicy defaultFailurePolicy, String availableContext,
                            String operationPhase) {
        this.operationPhase = operationPhase;
        this.label = label;
        this.description = description;
        this.scopeTypes = scopeTypes;
        this.userTaskOnly = userTaskOnly;
        this.defaultExecutionMode = defaultExecutionMode;
        this.defaultFailurePolicy = defaultFailurePolicy;
        this.availableContext = availableContext;
    }

    /**
     * 获取展示名称
     *
     * @return 读取后的标签文本，供调用方比较或展示
     */
    public String getLabel() {
        return label;
    }

    /**
     * 获取用途说明
     *
     * @return 读取后的描述文本，供调用方比较或展示
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取适用作用域集合
     *
     * @return 流程动作作用域类型集合，供调用方遍历或展示
     */
    public Set<FlowActionScopeType> getScopeTypes() {
        return scopeTypes;
    }

    /**
     * 是否仅限用户任务节点使用
     *
     * @return 用户任务仅条件成立时为 true，否则为 false
     */
    public boolean isUserTaskOnly() {
        return userTaskOnly;
    }

    /**
     * 获取默认执行方式
     *
     * @return 符合条件的流程动作执行模式结果，供调用方继续处理
     */
    public FlowActionExecutionMode getDefaultExecutionMode() {
        return defaultExecutionMode;
    }

    /**
     * 获取默认失败策略
     *
     * @return 符合条件的流程动作失败策略结果，供调用方继续处理
     */
    public FlowActionFailurePolicy getDefaultFailurePolicy() {
        return defaultFailurePolicy;
    }

    /**
     * 获取可用上下文描述
     *
     * @return 读取后的可用上下文文本，供调用方比较或展示
     */
    public String getAvailableContext() {
        return availableContext;
    }
}
