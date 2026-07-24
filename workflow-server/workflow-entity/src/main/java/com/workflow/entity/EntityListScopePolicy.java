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

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 实体编码 */
    private String entityCode;

    /** 方案唯一标识 */
    private String policyKey;

    /** 方案名称 */
    private String policyName;

    /** 方案描述 */
    private String description;

    /** 预设编码（内置的快捷范围模板） */
    private String presetCode;

    /** 过滤条件配置（JSON，描述可见数据范围） */
    private String filterConfig;

    /** 状态（如 DRAFT/PUBLISHED/DISABLED） */
    private String status;

    /** 是否启用（0-否 1-是） */
    private Integer enabled;

    /** 版本号 */
    private Integer version;

    /** 是否需要审批生效（0-否 1-是） */
    private Integer reviewRequired;

    /** 创建人ID */
    private String createdBy;

    @TableField("create_time")
    private LocalDateTime createdAt;

    @TableField("update_time")
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
