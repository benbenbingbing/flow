package com.workflow.system.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 只追加的系统关键操作审计日志。
 */
@Data
@TableName("system_operation_log")
public class SystemOperationLog {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String eventId;
    private String traceId;
    private String moduleCode;
    private String operationCode;
    private String operationName;
    private String riskLevel;
    private String result;
    private String operatorId;
    private String operatorName;
    private String operatorIp;
    private String userAgent;
    private String requestMethod;
    private String requestPath;
    private String targetType;
    private String targetId;
    private String targetName;
    private String summary;
    private String beforeJson;
    private String afterJson;
    private String changedFieldsJson;
    private Integer payloadTruncated;
    private String errorCode;
    private String errorMessage;
    private Long durationMs;
    private LocalDateTime createTime;
}
