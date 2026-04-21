package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视图字段配置
 */
@Data
@TableName("view_field_config")
public class ViewFieldConfig {
    
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    private String viewId;
    private String fieldCode;
    private String fieldName;
    private String fieldType;
    private Integer sortOrder;
    private String width;
    private String align;
    private Integer isShow;
    private Integer isSortable;
    private Integer isSearchable;
    
    /**
     * 格式化类型：TEXT/DATE/DATETIME/DICT/TAG/LINK/IMAGE/PROGRESS/CUSTOM
     */
    private String formatterType;
    
    private String formatterConfig;
    private String fixed;
    private Integer showInList;
    private Integer showInDetail;
    
    private LocalDateTime createdAt;
}
