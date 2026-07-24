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

    /** 列表配置 ID */
    private String id;

    /** 实体 ID */
    private String entityId;

    /** 实体编码 */
    private String entityCode;

    /** 列表标识 */
    private String listKey;

    /** 列表名称 */
    private String listName;

    /** 列表描述 */
    private String description;

    /** 是否为默认列表 */
    private Boolean isDefault;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 自定义列表组件注册名
     */
    private String customComponent;

    /** 工具栏配置 */
    private List<Map<String, Object>> toolbarConfig;

    /** 行操作按钮配置 */
    private List<Map<String, Object>> rowActionConfig;

    /** 视图配置 */
    private Map<String, Object> viewConfig;

    /** 数据范围模式 */
    private String dataScopeMode;

    /** 访问权限码 */
    private String accessPermissionCode;

    /** 允许的场景列表 */
    private List<String> allowedScenes;

    /** 选择配置（多选/单选等） */
    private Map<String, Object> selectionConfig;

    /** 固定过滤配置 */
    private Map<String, Object> fixedFilterConfig;

    /** 上下文绑定配置（父子数据联动等） */
    private Map<String, Object> contextBindingConfig;

    /** 数据查询提供者编码 */
    private String queryProviderCode;

    /** 已发布版本号 */
    private Integer publishedVersion;

    /** 草稿修订号 */
    private Integer revision;

    /**
     * 整包更新时客户端读取到的草稿修订号。
     */
    private Integer expectedRevision;

    /** 当前生效的发布 ID */
    private String activeReleaseId;

    /** 草稿内容哈希 */
    private String draftHash;

    /** 数据查询数据源 ID */
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
