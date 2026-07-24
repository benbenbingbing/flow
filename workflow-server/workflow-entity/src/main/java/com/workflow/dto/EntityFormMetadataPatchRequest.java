package com.workflow.dto;

import lombok.Data;

import java.util.Map;
import java.util.Set;

/**
 * 表单元数据局部更新请求。
 * 仅更新表单的元数据属性（不含节点结构），支持通过 clearFields 清空指定字段。
 */
@Data
public class EntityFormMetadataPatchRequest {

    /** 客户端读取到的草稿修订号，用于乐观并发控制 */
    private Integer expectedRevision;
    /** 表单名称 */
    private String formName;
    /** 表单描述 */
    private String description;
    /** 布局类型 */
    private String layoutType;
    /** 是否为默认表单 */
    private Boolean isDefault;
    /** 表单状态 */
    private Integer status;
    /** 自定义组件注册名 */
    private String customComponent;
    /** 自定义组件版本 */
    private Integer customComponentVersion;
    /** 自定义组件快照版本 */
    private Integer customComponentSnapshotVersion;
    /** 初始化配置 */
    private Map<String, Object> initConfig;
    /** 数据源绑定配置 */
    private Map<String, Object> dataSourceBindings;
    /** 视图配置 */
    private Map<String, Object> viewConfig;
    /** 需要清空的字段集合（局部更新时置空指定字段） */
    private Set<String> clearFields;
}
