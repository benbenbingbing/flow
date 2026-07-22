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

    private String id;
    private String entityId;
    private String entityCode;
    private String entityName;
    private String processDefinitionId;
    private com.workflow.entity.EntityDefinition.LifecycleMode lifecycleMode;
    private Boolean teamVisibilityEnabled;
    private com.workflow.entity.EntityDefinition.TeamVisibilityLevel teamVisibilityLevel;
    private Integer version;
    private String versionDescription;
    private String fieldsSnapshot;
    private String tableDdl;
    private EntityPublishHistory.PublishType publishType;
    private String changesDescription;
    private LocalDateTime publishedAt;
    private String publishedBy;
    private String publishedByName;
    private EntityPublishHistory.Status status;

    /**
     * 解析后的字段列表（用于前端展示）
     */
    private List<EntityFieldDTO> fields;
}
