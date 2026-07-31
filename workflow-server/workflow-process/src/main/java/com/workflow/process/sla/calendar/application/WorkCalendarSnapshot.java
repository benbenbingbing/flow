package com.workflow.process.sla.calendar.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record WorkCalendarSnapshot(
        String calendarCode,
        String calendarName,
        int version,
        String timezoneId,
        Map<Integer, List<Period>> weeklyPeriods,
        Map<LocalDate, ExceptionDay> exceptions) {

    public record Period(int startMinute, int endMinute) {
    }

    public record ExceptionDay(
            String type,
            String name,
            List<Period> periods) {
    }
}
