package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据权限委托
 */
@Data
@TableName("entity_list_permission_delegate")
public class EntityListPermissionDelegate {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 实体编码（为空表示全部实体） */
    @TableField("entity_code")
    private String entityCode;

    /** 委托方用户ID */
    @TableField("from_user_id")
    private String fromUserId;

    /** 受托方用户ID */
    @TableField("to_user_id")
    private String toUserId;

    /** 委托范围：ALL(全部)/PERSONAL(仅本人数据)/CONDITION(按条件) */
    @TableField("delegate_scope")
    private String delegateScope;

    /** 委托范围配置JSON */
    @TableField("delegate_config")
    private String delegateConfig;

    /** 委托开始时间 */
    @TableField("start_time")
    private LocalDateTime startTime;

    /** 委托结束时间 */
    @TableField("end_time")
    private LocalDateTime endTime;

    /** 是否启用（0否/1是） */
    @TableField("enabled")
    private Integer enabled;

    /** 创建时间 */
        @TableField("create_time")
    private LocalDateTime createdAt;
}
