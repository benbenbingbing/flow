package com.workflow.entity.publish;

import com.workflow.entity.EntityField;
import lombok.Data;

import java.util.List;

/**
 * 实体发布快照。
 */
@Data
public class EntityPublishedSnapshot {

    private String historyId;
    private String entityId;
    private String entityCode;
    private String entityName;
    private String processDefinitionId;
    private Integer version;
    private List<EntityField> fields;
}
