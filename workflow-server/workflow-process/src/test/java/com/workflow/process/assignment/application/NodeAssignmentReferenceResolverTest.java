package com.workflow.process.assignment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeAssignmentReferenceResolverTest {

    private final NodeAssignmentReferenceResolver resolver =
            new NodeAssignmentReferenceResolver(new ObjectMapper());

    @Test
    void resolvesCanonicalAndLegacyAliasReferencesRecursively() {
        UserTask current = task("current", """
                {"assignmentConfigVersion":2,
                 "assigneeType":"node_reference",
                 "referencedNodeId":"middle",
                 "referencedNodeName":"中间审批"}
                """);
        UserTask middle = task("middle", """
                {"assignmentConfigVersion":2,
                 "assigneeType":"nodeReference",
                 "sourceNodeId":"source"}
                """);
        UserTask source = task("source", """
                {"assignmentConfigVersion":2,
                 "assigneeType":"user",
                 "assigneeValue":"alice"}
                """);
        BpmnModel model = model(current, middle, source);

        var result = resolver.resolve(
                model, current, resolver.readAssigneeConfig(current));

        assertEquals(source, result.sourceTask());
        assertEquals("alice", result.assigneeConfig().get("assigneeValue"));
        assertEquals(List.of("current", "middle", "source"),
                result.chainNodeIds());
        assertTrue(result.referenced());
    }

    @Test
    void rejectsSelfCycleMissingNonUserTaskAndExpressions() {
        assertInvalid(
                task("self", reference("self")),
                model(task("self", reference("self"))),
                "形成环");

        UserTask first = task("first", reference("second"));
        UserTask second = task("second", reference("first"));
        assertInvalid(first, model(first, second), "形成环");

        UserTask missing = task("missing-source", reference("unknown"));
        assertInvalid(missing, model(missing), "不存在或不是 UserTask");

        UserTask nonUser = task("non-user", reference("service"));
        ServiceTask serviceTask = new ServiceTask();
        serviceTask.setId("service");
        BpmnModel nonUserModel = model(nonUser);
        nonUserModel.getMainProcess().addFlowElement(serviceTask);
        assertInvalid(nonUser, nonUserModel, "不存在或不是 UserTask");

        UserTask expression = task(
                "expression", reference("${targetNode}"));
        assertInvalid(expression, model(expression), "不能使用表达式");
    }

    @Test
    void rejectsReferenceChainsBeyondThePublishedLimit() {
        Process process = new Process();
        process.setId("process");
        UserTask first = null;
        for (int index = 0;
                index <= NodeAssignmentReferenceResolver.MAX_REFERENCE_DEPTH;
                index++) {
            String id = "node-" + index;
            UserTask task = task(id, reference("node-" + (index + 1)));
            if (first == null) {
                first = task;
            }
            process.addFlowElement(task);
        }
        process.addFlowElement(task(
                "node-" + (NodeAssignmentReferenceResolver.MAX_REFERENCE_DEPTH + 1),
                "{\"assigneeType\":\"user\",\"assigneeValue\":\"alice\"}"));
        BpmnModel model = new BpmnModel();
        model.addProcess(process);

        UserTask start = first;
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(
                        model,
                        start,
                        resolver.readAssigneeConfig(start)));
        assertTrue(exception.getMessage().contains("超过最大深度"));
    }

    @Test
    void acceptsExactlySixteenReferenceEdges() {
        Process process = new Process();
        process.setId("process");
        UserTask start = null;
        for (int index = 0;
                index < NodeAssignmentReferenceResolver.MAX_REFERENCE_DEPTH;
                index++) {
            UserTask task = task(
                    "allowed-" + index,
                    reference("allowed-" + (index + 1)));
            if (start == null) {
                start = task;
            }
            process.addFlowElement(task);
        }
        UserTask terminal = task(
                "allowed-" + NodeAssignmentReferenceResolver.MAX_REFERENCE_DEPTH,
                "{\"assigneeType\":\"user\",\"assigneeValue\":\"alice\"}");
        process.addFlowElement(terminal);
        BpmnModel model = new BpmnModel();
        model.addProcess(process);

        var result = resolver.resolve(
                model, start, resolver.readAssigneeConfig(start));

        assertEquals(terminal, result.sourceTask());
        assertEquals(NodeAssignmentReferenceResolver.MAX_REFERENCE_DEPTH + 1,
                result.chainNodeIds().size());
    }

    @Test
    void canonicalIdWinsAndTerminalLegacyDocumentsAreMerged() {
        UserTask current = task("current", """
                {"assigneeType":"node_reference",
                 "referencedNodeId":"canonical-source",
                 "sourceNodeId":"wrong-source"}
                """);
        UserTask canonical = task(
                "canonical-source",
                "{\"multiInstanceUserIds\":\"alice\"}",
                "{\"multiInstanceUsernames\":\"bob\","
                        + "\"multiInstanceGroupIds\":\"finance\"}");
        UserTask wrong = task(
                "wrong-source",
                "{\"assigneeType\":\"user\","
                        + "\"assigneeValue\":\"mallory\"}");

        var result = resolver.resolve(
                model(current, canonical, wrong),
                current,
                resolver.readAssigneeConfig(current));

        assertEquals(canonical, result.sourceTask());
        assertEquals(List.of("alice", "bob"),
                result.assigneeConfig().get("multiInstanceUsernames"));
        assertEquals(List.of("finance"),
                result.assigneeConfig().get("multiInstanceGroupCodes"));
    }

    @Test
    void assignmentModeUsesCurrentLoopAndSourceCandidateSemantics() {
        UserTask current = task("current", "{}");
        UserTask bpmnCandidates = task("bpmn-candidates", "{}");
        bpmnCandidates.setCandidateUsers(List.of("alice"));
        assertEquals("CANDIDATE",
                NodeAssignmentReferenceResolver.assignmentMode(
                        current, bpmnCandidates, Map.of()));

        UserTask v2Candidate = task("v2-candidate", "{}");
        assertEquals("CANDIDATE",
                NodeAssignmentReferenceResolver.assignmentMode(
                        current,
                        v2Candidate,
                        Map.of("assignmentConfigVersion", 2,
                                "assigneeType", "candidate",
                                "candidateUsers", "alice")));

        UserTask legacyGroups = task("legacy-groups", "{}");
        legacyGroups.setLoopCharacteristics(
                new org.flowable.bpmn.model.MultiInstanceLoopCharacteristics());
        assertEquals("CANDIDATE",
                NodeAssignmentReferenceResolver.assignmentMode(
                        current,
                        legacyGroups,
                        Map.of("multiInstanceGroupCodes", List.of("finance"))));

        current.setLoopCharacteristics(
                new org.flowable.bpmn.model.MultiInstanceLoopCharacteristics());
        assertEquals("MULTI_INSTANCE",
                NodeAssignmentReferenceResolver.assignmentMode(
                        current, bpmnCandidates, Map.of()));
    }

    private void assertInvalid(
            UserTask task,
            BpmnModel model,
            String message) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(
                        model,
                        task,
                        resolver.readAssigneeConfig(task)));
        assertTrue(exception.getMessage().contains(message),
                exception.getMessage());
    }

    private String reference(String nodeId) {
        return "{\"assignmentConfigVersion\":2,"
                + "\"assigneeType\":\"NODE_REFERENCE\","
                + "\"referencedNodeId\":\"" + nodeId + "\"}";
    }

    private BpmnModel model(UserTask... tasks) {
        Process process = new Process();
        process.setId("process");
        for (UserTask task : tasks) {
            process.addFlowElement(task);
        }
        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        return model;
    }

    private UserTask task(String id, String config) {
        UserTask task = new UserTask();
        task.setId(id);
        task.setName(id);
        ExtensionElement properties = extensionElement("properties");
        properties.addChildElement(property("assigneeConfig", config));
        task.addExtensionElement(properties);
        return task;
    }

    private UserTask task(
            String id,
            String assigneeConfig,
            String multiInstanceConfig) {
        UserTask task = task(id, assigneeConfig);
        task.getExtensionElements().get("properties").get(0)
                .addChildElement(property(
                        "multiInstanceConfig", multiInstanceConfig));
        return task;
    }

    private ExtensionElement property(String name, String value) {
        ExtensionElement property = extensionElement("property");
        property.addAttribute(new ExtensionAttribute("name", name));
        property.addAttribute(new ExtensionAttribute("value", value));
        return property;
    }

    private ExtensionElement extensionElement(String name) {
        ExtensionElement element = new ExtensionElement();
        element.setName(name);
        element.setNamespace("http://flowable.org/bpmn");
        element.setNamespacePrefix("flowable");
        return element;
    }
}
