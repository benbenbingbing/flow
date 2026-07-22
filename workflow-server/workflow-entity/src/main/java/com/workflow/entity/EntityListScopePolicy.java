package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体列表数据范围方案，只描述“哪些数据可见”。
 */
@Data
@TableName("entity_list_scope_policy")
public class EntityListScopePolicy {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String entityCode;

    private String policyKey;

    private String policyName;

    private String description;

    private String presetCode;

    private String filterConfig;

    private String status;

    private Integer enabled;

    private Integer version;

    private Integer reviewRequired;

    private String createdBy;

    @TableField("create_time")
    private LocalDateTime createdAt;

    @TableField("update_time")
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
