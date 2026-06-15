package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableField;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
/**
 * 实体字段附件项配置
 * 用于文件类型字段配置多个独立的附件要求
 */
@Data
@TableName("entity_field_file_item")
public class EntityFieldFileItem {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 关联字段ID（entity_field.id）
     */
    private String fieldId;

    /**
     * 附件项名称（如：项目章程、需求文档）
     */
    private String itemName;

    /**
     * 允许的文件类型（逗号分隔，如：.pdf,.doc,.docx）
     */
    private String fileTypes;

    /**
     * 单文件大小限制（MB）
     */
    private Integer maxSize;

    /**
     * 文件数量限制
     */
    private Integer maxCount;

    /**
     * 排序号
     */
    private Integer sortOrder;

    /**
     * 创建时间
     */
        @TableField("create_time")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
        @TableField("update_time")
    private LocalDateTime updatedAt;
}
