package com.workflow.admin.audit.application;

import com.workflow.core.database.jdbc.JdbcWriteAttempt;
import com.workflow.admin.audit.domain.AuditLogPayload;
import com.workflow.admin.audit.infrastructure.persistence.record.SystemOperationLog;
import com.workflow.admin.audit.infrastructure.persistence.mapper.SystemOperationLogMapper;
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
    private final JdbcWriteAttempt writeAttempt;

    /**
     * 处理{@code persist}，并将结果传给后续步骤。
     *
     * @param payload 载荷，后续用于处理{@code persist}并传递处理结果
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(AuditLogPayload payload) {
        try {
            writeAttempt.execute(() -> operationLogMapper.insert(toLog(payload)));
        } catch (DuplicateKeyException ignored) {
            // event_id 唯一约束保证失败日志幂等。
        }
    }

    /**
     * 转换为日志；输出作为后续校验或处理的输入。
     *
     * @param payload 载荷，后续用于转换为日志并传递处理结果
     * @return 转换为后的日志结果，供调用方继续处理
     */
    static SystemOperationLog toLog(AuditLogPayload payload) {
        SystemOperationLog log = new SystemOperationLog();
        log.setEventId(payload.eventId());
        log.setOperationId(payload.operationId());
        log.setTraceId(payload.traceId());
        log.setParentOperationId(payload.parentOperationId());
        log.setSourceSystem(payload.sourceSystem());
        log.setSourceType(payload.sourceType());
        log.setSourceId(payload.sourceId());
        log.setSourceEventId(payload.sourceEventId());
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
