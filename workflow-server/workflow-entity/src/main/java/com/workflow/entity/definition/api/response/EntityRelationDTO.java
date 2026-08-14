package com.workflow.entity.definition.api.response;

import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import lombok.Data;

/**
 * 独立实体关系响应。
 */
@Data
public class EntityRelationDTO {

    private String id;
    private String parentEntityId;
    private String parentEntityCode;
    private String relationCode;
    private String relationName;
    private String dataKey;
    private String childEntityId;
    private String childEntityCode;
    private String childEntityName;
    private String childRefFieldCode;
    private EntityRelation.RelationType relationType;
    private EntityRelation.OwnershipType ownershipType;
    private Boolean cascadeDelete;
    private Boolean required;
    private Integer sortOrder;
    private Boolean enabled;
    private String parentFieldId;
    private String parentFieldCode;
}
