package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("entity_field_option")
public class EntityFieldOption {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String fieldId;
    private String optionValue;
    private String optionLabel;
    private String styleType;
    private Boolean disabled;
    private Integer sortOrder;
    private String optionDocument;

    @TableField("create_time")
    private LocalDateTime createdAt;

    @TableField("update_time")
    private LocalDateTime updatedAt;
}
