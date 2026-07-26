package com.workflow.contracts.audit;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 跨模块传递的不可变系统审计事件。
 */
public record SystemAuditEvent(
        String eventId,
        String traceId,
        AuditModule module,
        AuditAction action,
        String operationName,
        AuditRiskLevel riskLevel,
        AuditResult result,
        boolean required,
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
        Object beforeData,
        Object afterData,
        Object changedFields,
        String errorCode,
        String errorMessage,
        Long durationMs,
        LocalDateTime createdAt) {

    public SystemAuditEvent {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(operationName, "operationName");
        Objects.requireNonNull(riskLevel, "riskLevel");
        Objects.requireNonNull(result, "result");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String eventId;
        private String traceId;
        private AuditModule module;
        private AuditAction action;
        private String operationName;
        private AuditRiskLevel riskLevel = AuditRiskLevel.MEDIUM;
        private AuditResult result = AuditResult.SUCCESS;
        private boolean required;
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
        private Object beforeData;
        private Object afterData;
        private Object changedFields;
        private String errorCode;
        private String errorMessage;
        private Long durationMs;
        private LocalDateTime createdAt;

        private Builder() {
        }

        public Builder eventId(String value) { this.eventId = value; return this; }
        public Builder traceId(String value) { this.traceId = value; return this; }
        public Builder module(AuditModule value) { this.module = value; return this; }
        public Builder action(AuditAction value) { this.action = value; return this; }
        public Builder operationName(String value) { this.operationName = value; return this; }
        public Builder riskLevel(AuditRiskLevel value) { this.riskLevel = value; return this; }
        public Builder result(AuditResult value) { this.result = value; return this; }
        public Builder required(boolean value) { this.required = value; return this; }
        public Builder operatorId(String value) { this.operatorId = value; return this; }
        public Builder operatorName(String value) { this.operatorName = value; return this; }
        public Builder operatorIp(String value) { this.operatorIp = value; return this; }
        public Builder userAgent(String value) { this.userAgent = value; return this; }
        public Builder requestMethod(String value) { this.requestMethod = value; return this; }
        public Builder requestPath(String value) { this.requestPath = value; return this; }
        public Builder targetType(String value) { this.targetType = value; return this; }
        public Builder targetId(String value) { this.targetId = value; return this; }
        public Builder targetName(String value) { this.targetName = value; return this; }
        public Builder summary(String value) { this.summary = value; return this; }
        public Builder beforeData(Object value) { this.beforeData = value; return this; }
        public Builder afterData(Object value) { this.afterData = value; return this; }
        public Builder changedFields(Object value) { this.changedFields = value; return this; }
        public Builder errorCode(String value) { this.errorCode = value; return this; }
        public Builder errorMessage(String value) { this.errorMessage = value; return this; }
        public Builder durationMs(Long value) { this.durationMs = value; return this; }
        public Builder createdAt(LocalDateTime value) { this.createdAt = value; return this; }

        public SystemAuditEvent build() {
            return new SystemAuditEvent(
                    eventId, traceId, module, action, operationName, riskLevel, result,
                    required, operatorId, operatorName, operatorIp, userAgent,
                    requestMethod, requestPath, targetType, targetId, targetName,
                    summary, beforeData, afterData, changedFields, errorCode,
                    errorMessage, durationMs, createdAt);
        }
    }
}
