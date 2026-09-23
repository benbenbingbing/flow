package com.workflow.process.sla.calendar.api.request;

import java.time.LocalDate;
import java.util.List;

/**
 * 工作日历草稿请求。timezoneId 用于解释 periods 的本地分钟偏移；
 * exceptions 覆盖指定日期的周规则，bindings 决定部门或组织何时使用此日历。
 *
 * @param calendarCode 日历业务编码，供草稿和发布版本引用
 * @param calendarName 管理端展示名称
 * @param timezoneId 解释本地时段的时区，后续换算绝对截止时间
 * @param description 草稿说明，供配置回显
 * @param defaultFlag 是否供未命中绑定的任务使用
 * @param effectiveFrom 日历起始生效日期
 * @param effectiveTo 日历结束生效日期
 * @param periods 每周工作时段，发布后用于累计工作分钟
 * @param exceptions 特殊日期覆盖规则，优先于周时段
 * @param bindings 部门或组织适用范围，发布时形成选择表
 */
public record WorkCalendarSaveRequest(
        String calendarCode,
        String calendarName,
        String timezoneId,
        String description,
        Boolean defaultFlag,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        List<PeriodRequest> periods,
        List<ExceptionRequest> exceptions,
        List<BindingRequest> bindings) {

    /**
     * 每周工作时段，dayOfWeek 为 1..7，起止分钟用于截止时间累计。
     *
     * @param dayOfWeek 星期一到日的 1..7，决定周期时段归属
     * @param startMinute 当地零点起算的开始分钟
     * @param endMinute 当地零点起算的结束分钟
     */
    public record PeriodRequest(
            Integer dayOfWeek,
            Integer startMinute,
            Integer endMinute) {
    }

    /**
     * 指定日期的工作/非工作覆盖规则，WORKING 时使用其独立时段。
     *
     * @param date 被覆盖的当地日期
     * @param type WORKING 或 NON_WORKING，决定当天是否计时
     * @param name 管理端展示的特殊日期名称
     * @param description 特殊日期配置说明
     * @param periods WORKING 日期的时段，替换常规周规则
     */
    public record ExceptionRequest(
            LocalDate date,
            String type,
            String name,
            String description,
            List<TimePeriodRequest> periods) {
    }

    /**
     * 特殊工作日的时段，分钟偏移仍按日历时区解释。
     *
     * @param startMinute 特殊工作日开始分钟，按日历时区解释
     * @param endMinute 特殊工作日结束分钟
     */
    public record TimePeriodRequest(
            Integer startMinute,
            Integer endMinute) {
    }

    /**
     * 作用域绑定；priority 和生效区间供任务初始化时选择日历。
     *
     * @param scopeType 部门或组织作用域类型
     * @param scopeKey 具体部门或组织键，供任务初始化匹配
     * @param priority 多条候选绑定的选取权重
     * @param effectiveFrom 绑定起始生效日期
     * @param effectiveTo 绑定结束生效日期
     */
    public record BindingRequest(
            String scopeType,
            String scopeKey,
            Integer priority,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {
    }
}
