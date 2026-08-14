package com.workflow.process.task.api.request;

import lombok.Data;

import java.util.List;

/**
 * 审批提交时对某个下一节点的人工审批人覆盖。
 */
@Data
public class NextApproverSelectionRequest {

    private String nodeId;
    private List<String> userKeys;
}
