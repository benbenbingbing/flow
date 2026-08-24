package com.workflow.process.configtest.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.definition.application.EntityDefinitionService;
import com.workflow.entity.form.application.EntityFormService;
import com.workflow.entity.list.application.EntityListConfigService;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.process.assignment.application.EmptyAssigneePolicyResolver;
import com.workflow.process.configtest.application.ConfigTestModels.ExecutionResult;
import com.workflow.process.configtest.application.ConfigTestModels.ResultStatus;
import com.workflow.process.configtest.application.ConfigTestModels.TargetType;
import com.workflow.process.configtest.application.ConfigTestModels.TestCase;
import com.workflow.process.definition.api.response.ProcessPublishPreviewDTO;
import com.workflow.process.definition.api.response.ProcessValidationIssueDTO;
import com.workflow.process.definition.application.ProcessDefinitionPreflightService;
import com.workflow.process.task.application.operation.NodeOperationDecisionService;
import com.workflow.process.task.application.operation.NodeOperationPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigTestCaseExecutorTest {

    private final EntityDefinitionService entityService = mock(EntityDefinitionService.class);
    private final EntityFormService formService = mock(EntityFormService.class);
    private final EntityListConfigService listService = mock(EntityListConfigService.class);
    private final UiConfigReleaseService releaseService = mock(UiConfigReleaseService.class);
    private final ProcessDefinitionPreflightService preflightService = mock(ProcessDefinitionPreflightService.class);
    private final EmptyAssigneePolicyResolver emptyAssigneeResolver = mock(EmptyAssigneePolicyResolver.class);
    private final NodeOperationDecisionService operationService = mock(NodeOperationDecisionService.class);
    private ConfigTestCaseExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ConfigTestCaseExecutor(
                new ObjectMapper().findAndRegisterModules(),
                new ConfigTestSandboxPolicy(),
                entityService,
                formService,
                listService,
                releaseService,
                preflightService,
                emptyAssigneeResolver,
                operationService);
    }

    @Test
    void processPreflightMapsBlockersAndWarnings() {
        ProcessValidationIssueDTO blocker = new ProcessValidationIssueDTO(
                "BROKEN_NODE", ProcessValidationIssueDTO.Severity.BLOCKER, true,
                "task-1", "userTask", "节点配置无效", "修复节点", null);
        when(preflightService.preview("process-1")).thenReturn(new ProcessPublishPreviewDTO(
                "process-1", 2, "hash-1", 1, false, 1, 0,
                List.of(blocker), null, 0, 1, "token", Instant.now()));
        when(preflightService.preview("process-2")).thenReturn(new ProcessPublishPreviewDTO(
                "process-2", 3, "hash-2", 2, true, 0, 1,
                List.of(), null, 0, 1, "token", Instant.now()));

        assertEquals(ResultStatus.FAIL, executor.execute(testCase(
                TargetType.PROCESS, "process-1", Map.of(), Map.of())).status());
        assertEquals(ResultStatus.WARNING, executor.execute(testCase(
                TargetType.PROCESS, "process-2", Map.of(), Map.of())).status());
    }

    @Test
    void nodeOperationNegativeExpectationCanPass() {
        NodeOperationPolicy.Rule rule = new NodeOperationPolicy.Rule(
                false, null, null, false, List.of(), false,
                NodeOperationPolicy.TargetScope.ANY, Set.of(), Set.of(), Set.of(), null);
        when(operationService.simulate(anyString(), any(), any())).thenReturn(
                new NodeOperationDecisionService.ActionDecision(
                        "terminate", false, "DISABLED", "未开放终止", rule));

        ExecutionResult result = executor.execute(testCase(
                TargetType.NODE_OPERATION,
                null,
                Map.of(
                        "policy", Map.of("version", 1, "operations", Map.of()),
                        "operation", "terminate"),
                Map.of("allowed", false)));

        assertEquals(ResultStatus.PASS, result.status());
        assertEquals("NODE_OPERATION_EXPECTATION_MET", result.code());
    }

    @Test
    void genericJsonReportsMissingRequiredPath() {
        ExecutionResult result = executor.execute(testCase(
                TargetType.CONFIG_JSON,
                null,
                Map.of(
                        "document", Map.of("name", "demo"),
                        "requiredPaths", List.of("/name", "/version")),
                Map.of()));

        assertEquals(ResultStatus.FAIL, result.status());
        assertEquals("CONFIG_REQUIRED_PATH_MISSING", result.code());
    }

    @Test
    void isolatedScenarioFreezesTimeAndMocksActionsWithoutSideEffects() {
        ExecutionResult result = executor.execute(testCase(
                TargetType.SCENARIO,
                "scenario-1",
                Map.of(
                        "environment", Map.of(
                                "adapterMode", "MOCK",
                                "identity", "tester-1",
                                "frozenAt", "2026-08-22T00:00:00Z"),
                        "steps", List.of(
                                Map.of(
                                        "key", "clock",
                                        "type", "TIME_ADVANCE",
                                        "input", Map.of("advanceSeconds", 3600),
                                        "expected", Map.of("advancedAt", "2026-08-22T01:00:00Z")),
                                Map.of(
                                        "key", "notification",
                                        "type", "ACTION",
                                        "input", Map.of("mockFailure", true),
                                        "expected", Map.of("failure", true)))),
                Map.of()));

        assertEquals(ResultStatus.PASS, result.status());
        assertEquals("SCENARIO_PASSED", result.code());
        assertEquals(true, result.evidence().get("sideEffectsBlocked"));
    }

    @Test
    void liveAdapterIsBlockedBeforeAnyBusinessServiceRuns() {
        ExecutionResult result = executor.execute(testCase(
                TargetType.ACTION,
                null,
                Map.of(
                        "environment", Map.of("adapterMode", "LIVE"),
                        "mockResponse", Map.of("ok", true)),
                Map.of()));

        assertEquals(ResultStatus.FAIL, result.status());
        assertEquals("CONFIG_TEST_SIDE_EFFECT_BLOCKED", result.code());
    }

    private TestCase testCase(
            TargetType targetType,
            String targetId,
            Map<String, Object> input,
            Map<String, Object> expected) {
        return new TestCase(
                "case-1", "suite-1", "case-key", "测试用例", targetType,
                targetId, "BASELINE", input, expected, true, 0);
    }
}
