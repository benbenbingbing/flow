package com.workflow.dto.migration;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class ConfigExportRequest {

    private List<String> assetIds;
    private String migrationTag;
    private Map<String, Object> selections = new LinkedHashMap<>();
    private Set<String> validateOnlyDependencies = new LinkedHashSet<>();
}
