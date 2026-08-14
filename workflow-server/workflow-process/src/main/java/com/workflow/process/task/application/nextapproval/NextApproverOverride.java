package com.workflow.process.task.application.nextapproval;

import java.util.List;

/**
 * 从流程变量一次性取出的下一节点审批人覆盖。
 */
public record NextApproverOverride(
        String sourceTaskId,
        String targetNodeId,
        String assignmentMode,
        List<String> usernames) {
}
