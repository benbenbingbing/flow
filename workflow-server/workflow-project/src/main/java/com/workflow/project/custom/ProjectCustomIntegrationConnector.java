package com.workflow.project.custom;

import com.workflow.contracts.integration.IntegrationConnector;
import com.workflow.contracts.integration.IntegrationRequest;
import com.workflow.contracts.integration.IntegrationResult;
import com.workflow.core.logging.LogValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 集成连接器扩展示例。
 *
 * <p>通过连接器编码 {@value #CODE} 调用。当前实现不会发起网络请求，
 * 仅记录操作信息并返回成功的演示结果，用于验证接口服务的连接器路由。</p>
 */
@Slf4j
@Component
public class ProjectCustomIntegrationConnector
        implements IntegrationConnector {

    public static final String CODE =
            "PROJECT_CUSTOM_LOG_CONNECTOR";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public IntegrationResult execute(
            IntegrationRequest request) {
        Map<String, Object> parameters =
                request == null || request.getParameters() == null
                        ? Map.of()
                        : request.getParameters();
        boolean allowed = request != null
                && request.getDataScopePlan() != null
                && request.getDataScopePlan().allowed();
        log.info(
                "项目集成连接器执行: code={}, operation={}, connectorConfigId={}, idempotencyKey={}, allowed={}, parameterKeys={}",
                CODE,
                LogValue.safe(request == null
                        ? null : request.getOperation()),
                LogValue.safe(request == null
                        ? null : request.getConnectorConfigId()),
                LogValue.safe(request == null
                        ? null : request.getIdempotencyKey()),
                allowed,
                parameters.keySet());

        if (request != null
                && request.getDataScopePlan() != null
                && !allowed) {
            return IntegrationResult.builder()
                    .success(false)
                    .code("DATA_SCOPE_DENIED")
                    .message("数据范围未授权，项目连接器未执行")
                    .data(Map.of(
                            "connectorCode", CODE,
                            "externalRequestSent", false))
                    .build();
        }
        Map<String, Object> data =
                new LinkedHashMap<>();
        data.put("connectorCode", CODE);
        data.put("parameterKeys",
                List.copyOf(parameters.keySet()));
        data.put("externalRequestSent", false);
        return IntegrationResult.builder()
                .success(true)
                .code("PROJECT_CUSTOM_LOGGED")
                .message("项目自定义连接器已记录日志，未调用外部系统")
                .data(data)
                .build();
    }
}
