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

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String entityCode;

    private String fromUserId;

    private String toUserId;

    private String delegateScope;

    private String policyId;

    private String delegateConfig;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer enabled;

    private String createdBy;

    @TableField("create_time")
    private LocalDateTime createdAt;

    @TableField("update_time")
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
