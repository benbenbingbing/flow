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
 * 流程终止运行时服务
 * 负责流程发起人主动终止流程实例，并同步清理待办、回写实体状态、记录操作日志与团队记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessTerminationService {

    /** Flowable 运行时服务，删除流程实例 */
    private final RuntimeService runtimeService;
    /** Flowable 历史服务，查询历史实例 */
    private final HistoryService historyService;
    /** 动态表服务，获取实体动态表名 */
    private final DynamicTableService dynamicTableService;
    /** 动态实体数据 Mapper，更新实体状态 */
    private final EntityDataDynamicMapper entityDataDynamicMapper;
    /** 实体状态 Mapper，查询终止状态码 */
    private final EntityStatusMapper entityStatusMapper;
    /** 流程操作日志 Mapper，记录终止日志 */
    private final ProcessOperationLogMapper operationLogMapper;
    /** 流程任务服务，删除流程实例下的待办 */
    private final ProcessTaskService processTaskService;
    /** 用户服务，获取操作人显示名 */
    private final SysUserService sysUserService;
    /** 实体记录团队服务，记录终止事件 */
    private final com.workflow.service.EntityRecordTeamService entityRecordTeamService;

    /**
     * 终止流程实例。
     * <p>
     * 校验流程存在性、发起人权限后，删除流程实例、清理待办、记录终止日志、回写实体终止状态。
     *
     * @param processInstanceId 流程实例ID
     * @param userId            操作用户ID（需为发起人）
     * @param reason            终止原因，为空时默认"发起人主动终止"
     * @return 操作结果，成功返回 success，失败返回错误码与信息
     */
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

    /**
     * 写入流程终止操作日志。
     *
     * @param processInstanceId 流程实例ID
     * @param userId            操作人ID
     * @param deleteReason      终止原因
     */
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

    /**
     * 更新实体数据为终止状态，并清空当前任务信息。
     *
     * @param entityCode   实体编码
     * @param entityDataId 实体数据ID
     */
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

    /**
     * 获取实体"已终止"对应的状态码，无配置时回退为 TERMINATED。
     *
     * @param entityCode 实体编码
     * @return 终止状态码
     */
    private String getTerminatedStatus(String entityCode) {
        List<EntityStatus> statuses = entityStatusMapper.findByCategory(entityCode, "TERMINATED");
        return statuses != null && !statuses.isEmpty() ? statuses.get(0).getStatusCode() : "TERMINATED";
    }
}
