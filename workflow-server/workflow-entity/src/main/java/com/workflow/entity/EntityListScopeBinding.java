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

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String entityCode;

    private String policyId;

    private String listKey;

    private String matchConfig;

    private String ruleEffect;

    private Integer enabled;

    private LocalDateTime effectiveStartTime;

    private LocalDateTime effectiveEndTime;

    private String createdBy;

    @TableField("create_time")
    private LocalDateTime createdAt;

    @TableField("update_time")
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
