package com.workflow.process.sla.policy.api.request;

import java.util.List;

public record TaskSlaPolicySaveRequest(
        String policyCode,
        String policyName,
        String description,
        Integer responseTargetMinutes,
        Integer completionTargetMinutes,
        String responseTimeBasis,
        String completionTimeBasis,
        Boolean allowManualPause,
        Boolean pauseOnProcessSuspend,
        Integer maxPauseMinutes,
        List<EscalationStepRequest> escalationSteps) {

    public record EscalationStepRequest(
            String stepName,
            String metricType,
            String triggerType,
            Integer offsetMinutes,
            Integer repeatIntervalMinutes,
            Integer maxExecutions,
            String actionType,
            String templateCode,
            String recipientConfigJson,
            String targetConfigJson) {
    }
}
