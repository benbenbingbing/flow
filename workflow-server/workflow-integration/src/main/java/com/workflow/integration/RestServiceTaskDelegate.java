package com.workflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component("restServiceTaskDelegate")
@RequiredArgsConstructor
public class RestServiceTaskDelegate implements JavaDelegate {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final ObjectMapper objectMapper;

    @Override
    public void execute(DelegateExecution execution) {
        try {
            executeConfigured(execution);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("REST 服务任务执行失败: " + exception.getMessage(), exception);
        }
    }

    private void executeConfigured(DelegateExecution execution) throws Exception {
        String configDocument = readProperty(execution.getCurrentFlowElement(), "restConfig");
        if (!StringUtils.hasText(configDocument)) {
            throw new IllegalArgumentException("REST 服务任务缺少 restConfig");
        }
        JsonNode config = objectMapper.readTree(configDocument);
        String url = appendQueryParameters(
                resolveTemplate(config.path("url").asText(""), execution),
                config.path("queryParams").asText(""),
                execution);
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("REST 服务任务未配置请求URL");
        }
        String contentType = config.path("contentType").asText("application/json");
        if ("multipart/form-data".equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException("REST 服务任务暂不支持 multipart/form-data，请使用自定义连接器");
        }

        int timeout = Math.max(1, config.path("timeout").asInt(30));
        int retryCount = Math.max(0, Math.min(5, config.path("retryCount").asInt(0)));
        String errorHandling = config.path("errorHandling").asText("throw");
        Exception lastError = null;
        for (int attempt = 0; attempt <= retryCount; attempt++) {
            try {
                HttpResponse<String> response = executeRequest(
                        config,
                        execution,
                        url,
                        contentType,
                        timeout);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException(
                            "HTTP " + response.statusCode() + ": " + response.body());
                }
                mapResult(config.path("resultMapping").asText(""), response.body(), execution);
                execution.setVariable(execution.getCurrentActivityId() + "_httpStatus", response.statusCode());
                execution.setVariable(execution.getCurrentActivityId() + "_httpResponse", response.body());
                return;
            } catch (Exception exception) {
                lastError = exception;
            }
        }

        execution.setVariable(
                execution.getCurrentActivityId() + "_httpError",
                lastError == null ? "未知错误" : lastError.getMessage());
        if ("continue".equalsIgnoreCase(errorHandling)
                || "ignore".equalsIgnoreCase(errorHandling)) {
            return;
        }
        throw lastError;
    }

    private HttpResponse<String> executeRequest(
            JsonNode config,
            DelegateExecution execution,
            String url,
            String contentType,
            int timeout) throws Exception {
        String method = config.path("method").asText("POST").toUpperCase();
        String body = resolveTemplate(config.path("body").asText(""), execution);
        HttpRequest.BodyPublisher publisher = "GET".equals(method) || "DELETE".equals(method)
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeout))
                .method(method, publisher)
                .header("Content-Type", contentType)
                .header("X-Workflow-Instance-Id", execution.getProcessInstanceId())
                .header("X-Workflow-Activity-Id", execution.getCurrentActivityId());
        readObject(config.path("headers").asText("")).forEach((name, value) ->
                builder.header(name, resolveTemplate(String.valueOf(value), execution)));
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .build()
                .send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String appendQueryParameters(
            String url,
            String queryDocument,
            DelegateExecution execution) throws Exception {
        Map<String, Object> parameters = readObject(queryDocument);
        if (parameters.isEmpty()) {
            return url;
        }
        List<String> values = new ArrayList<>();
        parameters.forEach((name, value) -> values.add(
                URLEncoder.encode(name, StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(
                                resolveTemplate(String.valueOf(value), execution),
                                StandardCharsets.UTF_8)));
        return url + (url.contains("?") ? "&" : "?") + String.join("&", values);
    }

    private void mapResult(
            String mappingDocument,
            String responseBody,
            DelegateExecution execution) throws Exception {
        Map<String, Object> mappings = readObject(mappingDocument);
        if (mappings.isEmpty() || !StringUtils.hasText(responseBody)) {
            return;
        }
        JsonNode response = objectMapper.readTree(responseBody);
        mappings.forEach((path, variable) ->
                execution.setVariable(String.valueOf(variable), jsonPath(response, path)));
    }

    private Object jsonPath(JsonNode root, String path) {
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            current = current.path(segment);
        }
        return current.isMissingNode() || current.isNull()
                ? null
                : objectMapper.convertValue(current, Object.class);
    }

    private Map<String, Object> readObject(String document) throws Exception {
        if (!StringUtils.hasText(document)) {
            return Map.of();
        }
        JsonNode node = objectMapper.readTree(document);
        if (!node.isObject()) {
            throw new IllegalArgumentException("配置必须是 JSON 对象");
        }
        return objectMapper.convertValue(
                node,
                objectMapper.getTypeFactory().constructMapType(
                        LinkedHashMap.class,
                        String.class,
                        Object.class));
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

    private String readProperty(BaseElement element, String propertyName) {
        if (element == null || element.getExtensionElements() == null) {
            return null;
        }
        for (List<ExtensionElement> elements : element.getExtensionElements().values()) {
            String value = readProperty(elements, propertyName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String readProperty(List<ExtensionElement> elements, String propertyName) {
        for (ExtensionElement element : elements) {
            if ("property".equalsIgnoreCase(element.getName())
                    && propertyName.equals(attribute(element, "name"))) {
                return attribute(element, "value");
            }
            if (element.getChildElements() != null) {
                for (List<ExtensionElement> children : element.getChildElements().values()) {
                    String value = readProperty(children, propertyName);
                    if (value != null) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private String attribute(ExtensionElement element, String name) {
        for (List<ExtensionAttribute> attributes : element.getAttributes().values()) {
            for (ExtensionAttribute attribute : attributes) {
                if (name.equals(attribute.getName())) {
                    return attribute.getValue();
                }
            }
        }
        return null;
    }
}
