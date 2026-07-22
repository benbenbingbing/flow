package com.workflow.process.runtime;

import com.workflow.common.Result;
import com.workflow.entity.EntityStatus;
import com.workflow.entity.ProcessOperationLog;
import com.workflow.mapper.EntityDataDynamicMapper;
import com.workflow.mapper.EntityStatusMapper;
import com.workflow.mapper.ProcessOperationLogMapper;
import com.workflow.service.DynamicTableService;
import com.workflow.service.ProcessTaskService;
import com.workflow.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程终止运行时。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessTerminationService {

    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final DynamicTableService dynamicTableService;
    private final EntityDataDynamicMapper entityDataDynamicMapper;
    private final EntityStatusMapper entityStatusMapper;
    private final ProcessOperationLogMapper operationLogMapper;
    private final ProcessTaskService processTaskService;
    private final SysUserService sysUserService;
    private final com.workflow.service.EntityRecordTeamService entityRecordTeamService;

    @Transactional(rollbackFor = Exception.class)
    public Result<Void> terminateProcess(String processInstanceId, String userId, String reason) {
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (processInstance == null) {
            HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (historicInstance == null) {
                return Result.error(404, "流程实例不存在");
            }
            if (historicInstance.getEndTime() != null) {
                return Result.error(400, "流程已结束，无法终止");
            }
        }

        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (historicInstance != null && !userId.equals(historicInstance.getStartUserId())) {
            return Result.error(403, "只有发起人可以终止流程");
        }

        String entityCode = null;
        String entityDataId = null;
        try {
            entityCode = (String) runtimeService.getVariable(processInstanceId, "entityCode");
            entityDataId = (String) runtimeService.getVariable(processInstanceId, "entityDataId");
        } catch (Exception e) {
            log.warn("终止前获取流程变量失败: processInstanceId={}", processInstanceId, e);
        }

        try {
            String deleteReason = (reason != null && !reason.isEmpty()) ? reason : "发起人主动终止";
            runtimeService.deleteProcessInstance(processInstanceId, deleteReason);
            processTaskService.deleteTasksByProcessInstance(processInstanceId);
            writeTerminateLog(processInstanceId, userId, deleteReason);
            updateEntityStatus(entityCode, entityDataId);
            if (entityCode != null && entityDataId != null) {
                entityRecordTeamService.record(
                        entityCode,
                        entityDataId,
                        "TERMINATE",
                        deleteReason,
                        processInstanceId,
                        null);
            }
            log.info("流程终止成功: processInstanceId={}, userId={}, reason={}", processInstanceId, userId, deleteReason);
            return Result.success(null);
        } catch (Exception e) {
            log.error("流程终止失败: processInstanceId={}, userId={}", processInstanceId, userId, e);
            return Result.error(500, "流程终止失败: " + e.getMessage());
        }
    }

    private void writeTerminateLog(String processInstanceId, String userId, String deleteReason) {
        try {
            ProcessOperationLog log = new ProcessOperationLog();
            log.setProcessInstanceId(processInstanceId);
            log.setOperationType("TERMINATE");
            log.setOperatorId(userId);
            String operatorName = sysUserService.getDisplayName(userId);
            log.setOperatorName(operatorName);
            log.setOperationTime(LocalDateTime.now());
            log.setOperationComment(deleteReason);
            operationLogMapper.insert(log);
        } catch (Exception e) {
            log.warn("记录终止日志失败", e);
        }
    }

    private void updateEntityStatus(String entityCode, String entityDataId) {
        try {
            if (entityCode == null || entityDataId == null) {
                return;
            }
            String tableName = dynamicTableService.getTableName(entityCode);
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("id", entityDataId);
            updateData.put("process_end_time", LocalDateTime.now());
            updateData.put("update_time", LocalDateTime.now());
            updateData.put("status", getTerminatedStatus(entityCode));
            entityDataDynamicMapper.update(tableName, updateData);
            entityDataDynamicMapper.updateCurrentTask(tableName, entityDataId, null, null, null);
            log.info("流程终止，已更新实体数据状态: entityCode={}, entityDataId={}, status={}",
                    entityCode, entityDataId, updateData.get("status"));
        } catch (Exception e) {
            log.warn("终止流程后更新实体数据状态失败: entityCode={}, entityDataId={}", entityCode, entityDataId, e);
        }
    }

    private String getTerminatedStatus(String entityCode) {
        List<EntityStatus> statuses = entityStatusMapper.findByCategory(entityCode, "TERMINATED");
        return statuses != null && !statuses.isEmpty() ? statuses.get(0).getStatusCode() : "TERMINATED";
    }
}
