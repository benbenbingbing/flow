package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据范围方案的适用对象和列表绑定。
 */
@Data
@TableName("entity_list_scope_binding")
public class EntityListScopeBinding {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 实体编码 */
    private String entityCode;

    /** 数据范围方案ID */
    private String policyId;

    /** 列表标识 */
    private String listKey;

    /** 匹配条件配置（JSON，描述方案适用对象） */
    private String matchConfig;

    /** 规则生效方式（如 ADDITIVE/OVERRIDE 等） */
    private String ruleEffect;

    /** 是否启用（0-否 1-是） */
    private Integer enabled;

    /** 生效开始时间 */
    private LocalDateTime effectiveStartTime;

    /** 生效结束时间 */
    private LocalDateTime effectiveEndTime;

    /** 创建人ID */
    private String createdBy;

    @TableField("create_time")
    private LocalDateTime createdAt;

    @TableField("update_time")
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
