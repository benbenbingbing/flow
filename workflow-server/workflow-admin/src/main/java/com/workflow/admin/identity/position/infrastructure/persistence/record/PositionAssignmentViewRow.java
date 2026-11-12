package com.workflow.admin.identity.position.infrastructure.persistence.record;

import lombok.Data;

import java.time.LocalDateTime;

/** 当前组织职务人员关系；移除时直接删除，不维护时间区间或任职履历。 */
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
    private Integer revision;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
