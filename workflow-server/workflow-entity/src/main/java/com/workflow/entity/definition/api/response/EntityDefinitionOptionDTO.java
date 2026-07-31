package com.workflow.entity.definition.api.response;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import lombok.Data;

/**
 * 实体选择器使用的轻量选项。
 */
@Data
public class EntityDefinitionOptionDTO {

    private String id;

    private String entityCode;

    private String entityName;

    private EntityDefinition.LifecycleMode lifecycleMode;

    private EntityDefinition.StorageMode storageMode;

    private EntityDefinition.Status status;
}
