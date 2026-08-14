package com.workflow.process.task.application.nextapproval;

import org.flowable.common.engine.api.variable.VariableContainer;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 使用当前 Flowable 引擎的 ExpressionManager 计算路由条件。
 */
@Component
public class FlowableConditionEvaluator {

    private static final Pattern ROOT_IDENTIFIER = Pattern.compile(
            "(?<![.\\w])([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern STRING_LITERAL = Pattern.compile(
            "'(?:\\\\.|[^'\\\\])*'|\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Set<String> EL_KEYWORDS = Set.of(
            "true", "false", "null", "empty", "and", "or", "not",
            "eq", "ne", "lt", "gt", "le", "ge", "div", "mod");

    private final ProcessEngineConfigurationImpl configuration;

    public FlowableConditionEvaluator(ProcessEngine processEngine) {
        if (!(processEngine.getProcessEngineConfiguration()
                instanceof ProcessEngineConfigurationImpl engineConfiguration)) {
            throw new IllegalStateException(
                    "Flowable 引擎配置不支持条件预览");
        }
        this.configuration = engineConfiguration;
    }

    public boolean evaluate(
            String expression,
            Map<String, Object> variables) {
        assertPreviewSafe(expression, variables);
        Object value = configuration.getExpressionManager()
                .createExpression(expression)
                .getValue(new MapVariableContainer(variables));
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private void assertPreviewSafe(
            String expression,
            Map<String, Object> variables) {
        String body = expression == null ? "" : expression.trim();
        if ((body.startsWith("${") || body.startsWith("#{"))
                && body.endsWith("}")) {
            body = body.substring(2, body.length() - 1);
        }
        if (body.contains("(")
                || body.contains(")")
                || body.contains(":")
                || body.contains(";")
                || body.contains(".class")
                || body.contains("getClass")) {
            throw new UnsafePreviewExpressionException(
                    "条件包含方法、函数或类型访问");
        }
        String identifiersOnly = STRING_LITERAL.matcher(body)
                .replaceAll(" ");
        Matcher matcher = ROOT_IDENTIFIER.matcher(identifiersOnly);
        while (matcher.find()) {
            String identifier = matcher.group(1);
            if (EL_KEYWORDS.contains(identifier.toLowerCase())
                    || variables.containsKey(identifier)) {
                continue;
            }
            throw new UnsafePreviewExpressionException(
                    "条件引用了非流程变量: " + identifier);
        }
    }

    public static class UnsafePreviewExpressionException
            extends RuntimeException {
        public UnsafePreviewExpressionException(String message) {
            super(message);
        }
    }

    private static final class MapVariableContainer
            implements VariableContainer {

        private final Map<String, Object> values;

        private MapVariableContainer(Map<String, Object> values) {
            this.values = new LinkedHashMap<>(values);
        }

        @Override
        public boolean hasVariable(String variableName) {
            return values.containsKey(variableName);
        }

        @Override
        public Object getVariable(String variableName) {
            return values.get(variableName);
        }

        @Override
        public void setVariable(String variableName, Object value) {
            values.put(variableName, value);
        }

        @Override
        public void setTransientVariable(String variableName, Object value) {
            values.put(variableName, value);
        }

        @Override
        public String getTenantId() {
            return null;
        }

        @Override
        public Set<String> getVariableNames() {
            return Set.copyOf(values.keySet());
        }
    }
}
