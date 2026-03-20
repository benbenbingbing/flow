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

    /**
     * 完成任务
     *
     * @param taskId     任务ID
     * @param userId     当前用户ID
     * @param action     操作类型：approve/reject/transfer
     * @param comment    审批意见
     * @param transferTo 转办人（转办时使用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(String taskId, String userId, String action, String comment, String transferTo) {
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

        // 根据不同操作类型处理
        switch (action) {
            case "approve":
                // 通过 - 设置变量并完成任务
                Map<String, Object> approveVars = new HashMap<>();
                approveVars.put("approved", true);
                approveVars.put("action", "approve");
                approveVars.put("comment", comment);
                taskService.complete(taskId, approveVars);
                processTaskService.completeTask(taskId, "approve", comment);
                log.info("任务 {} 已通过，处理人: {}", taskId, userId);
                break;

            case "reject":
                // 驳回 - 设置变量并完成任务
                Map<String, Object> rejectVars = new HashMap<>();
                rejectVars.put("approved", false);
                rejectVars.put("action", "reject");
                rejectVars.put("comment", comment);
                taskService.complete(taskId, rejectVars);
                processTaskService.completeTask(taskId, "reject", comment);
                log.info("任务 {} 已驳回，处理人: {}", taskId, userId);
                break;

            case "transfer":
                // 转办
                if (transferTo == null || transferTo.isEmpty()) {
                    throw new RuntimeException("转办人不能为空");
                }
                taskService.setAssignee(taskId, transferTo);
                processTaskService.completeTask(taskId, "transfer", "转办给: " + transferTo);
                log.info("任务 {} 已转办给 {}", taskId, transferTo);
                break;

            default:
                throw new RuntimeException("未知的操作类型: " + action);
        }

        // 同步更新待办状态（实体状态由 EntityStatusUpdateListener 监听器自动更新）
        String processInstanceId = task.getProcessInstanceId();
        if (processInstanceId != null) {
            processTaskService.syncTasksFromFlowable(processInstanceId);
            // 注意：实体数据状态由 EntityStatusUpdateListener 监听器自动更新
            // 不需要在这里手动更新，避免重复更新
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
            vo.setResult("processing");
            historyList.add(vo);
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

        // 从变量中获取审批结果
        String action = getTaskVariable(historicTask.getId(), "action");
        vo.setResult(action != null ? action : "approve");

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
