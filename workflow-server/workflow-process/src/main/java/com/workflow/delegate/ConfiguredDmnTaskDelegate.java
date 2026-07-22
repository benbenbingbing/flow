package com.workflow.delegate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.flowable.dmn.api.DecisionExecutionAuditContainer;
import org.flowable.dmn.api.DmnDecisionService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component("configuredDmnTaskDelegate")
@RequiredArgsConstructor
public class ConfiguredDmnTaskDelegate implements JavaDelegate {

    private final DmnDecisionService decisionService;
    private final ObjectMapper objectMapper;

    @Override
    public void execute(DelegateExecution execution) {
        try {
            executeConfigured(execution);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("DMN 规则任务执行失败: " + exception.getMessage(), exception);
        }
    }

    private void executeConfigured(DelegateExecution execution) throws Exception {
        String configDocument = ConfiguredTaskPropertyReader.read(
                execution.getCurrentFlowElement(),
                "ruleConfig");
        if (!StringUtils.hasText(configDocument)) {
            throw new IllegalArgumentException("业务规则任务缺少 ruleConfig");
        }
        JsonNode config = objectMapper.readTree(configDocument);
        String decisionRef = config.path("decisionRef").asText("");
        if (!StringUtils.hasText(decisionRef)) {
            throw new IllegalArgumentException("业务规则任务未配置决策表Key");
        }

        Map<String, Object> variables = resolveInputVariables(
                config.path("inputVariables").asText(""),
                execution);
        DecisionExecutionAuditContainer audit = decisionService.createExecuteDecisionBuilder()
                .decisionKey(decisionRef)
                .instanceId(execution.getProcessInstanceId())
                .executionId(execution.getId())
                .activityId(execution.getCurrentActivityId())
                .tenantId(execution.getTenantId())
                .variables(variables)
                .executeWithAuditTrail();
        if (Boolean.TRUE.equals(audit.isFailed())) {
            throw new IllegalStateException(
                    "DMN 决策执行失败: " + audit.getExceptionMessage());
        }

        List<Map<String, Object>> result = audit.getDecisionResult();
        String resultVariable = config.path("resultVariable").asText("");
        if (StringUtils.hasText(resultVariable)) {
            execution.setVariable(
                    resultVariable,
                    result != null && result.size() == 1 ? result.get(0) : result);
        }
        if (config.path("mapDecisionResult").asBoolean(true)
                && result != null
                && result.size() == 1) {
            result.get(0).forEach(execution::setVariable);
        }
    }

    private Map<String, Object> resolveInputVariables(
            String mappingDocument,
            DelegateExecution execution) throws Exception {
        if (!StringUtils.hasText(mappingDocument)) {
            return new LinkedHashMap<>(execution.getVariables());
        }
        JsonNode mapping = objectMapper.readTree(mappingDocument);
        if (!mapping.isObject()) {
            throw new IllegalArgumentException("DMN 输入变量必须是 JSON 对象");
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        mapping.fields().forEachRemaining(entry ->
                variables.put(entry.getKey(), resolveValue(entry.getValue(), execution)));
        return variables;
    }

    private Object resolveValue(JsonNode value, DelegateExecution execution) {
        if (!value.isTextual()) {
            return objectMapper.convertValue(value, Object.class);
        }
        String text = value.asText();
        if (text.startsWith("${") && text.endsWith("}") && text.indexOf('}') == text.length() - 1) {
            return execution.getVariable(text.substring(2, text.length() - 1).trim());
        }
        return text;
    }
}
