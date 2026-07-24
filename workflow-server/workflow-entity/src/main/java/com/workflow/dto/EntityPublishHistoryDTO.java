package com.workflow.dto;

import com.workflow.entity.EntityPublishHistory;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 实体发布版本历史DTO
 */
@Data
public class EntityPublishHistoryDTO {

    /** 历史记录 ID */
    private String id;
    /** 实体 ID */
    private String entityId;
    /** 实体编码 */
    private String entityCode;
    /** 实体名称 */
    private String entityName;
    /** 绑定的流程定义 ID */
    private String processDefinitionId;
    /** 生命周期模式：STANDALONE / WORKFLOW */
    private com.workflow.entity.EntityDefinition.LifecycleMode lifecycleMode;
    /** 是否启用团队可见性 */
    private Boolean teamVisibilityEnabled;
    /** 团队可见性级别 */
    private com.workflow.entity.EntityDefinition.TeamVisibilityLevel teamVisibilityLevel;
    /** 发布版本号 */
    private Integer version;
    /** 版本描述 */
    private String versionDescription;
    /** 字段结构快照（JSON） */
    private String fieldsSnapshot;
    /** 建表 DDL */
    private String tableDdl;
    /** 发布类型（首次发布/字段变更/配置变更等） */
    private EntityPublishHistory.PublishType publishType;
    /** 变更内容描述 */
    private String changesDescription;
    /** 发布时间 */
    private LocalDateTime publishedAt;
    /** 发布人 ID */
    private String publishedBy;
    /** 发布人姓名 */
    private String publishedByName;
    /** 发布状态 */
    private EntityPublishHistory.Status status;

    /**
     * 解析后的字段列表（用于前端展示）
     */
    private List<EntityFieldDTO> fields;
}
