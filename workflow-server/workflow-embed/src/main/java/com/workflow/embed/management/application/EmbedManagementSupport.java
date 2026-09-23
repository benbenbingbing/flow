package com.workflow.embed.management.application;

import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditResult;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.model.SystemAuditEvent;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.contracts.identity.model.CurrentActor;
import com.workflow.contracts.identity.port.CurrentActorPort;
import com.workflow.core.error.ForbiddenException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.util.StringUtils;

/** 管理用例共享的操作人和 required 审计辅助。 */
final class EmbedManagementSupport {

    /**
     * 初始化嵌入式管理支持，保存构造参数供后续方法使用。
     */
    private EmbedManagementSupport() {
    }

    /**
     * 校验并获取操作人；不满足约束时阻止后续处理。
     *
     * @param actorProvider 操作人提供者，供本方法校验并获取操作人时使用
     * @return 校验并获取后的操作人结果，供调用方继续处理
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     */
    static CurrentActor requireActor(CurrentActorPort actorProvider) {
        CurrentActor actor = actorProvider.current();
        if (actor == null || !StringUtils.hasText(actor.userId())) {
            throw new ForbiddenException("用户未登录");
        }
        return actor;
    }

    /**
     * 审计嵌入式管理支持；供后续追溯或审计使用。
     *
     * @param auditPort 审计端口，供本方法审计嵌入式管理支持时使用
     * @param actor 操作人，供本方法审计嵌入式管理支持时使用
     * @param action 动作标识，决定后续嵌入式管理支持采用的处理分支
     * @param operationName 操作名称，后续用于审计嵌入式管理支持时匹配或展示
     * @param targetType 目标类型标识，决定后续嵌入式管理支持采用的处理分支
     * @param targetId 目标ID，后续用于审计嵌入式管理支持时定位或关联目标
     * @param targetName 目标名称，后续用于审计嵌入式管理支持时匹配或展示
     * @param before 之前，作为 {@code beforeData} 的输入影响后续处理
     * @param after 之后，供本方法审计嵌入式管理支持时使用
     * @param now 当前时间，供本方法审计嵌入式管理支持时使用
     */
    static void audit(
            SystemAuditPort auditPort,
            CurrentActor actor,
            AuditAction action,
            String operationName,
            String targetType,
            String targetId,
            String targetName,
            Object before,
            Object after,
            LocalDateTime now) {
        auditPort.record(SystemAuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .module(AuditModule.INTEGRATION)
                .action(action)
                .operationName(operationName)
                .riskLevel(AuditRiskLevel.HIGH)
                .result(AuditResult.SUCCESS)
                .required(true)
                .operatorId(actor.userId())
                .operatorName(actor.username())
                .targetType(targetType)
                .targetId(targetId)
                .targetName(targetName)
                .beforeData(before)
                .afterData(after)
                .createdAt(now)
                .build());
    }
}
