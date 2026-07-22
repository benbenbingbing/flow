package com.workflow.dto;

import lombok.Data;

import java.util.Map;
import java.util.Set;

@Data
public class EntityFormMetadataPatchRequest {

    private Integer expectedRevision;
    private String formName;
    private String description;
    private String layoutType;
    private Boolean isDefault;
    private Integer status;
    private String customComponent;
    private Integer customComponentVersion;
    private Integer customComponentSnapshotVersion;
    private Map<String, Object> initConfig;
    private Map<String, Object> dataSourceBindings;
    private Map<String, Object> viewConfig;
    private Set<String> clearFields;
}
