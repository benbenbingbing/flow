package com.workflow.contracts.integration;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class IntegrationRequest {

    String idempotencyKey;
    String operation;
    Map<String, Object> parameters;
}
