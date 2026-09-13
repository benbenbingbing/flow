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
    /** 本次提交审批所使用的已发布表单及活动任务令牌。 */
    private String formId;
    private String formReleaseId;
    private Integer formReleaseVersion;
    private String formReleaseResolutionToken;
    /** 客户端声明只用于与服务端任务/令牌联合校验，不能单独授权。 */
    private String entityCode;
    private String recordId;
    private String listKey;
    private String nextApprovalScopeKey;
    private List<NextApproverSelectionRequest> nextApproverSelections;
}
