package com.workflow.admin.audit.api;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 系统审计日志组合查询条件。
 */
@Data
public class SystemAuditQuery {

    private int pageNum = 1;
    private int pageSize = 20;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
    private String module;
    private String operation;
    private String operator;
    private String result;
    private String riskLevel;
    private String targetType;
    private String targetId;
    private String traceId;
}
