package com.workflow.port.system;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 读取已发布工作日历，并按平台工作时间定义计算业务截止时刻。 */
public interface WorkCalendarPort {
    /** 按日历编码获取最新发布版本。调用方负责授权。 */
    Optional<Calendar> findPublishedByCode(String calendarCode);

    /** 从 start 累计非负工作分钟。日历未发布时抛出异常。 */
    Instant addWorkingMinutes(String calendarCode, Instant start, int minutes);

    /** weeklyPeriods 以周一至周日的 1 至 7 为键，日期例外覆盖对应的周工作时段。 */
    record Calendar(String code, String name, int version, String timezoneId,
                    Map<Integer, List<Period>> weeklyPeriods, Map<LocalDate, ExceptionDay> exceptions) { }

    /** 从当地零点计起的分钟偏移，结束值 1440 表示次日零点。 */
    record Period(int startMinute, int endMinute) { }

    /** 非工作日的时段为空，特殊工作日使用 periods 代替常规规则。 */
    record ExceptionDay(String type, String name, List<Period> periods) { }
}
