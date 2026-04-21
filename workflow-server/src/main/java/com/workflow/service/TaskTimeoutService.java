package com.workflow.service;

import com.workflow.entity.ProcessTask;
import com.workflow.mapper.ProcessTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务超时处理服务
 * 定时检查超时任务并处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskTimeoutService {
    
    private final ProcessTaskMapper taskMapper;
    private final TaskService taskService;
    private final ProcessTaskService processTaskService;
    
    /**
     * 每小时检查一次超时任务
     */
    @Scheduled(fixedRate = 3600000) // 1小时
    public void checkTimeoutTasks() {
        log.info("开始检查超时任务...");
        
        // 获取所有未完成的任务
        List<ProcessTask> tasks = taskMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProcessTask>()
                .eq(ProcessTask::getStatus, ProcessTask.STATUS_TODO)
        );
        
        int timeoutCount = 0;
        for (ProcessTask task : tasks) {
            if (isTaskTimeout(task)) {
                handleTimeoutTask(task);
                timeoutCount++;
            }
        }
        
        log.info("超时任务检查完成，发现 {} 个超时任务", timeoutCount);
    }
    
    /**
     * 检查任务是否超时
     */
    private boolean isTaskTimeout(ProcessTask task) {
        // 获取任务配置的超时时间（小时）
        Integer timeoutHours = task.getTimeoutHours();
        if (timeoutHours == null || timeoutHours <= 0) {
            return false;
        }
        
        // 计算是否超时
        LocalDateTime startTime = task.getStartTime();
        if (startTime == null) {
            return false;
        }
        
        LocalDateTime timeoutTime = startTime.plusHours(timeoutHours);
        return LocalDateTime.now().isAfter(timeoutTime);
    }
    
    /**
     * 处理超时任务
     */
    private void handleTimeoutTask(ProcessTask task) {
        log.info("处理超时任务: taskId={}, taskName={}", task.getTaskId(), task.getNodeName());
        
        // 获取超时处理策略
        String timeoutAction = task.getTimeoutAction();
        if (timeoutAction == null) {
            timeoutAction = "REMIND"; // 默认仅提醒
        }
        
        switch (timeoutAction) {
            case "REMIND":
                // 发送提醒通知
                sendTimeoutReminder(task);
                break;
            case "TRANSFER":
                // 转办给上级
                transferToSupervisor(task);
                break;
            case "AUTO_APPROVE":
                // 自动通过
                autoApprove(task);
                break;
            case "AUTO_REJECT":
                // 自动驳回
                autoReject(task);
                break;
            default:
                sendTimeoutReminder(task);
        }
        
        // 更新任务超时标记
        task.setTimeoutHandled(true);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }
    
    /**
     * 发送超时提醒
     */
    private void sendTimeoutReminder(ProcessTask task) {
        log.info("发送超时提醒: taskId={}, assignee={}", task.getTaskId(), task.getAssigneeId());
        
        // TODO: 集成消息通知服务（短信、邮件、站内信等）
        // 这里仅记录日志，实际项目中需要接入消息服务
        
        // 示例：发送给任务处理人
        String message = String.format(
            "您的任务已超时，请尽快处理。任务：%s，流程：%s",
            task.getNodeName(),
            task.getProcessName()
        );
        
        log.info("提醒消息: {}", message);
    }
    
    /**
     * 转办给上级
     */
    private void transferToSupervisor(ProcessTask task) {
        log.info("超时转办给上级: taskId={}", task.getTaskId());
        
        // TODO: 获取任务处理人的上级
        String supervisorId = getSupervisorId(task.getAssigneeId());
        
        if (supervisorId != null) {
            // 转办任务
            taskService.setAssignee(task.getTaskId(), supervisorId);
            
            // 更新本地记录
            task.setAssigneeId(supervisorId);
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
            
            // 同步 Flowable
            processTaskService.syncTasksFromFlowable(task.getProcessInstanceId());
            
            log.info("任务 {} 已转办给上级 {}", task.getTaskId(), supervisorId);
        }
    }
    
    /**
     * 自动通过
     */
    private void autoApprove(ProcessTask task) {
        log.info("超时自动通过: taskId={}", task.getTaskId());
        
        Task flowableTask = taskService.createTaskQuery()
                .taskId(task.getTaskId())
                .singleResult();
        
        if (flowableTask != null) {
            // 设置自动通过标记
            taskService.setVariable(task.getTaskId(), "_autoApproved_", true);
            taskService.setVariable(task.getTaskId(), "_autoApproveReason_", "任务超时自动通过");
            
            // 完成任务
            taskService.complete(task.getTaskId());
            
            // 同步状态
            processTaskService.completeTask(task.getTaskId(), "auto_approve", "超时自动通过");
            processTaskService.syncTasksFromFlowable(task.getProcessInstanceId());
        }
    }
    
    /**
     * 自动驳回
     */
    private void autoReject(ProcessTask task) {
        log.info("超时自动驳回: taskId={}", task.getTaskId());
        
        Task flowableTask = taskService.createTaskQuery()
                .taskId(task.getTaskId())
                .singleResult();
        
        if (flowableTask != null) {
            // 设置自动驳回标记
            taskService.setVariable(task.getTaskId(), "approved", false);
            taskService.setVariable(task.getTaskId(), "_autoRejected_", true);
            taskService.setVariable(task.getTaskId(), "_autoRejectReason_", "任务超时自动驳回");
            
            // 完成任务
            taskService.complete(task.getTaskId());
            
            // 同步状态
            processTaskService.completeTask(task.getTaskId(), "auto_reject", "超时自动驳回");
            processTaskService.syncTasksFromFlowable(task.getProcessInstanceId());
        }
    }
    
    /**
     * 获取用户的上级（简化实现）
     */
    private String getSupervisorId(String userId) {
        // TODO: 从组织架构或用户表中查询上级
        // 这里简化处理，实际应该查询用户部门关系
        return null;
    }
}
