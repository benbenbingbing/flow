package com.workflow.contracts.integration;

import com.workflow.contracts.entity.list.DataScopePlan;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class IntegrationRequest {

    String idempotencyKey;
    String operation;
    Map<String, Object> parameters;
    IntegrationRuntimeContext runtimeContext;
    DataScopePlan dataScopePlan;
    Map<String, Object> permissionSummary;
}
