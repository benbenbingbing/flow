package com.workflow.process.task.application.nextapproval;

import org.flowable.bpmn.model.UserTask;

import java.util.Map;

/**
 * 路由预测得到的目标用户任务及其发布时配置。
 */
public record NextApprovalTarget(
        UserTask userTask,
        Map<String, Object> assigneeConfig,
        NextApproverSelectionPolicy selectionPolicy,
        UserTask assignmentSourceTask) {

    /** 普通节点沿用自身办理人规则，保持现有调用方源码兼容。 */
    public NextApprovalTarget(
            UserTask userTask,
            Map<String, Object> assigneeConfig,
            NextApproverSelectionPolicy selectionPolicy) {
        this(userTask, assigneeConfig, selectionPolicy, userTask);
    }
}
