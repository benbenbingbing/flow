package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("process_cc_outbox")
public class ProcessCcOutbox {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String ccRecordId;
    private String channel;
    private String payload;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime sentTime;
}
