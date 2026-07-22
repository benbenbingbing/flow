package com.workflow.process.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.CallActivity;
import org.flowable.bpmn.model.ServiceTask;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessBpmnPublishSanitizerTest {

    @Test
    void sanitizeConvertsCamundaAttributesAndUsesProcessKey() {
        ProcessBpmnPublishSanitizer sanitizer = new ProcessBpmnPublishSanitizer(new ObjectMapper());
        String input = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:camunda="http://camunda.org/schema/1.0/bpmn">
                  <bpmn:process id="draft_process">
                    <bpmn:userTask id="task-1" name="审批" camunda:assignee="admin" />
                  </bpmn:process>
                  <bpmndi:BPMNPlane id="plane-1" bpmnElement="draft_process" />
                </bpmn:definitions>
                """;

        String result = sanitizer.sanitize(input, "expense_flow");

        assertTrue(result.contains("<bpmn:process id=\"expense_flow\""));
        assertTrue(result.contains("flowable:assignee=\"admin\""));
        assertTrue(result.contains("xmlns:flowable=\"http://flowable.org/bpmn\""));
        assertTrue(result.contains("bpmnElement=\"expense_flow\""));
        assertFalse(result.contains("camunda:"));
    }

    @Test
    void sanitizeTurnsConfiguredNodesIntoExecutableRuntimeBpmn() {
        ProcessBpmnPublishSanitizer sanitizer = new ProcessBpmnPublishSanitizer(new ObjectMapper());
        String input = wrap("""
                <bpmn:startEvent id="start" />
                <bpmn:serviceTask id="rest">
                  %s
                </bpmn:serviceTask>
                <bpmn:sendTask id="send">
                  %s
                </bpmn:sendTask>
                <bpmn:businessRuleTask id="rule">
                  %s
                </bpmn:businessRuleTask>
                <bpmn:callActivity id="call">
                  %s
                </bpmn:callActivity>
                <bpmn:endEvent id="end" />
                """.formatted(
                properties(
                        property("restConfig", """
                                {"method":"POST","url":"http://localhost:8080/api/demo/hello","contentType":"application/json"}
                                """),
                        property("serviceResultVariable", "restResult")),
                properties(property("sendConfig", """
                        {"channels":["message"],"to":"admin","subject":"测试","content":"内容","templateKey":"PROCESS_SUBMIT"}
                        """)),
                properties(property("ruleConfig", """
                        {"decisionRef":"approvalDecision","inputVariables":"{\\"amount\\":\\"${amount}\\"}","resultVariable":"decisionResult","mapDecisionResult":true}
                        """)),
                properties(property("callConfig", """
                        {"calledElement":"child_process","callActivityType":"bpmn","inputParameters":"{\\"childAmount\\":\\"${amount}\\"}","outputParameters":"{\\"result\\":\\"${childResult}\\"}","businessKey":"${businessKey}"}
                        """))));

        String result = sanitizer.sanitize(input, "runtime_process");

        assertTrue(result.contains("id=\"rest\" flowable:delegateExpression=\"${restServiceTaskDelegate}\""));
        assertTrue(result.contains("flowable:resultVariableName=\"restResult\""));
        assertTrue(result.contains("<bpmn:serviceTask id=\"send\""));
        assertTrue(result.contains("flowable:delegateExpression=\"${configuredSendTaskDelegate}\""));
        assertFalse(result.contains("<bpmn:sendTask"));
        assertTrue(result.contains("<bpmn:serviceTask id=\"rule\""));
        assertTrue(result.contains("flowable:delegateExpression=\"${configuredDmnTaskDelegate}\""));
        assertFalse(result.contains("<bpmn:businessRuleTask"));
        assertTrue(result.contains("calledElement=\"child_process\""));
        assertTrue(result.contains("flowable:businessKey=\"${businessKey}\""));
        assertTrue(result.contains("<flowable:in sourceExpression=\"${amount}\" target=\"childAmount\" />"));
        assertTrue(result.contains("<flowable:out sourceExpression=\"${childResult}\" target=\"result\" />"));

        BpmnModel model = new BpmnXMLConverter().convertToBpmnModel(
                () -> new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8)),
                true,
                false);
        assertTrue(model.getFlowElement("send") instanceof ServiceTask);
        assertTrue(model.getFlowElement("rule") instanceof ServiceTask);
        assertTrue(model.getFlowElement("call") instanceof CallActivity);
        assertEquals("child_process", ((CallActivity) model.getFlowElement("call")).getCalledElement());
    }

    @Test
    void sanitizeRejectsIncompleteConfiguredNodesBeforeDeployment() {
        ProcessBpmnPublishSanitizer sanitizer = new ProcessBpmnPublishSanitizer(new ObjectMapper());

        IllegalArgumentException sendError = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap("<bpmn:sendTask id=\"send\">"
                                + properties(property("sendConfig", "{\"channels\":[\"message\"],\"to\":\"\"}"))
                                + "</bpmn:sendTask>"),
                        "runtime_process"));
        assertTrue(sendError.getMessage().contains("接收人"));

        IllegalArgumentException callError = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap("<bpmn:callActivity id=\"call\">"
                                + properties(property("callConfig", "{\"calledElement\":\"\"}"))
                                + "</bpmn:callActivity>"),
                        "runtime_process"));
        assertTrue(callError.getMessage().contains("子流程Key"));
    }

    private static String wrap(String elements) {
        return """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:flowable="http://flowable.org/bpmn"
                    targetNamespace="http://workflow.test/process">
                  <bpmn:process id="draft_process" isExecutable="true">
                    %s
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(elements);
    }

    private static String properties(String... values) {
        return "<bpmn:extensionElements><flowable:properties>"
                + String.join("", values)
                + "</flowable:properties></bpmn:extensionElements>";
    }

    private static String property(String name, String value) {
        return "<flowable:property name=\"" + name + "\" value=\"" + escape(value.trim()) + "\" />";
    }

    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
