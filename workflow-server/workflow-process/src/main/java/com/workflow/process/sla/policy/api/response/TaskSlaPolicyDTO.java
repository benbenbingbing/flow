package com.workflow.process.sla.policy.api.response;

import com.workflow.process.sla.policy.application.TaskSlaPolicySnapshot;
import com.workflow.process.sla.policy.infrastructure.persistence.record.TaskSlaPolicy;

/**
 * 策略详情响应；snapshot 展开升级步骤供发布前检查和管理端回显。
 *
 * @param policy 策略基本信息，供管理端显示状态
 * @param snapshot 发布规则和升级步骤，供回显和检查
 */
public record TaskSlaPolicyDTO(
        TaskSlaPolicy policy,
        TaskSlaPolicySnapshot snapshot) {
}
