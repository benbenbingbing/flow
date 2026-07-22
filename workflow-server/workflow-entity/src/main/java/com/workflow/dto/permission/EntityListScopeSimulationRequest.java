package com.workflow.dto.permission;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class EntityListScopeSimulationRequest {
    private String userId;
    private String scene;
    private Map<String, Object> filters = new LinkedHashMap<>();
}
