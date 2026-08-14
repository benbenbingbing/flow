package com.workflow.process.task.api.request;

import lombok.Data;

import java.util.Map;

/**
 * 下一审批节点预览请求。
 */
@Data
public class NextApprovalPreviewRequest {

    private String action;
    private String actionLabel;
    private String comment;
    private Map<String, Object> formData;
}
