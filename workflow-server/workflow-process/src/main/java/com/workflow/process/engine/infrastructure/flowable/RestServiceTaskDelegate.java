package com.workflow.process.engine.infrastructure.flowable;

import com.workflow.http.api.HttpTransportUnavailableException;

import com.workflow.http.api.HttpTransportRequest;
import com.workflow.http.api.HttpTransportResult;
import com.workflow.http.config.WorkflowHttpProperties;
import com.workflow.http.policy.RestEndpointPolicy;
import com.workflow.http.transport.PinnedHttpTransport;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.core.concurrent.ExecutionDeadline;
import jakarta.annotation.PreDestroy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * REST 服务任务委托。
 *
 * <p>作为 Flowable {@link JavaDelegate} 实现，从 BPMN 扩展属性中读取
 * REST 配置（url、method、headers、body、queryParams、resultMapping 等），
 * 通过受控 HTTP 传输发起调用，并将声明的响应字段映射回流程变量。</p>
 *
 * <p>支持变量模板解析（${var}）、重试、错误处理策略（throw/continue/ignore）
 * 以及响应结果字段映射。</p>
 */
@Component("restServiceTaskDelegate")
public class RestServiceTaskDelegate implements JavaDelegate, AutoCloseable {

    /** 变量模板正则，匹配 ${variable} 形式的占位符 */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /** JSON 序列化器 */
    private final ObjectMapper objectMapper;
    private final WorkflowHttpProperties properties;
    private final PinnedHttpTransport transport;
    private boolean ownsTransport;

    /**
     * 初始化{@code rest}服务任务委托，保存构造参数供后续方法使用。
     *
     * @param objectMapper 对象映射器，保存在对象中供后续校验、查询或展示
     * @param endpointPolicy 接口端点策略，保存在对象中供后续校验、查询或展示
     * @param properties 属性集合，保存在对象中供后续校验、查询或展示
     */
    public RestServiceTaskDelegate(
            ObjectMapper objectMapper,
            RestEndpointPolicy endpointPolicy,
            WorkflowHttpProperties properties) {
        this(
                objectMapper,
                endpointPolicy,
                properties,
                new PinnedHttpTransport(
                        endpointPolicy,
                        properties));
        this.ownsTransport = true;
    }

    /**
     * 初始化{@code rest}服务任务委托，保存构造参数供后续方法使用。
     *
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     * @param endpointPolicy 接口端点策略，保存在对象中供后续校验、查询或展示
     * @param properties 属性集合依赖，保存到当前对象供后续业务方法调用
     * @param transport 传输依赖，保存到当前对象供后续业务方法调用
     */
    @Autowired
    RestServiceTaskDelegate(
            ObjectMapper objectMapper,
            RestEndpointPolicy endpointPolicy,
            WorkflowHttpProperties properties,
            PinnedHttpTransport transport) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.transport = transport;
    }

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
            throw new IllegalStateException("REST 服务任务执行失败", exception);
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
        String configuredUrl = config.path("url").asText("");
        String url = appendQueryParameters(
                resolveTemplate(configuredUrl, execution),
                config.path("queryParams").asText(""),
                execution);
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("REST 服务任务未配置请求URL");
        }
        URI resolvedUri = URI.create(url);
        validateStaticAuthority(configuredUrl, resolvedUri);
        String contentType = config.path("contentType")
                .asText("application/json");
        if (!"application/json".equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException(
                    "REST 服务任务仅支持 application/json");
        }

        int timeout = bounded(
                config.path("timeout").asInt(30),
                1,
                bounded(
                        properties
                                .getMaxRequestTimeoutSeconds(),
                        1,
                        120));
        int retryCount = Math.max(0, Math.min(5, config.path("retryCount").asInt(0)));
        String errorHandling = config.path("errorHandling").asText("throw");
        Exception lastError = null;
        // timeout 是整个节点的网络预算，重试、退避和 DNS 都不能重新开始计时。
        try (var deadline = ExecutionDeadline.afterMillis(timeout * 1000L); var scope = deadline.attach()) {
            for (int attempt = 0; attempt <= retryCount; attempt++) {
                try {
                    deadline.check();
                    HttpCallResult response = executeRequest(config, execution, url, contentType, timeout);
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new HttpStatusException(response.statusCode());
                    }
                    deadline.check();
                    mapResult(config.path("resultMapping").asText(""), response.body(), execution);
                    execution.setVariable(execution.getCurrentActivityId() + "_httpStatus", response.statusCode());
                    return;
                } catch (Exception exception) {
                    lastError = exception;
                    if (attempt >= retryCount || !isRetryable(exception)) break;
                    try {
                        long backoff = Math.min(1000L, 100L << attempt);
                        if (deadline.remainingMillis() <= backoff) break;
                        Thread.sleep(backoff);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        lastError = interrupted;
                        break;
                    } catch (ExecutionDeadline.ExceededException expired) {
                        lastError = expired;
                        break;
                    }
                }
            }
        }

        // 全部重试失败后记录错误变量，并按策略决定是否抛出
        execution.setVariable(
                execution.getCurrentActivityId() + "_httpError",
                "REST_SERVICE_TASK_FAILED");
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
    private HttpCallResult executeRequest(
            JsonNode config,
            DelegateExecution execution,
            String url,
            String contentType,
            int timeout) throws Exception {
        String method = config.path("method")
                .asText("POST")
                .toUpperCase(Locale.ROOT);
        if (!Set.of("GET", "POST", "PUT", "PATCH", "DELETE")
                .contains(method)) {
            throw new IllegalArgumentException(
                    "REST 服务任务请求方法无效");
        }
        String body = resolveTemplate(config.path("body").asText(""), execution);
        URI uri = URI.create(url);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", contentType);
        headers.put(
                "X-Workflow-Instance-Id",
                safeHeaderValue(execution.getProcessInstanceId()));
        headers.put(
                "X-Workflow-Activity-Id",
                safeHeaderValue(execution.getCurrentActivityId()));
        headers.put(
                "Idempotency-Key",
                execution.getProcessInstanceId()
                        + ":" + execution.getCurrentActivityId());
        readObject(config.path("headers").asText("")).forEach(
                (name, value) -> {
                    validateLegacyHeader(name);
                    headers.put(
                            name,
                            safeHeaderValue(resolveTemplate(
                                    String.valueOf(value),
                                    execution)));
                });
        HttpTransportResult response = transport.execute(
                new HttpTransportRequest(
                        method,
                        uri,
                        Collections.unmodifiableMap(headers),
                        Set.of("GET", "DELETE").contains(method)
                                ? null
                                : body,
                        timeout * 1000,
                        Set.copyOf(properties.getAllowedHosts()),
                        properties.getMaxResponseBytes(),
                        false),
                properties.isAllowPrivateAddresses());
        return new HttpCallResult(
                response.statusCode(),
                response.body());
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
        mappings.forEach((path, variable) -> {
            String target = String.valueOf(variable);
            if (!path.matches("[A-Za-z][A-Za-z0-9_.-]{0,255}")
                    || !target.matches(
                            "[A-Za-z][A-Za-z0-9_.-]{0,127}")) {
                throw new IllegalArgumentException(
                        "REST 服务任务响应映射无效");
            }
            execution.setVariable(target, jsonPath(response, path));
        });
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
     * 校验{@code static}{@code authority}；不满足约束时阻止后续处理。
     *
     * @param configuredUrl 已配置URL，供本方法校验{@code static}{@code authority}时使用
     * @param resolvedUri 已解析{@code uri}，供本方法校验{@code static}{@code authority}时使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateStaticAuthority(
            String configuredUrl,
            URI resolvedUri) {
        int schemeEnd = configuredUrl.indexOf("://");
        if (schemeEnd <= 0) {
            throw new IllegalArgumentException(
                    "REST 服务任务 URL 必须包含固定协议和主机");
        }
        int authorityEnd = configuredUrl.length();
        for (char separator : new char[]{'/', '?', '#'}) {
            int found = configuredUrl.indexOf(
                    separator,
                    schemeEnd + 3);
            if (found >= 0) {
                authorityEnd = Math.min(authorityEnd, found);
            }
        }
        String authority = configuredUrl.substring(0, authorityEnd);
        if (authority.contains("${")) {
            throw new IllegalArgumentException(
                    "REST 服务任务禁止动态修改目标主机");
        }
        URI configuredAuthority = URI.create(authority + "/");
        if (!java.util.Objects.equals(
                        configuredAuthority.getScheme(),
                        resolvedUri.getScheme())
                || !java.util.Objects.equals(
                        configuredAuthority.getHost(),
                        resolvedUri.getHost())
                || configuredAuthority.getPort()
                != resolvedUri.getPort()) {
            throw new IllegalArgumentException(
                    "REST 服务任务禁止动态修改目标主机");
        }
    }

    /**
     * 校验旧版{@code header}；不满足约束时阻止后续处理。
     *
     * @param name 名称，后续用于校验旧版{@code header}时匹配或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateLegacyHeader(String name) {
        String normalized = name == null
                ? ""
                : name.toLowerCase(Locale.ROOT);
        if (!normalized.matches(
                        "[a-z0-9!#$%&'*+.^_`|~-]{1,64}")
                || Set.of(
                        "authorization",
                        "proxy-authorization",
                        "cookie",
                        "host",
                        "content-length",
                        "transfer-encoding",
                        "connection",
                        "upgrade").contains(normalized)
                || normalized.matches(
                        ".*(secret|token|api[-_]?key|credential|password).*")) {
            throw new IllegalArgumentException(
                    "REST 服务任务 Header 不允许携带凭据");
        }
    }

    /**
     * 生成安全{@code header}值文本，供后续匹配或展示。
     *
     * @param value 待处理安全{@code header}值的原始输入，结果供调用方继续使用
     * @return 处理后的安全{@code header}值文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String safeHeaderValue(String value) {
        if (value == null
                || value.length() > 8192
                || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(
                    "REST 服务任务 Header 值无效");
        }
        return value;
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

    /**
     * 处理{@code bounded}，并将结果传给后续步骤。
     *
     * @param value 待处理{@code bounded}的原始输入，结果供调用方继续使用
     * @param minimum {@code minimum}，作为 {@code Math.max} 的输入影响后续处理
     * @param maximum {@code maximum}，作为 {@code Math.max} 的输入影响后续处理
     * @return 处理后的{@code bounded}结果，供调用方继续处理
     */
    private static int bounded(
            int value,
            int minimum,
            int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /**
     * 判断是否可重试；判断结果决定调用方的后续分支。
     *
     * @param exception 异常，供本方法判断是否可重试时使用
     * @return 可重试条件成立时为 true，否则为 false
     */
    private boolean isRetryable(Exception exception) {
        if (Thread.currentThread().isInterrupted()
                || exception instanceof ExecutionDeadline.ExceededException
                || exception instanceof HttpTransportUnavailableException
                || exception instanceof IllegalArgumentException
                || exception instanceof InterruptedException) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
        return !(exception instanceof HttpStatusException status)
                || status.statusCode == 429
                || status.statusCode >= 500;
    }

    /** 手工构造的实例拥有传输资源；Spring 注入的共享传输由其自身生命周期关闭。 */
    @PreDestroy
    @Override public void close() {
        if (ownsTransport) transport.close();
    }

    /**
     * 封装HTTP{@code call}的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param statusCode 状态编码，后续用于处理HTTP{@code call}结果时定位或关联目标
     * @param body 请求体，后续用于处理HTTP{@code call}结果并传递处理结果
     */
    private record HttpCallResult(int statusCode, String body) {
    }

    /**
     * 表示HTTP状态处理失败；调用方可据此区分错误并终止后续操作。
     */
    private static final class HttpStatusException
            extends IllegalStateException {

        private final int statusCode;

        /**
         * 初始化HTTP状态异常，保存构造参数供后续方法使用。
         *
         * @param statusCode 状态编码依赖，保存到当前对象供后续业务方法调用
         */
        private HttpStatusException(int statusCode) {
            super("HTTP " + statusCode);
            this.statusCode = statusCode;
        }
    }
}
