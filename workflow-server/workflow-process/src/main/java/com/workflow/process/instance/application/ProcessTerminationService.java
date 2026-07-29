package com.workflow.process.instance.application;

import com.workflow.core.logging.LogValue;
import com.workflow.core.result.Result;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.contracts.entity.EntityRecordPort;
import com.workflow.contracts.identity.IdentityDirectoryPort;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.task.application.ProcessTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 流程终止运行时服务。
 *
 * <p>负责校验发起人权限、删除流程实例、清理待办、记录流程操作日志，
 * 并通过跨模块端口回写实体状态和实体活动记录。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessTerminationService {

    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final ProcessOperationLogMapper operationLogMapper;
    private final ProcessTaskService processTaskService;
    private final IdentityDirectoryPort identityDirectoryPort;
    private final EntityRecordPort entityRecordPort;

    /**
     * 终止流程实例。
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.PROCESS,
            action = AuditAction.TERMINATE,
            operation = "终止流程实例",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "PROCESS_INSTANCE",
            targetIdArg = 0)
    public Result<Void> terminateProcess(
            String processInstanceId,
            String userId,
            String reason) {
        ProcessInstance processInstance = runtimeService
                .createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (processInstance == null) {
            HistoricProcessInstance historicInstance = historyService
                    .createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (historicInstance == null) {
                return Result.error(404, "流程实例不存在");
            }
            if (historicInstance.getEndTime() != null) {
                return Result.error(400, "流程已结束，无法终止");
            }
        }

        HistoricProcessInstance historicInstance = historyService
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (historicInstance != null
                && !userId.equals(historicInstance.getStartUserId())) {
            return Result.error(403, "只有发起人可以终止流程");
        }

        String entityCode = null;
        String entityDataId = null;
        try {
            entityCode = (String) runtimeService.getVariable(
                    processInstanceId,
                    "entityCode");
            entityDataId = (String) runtimeService.getVariable(
                    processInstanceId,
                    "entityDataId");
        } catch (Exception exception) {
            log.warn(
                    "终止前获取流程变量失败: processInstanceId={}",
                    LogValue.safe(processInstanceId),
                    LogValue.failureType(exception));
        }

        try {
            String deleteReason = reason != null && !reason.isEmpty()
                    ? reason
                    : "发起人主动终止";
            runtimeService.deleteProcessInstance(
                    processInstanceId,
                    deleteReason);
            processTaskService.deleteTasksByProcessInstance(
                    processInstanceId);
            writeTerminateLog(
                    LogValue.safe(processInstanceId),
                    LogValue.safe(userId),
                    LogValue.safe(deleteReason));
            updateEntityStatus(entityCode, entityDataId);
            if (entityCode != null && entityDataId != null) {
                entityRecordPort.recordActivity(
                        entityCode,
                        entityDataId,
                        "TERMINATE",
                        deleteReason,
                        processInstanceId,
                        null);
            }
            log.info(
                    "流程终止成功: processInstanceId={}, userId={}, reason={}",
                    processInstanceId,
                    userId,
                    deleteReason);
            return Result.success(null);
        } catch (Exception exception) {
            log.error(
                    "流程终止失败: processInstanceId={}, userId={}",
                    LogValue.safe(processInstanceId),
                    LogValue.safe(userId),
                    LogValue.failureType(exception));
            return Result.error(
                    500,
                    "流程终止失败: " + exception.getMessage());
        }
    }

    private void writeTerminateLog(
            String processInstanceId,
            String userId,
            String deleteReason) {
        try {
            ProcessOperationLog operationLog = new ProcessOperationLog();
            operationLog.setProcessInstanceId(processInstanceId);
            operationLog.setOperationType("TERMINATE");
            operationLog.setOperatorId(userId);
            operationLog.setOperatorName(
                    identityDirectoryPort.getDisplayName(userId));
            operationLog.setOperationTime(LocalDateTime.now());
            operationLog.setOperationComment(deleteReason);
            operationLogMapper.insert(operationLog);
        } catch (Exception exception) {
            log.warn("记录终止日志失败", exception);
        }
    }

    private void updateEntityStatus(
            String entityCode,
            String entityDataId) {
        try {
            if (entityCode == null || entityDataId == null) {
                return;
            }
            entityRecordPort.markProcessEnded(
                    entityCode,
                    entityDataId,
                    "TERMINATED",
                    "TERMINATED");
            log.info(
                    "流程终止，已更新实体数据状态: entityCode={}, entityDataId={}",
                    entityCode,
                    entityDataId);
        } catch (Exception exception) {
            log.warn(
                    "终止流程后更新实体数据状态失败: entityCode={}, entityDataId={}",
                    entityCode,
                    entityDataId,
                    exception);
        }
    }
}
