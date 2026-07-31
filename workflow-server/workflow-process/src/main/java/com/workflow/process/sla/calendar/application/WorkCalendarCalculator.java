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

@Component
public class WorkCalendarCalculator {

    private static final int MAX_SEARCH_DAYS = 3660;

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

    private ZoneId requireZone(String timezoneId) {
        try {
            return ZoneId.of(timezoneId);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "无效IANA时区: " + timezoneId,
                    exception);
        }
    }

    private int ceilMinutes(long seconds) {
        return Math.toIntExact((seconds + 59L) / 60L);
    }
}
