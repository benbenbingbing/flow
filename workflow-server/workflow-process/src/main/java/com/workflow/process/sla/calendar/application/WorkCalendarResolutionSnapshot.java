package com.workflow.process.sla.calendar.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 发布时固定的日历选择表。运行时先匹配 bindings，再从 calendars 读取日历；
 * 未命中时使用 defaultCalendarCode，不能回查后续变化的日历绑定。
 *
 * @param defaultCalendarCode 作用域未命中时回退的日历编码
 * @param calendars 按编码索引的发布日历，运行时据此取快照
 * @param bindings 固定的作用域规则，任务初始化时据此选日历
 */
public record WorkCalendarResolutionSnapshot(
        String defaultCalendarCode,
        Map<String, WorkCalendarSnapshot> calendars,
        List<Binding> bindings) {

    /**
     * 作用域与日历的发布绑定。scopeType/scopeKey 定位部门或组织，
     * 生效区间过滤候选项，priority 决定同作用域冲突时的选取顺序。
     *
     * @param scopeType 部门或组织作用域类型
     * @param scopeKey 与任务上下文比对的具体作用域键
     * @param calendarCode 命中后引用的发布日历编码
     * @param priority 同作用域多条规则的选取顺序
     * @param effectiveFrom 过滤候选项的起始生效日期
     * @param effectiveTo 过滤候选项的结束生效日期
     */
    public record Binding(
            String scopeType,
            String scopeKey,
            String calendarCode,
            int priority,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {
    }
}
