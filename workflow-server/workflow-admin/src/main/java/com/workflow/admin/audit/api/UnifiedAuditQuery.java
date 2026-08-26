package com.workflow.admin.audit.api;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 统一审计时间线查询条件；所有标识均为精确匹配，避免模糊查询扩大数据暴露面。
 */
@Data
public class UnifiedAuditQuery {

    private int pageNum = 1;
    private int pageSize = 20;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
    private String operationId;
    private String traceId;
    private String module;
    private String result;
    private String targetType;
    private String targetId;
    private String sourceType;
    private String sourceId;
}
