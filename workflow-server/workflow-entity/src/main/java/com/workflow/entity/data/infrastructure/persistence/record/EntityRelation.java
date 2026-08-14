package com.workflow.entity.data.infrastructure.persistence.record;

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

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 父实体ID */
    private String parentEntityId;
    /** 父实体编码 */
    private String parentEntityCode;
    /** 父字段ID（旧版展示字段绑定，仅用于兼容） */
    private String parentFieldId;
    /** 父字段编码（旧版展示字段绑定，仅用于兼容） */
    private String parentFieldCode;
    /** 关系编码 */
    private String relationCode;
    /** 关系名称 */
    private String relationName;
    /** 聚合数据中的稳定属性名，不依赖实体字段 */
    private String dataKey;
    /** 子实体ID */
    private String childEntityId;
    /** 子实体编码 */
    private String childEntityCode;
    /** 子表中用于关联父记录的字段编码 */
    private String childRefFieldCode;
    /** 关系类型（一对一/一对多） */
    private RelationType relationType;
    /** 关系所有权：组成关系或普通关联 */
    private OwnershipType ownershipType;
    /** 是否级联删除（删除父记录时联动删除子记录） */
    private Boolean cascadeDelete;
    /** 关系是否必填 */
    private Boolean required;
    /** 排序号 */
    private Integer sortOrder;
    /** 是否启用 */
    private Boolean enabled;

    @TableLogic
    private Integer deleted;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum RelationType {
        /** 一对一：一条父记录对应至多一条子记录 */
        ONE_TO_ONE,
        /** 一对多：一条父记录对应多条子记录 */
        ONE_TO_MANY
    }

    public enum OwnershipType {
        /** 子记录属于父聚合，可配置级联删除 */
        COMPOSITION,
        /** 子记录独立存在，不允许由父记录级联删除 */
        ASSOCIATION
    }
}
