package com.workflow.process.action;

/**
 * 流程动作执行方式。
 *
 * <p>决定动作在流程事务内同步执行，还是提交后异步执行。</p>
 */
public enum FlowActionExecutionMode {
    /** 事务内执行：动作在主流程事务中同步执行，失败可回滚主事务 */
    IN_TRANSACTION,
    /** 提交后执行：主事务提交后异步执行，失败可重试 */
    AFTER_COMMIT
}
