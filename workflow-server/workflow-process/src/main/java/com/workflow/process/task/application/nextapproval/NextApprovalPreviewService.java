package com.workflow.process.task.application.nextapproval;

import com.workflow.process.task.api.request.NextApprovalPreviewRequest;
import com.workflow.process.task.api.response.NextApprovalNodeDTO;
import com.workflow.process.task.api.response.NextApprovalPreviewResponse;
import com.workflow.process.task.api.response.NextApprovalPreviewStatus;
import com.workflow.process.task.api.response.NextApproverCandidateDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 组装审批面板所需的下一节点与当前默认审批人信息。
 */
@Service
@RequiredArgsConstructor
public class NextApprovalPreviewService {

    private final NextApprovalRouteService routeService;
    private final NextApproverCandidateService candidateService;

    public NextApprovalPreviewResponse preview(
            String taskId,
            NextApprovalPreviewRequest request) {
        NextApprovalResolution resolution = routeService.resolve(
                taskId, request);
        NextApprovalPreviewResponse response = new NextApprovalPreviewResponse();
        response.setTaskId(resolution.task().getId());
        response.setProcessDefinitionId(
                resolution.task().getProcessDefinitionId());
        response.setStatus(resolution.status());
        response.setMessage(resolution.message());
        response.setScopeKey(resolution.scopeKey());
        if (!resolution.ready()) {
            return response;
        }

        try {
            for (NextApprovalTarget target : resolution.targets()) {
                NextApproverSelectionPolicy policy =
                        target.selectionPolicy();
                if (!policy.visible()) {
                    continue;
                }
                NextApprovalNodeDTO node = new NextApprovalNodeDTO();
                node.setNodeId(target.userTask().getId());
                node.setNodeName(target.userTask().getName());
                node.setVisible(true);
                node.setEditable(policy.editable());
                node.setAssignmentMode(policy.assignmentMode());
                node.setMultiple(policy.multiple());
                node.setSourceType(policy.sourceType() == null
                        ? null : policy.sourceType().name());
                List<NextApproverCandidateDTO> defaultAssignees =
                        candidateService.defaultAssignees(
                                resolution, target);
                if (!policy.editable() && defaultAssignees.isEmpty()) {
                    throw new IllegalStateException(
                            "下一节点“" + node.getNodeName()
                                    + "”未解析到默认审批人，无法继续");
                }
                node.setAssignees(new ArrayList<>(defaultAssignees));
                response.getNodes().add(node);
            }
        } catch (RuntimeException exception) {
            response.setStatus(NextApprovalPreviewStatus.BLOCKED);
            response.setMessage(exception.getMessage());
            response.setScopeKey(null);
            response.getNodes().clear();
        }
        return response;
    }
}
