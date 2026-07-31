package com.workflow.process.sla.calendar.api.response;

import com.workflow.process.sla.calendar.application.WorkCalendarSnapshot;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendar;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendarBinding;

import java.util.List;

public record WorkCalendarDTO(
        WorkCalendar calendar,
        WorkCalendarSnapshot snapshot,
        List<WorkCalendarBinding> bindings) {
}
