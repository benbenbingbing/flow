package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据范围委托。
 */
@Data
@TableName("entity_list_scope_delegation")
public class EntityListScopeDelegation {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 实体编码 */
    private String entityCode;

    /** 委托人用户ID */
    private String fromUserId;

    /** 被委托人用户ID */
    private String toUserId;

    /** 委托的数据范围描述 */
    private String delegateScope;

    /** 关联的数据范围方案ID */
    private String policyId;

    /** 委托配置（JSON） */
    private String delegateConfig;

    /** 委托生效开始时间 */
    private LocalDateTime startTime;

    /** 委托生效结束时间 */
    private LocalDateTime endTime;

    /** 是否启用（0-否 1-是） */
    private Integer enabled;

    /** 创建人ID */
    private String createdBy;

    @TableField("create_time")
    private LocalDateTime createdAt;

    @TableField("update_time")
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
