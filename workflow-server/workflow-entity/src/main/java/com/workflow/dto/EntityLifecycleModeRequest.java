package com.workflow.dto;

import com.workflow.entity.EntityDefinition;
import lombok.Data;

@Data
public class EntityLifecycleModeRequest {
    private EntityDefinition.LifecycleMode lifecycleMode;
}
