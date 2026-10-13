package com.workflow.entity.definition.infrastructure.persistence.record;

import com.workflow.entity.definition.api.response.EntitySchemaOperationDTO;
import lombok.Getter;
import lombok.Setter;

/** 操作台账的数据库投影；JSON 原文交由服务校验后才作为发布计划或诊断列表使用。 */
@Getter
@Setter
public class EntitySchemaOperationRow extends EntitySchemaOperationDTO {
    private String planJson;
    private String driftJson;
    private String uniqueConflictJson;
}
