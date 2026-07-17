package com.workflow.contracts.integration;

public interface IntegrationSecretResolver {

    String resolve(String secretAlias);
}
