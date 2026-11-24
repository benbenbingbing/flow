package com.workflow.process.sla.calendar.application;

import com.workflow.port.system.WorkCalendarPort;
import com.workflow.process.sla.calendar.application.model.WorkCalendarSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** 使用已发布日历和平台 SLA 计时规则。 */
@Component
@RequiredArgsConstructor
public class WorkCalendarAdapter implements WorkCalendarPort {
    private final WorkCalendarService calendars;
    private final WorkCalendarCalculator calculator;

    @Override
    public Optional<Calendar> findPublishedByCode(String calendarCode) {
        return calendars.findPublishedSnapshot(requireCalendarCode(calendarCode)).map(this::project);
    }

    @Override
    public Instant addWorkingMinutes(String calendarCode, Instant start, int minutes) {
        var snapshot = calendars.findPublishedSnapshotByCode(requireCalendarCode(calendarCode));
        return calculator.addMinutes(Objects.requireNonNull(start, "起点不能为空"), minutes, "WORKING_TIME", snapshot);
    }

    private String requireCalendarCode(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("工作日历编码不能为空");
        return code.trim();
    }

    private Calendar project(WorkCalendarSnapshot snapshot) {
        var periods = snapshot.weeklyPeriods().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(java.util.Map.Entry::getKey, entry -> periods(entry.getValue())));
        var exceptions = snapshot.exceptions().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(java.util.Map.Entry::getKey, entry -> {
                    var day = entry.getValue();
                    return new ExceptionDay(day.type(), day.name(), periods(day.periods()));
                }));
        return new Calendar(snapshot.calendarCode(), snapshot.calendarName(), snapshot.version(), snapshot.timezoneId(),
                periods, exceptions);
    }

    private List<Period> periods(List<WorkCalendarSnapshot.Period> periods) {
        return periods == null ? List.of() : periods.stream()
                .map(period -> new Period(period.startMinute(), period.endMinute())).toList();
    }
}
