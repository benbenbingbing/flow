package com.workflow.dto;

import lombok.Data;

import java.util.Map;

@Data
public class UiComponentTemplateSaveRequest {

    private String id;
    private String templateKey;
    private String templateName;
    private String templateType;
    private String description;
    private Map<String, Object> snapshot;
}
