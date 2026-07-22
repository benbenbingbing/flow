package com.workflow.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class UiExtensionDefinitionSaveRequest {

    private String id;
    private String extensionType;
    private String extensionKey;
    private String displayName;
    private Integer version;
    private Integer snapshotVersion;
    private List<String> supportedModes;
    private List<String> supportedNodeTypes;
    private List<String> supportedBindings;
    private Map<String, Object> configSchema;
    private Map<String, Object> capabilities;
    private String status;
    private Integer expectedRevision;
}
