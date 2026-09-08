package com.workflow.process.definition.application;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 发布校验与历史部署安全闸共用表达式语法的回归测试。 */
class BpmnExecutableContentValidatorTest {

    @Test
    void acceptsStructuredDataConditions() {
        assertTrue(safe("${true}"));
        assertTrue(safe("${approved == 'approve'}"));
        assertTrue(safe("${amount >= 1000 && amount <= 5000}"));
        assertTrue(safe("${approved != 'reject' || urgent == true}"));
        assertTrue(safe("${(amount > 0 && enabled) || override}"));
        assertTrue(safe("${label == 'a=b -> literal'}"));
        assertTrue(safe("${label == 'a,b'}"));
    }

    @Test
    void rejectsAssignmentLambdaAndDynamicInvocation() {
        assertFalse(safe("${foo = true}"));
        assertFalse(safe("${foo += 1}"));
        assertFalse(safe("${foo === true}"));
        assertFalse(safe("${foo !== true}"));
        assertFalse(safe("${x -> x}"));
        assertFalse(safe("${(x->x)(true)}"));
        assertFalse(safe("${(candidate)(true)}"));
        assertFalse(safe("${}"));
        assertFalse(safe("${   }"));
        assertFalse(safe("${left, right}"));
    }

    @Test
    void deployedSafetyUsesTheSameHardenedGrammar() {
        BpmnModel safeModel = modelWithSkip(
                "safe-review", "${amount >= 100}");
        BpmnModel assignmentModel = modelWithSkip(
                "assignment-review", "${foo = true}");
        BpmnModel lambdaModel = modelWithSkip(
                "lambda-review", "${(x->x)(true)}");

        assertNull(DeployedSkipExpressionSafety.firstUnsafeElementId(
                safeModel));
        assertEquals(
                "assignment-review",
                DeployedSkipExpressionSafety.firstUnsafeElementId(
                        assignmentModel));
        assertEquals(
                "lambda-review",
                DeployedSkipExpressionSafety.firstUnsafeElementId(
                        lambdaModel));
    }

    private boolean safe(String expression) {
        return BpmnExecutableContentValidator.isSafeDataExpression(
                expression);
    }

    private BpmnModel modelWithSkip(
            String taskId,
            String skipExpression) {
        UserTask task = new UserTask();
        task.setId(taskId);
        task.setSkipExpression(skipExpression);
        Process process = new Process();
        process.setId("test-process");
        process.addFlowElement(task);
        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        return model;
    }
}
