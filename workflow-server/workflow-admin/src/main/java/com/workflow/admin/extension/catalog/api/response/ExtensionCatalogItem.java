package com.workflow.admin.extension.catalog.api.response;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一扩展目录条目。
 */
@Data
public class ExtensionCatalogItem {

    private String id;
    private String capabilityType;
    private String key;
    private String displayName;
    private String description;
    private Integer implementationVersion;
    private Integer snapshotVersion;
    private Integer contractVersion;
    private String sourceType;
    private String sourceName;
    private String implementationClass;
    private String status;
    private Boolean configured;
    private Boolean available;
    private Boolean enabled;
    private String visibilityScope;
    private List<String> entityCodes;
    private Set<String> supportedUsages;
    private Set<String> supportedModes;
    private Set<String> supportedNodeTypes;
    private Set<String> supportedBindings;
    private Set<String> supportedTriggerTimings;
    private Set<String> supportedExecutionModes;
    private String recommendedExecutionMode;
    private String parameterType;
    private Object configSchema;
    private Map<String, Object> extraParamSchema;
    private Map<String, Object> capabilities;
    private Boolean dynamicExtraParams;
    private Integer revision;
}
