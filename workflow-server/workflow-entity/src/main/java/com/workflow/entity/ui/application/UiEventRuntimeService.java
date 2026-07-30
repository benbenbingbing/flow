package com.workflow.entity.ui.application;

import com.workflow.entity.ui.api.request.UiDataSourceExecuteRequest;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.api.response.UiEventExecutionResult;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditResult;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * 统一 UI 事件执行链运行时。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UiEventRuntimeService {

    private static final Set<String> WRITE_EVENTS = Set.of(
            "DATA_CREATE", "DATA_UPDATE", "DATA_DELETE",
            "DATA_BATCH_DELETE", "FORM_SAVE", "SUBFORM_SAVE",
            "TOOLBAR_BUTTON_CLICK", "ROW_BUTTON_CLICK",
            "FORM_BUTTON_CLICK", "FIELD_BUTTON_CLICK");

    private final UiEventBindingService bindingService;
    private final UiDataSourceService dataSourceService;
    private final UiEventValueMapper valueMapper;
    private final EntitySelectionRuntimeService selectionRuntimeService;
    private final SystemAuditPort auditPort;

    /**
     * 执行已发布事件。按钮和字段事件可直接调用此入口。
     */
    public UiEventExecutionResult execute(
            UiEventExecuteRequest request) {
        return execute(request, null);
    }

    /**
     * 执行事件链，并在没有 REPLACE 步骤时调用平台默认处理。
     */
    public UiEventExecutionResult execute(
            UiEventExecuteRequest request,
            Function<Map<String, Object>, Object> defaultHandler) {
        long startedAt = System.nanoTime();
        try {
            UiEventExecutionResult result =
                    executeChain(request, defaultHandler);
            recordExecution(
                    request,
                    result,
                    null,
                    startedAt);
            return result;
        } catch (RuntimeException exception) {
            recordExecution(
                    request,
                    null,
                    exception,
                    startedAt);
            throw exception;
        }
    }

    private UiEventExecutionResult executeChain(
            UiEventExecuteRequest request,
            Function<Map<String, Object>, Object> defaultHandler) {
        UiEventBindingService.ResolvedEventChain chain =
                bindingService.resolvePublished(request);
        UiEventExecutionResult result = new UiEventExecutionResult();
        Object selection =
                selectionRuntimeService.resolve(request, chain);
        Map<String, Object> state =
                initialState(request, selection);
        Object latest = null;

        for (Map<String, Object> step :
                steps(chain.steps(), "BEFORE")) {
            Object stepResult = executeStep(
                    step, request, chain, state, result);
            if (stepResult instanceof Map<?, ?> map) {
                mutableInput(state).putAll(stringMap(map));
            }
            if (stepResult != null) {
                latest = stepResult;
            }
        }

        List<Map<String, Object>> replacements =
                steps(chain.steps(), "REPLACE");
        if (!replacements.isEmpty()) {
            result.setReplaced(true);
            latest = executeStep(
                    replacements.get(0),
                    request,
                    chain,
                    state,
                    result);
        } else if (defaultHandler != null) {
            latest = defaultHandler.apply(
                    new LinkedHashMap<>(mutableInput(state)));
            result.setDefaultExecuted(true);
            trace(result, "PLATFORM_DEFAULT", "SUCCESS", null);
        }

        state.put("result", latest);
        for (Map<String, Object> step :
                steps(chain.steps(), "AFTER")) {
            Object stepResult = executeStep(
                    step, request, chain, state, result);
            if (Boolean.TRUE.equals(step.get("replaceResult"))) {
                latest = stepResult;
                state.put("result", latest);
            }
        }
        result.setData(latest);
        return result;
    }

    private void recordExecution(
            UiEventExecuteRequest request,
            UiEventExecutionResult result,
            RuntimeException exception,
            long startedAt) {
        String eventCode = normalize(
                request == null ? null : request.getEventCode());
        boolean write = WRITE_EVENTS.contains(eventCode);
        Map<String, Object> before = new LinkedHashMap<>();
        if (request != null) {
            before.put("configType", normalize(request.getConfigType()));
            before.put("configId", request.getConfigId());
            before.put("releaseId", request.getReleaseId());
            before.put("releaseVersion", request.getReleaseVersion());
            before.put("entityCode", request.getEntityCode());
            before.put("listKey", request.getListKey());
            before.put("targetType", normalize(request.getTargetType()));
            before.put("targetKey", request.getTargetKey());
            before.put("recordId", request.getRecordId());
            before.put(
                    "selectedCount",
                    request.getSelectedIds() == null
                            ? 0 : request.getSelectedIds().size());
        }
        Map<String, Object> after = null;
        if (result != null) {
            after = new LinkedHashMap<>();
            after.put("defaultExecuted", result.isDefaultExecuted());
            after.put("replaced", result.isReplaced());
            after.put("message", result.getMessage());
            after.put("effects", result.getEffects());
            after.put("trace", result.getTrace());
        }
        try {
            auditPort.record(SystemAuditEvent.builder()
                    .eventId(UUID.randomUUID().toString()
                            .replace("-", ""))
                    .traceId(MDC.get("traceId"))
                    .module(AuditModule.INTEGRATION)
                    .action(write ? AuditAction.UPDATE : AuditAction.OTHER)
                    .operationName("执行UI事件链:" + eventCode)
                    .riskLevel(write
                            ? AuditRiskLevel.HIGH
                            : AuditRiskLevel.LOW)
                    .result(exception == null
                            ? AuditResult.SUCCESS
                            : AuditResult.FAILURE)
                    .required(false)
                    .operatorId(UserContext.getUserId())
                    .operatorName(UserContext.getUsername())
                    .targetType("UI_EVENT")
                    .targetId(eventTarget(request))
                    .targetName(eventCode)
                    .summary(executionSummary(
                            eventCode, result, exception))
                    .beforeData(before)
                    .afterData(after)
                    .changedFields(result == null
                            ? null : result.getTrace())
                    .errorCode(exception == null
                            ? null
                            : exception.getClass().getSimpleName())
                    .errorMessage(exception == null
                            ? null : exception.getMessage())
                    .durationMs((System.nanoTime() - startedAt)
                            / 1_000_000)
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (RuntimeException auditException) {
            log.warn(
                    "记录UI事件执行日志失败: eventCode={}, configId={}",
                    eventCode,
                    request == null ? null : request.getConfigId(),
                    auditException);
        }
    }

    private String eventTarget(UiEventExecuteRequest request) {
        if (request == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        parts.add(normalize(request.getConfigType()));
        parts.add(text(request.getConfigId()));
        if (StringUtils.hasText(request.getTargetType())) {
            parts.add(normalize(request.getTargetType()));
        }
        if (StringUtils.hasText(request.getTargetKey())) {
            parts.add(request.getTargetKey());
        }
        return parts.stream()
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.joining(":"));
    }

    private String executionSummary(
            String eventCode,
            UiEventExecutionResult result,
            RuntimeException exception) {
        if (exception != null) {
            return eventCode + " 执行失败: "
                    + firstText(exception.getMessage(), "未知错误");
        }
        int stepCount = result == null || result.getTrace() == null
                ? 0 : result.getTrace().size();
        String main = result != null && result.isReplaced()
                ? "自定义接口替代平台处理"
                : result != null && result.isDefaultExecuted()
                ? "已执行平台默认处理"
                : "仅执行自定义映射或接口";
        return eventCode + " 执行成功，" + main
                + "，步骤数 " + stepCount;
    }

    private Object executeStep(
            Map<String, Object> step,
            UiEventExecuteRequest request,
            UiEventBindingService.ResolvedEventChain chain,
            Map<String, Object> state,
            UiEventExecutionResult result) {
        if (!valueMapper.matches(step.get("condition"), state)) {
            trace(result, stepLabel(step), "SKIPPED", null);
            return null;
        }
        String failurePolicy = normalize(text(
                step.getOrDefault("failurePolicy", "STOP")));
        try {
            Object raw;
            String serviceId = firstText(
                    step.get("serviceId"),
                    step.get("sourceId"));
            if (StringUtils.hasText(serviceId)) {
                Object mappedInput = valueMapper.apply(
                        step.get("inputMapping"),
                        state,
                        state.get("input"));
                if (!(mappedInput instanceof Map<?, ?> inputMap)) {
                    throw new IllegalArgumentException(
                            "事件接口输入映射结果必须为对象");
                }
                UiDataSourceExecuteRequest execute =
                        new UiDataSourceExecuteRequest();
                execute.setUsage(normalize(request.getEventCode()));
                execute.setOperationCode(firstText(
                        step.get("operationCode"), "default"));
                execute.setConfigType(normalize(request.getConfigType()));
                execute.setConfigId(request.getConfigId());
                execute.setReleaseId(chain.releaseId());
                execute.setReleaseVersion(chain.releaseVersion());
                execute.setEntityCode(chain.entityCode());
                execute.setListKey(chain.listKey());
                execute.setInput(stringMap(inputMap));
                execute.setContext(runtimeContext(request, state));
                execute.setServerIdempotencyKey(
                        request.getServerIdempotencyKey());
                raw = dataSourceService.executeOperation(
                        serviceId,
                        execute.getOperationCode(),
                        execute);
            } else {
                raw = state;
            }
            Object outputMapping = step.get("outputMapping");
            Map<String, Object> mappingSource =
                    StringUtils.hasText(serviceId)
                            ? Map.of(
                                    "data",
                                    raw == null ? Map.of() : raw,
                                    "response",
                                    raw == null ? Map.of() : raw,
                                    "state",
                                    state)
                            : state;
            Object mapped = valueMapper.apply(
                    outputMapping,
                    mappingSource,
                    raw);
            if (outputMapping instanceof List<?> mappings
                    && !mappings.isEmpty()
                    && mapped instanceof Map<?, ?> data) {
                Map<String, Object> effect = new LinkedHashMap<>();
                effect.put("type", "FIELD_MAPPING");
                effect.put("data", stringMap(data));
                effect.put("mappings", mappings);
                result.getEffects().add(effect);
            }
            collectEnvelope(mapped, result);
            trace(result, stepLabel(step), "SUCCESS", null);
            return mapped;
        } catch (RuntimeException exception) {
            trace(
                    result,
                    stepLabel(step),
                    "FAILED",
                    exception.getMessage());
            if ("CONTINUE".equals(failurePolicy)) {
                return null;
            }
            if ("EMPTY".equals(failurePolicy)) {
                return Map.of();
            }
            throw exception;
        }
    }

    private Map<String, Object> initialState(
            UiEventExecuteRequest request,
            Object selection) {
        Map<String, Object> input = request.getInput() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(request.getInput());
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("input", input);
        state.put("data", input);
        state.put("context", request.getContext() == null
                ? Map.of() : request.getContext());
        state.put("selection", selection);
        state.put("recordId", request.getRecordId());
        state.put("selectedIds", request.getSelectedIds() == null
                ? List.of() : request.getSelectedIds());
        return state;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mutableInput(
            Map<String, Object> state) {
        return (Map<String, Object>) state.get("input");
    }

    private Map<String, Object> runtimeContext(
            UiEventExecuteRequest request,
            Map<String, Object> state) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (request.getContext() != null) {
            context.putAll(request.getContext());
        }
        context.put("eventCode", normalize(request.getEventCode()));
        context.put("targetType", normalize(request.getTargetType()));
        context.put("targetKey", request.getTargetKey());
        context.put("recordId", request.getRecordId());
        context.put("selectedIds", request.getSelectedIds() == null
                ? List.of() : request.getSelectedIds());
        context.put("eventState", state);
        return context;
    }

    private void collectEnvelope(
            Object value,
            UiEventExecutionResult result) {
        if (!(value instanceof Map<?, ?> map)) {
            return;
        }
        Object message = map.get("message");
        if (message != null && StringUtils.hasText(String.valueOf(message))) {
            result.setMessage(String.valueOf(message));
        }
        if (map.get("effects") instanceof List<?> effects) {
            effects.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> stringMap((Map<?, ?>) item))
                    .forEach(result.getEffects()::add);
        }
    }

    private List<Map<String, Object>> steps(
            List<Map<String, Object>> source,
            String strategy) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> step : source) {
            if (strategy.equals(normalize(text(
                    step.getOrDefault("strategy", "BEFORE"))))) {
                result.add(step);
            }
        }
        return result;
    }

    private void trace(
            UiEventExecutionResult result,
            String step,
            String status,
            String message) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("step", step);
        trace.put("status", status);
        if (StringUtils.hasText(message)) {
            trace.put("message", message);
        }
        result.getTrace().add(trace);
    }

    private String stepLabel(Map<String, Object> step) {
        return firstText(
                step.get("name"),
                step.get("operationCode"),
                step.get("serviceId"),
                "MAPPING");
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) ->
                result.put(String.valueOf(key), value));
        return result;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value != null
                    && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }
}
