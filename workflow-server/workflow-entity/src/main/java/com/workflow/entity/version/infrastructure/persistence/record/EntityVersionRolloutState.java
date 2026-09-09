package com.workflow.entity.version.infrastructure.persistence.record;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单配置模型滚动升级期间读取旧草稿信封的临时投影。
 * 后续 contract 迁移删除旧列和旧路由时应一并删除。
 */
@Data
public class EntityVersionRolloutState {

    private String id;
    private String entityId;
    private String entityCode;
    private Boolean enabled;
    private String activeReleaseId;
    private Integer revision;
    private String status;
    private String draftDocument;
    private String configDocument;
    private String updateBy;
    private LocalDateTime updateTime;
}
