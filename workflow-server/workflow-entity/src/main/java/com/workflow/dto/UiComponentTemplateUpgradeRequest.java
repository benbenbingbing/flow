package com.workflow.dto;

import lombok.Data;

import java.util.Map;

@Data
public class UiComponentTemplateUpgradeRequest {

    private Integer fromVersion;
    private Integer toVersion;
    private Map<String, Object> currentSnapshot;
    private Map<String, Object> localOverrides;
}
