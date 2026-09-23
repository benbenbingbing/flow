package com.workflow.process.sla.calendar.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 工作日历的发布快照，任务计算截止时间时按 timezoneId 解释本地工作时段。
 * weeklyPeriods 以星期一到日的 1..7 为键，exceptions 按日期覆盖每周规则。
 *
 * @param calendarCode 日历业务编码，供任务追踪配置来源
 * @param calendarName 发布时名称，供详情解释计时口径
 * @param version 发布版本，标识任务冻结的配置
 * @param timezoneId 本地工作时段所属时区，换算截止时间时使用
 * @param weeklyPeriods 按星期索引的常规工作时段，供累计工作分钟
 * @param exceptions 按日期索引的例外规则，命中时覆盖周时段
 */
public record WorkCalendarSnapshot(
        String calendarCode,
        String calendarName,
        int version,
        String timezoneId,
        Map<Integer, List<Period>> weeklyPeriods,
        Map<LocalDate, ExceptionDay> exceptions) {

    /**
     * 当日工作时段，以零点起算的分钟偏移表示，endMinute=1440 指向次日零点。
     *
     * @param startMinute 当地零点后的开始分钟
     * @param endMinute 当地零点后的结束分钟，1440 表示次日零点
     */
    public record Period(int startMinute, int endMinute) {
    }

    /**
     * 特殊日期规则；NON_WORKING 不计时，WORKING 使用自己的 periods 覆盖周规则。
     *
     * @param type WORKING 或 NON_WORKING，决定当天是否计时
     * @param name 特殊日期名称，供解释例外原因
     * @param periods WORKING 日期的独立时段，替换周规则
     */
    public record ExceptionDay(
            String type,
            String name,
            List<Period> periods) {
    }
}
