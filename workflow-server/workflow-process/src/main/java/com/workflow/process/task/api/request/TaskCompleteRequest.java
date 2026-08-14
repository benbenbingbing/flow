package com.workflow.process.task.api.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 完成流程任务请求。
 *
 * <p>保留原有字段并增加下一节点审批人选择，兼容既有调用方。</p>
 */
@Data
public class TaskCompleteRequest {

    private String taskId;
    private String action;
    private String comment;
    private String transferTo;
    private String actionLabel;
    private Map<String, Object> formData;
    private String nextApprovalScopeKey;
    private List<NextApproverSelectionRequest> nextApproverSelections;
}
