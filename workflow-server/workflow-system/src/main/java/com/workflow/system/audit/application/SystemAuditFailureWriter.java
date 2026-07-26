package com.workflow.system.audit.application;

import com.workflow.system.audit.domain.AuditLogPayload;
import com.workflow.system.audit.domain.SystemOperationLog;
import com.workflow.system.audit.infrastructure.SystemOperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 使用独立事务保存失败审计，避免随原业务事务回滚。
 */
@Service
@RequiredArgsConstructor
public class SystemAuditFailureWriter {

    private final SystemOperationLogMapper operationLogMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(AuditLogPayload payload) {
        try {
            operationLogMapper.insert(toLog(payload));
        } catch (DuplicateKeyException ignored) {
            // event_id 唯一约束保证失败日志幂等。
        }
    }

    static SystemOperationLog toLog(AuditLogPayload payload) {
        SystemOperationLog log = new SystemOperationLog();
        log.setEventId(payload.eventId());
        log.setTraceId(payload.traceId());
        log.setModuleCode(payload.moduleCode());
        log.setOperationCode(payload.operationCode());
        log.setOperationName(payload.operationName());
        log.setRiskLevel(payload.riskLevel());
        log.setResult(payload.result());
        log.setOperatorId(payload.operatorId());
        log.setOperatorName(payload.operatorName());
        log.setOperatorIp(payload.operatorIp());
        log.setUserAgent(payload.userAgent());
        log.setRequestMethod(payload.requestMethod());
        log.setRequestPath(payload.requestPath());
        log.setTargetType(payload.targetType());
        log.setTargetId(payload.targetId());
        log.setTargetName(payload.targetName());
        log.setSummary(payload.summary());
        log.setBeforeJson(payload.beforeJson());
        log.setAfterJson(payload.afterJson());
        log.setChangedFieldsJson(payload.changedFieldsJson());
        log.setPayloadTruncated(payload.payloadTruncated() ? 1 : 0);
        log.setErrorCode(payload.errorCode());
        log.setErrorMessage(payload.errorMessage());
        log.setDurationMs(payload.durationMs());
        log.setCreateTime(payload.createTime());
        return log;
    }
}
