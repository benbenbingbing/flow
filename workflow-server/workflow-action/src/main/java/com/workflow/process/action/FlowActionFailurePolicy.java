package com.workflow.process.action;

/**
 * 流程动作失败策略。
 *
 * <p>定义动作执行失败后系统的处理方式，不同执行方式支持的策略不同。</p>
 */
public enum FlowActionFailurePolicy {
    /** 回滚：事务内动作失败时回滚主流程事务 */
    ROLLBACK,
    /** 继续：事务内动作失败时仅记录失败，不影响主流程事务 */
    CONTINUE,
    /** 重试：提交后动作失败时按指数退避自动重试，直至次数耗尽进入死信 */
    RETRY,
    /** 忽略：提交后动作失败时直接标记为死信，不再重试 */
    IGNORE
}
