package com.workflow.process.action;

public enum FlowActionFailurePolicy {
    ROLLBACK,
    CONTINUE,
    RETRY,
    IGNORE
}
