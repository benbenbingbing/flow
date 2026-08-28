package com.workflow.embed.management.application;

import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditResult;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.CurrentActorProvider;
import com.workflow.core.error.ForbiddenException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.util.StringUtils;

/** 管理用例共享的操作人和 required 审计辅助。 */
final class EmbedManagementSupport {

    private EmbedManagementSupport() {
    }

    static CurrentActor requireActor(CurrentActorProvider actorProvider) {
        CurrentActor actor = actorProvider.current();
        if (actor == null || !StringUtils.hasText(actor.userId())) {
            throw new ForbiddenException("用户未登录");
        }
        return actor;
    }

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
