package com.workflow.entity.mutationpolicy.application.model;

import java.time.LocalDateTime;

public record EntityMutationPolicySummary(
        String entityId,
        String entityCode,
        String entityName,
        boolean enabled,
        String status,
        String migrationState,
        Integer revision,
        Integer activeReleaseVersion,
        boolean runtimeEnabled,
        int ruleCount,
        int stepCount,
        int targetBindingCount,
        LocalDateTime updateTime) {
}
