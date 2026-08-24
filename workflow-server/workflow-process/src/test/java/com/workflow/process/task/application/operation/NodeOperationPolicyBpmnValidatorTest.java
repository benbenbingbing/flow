package com.workflow.process.task.application.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeOperationPolicyBpmnValidatorTest {

    private final NodeOperationPolicyBpmnValidator validator = new NodeOperationPolicyBpmnValidator(
            new NodeOperationPolicyParser(new ObjectMapper(), new NodeOperationConditionEvaluator()));

    @Test
    void acceptsSafeVersionedPolicy() {
        assertDoesNotThrow(() -> validator.validate(bpmn("""
                {
                  "version": 1,
                  "allowedVariables": ["amount"],
                  "operations": {
                    "approve": {"condition": "amount <= 5000"}
                  }
                }
                """)));
    }

    @Test
    void blocksUnsafePolicyAndReportsElementId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(bpmn("""
                        {
                          "version": 1,
                          "allowedVariables": ["amount"],
                          "operations": {
                            "approve": {"condition": "amount.toString() == '1'"}
                          }
                        }
                        """)));

        assertTrue(exception.getMessage().contains("elementId=approval-node"));
    }

    private String bpmn(String policyJson) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xmlns:flowable="http://flowable.org/bpmn"
                  targetNamespace="http://workflow.example/test">
                  <process id="operation-policy-test" isExecutable="true">
                    <startEvent id="start" />
                    <userTask id="approval-node" flowable:assignee="tester">
                      <extensionElements>
                        <flowable:properties>
                          <flowable:property name="nodeOperationPolicy" value="%s" />
                        </flowable:properties>
                      </extensionElements>
                    </userTask>
                    <endEvent id="end" />
                    <sequenceFlow id="to-task" sourceRef="start" targetRef="approval-node" />
                    <sequenceFlow id="to-end" sourceRef="approval-node" targetRef="end" />
                  </process>
                </definitions>
                """.formatted(xmlAttribute(policyJson));
    }

    private String xmlAttribute(String value) {
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
