package com.workflow.process.task.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.result.PageResult;
import com.workflow.process.task.api.request.TaskCompleteRequest;
import com.workflow.process.task.api.request.NextApproverOptionsRequest;
import com.workflow.process.task.api.response.NextApprovalNodeDTO;
import com.workflow.process.task.api.response.NextApprovalPreviewResponse;
import com.workflow.process.task.api.response.NextApprovalPreviewStatus;
import com.workflow.process.task.api.response.NextApproverCandidateDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NextApprovalApiContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void completeRequestKeepsOneTopLevelScopeKeyAndNodeScopedSelections() throws Exception {
        TaskCompleteRequest request = objectMapper.readValue("""
                {
                  "taskId": "task-7",
                  "action": "approve",
                  "nextApprovalScopeKey": "scope-route-7",
                  "nextApproverSelections": [
                    {
                      "nodeId": "finance-review",
                      "userKeys": ["alice", "bob"]
                    }
                  ]
                }
                """, TaskCompleteRequest.class);

        assertEquals("scope-route-7", request.getNextApprovalScopeKey());
        assertEquals("finance-review", request.getNextApproverSelections().get(0).getNodeId());
        assertEquals(List.of("alice", "bob"),
                request.getNextApproverSelections().get(0).getUserKeys());

        JsonNode json = objectMapper.valueToTree(request);
        JsonNode selection = json.path("nextApproverSelections").get(0);
        assertEquals("scope-route-7", json.path("nextApprovalScopeKey").asText());
        assertFalse(selection.has("scopeKey"),
                "scopeKey belongs to the predicted route as a whole, not an individual node");
    }

    @Test
    void optionsRequestCarriesEveryInputThatCanAffectRoutePrediction() throws Exception {
        NextApproverOptionsRequest request = objectMapper.readValue("""
                {
                  "targetNodeId": "finance-review",
                  "scopeKey": "scope-route-7",
                  "action": "approve",
                  "actionLabel": "同意",
                  "comment": "条件备注",
                  "formData": {"amount": 2000},
                  "keyword": "alice",
                  "pageNum": 2,
                  "pageSize": 20
                }
                """, NextApproverOptionsRequest.class);

        assertEquals("finance-review", request.getTargetNodeId());
        assertEquals("scope-route-7", request.getScopeKey());
        assertEquals("条件备注", request.getComment());
        assertEquals(2000, request.getFormData().get("amount"));
        assertEquals(2, request.getPageNum());
        assertEquals(20, request.getPageSize());
    }

    @Test
    void previewResponseUsesNodesAndExposesAssignmentModeWithTopLevelScopeKey() {
        NextApproverCandidateDTO assignee =
                new NextApproverCandidateDTO("user-1", "alice", "Alice");
        NextApprovalNodeDTO node = new NextApprovalNodeDTO();
        node.setNodeId("finance-review");
        node.setNodeName("财务审批");
        node.setVisible(true);
        node.setEditable(true);
        node.setAssignmentMode("CANDIDATE");
        node.setMultiple(true);
        node.setSourceType("SCOPE");
        node.setAssignees(List.of(assignee));

        NextApprovalPreviewResponse response = new NextApprovalPreviewResponse();
        response.setTaskId("task-7");
        response.setProcessDefinitionId("definition-7");
        response.setStatus(NextApprovalPreviewStatus.READY);
        response.setScopeKey("scope-route-7");
        response.setNodes(List.of(node));

        JsonNode json = objectMapper.valueToTree(response);
        assertEquals("scope-route-7", json.path("scopeKey").asText());
        assertTrue(json.has("nodes"));
        assertFalse(json.has("nextNodes"));
        assertEquals("CANDIDATE", json.path("nodes").get(0).path("assignmentMode").asText());
        assertFalse(json.path("nodes").get(0).has("scopeKey"));
    }

    @Test
    void candidateOptionsUseTheSharedPageResultShape() {
        PageResult<NextApproverCandidateDTO> response = new PageResult<>(
                List.of(new NextApproverCandidateDTO("user-1", "alice", "Alice")),
                37,
                2,
                20);

        JsonNode json = objectMapper.valueToTree(response);
        assertTrue(json.path("records").isArray());
        assertEquals("alice", json.path("records").get(0).path("username").asText());
        assertEquals(37, json.path("total").asLong());
        assertEquals(2, json.path("pageNum").asLong());
        assertEquals(20, json.path("pageSize").asLong());
    }
}
