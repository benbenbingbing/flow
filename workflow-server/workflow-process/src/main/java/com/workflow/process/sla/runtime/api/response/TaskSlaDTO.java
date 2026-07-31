package com.workflow.process.sla.runtime.api.response;

import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSla;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSlaEvent;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSlaPause;

import java.util.List;

public record TaskSlaDTO(
        ProcessTaskSla sla,
        List<ProcessTaskSlaPause> pauses,
        List<ProcessTaskSlaEvent> events) {
}
