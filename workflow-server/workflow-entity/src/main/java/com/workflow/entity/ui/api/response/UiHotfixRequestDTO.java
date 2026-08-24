package com.workflow.entity.ui.api.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** UI HOTFIX 申请、复核、发布和观察状态。 */
@Data
public class UiHotfixRequestDTO {

    private String id;
    private String configType;
    private String configId;
    private String draftHash;
    private String activeReleaseId;
    private String targetHash;
    private String riskLevel;
    private String reason;
    private String ticketRef;
    private Map<String, Object> impact;
    private String applicantId;
    private String applicantName;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private Boolean reviewRequired;
    private String status;
    private String reviewerId;
    private String reviewerName;
    private String reviewComment;
    private LocalDateTime reviewedAt;
    private String releaseId;
    private LocalDateTime publishedAt;
    private LocalDateTime observationStart;
    private LocalDateTime observationEnd;
    private String observationStatus;
    private LocalDateTime rolledBackAt;
    private String rollbackReason;
    private String cancelledBy;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<UiHotfixObservationMetricDTO> metrics = new ArrayList<>();
}
