package com.workflow.process.task.api.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 完成流程任务请求。
 *
 * <p>携带审批表单摘要及下一节点审批人选择；服务端按真实任务重新校验操作权限。</p>
 */
@Data
public class TaskCompleteRequest {

    private String taskId;
    private String action;
    private String comment;
    private String transferTo;
    private String actionLabel;
    private Map<String, Object> formData;
    /** 本次审批表单和页面配置摘要；任务身份由 taskId 实时核验。 */
    private String formId;
    private String formConfigHash;
    private Map<String, Object> runtimeContext;
    /** 客户端声明只用于与服务端任务联合校验，不能单独授权。 */
    private String entityCode;
    private String recordId;
    private String listKey;
    private String nextApprovalScopeKey;
    private List<NextApproverSelectionRequest> nextApproverSelections;
}
