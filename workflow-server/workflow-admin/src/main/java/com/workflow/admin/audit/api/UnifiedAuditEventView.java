package com.workflow.admin.audit.api;

import java.time.LocalDateTime;

/**
 * 已收敛敏感字段的统一审计只读投影。
 *
 * <p>不返回 before/after、请求地址、IP、User-Agent、错误堆栈等内容；来源详情
 * 必须回到来源模块并再次鉴权读取。</p>
 */
public record UnifiedAuditEventView(
        String id,
        String eventId,
        String operationId,
        String parentOperationId,
        String traceId,
        String module,
        String operationCode,
        String operationName,
        String result,
        String riskLevel,
        String operatorId,
        String operatorName,
        String targetType,
        String targetId,
        String targetName,
        String summary,
        String errorCode,
        Long durationMs,
        SourcePointer source,
        boolean payloadDetailsOmitted,
        LocalDateTime createdAt) {

    public record SourcePointer(
            String system,
            String type,
            String id,
            String eventId) {
    }
}
