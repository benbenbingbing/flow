package com.workflow.process.configtest.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import com.workflow.process.configtest.application.ConfigTestSandboxPolicy.SandboxContext;
import com.workflow.process.configtest.application.ConfigTestSandboxPolicy.UnsafeTestExecutionException;
import com.workflow.process.definition.api.response.ProcessPublishPreviewDTO;
import com.workflow.process.definition.application.ProcessDefinitionPreflightService;
import com.workflow.process.task.application.operation.NodeOperationDecisionService;
import com.workflow.process.task.application.operation.NodeOperationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 不产生业务副作用的统一配置测试执行器。
 *
 * <p>实体、表单、列表和流程均读取当前权威草稿快照；空办理人与节点操作使用和运行时
 * 相同的解析器/决策服务，避免测试规则与生产规则漂移。</p>
 */
@Component
@RequiredArgsConstructor
public class ConfigTestCaseExecutor {

    private final ObjectMapper objectMapper;
    private final ConfigTestSandboxPolicy sandboxPolicy;
    private final EntityDefinitionService entityDefinitionService;
    private final EntityFormService entityFormService;
    private final EntityListConfigService entityListConfigService;
    private final UiConfigReleaseService uiConfigReleaseService;
    private final ProcessDefinitionPreflightService processPreflightService;
    private final EmptyAssigneePolicyResolver emptyAssigneePolicyResolver;
    private final NodeOperationDecisionService nodeOperationDecisionService;

    /** 执行单条用例，并将所有异常收敛为可审计阻断结果。 */
    public ExecutionResult execute(TestCase testCase) {
        try {
            SandboxContext sandbox = sandboxPolicy.requireSafe(testCase);
            ExecutionResult actual = executeTarget(testCase, sandbox);
            return applyExpectedStatus(testCase, actual);
        } catch (UnsafeTestExecutionException exception) {
            return ExecutionResult.fail(
                    "CONFIG_TEST_SIDE_EFFECT_BLOCKED",
                    safeMessage(exception),
                    Map.of("sideEffectsBlocked", true));
        } catch (RuntimeException exception) {
            return ExecutionResult.fail(
                    "CONFIG_TEST_EXECUTION_ERROR",
                    safeMessage(exception),
                    Map.of("exceptionType", exception.getClass().getSimpleName()));
        }
    }

    private ExecutionResult executeTarget(TestCase testCase, SandboxContext sandbox) {
        return switch (testCase.targetType()) {
            case ENTITY -> executeEntity(testCase);
            case FORM -> executeForm(testCase);
            case LIST -> executeList(testCase);
            case PROCESS -> executeProcess(testCase);
            case EMPTY_ASSIGNEE -> executeEmptyAssignee(testCase);
            case NODE_OPERATION -> executeNodeOperation(testCase);
            case CONFIG_JSON -> executeJson(testCase);
            case SCENARIO -> executeScenario(testCase, sandbox);
            case ACTION -> executeMockAction(testCase, sandbox);
            case TIME_ADVANCE -> executeTimeAdvance(testCase, sandbox);
            case SLA -> executeSla(testCase, sandbox);
        };
    }

    /**
     * 按顺序执行隔离场景步骤。每个步骤仍复用对应模块的权威校验器，动作只允许 mock/stub，
     * 并把目标类型、节点和操作覆盖写入证据。
     */
    private ExecutionResult executeScenario(TestCase testCase, SandboxContext sandbox) {
        JsonNode steps = objectMapper.valueToTree(testCase.input()).path("steps");
        if (!steps.isArray() || steps.isEmpty()) {
            throw new IllegalArgumentException("沙箱场景至少需要一个 steps 步骤");
        }
        List<Map<String, Object>> stepResults = new ArrayList<>();
        Set<String> targetCoverage = new LinkedHashSet<>();
        Set<String> nodeCoverage = new LinkedHashSet<>();
        Set<String> operationCoverage = new LinkedHashSet<>();
        int failed = 0;
        int warnings = 0;
        int index = 0;
        for (JsonNode step : steps) {
            String stepKey = step.path("key").asText("step-" + (++index));
            TargetType targetType = scenarioTarget(step.path("targetType").asText(
                    step.path("type").asText()));
            if (targetType == TargetType.SCENARIO) {
                throw new IllegalArgumentException("沙箱场景不允许递归嵌套 SCENARIO");
            }
            Map<String, Object> input = step.path("input").isObject()
                    ? objectMapper.convertValue(step.path("input"), new TypeReference<Map<String, Object>>() { })
                    : Map.of();
            Map<String, Object> expected = step.path("expected").isObject()
                    ? objectMapper.convertValue(step.path("expected"), new TypeReference<Map<String, Object>>() { })
                    : Map.of();
            TestCase child = new TestCase(
                    testCase.id() + ":" + stepKey,
                    testCase.suiteId(),
                    testCase.caseKey() + ":" + stepKey,
                    step.path("name").asText(stepKey),
                    targetType,
                    step.path("targetId").asText(testCase.targetId()),
                    step.path("type").asText(targetType.name()),
                    input,
                    expected,
                    true,
                    index);
            ExecutionResult result;
            try {
                sandboxPolicy.requireSafe(child);
                result = applyExpectedStatus(child, executeTarget(child, sandbox));
            } catch (UnsafeTestExecutionException exception) {
                result = ExecutionResult.fail(
                        "CONFIG_TEST_SIDE_EFFECT_BLOCKED",
                        safeMessage(exception),
                        Map.of("sideEffectsBlocked", true));
            } catch (RuntimeException exception) {
                result = ExecutionResult.fail(
                        "SCENARIO_STEP_EXECUTION_ERROR",
                        safeMessage(exception),
                        Map.of("exceptionType", exception.getClass().getSimpleName()));
            }
            targetCoverage.add(targetType.name());
            addCoverage(nodeCoverage, input.get("nodeId"));
            addCoverage(nodeCoverage, input.get("elementId"));
            addCoverage(operationCoverage, input.get("operation"));
            if (result.status() == ResultStatus.FAIL) failed++;
            if (result.status() == ResultStatus.WARNING) warnings++;
            Map<String, Object> stepEvidence = new LinkedHashMap<>();
            stepEvidence.put("key", stepKey);
            stepEvidence.put("targetType", targetType.name());
            stepEvidence.put("status", result.status().name());
            stepEvidence.put("code", result.code());
            stepEvidence.put("message", result.message());
            stepEvidence.put("evidence", result.evidence());
            stepResults.add(stepEvidence);
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("steps", stepResults);
        evidence.put("stepCount", stepResults.size());
        evidence.put("targetCoverage", List.copyOf(targetCoverage));
        evidence.put("nodeCoverage", List.copyOf(nodeCoverage));
        evidence.put("operationCoverage", List.copyOf(operationCoverage));
        evidence.put("identity", sandbox.identity());
        evidence.put("organizations", sandbox.organizations());
        evidence.put("frozenAt", sandbox.frozenAt().toString());
        evidence.put("adapterMode", sandbox.adapterMode());
        evidence.put("sideEffectsBlocked", sandbox.sideEffectsBlocked());
        if (failed > 0) {
            return ExecutionResult.fail("SCENARIO_STEP_FAILED", failed + " 个场景步骤失败", evidence);
        }
        if (warnings > 0) {
            return ExecutionResult.warning("SCENARIO_STEP_WARNING", warnings + " 个场景步骤存在警告", evidence);
        }
        return ExecutionResult.pass("SCENARIO_PASSED", "隔离场景全部步骤通过", evidence);
    }

    private ExecutionResult executeMockAction(TestCase testCase, SandboxContext sandbox) {
        Map<String, Object> input = safeMap(testCase.input());
        if (!input.containsKey("mockResponse") && !input.containsKey("mockFailure")) {
            throw new IllegalArgumentException("动作测试必须提供 mockResponse 或 mockFailure");
        }
        boolean actualFailure = truthy(input.get("mockFailure"));
        boolean expectedFailure = truthy(safeMap(testCase.expected()).get("failure"));
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("adapterMode", sandbox.adapterMode());
        evidence.put("mockResponse", input.get("mockResponse"));
        evidence.put("mockFailure", actualFailure);
        evidence.put("sideEffectsBlocked", true);
        return actualFailure == expectedFailure
                ? ExecutionResult.pass("MOCK_ACTION_EXPECTATION_MET", "Mock 动作结果符合预期", evidence)
                : ExecutionResult.fail("MOCK_ACTION_EXPECTATION_MISMATCH", "Mock 动作结果与预期不一致", evidence);
    }

    private ExecutionResult executeTimeAdvance(TestCase testCase, SandboxContext sandbox) {
        long seconds = number(testCase.input().get("advanceSeconds"), 0L);
        if (seconds < 0 || seconds > 31_536_000L) {
            throw new IllegalArgumentException("时间推进范围必须在 0 到 365 天之间");
        }
        Instant advancedAt = sandbox.frozenAt().plusSeconds(seconds);
        String expected = text(testCase.expected(), "advancedAt");
        Map<String, Object> evidence = Map.of(
                "frozenAt", sandbox.frozenAt().toString(),
                "advancedAt", advancedAt.toString(),
                "advanceSeconds", seconds);
        if (StringUtils.hasText(expected) && !advancedAt.equals(parseInstant(expected, "advancedAt"))) {
            return ExecutionResult.fail("TIME_ADVANCE_EXPECTATION_MISMATCH", "冻结时间推进结果与预期不一致", evidence);
        }
        return ExecutionResult.pass("TIME_ADVANCE_PASSED", "冻结时间已按测试场景推进", evidence);
    }

    private ExecutionResult executeSla(TestCase testCase, SandboxContext sandbox) {
        Instant dueAt = parseInstant(text(testCase.input(), "dueAt"), "dueAt");
        Instant evaluatedAt = sandbox.frozenAt().plusSeconds(number(
                testCase.input().get("advanceSeconds"), 0L));
        boolean breached = evaluatedAt.isAfter(dueAt);
        Object expectedValue = safeMap(testCase.expected()).get("breached");
        Map<String, Object> evidence = Map.of(
                "dueAt", dueAt.toString(),
                "evaluatedAt", evaluatedAt.toString(),
                "breached", breached,
                "sideEffectsBlocked", true);
        if (expectedValue != null && breached != truthy(expectedValue)) {
            return ExecutionResult.fail("SLA_EXPECTATION_MISMATCH", "SLA 模拟结果与预期不一致", evidence);
        }
        return ExecutionResult.pass("SLA_EXPECTATION_MET", "SLA 冻结时间模拟符合预期", evidence);
    }

    private TargetType scenarioTarget(String value) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException("场景步骤缺少 type");
        return switch (value.trim().toUpperCase()) {
            case "FORM_INIT", "FORM_SUBMIT" -> TargetType.FORM;
            case "LIST_QUERY" -> TargetType.LIST;
            case "PROCESS_START", "PROCESS_ADVANCE" -> TargetType.PROCESS;
            case "TASK_OPERATION" -> TargetType.NODE_OPERATION;
            default -> TargetType.valueOf(value.trim().toUpperCase());
        };
    }

    private void addCoverage(Set<String> values, Object value) {
        if (StringUtils.hasText(value == null ? null : String.valueOf(value))) {
            values.add(String.valueOf(value));
        }
    }

    private Instant parseInstant(String value, String label) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(label + "不能为空");
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(label + " 必须使用 ISO-8601 Instant 格式", exception);
        }
    }

    private long number(Object value, long fallback) {
        if (value == null) return fallback;
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private boolean truthy(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private ExecutionResult executeEntity(TestCase testCase) {
        Object entity = entityDefinitionService.findById(requireTargetId(testCase));
        if (entity == null) {
            return ExecutionResult.fail("ENTITY_NOT_FOUND", "实体配置不存在", Map.of());
        }
        JsonNode snapshot = objectMapper.valueToTree(entity);
        return ExecutionResult.pass(
                "ENTITY_SNAPSHOT_VALID",
                "实体定义及字段快照可读取",
                Map.of("snapshot", objectMapper.convertValue(snapshot, new TypeReference<Map<String, Object>>() { })));
    }

    private ExecutionResult executeForm(TestCase testCase) {
        String formId = requireTargetId(testCase);
        Object form = entityFormService.getById(formId);
        if (form == null) {
            return ExecutionResult.fail("FORM_NOT_FOUND", "表单配置不存在", Map.of());
        }
        List<?> fields = entityFormService.getFormFields(formId);
        Map<String, Object> snapshot = uiConfigReleaseService.draftSnapshot(UiConfigReleaseService.FORM, formId);
        return ExecutionResult.pass(
                "FORM_DRAFT_VALID",
                "表单草稿、字段和数据源引用可解析",
                Map.of("fieldCount", fields == null ? 0 : fields.size(), "snapshot", safeMap(snapshot)));
    }

    private ExecutionResult executeList(TestCase testCase) {
        String listId = requireTargetId(testCase);
        Object config = entityListConfigService.findById(listId);
        if (config == null) {
            return ExecutionResult.fail("LIST_NOT_FOUND", "列表配置不存在", Map.of());
        }
        Map<String, Object> snapshot = uiConfigReleaseService.draftSnapshot(UiConfigReleaseService.LIST, listId);
        return ExecutionResult.pass(
                "LIST_DRAFT_VALID",
                "列表字段、数据范围、操作和扩展引用可解析",
                Map.of("snapshot", safeMap(snapshot)));
    }

    private ExecutionResult executeProcess(TestCase testCase) {
        ProcessPublishPreviewDTO preview = processPreflightService.preview(requireTargetId(testCase));
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("publishable", preview.publishable());
        evidence.put("draftHash", preview.draftHash());
        evidence.put("revision", preview.revision());
        evidence.put("blockerCount", preview.blockerCount());
        evidence.put("warningCount", preview.warningCount());
        evidence.put("issues", preview.issues());
        if (!preview.publishable()) {
            return ExecutionResult.fail("PROCESS_PREFLIGHT_BLOCKED", "流程发布预检存在阻断项", evidence);
        }
        if (preview.warningCount() > 0) {
            return ExecutionResult.warning("PROCESS_PREFLIGHT_WARNING", "流程预检通过，但存在警告", evidence);
        }
        return ExecutionResult.pass("PROCESS_PREFLIGHT_PASSED", "流程发布预检通过", evidence);
    }

    private ExecutionResult executeEmptyAssignee(TestCase testCase) {
        JsonNode input = objectMapper.valueToTree(testCase.input());
        JsonNode processConfigNode = input.path("processConfig");
        String processConfig = processConfigNode.isTextual()
                ? processConfigNode.asText()
                : processConfigNode.isMissingNode() ? "{}" : processConfigNode.toString();
        Map<String, Object> nodeConfig = input.path("nodeConfig").isObject()
                ? objectMapper.convertValue(input.path("nodeConfig"), new TypeReference<Map<String, Object>>() { })
                : Map.of();
        Object policy = emptyAssigneePolicyResolver.resolve(processConfig, nodeConfig);
        emptyAssigneePolicyResolver.validate(
                (com.workflow.process.assignment.domain.EmptyAssigneePolicy) policy);
        Map<String, Object> evidence = Map.of("resolvedPolicy", objectMapper.convertValue(
                policy, new TypeReference<Map<String, Object>>() { }));
        String expectedPolicy = text(testCase.expected(), "policy");
        if (StringUtils.hasText(expectedPolicy)
                && !expectedPolicy.equalsIgnoreCase(objectMapper.valueToTree(policy).path("policy").asText())) {
            return ExecutionResult.fail("EMPTY_ASSIGNEE_EXPECTATION_MISMATCH",
                    "空办理人策略解析结果与预期不一致", evidence);
        }
        return ExecutionResult.pass("EMPTY_ASSIGNEE_POLICY_VALID", "空办理人策略可解析且约束有效", evidence);
    }

    private ExecutionResult executeNodeOperation(TestCase testCase) {
        JsonNode input = objectMapper.valueToTree(testCase.input());
        JsonNode policyNode = input.get("policy");
        String policyJson = input.path("policyJson").asText(null);
        if (!StringUtils.hasText(policyJson) && policyNode != null) {
            policyJson = policyNode.toString();
        }
        if (!StringUtils.hasText(policyJson)) {
            throw new IllegalArgumentException("节点操作测试缺少 policy 或 policyJson");
        }
        NodeOperationPolicy.Operation operation = NodeOperationPolicy.Operation.fromCode(
                input.path("operation").asText());
        NodeOperationDecisionService.SimulationContext context = input.path("context").isObject()
                ? objectMapper.convertValue(
                        input.path("context"), NodeOperationDecisionService.SimulationContext.class)
                : NodeOperationDecisionService.SimulationContext.empty();
        NodeOperationDecisionService.ActionDecision decision =
                nodeOperationDecisionService.simulate(policyJson, operation, context);
        Map<String, Object> evidence = Map.of("decision", decision);
        JsonNode expected = objectMapper.valueToTree(testCase.expected());
        if (expected.has("allowed")) {
            boolean expectedAllowed = expected.path("allowed").asBoolean();
            return decision.allowed() == expectedAllowed
                    ? ExecutionResult.pass("NODE_OPERATION_EXPECTATION_MET", "节点操作判定符合预期", evidence)
                    : ExecutionResult.fail("NODE_OPERATION_EXPECTATION_MISMATCH", "节点操作判定与预期不一致", evidence);
        }
        return decision.allowed()
                ? ExecutionResult.pass("NODE_OPERATION_ALLOWED", decision.message(), evidence)
                : ExecutionResult.fail("NODE_OPERATION_DENIED", decision.message(), evidence);
    }

    private ExecutionResult executeJson(TestCase testCase) {
        JsonNode input = objectMapper.valueToTree(testCase.input());
        JsonNode document = input.has("document") ? input.get("document") : input;
        if (!document.isObject() && !document.isArray()) {
            return ExecutionResult.fail("CONFIG_JSON_INVALID", "配置文档必须是对象或数组", Map.of());
        }
        for (JsonNode path : input.path("requiredPaths")) {
            String pointer = path.asText();
            if (!document.at(pointer).isMissingNode()) {
                continue;
            }
            return ExecutionResult.fail(
                    "CONFIG_REQUIRED_PATH_MISSING",
                    "配置缺少必填路径: " + pointer,
                    Map.of("missingPath", pointer));
        }
        return ExecutionResult.pass("CONFIG_JSON_VALID", "配置 JSON 结构有效", Map.of());
    }

    private ExecutionResult applyExpectedStatus(TestCase testCase, ExecutionResult actual) {
        String expectedStatus = text(testCase.expected(), "status");
        if (!StringUtils.hasText(expectedStatus)) {
            return actual;
        }
        if (actual.status().name().equalsIgnoreCase(expectedStatus)) {
            return actual;
        }
        return ExecutionResult.fail(
                "CONFIG_TEST_STATUS_MISMATCH",
                "实际状态 " + actual.status() + " 与预期 " + expectedStatus + " 不一致",
                Map.of("actual", actual));
    }

    private String requireTargetId(TestCase testCase) {
        if (!StringUtils.hasText(testCase.targetId())) {
            throw new IllegalArgumentException(testCase.targetType() + " 测试缺少 targetId");
        }
        return testCase.targetId();
    }

    private String text(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value == null ? null : value.toString();
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private String safeMessage(Throwable throwable) {
        return StringUtils.hasText(throwable.getMessage())
                ? throwable.getMessage()
                : throwable.getClass().getSimpleName();
    }
}
