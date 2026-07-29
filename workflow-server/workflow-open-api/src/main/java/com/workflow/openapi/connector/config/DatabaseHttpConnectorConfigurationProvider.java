package com.workflow.openapi.connector.config;

import com.workflow.http.HttpConnectorConfiguration;
import com.workflow.http.HttpConnectorConfigurationCodec;
import com.workflow.http.HttpConnectorConfigurationProvider;
import org.springframework.stereotype.Component;

@Component
public class DatabaseHttpConnectorConfigurationProvider
        implements HttpConnectorConfigurationProvider {

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
        if (configurationId == null
                || !configurationId.matches(
                        "[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("HTTP Connector 配置 ID 无效");
        }
        IntegrationConnectorConfigRecord record =
                mapper.findActive(configurationId);
        if (record == null) {
            throw new IllegalArgumentException(
                    "HTTP Connector 配置不存在或未启用");
        }
        return codec.read(
                record.getId(),
                record.getApplicationId(),
                record.getConfigurationDocument(),
                record.getAllowedHostsDocument());
    }
}
