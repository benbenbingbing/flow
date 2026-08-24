package com.workflow.entity.definition.api.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 实体结构发布操作及发布前风险评估。
 */
@Data
public class EntitySchemaOperationDTO {

    private String id;
    private String entityId;
    private String entityCode;
    private String status;
    private String planHash;
    private String idempotencyKey;
    private List<String> plan = new ArrayList<>();
    private String targetFingerprint;
    private String actualFingerprint;
    private List<String> drift = new ArrayList<>();
    private List<String> uniqueConflicts = new ArrayList<>();
    private String riskLevel;
    private String riskReason;
    private Long estimatedRows;
    private String lockRisk;
    private String releaseWindow;
    private Integer attemptCount;
    private String errorMessage;
    private Boolean retryable;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
