package com.workflow.process.task.api.request;

import lombok.Data;

import java.util.Map;

/**
 * 下一节点可选审批人分页请求。
 */
@Data
public class NextApproverOptionsRequest {

    private String targetNodeId;
    private String scopeKey;
    private String action;
    private String actionLabel;
    private String comment;
    private Map<String, Object> formData;
    private String keyword;
    private Integer pageNum;
    private Integer pageSize;
}
