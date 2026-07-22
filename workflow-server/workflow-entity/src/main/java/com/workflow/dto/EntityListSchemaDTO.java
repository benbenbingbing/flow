package com.workflow.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class EntityListSchemaDTO {
    private String id;
    private String entityCode;
    private String entityName;
    private String listKey;
    private String listName;
    private String scene;
    private String accessPermissionCode;
    private String dataScopeMode;
    private Integer publishedVersion;
    private Map<String, Object> selectionConfig;
    private Map<String, Object> viewConfig;
    private List<Map<String, Object>> toolbarConfig;
    private List<Map<String, Object>> rowActionConfig;
    private String customComponent;
    private List<String> allowedScenes;
    private Map<String, Object> fixedFilterConfig;
    private Map<String, Object> contextBindingConfig;
    private String queryProviderCode;
    private Map<String, ?> toolbarCapabilities = new LinkedHashMap<>();
    private List<?> fields = new ArrayList<>();
}
