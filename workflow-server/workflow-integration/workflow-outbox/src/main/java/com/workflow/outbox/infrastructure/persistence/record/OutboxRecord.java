package com.workflow.outbox.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通用数据库 Outbox 持久化记录。
 */
@Data
@TableName("workflow_outbox_event")
public class OutboxRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String topic;
    private String eventKey;
    private String aggregateType;
    private String aggregateId;
    private String payloadDocument;
    private String status;
    private String ownerId;
    private Long leaseToken;
    private LocalDateTime leaseUntil;
    private Integer retryCount;
    private Integer maxRetries;
    private LocalDateTime nextRetryTime;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime processedTime;
}
