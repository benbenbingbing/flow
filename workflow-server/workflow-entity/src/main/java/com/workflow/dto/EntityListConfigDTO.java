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

    private List<Map<String, Object>> toolbarConfig;

    private List<Map<String, Object>> rowActionConfig;

    private Map<String, Object> viewConfig;

    private String dataScopeMode;

    private String accessPermissionCode;

    private List<String> allowedScenes;

    private Map<String, Object> selectionConfig;

    private Map<String, Object> fixedFilterConfig;

    private Map<String, Object> contextBindingConfig;

    private String queryProviderCode;

    private Integer publishedVersion;

    private Integer revision;

    /**
     * 整包更新时客户端读取到的草稿修订号。
     */
    private Integer expectedRevision;

    private String activeReleaseId;

    private String draftHash;

    private String queryDataSourceId;

    /**
     * 列表字段配置
     */
    private List<EntityListField> fields;

    /**
     * 当前用户的工具栏运行时能力。
     */
    private Map<String, EntityActionCapabilityDTO> toolbarCapabilities = new LinkedHashMap<>();
}
