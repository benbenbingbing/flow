package com.workflow.process.task.application.nextapproval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.task.api.request.NextApprovalPreviewRequest;
import com.workflow.process.task.api.response.NextApprovalPreviewResponse;
import com.workflow.process.task.api.response.NextApprovalPreviewStatus;
import com.workflow.process.task.api.response.NextApproverCandidateDTO;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NextApprovalPreviewServiceTest {

    @Test
    void blocksVisibleReadonlyNodeWhenDefaultAssigneeCannotBeResolved() {
        NextApprovalRouteService routeService =
                mock(NextApprovalRouteService.class);
        NextApproverCandidateService candidateService =
                mock(NextApproverCandidateService.class);
        NextApprovalPreviewService service =
                new NextApprovalPreviewService(
                        routeService, candidateService);
        NextApprovalPreviewRequest request =
                new NextApprovalPreviewRequest();
        NextApprovalTarget target = target(false);
        NextApprovalResolution resolution = resolution(target);
        when(routeService.resolve("task-1", request))
                .thenReturn(resolution);
        when(candidateService.defaultAssignees(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(target)))
                .thenReturn(List.of());

        NextApprovalPreviewResponse response =
                service.preview("task-1", request);

        assertEquals(NextApprovalPreviewStatus.BLOCKED,
                response.getStatus());
        assertTrue(response.getNodes().isEmpty());
        assertTrue(response.getMessage().contains("未解析到默认审批人"));
    }

    @Test
    void allowsVisibleEditableNodeToOpenSelectorWithNoDefaultAssignee() {
        NextApprovalRouteService routeService =
                mock(NextApprovalRouteService.class);
        NextApproverCandidateService candidateService =
                mock(NextApproverCandidateService.class);
        NextApprovalPreviewService service =
                new NextApprovalPreviewService(
                        routeService, candidateService);
        NextApprovalPreviewRequest request =
                new NextApprovalPreviewRequest();
        NextApprovalTarget target = target(true);
        NextApprovalResolution resolution = resolution(target);
        when(routeService.resolve("task-1", request))
                .thenReturn(resolution);
        when(candidateService.defaultAssignees(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(target)))
                .thenReturn(List.of());

        NextApprovalPreviewResponse response =
                service.preview("task-1", request);

        assertEquals(NextApprovalPreviewStatus.READY,
                response.getStatus());
        assertEquals(1, response.getNodes().size());
        assertTrue(response.getNodes().get(0).getAssignees().isEmpty());
    }

    @Test
    void previewsAssigneeWithCandidateUsersAsSingleDirectAssignment() {
        NextApprovalRouteService routeService =
                mock(NextApprovalRouteService.class);
        NextApproverCandidateService candidateService =
                mock(NextApproverCandidateService.class);
        NextApprovalPreviewService service =
                new NextApprovalPreviewService(
                        routeService, candidateService);
        NextApprovalPreviewRequest request =
                new NextApprovalPreviewRequest();
        UserTask userTask = configuredUserTask("manager-approval");
        userTask.setAssignee("alice");
        userTask.setCandidateUsers(List.of("bob"));
        NextApprovalTarget target =
                new NextApproverSelectionPolicyReader(new ObjectMapper())
                        .read("definition-1", userTask);
        NextApprovalResolution resolution = resolution(target);
        when(routeService.resolve("task-1", request))
                .thenReturn(resolution);
        when(candidateService.defaultAssignees(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(target)))
                .thenReturn(List.of(new NextApproverCandidateDTO(
                        "user-1", "alice", "Alice")));

        NextApprovalPreviewResponse response =
                service.preview("task-1", request);

        assertEquals(NextApprovalPreviewStatus.READY, response.getStatus());
        assertEquals("DIRECT",
                response.getNodes().get(0).getAssignmentMode());
        assertEquals(false, response.getNodes().get(0).isMultiple());
        assertEquals(List.of("alice"),
                response.getNodes().get(0).getAssignees().stream()
                        .map(NextApproverCandidateDTO::getUsername)
                        .toList());
    }

    private NextApprovalResolution resolution(NextApprovalTarget target) {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(task.getProcessDefinitionId()).thenReturn("definition-1");
        return new NextApprovalResolution(
                task,
                NextApprovalPreviewStatus.READY,
                null,
                "scope-1",
                List.of(target),
                Map.of());
    }

    private NextApprovalTarget target(boolean editable) {
        UserTask userTask = new UserTask();
        userTask.setId("manager-approval");
        userTask.setName("经理审批");
        NextApproverSelectionPolicy policy =
                new NextApproverSelectionPolicy(
                        true,
                        1,
                        true,
                        editable,
                        "DIRECT",
                        false,
                        NextApproverSelectionPolicy.SourceType.SCOPE,
                        List.of(new NextApproverSelectionPolicy.Scope(
                                NextApproverSelectionPolicy.ScopeType.USER,
                                List.of("manager"),
                                false)),
                        null,
                        Map.of(),
                        "policy-scope");
        return new NextApprovalTarget(userTask, Map.of(), policy);
    }

    private UserTask configuredUserTask(String id) {
        UserTask task = new UserTask();
        task.setId(id);
        task.setName("经理审批");
        ExtensionElement properties = extensionElement("properties");
        ExtensionElement property = extensionElement("property");
        property.addAttribute(new ExtensionAttribute(
                "name", "assigneeConfig"));
        property.addAttribute(new ExtensionAttribute("value", """
                {
                  "nextApproverSelection": {
                    "version": 1,
                    "visible": true,
                    "editable": true,
                    "source": {
                      "type": "SCOPE",
                      "rules": [{"type":"ALL_USERS","values":[]}]
                    }
                  }
                }
                """));
        properties.addChildElement(property);
        task.addExtensionElement(properties);
        return task;
    }

    private ExtensionElement extensionElement(String name) {
        ExtensionElement element = new ExtensionElement();
        element.setName(name);
        element.setNamespace("http://flowable.org/bpmn");
        element.setNamespacePrefix("flowable");
        return element;
    }
}
