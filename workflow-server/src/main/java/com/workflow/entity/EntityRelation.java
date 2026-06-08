package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体关系定义。
 */
@Data
@TableName("entity_relation")
public class EntityRelation {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String parentEntityId;
    private String parentEntityCode;
    private String parentFieldId;
    private String parentFieldCode;
    private String relationCode;
    private String relationName;
    private String childEntityId;
    private String childEntityCode;
    private String childRefFieldCode;
    private RelationType relationType;
    private Boolean cascadeDelete;
    private Boolean required;
    private Integer sortOrder;
    private Boolean enabled;

    @TableLogic
    private Integer deleted;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum RelationType {
        ONE_TO_ONE,
        ONE_TO_MANY
    }
}
