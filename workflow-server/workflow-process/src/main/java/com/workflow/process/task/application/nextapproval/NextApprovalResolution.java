package com.workflow.process.task.application.nextapproval;

import com.workflow.process.task.api.response.NextApprovalPreviewStatus;
import org.flowable.task.api.Task;

import java.util.List;
import java.util.Map;

/**
 * 下一审批节点路由的内部强类型结果。
 */
public record NextApprovalResolution(
        Task task,
        NextApprovalPreviewStatus status,
        String message,
        String scopeKey,
        List<NextApprovalTarget> targets,
        Map<String, Object> variables) {

    public boolean ready() {
        return status == NextApprovalPreviewStatus.READY;
    }
}
