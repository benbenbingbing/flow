package com.workflow.openapi.connector.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.CurrentActorProvider;
import com.workflow.contracts.integration.IntegrationRequest;
import com.workflow.contracts.integration.IntegrationResult;
import com.workflow.contracts.integration.IntegrationRuntimeContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.http.HttpIntegrationConnector;
import com.workflow.openapi.api.request.TestIntegrationConnectorRequest;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "workflow.integration.connector.http.enabled",
        havingValue = "true")
public class IntegrationConnectorTestService {

    private static final int MAX_TEST_INPUT_BYTES = 65_536;
    private static final int MAX_TEST_INPUT_DEPTH = 16;
    private static final int MAX_TEST_INPUT_NODES = 1000;
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i).*(secret|token|password|credential|authorization|"
                    + "api[-_]?key|cookie).*");

    private final IntegrationConnectorConfigMapper configMapper;
    private final HttpIntegrationConnector connector;
    private final CurrentActorProvider actorProvider;
    private final ObjectMapper objectMapper;

    IntegrationConnectorTestService(
            IntegrationConnectorConfigMapper configMapper,
            HttpIntegrationConnector connector,
            CurrentActorProvider actorProvider,
            ObjectMapper objectMapper) {
        this.configMapper = configMapper;
        this.connector = connector;
        this.actorProvider = actorProvider;
        this.objectMapper = objectMapper;
    }

    public IntegrationResult test(
            String applicationId,
            String configId,
            TestIntegrationConnectorRequest request) {
        CurrentActor actor = requireActor();
        IntegrationConnectorConfigRecord configuration =
                configMapper.findOwned(applicationId, configId);
        if (configuration == null
                || !"ACTIVE".equals(configuration.getStatus())) {
            throw new IllegalArgumentException(
                    "HTTP Connector 配置不存在或未启用");
        }
        validateTestInput(request.input());
        Map<String, Object> input = Collections.unmodifiableMap(
                new LinkedHashMap<>(request.input()));
        return connector.execute(IntegrationRequest.builder()
                .connectorConfigId(configId)
                .operation(request.operation())
                .idempotencyKey(
                        "connection-test-" + UUID.randomUUID())
                .parameters(input)
                .runtimeContext(new IntegrationRuntimeContext(
                        "connector-test",
                        "CONNECTION_TEST",
                        "INTEGRATION_CONNECTOR",
                        configId,
                        null,
                        null,
                        null,
                        null,
                        null,
                        actor.userId(),
                        actor.username(),
                        null,
                        null,
                        null))
                .build());
    }

    private void validateTestInput(Map<String, Object> input) {
        rejectSensitiveKeys(
                input,
                "input",
                0,
                new int[]{0});
        try {
            if (objectMapper.writeValueAsString(input)
                    .getBytes(StandardCharsets.UTF_8).length
                    > MAX_TEST_INPUT_BYTES) {
                throw new IllegalArgumentException(
                        "连接测试输入超过 65536 字节");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "连接测试输入无法序列化",
                    exception);
        }
    }

    private void rejectSensitiveKeys(
            Object value,
            String path,
            int depth,
            int[] nodes) {
        if (depth > MAX_TEST_INPUT_DEPTH
                || ++nodes[0] > MAX_TEST_INPUT_NODES) {
            throw new IllegalArgumentException(
                    "连接测试输入结构过深或节点过多");
        }
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, child) -> {
                String name = String.valueOf(key);
                if (SENSITIVE_KEY.matcher(name).matches()) {
                    throw new IllegalArgumentException(
                            "连接测试输入禁止包含敏感字段: "
                                    + path + "." + name);
                }
                rejectSensitiveKeys(
                        child,
                        path + "." + name,
                        depth + 1,
                        nodes);
            });
        } else if (value instanceof Iterable<?> iterable) {
            int index = 0;
            for (Object child : iterable) {
                rejectSensitiveKeys(
                        child,
                        path + "[" + index++ + "]",
                        depth + 1,
                        nodes);
            }
        }
    }

    private CurrentActor requireActor() {
        CurrentActor actor = actorProvider.current();
        if (actor == null
                || actor.userId() == null
                || actor.userId().isBlank()) {
            throw new ForbiddenException("用户未登录");
        }
        return actor;
    }
}
