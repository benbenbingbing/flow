package com.workflow.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class HttpConnectorConfigurationCodec {

    private static final Set<String> ROOT_FIELDS =
            Set.of("baseUrl", "operations");
    private static final Set<String> OPERATION_FIELDS = Set.of(
            "method",
            "path",
            "query",
            "headers",
            "body",
            "response",
            "acceptedStatuses",
            "authentication",
            "timeoutMs",
            "maxAttempts");
    private static final Set<String> AUTH_FIELDS = Set.of(
            "type",
            "headerName",
            "usernameSecretRef",
            "secretRef");
    private static final Set<String> METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> FORBIDDEN_HEADERS = Set.of(
            "authorization",
            "proxy-authorization",
            "cookie",
            "host",
            "content-length",
            "transfer-encoding",
            "connection",
            "upgrade");
    private static final Pattern IDENTIFIER =
            Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,63}");
    private static final Pattern APPLICATION_ID =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private static final Pattern INPUT_SOURCE = Pattern.compile(
            "\\$input\\.[A-Za-z][A-Za-z0-9_-]{0,63}"
                    + "(?:\\.[A-Za-z][A-Za-z0-9_-]{0,63}){0,7}");
    private static final Set<String> CONTEXT_SOURCES = Set.of(
            "$context.serviceId",
            "$context.usage",
            "$context.configType",
            "$context.configId",
            "$context.releaseId",
            "$context.releaseVersion",
            "$context.entityId",
            "$context.entityCode",
            "$context.listKey",
            "$context.userId",
            "$context.tenantId",
            "$context.organizationId",
            "$context.departmentId");
    private static final int MAX_CONFIGURATION_BYTES = 256 * 1024;
    private static final int MAX_ALLOWED_HOSTS_BYTES = 16 * 1024;
    private static final Pattern SECRET_REFERENCE = Pattern.compile(
            "secret://integration/([A-Za-z0-9][A-Za-z0-9_-]{0,63})/"
                    + "([A-Za-z][A-Za-z0-9._-]{0,63})");

    private final ObjectMapper objectMapper;

    public HttpConnectorConfigurationCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public HttpConnectorConfiguration read(
            String id,
            String applicationId,
            String configurationDocument,
            String allowedHostsDocument) {
        if (id == null || id.isBlank()
                || !APPLICATION_ID.matcher(applicationId).matches()) {
            throw invalid("连接器配置归属无效");
        }
        requireDocumentSize(
                configurationDocument,
                MAX_CONFIGURATION_BYTES,
                "连接器配置");
        requireDocumentSize(
                allowedHostsDocument,
                MAX_ALLOWED_HOSTS_BYTES,
                "连接器主机白名单");
        JsonNode root = readObject(configurationDocument, "连接器配置");
        rejectUnknown(root, ROOT_FIELDS, "连接器配置");
        Set<String> allowedHosts = readAllowedHosts(
                allowedHostsDocument);
        URI baseUri = readBaseUri(
                requiredText(root, "baseUrl"),
                allowedHosts);
        JsonNode operationsNode = root.path("operations");
        if (!operationsNode.isObject()
                || operationsNode.isEmpty()
                || operationsNode.size() > 50) {
            throw invalid("连接器必须配置 1 到 50 个操作");
        }
        Map<String, HttpConnectorConfiguration.Operation> operations =
                new LinkedHashMap<>();
        operationsNode.fields().forEachRemaining(entry -> {
            if (!IDENTIFIER.matcher(entry.getKey()).matches()) {
                throw invalid("连接器操作编码无效");
            }
            operations.put(
                    entry.getKey(),
                    readOperation(applicationId, entry.getValue()));
        });
        return new HttpConnectorConfiguration(
                id,
                applicationId,
                baseUri,
                Set.copyOf(allowedHosts),
                Map.copyOf(operations));
    }

    private HttpConnectorConfiguration.Operation readOperation(
            String applicationId,
            JsonNode node) {
        if (!node.isObject()) {
            throw invalid("连接器操作必须是 JSON 对象");
        }
        rejectUnknown(node, OPERATION_FIELDS, "连接器操作");
        String method = requiredText(node, "method")
                .toUpperCase(Locale.ROOT);
        if (!METHODS.contains(method)) {
            throw invalid("连接器请求方法不受支持");
        }
        String path = requiredText(node, "path");
        validatePath(path);
        Map<String, String> query =
                readMappings(node.path("query"), false, false);
        Map<String, String> headers =
                readMappings(node.path("headers"), true, false);
        Map<String, String> body =
                readMappings(node.path("body"), false, true);
        validateBodyPointerConflicts(body.keySet());
        Map<String, String> response =
                readResponseMappings(node.path("response"));
        Set<Integer> statuses =
                readStatuses(node.path("acceptedStatuses"));
        HttpConnectorConfiguration.Authentication authentication =
                readAuthentication(
                        applicationId,
                        node.path("authentication"));
        int timeout = integer(node, "timeoutMs", 5000, 100, 30000);
        int attempts = integer(node, "maxAttempts", 1, 1, 4);
        if (Set.of("GET", "DELETE").contains(method)
                && !body.isEmpty()) {
            throw invalid("GET 和 DELETE 操作不能配置请求体");
        }
        return new HttpConnectorConfiguration.Operation(
                method,
                path,
                query,
                headers,
                body,
                response,
                statuses,
                authentication,
                timeout,
                attempts);
    }

    private HttpConnectorConfiguration.Authentication readAuthentication(
            String applicationId,
            JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return new HttpConnectorConfiguration.Authentication(
                    HttpConnectorConfiguration.Authentication.Type.NONE,
                    null,
                    null,
                    null);
        }
        if (!node.isObject()) {
            throw invalid("认证配置必须是 JSON 对象");
        }
        rejectUnknown(node, AUTH_FIELDS, "认证配置");
        HttpConnectorConfiguration.Authentication.Type type;
        try {
            type = HttpConnectorConfiguration.Authentication.Type.valueOf(
                    requiredText(node, "type")
                            .toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw invalid("认证类型无效");
        }
        String headerName = optionalText(node, "headerName");
        String usernameRef = optionalText(node, "usernameSecretRef");
        String secretRef = optionalText(node, "secretRef");
        switch (type) {
            case NONE -> {
                if (headerName != null
                        || usernameRef != null
                        || secretRef != null) {
                    throw invalid("NONE 认证不能配置凭据");
                }
            }
            case BASIC -> {
                requireSecretReference(applicationId, usernameRef);
                requireSecretReference(applicationId, secretRef);
                if (headerName != null) {
                    throw invalid("BASIC 认证不能配置 Header 名称");
                }
            }
            case BEARER -> {
                requireSecretReference(applicationId, secretRef);
                if (headerName != null || usernameRef != null) {
                    throw invalid("BEARER 认证配置无效");
                }
            }
            case HEADER -> {
                validateHeaderName(headerName);
                requireSecretReference(applicationId, secretRef);
                if (usernameRef != null) {
                    throw invalid("HEADER 认证配置无效");
                }
            }
        }
        return new HttpConnectorConfiguration.Authentication(
                type,
                headerName,
                usernameRef,
                secretRef);
    }

    private Set<String> readAllowedHosts(String document) {
        JsonNode node;
        try {
            node = objectMapper.readTree(document);
        } catch (JsonProcessingException exception) {
            throw invalid("连接器主机白名单不是合法 JSON");
        }
        if (node == null || !node.isArray()
                || node.isEmpty() || node.size() > 100) {
            throw invalid("连接器必须配置 1 到 100 个主机");
        }
        Set<String> hosts = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw invalid("连接器主机白名单只能包含主机名");
            }
            String host = normalizeHost(item.textValue());
            if (!hosts.add(host)) {
                throw invalid("连接器主机白名单包含重复项");
            }
        }
        return hosts;
    }

    private URI readBaseUri(String value, Set<String> allowedHosts) {
        try {
            URI uri = new URI(value);
            String host = normalizeHost(uri.getHost());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || uri.getPort() == 0
                    || uri.getPort() > 65535
                    || !allowedHosts.contains(host)) {
                throw invalid("连接器基址必须是白名单内的 HTTPS 地址");
            }
            String path = uri.getRawPath();
            return new URI(
                    "https",
                    null,
                    host,
                    uri.getPort(),
                    path == null || path.isBlank() ? "/" : path,
                    null,
                    null);
        } catch (URISyntaxException | NullPointerException exception) {
            throw invalid("连接器基址格式无效");
        }
    }

    private Map<String, String> readMappings(
            JsonNode node,
            boolean headerNames,
            boolean jsonPointers) {
        if (node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        if (!node.isObject() || node.size() > 100) {
            throw invalid("连接器请求映射必须是 JSON 对象且不超过 100 项");
        }
        Map<String, String> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            String target = entry.getKey();
            if (headerNames) {
                validateHeaderName(target);
            } else if (jsonPointers) {
                validateJsonPointer(target, "请求体");
            } else if (!IDENTIFIER.matcher(target).matches()) {
                throw invalid("查询参数名称无效");
            }
            if (!entry.getValue().isTextual()
                    || !isAllowedSource(entry.getValue().textValue())) {
                throw invalid("请求映射只能引用 $input 或 $context 字段");
            }
            values.put(target, entry.getValue().textValue());
        });
        return Map.copyOf(values);
    }

    private Map<String, String> readResponseMappings(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        if (!node.isObject() || node.size() > 100) {
            throw invalid("响应映射必须是 JSON 对象且不超过 100 项");
        }
        Map<String, String> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            if (!IDENTIFIER.matcher(entry.getKey()).matches()
                    || !entry.getValue().isTextual()) {
                throw invalid("响应映射字段无效");
            }
            validateJsonPointer(
                    entry.getValue().textValue(),
                    "响应");
            values.put(entry.getKey(), entry.getValue().textValue());
        });
        return Map.copyOf(values);
    }

    private Set<Integer> readStatuses(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return Set.of(200, 201, 202, 204);
        }
        if (!node.isArray() || node.isEmpty() || node.size() > 20) {
            throw invalid("成功状态码必须是 1 到 20 项数组");
        }
        Set<Integer> values = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (!item.canConvertToInt()) {
                throw invalid("成功状态码无效");
            }
            int status = item.intValue();
            if (status < 200 || status > 299 || !values.add(status)) {
                throw invalid("成功状态码必须是互不重复的 2xx");
            }
        }
        return Set.copyOf(values);
    }

    private void requireSecretReference(
            String applicationId,
            String value) {
        if (value == null) {
            throw invalid("认证凭据必须使用 Secret 引用");
        }
        var matcher = SECRET_REFERENCE.matcher(value);
        if (!matcher.matches()
                || !applicationId.equals(matcher.group(1))) {
            throw invalid("认证凭据必须引用当前应用的 Secret");
        }
    }

    private void validatePath(String path) {
        if (path.length() > 2048
                || !path.startsWith("/")
                || path.startsWith("//")
                || path.contains("\\")
                || path.contains("?")
                || path.contains("#")
                || path.contains("${")) {
            throw invalid("连接器操作路径必须是固定绝对路径");
        }
        try {
            URI value = new URI(null, null, path, null);
            if (!path.equals(value.getRawPath())) {
                throw invalid("连接器操作路径必须使用合法 URI 编码");
            }
        } catch (URISyntaxException exception) {
            throw invalid("连接器操作路径无效");
        }
    }

    private void validateHeaderName(String name) {
        if (name == null
                || !name.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]{1,64}")
                || FORBIDDEN_HEADERS.contains(
                        name.toLowerCase(Locale.ROOT))) {
            throw invalid("连接器 Header 名称无效或受保护");
        }
    }

    private void validateJsonPointer(String pointer, String label) {
        boolean bodyPointer = "请求体".equals(label);
        if (pointer == null
                || pointer.length() > 512
                || (bodyPointer && pointer.isEmpty())
                || (!pointer.isEmpty() && !pointer.startsWith("/"))
                || pointer.matches(".*~(?![01]).*")) {
            throw invalid(label + "映射必须使用 JSON Pointer");
        }
        if (pointer.isEmpty()) {
            return;
        }
        String[] segments = pointer.substring(1).split("/", -1);
        if (segments.length > 16) {
            throw invalid(label + "映射层级不能超过 16 层");
        }
        for (String segment : segments) {
            if (segment.isEmpty()
                    || segment.replace("~1", "/")
                            .replace("~0", "~")
                            .length() > 64) {
                throw invalid(label + "映射包含无效字段");
            }
        }
    }

    private boolean isAllowedSource(String source) {
        return INPUT_SOURCE.matcher(source).matches()
                || CONTEXT_SOURCES.contains(source);
    }

    private void validateBodyPointerConflicts(Set<String> pointers) {
        for (String pointer : pointers) {
            for (String other : pointers) {
                if (!pointer.equals(other)
                        && other.startsWith(pointer + "/")) {
                    throw invalid("请求体映射字段存在父子冲突");
                }
            }
        }
    }

    private String normalizeHost(String value) {
        if (value == null || value.isBlank()
                || value.contains("*")
                || value.endsWith(".")) {
            throw invalid("连接器主机必须是精确 DNS 名称");
        }
        try {
            String host = IDN.toASCII(
                    value.trim().toLowerCase(Locale.ROOT),
                    IDN.USE_STD3_ASCII_RULES);
            if (host.length() > 253
                    || host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")
                    || host.contains(":")) {
                throw invalid("连接器主机必须是精确 DNS 名称");
            }
            return host;
        } catch (IllegalArgumentException exception) {
            throw invalid("连接器主机名无效");
        }
    }

    private JsonNode readObject(String document, String label) {
        try {
            JsonNode node = objectMapper.readTree(document);
            if (node == null || !node.isObject()) {
                throw invalid(label + "必须是 JSON 对象");
            }
            return node;
        } catch (JsonProcessingException exception) {
            throw invalid(label + "不是合法 JSON");
        }
    }

    private void requireDocumentSize(
            String document,
            int maximumBytes,
            String label) {
        if (document == null
                || document.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8).length
                > maximumBytes) {
            throw invalid(label + "超过大小限制");
        }
    }

    private void rejectUnknown(
            JsonNode node,
            Set<String> allowed,
            String label) {
        node.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) {
                throw invalid(label + "包含未知字段: " + name);
            }
        });
    }

    private String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null) {
            throw invalid("连接器配置缺少字段: " + field);
        }
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()
                || value.textValue().isBlank()
                || value.textValue().length() > 65536) {
            throw invalid("连接器字段格式无效: " + field);
        }
        return value.textValue();
    }

    private int integer(
            JsonNode node,
            String field,
            int defaultValue,
            int minimum,
            int maximum) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (!value.isIntegralNumber()
                || value.longValue() < minimum
                || value.longValue() > maximum) {
            throw invalid("连接器数值字段超出范围: " + field);
        }
        return value.intValue();
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
