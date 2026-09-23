package com.workflow.admin.audit.application;

import java.time.LocalDateTime;

/**
 * 普通审计记录失败时发布的技术监控事件。
 *
 * @param eventId 事件ID，后续用于处理系统审计{@code technical}失败事件时定位或关联目标
 * @param operationName 操作名称，后续用于处理系统审计{@code technical}失败事件时匹配或展示
 * @param phase {@code phase}，保存在对象中供后续校验、查询或展示
 * @param exceptionType 异常类型标识，决定后续系统审计{@code technical}失败事件采用的处理分支
 * @param occurredAt {@code occurred}时间，后续用于判断有效期或展示该事件的发生时间
 */
public record SystemAuditTechnicalFailureEvent(
        String eventId,
        String operationName,
        String phase,
        String exceptionType,
        LocalDateTime occurredAt) {
}
