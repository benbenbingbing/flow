package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报表分类
 */
@Data
@TableName("report_category")
public class ReportCategory {
    
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    private String parentId;
    private String categoryCode;
    private String categoryName;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
