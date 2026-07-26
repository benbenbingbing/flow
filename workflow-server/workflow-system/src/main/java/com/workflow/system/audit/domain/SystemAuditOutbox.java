package com.workflow.system.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统审计可靠投递 Outbox。
 */
@Data
@TableName("system_audit_outbox")
public class SystemAuditOutbox {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String eventId;
    private String payloadJson;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime processedTime;
}
