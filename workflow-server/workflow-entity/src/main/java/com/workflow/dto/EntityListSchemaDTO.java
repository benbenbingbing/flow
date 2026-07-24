package com.workflow.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体列表 Schema DTO。
 * 用于前端运行时加载列表的完整 Schema（不含草稿修订等运维字段）。
 */
@Data
public class EntityListSchemaDTO {
    /** 列表配置 ID */
    private String id;
    /** 实体编码 */
    private String entityCode;
    /** 实体名称 */
    private String entityName;
    /** 列表标识 */
    private String listKey;
    /** 列表名称 */
    private String listName;
    /** 场景 */
    private String scene;
    /** 访问权限码 */
    private String accessPermissionCode;
    /** 数据范围模式 */
    private String dataScopeMode;
    /** 已发布版本号 */
    private Integer publishedVersion;
    /** 选择配置 */
    private Map<String, Object> selectionConfig;
    /** 视图配置 */
    private Map<String, Object> viewConfig;
    /** 工具栏配置 */
    private List<Map<String, Object>> toolbarConfig;
    /** 行操作按钮配置 */
    private List<Map<String, Object>> rowActionConfig;
    /** 自定义列表组件注册名 */
    private String customComponent;
    /** 允许的场景列表 */
    private List<String> allowedScenes;
    /** 固定过滤配置 */
    private Map<String, Object> fixedFilterConfig;
    /** 上下文绑定配置 */
    private Map<String, Object> contextBindingConfig;
    /** 数据查询提供者编码 */
    private String queryProviderCode;
    /** 当前用户的工具栏运行时能力 */
    private Map<String, ?> toolbarCapabilities = new LinkedHashMap<>();
    /** 列表字段配置 */
    private List<?> fields = new ArrayList<>();
}
