package com.workflow.dto.permission;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class EntityListScopeConfigurationDTO {
    private String entityCode;
    private Integer activeVersion;
    private List<EntityListScopePolicyDTO> policies = new ArrayList<>();
    private List<EntityListScopeBindingDTO> bindings = new ArrayList<>();
}
