package com.workflow.process.task.api.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 下一审批节点预览结果。
 */
@Data
public class NextApprovalPreviewResponse {

    private String taskId;
    private String processDefinitionId;
    private NextApprovalPreviewStatus status;
    private String message;
    private String scopeKey;
    private List<NextApprovalNodeDTO> nodes = new ArrayList<>();
}
