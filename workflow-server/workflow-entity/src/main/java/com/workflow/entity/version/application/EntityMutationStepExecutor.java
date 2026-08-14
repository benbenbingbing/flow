package com.workflow.entity.version.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationPhase;
import com.workflow.contracts.entity.mutation.EntityMutationStepContext;
import com.workflow.contracts.entity.mutation.EntityMutationStepProvider;
import com.workflow.contracts.entity.mutation.EntityMutationStepResult;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.ui.api.request.UiDataSourceExecuteRequest;
import com.workflow.entity.ui.application.UiDataSourceService;
import com.workflow.entity.mutationpolicy.application.EntityMutationPolicyService;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 按已发布版本配置执行实体变更前置操作。
 */
@Service
@RequiredArgsConstructor
public class EntityMutationStepExecutor {

    private final EntityMutationPolicyService mutationPolicyService;
    private final EntityVersionPolicyMatcher policyMatcher;
    private final EntityMutationBuiltInRuleExecutor builtInRuleExecutor;
    private final UiDataSourceService dataSourceService;
    private final List<EntityMutationStepProvider> providers;
    private final ObjectMapper objectMapper;

    public ExecutionOutcome execute(
            EntityMutationCommand command,
            EntityMutationPhase phase,
            Map<String, Object> beforeRecord,
            Map<String, Object> currentRecord) {
        return execute(
                command,
                phase,
                beforeRecord,
                currentRecord,
                null);
    }

    public ExecutionOutcome execute(
            EntityMutationCommand command,
            EntityMutationPhase phase,
            Map<String, Object> beforeRecord,
            Map<String, Object> currentRecord,
            String forcedScenarioCode) {
        EntityVersionConfiguration configuration =
                mutationPolicyService
                        .getPublished(command.entityCode())
                        .orElse(null);
        if (configuration == null
                || !Boolean.TRUE.equals(
                        configuration.getEnabled())) {
            return new ExecutionOutcome(
                    command,
                    List.of());
        }
        Map<String, Object> workingPayload =
                new LinkedHashMap<>(command.payload());
        List<EntityMutationCommand> planned =
                new ArrayList<>();
        String candidateScenario =
                StringUtils.hasText(forcedScenarioCode)
                        ? forcedScenarioCode
                        : policyMatcher.match(
                                        configuration,
                                        command,
                                        beforeRecord,
                                        phase == EntityMutationPhase.AFTER_WRITE
                                                ? currentRecord
                                                : workingPayload)
                                .map(EntityVersionPolicyMatcher
                                        .MatchedScenario::scenarioCode)
                                .orElse(null);
        List<EntityVersionConfiguration.Step> steps =
                configuration.getSteps().stream()
                        .filter(item ->
                                !Boolean.FALSE.equals(
                                        item.getEnabled()))
                        .filter(item -> phase.name()
                                .equalsIgnoreCase(
                                        item.getPhase()))
                        .filter(item ->
                                !StringUtils.hasText(
                                        item.getScenarioCode())
                                || Objects.equals(
                                        item.getScenarioCode(),
                                        candidateScenario))
                        .sorted(Comparator.comparingInt(
                                item -> item.getSortOrder() == null
                                        ? 0
                                        : item.getSortOrder()))
                        .toList();
        for (EntityVersionConfiguration.Step step : steps) {
            EntityMutationStepResult result = executeStep(
                    step,
                    phase,
                    command,
                    beforeRecord,
                    currentRecord,
                    workingPayload);
            if (result.decision()
                    == EntityMutationStepResult.Decision.BLOCK) {
                throw new BusinessConflictException(
                        "ENTITY_MUTATION_BLOCKED",
                        StringUtils.hasText(result.message())
                                ? result.message()
                                : "实体变更被前置操作阻止: "
                                        + step.getStepName());
            }
            if (result.decision()
                    == EntityMutationStepResult.Decision.PATCH) {
                if (phase == EntityMutationPhase.AFTER_WRITE
                        || phase == EntityMutationPhase.AFTER_COMMIT) {
                    throw new IllegalStateException(
                            phase + " 阶段不能再修改实体写入数据");
                }
                merge(workingPayload, result.patch());
            }
            if (result.decision()
                    == EntityMutationStepResult.Decision.MUTATION_PLAN) {
                if (phase != EntityMutationPhase.PREPARE) {
                    throw new IllegalStateException(
                            "MUTATION_PLAN 只能在 PREPARE 阶段返回");
                }
                planned.addAll(parseMutationPlan(
                        command,
                        result.details()));
            }
        }
        return new ExecutionOutcome(
                new EntityMutationCommand(
                        command.operationId(),
                        command.entityCode(),
                        command.recordId(),
                        command.operationType(),
                        workingPayload,
                        command.context()),
                planned);
    }

    public Map<String, Object> catalog() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("phases",
                List.of("PREPARE", "BEFORE_WRITE",
                        "AFTER_WRITE", "AFTER_COMMIT"));
        result.put("stepTypes",
                List.of("BUILT_IN_RULE", "EXPRESSION",
                        "FIELD_MAPPING", "MANAGED_INTERFACE",
                        "JAVA_PROVIDER"));
        result.put("builtInRules",
                List.of(
                        Map.of("code", "REQUIRED_FIELDS",
                                "name", "必填字段校验"),
                        Map.of("code", "EXPECTED_VERSION",
                                "name", "基线版本校验"),
                        Map.of("code", "ALLOWED_STATUS",
                                "name", "允许状态校验"),
                        Map.of("code", "DATA_RANGE",
                                "name", "数据范围校验"),
                        Map.of("code", "UNIQUE",
                                "name", "唯一性校验")));
        return result;
    }

    private EntityMutationStepResult executeStep(
            EntityVersionConfiguration.Step step,
            EntityMutationPhase phase,
            EntityMutationCommand command,
            Map<String, Object> beforeRecord,
            Map<String, Object> currentRecord,
            Map<String, Object> workingPayload) {
        return switch (step.getStepType()) {
            case "BUILT_IN_RULE" -> builtInRuleExecutor.execute(
                    step,
                    command,
                    beforeRecord,
                    workingPayload);
            case "EXPRESSION" -> expression(
                    step,
                    command,
                    beforeRecord,
                    currentRecord,
                    workingPayload);
            case "FIELD_MAPPING" -> mapping(
                    step,
                    command,
                    beforeRecord,
                    workingPayload);
            case "MANAGED_INTERFACE" -> managedInterface(
                    step,
                    command,
                    beforeRecord,
                    workingPayload);
            case "JAVA_PROVIDER" -> javaProvider(
                    step,
                    phase,
                    command,
                    beforeRecord,
                    workingPayload);
            default -> throw new IllegalArgumentException(
                    "不支持的实体变更步骤类型: "
                            + step.getStepType());
        };
    }

    private EntityMutationStepResult expression(
            EntityVersionConfiguration.Step step,
            EntityMutationCommand command,
            Map<String, Object> beforeRecord,
            Map<String, Object> currentRecord,
            Map<String, Object> workingPayload) {
        Map<String, Object> condition =
                map(step.getConfig().getOrDefault(
                        "condition",
                        step.getConfig()));
        boolean allowed = policyMatcher.evaluateCondition(
                condition,
                command,
                beforeRecord,
                currentRecord == null
                        || currentRecord.isEmpty()
                        ? workingPayload
                        : currentRecord);
        return allowed
                ? EntityMutationStepResult.allow()
                : EntityMutationStepResult.block(
                        firstText(
                                step.getConfig()
                                        .get("message"),
                                "条件表达式不满足"));
    }

    private EntityMutationStepResult mapping(
            EntityVersionConfiguration.Step step,
            EntityMutationCommand command,
            Map<String, Object> beforeRecord,
            Map<String, Object> workingPayload) {
        Map<String, Object> patch = new LinkedHashMap<>();
        Map<String, Object> mappings =
                map(step.getConfig().get("mappings"));
        for (Map.Entry<String, Object> entry
                : mappings.entrySet()) {
            Object value = resolveMappingValue(
                    String.valueOf(entry.getValue()),
                    command,
                    beforeRecord,
                    workingPayload);
            setPath(patch, entry.getKey(), value);
        }
        Map<String, Object> constants =
                map(step.getConfig().get("constants"));
        constants.forEach((key, value) ->
                setPath(patch, key, value));
        return new EntityMutationStepResult(
                EntityMutationStepResult.Decision.PATCH,
                null,
                patch,
                Map.of());
    }

    private EntityMutationStepResult managedInterface(
            EntityVersionConfiguration.Step step,
            EntityMutationCommand command,
            Map<String, Object> beforeRecord,
            Map<String, Object> workingPayload) {
        String dataSourceId = firstText(
                step.getConfig().get("dataSourceId"),
                step.getProviderCode());
        String operationCode = firstText(
                step.getConfig().get("operationCode"));
        if (!StringUtils.hasText(dataSourceId)) {
            throw new IllegalArgumentException(
                    "受管理接口步骤未配置接口服务");
        }
        if (!StringUtils.hasText(operationCode)) {
            throw new IllegalArgumentException(
                    "受管理接口步骤未配置接口操作");
        }
        UiDataSourceExecuteRequest request =
                new UiDataSourceExecuteRequest();
        request.setEntityCode(command.entityCode());
        request.setServerEntityOperation(
                command.operationType().name());
        request.setServerIdempotencyKey(
                command.context().idempotencyKey());
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("recordId", defaultText(
                command.recordId(), ""));
        input.put("record", workingPayload);
        input.put("changedFields", workingPayload);
        input.put("params", command.context().extraParams());
        input.put("command", objectMapper.convertValue(
                command,
                new TypeReference<Map<String, Object>>() {
                }));
        input.put("beforeRecord",
                beforeRecord == null
                        ? Map.of() : beforeRecord);
        input.put("payload", workingPayload);
        input.put("extraParams",
                command.context().extraParams());
        request.setInput(input);
        request.setContext(Map.of(
                "sourceType",
                command.context().sourceType().name(),
                "sourceId",
                defaultText(
                        command.context().sourceId(),
                        ""),
                "businessIntentCode",
                command.context().businessIntentCode()));
        Object raw = dataSourceService
                .executeManagedMutationOperation(
                        dataSourceId,
                        operationCode,
                        request);
        return toStepResult(raw);
    }

    private EntityMutationStepResult javaProvider(
            EntityVersionConfiguration.Step step,
            EntityMutationPhase phase,
            EntityMutationCommand command,
            Map<String, Object> beforeRecord,
            Map<String, Object> workingPayload) {
        EntityMutationStepProvider provider =
                providers.stream()
                        .filter(item -> item.getCode()
                                .equalsIgnoreCase(
                                        step.getProviderCode()))
                        .findFirst()
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "实体变更 Provider 未注册: "
                                                + step.getProviderCode()));
        if (!provider.supportedPhases().contains(phase)) {
            throw new IllegalArgumentException(
                    "Provider 不支持阶段 "
                            + phase + ": "
                            + provider.getCode());
        }
        return provider.execute(
                new EntityMutationStepContext(
                        phase,
                        command,
                        beforeRecord,
                        workingPayload,
                        step.getConfig()));
    }

    private EntityMutationStepResult toStepResult(
            Object raw) {
        if (!(raw instanceof Map<?, ?> value)) {
            return EntityMutationStepResult.allow();
        }
        Map<String, Object> result = map(value);
        String decisionText = defaultText(
                firstText(result.get("decision"),
                        result.get("result")),
                "ALLOW");
        EntityMutationStepResult.Decision decision;
        try {
            decision = EntityMutationStepResult.Decision
                    .valueOf(decisionText
                            .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "受管理接口返回了不支持的变更决策: "
                            + decisionText);
        }
        return new EntityMutationStepResult(
                decision,
                firstText(result.get("message")),
                map(result.get("patch")),
                result);
    }

    private List<EntityMutationCommand> parseMutationPlan(
            EntityMutationCommand parent,
            Map<String, Object> details) {
        Object raw = details.get("mutations");
        if (!(raw instanceof Collection<?> values)) {
            return List.of();
        }
        List<EntityMutationCommand> result =
                new ArrayList<>();
        int index = 0;
        for (Object value : values) {
            Map<String, Object> item = map(value);
            String entityCode = firstText(
                    item.get("entityCode"));
            String operation = defaultText(
                    firstText(item.get("operationType")),
                    "UPDATE");
            EntityMutationOperationType operationType =
                    EntityMutationOperationType.valueOf(
                            operation.toUpperCase(
                                    Locale.ROOT));
            String recordId = firstText(
                    item.get("recordId"));
            Map<String, Object> payload =
                    map(item.get("payload"));
            EntityMutationContext context =
                    inheritedContext(parent.context(),
                            index++);
            result.add(new EntityMutationCommand(
                    parent.operationId()
                            + "-planned-" + index,
                    entityCode,
                    recordId,
                    operationType,
                    payload,
                    context));
        }
        return result;
    }

    private EntityMutationContext inheritedContext(
            EntityMutationContext parent,
            int index) {
        return new EntityMutationContext(
                parent.sourceType(),
                parent.sourceId(),
                parent.businessIntentCode(),
                parent.businessIntentName(),
                parent.sourceEntityCode(),
                parent.sourceRecordId(),
                parent.processDefinitionId(),
                parent.processInstanceId(),
                parent.taskId(),
                parent.operatorId(),
                parent.operatorName(),
                parent.businessTraceKey(),
                parent.idempotencyKey()
                        + ":planned:" + index,
                parent.extraParams());
    }

    private Object resolveMappingValue(
            String source,
            EntityMutationCommand command,
            Map<String, Object> beforeRecord,
            Map<String, Object> workingPayload) {
        if (!StringUtils.hasText(source)) {
            return null;
        }
        int separator = source.indexOf(':');
        String scope = separator < 0
                ? "PAYLOAD"
                : source.substring(0, separator);
        String path = separator < 0
                ? source
                : source.substring(separator + 1);
        return switch (scope.toUpperCase(Locale.ROOT)) {
            case "BEFORE" -> path(beforeRecord, path);
            case "CONTEXT" -> path(
                    objectMapper.convertValue(
                            command.context(),
                            new TypeReference<Map<String, Object>>() {
                            }),
                    path);
            case "EXTRA" -> path(
                    command.context().extraParams(),
                    path);
            default -> path(workingPayload, path);
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>(
                    (Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    private Object path(
            Map<String, Object> source,
            String path) {
        if (source == null
                || !StringUtils.hasText(path)) {
            return null;
        }
        Object current = source;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private void setPath(
            Map<String, Object> target,
            String path,
            Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = target;
        for (int i = 0; i < parts.length - 1; i++) {
            Object nested = current.computeIfAbsent(
                    parts[i],
                    ignored -> new LinkedHashMap<>());
            if (!(nested instanceof Map<?, ?>)) {
                throw new IllegalArgumentException(
                        "字段映射目标路径冲突: " + path);
            }
            current = (Map<String, Object>) nested;
        }
        current.put(parts[parts.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    private void merge(
            Map<String, Object> target,
            Map<String, Object> patch) {
        patch.forEach((key, value) -> {
            Object existing = target.get(key);
            if (existing instanceof Map<?, ?>
                    && value instanceof Map<?, ?>) {
                merge((Map<String, Object>) existing,
                        (Map<String, Object>) value);
            } else {
                target.put(key, value);
            }
        });
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value != null
                    && StringUtils.hasText(
                            String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private String defaultText(
            String value,
            String fallback) {
        return StringUtils.hasText(value)
                ? value : fallback;
    }

    public record ExecutionOutcome(
            EntityMutationCommand command,
            List<EntityMutationCommand> plannedCommands) {
    }
}
