package com.workflow.contracts.integration;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class IntegrationResult {

    boolean success;
    String code;
    String message;
    Map<String, Object> data;
}
