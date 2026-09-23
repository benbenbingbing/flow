package com.workflow.openapi.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import com.workflow.openapi.network.IpNetwork;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 封装集成客户端{@code network}策略相关能力和状态；供同一业务流程的后续处理使用。
 */
@Component
@ConditionalOnProperty(
        name = "workflow.open-api.enabled",
        havingValue = "true")
public class IntegrationClientNetworkPolicy {

    private static final TypeReference<List<String>> STRING_LIST =
            new TypeReference<>() {
            };

    private final IntegrationApplicationMapper applicationMapper;
    private final ObjectMapper objectMapper;

    /**
     * 初始化集成客户端{@code network}策略，保存构造参数供后续方法使用。
     *
     * @param applicationMapper 应用映射器依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     */
    public IntegrationClientNetworkPolicy(
            IntegrationApplicationMapper applicationMapper,
            ObjectMapper objectMapper) {
        this.applicationMapper = applicationMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 求值集成客户端{@code network}策略，并将结果传给后续步骤。
     *
     * @param clientId 客户端ID，后续用于求值集成客户端{@code network}策略时定位或关联目标
     * @param clientAddress 客户端地址，作为 {@code contains} 的输入影响后续处理
     * @return 求值后的集成客户端{@code network}策略结果，供调用方继续处理
     */
    public Decision evaluate(String clientId, String clientAddress) {
        if (clientAddress == null) {
            return new Decision(null, false);
        }
        IntegrationApplicationRecord application =
                applicationMapper.findByClientId(clientId);
        if (application == null) {
            return new Decision(null, true);
        }
        String configured = application.getAllowedSourceCidrs();
        if (configured == null || configured.isBlank()) {
            return new Decision(application.getId(), true);
        }
        try {
            List<String> networks = objectMapper.readValue(
                    configured,
                    STRING_LIST);
            boolean allowed = networks.isEmpty()
                    || networks.stream()
                    .map(IpNetwork::parse)
                    .anyMatch(network ->
                            network.contains(clientAddress));
            return new Decision(application.getId(), allowed);
        } catch (RuntimeException
                | com.fasterxml.jackson.core.JsonProcessingException
                exception) {
            return new Decision(application.getId(), false);
        }
    }

    /**
     * 封装决策的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param applicationId 应用ID，后续用于处理决策时定位或关联目标
     * @param allowed 允许，保存在对象中供后续校验、查询或展示
     */
    public record Decision(String applicationId, boolean allowed) {
    }
}
