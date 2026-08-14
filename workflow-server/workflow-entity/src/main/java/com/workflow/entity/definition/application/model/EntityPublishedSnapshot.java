package com.workflow.entity.definition.application.model;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import lombok.Data;

import java.util.List;

/**
 * 实体发布快照。
 */
@Data
public class EntityPublishedSnapshot {

    /** 发布历史记录ID */
    private String historyId;
    /** 实体定义ID */
    private String entityId;
    /** 实体编码 */
    private String entityCode;
    /** 实体名称 */
    private String entityName;
    /** 发布时绑定的流程定义ID */
    private String processDefinitionId;
    /** 实体生命周期模式 */
    private com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition.LifecycleMode lifecycleMode;
    /** 是否启用团队可见性 */
    private Boolean teamVisibilityEnabled;
    /** 团队可见性级别 */
    private com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition.TeamVisibilityLevel teamVisibilityLevel;
    /** 发布版本号 */
    private Integer version;
    /** 该版本的字段定义列表 */
    private List<EntityField> fields;
    /** 该版本冻结的实体关系；旧发布未冻结时为空列表 */
    private List<EntityRelation> relations;
    /** 是否存在关系快照，用于区分旧发布与明确发布空关系 */
    private boolean relationsSnapshotAvailable;
}
