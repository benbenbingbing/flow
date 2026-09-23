package com.workflow.process.task.application.nextapproval;

import java.util.List;

/**
 * 从流程变量一次性取出的下一节点审批人覆盖。
 *
 * @param sourceTaskId 来源任务ID，后续用于处理下一步审批人覆盖时定位或关联目标
 * @param targetNodeId 目标节点ID，后续用于处理下一步审批人覆盖时定位或关联目标
 * @param assignmentMode 人员分配模式，后续选择解析器和校验规则
 * @param usernames {@code usernames}，保存在对象中供后续校验、查询或展示
 */
public record NextApproverOverride(
        String sourceTaskId,
        String targetNodeId,
        String assignmentMode,
        List<String> usernames) {
}
