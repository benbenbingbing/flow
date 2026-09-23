package com.workflow.admin.audit.domain;

import java.time.LocalDateTime;

/**
 * 已完成脱敏和长度限制、可安全写入 Outbox 的审计载荷。
 *
 * @param eventId 事件ID，后续用于处理审计日志载荷时定位或关联目标
 * @param operationId 操作ID，后续用于处理审计日志载荷时定位或关联目标
 * @param traceId 追踪ID，后续用于处理审计日志载荷时定位或关联目标
 * @param parentOperationId 父级操作ID，后续用于处理审计日志载荷时定位或关联目标
 * @param sourceSystem 来源系统，保存在对象中供后续校验、查询或展示
 * @param sourceType 来源类型标识，决定后续审计日志载荷采用的处理分支
 * @param sourceId 来源ID，后续用于处理审计日志载荷时定位或关联目标
 * @param sourceEventId 来源事件ID，后续用于处理审计日志载荷时定位或关联目标
 * @param moduleCode {@code module}编码，后续用于处理审计日志载荷时定位或关联目标
 * @param operationCode 操作编码，后续用于处理审计日志载荷时定位或关联目标
 * @param operationName 操作名称，后续用于处理审计日志载荷时匹配或展示
 * @param riskLevel 风险层级，保存在对象中供后续校验、查询或展示
 * @param result 结果，保存在对象中供后续校验、查询或展示
 * @param operatorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
 * @param operatorName 用户名称，后续用于身份匹配或操作展示
 * @param operatorIp 操作人{@code ip}，保存在对象中供后续校验、查询或展示
 * @param userAgent 用户{@code agent}，保存在对象中供后续校验、查询或展示
 * @param requestMethod 请求{@code method}，保存在对象中供后续校验、查询或展示
 * @param requestPath 请求路径，保存在对象中供后续校验、查询或展示
 * @param targetType 目标类型标识，决定后续审计日志载荷采用的处理分支
 * @param targetId 目标ID，后续用于处理审计日志载荷时定位或关联目标
 * @param targetName 目标名称，后续用于处理审计日志载荷时匹配或展示
 * @param summary 摘要，保存在对象中供后续校验、查询或展示
 * @param beforeJson 之前JSON，保存在对象中供后续校验、查询或展示
 * @param afterJson 之后JSON，保存在对象中供后续校验、查询或展示
 * @param changedFieldsJson 已变更字段JSON，保存在对象中供后续校验、查询或展示
 * @param payloadTruncated 载荷{@code truncated}，保存在对象中供后续校验、查询或展示
 * @param errorCode 错误编码，后续用于处理审计日志载荷时定位或关联目标
 * @param errorMessage 错误消息，保存在对象中供后续校验、查询或展示
 * @param durationMs 时长{@code ms}，保存在对象中供后续校验、查询或展示
 * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
 */
public record AuditLogPayload(
        String eventId,
        String operationId,
        String traceId,
        String parentOperationId,
        String sourceSystem,
        String sourceType,
        String sourceId,
        String sourceEventId,
        String moduleCode,
        String operationCode,
        String operationName,
        String riskLevel,
        String result,
        String operatorId,
        String operatorName,
        String operatorIp,
        String userAgent,
        String requestMethod,
        String requestPath,
        String targetType,
        String targetId,
        String targetName,
        String summary,
        String beforeJson,
        String afterJson,
        String changedFieldsJson,
        boolean payloadTruncated,
        String errorCode,
        String errorMessage,
        Long durationMs,
        LocalDateTime createTime) {
}
