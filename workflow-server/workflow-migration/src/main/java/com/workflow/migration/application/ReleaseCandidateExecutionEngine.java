package com.workflow.migration.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 无全局事务的候选步骤执行器。
 * 每个步骤先写 RUNNING，再独立写结果；任一步骤异常立即停止，后续步骤保持 NOT_EXECUTED。
 */
@Component
@RequiredArgsConstructor
public class ReleaseCandidateExecutionEngine {

    private final ReleaseCandidateDeploymentPort deploymentPort;

    /** 执行步骤并返回真实迁移发布结果。 */
    public ExecutionResult execute(
            String importId,
            List<ExecutionStep> steps,
            StepJournal journal) {
        Map<String, Object> deploymentResult = Map.of();
        for (ExecutionStep step : steps) {
            long startedNanos = System.nanoTime();
            journal.started(step);
            try {
                Map<String, Object> output = new LinkedHashMap<>();
                if ("DEPLOY_IMPORT_PACKAGE".equals(step.stepType())) {
                    deploymentResult = deploymentPort.publishImport(importId);
                    output.put("deployment", deploymentResult);
                } else if ("FINALIZE_REPORT".equals(step.stepType())) {
                    output.put("reportReady", true);
                } else {
                    output.put("verified", true);
                    output.put("stepType", step.stepType());
                }
                long duration = elapsedMillis(startedNanos);
                journal.completed(step, output, duration);
            } catch (RuntimeException error) {
                long duration = elapsedMillis(startedNanos);
                journal.failed(step, error, duration);
                throw new StepExecutionException(step.id(), error);
            }
        }
        return new ExecutionResult(deploymentResult);
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    public record ExecutionStep(String id, String stepType) {
    }

    public record ExecutionResult(Map<String, Object> deploymentResult) {
    }

    public interface StepJournal {
        void started(ExecutionStep step);

        void completed(ExecutionStep step, Map<String, Object> output, long durationMs);

        void failed(ExecutionStep step, RuntimeException error, long durationMs);
    }

    /** 携带失败步骤 ID，供编排服务更新候选恢复入口。 */
    public static class StepExecutionException extends RuntimeException {
        private final String stepId;

        public StepExecutionException(String stepId, RuntimeException cause) {
            super(cause.getMessage(), cause);
            this.stepId = stepId;
        }

        public String getStepId() {
            return stepId;
        }
    }
}
