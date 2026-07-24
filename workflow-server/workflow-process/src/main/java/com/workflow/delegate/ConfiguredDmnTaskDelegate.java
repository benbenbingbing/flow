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

/**
 * 配置化 DMN 业务规则任务代理
 * 从节点 ruleConfig 配置中读取决策表Key与输入输出映射，调用 Flowable DMN 引擎执行决策，
 * 并将决策结果写回流程变量。
 */
@Component("configuredDmnTaskDelegate")
@RequiredArgsConstructor
public class ConfiguredDmnTaskDelegate implements JavaDelegate {

    /** Flowable DMN 决策服务，执行决策表 */
    private final DmnDecisionService decisionService;
    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper;

    /**
     * 节点执行回调：执行配置化的 DMN 决策并回写结果。
     * <p>
     * 受检异常包装为 IllegalStateException，运行时异常原样抛出。
     *
     * @param execution Flowable 执行上下文
     * @throws IllegalStateException 当决策执行失败或配置缺失时抛出
     */
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

    /**
     * 执行配置化的 DMN 决策：读取配置、组装输入变量、执行决策、回写结果变量。
     *
     * @param execution Flowable 执行上下文
     * @throws Exception 配置缺失或决策执行失败时抛出
     */
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

    /**
     * 解析决策输入变量映射：无映射时返回全部流程变量，有映射时按映射键取值。
     *
     * @param mappingDocument 输入变量映射 JSON，可为空
     * @param execution       Flowable 执行上下文
     * @return 决策输入变量集合
     * @throws Exception 当映射 JSON 不是对象时抛出
     */
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

    /**
     * 解析单个映射值：文本形如 {@code ${var}} 时取流程变量，否则原样返回或转换。
     *
     * @param value     JSON 节点值
     * @param execution Flowable 执行上下文
     * @return 解析后的值
     */
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
