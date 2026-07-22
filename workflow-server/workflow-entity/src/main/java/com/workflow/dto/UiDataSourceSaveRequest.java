package com.workflow.dto;

import lombok.Data;

import java.util.Map;

@Data
public class UiDataSourceSaveRequest {

    private String id;
    private Integer expectedRevision;
    private String sourceCode;
    private String sourceName;
    private String sourceType;
    private String providerCode;
    private String scopeType;
    private String scopeId;
    private Map<String, Object> config;
    private Map<String, Object> inputSchema;
    private Map<String, Object> outputSchema;
    private Map<String, Object> executionPolicy;
    private Boolean enabled;
}
