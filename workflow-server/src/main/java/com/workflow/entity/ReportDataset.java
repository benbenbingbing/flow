package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报表数据集
 */
@Data
@TableName("report_dataset")
public class ReportDataset {
    
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    private String reportId;
    private String datasetCode;
    private String datasetName;
    
    /**
     * 数据集类型：SQL/ENTITY/API
     */
    private String datasetType;
    
    private String sourceConfig;
    private String fieldMappings;
    private String cacheConfig;
    
    private LocalDateTime createdAt;
}
