package com.workflow.process.task.infrastructure.flowable;

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

    /**
     * 初始化Flowable条件求值器，保存构造参数供后续方法使用。
     *
     * @param processEngine 流程{@code engine}，保存在对象中供后续校验、查询或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    public FlowableConditionEvaluator(ProcessEngine processEngine) {
        if (!(processEngine.getProcessEngineConfiguration()
                instanceof ProcessEngineConfigurationImpl engineConfiguration)) {
            throw new IllegalStateException(
                    "Flowable 引擎配置不支持条件预览");
        }
        this.configuration = engineConfiguration;
    }

    /**
     * 求值Flowable条件求值器，并将结果传给后续步骤。
     *
     * @param expression 表达式，作为 {@code assertPreviewSafe} 的输入影响后续处理
     * @param variables 流程变量，后续传给流程引擎或规则求值器使用
     * @return Flowable条件求值器条件成立时为 true，否则为 false
     */
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

    /**
     * 处理{@code assert}预览安全，并将结果传给后续步骤。
     *
     * @param expression 表达式，供本方法处理{@code assert}预览安全时使用
     * @param variables 流程变量，后续传给流程引擎或规则求值器使用
     */
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

    /**
     * 负责{@code unsafe}预览表达式的业务处理；协调校验、状态变化及后续结果传递。
     */
    public static class UnsafePreviewExpressionException
            extends RuntimeException {
        /**
         * 初始化{@code unsafe}预览表达式异常，保存构造参数供后续方法使用。
         *
         * @param message 消息，保存在对象中供后续校验、查询或展示
         */
        public UnsafePreviewExpressionException(String message) {
            super(message);
        }
    }

    /**
     * 负责映射变量{@code container}的业务处理；协调校验、状态变化及后续结果传递。
     */
    private static final class MapVariableContainer
            implements VariableContainer {

        private final Map<String, Object> values;

        /**
         * 初始化映射变量{@code container}，保存构造参数供后续方法使用。
         *
         * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
         */
        private MapVariableContainer(Map<String, Object> values) {
            this.values = new LinkedHashMap<>(values);
        }

        /**
         * 判断是否具有变量；判断结果决定调用方的后续分支。
         *
         * @param variableName 变量名称，后续用于判断是否具有变量时匹配或展示
         * @return 变量条件成立时为 true，否则为 false
         */
        @Override
        public boolean hasVariable(String variableName) {
            return values.containsKey(variableName);
        }

        /**
         * 读取变量；查询结果供调用方展示或继续处理。
         *
         * @param variableName 变量名称，后续用于读取变量时匹配或展示
         * @return 符合条件的映射变量{@code container}结果，供调用方继续处理
         */
        @Override
        public Object getVariable(String variableName) {
            return values.get(variableName);
        }

        /**
         * 设置变量；后续读取或执行将使用更新后的状态。
         *
         * @param variableName 变量名称，后续用于设置变量时匹配或展示
         * @param value 待设置变量的原始输入，结果供调用方继续使用
         */
        @Override
        public void setVariable(String variableName, Object value) {
            values.put(variableName, value);
        }

        /**
         * 设置{@code transient}变量；后续读取或执行将使用更新后的状态。
         *
         * @param variableName 变量名称，后续用于设置{@code transient}变量时匹配或展示
         * @param value 待设置{@code transient}变量的原始输入，结果供调用方继续使用
         */
        @Override
        public void setTransientVariable(String variableName, Object value) {
            values.put(variableName, value);
        }

        /**
         * 读取租户ID；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的租户ID文本，供调用方比较或展示
         */
        @Override
        public String getTenantId() {
            return null;
        }

        /**
         * 读取变量名称集合；查询结果供调用方展示或继续处理。
         *
         * @return 映射变量{@code container}集合，供调用方遍历或展示
         */
        @Override
        public Set<String> getVariableNames() {
            return Set.copyOf(values.keySet());
        }
    }
}
