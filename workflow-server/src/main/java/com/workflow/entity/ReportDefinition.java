package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报表定义
 */
@Data
@TableName("report_definition")
public class ReportDefinition {
    
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    private String reportCode;
    private String reportName;
    
    /**
     * 报表类型：TABLE表格/CHART图表/DASHBOARD大屏/PRINT打印
     */
    private String reportType;
    
    private String categoryId;
    private String datasetConfig;
    private String layoutConfig;
    private String paramsConfig;
    private String styleConfig;
    private String permissionConfig;
    
    private Integer version;
    private String status;
    
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 非持久化字段
    private transient String categoryName;
}
