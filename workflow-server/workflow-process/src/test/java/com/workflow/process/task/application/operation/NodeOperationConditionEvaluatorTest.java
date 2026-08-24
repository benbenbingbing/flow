package com.workflow.process.task.application.operation;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeOperationConditionEvaluatorTest {

    private final NodeOperationConditionEvaluator evaluator = new NodeOperationConditionEvaluator();

    @Test
    void evaluatesDeclaredVariablesAndMapPaths() {
        NodeOperationConditionEvaluator.CompiledCondition condition = evaluator.compile(
                "amount >= 100 && process.status == 'OPEN' && !request.forceDenied",
                Set.of("amount"));

        assertTrue(condition.evaluate(Map.of(
                "amount", 120,
                "process", Map.of("status", "OPEN"),
                "request", Map.of("forceDenied", false))));
        assertFalse(condition.evaluate(Map.of(
                "amount", 99,
                "process", Map.of("status", "OPEN"),
                "request", Map.of("forceDenied", false))));
    }

    @Test
    void rejectsUndeclaredVariables() {
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.compile("secretAmount > 0", Set.of("amount")));
    }

    @Test
    void rejectsMethodCallsAndArbitraryScripts() {
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.compile("java.lang.Runtime.getRuntime() == null", Set.of("java")));
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.compile("amount > 0; process.exit", Set.of("amount")));
    }

    @Test
    void rejectsStaticallyNonBooleanResult() {
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.compile("'not-a-boolean'", Set.of()));
    }
}
