package com.workflow.process.task.application.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeOperationPolicyParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NodeOperationConfigReader operationConfigReader =
            new NodeOperationConfigReader(objectMapper);
    private final NodeOperationPolicyParser parser = new NodeOperationPolicyParser(
            objectMapper, new NodeOperationConditionEvaluator(), operationConfigReader);

    @Test
    void parsesVersionedPolicyAndDisablesUnlistedOperations() {
        NodeOperationPolicy policy = parser.parse("""
                {
                  "version": 1,
                  "allowedVariables": ["amount"],
                  "operations": {
                    "approve": {
                      "enabled": true,
                      "condition": "amount <= 5000",
                      "permissionCode": "task:approve",
                      "reasonRequired": true,
                      "reasonTemplates": ["同意"]
                    }
                  }
                }
                """);

        assertTrue(policy.configured());
        assertEquals("task:approve",
                policy.rule(NodeOperationPolicy.Operation.APPROVE).permissionCode());
        assertFalse(policy.rule(NodeOperationPolicy.Operation.REJECT).enabled());
    }

    @Test
    void keepsLegacyCompatibilityOnlyWhenPolicyIsAbsent() {
        NodeOperationPolicy policy = parser.parse("  ");

        assertFalse(policy.configured());
        assertTrue(policy.rule(NodeOperationPolicy.Operation.TERMINATE).enabled());
    }

    @Test
    void givesSimpleSwitchesPriorityOverNestedLegacyPolicy() {
        UserTask task = userTaskWithAssigneeConfig("""
                {
                  "allowTransfer": true,
                  "nodeOperationPolicy": {
                    "version": 1,
                    "operations": {
                      "transfer": {"enabled": false}
                    }
                  }
                }
                """);

        NodeOperationPolicy policy = parser.parse(task);

        assertFalse(policy.configured());
        assertTrue(policy.rule(NodeOperationPolicy.Operation.TRANSFER).enabled());
    }

    @Test
    void keepsNestedLegacyPolicyWhenSimpleSwitchesAreAbsent() {
        UserTask task = userTaskWithAssigneeConfig("""
                {
                  "nodeOperationPolicy": {
                    "version": 1,
                    "operations": {
                      "transfer": {"enabled": false},
                      "terminate": {"enabled": true}
                    }
                  }
                }
                """);

        NodeOperationPolicy policy = parser.parse(task);

        assertTrue(policy.configured());
        assertFalse(policy.rule(NodeOperationPolicy.Operation.TRANSFER).enabled());
        assertTrue(policy.rule(NodeOperationPolicy.Operation.TERMINATE).enabled());
    }

    @Test
    void rejectsInvalidSpecificConstraints() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                {
                  "version": 1,
                  "operations": {
                    "approve": {
                      "reasonTemplateRequired": true,
                      "reasonTemplates": []
                    }
                  }
                }
                """));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                {
                  "version": 1,
                  "operations": {
                    "transfer": {"targetScope": "FIXED", "targetIds": []}
                  }
                }
                """));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                {
                  "version": 1,
                  "operations": {
                    "approve": {"condition": "undeclared > 0"}
                  }
                }
                """));
    }

    private UserTask userTaskWithAssigneeConfig(String config) {
        UserTask task = new UserTask();
        ExtensionElement properties = extensionElement("properties");
        ExtensionElement property = extensionElement("property");
        property.addAttribute(new ExtensionAttribute("name", "assigneeConfig"));
        property.addAttribute(new ExtensionAttribute("value", config));
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
