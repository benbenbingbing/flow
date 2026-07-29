package com.workflow.http;

public interface HttpConnectorConfigurationProvider {

    HttpConnectorConfiguration findActive(String configurationId);
}
