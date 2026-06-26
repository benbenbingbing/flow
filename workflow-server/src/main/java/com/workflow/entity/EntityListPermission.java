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

    /** 适用列表配置ID，为空表示对该实体所有列表生效 */
    @TableField("list_config_id")
    private String listConfigId;

    /** 规则效果：ALLOW(放行)/DENY(拒绝) */
    @TableField("rule_effect")
    private String ruleEffect;

    /** 命中后是否停止评估更低优先级规则：0否/1是 */
    @TableField("stop_processing")
    private Integer stopProcessing;

    /** 创建人 */
    @TableField("created_by")
    private String createdBy;

    /** 创建时间 */
        @TableField("create_time")
    private LocalDateTime createdAt;

    /** 更新时间 */
        @TableField("update_time")
    private LocalDateTime updatedAt;

    /** 是否删除（0否/1是） */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
