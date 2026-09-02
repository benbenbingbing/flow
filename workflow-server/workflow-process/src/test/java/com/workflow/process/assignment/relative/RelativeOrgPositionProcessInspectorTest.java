package com.workflow.process.assignment.relative;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RelativeOrgPositionProcessInspectorTest {

    @Test
    void onlyRelativeDeploymentsRequireSnapshot() {
        RepositoryService repository = mock(RepositoryService.class);
        RelativeOrgPositionProcessInspector inspector =
                new RelativeOrgPositionProcessInspector(
                        repository, new ObjectMapper());
        when(repository.getBpmnModel("ordinary"))
                .thenReturn(model(task(
                        "ordinaryTask",
                        "{\"assignmentConfigVersion\":2,"
                                + "\"assigneeType\":\"user\","
                                + "\"assigneeValue\":\"alice\"}")));
        when(repository.getBpmnModel("relative"))
                .thenReturn(model(task(
                        "relativeTask",
                        "{\"assignmentConfigVersion\":2,"
                                + "\"assigneeType\":\"interface\","
                                + "\"resolverCode\":"
                                + "\"relativeOrgPosition\"}")));

        assertFalse(inspector.requiresInitiatorOrganizationSnapshot(
                "ordinary"));
        assertTrue(inspector.requiresInitiatorOrganizationSnapshot(
                "relative"));
    }

    @Test
    void malformedDocumentMentioningRelativeResolverIsConservative() {
        RepositoryService repository = mock(RepositoryService.class);
        RelativeOrgPositionProcessInspector inspector =
                new RelativeOrgPositionProcessInspector(
                        repository, new ObjectMapper());
        when(repository.getBpmnModel("damaged"))
                .thenReturn(model(task(
                        "relativeTask",
                        "{\"resolverCode\":\"relativeOrgPosition\"")));

        assertTrue(inspector.requiresInitiatorOrganizationSnapshot(
                "damaged"));
    }

    @Test
    void legacyStaticAssignmentIgnoresStaleBaseRelativeResolver() {
        RepositoryService repository = mock(RepositoryService.class);
        RelativeOrgPositionProcessInspector inspector =
                new RelativeOrgPositionProcessInspector(
                        repository, new ObjectMapper());
        UserTask legacyTask = task(
                        "legacyReview",
                        "{\"multiInstanceUsernames\":[\"alice\"],"
                                + "\"assigneeType\":\"interface\","
                                + "\"resolverCode\":"
                                + "\"relativeOrgPosition\"}");
        legacyTask.setLoopCharacteristics(
                new MultiInstanceLoopCharacteristics());
        when(repository.getBpmnModel("legacy-static"))
                .thenReturn(model(legacyTask));

        assertFalse(inspector.requiresInitiatorOrganizationSnapshot(
                "legacy-static"));
    }

    private BpmnModel model(UserTask task) {
        org.flowable.bpmn.model.Process process =
                new org.flowable.bpmn.model.Process();
        process.setId("process");
        process.addFlowElement(task);
        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        return model;
    }

    private UserTask task(String id, String config) {
        UserTask task = new UserTask();
        task.setId(id);
        ExtensionElement properties = extension("properties");
        ExtensionElement property = extension("property");
        property.addAttribute(new ExtensionAttribute(
                "name", "assigneeConfig"));
        property.addAttribute(new ExtensionAttribute(
                "value", config));
        properties.addChildElement(property);
        task.addExtensionElement(properties);
        return task;
    }

    private ExtensionElement extension(String name) {
        ExtensionElement element = new ExtensionElement();
        element.setName(name);
        return element;
    }
}
