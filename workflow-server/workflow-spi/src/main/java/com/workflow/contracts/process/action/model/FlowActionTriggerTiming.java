package com.workflow.contracts.process.action.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * 流程动作触发时机。
 *
 * <p>枚举平台内置的全部标准触发时机，每个时机绑定适用作用域、默认执行方式、默认失败策略
 * 以及可用的上下文信息，供前端选项展示与配置校验使用。</p>
 */
public enum FlowActionTriggerTiming {
    PROCESS_STARTING(
            "发起前", "平台检查通过后、准备启动变量及调用引擎前",
            EnumSet.of(FlowActionScopeType.PROCESS), false,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "流程定义及发布版本、实体标识、发起人、启动变量；发起前实例及任务 ID 为空", "BEFORE_EXECUTE"),
    PROCESS_START_EXECUTED(
            "发起操作执行后", "引擎启动、流程关联及待办同步完成后",
            EnumSet.of(FlowActionScopeType.PROCESS), false,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "流程实例、实体标识、发起人、最终流程变量快照", "AFTER_EXECUTE"),
    PROCESS_WITHDRAWING(
            "撤回前", "权限与状态检查通过后、记录活动及删除实例前",
            EnumSet.of(FlowActionScopeType.PROCESS), false,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "流程实例、实体标识、操作人、结束原因；结束后读取最终历史根变量快照", "BEFORE_EXECUTE"),
    PROCESS_WITHDRAW_EXECUTED(
            "撤回操作执行后", "删除实例、操作日志及本地待办清理完成后",
            EnumSet.of(FlowActionScopeType.PROCESS), false,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "流程实例、实体标识、操作人、结束原因；结束后读取最终历史根变量快照", "AFTER_EXECUTE"),
    PROCESS_TERMINATING(
            "终止前", "权限与状态检查通过后、删除实例前",
            EnumSet.of(FlowActionScopeType.PROCESS), false,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "流程实例、实体标识、操作人、结束原因；结束后读取最终历史根变量快照", "BEFORE_EXECUTE"),
    PROCESS_TERMINATE_EXECUTED(
            "终止操作执行后", "删除实例、操作日志及本地待办清理完成后",
            EnumSet.of(FlowActionScopeType.PROCESS), false,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "流程实例、实体标识、操作人、结束原因；结束后读取最终历史根变量快照", "AFTER_EXECUTE"),
    TASK_FORM_SAVING(
            "审批表单保存前", "权限与操作检查通过后、自动认领及节点表单写入前",
            EnumSet.of(FlowActionScopeType.NODE), true,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "原引擎任务、审批动作、操作人、本次提交表单只读快照、意见和流程变量", "BEFORE_FORM_SAVE"),
    TASK_ADVANCING(
            "任务引擎推进前", "表单保存后、下一审批人重验和引擎完成前",
            EnumSet.of(FlowActionScopeType.NODE), true,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "已保存的实体数据、流程变量、审批输入；随后仍将重验下一审批人", "BEFORE_ENGINE_ADVANCE"),
    TASK_SUBMITTED(
            "审批办理执行后", "原任务完成及本地待办、实体活动同步完成后",
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
    ADD_SIGN_SOURCE_SUBMITTING(
            "加签源任务暂存前", "权限与状态检查通过后、保存源任务审批输入前",
            EnumSet.of(FlowActionScopeType.NODE), true,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "源任务、加签类型、本次审批输入及表单快照", "BEFORE_EXECUTE"),
    ADD_SIGN_SOURCE_SUBMITTED(
            "加签源任务暂存执行后", "输入暂存及子任务激活或同步收口处理完成后",
            EnumSet.of(FlowActionScopeType.NODE), true,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "源任务、加签类型、本次审批输入及表单快照", "AFTER_EXECUTE"),
    ADD_SIGN_TASK_COMPLETING(
            "加签子任务办理前", "子任务权限与动作检查通过后、更新本地任务前",
            EnumSet.of(FlowActionScopeType.NODE), true,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "本地子任务 ID、源任务 ID、源节点、子任务办理人、审批动作、加签类型", "BEFORE_EXECUTE"),
    ADD_SIGN_TASK_COMPLETED(
            "加签子任务办理执行后", "子任务及加签编排处理完成后，可能包含源任务收口",
            EnumSet.of(FlowActionScopeType.NODE), true,
            FlowActionExecutionMode.IN_TRANSACTION, FlowActionFailurePolicy.ROLLBACK,
            "本地子任务 ID、源任务 ID、源节点、子任务办理人、审批动作、加签类型", "AFTER_EXECUTE"),
    PROCESS_STARTED(
            "引擎流程启动时",
            "引擎启动事件；平台流程关联及待办同步尚未全部完成",
            EnumSet.of(FlowActionScopeType.PROCESS),
            false,
            FlowActionExecutionMode.IN_TRANSACTION,
            FlowActionFailurePolicy.ROLLBACK,
            "流程变量、实体数据"),
    PROCESS_COMPLETED(
            "引擎流程正常结束时",
            "引擎正常结束事件；当前办理的本地同步可能尚未完成",
            EnumSet.of(FlowActionScopeType.PROCESS),
            false,
            FlowActionExecutionMode.AFTER_COMMIT,
            FlowActionFailurePolicy.RETRY,
            "历史变量、实体数据；当前任务为空"),
    PROCESS_WITHDRAWN(
            "引擎实例撤回时",
            "引擎删除事件；撤回日志及本地待办清理尚未全部完成",
            EnumSet.of(FlowActionScopeType.PROCESS),
            false,
            FlowActionExecutionMode.AFTER_COMMIT,
            FlowActionFailurePolicy.RETRY,
            "历史变量、撤回原因、实体数据"),
    PROCESS_TERMINATED(
            "引擎实例终止时",
            "引擎终止事件；终止日志及本地待办清理尚未全部完成",
            EnumSet.of(FlowActionScopeType.PROCESS),
            false,
            FlowActionExecutionMode.AFTER_COMMIT,
            FlowActionFailurePolicy.RETRY,
            "历史变量、终止原因、实体数据"),
    NODE_ENTERED(
            "进入节点时",
            "初始化节点变量、准备数据",
            EnumSet.of(FlowActionScopeType.NODE),
            false,
            FlowActionExecutionMode.IN_TRANSACTION,
            FlowActionFailurePolicy.ROLLBACK,
            "节点、执行实例、流程变量"),
    NODE_COMPLETED(
            "节点完成、路由计算前",
            "写入计算结果、影响后续条件",
            EnumSet.of(FlowActionScopeType.NODE),
            false,
            FlowActionExecutionMode.IN_TRANSACTION,
            FlowActionFailurePolicy.ROLLBACK,
            "节点、执行实例、流程变量"),
    TASK_CREATED(
            "待办创建后",
            "通知下一办理人、设置任务属性",
            EnumSet.of(FlowActionScopeType.NODE),
            true,
            FlowActionExecutionMode.AFTER_COMMIT,
            FlowActionFailurePolicy.RETRY,
            "任务 ID、任务名称、办理人、流程变量"),
    TASK_ASSIGNED(
            "办理人分配或变更后",
            "认领、转办通知",
            EnumSet.of(FlowActionScopeType.NODE),
            true,
            FlowActionExecutionMode.AFTER_COMMIT,
            FlowActionFailurePolicy.RETRY,
            "任务 ID、办理人、流程变量"),
    TASK_COMPLETING(
            "引擎任务完成事件（兼容时机）",
            "由 TASK_COMPLETED 映射；表单已保存，新增前置逻辑请使用审批表单保存前",
            EnumSet.of(FlowActionScopeType.NODE),
            true,
            FlowActionExecutionMode.IN_TRANSACTION,
            FlowActionFailurePolicy.ROLLBACK,
            "任务、审批动作、审批人、流程变量"),
    TRANSITION_TAKEN(
            "分支选中、进入目标节点前",
            "审批结果处理、分支状态更新",
            EnumSet.of(FlowActionScopeType.SEQUENCE_FLOW),
            false,
            FlowActionExecutionMode.IN_TRANSACTION,
            FlowActionFailurePolicy.ROLLBACK,
            "来源节点、目标节点、流程变量");

    /** 业务操作阶段；引擎事件为空，不能据 IN_TRANSACTION 推断业务步骤。 */
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

    /** 返回精确业务阶段；AFTER_EXECUTE 是业务步骤完成后，并非整个外层事务的 beforeCommit。 */
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
