package com.workflow.process.sla.runtime.application.model;

import com.workflow.process.sla.calendar.application.model.WorkCalendarResolutionSnapshot;
import com.workflow.process.sla.calendar.application.model.WorkCalendarSnapshot;
import com.workflow.process.sla.policy.application.model.TaskSlaPolicySnapshot;

/**
 * 节点发布版本保存的 SLA 入口配置。policySnapshot 定义计时和升级规则；
 * calendarSnapshot 用于固定日历，calendarResolutionSnapshot 用于按业务或发起人部门解析。
 * snapshotVersion 供后续读取器识别发布文档格式。
 *
 * @param enabled 关闭时任务初始化跳过 SLA
 * @param calendarSource 决定使用固定或作用域解析日历
 * @param businessFieldCode 业务部门字段，解析日历时从流程变量提取
 * @param policySnapshot 发布策略，复制到任务用于计时和升级
 * @param calendarSnapshot 固定日历，用于直接计算截止点
 * @param calendarResolutionSnapshot 作用域选择表，按部门或组织解析日历
 * @param snapshotVersion 文档格式版本，供读取器兼容
 */
public record PublishedTaskSlaConfig(
        boolean enabled,
        String calendarSource,
        String businessFieldCode,
        TaskSlaPolicySnapshot policySnapshot,
        WorkCalendarSnapshot calendarSnapshot,
        WorkCalendarResolutionSnapshot calendarResolutionSnapshot,
        int snapshotVersion) {
}
