package com.workflow.process.sla.calendar.api.request;

import java.time.OffsetDateTime;

public record WorkCalendarSimulationRequest(
        OffsetDateTime startAt,
        Integer minutes) {
}
