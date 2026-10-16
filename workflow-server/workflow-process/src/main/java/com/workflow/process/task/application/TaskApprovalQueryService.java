package com.workflow.process.task.application;

import com.workflow.core.result.PageResult;
import com.workflow.process.task.api.request.NextApprovalPreviewRequest;
import com.workflow.process.task.api.request.NextApproverOptionsRequest;
import com.workflow.process.task.api.response.NextApprovalPreviewResponse;
import com.workflow.process.task.api.response.NextApprovalPreviewStatus;
import com.workflow.process.task.api.response.NextApproverCandidateDTO;
import com.workflow.process.task.application.nextapproval.NextApprovalPreviewService;
import com.workflow.process.task.application.nextapproval.NextApproverCandidateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 下一审批人查询的共享编排；页面与 Java 调用必须遵守同一加签和授权约束。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskApprovalQueryService {
    private final TaskActionService actions;
    private final TaskAddSignService addSign;
    private final NextApprovalPreviewService previews;
    private final NextApproverCandidateService candidates;

    /**
     * 预览只读取路由与允许预览的数据源。加签尚未汇合时返回 DEFERRED，
     * 不能提前用源任务当前变量计算最终下一节点，更不能为了预览执行实际审批。
     * @param taskId 待办理任务；普通任务与本地加签分别复用现有办理授权
     * @param request 动作和本次表单补丁；省略时使用既有默认预览行为
     */
    public NextApprovalPreviewResponse preview(String taskId, NextApprovalPreviewRequest request) {
        if (addSign.isAddSignTask(taskId)) {
            addSign.requireAddSignTaskAccess(taskId);
            return deferred(taskId, "加签子任务需等待加签编排完成");
        }
        actions.requireTaskAccess(taskId);
        if (addSign.isAddSignSourceTask(taskId)) {
            return deferred(taskId, "加签编排中的下一节点需等待加签完成");
        }
        return previews.preview(taskId, request == null ? new NextApprovalPreviewRequest() : request);
    }

    /** 查询预览范围内的候选人；scopeKey 在现有候选服务中重新核对，不能任意指定人员来源。 */
    public PageResult<NextApproverCandidateDTO> options(String taskId, NextApproverOptionsRequest request) {
        actions.requireTaskAccess(taskId);
        return candidates.options(taskId, request);
    }

    private NextApprovalPreviewResponse deferred(String taskId, String message) {
        var response = new NextApprovalPreviewResponse();
        response.setTaskId(taskId);
        response.setStatus(NextApprovalPreviewStatus.DEFERRED);
        response.setMessage(message);
        return response;
    }
}
