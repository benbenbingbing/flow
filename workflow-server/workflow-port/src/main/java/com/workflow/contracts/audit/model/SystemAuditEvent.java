package com.workflow.contracts.audit.model;

import com.workflow.contracts.audit.context.OperationContext;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 跨模块传递的不可变系统审计事件。
 *
 * @param eventId 事件ID，后续用于处理系统审计事件时定位或关联目标
 * @param operationId 操作ID，后续用于处理系统审计事件时定位或关联目标
 * @param traceId 追踪ID，后续用于处理系统审计事件时定位或关联目标
 * @param parentOperationId 父级操作ID，后续用于处理系统审计事件时定位或关联目标
 * @param sourcePointer 来源指针，保存在对象中供后续校验、查询或展示
 * @param module {@code module}，保存在对象中供后续校验、查询或展示
 * @param action 动作标识，决定后续系统审计事件采用的处理分支
 * @param operationName 操作名称，后续用于处理系统审计事件时匹配或展示
 * @param riskLevel 风险层级，保存在对象中供后续校验、查询或展示
 * @param result 结果，保存在对象中供后续校验、查询或展示
 * @param required 必填，保存在对象中供后续校验、查询或展示
 * @param operatorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
 * @param operatorName 用户名称，后续用于身份匹配或操作展示
 * @param operatorIp 操作人{@code ip}，保存在对象中供后续校验、查询或展示
 * @param userAgent 用户{@code agent}，保存在对象中供后续校验、查询或展示
 * @param requestMethod 请求{@code method}，保存在对象中供后续校验、查询或展示
 * @param requestPath 请求路径，保存在对象中供后续校验、查询或展示
 * @param targetType 目标类型标识，决定后续系统审计事件采用的处理分支
 * @param targetId 目标ID，后续用于处理系统审计事件时定位或关联目标
 * @param targetName 目标名称，后续用于处理系统审计事件时匹配或展示
 * @param summary 摘要，保存在对象中供后续校验、查询或展示
 * @param beforeData 之前数据，保存在对象中供后续校验、查询或展示
 * @param afterData 之后数据，保存在对象中供后续校验、查询或展示
 * @param changedFields 已变更字段，保存在对象中供后续校验、查询或展示
 * @param errorCode 错误编码，后续用于处理系统审计事件时定位或关联目标
 * @param errorMessage 错误消息，保存在对象中供后续校验、查询或展示
 * @param durationMs 时长{@code ms}，保存在对象中供后续校验、查询或展示
 * @param createdAt 已创建时间，后续用于判断有效期或展示该事件的发生时间
 */
public record SystemAuditEvent(
        String eventId,
        String operationId,
        String traceId,
        String parentOperationId,
        AuditSourcePointer sourcePointer,
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

    /**
     * 初始化系统审计事件，保存构造参数供后续方法使用。
     *
     * @param eventId 事件ID，后续用于初始化系统审计事件时定位或关联目标
     * @param operationId 操作ID，后续用于初始化系统审计事件时定位或关联目标
     * @param traceId 追踪ID，后续用于初始化系统审计事件时定位或关联目标
     * @param parentOperationId 父级操作ID，后续用于初始化系统审计事件时定位或关联目标
     * @param sourcePointer 来源指针，保存在对象中供后续校验、查询或展示
     * @param module {@code module}，保存在对象中供后续校验、查询或展示
     * @param action 动作标识，决定后续系统审计事件采用的处理分支
     * @param operationName 操作名称，后续用于初始化系统审计事件时匹配或展示
     * @param riskLevel 风险层级，保存在对象中供后续校验、查询或展示
     * @param result 结果，保存在对象中供后续校验、查询或展示
     * @param required 必填，保存在对象中供后续校验、查询或展示
     * @param operatorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param operatorName 用户名称，后续用于身份匹配或操作展示
     * @param operatorIp 操作人{@code ip}，保存在对象中供后续校验、查询或展示
     * @param userAgent 用户{@code agent}，保存在对象中供后续校验、查询或展示
     * @param requestMethod 请求{@code method}，保存在对象中供后续校验、查询或展示
     * @param requestPath 请求路径，保存在对象中供后续校验、查询或展示
     * @param targetType 目标类型标识，决定后续系统审计事件采用的处理分支
     * @param targetId 目标ID，后续用于初始化系统审计事件时定位或关联目标
     * @param targetName 目标名称，后续用于初始化系统审计事件时匹配或展示
     * @param summary 摘要，保存在对象中供后续校验、查询或展示
     * @param beforeData 之前数据，保存在对象中供后续校验、查询或展示
     * @param afterData 之后数据，保存在对象中供后续校验、查询或展示
     * @param changedFields 已变更字段，保存在对象中供后续校验、查询或展示
     * @param errorCode 错误编码，后续用于初始化系统审计事件时定位或关联目标
     * @param errorMessage 错误消息，保存在对象中供后续校验、查询或展示
     * @param durationMs 时长{@code ms}，保存在对象中供后续校验、查询或展示
     * @param createdAt 已创建时间，后续用于判断有效期或展示该事件的发生时间
     */
    public SystemAuditEvent {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(operationName, "operationName");
        Objects.requireNonNull(riskLevel, "riskLevel");
        Objects.requireNonNull(result, "result");
    }

    /**
     * 处理构建器，并将结果传给后续步骤。
     *
     * @return 处理后的构建器结果，供调用方继续处理
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 封装构建器相关能力和状态；供同一业务流程的后续处理使用。
     */
    public static final class Builder {
        private String eventId;
        private String operationId;
        private String traceId;
        private String parentOperationId;
        private AuditSourcePointer sourcePointer;
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

        /**
         * 初始化构建器，保存构造参数供后续方法使用。
         */
        private Builder() {
        }

        /**
         * 处理事件ID，并将结果传给后续步骤。
         *
         * @param value 待处理事件ID的原始输入，结果供调用方继续使用
         * @return 处理后的事件ID结果，供调用方继续处理
         */
        public Builder eventId(String value) { this.eventId = value; return this; }
        /**
         * 处理操作ID，并将结果传给后续步骤。
         *
         * @param value 待处理操作ID的原始输入，结果供调用方继续使用
         * @return 处理后的操作ID结果，供调用方继续处理
         */
        public Builder operationId(String value) { this.operationId = value; return this; }
        /**
         * 处理追踪ID，并将结果传给后续步骤。
         *
         * @param value 待处理追踪ID的原始输入，结果供调用方继续使用
         * @return 处理后的追踪ID结果，供调用方继续处理
         */
        public Builder traceId(String value) { this.traceId = value; return this; }
        /**
         * 处理父级操作ID，并将结果传给后续步骤。
         *
         * @param value 待处理父级操作ID的原始输入，结果供调用方继续使用
         * @return 处理后的父级操作ID结果，供调用方继续处理
         */
        public Builder parentOperationId(String value) { this.parentOperationId = value; return this; }
        /**
         * 处理来源指针，并将结果传给后续步骤。
         *
         * @param value 待处理来源指针的原始输入，结果供调用方继续使用
         * @return 处理后的来源指针结果，供调用方继续处理
         */
        public Builder sourcePointer(AuditSourcePointer value) { this.sourcePointer = value; return this; }
        /**
         * 处理操作上下文，并将结果传给后续步骤。
         *
         * @param value 待处理操作上下文的原始输入，结果供调用方继续使用
         * @return 处理后的操作上下文结果，供调用方继续处理
         */
        public Builder operationContext(OperationContext value) {
            if (value != null) {
                this.operationId = value.operationId();
                this.traceId = value.traceId();
                this.parentOperationId = value.parentOperationId();
                this.sourcePointer = value.sourcePointer();
            }
            return this;
        }
        /**
         * 处理{@code module}，并将结果传给后续步骤。
         *
         * @param value 待处理{@code module}的原始输入，结果供调用方继续使用
         * @return 处理后的{@code module}结果，供调用方继续处理
         */
        public Builder module(AuditModule value) { this.module = value; return this; }
        /**
         * 处理动作，并将结果传给后续步骤。
         *
         * @param value 待处理动作的原始输入，结果供调用方继续使用
         * @return 处理后的动作结果，供调用方继续处理
         */
        public Builder action(AuditAction value) { this.action = value; return this; }
        /**
         * 处理操作名称，并将结果传给后续步骤。
         *
         * @param value 待处理操作名称的原始输入，结果供调用方继续使用
         * @return 处理后的操作名称结果，供调用方继续处理
         */
        public Builder operationName(String value) { this.operationName = value; return this; }
        /**
         * 处理风险层级，并将结果传给后续步骤。
         *
         * @param value 待处理风险层级的原始输入，结果供调用方继续使用
         * @return 处理后的风险层级结果，供调用方继续处理
         */
        public Builder riskLevel(AuditRiskLevel value) { this.riskLevel = value; return this; }
        /**
         * 处理结果，并将结果传给后续步骤。
         *
         * @param value 待处理结果的原始输入，结果供调用方继续使用
         * @return 处理后的结果，供调用方继续处理
         */
        public Builder result(AuditResult value) { this.result = value; return this; }
        /**
         * 处理必填，并将结果传给后续步骤。
         *
         * @param value 待处理必填的原始输入，结果供调用方继续使用
         * @return 处理后的必填结果，供调用方继续处理
         */
        public Builder required(boolean value) { this.required = value; return this; }
        /**
         * 处理操作人ID，并将结果传给后续步骤。
         *
         * @param value 待处理操作人ID的原始输入，结果供调用方继续使用
         * @return 处理后的操作人ID结果，供调用方继续处理
         */
        public Builder operatorId(String value) { this.operatorId = value; return this; }
        /**
         * 处理操作人名称，并将结果传给后续步骤。
         *
         * @param value 待处理操作人名称的原始输入，结果供调用方继续使用
         * @return 处理后的操作人名称结果，供调用方继续处理
         */
        public Builder operatorName(String value) { this.operatorName = value; return this; }
        /**
         * 处理操作人{@code ip}，并将结果传给后续步骤。
         *
         * @param value 待处理操作人{@code ip}的原始输入，结果供调用方继续使用
         * @return 处理后的操作人{@code ip}结果，供调用方继续处理
         */
        public Builder operatorIp(String value) { this.operatorIp = value; return this; }
        /**
         * 处理用户{@code agent}，并将结果传给后续步骤。
         *
         * @param value 待处理用户{@code agent}的原始输入，结果供调用方继续使用
         * @return 处理后的用户{@code agent}结果，供调用方继续处理
         */
        public Builder userAgent(String value) { this.userAgent = value; return this; }
        /**
         * 处理请求{@code method}，并将结果传给后续步骤。
         *
         * @param value 待处理请求{@code method}的原始输入，结果供调用方继续使用
         * @return 处理后的请求{@code method}结果，供调用方继续处理
         */
        public Builder requestMethod(String value) { this.requestMethod = value; return this; }
        /**
         * 处理请求路径，并将结果传给后续步骤。
         *
         * @param value 待处理请求路径的原始输入，结果供调用方继续使用
         * @return 处理后的请求路径结果，供调用方继续处理
         */
        public Builder requestPath(String value) { this.requestPath = value; return this; }
        /**
         * 处理目标类型，并将结果传给后续步骤。
         *
         * @param value 待处理目标类型的原始输入，结果供调用方继续使用
         * @return 处理后的目标类型结果，供调用方继续处理
         */
        public Builder targetType(String value) { this.targetType = value; return this; }
        /**
         * 处理目标ID，并将结果传给后续步骤。
         *
         * @param value 待处理目标ID的原始输入，结果供调用方继续使用
         * @return 处理后的目标ID结果，供调用方继续处理
         */
        public Builder targetId(String value) { this.targetId = value; return this; }
        /**
         * 处理目标名称，并将结果传给后续步骤。
         *
         * @param value 待处理目标名称的原始输入，结果供调用方继续使用
         * @return 处理后的目标名称结果，供调用方继续处理
         */
        public Builder targetName(String value) { this.targetName = value; return this; }
        /**
         * 处理摘要，并将结果传给后续步骤。
         *
         * @param value 待处理摘要的原始输入，结果供调用方继续使用
         * @return 处理后的摘要结果，供调用方继续处理
         */
        public Builder summary(String value) { this.summary = value; return this; }
        /**
         * 处理之前数据，并将结果传给后续步骤。
         *
         * @param value 待处理之前数据的原始输入，结果供调用方继续使用
         * @return 处理后的之前数据结果，供调用方继续处理
         */
        public Builder beforeData(Object value) { this.beforeData = value; return this; }
        /**
         * 处理之后数据，并将结果传给后续步骤。
         *
         * @param value 待处理之后数据的原始输入，结果供调用方继续使用
         * @return 处理后的之后数据结果，供调用方继续处理
         */
        public Builder afterData(Object value) { this.afterData = value; return this; }
        /**
         * 处理已变更字段，并将结果传给后续步骤。
         *
         * @param value 待处理已变更字段的原始输入，结果供调用方继续使用
         * @return 处理后的已变更字段结果，供调用方继续处理
         */
        public Builder changedFields(Object value) { this.changedFields = value; return this; }
        /**
         * 处理错误编码，并将结果传给后续步骤。
         *
         * @param value 待处理错误编码的原始输入，结果供调用方继续使用
         * @return 处理后的错误编码结果，供调用方继续处理
         */
        public Builder errorCode(String value) { this.errorCode = value; return this; }
        /**
         * 处理错误消息，并将结果传给后续步骤。
         *
         * @param value 待处理错误消息的原始输入，结果供调用方继续使用
         * @return 处理后的错误消息结果，供调用方继续处理
         */
        public Builder errorMessage(String value) { this.errorMessage = value; return this; }
        /**
         * 处理时长{@code ms}，并将结果传给后续步骤。
         *
         * @param value 待处理时长{@code ms}的原始输入，结果供调用方继续使用
         * @return 处理后的时长{@code ms}结果，供调用方继续处理
         */
        public Builder durationMs(Long value) { this.durationMs = value; return this; }
        /**
         * 处理已创建时间，并将结果传给后续步骤。
         *
         * @param value 待处理已创建时间的原始输入，结果供调用方继续使用
         * @return 处理后的已创建时间结果，供调用方继续处理
         */
        public Builder createdAt(LocalDateTime value) { this.createdAt = value; return this; }

        /**
         * 构建构建器；结果供后续流程传递或持久化。
         *
         * @return 构建后的构建器结果，供调用方继续处理
         */
        public SystemAuditEvent build() {
            return new SystemAuditEvent(
                    eventId, operationId, traceId, parentOperationId,
                    sourcePointer, module, action, operationName, riskLevel, result,
                    required, operatorId, operatorName, operatorIp, userAgent,
                    requestMethod, requestPath, targetType, targetId, targetName,
                    summary, beforeData, afterData, changedFields, errorCode,
                    errorMessage, durationMs, createdAt);
        }
    }
}
