package com.workflow.delegate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.service.ProcessCcRuntimeService;
import com.workflow.service.cc.CcRuntimeContext;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component("configuredSendTaskDelegate")
@RequiredArgsConstructor
public class ConfiguredSendTaskDelegate implements JavaDelegate {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final ProcessCcRuntimeService ccRuntimeService;
    private final RepositoryService repositoryService;
    private final ObjectMapper objectMapper;

    @Override
    public void execute(DelegateExecution execution) {
        try {
            executeConfigured(execution);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("发送任务执行失败: " + exception.getMessage(), exception);
        }
    }

    private void executeConfigured(DelegateExecution execution) throws Exception {
        String configDocument = ConfiguredTaskPropertyReader.read(
                execution.getCurrentFlowElement(),
                "sendConfig");
        if (!StringUtils.hasText(configDocument)) {
            throw new IllegalArgumentException("发送任务缺少 sendConfig");
        }
        JsonNode config = objectMapper.readTree(configDocument);
        List<String> recipients = splitRecipients(resolveTemplate(
                config.path("to").asText(""),
                execution));
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("发送任务接收人解析结果为空");
        }

        ObjectNode ccConfig = objectMapper.createObjectNode();
        ccConfig.put("enabled", true);
        ccConfig.putArray("timings").add("EXPLICIT");
        ArrayNode channels = ccConfig.putArray("channels");
        config.path("channels").forEach(channel ->
                channels.add(channelCode(channel.asText())));
        ObjectNode recipientRule = ccConfig.putArray("recipientRules").addObject();
        recipientRule.put("type", "USER");
        recipients.forEach(recipientRule.putArray("values")::add);
        ccConfig.put("includeOperator", false);

        String subject = resolveTemplate(config.path("subject").asText(""), execution);
        String content = resolveTemplate(config.path("content").asText(""), execution);
        String templateKey = config.path("templateKey").asText("");
        ccConfig.put("summary", buildSummary(subject, content, templateKey));

        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(execution.getProcessDefinitionId())
                .singleResult();
        int created = ccRuntimeService.trigger(new CcRuntimeContext(
                execution.getProcessInstanceId(),
                execution.getProcessDefinitionId(),
                definition == null ? null : definition.getKey(),
                definition == null ? null : definition.getName(),
                execution.getProcessInstanceBusinessKey(),
                execution.getCurrentActivityId(),
                execution.getCurrentActivityName(),
                "EXPLICIT",
                null,
                execution.getVariables()), objectMapper.writeValueAsString(ccConfig));
        execution.setVariable(execution.getCurrentActivityId() + "_sendCount", created);
    }

    private String channelCode(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "message", "in_app" -> "IN_APP";
            case "email" -> "EMAIL";
            case "sms" -> "SMS";
            default -> value.toUpperCase(Locale.ROOT);
        };
    }

    private List<String> splitRecipients(String value) {
        List<String> recipients = new ArrayList<>();
        for (String item : value.split("[,;\\s]+")) {
            if (!item.isBlank()) {
                recipients.add(item.trim());
            }
        }
        return recipients;
    }

    private String resolveTemplate(String template, DelegateExecution execution) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            Object value = execution.getVariable(matcher.group(1).trim());
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : value.toString()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String buildSummary(String subject, String content, String templateKey) {
        StringBuilder summary = new StringBuilder();
        if (!subject.isBlank()) {
            summary.append(subject);
        }
        if (!content.isBlank()) {
            if (!summary.isEmpty()) {
                summary.append(" - ");
            }
            summary.append(content);
        }
        if (!templateKey.isBlank()) {
            if (!summary.isEmpty()) {
                summary.append(" [");
            }
            summary.append(templateKey);
            if (summary.indexOf("[") >= 0) {
                summary.append(']');
            }
        }
        return summary.toString();
    }
}
