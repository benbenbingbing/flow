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

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 实体编码 */
    private String entityCode;

    /** 列表标识 */
    private String listKey;

    /** 操作人用户ID */
    private String userId;

    /** 操作类型（如 grant/revoke/delegate 等） */
    private String operation;

    /** 操作结果（如 success/failure） */
    private String result;

    /** 操作明细（JSON） */
    private String detailJson;

    /** 操作时间 */
    private LocalDateTime createTime;
}
