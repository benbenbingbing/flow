package com.workflow.admin.identity.position.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户在组织节点担任职务的一段不可覆盖任职事实。
 *
 * <p>有效区间统一为 {@code [effectiveFrom, effectiveTo)}，撤销通过
 * revoked 字段保留历史，重新任职必须新增记录。</p>
 */
@Data
@TableName("sys_position_assignment")
public class SysPositionAssignment {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String positionId;
    private String organizationUnitId;
    private String userId;
    private Boolean isPrimary;
    private Integer sortOrder;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private LocalDateTime revokedAt;
    private String revokedBy;
    private String revokeReason;
    private Integer revision;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
