package com.workflow.openapi.connector.config;

import com.workflow.http.HttpConnectorConfiguration;
import com.workflow.http.HttpConnectorConfigurationCodec;
import com.workflow.http.HttpConnectorConfigurationProvider;
import com.workflow.contracts.integration.IntegrationConnectorConfigurationSnapshot;
import com.workflow.contracts.integration.spi.IntegrationConnectorConfigurationSnapshotProvider;
import org.springframework.stereotype.Component;

@Component
public class DatabaseHttpConnectorConfigurationProvider
        implements HttpConnectorConfigurationProvider,
        IntegrationConnectorConfigurationSnapshotProvider {

    private final IntegrationConnectorConfigMapper mapper;
    private final HttpConnectorConfigurationCodec codec;

    DatabaseHttpConnectorConfigurationProvider(
            IntegrationConnectorConfigMapper mapper,
            HttpConnectorConfigurationCodec codec) {
        this.mapper = mapper;
        this.codec = codec;
    }

    @Override
    public HttpConnectorConfiguration findActive(String configurationId) {
        IntegrationConnectorConfigRecord record = requireActive(
                configurationId);
        return codec.read(
                record.getId(),
                record.getApplicationId(),
                record.getConfigurationDocument(),
                record.getAllowedHostsDocument());
    }

    @Override
    public String connectorCode() {
        return "http-json";
    }

    /**
     * 发布时只导出已通过 HTTP codec 校验的配置和 SecretRef。
     */
    @Override
    public IntegrationConnectorConfigurationSnapshot snapshot(
            String configurationId) {
        IntegrationConnectorConfigRecord record = requireActive(
                configurationId);
        return new IntegrationConnectorConfigurationSnapshot(
                connectorCode(),
                record.getId(),
                String.valueOf(record.getVersion()),
                codec.freezeSnapshot(
                        record.getId(),
                        record.getApplicationId(),
                        record.getConfigurationDocument(),
                        record.getAllowedHostsDocument()));
    }

    private IntegrationConnectorConfigRecord requireActive(
            String configurationId) {
        if (configurationId == null
                || !configurationId.matches(
                "[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException(
                    "HTTP Connector 配置 ID 无效");
        }
        IntegrationConnectorConfigRecord record =
                mapper.findActive(configurationId);
        if (record == null) {
            throw new IllegalArgumentException(
                    "HTTP Connector 配置不存在或未启用");
        }
        return record;
    }
}
