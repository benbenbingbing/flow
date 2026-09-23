package com.workflow.process.sla.calendar.api.request;

import java.time.OffsetDateTime;

/**
 * 日历模拟输入；从 startAt 累计 minutes 工作分钟，仅返回预演截止时间。
 *
 * @param startAt 模拟起点，转换到日历时区后进入工作窗口
 * @param minutes 待累计的工作分钟数，决定预演截止时间
 */
public record WorkCalendarSimulationRequest(
        OffsetDateTime startAt,
        Integer minutes) {
}
