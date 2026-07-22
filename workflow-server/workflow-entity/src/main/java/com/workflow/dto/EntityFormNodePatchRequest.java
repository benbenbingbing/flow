package com.workflow.dto;

import lombok.Data;

import java.util.Map;
import java.util.Set;

@Data
public class EntityFormNodePatchRequest {

    private Integer expectedRevision;
    private String parentId;
    private String nodeKey;
    private String nodeType;
    private String bindingType;
    private String bindingRef;
    private String componentName;
    private Integer componentVersion;
    private Integer snapshotVersion;
    private String childFormId;
    private String childFormReleaseId;
    private Integer childFormReleaseVersion;
    private Map<String, Object> props;
    private Map<String, Object> rules;
    private Map<String, Object> dataSourceBindings;
    private Map<String, Object> legacyProps;
    private Long orderKey;
    private String templateId;
    private Integer templateVersion;
    private Map<String, Object> localOverrides;
    private Set<String> clearFields;
}
