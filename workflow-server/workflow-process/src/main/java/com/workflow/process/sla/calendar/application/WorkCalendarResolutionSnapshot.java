package com.workflow.process.sla.calendar.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record WorkCalendarResolutionSnapshot(
        String defaultCalendarCode,
        Map<String, WorkCalendarSnapshot> calendars,
        List<Binding> bindings) {

    public record Binding(
            String scopeType,
            String scopeKey,
            String calendarCode,
            int priority,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {
    }
}
