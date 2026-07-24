package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体字段选项实体，对应 entity_field_option 表。
 * 用于存储下拉、单选、多选等字段的候选项，每项包含值、标签、样式及扩展配置。
 */
@Data
@TableName("entity_field_option")
public class EntityFieldOption {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 所属字段ID */
    private String fieldId;
    /** 选项值（提交时存储的实际值） */
    private String optionValue;
    /** 选项标签（界面显示文案） */
    private String optionLabel;
    /** 选项样式类型（如 primary/success/danger 等） */
    private String styleType;
    /** 是否禁用（true-禁用不可选） */
    private Boolean disabled;
    /** 排序号 */
    private Integer sortOrder;
    /** 选项扩展配置（JSON） */
    private String optionDocument;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updatedAt;
}
