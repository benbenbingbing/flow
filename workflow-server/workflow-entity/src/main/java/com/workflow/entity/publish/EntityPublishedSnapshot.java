package com.workflow.entity.publish;

import com.workflow.entity.EntityField;
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
    private com.workflow.entity.EntityDefinition.LifecycleMode lifecycleMode;
    /** 是否启用团队可见性 */
    private Boolean teamVisibilityEnabled;
    /** 团队可见性级别 */
    private com.workflow.entity.EntityDefinition.TeamVisibilityLevel teamVisibilityLevel;
    /** 发布版本号 */
    private Integer version;
    /** 该版本的字段定义列表 */
    private List<EntityField> fields;
}
