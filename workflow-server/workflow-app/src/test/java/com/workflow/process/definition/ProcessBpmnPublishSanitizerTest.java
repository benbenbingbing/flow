package com.workflow.process.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
