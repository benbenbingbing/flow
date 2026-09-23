package com.workflow.process.task.application.nextapproval;

import org.flowable.bpmn.model.UserTask;

import java.util.Map;

/**
 * 路由预测得到的目标用户任务及其发布时配置。
 *
 * @param userTask 用户任务，保存在对象中供后续校验、查询或展示
 * @param assigneeConfig 办理人配置内容，决定后续下一步审批目标的处理规则
 * @param selectionPolicy 选择策略，保存在对象中供后续校验、查询或展示
 * @param assignmentSourceTask 分配来源任务，保存在对象中供后续校验、查询或展示
 */
public record NextApprovalTarget(
        UserTask userTask,
        Map<String, Object> assigneeConfig,
        NextApproverSelectionPolicy selectionPolicy,
        UserTask assignmentSourceTask) {

    /**
     * 普通节点沿用自身办理人规则，保持现有调用方源码兼容。
     *
     * @param userTask 用户任务，保存在对象中供后续校验、查询或展示
     * @param assigneeConfig 办理人配置内容，决定后续下一步审批目标的处理规则
     * @param selectionPolicy 选择策略，保存在对象中供后续校验、查询或展示
     */
    public NextApprovalTarget(
            UserTask userTask,
            Map<String, Object> assigneeConfig,
            NextApproverSelectionPolicy selectionPolicy) {
        this(userTask, assigneeConfig, selectionPolicy, userTask);
    }
}
