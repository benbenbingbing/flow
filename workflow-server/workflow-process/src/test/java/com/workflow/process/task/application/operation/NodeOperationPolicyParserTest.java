package com.workflow.process.task.application.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeOperationPolicyParserTest {

    private final NodeOperationPolicyParser parser = new NodeOperationPolicyParser(
            new ObjectMapper(), new NodeOperationConditionEvaluator());

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
}
