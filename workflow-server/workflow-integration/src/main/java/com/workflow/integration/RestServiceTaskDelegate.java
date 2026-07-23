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

/**
 * REST 服务任务委托。
 *
 * <p>作为 Flowable {@link JavaDelegate} 实现，从 BPMN 扩展属性中读取
 * REST 配置（url、method、headers、body、queryParams、resultMapping 等），
 * 通过 JDK {@link HttpClient} 发起 HTTP 调用，并将响应映射回流程变量。</p>
 *
 * <p>支持变量模板解析（${var}）、重试、错误处理策略（throw/continue/ignore）
 * 以及响应结果字段映射。</p>
 */
@Component("restServiceTaskDelegate")
@RequiredArgsConstructor
public class RestServiceTaskDelegate implements JavaDelegate {

    /** 变量模板正则，匹配 ${variable} 形式的占位符 */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /** JSON 序列化器 */
    private final ObjectMapper objectMapper;

    /**
     * Flowable 调用入口：执行已配置的 REST 任务。
     *
     * @param execution 流程执行上下文
     * @throws IllegalStateException 当底层调用抛出受检异常时包装抛出
     */
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

    /**
     * 读取并执行 restConfig 配置的 REST 调用，支持重试与错误处理策略。
     *
     * @param execution 流程执行上下文
     * @throws Exception 当 HTTP 调用失败且错误处理策略为 throw 时抛出
     */
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
        // 按 retryCount 重试调用，成功则立即返回
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

        // 全部重试失败后记录错误变量，并按策略决定是否抛出
        execution.setVariable(
                execution.getCurrentActivityId() + "_httpError",
                lastError == null ? "未知错误" : lastError.getMessage());
        if ("continue".equalsIgnoreCase(errorHandling)
                || "ignore".equalsIgnoreCase(errorHandling)) {
            return;
        }
        throw lastError;
    }

    /**
     * 构建并发送 HTTP 请求。
     *
     * @param config      REST 配置节点
     * @param execution   流程执行上下文
     * @param url         已拼接查询参数的目标 URL
     * @param contentType 请求体内容类型
     * @param timeout     超时秒数
     * @return HTTP 响应
     * @throws Exception 当请求构建或发送失败时抛出
     */
    private HttpResponse<String> executeRequest(
            JsonNode config,
            DelegateExecution execution,
            String url,
            String contentType,
            int timeout) throws Exception {
        String method = config.path("method").asText("POST").toUpperCase();
        String body = resolveTemplate(config.path("body").asText(""), execution);
        // GET/DELETE 不发送请求体
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

    /**
     * 将查询参数 JSON 解析后追加到 URL 上。
     *
     * @param url           原始 URL
     * @param queryDocument 查询参数 JSON 文档
     * @param execution     流程执行上下文，用于解析变量模板
     * @return 拼接查询参数后的 URL
     * @throws Exception 当查询参数文档非法时抛出
     */
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

    /**
     * 根据结果映射配置将响应体字段写入流程变量。
     *
     * @param mappingDocument 结果映射 JSON 文档，key 为字段路径，value 为流程变量名
     * @param responseBody   响应体字符串
     * @param execution      流程执行上下文
     * @throws Exception 当响应体非法时抛出
     */
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

    /**
     * 按点分路径从 JSON 树中取值。
     *
     * @param root 根节点
     * @param path 点分路径，如 data.id
     * @return 取到的值；缺失或为 null 时返回 null
     */
    private Object jsonPath(JsonNode root, String path) {
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            current = current.path(segment);
        }
        return current.isMissingNode() || current.isNull()
                ? null
                : objectMapper.convertValue(current, Object.class);
    }

    /**
     * 将 JSON 文档解析为有序 Map。
     *
     * @param document JSON 文档字符串
     * @return 解析得到的有序 Map，空文档返回空 Map
     * @throws Exception 当文档非对象时抛出
     */
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

    /**
     * 解析模板中的 ${variable} 占位符为流程变量值。
     *
     * @param template  模板字符串
     * @param execution 流程执行上下文
     * @return 替换占位符后的字符串
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
     * 从 BPMN 元素的扩展属性中读取指定属性值。
     *
     * @param element      BPMN 元素
     * @param propertyName 属性名
     * @return 属性值，未找到返回 null
     */
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

    /**
     * 递归在扩展元素列表中查找指定属性值。
     *
     * @param elements     扩展元素列表
     * @param propertyName 属性名
     * @return 属性值，未找到返回 null
     */
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

    /**
     * 从扩展元素的属性集合中读取指定名称的属性值。
     *
     * @param element 扩展元素
     * @param name    属性名
     * @return 属性值，未找到返回 null
     */
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
