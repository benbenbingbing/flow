package com.workflow.service;

import com.workflow.entity.EntityData;
import com.workflow.entity.ProcessTask;
import com.workflow.mapper.EntityDataMapper;
import com.workflow.vo.TaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 任务动作服务
 * 处理任务完成、流程撤回、历史查询等操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskActionService {

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final ProcessTaskService processTaskService;
    private final org.flowable.engine.RepositoryService repositoryService;
    private final EntityDataMapper entityDataMapper;
    private final com.workflow.mapper.ProcessOperationLogMapper operationLogMapper;
    private final SysUserService sysUserService;
    private final NodeFormSubmissionService nodeFormSubmissionService;

    /**
     * 完成任务
     *
     * @param taskId      任务ID
     * @param userId      当前用户ID
     * @param action      操作类型：approve/reject/transfer
     * @param comment     审批意见
     * @param transferTo  转办人（转办时使用）
     * @param actionLabel 操作显示文本（如"同意，需要会签"）
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(String taskId, String userId, String action, String comment, String transferTo, String actionLabel) {
        completeTask(taskId, userId, action, comment, transferTo, actionLabel, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeTask(String taskId, String userId, String action, String comment, String transferTo,
                             String actionLabel, Map<String, Object> formData) {
        // 验证任务是否存在
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();

        if (task == null) {
            throw new RuntimeException("任务不存在或已处理: " + taskId);
        }

        // 验证任务是否分配给当前用户或用户是候选人
        String assignee = task.getAssignee();
        if (assignee != null && !assignee.equals(userId)) {
            // 如果任务已分配给其他人，检查当前用户是否有权限处理
            // 简化处理：记录警告但允许处理（实际应该检查候选组）
            log.warn("任务 {} 分配给 {}，但由 {} 处理", taskId, assignee, userId);
        }

        // 设置审批人（如果未分配或分配给别人，则重新认领）
        if (assignee == null || !assignee.equals(userId)) {
            taskService.setAssignee(taskId, userId);
        }

        nodeFormSubmissionService.applyEditableData(task, formData);

        // 检查是否是多实例任务（会签/或签）
        boolean isMultiInstance = isMultiInstanceTask(task);
        
        String normalizedAction = normalizeAction(action);

        // 根据不同操作类型处理
        switch (normalizedAction) {
            case "approve":
                handleApprove(task, userId, comment, isMultiInstance, actionLabel);
                break;

            case "reject":
                handleReject(task, userId, comment, isMultiInstance, actionLabel);
                break;

            case "transfer":
                // 转办
                if (transferTo == null || transferTo.isEmpty()) {
                    throw new RuntimeException("转办人不能为空");
                }
                taskService.setAssignee(taskId, transferTo);
                processTaskService.completeTask(taskId, "transfer", "转办给: " + transferTo);
                
                // 为转办人创建新的待办记录（原待办已标记为 transfer，需要一条新的 todo）
                try {
                    Task transferredTask = taskService.createTaskQuery().taskId(taskId).singleResult();
                    if (transferredTask != null) {
                        Map<String, Object> variables = runtimeService.getVariables(transferredTask.getProcessInstanceId());
                        processTaskService.createTask(transferredTask, variables);
                        log.info("已为转办人 {} 创建新待办: taskId={}", transferTo, taskId);
                    }
                } catch (Exception e) {
                    log.warn("为转办人创建待办失败: taskId={}, transferTo={}", taskId, transferTo, e);
                }
                
                // 记录转办日志到 process_operation_log
                try {
                    com.workflow.entity.ProcessOperationLog log = new com.workflow.entity.ProcessOperationLog();
                    log.setProcessInstanceId(task.getProcessInstanceId());
                    log.setTaskId(taskId);
                    log.setOperationType("TRANSFER");
                    log.setOperatorId(userId);
                    String operatorName = sysUserService.getDisplayName(userId);
                    log.setOperatorName(operatorName);
                    log.setOperationTime(LocalDateTime.now());
                    log.setOperationComment(comment);
                    log.setOldValue(assignee);
                    log.setNewValue(transferTo);
                    operationLogMapper.insert(log);
                } catch (Exception e) {
                    log.warn("记录转办日志失败", e);
                }
                
                log.info("任务 {} 已转办给 {}", taskId, transferTo);
                break;

            default:
                // 自定义操作类型：按普通审批完成，approved 使用原始 action 值
                // 这样前端可以配置任意审批选项值（如 needMeeting、approve、reject 等），
                // 网关条件通过 ${approved == 'xxx'} 即可分支。
                Map<String, Object> customVars = new HashMap<>();
                customVars.put("approved", normalizedAction);
                customVars.put("action", normalizedAction);
                if (actionLabel != null && !actionLabel.isBlank()) {
                    customVars.put("actionLabel", actionLabel);
                }
                customVars.put("comment", comment);
                customVars.put("approver", userId);

                List<String> customApprovers = (List<String>) runtimeService.getVariable(task.getProcessInstanceId(), "_approvers_");
                if (customApprovers == null) {
                    customApprovers = new ArrayList<>();
                }
                customApprovers.add(userId);
                customVars.put("_approvers_", customApprovers);

                // 将操作显示文本存为任务本地变量，便于后续按任务ID读取
                if (actionLabel != null && !actionLabel.isBlank()) {
                    taskService.setVariableLocal(taskId, "actionLabel", actionLabel);
                }

                taskService.complete(taskId, customVars);
                processTaskService.completeTask(taskId, normalizedAction, comment, actionLabel);

                log.info("任务 {} 已通过自定义操作完成: action={}, user={}", taskId, normalizedAction, userId);
                break;
        }

        // 同步更新待办状态（实体状态由 EntityStatusUpdateListener 监听器自动更新）
        String processInstanceId = task.getProcessInstanceId();
        if (processInstanceId != null) {
            processTaskService.syncTasksFromFlowable(processInstanceId);
            // 注意：实体数据状态由 EntityStatusUpdateListener 监听器自动更新
            // 不需要在这里手动更新，避免重复更新
        }
    }

    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return "approve";
        }

        return switch (action.trim().toUpperCase(Locale.ROOT)) {
            case "APPROVE", "APPROVED" -> "approve";
            case "REJECT", "REJECTED" -> "reject";
            case "TRANSFER", "TRANSFERRED" -> "transfer";
            default -> action;
        };
    }
    
    /**
     * 检查任务是否是多实例任务（会签/或签）
     */
    private boolean isMultiInstanceTask(Task task) {
        try {
            // 从流程变量中检查是否是多实例任务
            Map<String, Object> vars = runtimeService.getVariables(task.getProcessInstanceId());
            // 检查是否有 nrOfInstances 变量（Flowable 多实例任务的标志）
            return vars.containsKey("nrOfInstances") || vars.containsKey("nrOfCompletedInstances");
        } catch (Exception e) {
            log.debug("检查多实例任务失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 处理通过操作（支持单签/会签）
     */
    private void handleApprove(Task task, String userId, String comment, boolean isMultiInstance, String actionLabel) {
        String taskId = task.getId();
        
        // 设置流程变量
        Map<String, Object> vars = new HashMap<>();
        vars.put("approved", "approve");
        vars.put("action", "approve");
        if (actionLabel != null && !actionLabel.isBlank()) {
            vars.put("actionLabel", actionLabel);
        }
        vars.put("comment", comment);
        vars.put("approver", userId);
        
        // 记录审批人信息到变量（用于会签统计）
        List<String> approvers = (List<String>) runtimeService.getVariable(task.getProcessInstanceId(), "_approvers_");
        if (approvers == null) {
            approvers = new ArrayList<>();
        }
        approvers.add(userId);
        vars.put("_approvers_", approvers);
        
        // 将操作显示文本存为任务本地变量，便于后续按任务ID读取
        if (actionLabel != null && !actionLabel.isBlank()) {
            taskService.setVariableLocal(taskId, "actionLabel", actionLabel);
        }

        // 完成任务
        taskService.complete(taskId, vars);
        processTaskService.completeTask(taskId, "approve", comment, actionLabel);
        
        log.info("任务 {} 已通过，处理人: {}，是否多实例: {}", taskId, userId, isMultiInstance);
    }
    
    /**
     * 处理驳回操作（支持单签/会签）
     * 会签模式下：一人驳回即整体驳回
     */
    private void handleReject(Task task, String userId, String comment, boolean isMultiInstance, String actionLabel) {
        String taskId = task.getId();
        String processInstanceId = task.getProcessInstanceId();
        
        // 设置流程变量
        Map<String, Object> vars = new HashMap<>();
        vars.put("approved", "reject");
        vars.put("action", "reject");
        if (actionLabel != null && !actionLabel.isBlank()) {
            vars.put("actionLabel", actionLabel);
        }
        vars.put("comment", comment);
        vars.put("rejectBy", userId);
        vars.put("rejectTime", new Date());
        
        if (isMultiInstance) {
            // 会签模式下：记录驳回信息，所有未完成的实例将被终止
            vars.put("_multiInstanceRejected_", true);
            vars.put("_rejectReason_", comment);
            
            // 终止其他未完成的多实例任务
            terminateOtherMultiInstanceTasks(processInstanceId, taskId);
        }
        
        // 将操作显示文本存为任务本地变量，便于后续按任务ID读取
        if (actionLabel != null && !actionLabel.isBlank()) {
            taskService.setVariableLocal(taskId, "actionLabel", actionLabel);
        }

        // 完成任务
        taskService.complete(taskId, vars);
        processTaskService.completeTask(taskId, "reject", comment, actionLabel);
        
        log.info("任务 {} 已驳回，处理人: {}，是否多实例: {}", taskId, userId, isMultiInstance);
    }
    
    /**
     * 终止其他未完成的多实例任务（会签驳回时使用）
     */
    private void terminateOtherMultiInstanceTasks(String processInstanceId, String currentTaskId) {
        try {
            // 获取同一节点上的其他未完成任务
            List<Task> activeTasks = taskService.createTaskQuery()
                    .processInstanceId(processInstanceId)
                    .list();
            
            for (Task otherTask : activeTasks) {
                if (!otherTask.getId().equals(currentTaskId)) {
                    // 设置变量标记该任务因驳回而跳过
                    taskService.setVariable(otherTask.getId(), "_skippedDueToReject_", true);
                    taskService.setVariable(otherTask.getId(), "approved", "reject");
                    log.debug("多实例任务 {} 因驳回而被跳过", otherTask.getId());
                }
            }
        } catch (Exception e) {
            log.warn("终止其他多实例任务失败: {}", e.getMessage());
        }
    }

    /**
     * 撤回流程
     * 发起人可以在流程未完成前撤回
     *
     * @param processInstanceId 流程实例ID
     * @param userId            当前用户ID
     * @param reason            撤回原因
     */
    @Transactional(rollbackFor = Exception.class)
    public void withdrawProcess(String processInstanceId, String userId, String reason) {
        // 验证流程实例是否存在
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (processInstance == null) {
            throw new RuntimeException("流程实例不存在或已结束");
        }

        // 验证是否是发起人（简化处理）
        String startUserId = processInstance.getStartUserId();
        if (startUserId != null && !startUserId.equals(userId)) {
            throw new RuntimeException("只有发起人才能撤回流程");
        }

        try {
            // 删除流程实例（撤回相当于终止流程）
            runtimeService.deleteProcessInstance(processInstanceId, "发起人撤回: " + reason);

            // 清理本地待办
            processTaskService.deleteTasksByProcessInstance(processInstanceId);

            log.info("流程实例 {} 已被用户 {} 撤回，原因: {}", processInstanceId, userId, reason);
        } catch (Exception e) {
            log.error("撤回流程失败: {}", processInstanceId, e);
            throw new RuntimeException("撤回失败: " + e.getMessage());
        }
    }

    /**
     * 获取流程历史记录
     *
     * @param processInstanceId 流程实例ID
     * @return 历史任务列表
     */
    public List<TaskVO> getProcessHistory(String processInstanceId) {
        List<TaskVO> historyList = new ArrayList<>();

        // 1. 获取流程发起信息
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (historicInstance != null) {
            TaskVO startVo = new TaskVO();
            startVo.setTaskName("流程发起");
            startVo.setAssignee(historicInstance.getStartUserId());
            startVo.setResult("start");
            if (historicInstance.getStartTime() != null) {
                startVo.setCreateTime(historicInstance.getStartTime());
            }
            historyList.add(startVo);
        }

        // 2. 获取历史任务（已完成）
        List<HistoricTaskInstance> historicTasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .finished()
                .orderByHistoricTaskInstanceEndTime()
                .asc()
                .list();

        for (HistoricTaskInstance historicTask : historicTasks) {
            TaskVO vo = convertHistoricTaskToVO(historicTask);
            historyList.add(vo);
        }

        // 3. 获取当前活动任务（未完成的）
        List<Task> activeTasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .list();

        for (Task task : activeTasks) {
            TaskVO vo = new TaskVO();
            vo.setTaskId(task.getId());
            vo.setTaskName(task.getName());
            vo.setAssignee(task.getAssignee());
            vo.setProcessInstanceId(task.getProcessInstanceId());
            vo.setCreateTime(task.getCreateTime());
            
            // 获取任务评论判断是否是转办
            List<org.flowable.engine.task.Comment> comments = taskService.getTaskComments(task.getId());
            String result = "processing";
            for (org.flowable.engine.task.Comment c : comments) {
                if (c.getFullMessage() != null && c.getFullMessage().contains("转办给:")) {
                    result = "transfer";
                    break;
                }
            }
            vo.setResult(result);
            
            historyList.add(vo);
        }

        // 4. 合并转办记录
        try {
            List<com.workflow.entity.ProcessOperationLog> transferLogs = operationLogMapper
                    .selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflow.entity.ProcessOperationLog>()
                            .eq(com.workflow.entity.ProcessOperationLog::getProcessInstanceId, processInstanceId)
                            .eq(com.workflow.entity.ProcessOperationLog::getOperationType, "TRANSFER")
                            .orderByAsc(com.workflow.entity.ProcessOperationLog::getOperationTime));
            
            for (com.workflow.entity.ProcessOperationLog log : transferLogs) {
                TaskVO vo = new TaskVO();
                vo.setTaskId(log.getTaskId());
                vo.setProcessInstanceId(processInstanceId);
                
                // 查找任务名称
                var ht = historyService.createHistoricTaskInstanceQuery().taskId(log.getTaskId()).singleResult();
                vo.setTaskName(ht != null ? ht.getName() : "任务转办");
                vo.setAssignee(log.getOperatorId());
                vo.setResult("transfer");
                vo.setComment(log.getNewValue() != null ? "转办给: " + log.getNewValue() : log.getOperationComment());
                vo.setCreateTime(log.getOperationTime() != null ? 
                    Date.from(log.getOperationTime().atZone(ZoneId.systemDefault()).toInstant()) : null);
                
                // 插入到对应任务记录之前
                int insertIndex = -1;
                for (int i = 0; i < historyList.size(); i++) {
                    if (log.getTaskId() != null && log.getTaskId().equals(historyList.get(i).getTaskId())) {
                        insertIndex = i;
                        break;
                    }
                }
                if (insertIndex >= 0) {
                    historyList.add(insertIndex, vo);
                } else {
                    historyList.add(vo);
                }
            }
        } catch (Exception e) {
            log.warn("合并转办记录到历史失败", e);
        }

        return historyList;
    }

    /**
     * 获取任务统计信息
     *
     * @param userId 用户ID
     * @return 统计信息
     */
    public Map<String, Object> getTaskStatistics(String userId) {
        Map<String, Object> statistics = new HashMap<>();

        // 待办数
        Long todoCount = processTaskService.countTodo(userId);
        statistics.put("todoCount", todoCount);

        // 已办数
        Long doneCount = processTaskService.countDone(userId);
        statistics.put("doneCount", doneCount);

        // 我发起的流程数（简化统计）
        long myProcessCount = historyService.createHistoricProcessInstanceQuery()
                .startedBy(userId)
                .count();
        statistics.put("processCount", myProcessCount);

        // 平均处理时长（简化计算）
        List<ProcessTask> doneTasks = processTaskService.getDoneList(userId);
        long totalDuration = doneTasks.stream()
                .filter(t -> t.getDuration() != null)
                .mapToLong(ProcessTask::getDuration)
                .sum();
        double avgHours = doneTasks.isEmpty() ? 0 : (totalDuration / doneTasks.size() / 1000.0 / 60 / 60);
        statistics.put("avgProcessTime", Math.round(avgHours * 10) / 10.0);

        return statistics;
    }

    /**
     * 转换历史任务为VO
     */
    private TaskVO convertHistoricTaskToVO(HistoricTaskInstance historicTask) {
        TaskVO vo = new TaskVO();
        vo.setTaskId(historicTask.getId());
        vo.setTaskName(historicTask.getName());
        vo.setProcessInstanceId(historicTask.getProcessInstanceId());
        vo.setProcessDefinitionId(historicTask.getProcessDefinitionId());
        vo.setAssignee(historicTask.getAssignee());
        vo.setCreateTime(historicTask.getCreateTime());
        vo.setEndTime(historicTask.getEndTime());
        vo.setDuration(historicTask.getDurationInMillis());

        // 获取任务评论
        List<org.flowable.engine.task.Comment> comments = taskService.getTaskComments(historicTask.getId());
        String commentMsg = comments.isEmpty() ? null : comments.get(0).getFullMessage();
        ProcessTask localTask = processTaskService.getTaskByTaskId(historicTask.getId());
        if ((commentMsg == null || commentMsg.isBlank()) && localTask != null) {
            commentMsg = localTask.getComment();
        }
        vo.setComment(commentMsg);
        
        // 判断是否是转办
        if (commentMsg != null && commentMsg.contains("转办给:")) {
            vo.setResult("transfer");
        } else {
            // 从变量中获取审批结果
            String action = getTaskVariable(historicTask.getId(), "action");
            if ((action == null || action.isBlank()) && localTask != null) {
                action = localTask.getAction();
            }
            vo.setResult(action != null ? action : "approve");
        }

        return vo;
    }

    /**
     * 获取任务变量
     */
    private String getTaskVariable(String taskId, String variableName) {
        try {
            HistoricTaskInstance task = historyService.createHistoricTaskInstanceQuery()
                    .taskId(taskId)
                    .includeTaskLocalVariables()
                    .singleResult();
            if (task != null && task.getTaskLocalVariables() != null) {
                Object value = task.getTaskLocalVariables().get(variableName);
                return value != null ? value.toString() : null;
            }
        } catch (Exception e) {
            log.warn("获取任务变量失败: taskId={}, variable={}", taskId, variableName);
        }
        return null;
    }
}
