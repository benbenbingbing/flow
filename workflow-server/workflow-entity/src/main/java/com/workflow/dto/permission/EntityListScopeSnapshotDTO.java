package com.workflow.dto.permission;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class EntityListScopeSnapshotDTO {
    private String entityCode;
    private Integer version;
    private List<EntityListScopePolicyDTO> policies = new ArrayList<>();
    private List<EntityListScopeBindingDTO> bindings = new ArrayList<>();
    private Map<String, String> listModes = new LinkedHashMap<>();
}
