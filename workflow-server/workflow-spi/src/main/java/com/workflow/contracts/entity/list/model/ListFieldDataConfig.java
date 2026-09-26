package com.workflow.contracts.entity.list.model;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 列数据源与展示配置的边界快照，供 Provider 校验参数及定位输出列。
 * 保留现有配置值和稳定标识，不包含 ORM 注解；修改此对象不会更新列表配置。
 */
@Data
public class ListFieldDataConfig {
    private String id;
    private String listConfigId;
    private String fieldId;
    /** 写入行 extData 时使用的列键。 */
    private String fieldCode;
    private String fieldName;
    private Integer sortOrder;
    private Long orderKey;
    private Integer revision;
    private Integer width;
    private Boolean showInList;
    private Boolean isQuery;
    private String queryType;
    private String align;
    private String dataSourceType;
    /** 当前列数据源参数文档，Provider 按其声明的 schema 解析。 */
    private String dataSourceConfig;
    private String interfaceExtensionId;
    private String renderComponent;
    private String formatter;
    private String columnConfig;
    private String queryConfig;
    private String renderConfig;
    private String templateId;
    private Integer templateVersion;
    private String localOverridesDocument;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
