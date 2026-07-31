package com.workflow.process.sla.runtime.application;

import com.workflow.process.sla.calendar.application.WorkCalendarResolutionSnapshot;
import com.workflow.process.sla.calendar.application.WorkCalendarSnapshot;
import com.workflow.process.sla.policy.application.TaskSlaPolicySnapshot;

public record PublishedTaskSlaConfig(
        boolean enabled,
        String calendarSource,
        String businessFieldCode,
        TaskSlaPolicySnapshot policySnapshot,
        WorkCalendarSnapshot calendarSnapshot,
        WorkCalendarResolutionSnapshot calendarResolutionSnapshot,
        int snapshotVersion) {
}
