package com.workflow.entity.list.api.request;

import lombok.Data;

import java.util.Map;
import java.util.Set;

/**
 * 实体列表元数据局部更新请求。
 * 仅更新列表的元数据属性，支持通过 clearFields 清空指定字段。
 */
@Data
public class EntityListMetadataPatchRequest {

    /** 客户端读取到的草稿修订号，用于乐观并发控制 */
    private Integer expectedRevision;
    /** 列表名称 */
    private String listName;
    /** 列表描述 */
    private String description;
    /** 是否为默认列表 */
    private Boolean isDefault;
    /** 自定义列表组件注册名 */
    private String customComponent;
    /** 数据范围模式 */
    private String dataScopeMode;
    /** 访问权限码 */
    private String accessPermissionCode;
    /** 选择配置 */
    private Map<String, Object> selectionConfig;
    /** 固定过滤配置 */
    private Map<String, Object> fixedFilterConfig;
    /** 视图配置 */
    private Map<String, Object> viewConfig;
    /** 数据查询提供者编码 */
    private String queryProviderCode;
    /** 历史查询槽位，仅用于旧配置兼容；新查询接口通过 LIST_LOAD 替代步骤配置。 */
    private String queryInterfaceExtensionId;
    /** 需要清空的字段集合（局部更新时置空指定字段） */
    private Set<String> clearFields;
}
