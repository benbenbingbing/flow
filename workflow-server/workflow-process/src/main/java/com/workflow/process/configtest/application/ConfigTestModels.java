package com.workflow.process.configtest.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 统一配置测试中心的稳定 API 与持久化模型。 */
public final class ConfigTestModels {

    private ConfigTestModels() {
    }

    public enum TargetType {
        ENTITY,
        FORM,
        LIST,
        PROCESS,
        EMPTY_ASSIGNEE,
        NODE_OPERATION,
        CONFIG_JSON,
        SCENARIO,
        ACTION,
        TIME_ADVANCE,
        SLA
    }

    public enum ResultStatus {
        PASS,
        WARNING,
        FAIL
    }

    public enum Severity {
        INFO,
        WARNING,
        BLOCKER
    }

    public enum RunStatus {
        RUNNING,
        PASS,
        WARNING,
        FAIL
    }

    public record SaveCaseRequest(
            String id,
            String caseKey,
            String name,
            String targetType,
            String targetId,
            String scenarioType,
            Map<String, Object> input,
            Map<String, Object> expected,
            Boolean enabled,
            Integer sortOrder) {
    }

    public record SaveSuiteRequest(
            String id,
            Long version,
            String name,
            String description,
            String scopeType,
            String scopeId,
            Boolean enabled,
            Boolean releaseGate,
            List<SaveCaseRequest> cases) {
    }

    public record GenerateSuiteRequest(
            String name,
            String description,
            String scopeType,
            String scopeId,
            Boolean releaseGate) {
    }

    public record QuickRunRequest(
            String name,
            String scopeType,
            String scopeId,
            List<SaveCaseRequest> cases) {
    }

    public record TestSuite(
            String id,
            String name,
            String description,
            String scopeType,
            String scopeId,
            boolean enabled,
            boolean releaseGate,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record TestCase(
            String id,
            String suiteId,
            String caseKey,
            String name,
            TargetType targetType,
            String targetId,
            String scenarioType,
            Map<String, Object> input,
            Map<String, Object> expected,
            boolean enabled,
            int sortOrder) {
    }

    public record SuiteDetail(TestSuite suite, List<TestCase> cases) {
    }

    public record ExecutionResult(
            ResultStatus status,
            Severity severity,
            String code,
            String message,
            Map<String, Object> evidence) {

        public static ExecutionResult pass(String code, String message, Map<String, Object> evidence) {
            return new ExecutionResult(ResultStatus.PASS, Severity.INFO, code, message, evidence);
        }

        public static ExecutionResult warning(String code, String message, Map<String, Object> evidence) {
            return new ExecutionResult(ResultStatus.WARNING, Severity.WARNING, code, message, evidence);
        }

        public static ExecutionResult fail(String code, String message, Map<String, Object> evidence) {
            return new ExecutionResult(ResultStatus.FAIL, Severity.BLOCKER, code, message, evidence);
        }
    }

    public record TestResult(
            String id,
            String runId,
            String caseId,
            String caseName,
            String targetType,
            String targetId,
            ResultStatus status,
            Severity severity,
            String code,
            String message,
            Map<String, Object> evidence,
            long durationMs) {
    }

    public record TestRun(
            String id,
            String suiteId,
            String triggerType,
            RunStatus status,
            String configFingerprint,
            int totalCount,
            int passedCount,
            int warningCount,
            int failedCount,
            int blockerCount,
            String operatorId,
            Instant startedAt,
            Instant finishedAt) {
    }

    public record RunReport(TestRun run, List<TestResult> results, double passRate) {
    }

    public record GateStatus(
            String scopeType,
            String scopeId,
            boolean required,
            boolean passed,
            List<String> reasons) {
    }
}
