package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据范围安全审计日志。
 */
@Data
@TableName("entity_list_scope_audit_log")
public class EntityListScopeAuditLog {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String entityCode;

    private String listKey;

    private String userId;

    private String operation;

    private String result;

    private String detailJson;

    private LocalDateTime createTime;
}
