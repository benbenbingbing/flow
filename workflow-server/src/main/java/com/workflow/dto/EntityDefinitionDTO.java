package com.workflow.dto;

import com.workflow.entity.EntityDefinition;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 实体定义DTO
 */
@Data
public class EntityDefinitionDTO {
    private String id;
    private String entityCode;
    private String entityName;
    private String description;
    private String processDefinitionId;
    private String processName;
    private Boolean enableProcess;
    private List<EntityFieldDTO> fields;
    private EntityDefinition.Status status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
}
