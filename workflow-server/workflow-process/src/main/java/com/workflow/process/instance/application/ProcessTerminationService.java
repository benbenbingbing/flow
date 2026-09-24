package com.workflow.process.instance.application;

import com.workflow.process.status.application.ProcessEndReason;

import com.workflow.core.logging.LogValue;
import com.workflow.core.result.Result;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.contracts.entity.port.EntityRecordPort;
import com.workflow.contracts.identity.port.IdentityDirectoryPort;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.task.application.ProcessTaskService;
import com.workflow.process.task.application.operation.NodeOperationCapabilityService;
import com.workflow.process.task.application.operation.NodeOperationDecisionService;
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
    private final NodeOperationCapabilityService nodeOperationCapabilityService;

    /**
     * 终止流程实例。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param reason 原因，作为 {@code nodeOperationCapabilityService.requireTerminateAllowed} 的输入影响后续处理
     * @return 终止后的流程结果，供调用方继续处理
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
        String startUserId = historicInstance != null
                ? historicInstance.getStartUserId()
                : processInstance.getStartUserId();
        if (!userId.equals(startUserId)) {
            return Result.error(403, "只有发起人可以终止流程");
        }

        // 权限校验下沉到事务服务，确保任何调用路径都无法绕过节点终止开关。
        nodeOperationCapabilityService.requireTerminateAllowed(
                processInstanceId,
                NodeOperationDecisionService.CheckContext.ofReason(reason));

        // 元数据读取失败必须阻止终止，不能跳过实体目标状态校验后继续删除实例。
        String entityCode = (String) runtimeService.getVariable(processInstanceId, "entityCode");
        String entityDataId = (String) runtimeService.getVariable(processInstanceId, "entityDataId");

        if (org.springframework.util.StringUtils.hasText(entityCode)) {
            entityRecordPort.requireProcessEndStatus(entityCode, "TERMINATED");
        }

        try {
            String deleteReason = reason != null && !reason.isEmpty()
                    ? reason
                    : "发起人主动终止";
            runtimeService.deleteProcessInstance(
                    processInstanceId,
                    ProcessEndReason.encode("TERMINATED", deleteReason));
            processTaskService.deleteTasksByProcessInstance(
                    processInstanceId);
            writeTerminateLog(
                    processInstanceId, userId, deleteReason);
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
                    processInstanceId, userId, deleteReason);
            return Result.success(null);
        } catch (Exception exception) {
            // 返回业务错误也必须回滚已推进的引擎与实体状态，避免吞异常后提交半成品。
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
                org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
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

    /**
     * 写入终止日志；后续读取或执行将使用更新后的状态。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param deleteReason 删除原因，作为 {@code operationLog.setOperationComment} 的输入影响后续处理
     */
    private void writeTerminateLog(
            String processInstanceId,
            String userId,
            String deleteReason) {
        ProcessOperationLog operationLog = new ProcessOperationLog();
        operationLog.setProcessInstanceId(processInstanceId);
        operationLog.setOperationType("TERMINATE");
        operationLog.setOperatorId(userId);
        operationLog.setOperatorName(
                identityDirectoryPort.getDisplayName(userId));
        operationLog.setOperationTime(LocalDateTime.now());
        operationLog.setOperationComment(deleteReason);
        // 日志是审批历史的事实来源，失败必须让外层事务回滚，不能吞掉异常。
        operationLogMapper.insert(operationLog);
    }

}
