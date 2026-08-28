package com.workflow.admin.identity.position.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 已成功提交的批量任命幂等结果。
 */
@Data
@TableName("sys_position_assignment_batch")
public class SysPositionAssignmentBatch {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String idempotencyKey;
    private String requestHash;
    private String assignmentIdsJson;
    private String createdBy;
    private LocalDateTime createTime;
}
