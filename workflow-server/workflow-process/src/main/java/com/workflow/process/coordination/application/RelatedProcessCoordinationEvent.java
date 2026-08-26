package com.workflow.process.coordination.application;

import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.Command;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.TargetImpact;

/**
 * 一个目标流程的 Outbox 执行快照。
 *
 * <p>快照只能由服务端计划发布器生成。消费器不会相信其中的
 * 记录或流程 ID，而是使用同一已发布关系路径按原操作人重新解析并对比。</p>
 */
public record RelatedProcessCoordinationEvent(
        RelatedProcessCoordinationPlan originalPlan,
        Command command,
        TargetImpact target) {
}
