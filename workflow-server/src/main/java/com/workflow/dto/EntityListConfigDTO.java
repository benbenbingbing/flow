package com.workflow.dto;

import com.workflow.entity.EntityListField;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 实体列表配置 DTO
 */
@Data
public class EntityListConfigDTO {

    private String id;

    private String entityId;

    private String entityCode;

    private String listKey;

    private String listName;

    private String description;

    private Boolean isDefault;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 自定义列表组件注册名
     */
    private String customComponent;

    /**
     * 列表字段配置
     */
    private List<EntityListField> fields;
}
