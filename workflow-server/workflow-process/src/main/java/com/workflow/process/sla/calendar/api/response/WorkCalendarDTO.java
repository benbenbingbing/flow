package com.workflow.process.sla.calendar.api.response;

import com.workflow.process.sla.calendar.application.model.WorkCalendarSnapshot;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendar;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendarBinding;

import java.util.List;

/**
 * 日历详情响应；snapshot 供时段预览，bindings 供管理端检查适用范围。
 *
 * @param calendar 日历基本信息，供管理端显示版本和状态
 * @param snapshot 发布时段和特殊日期，供预览及计算核对
 * @param bindings 适用范围绑定，供检查日历选择规则
 */
public record WorkCalendarDTO(
        WorkCalendar calendar,
        WorkCalendarSnapshot snapshot,
        List<WorkCalendarBinding> bindings) {
}
