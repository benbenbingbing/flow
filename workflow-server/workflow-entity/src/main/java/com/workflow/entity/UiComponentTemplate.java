package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UI 组件模板实体，对应 ui_component_template 表。
 * 定义可复用的前端组件模板（如字段控件、布局容器），维护当前版本与状态。
 */
@Data
@TableName("ui_component_template")
public class UiComponentTemplate {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 模板唯一标识 */
    private String templateKey;
    /** 模板名称 */
    private String templateName;
    /** 模板类型（如 field/layout/container 等） */
    private String templateType;
    /** 当前版本号 */
    private Integer currentVersion;
    /** 状态（如 DRAFT/PUBLISHED/DISABLED） */
    private String status;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updatedAt;

    /** 逻辑删除标志（0-未删除 1-已删除） */
    @TableLogic
    private Integer deleted;
}
