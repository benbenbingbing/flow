package com.workflow.process.task.infrastructure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 多实例变量名清洗必须与前端 process-config sanitize 规则一致。
 */
class MultiInstanceVariableNamesTest {

    @Test
    void sanitizesHyphenatedNodeIds() {
        assertEquals("finance_review",
                MultiInstanceVariableNames.sanitizeNodeId("finance-review"));
        assertEquals(
                "_wf_mi_approved_count_finance_review",
                MultiInstanceVariableNames.buildApprovedCountVariableName(
                        "finance-review"));
        assertEquals(
                "_wf_mi_rejected_finance_review",
                MultiInstanceVariableNames.buildRejectedVariableName(
                        "finance-review"));
        assertEquals(
                "_wfMultiInstanceUsers_finance_review",
                MultiInstanceVariableNames.buildCollectionVariableName(
                        "finance-review"));
    }

    @Test
    void emptyNodeIdFallsBackToNode() {
        assertEquals("node", MultiInstanceVariableNames.sanitizeNodeId(""));
        assertEquals("node", MultiInstanceVariableNames.sanitizeNodeId(null));
        assertEquals("node", MultiInstanceVariableNames.sanitizeNodeId("___"));
    }
}
