package com.workflow.process.sla.policy.api.response;

import com.workflow.process.sla.policy.application.TaskSlaPolicySnapshot;
import com.workflow.process.sla.policy.infrastructure.persistence.record.TaskSlaPolicy;

public record TaskSlaPolicyDTO(
        TaskSlaPolicy policy,
        TaskSlaPolicySnapshot snapshot) {
}
