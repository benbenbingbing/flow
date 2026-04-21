package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务分类
 */
@Data
@TableName("service_category")
public class ServiceCategory {
    
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    private String parentId;
    private String categoryCode;
    private String categoryName;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
