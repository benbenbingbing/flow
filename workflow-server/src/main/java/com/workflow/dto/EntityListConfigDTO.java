package com.workflow.dto;

import com.workflow.dto.permission.EntityActionCapabilityDTO;
import com.workflow.entity.EntityListField;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
     * 工具栏按钮配置JSON
     */
    private String toolbarConfig;

    /**
     * 操作列按钮配置JSON
     */
    private String rowActionConfig;

    /**
     * 列表字段配置
     */
    private List<EntityListField> fields;

    /**
     * 当前用户的工具栏运行时能力。
     */
    private Map<String, EntityActionCapabilityDTO> toolbarCapabilities = new LinkedHashMap<>();
}
