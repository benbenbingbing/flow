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

/**
 * 配置化发送任务代理
 * 从节点 sendConfig 配置中读取接收人、渠道、主题、内容模板，解析后通过抄送运行时
 * 向指定接收人投递通知消息，并把发送数量回写到流程变量。
 */
@Component("configuredSendTaskDelegate")
@RequiredArgsConstructor
public class ConfiguredSendTaskDelegate implements JavaDelegate {

    /** 流程变量占位符正则，形如 ${var} */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /** 抄送运行时服务，触发消息投递 */
    private final ProcessCcRuntimeService ccRuntimeService;
    /** Flowable 仓库服务，查询流程定义 */
    private final RepositoryService repositoryService;
    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper;

    /**
     * 节点执行回调：执行配置化的发送任务。
     * <p>
     * 受检异常包装为 IllegalStateException，运行时异常原样抛出。
     *
     * @param execution Flowable 执行上下文
     * @throws IllegalStateException 当配置缺失或接收人解析为空时抛出
     */
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

    /**
     * 执行配置化的发送任务：读取配置、解析接收人与模板、组装抄送配置并触发投递。
     *
     * @param execution Flowable 执行上下文
     * @throws Exception 配置缺失或接收人解析为空时抛出
     */
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

    /**
     * 将渠道名称归一化为大写标准码（站内信/邮件/短信等）。
     *
     * @param value 渠道名称
     * @return 标准渠道码
     */
    private String channelCode(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "message", "in_app" -> "IN_APP";
            case "email" -> "EMAIL";
            case "sms" -> "SMS";
            default -> value.toUpperCase(Locale.ROOT);
        };
    }

    /**
     * 按逗号、分号或空白符拆分接收人字符串。
     *
     * @param value 接收人字符串
     * @return 接收人列表
     */
    private List<String> splitRecipients(String value) {
        List<String> recipients = new ArrayList<>();
        for (String item : value.split("[,;\\s]+")) {
            if (!item.isBlank()) {
                recipients.add(item.trim());
            }
        }
        return recipients;
    }

    /**
     * 解析模板字符串：将形如 {@code ${var}} 的占位符替换为对应流程变量值。
     *
     * @param template  模板字符串
     * @param execution Flowable 执行上下文
     * @return 解析后的字符串
     */
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

    /**
     * 根据主题、内容与模板Key拼装发送摘要。
     *
     * @param subject     主题
     * @param content     内容
     * @param templateKey 模板Key
     * @return 摘要字符串
     */
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
