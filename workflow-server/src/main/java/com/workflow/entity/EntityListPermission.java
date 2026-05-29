package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体列表数据权限规则
 */
@Data
@TableName("entity_list_permission")
public class EntityListPermission {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 实体编码 */
    @TableField("entity_code")
    private String entityCode;

    /** 规则名称 */
    @TableField("rule_name")
    private String ruleName;

    /** 优先级，数字越大越优先 */
    @TableField("priority")
    private Integer priority;

    /** 是否启用（0否/1是） */
    @TableField("enabled")
    private Integer enabled;

    /** 匹配条件配置JSON */
    @TableField("match_config")
    private String matchConfig;

    /** 数据过滤配置JSON */
    @TableField("filter_config")
    private String filterConfig;

    /** 规则叠加方式：UNION(并集)/INTERSECT(交集) */
    @TableField("combine_mode")
    private String combineMode;

    /** 创建人 */
    @TableField("created_by")
    private String createdBy;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /** 是否删除（0否/1是） */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
