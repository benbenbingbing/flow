package com.workflow.admin.identity.position.infrastructure.persistence.record;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任职事实及职务、组织、用户展示字段的只读联表投影。
 */
@Data
public class PositionAssignmentViewRow {
    private String id;
    private String positionId;
    private String positionCode;
    private String positionName;
    private String organizationUnitId;
    private String organizationUnitName;
    private String organizationUnitType;
    private String businessLevelCode;
    private String userId;
    private String username;
    private String nickname;
    private Boolean isPrimary;
    private Integer sortOrder;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private LocalDateTime revokedAt;
    private String revokedBy;
    private String revokeReason;
    private Integer revision;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
