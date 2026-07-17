package com.workflow.process.action;

import java.util.EnumSet;
import java.util.Set;

public enum FlowActionTriggerTiming {
    PROCESS_STARTED(
            "流程启动时",
            "初始化变量、创建业务关联",
            EnumSet.of(FlowActionScopeType.PROCESS),
            false,
            FlowActionExecutionMode.IN_TRANSACTION,
            FlowActionFailurePolicy.ROLLBACK,
            "流程变量、实体数据"),
    PROCESS_COMPLETED(
            "流程正常完成后",
            "归档、完成通知、外部同步",
            EnumSet.of(FlowActionScopeType.PROCESS),
            false,
            FlowActionExecutionMode.AFTER_COMMIT,
            FlowActionFailurePolicy.RETRY,
            "历史变量、实体数据；当前任务为空"),
    PROCESS_WITHDRAWN(
            "流程撤回后",
            "撤回通知、资源释放",
            EnumSet.of(FlowActionScopeType.PROCESS),
            false,
            FlowActionExecutionMode.AFTER_COMMIT,
            FlowActionFailurePolicy.RETRY,
            "历史变量、撤回原因、实体数据"),
    PROCESS_TERMINATED(
            "流程终止后",
            "终止通知、异常清理",
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
            "任务提交、流程流转前",
            "审批校验、核心业务写入",
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

    private final String label;
    private final String description;
    private final Set<FlowActionScopeType> scopeTypes;
    private final boolean userTaskOnly;
    private final FlowActionExecutionMode defaultExecutionMode;
    private final FlowActionFailurePolicy defaultFailurePolicy;
    private final String availableContext;

    FlowActionTriggerTiming(
            String label,
            String description,
            Set<FlowActionScopeType> scopeTypes,
            boolean userTaskOnly,
            FlowActionExecutionMode defaultExecutionMode,
            FlowActionFailurePolicy defaultFailurePolicy,
            String availableContext) {
        this.label = label;
        this.description = description;
        this.scopeTypes = scopeTypes;
        this.userTaskOnly = userTaskOnly;
        this.defaultExecutionMode = defaultExecutionMode;
        this.defaultFailurePolicy = defaultFailurePolicy;
        this.availableContext = availableContext;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public Set<FlowActionScopeType> getScopeTypes() {
        return scopeTypes;
    }

    public boolean isUserTaskOnly() {
        return userTaskOnly;
    }

    public FlowActionExecutionMode getDefaultExecutionMode() {
        return defaultExecutionMode;
    }

    public FlowActionFailurePolicy getDefaultFailurePolicy() {
        return defaultFailurePolicy;
    }

    public String getAvailableContext() {
        return availableContext;
    }
}
