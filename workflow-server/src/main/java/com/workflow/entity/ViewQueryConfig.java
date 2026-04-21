package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视图查询条件配置
 */
@Data
@TableName("view_query_config")
public class ViewQueryConfig {
    
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    private String viewId;
    private String fieldCode;
    private String fieldName;
    
    /**
     * 查询类型：EQ/LIKE/LEFT_LIKE/RIGHT_LIKE/GT/LT/BETWEEN/IN/NULL
     */
    private String queryType;
    
    /**
     * 组件类型：INPUT/SELECT/DATE/DATE_RANGE/NUMBER/NUMBER_RANGE/CASCADE/ENTITY_SELECT
     */
    private String componentType;
    
    private String componentConfig;
    private String defaultValue;
    private String placeholder;
    private Integer sortOrder;
    private Integer isAdvanced;
    
    private LocalDateTime createdAt;
}
