package com.workflow.contracts.integration;

public interface IntegrationConnector {

    String code();

    IntegrationResult execute(IntegrationRequest request);
}
