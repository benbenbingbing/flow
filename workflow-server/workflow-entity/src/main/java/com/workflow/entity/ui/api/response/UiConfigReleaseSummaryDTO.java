package com.workflow.entity.ui.api.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * UI 发布历史摘要，不包含快照和补丁大字段。
 */
@Data
public class UiConfigReleaseSummaryDTO {

    private String id;
    private String configType;
    private String configId;
    private Integer version;
    private String contentHash;
    private String status;
    private String description;
    private String releaseMode;
    private String baseReleaseId;
    private String riskLevel;
    private String rolloutScope;
    private String rolloutStatus;
    private String publishedBy;
    private LocalDateTime publishedAt;
}
