package com.workflow.process.sla.policy.application;

import java.util.List;

public record TaskSlaPolicySnapshot(
        String policyCode,
        String policyName,
        int version,
        Integer responseTargetMinutes,
        int completionTargetMinutes,
        String responseTimeBasis,
        String completionTimeBasis,
        boolean allowManualPause,
        boolean pauseOnProcessSuspend,
        Integer maxPauseMinutes,
        List<EscalationStep> escalationSteps) {

    public record EscalationStep(
            String id,
            String stepName,
            String metricType,
            String triggerType,
            int offsetMinutes,
            Integer repeatIntervalMinutes,
            int maxExecutions,
            String actionType,
            String templateCode,
            String recipientConfigJson,
            String targetConfigJson,
            int sortOrder) {
    }
}
