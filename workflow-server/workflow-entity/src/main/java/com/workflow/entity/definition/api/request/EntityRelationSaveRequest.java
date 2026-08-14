package com.workflow.entity.definition.api.request;

import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import lombok.Data;

/**
 * 独立实体关系保存请求。
 */
@Data
public class EntityRelationSaveRequest {

    /** 稳定关系编码，创建后不可修改 */
    private String relationCode;
    /** 中文关系名称 */
    private String relationName;
    /** 聚合数据中的稳定属性名，创建后不可修改 */
    private String dataKey;
    /** 子实体 ID */
    private String childEntityId;
    /** 子实体中保存父记录 ID 的字段编码 */
    private String childRefFieldCode;
    /** 一对一或一对多 */
    private EntityRelation.RelationType relationType;
    /** 组成关系或普通关联 */
    private EntityRelation.OwnershipType ownershipType;
    /** 是否级联删除，仅组成关系可开启 */
    private Boolean cascadeDelete;
    /** 是否必填 */
    private Boolean required;
    /** 排序号 */
    private Integer sortOrder;
    /** 是否启用 */
    private Boolean enabled;
    /** 旧版承载字段 ID，仅用于兼容已有子表单字段 */
    private String parentFieldId;
    /** 旧版承载字段编码，仅用于兼容已有子表单字段 */
    private String parentFieldCode;
}
