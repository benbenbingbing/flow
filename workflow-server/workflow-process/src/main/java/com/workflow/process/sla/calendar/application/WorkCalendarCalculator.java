package com.workflow.process.sla.calendar.application;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 按发布时保存的工作日历快照计算 SLA 截止时间与剩余时间。
 * 工作时段按日历时区解释；自然时间直接使用绝对时长，调用方不应混用两种口径。
 */
@Component
public class WorkCalendarCalculator {

    /** 限制错误日历引起的无界搜索；十年内仍无法累计时长时明确失败。 */
    private static final int MAX_SEARCH_DAYS = 3660;

    /**
     * 从起点累计 SLA 分钟并返回截止时刻。
     *
     * @param start 计时起点，工作时间模式下会先换算到日历时区
     * @param minutes 要累计的分钟数，不能为负
     * @param timeBasis NATURAL_TIME 使用连续时间，其余值按工作日历累计
     * @param calendar 工作时间模式的发布快照，决定工作日、时段与时区
     * @return 实际截止时刻；起点落在非工作时段时先移到下一工作时段
     * @throws IllegalArgumentException 分钟数为负、工作时间缺少日历或时区无效时抛出
     * @throws IllegalStateException 十年内没有足够工作时间时抛出
     */
    public Instant addMinutes(
            Instant start,
            int minutes,
            String timeBasis,
            WorkCalendarSnapshot calendar) {
        if (minutes < 0) {
            throw new IllegalArgumentException("SLA分钟数不能小于0");
        }
        if (minutes == 0) {
            return start;
        }
        if ("NATURAL_TIME".equalsIgnoreCase(timeBasis)) {
            return start.plus(Duration.ofMinutes(minutes));
        }
        if (calendar == null) {
            throw new IllegalArgumentException("工作时间计时必须解析到工作日历");
        }
        ZoneId zone = requireZone(calendar.timezoneId());
        ZonedDateTime cursor = start.atZone(zone);
        long remainingSeconds = Math.multiplyExact((long) minutes, 60L);
        // 按日期遍历并消费当天可用秒数，避免跨午休或非工作日直接相加。
        for (int day = 0; day < MAX_SEARCH_DAYS; day++) {
            List<WorkCalendarSnapshot.Period> periods =
                    periodsFor(cursor.toLocalDate(), calendar);
            for (WorkCalendarSnapshot.Period period : periods) {
                ZonedDateTime periodStart = atMinute(
                        cursor.toLocalDate(), period.startMinute(), zone);
                ZonedDateTime periodEnd = atMinute(
                        cursor.toLocalDate(), period.endMinute(), zone);
                if (!cursor.isBefore(periodEnd)) {
                    continue;
                }
                if (cursor.isBefore(periodStart)) {
                    cursor = periodStart;
                }
                long available = Math.max(
                        0L,
                        Duration.between(cursor, periodEnd).getSeconds());
                if (remainingSeconds <= available) {
                    return cursor.plusSeconds(remainingSeconds).toInstant();
                }
                remainingSeconds -= available;
                cursor = periodEnd;
            }
            cursor = cursor.toLocalDate()
                    .plusDays(1)
                    .atStartOfDay(zone);
        }
        throw new IllegalStateException("工作日历在十年范围内没有足够工作时间");
    }

    /**
     * 计算到截止时间尚余的工作分钟数，供待办倒计时和预警判断使用。
     * 不足一分钟向上取整，避免仍有可用时间时界面提前显示为零。
     *
     * @param from 开始计算的绝对时刻，通常为当前时间
     * @param due 已保存的 SLA 截止时刻
     * @param timeBasis 决定按连续时间还是工作时段扣减
     * @param calendar 工作时间模式使用的任务级日历快照
     * @return 剩余分钟数；已到期时为零
     * @throws IllegalArgumentException 工作时间模式缺少日历或时区无效时抛出
     */
    public int remainingMinutes(
            Instant from,
            Instant due,
            String timeBasis,
            WorkCalendarSnapshot calendar) {
        if (!from.isBefore(due)) {
            return 0;
        }
        if ("NATURAL_TIME".equalsIgnoreCase(timeBasis)) {
            return ceilMinutes(Duration.between(from, due).getSeconds());
        }
        if (calendar == null) {
            throw new IllegalArgumentException("工作时间计时必须解析到工作日历");
        }
        ZoneId zone = requireZone(calendar.timezoneId());
        ZonedDateTime cursor = from.atZone(zone);
        ZonedDateTime end = due.atZone(zone);
        long workingSeconds = 0L;
        for (int day = 0;
             day < MAX_SEARCH_DAYS && cursor.isBefore(end);
             day++) {
            LocalDate date = cursor.toLocalDate();
            for (WorkCalendarSnapshot.Period period :
                    periodsFor(date, calendar)) {
                ZonedDateTime periodStart =
                        atMinute(date, period.startMinute(), zone);
                ZonedDateTime periodEnd =
                        atMinute(date, period.endMinute(), zone);
                ZonedDateTime effectiveStart =
                        cursor.isAfter(periodStart) ? cursor : periodStart;
                ZonedDateTime effectiveEnd =
                        end.isBefore(periodEnd) ? end : periodEnd;
                if (effectiveStart.isBefore(effectiveEnd)) {
                    workingSeconds += Duration.between(
                            effectiveStart,
                            effectiveEnd).getSeconds();
                }
            }
            cursor = date.plusDays(1).atStartOfDay(zone);
        }
        return ceilMinutes(workingSeconds);
    }

    /**
     * 特殊日期覆盖每周规则；结果排序后供截止时间和剩余时间共用同一时段口径。
     *
     * @param date 日历本地日期，用于查找特殊日期或星期规则
     * @param calendar 当前任务固定的日历快照
     * @return 当日工作时段；非工作日返回空列表
     */
    public List<WorkCalendarSnapshot.Period> periodsFor(
            LocalDate date,
            WorkCalendarSnapshot calendar) {
        WorkCalendarSnapshot.ExceptionDay exception =
                calendar.exceptions() == null
                        ? null
                        : calendar.exceptions().get(date);
        if (exception != null) {
            if ("NON_WORKING".equalsIgnoreCase(exception.type())) {
                return List.of();
            }
            return sorted(exception.periods());
        }
        return sorted(calendar.weeklyPeriods() == null
                ? List.of()
                : calendar.weeklyPeriods().getOrDefault(
                        date.getDayOfWeek().getValue(),
                        List.of()));
    }

    /**
     * 发布前校验时区、每周时段和特殊日期；非法或重叠时段不能进入运行时计算。
     *
     * @param calendar 将用于任务截止时间计算的日历快照
     * @throws IllegalArgumentException 时区、星期或时段配置非法时抛出
     */
    public void validate(WorkCalendarSnapshot calendar) {
        requireZone(calendar.timezoneId());
        if (calendar.weeklyPeriods() == null
                || calendar.weeklyPeriods().values().stream()
                .allMatch(List::isEmpty)) {
            throw new IllegalArgumentException("工作日历至少需要一个工作时段");
        }
        calendar.weeklyPeriods().forEach((day, periods) -> {
            if (day == null || day < 1 || day > 7) {
                throw new IllegalArgumentException("星期必须在1到7之间");
            }
            validatePeriods(periods, "星期" + day);
        });
        if (calendar.exceptions() != null) {
            calendar.exceptions().forEach((date, exception) -> {
                String type = exception.type();
                if (!"WORKING".equalsIgnoreCase(type)
                        && !"NON_WORKING".equalsIgnoreCase(type)) {
                    throw new IllegalArgumentException(
                            "特殊日期类型仅支持WORKING或NON_WORKING: " + date);
                }
                if ("WORKING".equalsIgnoreCase(type)) {
                    validatePeriods(exception.periods(), date.toString());
                }
            });
        }
    }

    /**
     * 验证单日时段位于 0 到 1440 分钟内且互不重叠。
     *
     * @param values 同一天的时段配置，先排序再检查重叠
     * @param owner 错误所属星期或日期，用于提示管理员定位配置
     * @throws IllegalArgumentException 时段越界或重叠时抛出
     */
    private void validatePeriods(
            List<WorkCalendarSnapshot.Period> values,
            String owner) {
        List<WorkCalendarSnapshot.Period> periods = sorted(values);
        int previousEnd = -1;
        for (WorkCalendarSnapshot.Period period : periods) {
            if (period.startMinute() < 0
                    || period.endMinute() > 1440
                    || period.startMinute() >= period.endMinute()) {
                throw new IllegalArgumentException(
                        owner + "存在无效工作时段");
            }
            if (period.startMinute() < previousEnd) {
                throw new IllegalArgumentException(
                        owner + "的工作时段不能重叠");
            }
            previousEnd = period.endMinute();
        }
    }

    /**
     * 排序后才检查重叠并计算时间，避免配置录入顺序影响运行结果。
     *
     * @param values 配置的时段列表，允许为空
     * @return 按开始分钟升序排列的时段列表
     */
    private List<WorkCalendarSnapshot.Period> sorted(
            List<WorkCalendarSnapshot.Period> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .sorted(Comparator.comparingInt(
                        WorkCalendarSnapshot.Period::startMinute))
                .toList();
    }

    /**
     * 把本地日期的分钟偏移转换为日历时区时间；1440 指向次日零点。
     *
     * @param date 时段所属的日历本地日期
     * @param minute 从当日零点起算的分钟偏移
     * @param zone 发布日历指定的 IANA 时区
     * @return 对应的带时区时刻，供绝对时间差计算
     */
    private ZonedDateTime atMinute(
            LocalDate date,
            int minute,
            ZoneId zone) {
        if (minute == 1440) {
            return date.plusDays(1).atStartOfDay(zone);
        }
        return date.atTime(LocalTime.of(minute / 60, minute % 60))
                .atZone(zone);
    }

    /**
     * 拒绝无效 IANA 时区，防止截止时刻退化为服务器默认时区。
     *
     * @param timezoneId 发布日历保存的时区标识
     * @return 用于工作时段换算的时区
     * @throws IllegalArgumentException 标识不能被识别时抛出
     */
    private ZoneId requireZone(String timezoneId) {
        try {
            return ZoneId.of(timezoneId);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "无效IANA时区: " + timezoneId,
                    exception);
        }
    }

    /**
     * 秒级累计向上取整为展示分钟，溢出时明确抛错。
     *
     * @param seconds 已累计的工作秒数
     * @return 供倒计时展示的整数分钟
     * @throws ArithmeticException 分钟数超出 int 范围时抛出
     */
    private int ceilMinutes(long seconds) {
        return Math.toIntExact((seconds + 59L) / 60L);
    }
}
