package com.workflow.process.sla.calendar.api.request;

import java.time.LocalDate;
import java.util.List;

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

    public record PeriodRequest(
            Integer dayOfWeek,
            Integer startMinute,
            Integer endMinute) {
    }

    public record ExceptionRequest(
            LocalDate date,
            String type,
            String name,
            String description,
            List<TimePeriodRequest> periods) {
    }

    public record TimePeriodRequest(
            Integer startMinute,
            Integer endMinute) {
    }

    public record BindingRequest(
            String scopeType,
            String scopeKey,
            Integer priority,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {
    }
}
