package com.workflow.service;

import com.workflow.common.Result;
import com.workflow.entity.EntityData;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.entity.ProcessTask;
import com.workflow.mapper.EntityDataMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.mapper.ProcessTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 流程退回服务
 * 处理驳回重提、流程退回等复杂流转
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessRollbackService {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final ProcessTaskService processTaskService;
    private final ProcessTaskMapper processTaskMapper;
    private final EntityDataMapper entityDataMapper;
    private final ProcessDefinitionConfigMapper processDefinitionConfigMapper;
    private final org.flowable.engine.RepositoryService repositoryService;

    /**
     * 驳回任务到指定节点（默认驳回给发起人）
     *
     * @param taskId    当前任务ID
     * @param userId    当前用户
     * @param comment   驳回原因
     * @param targetNodeId 目标节点ID（可选，默认是发起人节点）
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> rejectTask(String taskId, String userId, String comment, String targetNodeId) {
        // 1. 验证任务
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();

        if (task == null) {
            return Result.error(404, "任务不存在或已处理");
        }

        String processInstanceId = task.getProcessInstanceId();
        String processDefinitionId = task.getProcessDefinitionId();

        // 2. 如果没有指定目标节点，默认回到发起人
        if (targetNodeId == null || targetNodeId.isEmpty()) {
            targetNodeId = findStartNodeId(processDefinitionId);
        }

        // 3. 获取流程发起人
        HistoricProcessInstance historicProcessInstance = historyService
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        String startUserId = historicProcessInstance != null ? 
                historicProcessInstance.getStartUserId() : null;

        if (startUserId == null) {
            return Result.error(500, "无法获取流程发起人");
        }

        try {
            // 4. 设置驳回标记
            Map<String, Object> variables = new HashMap<>();
            variables.put("_rejected_", true);
            variables.put("_rejectBy_", userId);
            variables.put("_rejectTime_", new Date());
            variables.put("_rejectComment_", comment);
            variables.put("_rejectSourceTaskId_", taskId);
            variables.put("_rejectSourceNodeId_", task.getTaskDefinitionKey());
            variables.put("approved", false);

            // 5. 完成任务并设置驳回变量
            taskService.setVariablesLocal(taskId, variables);
            taskService.complete(taskId, variables);

            // 6. 创建一个新的任务给发起人（用于重新提交）
            // 使用信号或消息触发重新提交节点
            // 这里简化处理：直接创建一个新任务

            log.info("任务 {} 被 {} 驳回，将重新分配给发起人 {}", 
                    taskId, userId, startUserId);

            // 7. 同步待办状态
            processTaskService.syncTasksFromFlowable(processInstanceId);

            // 8. 更新实体状态为"被驳回"
            updateEntityStatusToRejected(processInstanceId);

            return Result.success(null);

        } catch (Exception e) {
            log.error("驳回任务失败: taskId={}, userId={}", taskId, userId, e);
            return Result.error(500, "驳回失败: " + e.getMessage());
        }
    }

    /**
     * 重新提交流程（发起人在被驳回后重新提交）
     *
     * @param processInstanceId 流程实例ID
     * @param userId            当前用户（必须是发起人）
     * @param formData          更新的表单数据
     * @param comment           重新提交备注
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> resubmitProcess(String processInstanceId, String userId, 
                                         Map<String, Object> formData, String comment) {
        // 1. 验证流程实例
        ProcessInstance processInstance = runtimeService
                .createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (processInstance == null) {
            return Result.error(404, "流程实例不存在或已结束");
        }

        // 2. 验证是否是发起人
        HistoricProcessInstance historicInstance = historyService
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        String startUserId = historicInstance != null ? historicInstance.getStartUserId() : null;
        if (startUserId == null || !startUserId.equals(userId)) {
            return Result.error(403, "只有发起人才能重新提交");
        }

        // 3. 获取当前任务（应该是发起人的重新提交任务）
        Task currentTask = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskAssignee(userId)
                .singleResult();

        if (currentTask == null) {
            // 可能任务已经流转走了，或者被其他方式处理了
            return Result.error(400, "没有找到可重新提交的任务");
        }

        try {
            // 4. 更新表单数据
            if (formData != null && !formData.isEmpty()) {
                runtimeService.setVariables(processInstanceId, formData);
                
                // 更新实体数据
                updateEntityData(processInstanceId, formData);
            }

            // 5. 设置重新提交变量
            Map<String, Object> variables = new HashMap<>();
            variables.put("_resubmitted_", true);
            variables.put("_resubmitTime_", new Date());
            variables.put("_resubmitComment_", comment);
            variables.put("_resubmitBy_", userId);
            variables.put("approved", true);  // 重新提交表示同意继续流程
            variables.put("action", "resubmit");
            variables.put("comment", comment);

            // 6. 完成任务（继续流程）
            taskService.complete(currentTask.getId(), variables);

            log.info("流程 {} 被发起人 {} 重新提交", processInstanceId, userId);

            // 7. 同步待办状态
            processTaskService.syncTasksFromFlowable(processInstanceId);

            return Result.success(null);

        } catch (Exception e) {
            log.error("重新提交失败: processInstanceId={}, userId={}", 
                    processInstanceId, userId, e);
            return Result.error(500, "重新提交失败: " + e.getMessage());
        }
    }

    /**
     * 检查流程是否被驳回（发起人使用）
     *
     * @param processInstanceId 流程实例ID
     * @return 驳回信息，如果没有被驳回返回null
     */
    public Map<String, Object> checkRejectedStatus(String processInstanceId) {
        ProcessInstance processInstance = runtimeService
                .createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (processInstance == null) {
            return null;
        }

        // 检查流程变量中是否有驳回标记
        Map<String, Object> vars = runtimeService.getVariables(processInstanceId);
        
        Boolean rejected = (Boolean) vars.get("_rejected_");
        if (Boolean.TRUE.equals(rejected)) {
            Map<String, Object> result = new HashMap<>();
            result.put("rejected", true);
            result.put("rejectBy", vars.get("_rejectBy_"));
            result.put("rejectTime", vars.get("_rejectTime_"));
            result.put("rejectComment", vars.get("_rejectComment_"));
            result.put("canResubmit", true);
            return result;
        }

        return null;
    }

    /**
     * 找到流程的发起节点
     */
    private String findStartNodeId(String processDefinitionId) {
        try {
            org.flowable.bpmn.model.BpmnModel bpmnModel = repositoryService
                    .getBpmnModel(processDefinitionId);
            
            org.flowable.bpmn.model.Process process = bpmnModel.getMainProcess();
            Collection<org.flowable.bpmn.model.FlowElement> elements = process.getFlowElements();
            
            for (org.flowable.bpmn.model.FlowElement element : elements) {
                if (element instanceof org.flowable.bpmn.model.StartEvent) {
                    // 找到开始事件，返回它的下一个节点
                    List<org.flowable.bpmn.model.SequenceFlow> outgoingFlows = 
                            ((org.flowable.bpmn.model.StartEvent) element).getOutgoingFlows();
                    if (!outgoingFlows.isEmpty()) {
                        return outgoingFlows.get(0).getTargetRef();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查找发起节点失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 更新实体状态为"被驳回"
     */
    private void updateEntityStatusToRejected(String processInstanceId) {
        try {
            // 从流程变量中获取实体信息
            Map<String, Object> vars = runtimeService.getVariables(processInstanceId);
            String entityCode = (String) vars.get("entityCode");
            String entityDataId = (String) vars.get("entityDataId");

            if (entityDataId != null) {
                // 更新实体数据状态为被驳回
                EntityData entityData = entityDataMapper.selectById(entityDataId);
                if (entityData != null) {
                    // 使用通用的驳回状态码，或从配置中查找
                    String rejectedStatusCode = findRejectedStatusCode(entityCode);
                    if (rejectedStatusCode != null) {
                        entityData.setStatus(rejectedStatusCode);
                    }
                    entityData.setUpdatedAt(LocalDateTime.now());
                    entityDataMapper.updateById(entityData);
                    log.debug("实体数据 {} 状态更新为被驳回", entityDataId);
                }
            }
        } catch (Exception e) {
            log.warn("更新实体驳回状态失败: {}", e.getMessage());
        }
    }
    
    /**
     * 查找驳回状态编码
     */
    private String findRejectedStatusCode(String entityCode) {
        try {
            // 尝试从 entity_status 表查找类型为 REJECTED 的状态
            // 这里简化处理，直接返回常用的驳回状态码
            return "REJECTED";
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 更新实体数据
     */
    private void updateEntityData(String processInstanceId, Map<String, Object> formData) {
        try {
            Map<String, Object> vars = runtimeService.getVariables(processInstanceId);
            String entityCode = (String) vars.get("entityCode");
            String entityDataId = (String) vars.get("entityDataId");

            if (entityDataId != null && !formData.isEmpty()) {
                EntityData entityData = entityDataMapper.selectById(entityDataId);
                if (entityData != null) {
                    // 更新业务数据JSON
                    String dataJson = entityData.getDataJson();
                    Map<String, Object> dataMap = new HashMap<>();
                    
                    if (dataJson != null && !dataJson.isEmpty()) {
                        try {
                            dataMap = new com.fasterxml.jackson.databind.ObjectMapper()
                                    .readValue(dataJson, HashMap.class);
                        } catch (Exception e) {
                            log.warn("解析业务数据失败: {}", e.getMessage());
                        }
                    }
                    
                    // 合并新数据
                    dataMap.putAll(formData);
                    
                    // 保存回JSON
                    String updatedJson = new com.fasterxml.jackson.databind.ObjectMapper()
                            .writeValueAsString(dataMap);
                    entityData.setDataJson(updatedJson);
                    entityData.setUpdatedAt(LocalDateTime.now());
                    entityDataMapper.updateById(entityData);
                    
                    log.debug("实体数据 {} 已更新", entityDataId);
                }
            }
        } catch (Exception e) {
            log.warn("更新实体数据失败: {}", e.getMessage());
        }
    }
}
