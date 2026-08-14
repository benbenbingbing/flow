package com.workflow.process.task.application.nextapproval;

import org.flowable.bpmn.model.UserTask;

import java.util.Map;

/**
 * 路由预测得到的目标用户任务及其发布时配置。
 */
public record NextApprovalTarget(
        UserTask userTask,
        Map<String, Object> assigneeConfig,
        NextApproverSelectionPolicy selectionPolicy) {
}
