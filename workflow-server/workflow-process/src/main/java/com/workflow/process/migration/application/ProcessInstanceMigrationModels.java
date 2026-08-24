package com.workflow.process.migration.application;

import java.util.List;
import java.util.Map;

/** 流程实例版本迁移批次请求和安全评估模型。 */
public final class ProcessInstanceMigrationModels {

    private ProcessInstanceMigrationModels() {
    }

    public record CreateBatchRequest(
            String batchName,
            String sourceProcessDefinitionId,
            String targetProcessDefinitionId,
            List<String> processInstanceIds,
            Map<String, String> activityMappings,
            Map<String, Object> variableOverrides,
            Map<String, String> formReleaseMappings,
            String idempotencyKey) {
    }

    public record ExecuteBatchRequest(Integer expectedRevision, Integer batchSize) {
    }

    public record RetryBatchRequest(Integer expectedRevision, List<String> itemIds) {
    }

    public record SafetyInput(
            String sourceProcessKey,
            String targetProcessKey,
            List<String> activeActivityIds,
            Map<String, String> activityMappings,
            boolean suspended,
            boolean multiInstance,
            long pendingJobCount,
            long eventSubscriptionCount) {
    }

    public record SafetyAssessment(
            boolean allowed,
            boolean reversible,
            List<String> blockers,
            List<String> warnings) {
    }
}
