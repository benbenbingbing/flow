package com.workflow.delegate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置化 Groovy 脚本任务代理。
 */
@Component("configuredScriptTaskDelegate")
@RequiredArgsConstructor
public class ConfiguredScriptTaskDelegate implements JavaDelegate {

    private final ObjectMapper objectMapper;

    @Override
    public void execute(DelegateExecution execution) {
        String configDocument = ConfiguredTaskPropertyReader.read(
                execution.getCurrentFlowElement(),
                "scriptConfig");
        if (configDocument == null || configDocument.isBlank()) {
            throw new IllegalArgumentException("脚本任务缺少 scriptConfig");
        }
        try {
            JsonNode config = objectMapper.readTree(configDocument);
            String format = config.path("scriptFormat").asText("");
            if (!"groovy".equalsIgnoreCase(format)) {
                throw new IllegalArgumentException(
                        "脚本任务当前仅支持 Groovy: " + format);
            }
            String script = config.path("script").asText("");
            if (script.isBlank()) {
                throw new IllegalArgumentException("脚本任务内容不能为空");
            }

            Map<String, Object> initialVariables =
                    new LinkedHashMap<>(execution.getVariables());
            initialVariables.put("execution", execution);
            Binding binding = new Binding(initialVariables);
            Object result = new GroovyShell(binding).evaluate(script);

            String resultVariable = config.path("resultVariable").asText("");
            if (!resultVariable.isBlank()) {
                execution.setVariable(resultVariable, result);
            }
            if (config.path("autoStoreVariables").asBoolean(false)) {
                binding.getVariables().forEach((name, value) -> {
                    if (name instanceof String variableName
                            && !"execution".equals(variableName)) {
                        execution.setVariable(variableName, value);
                    }
                });
            }
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Groovy 脚本任务执行失败: " + exception.getMessage(),
                    exception);
        }
    }
}
