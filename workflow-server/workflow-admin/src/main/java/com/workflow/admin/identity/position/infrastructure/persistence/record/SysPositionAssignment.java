package com.workflow.admin.identity.position.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 当前组织职务人员关系；移除时直接删除，不维护时间区间或任职履历。 */
@Data
@TableName("sys_position_assignment")
public class SysPositionAssignment {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String positionId;
    private String organizationUnitId;
    private String userId;
    private Integer revision;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
