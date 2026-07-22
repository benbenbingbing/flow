package com.workflow.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class EntityListMetadataPatchRequest {

    private Integer expectedRevision;
    private String listName;
    private String description;
    private Boolean isDefault;
    private String customComponent;
    private String dataScopeMode;
    private String accessPermissionCode;
    private List<String> allowedScenes;
    private Map<String, Object> selectionConfig;
    private Map<String, Object> fixedFilterConfig;
    private Map<String, Object> contextBindingConfig;
    private Map<String, Object> viewConfig;
    private String queryProviderCode;
    private String queryDataSourceId;
    private Set<String> clearFields;
}
