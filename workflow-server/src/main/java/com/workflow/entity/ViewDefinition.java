package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视图定义
 */
@Data
@TableName("view_definition")
public class ViewDefinition {
    
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    private String viewCode;
    private String viewName;
    
    /**
     * 视图类型：LIST列表/CHART图表/DASHBOARD看板/DETAIL详情
     */
    private String viewType;
    
    private String entityCode;
    
    /**
     * 数据源类型：ENTITY/SQL/API
     */
    private String dataSourceType;
    
    private String dataSourceConfig;
    private String layoutConfig;
    private String styleConfig;
    
    private Integer isDefault;
    private Integer version;
    private String status;
    
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 非持久化字段
    private transient String entityName;
}
