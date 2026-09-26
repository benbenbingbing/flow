package com.workflow.contracts.process.action.model;

/** 失败后的处理决定。由平台实施，策略不得直接修改流程或执行队列。 */
public enum FailureDisposition {
    ROLLBACK, CONTINUE, RETRY, IGNORE, MANUAL
}
