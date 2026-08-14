package com.workflow.process.task.api.response;

/**
 * 下一审批节点预测状态。
 */
public enum NextApprovalPreviewStatus {
    /** 已可靠计算出下一人工审批节点；流程结束时节点列表为空。 */
    READY,
    /** 中间存在自动、异步、等待、调用活动或汇聚节点，需待引擎运行后确定。 */
    DEFERRED,
    /** 配置或表达式无效，不能安全提交人工覆盖。 */
    BLOCKED
}
