package com.workflow.openapi.infrastructure.persistence.record;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class IntegrationIdempotencyRecord {

    private String id;
    private String applicationId;
    private String operation;
    private String idempotencyKey;
    private String requestHash;
    private String status;
    private String resourceType;
    private String resourceId;
    private Integer responseStatus;
    private String responseBody;
    private Long fencingToken;
    private LocalDateTime processingStartedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
