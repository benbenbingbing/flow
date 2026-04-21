package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视图按钮配置
 */
@Data
@TableName("view_button_config")
public class ViewButtonConfig {
    
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    private String viewId;
    private String buttonCode;
    private String buttonName;
    
    /**
     * 按钮位置：TOOLBAR工具栏/ROW行操作/BATCH批量操作
     */
    private String buttonType;
    
    /**
     * 动作类型：ADD/EDIT/DELETE/VIEW/EXPORT/IMPORT/CUSTOM/SERVICE/JUMP
     */
    private String actionType;
    
    private String actionConfig;
    private String icon;
    private String style;
    private Integer sortOrder;
    private String visibleCondition;
    private String permissionCode;
    
    private LocalDateTime createdAt;
}
