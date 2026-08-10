package com.workflow.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditResult;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.contracts.integration.IntegrationConnector;
import com.workflow.contracts.integration.IntegrationRequest;
import com.workflow.contracts.integration.IntegrationResult;
import com.workflow.contracts.integration.IntegrationRuntimeContext;
import com.workflow.contracts.integration.IntegrationSecretResolver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "workflow.integration.connector.http.enabled",
        havingValue = "true")
public class HttpIntegrationConnector implements IntegrationConnector {

    private static final String CODE = "http-json";

    private final HttpConnectorConfigurationProvider configurationProvider;
    private final IntegrationSecretResolver secretResolver;
    private final PinnedHttpTransport transport;
    private final ObjectMapper objectMapper;
    private final SystemAuditPort auditPort;
    private final MeterRegistry meterRegistry;

    public HttpIntegrationConnector(
            HttpConnectorConfigurationProvider configurationProvider,
            IntegrationSecretResolver secretResolver,
            PinnedHttpTransport transport,
            ObjectMapper objectMapper,
            SystemAuditPort auditPort,
            MeterRegistry meterRegistry) {
        this.configurationProvider = configurationProvider;
        this.secretResolver = secretResolver;
        this.transport = transport;
        this.objectMapper = objectMapper;
        this.auditPort = auditPort;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public IntegrationResult execute(IntegrationRequest request) {
        Timer.Sample timer = Timer.start(meterRegistry);
        String operationLabel = safeOperationLabel(
                request == null ? null : request.getOperation());
        IntegrationResult result;
        HttpConnectorConfiguration configuration = null;
        boolean auditGateFailed = false;
        try {
            requireRequest(request);
            configuration = configurationProvider.findActive(
                    request.getConnectorConfigId());
            HttpConnectorConfiguration.Operation operation =
                    configuration.operations().get(
                            request.getOperation());
            if (operation == null) {
                throw new IllegalArgumentException(
                        "HTTP Connector 操作不存在");
            }
            try {
                auditAttempt(request, configuration);
                result = invoke(configuration, operation, request);
            } catch (ConnectorAuditGateException exception) {
                auditGateFailed = true;
                result = failure(
                        "CONNECTOR_AUDIT_FAILED",
                        "HTTP Connector 审计写入失败");
            }
        } catch (IllegalArgumentException exception) {
            result = failure(
                    "CONNECTOR_CONFIGURATION_INVALID",
                    "HTTP Connector 配置不可用");
        } catch (IOException exception) {
            result = failure(
                    "CONNECTOR_REMOTE_UNAVAILABLE",
                    "外部系统暂时不可用");
        } catch (RuntimeException exception) {
            result = failure(
                    "CONNECTOR_EXECUTION_FAILED",
                    "HTTP Connector 执行失败");
        }
        String outcome = result.isSuccess()
                ? "success"
                : result.getCode().toLowerCase(Locale.ROOT);
        timer.stop(Timer.builder("flow.connector.call.duration")
                .tag("connector", CODE)
                .tag("operation", operationLabel)
                .tag("status", outcome)
                .register(meterRegistry));
        meterRegistry.counter(
                "flow.connector.calls",
                "connector", CODE,
                "operation", operationLabel,
                "status", outcome).increment();
        if (!auditGateFailed) {
            try {
                auditOutcome(request, configuration, result);
            } catch (RuntimeException auditFailure) {
                meterRegistry.counter(
                        "flow.connector.audit.failures",
                        "connector", CODE).increment();
            }
        }
        return result;
    }

    private IntegrationResult invoke(
            HttpConnectorConfiguration configuration,
            HttpConnectorConfiguration.Operation operation,
            IntegrationRequest request) throws IOException {
        URI uri = buildUri(
                configuration.baseUri(),
                operation.path(),
                operation.queryMappings(),
                request);
        Map<String, String> headers = buildHeaders(
                operation,
                request);
        headers.put("Idempotency-Key", request.getIdempotencyKey());
        headers.put("X-Flow-Trace-Id", traceId());
        if (request.getRuntimeContext() != null
                && value(request.getRuntimeContext().serviceId()) != null) {
            headers.put(
                    "X-Flow-Service-Id",
                    request.getRuntimeContext().serviceId());
        }
        String body = operation.bodyMappings().isEmpty()
                ? null
                : buildBody(operation.bodyMappings(), request);
        HttpTransportResult response = null;
        IOException lastIoFailure = null;
        for (int attempt = 1; attempt <= operation.maxAttempts(); attempt++) {
            try {
                response = transport.execute(new HttpTransportRequest(
                        operation.method(),
                        uri,
                        Map.copyOf(headers),
                        body,
                        operation.timeoutMillis(),
                        configuration.allowedHosts(),
                        1024 * 1024,
                        false));
                if (!retryable(response.statusCode())
                        || attempt == operation.maxAttempts()) {
                    break;
                }
            } catch (IOException exception) {
                lastIoFailure = exception;
                if (attempt == operation.maxAttempts()) {
                    throw exception;
                }
            }
            boundedBackoff(attempt, response);
        }
        if (response == null) {
            throw lastIoFailure == null
                    ? new IOException("no response")
                    : lastIoFailure;
        }
        if (!operation.acceptedStatuses().contains(
                response.statusCode())) {
            return failure(
                    "CONNECTOR_REMOTE_REJECTED",
                    "外部系统拒绝请求");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("httpStatus", response.statusCode());
        if (!operation.responseMappings().isEmpty()) {
            JsonNode document;
            try {
                document = objectMapper.readTree(response.body());
            } catch (Exception exception) {
                return failure(
                        "CONNECTOR_RESPONSE_INVALID",
                        "外部系统响应格式无效");
            }
            operation.responseMappings().forEach((target, pointer) -> {
                JsonNode selected = document.at(pointer);
                data.put(
                        target,
                        selected.isMissingNode() || selected.isNull()
                                ? null
                                : objectMapper.convertValue(
                                        selected,
                                        Object.class));
            });
        }
        return IntegrationResult.builder()
                .success(true)
                .code("SUCCESS")
                .message("HTTP Connector 调用成功")
                .data(Collections.unmodifiableMap(data))
                .build();
    }

    private Map<String, String> buildHeaders(
            HttpConnectorConfiguration.Operation operation,
            IntegrationRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        operation.headerMappings().forEach((name, source) ->
                headers.put(name, stringSource(source, request)));
        var authentication = operation.authentication();
        switch (authentication.type()) {
            case NONE -> {
            }
            case BASIC -> {
                String username = secretResolver.resolve(
                        authentication.usernameSecretRef());
                String password = secretResolver.resolve(
                        authentication.secretRef());
                headers.put(
                        "Authorization",
                        "Basic " + Base64.getEncoder().encodeToString(
                                (username + ":" + password)
                                        .getBytes(StandardCharsets.UTF_8)));
            }
            case BEARER -> headers.put(
                    "Authorization",
                    "Bearer " + secretResolver.resolve(
                            authentication.secretRef()));
            case HEADER -> headers.put(
                    authentication.headerName(),
                    secretResolver.resolve(authentication.secretRef()));
        }
        return headers;
    }

    private URI buildUri(
            URI base,
            String operationPath,
            Map<String, String> queryMappings,
            IntegrationRequest request) {
        String basePath = base.getRawPath();
        String path = (basePath == null || "/".equals(basePath)
                ? ""
                : stripTrailingSlash(basePath))
                + operationPath;
        StringBuilder query = new StringBuilder();
        queryMappings.forEach((name, source) -> {
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(encode(name))
                    .append('=')
                    .append(encode(stringSource(source, request)));
        });
        try {
            return new URI(
                    base.getScheme(),
                    null,
                    base.getHost(),
                    base.getPort(),
                    path,
                    query.isEmpty() ? null : query.toString(),
                    null);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(
                    "HTTP Connector 目标地址无效");
        }
    }

    private String buildBody(
            Map<String, String> mappings,
            IntegrationRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        mappings.forEach((pointer, source) ->
                writePointer(root, pointer, source(source, request)));
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "HTTP Connector 请求体无法序列化");
        }
    }

    private void writePointer(
            ObjectNode root,
            String pointer,
            Object value) {
        String[] segments = pointer.substring(1).split("/", -1);
        ObjectNode current = root;
        for (int index = 0; index < segments.length; index++) {
            String segment = segments[index]
                    .replace("~1", "/")
                    .replace("~0", "~");
            if (segment.isBlank()) {
                throw new IllegalArgumentException(
                        "HTTP Connector 请求体映射无效");
            }
            if (index == segments.length - 1) {
                current.set(
                        segment,
                        objectMapper.valueToTree(value));
            } else {
                JsonNode child = current.get(segment);
                if (child == null) {
                    child = current.putObject(segment);
                }
                if (!(child instanceof ObjectNode objectChild)) {
                    throw new IllegalArgumentException(
                            "HTTP Connector 请求体映射冲突");
                }
                current = objectChild;
            }
        }
    }

    private String stringSource(
            String source,
            IntegrationRequest request) {
        Object value = source(source, request);
        return value == null ? "" : String.valueOf(value);
    }

    private Object source(
            String source,
            IntegrationRequest request) {
        if (source.startsWith("$input.")) {
            return mapPath(
                    request.getParameters(),
                    source.substring("$input.".length()));
        }
        if (source.startsWith("$context.")) {
            return contextValue(
                    request.getRuntimeContext(),
                    source.substring("$context.".length()));
        }
        throw new IllegalArgumentException(
                "HTTP Connector 请求映射无效");
    }

    private Object mapPath(Map<String, Object> values, String path) {
        Object current = values;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private Object contextValue(
            IntegrationRuntimeContext context,
            String field) {
        if (context == null) {
            return null;
        }
        return switch (field) {
            case "serviceId" -> context.serviceId();
            case "operationCode" -> context.operationCode();
            case "usage" -> context.usage();
            case "configType" -> context.configType();
            case "configId" -> context.configId();
            case "targetType" -> context.targetType();
            case "targetKey" -> context.targetKey();
            case "releaseId" -> context.releaseId();
            case "releaseVersion" -> context.releaseVersion();
            case "entityId" -> context.entityId();
            case "entityCode" -> context.entityCode();
            case "listKey" -> context.listKey();
            case "userId" -> context.userId();
            case "tenantId" -> context.tenantId();
            case "organizationId" -> context.organizationId();
            case "departmentId" -> context.departmentId();
            default -> throw new IllegalArgumentException(
                    "HTTP Connector 上下文字段未授权");
        };
    }

    private void requireRequest(IntegrationRequest request) {
        if (request == null
                || value(request.getConnectorConfigId()) == null
                || value(request.getOperation()) == null
                || value(request.getIdempotencyKey()) == null
                || request.getIdempotencyKey().length() > 128
                || request.getParameters() == null) {
            throw new IllegalArgumentException(
                    "HTTP Connector 请求缺少必要字段");
        }
    }

    private void boundedBackoff(
            int attempt,
            HttpTransportResult response) throws IOException {
        long delay = Math.min(1000L, 100L << Math.min(attempt - 1, 3));
        if (response != null && response.retryAfter() != null) {
            try {
                delay = Math.min(
                        2000L,
                        Math.max(delay,
                                Long.parseLong(response.retryAfter()) * 1000L));
            } catch (NumberFormatException ignored) {
                // HTTP-date Retry-After is intentionally not trusted here.
            }
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException(
                    "HTTP Connector 重试被中断",
                    exception);
        }
    }

    private boolean retryable(int status) {
        return status == 429
                || status == 502
                || status == 503
                || status == 504;
    }

    private IntegrationResult failure(String code, String message) {
        return IntegrationResult.builder()
                .success(false)
                .code(code)
                .message(message)
                .data(Map.of())
                .build();
    }

    private void auditAttempt(
            IntegrationRequest request,
            HttpConnectorConfiguration configuration) {
        try {
            audit(
                    request,
                    configuration,
                    "发起 HTTP Connector",
                    AuditResult.SUCCESS,
                    "ATTEMPT");
        } catch (RuntimeException exception) {
            throw new ConnectorAuditGateException(exception);
        }
    }

    private void auditOutcome(
            IntegrationRequest request,
            HttpConnectorConfiguration configuration,
            IntegrationResult result) {
        audit(
                request,
                configuration,
                "完成 HTTP Connector",
                result.isSuccess()
                        ? AuditResult.SUCCESS
                        : AuditResult.FAILURE,
                result.getCode());
    }

    private void audit(
            IntegrationRequest request,
            HttpConnectorConfiguration configuration,
            String operationName,
            AuditResult auditResult,
            String summary) {
        String userId = request != null
                && request.getRuntimeContext() != null
                ? value(request.getRuntimeContext().userId())
                : null;
        auditPort.record(SystemAuditEvent.builder()
                .module(AuditModule.INTEGRATION)
                .action(AuditAction.OTHER)
                .operationName(operationName)
                .riskLevel(AuditRiskLevel.MEDIUM)
                .result(auditResult)
                .required(true)
                .operatorId(userId == null
                        ? "system:integration"
                        : userId)
                .targetType("INTEGRATION_CONNECTOR_CONFIG")
                .targetId(configuration == null
                        ? "unresolved"
                        : configuration.id())
                .targetName(request == null
                        ? "unknown"
                        : safeOperationLabel(request.getOperation()))
                .summary(summary)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .build());
    }

    private static final class ConnectorAuditGateException
            extends RuntimeException {

        private ConnectorAuditGateException(Throwable cause) {
            super(cause);
        }
    }

    private String traceId() {
        String traceId = value(MDC.get("traceId"));
        return traceId == null
                ? UUID.randomUUID().toString()
                : traceId;
    }

    private String safeOperationLabel(String value) {
        return value != null
                && value.matches("[A-Za-z][A-Za-z0-9._-]{0,63}")
                ? value
                : "invalid";
    }

    private String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private String value(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
