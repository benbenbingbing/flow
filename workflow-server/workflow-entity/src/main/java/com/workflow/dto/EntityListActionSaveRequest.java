package com.workflow.dto;

import lombok.Data;

import java.util.Map;
import java.util.Set;

@Data
public class EntityListActionSaveRequest {

    private Integer expectedRevision;
    private String position;
    private String buttonKey;
    private String buttonType;
    private String buttonLabel;
    private String icon;
    private String styleType;
    private Boolean linkMode;
    private String customMode;
    private String handlerCode;
    private String permissionCode;
    private Boolean enabled;
    private String unavailableBehavior;
    private Integer sortOrder;
    private Map<String, Object> actionParams;
    private Map<String, Object> availabilityRule;
    private Long orderKey;
    private String templateId;
    private Integer templateVersion;
    private Object localOverridesDocument;
    private Set<String> clearFields;
}
