package com.workflow.process.task.api.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个可预览的下一人工审批节点。
 */
@Data
public class NextApprovalNodeDTO {

    private String nodeId;
    private String nodeName;
    private boolean visible;
    private boolean editable;
    private String assignmentMode;
    private boolean multiple;
    private String sourceType;
    private List<NextApproverCandidateDTO> assignees = new ArrayList<>();
}
