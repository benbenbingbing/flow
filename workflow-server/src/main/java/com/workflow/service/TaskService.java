package com.workflow.service;

import com.workflow.common.PageResult;
import com.workflow.vo.TaskStatisticsVO;
import com.workflow.vo.TaskVO;

import java.util.List;
import java.util.Map;

/**
 * 任务服务接口
 */
public interface TaskService {
    
    /**
     * 获取任务统计
     */
    TaskStatisticsVO getStatistics();
    
    /**
     * 获取待办任务列表
     */
    PageResult<TaskVO> getTodoList(Integer pageNum, Integer pageSize, String processName, String taskName, String timeRange);
    
    /**
     * 获取已办任务列表
     */
    PageResult<TaskVO> getDoneList(Integer pageNum, Integer pageSize, String processName, String taskName, String timeRange);
    
    /**
     * 完成任务审批
     */
    void completeTask(String taskId, String action, String comment, String transferTo);
    
    /**
     * 获取任务详情
     */
    TaskVO getTaskDetail(String taskId);
    
    /**
     * 撤回流程
     * 发起人可在第一个审批人审批前撤回
     */
    void withdrawProcess(String processInstanceId, String reason);
    
    /**
     * 获取流程审批历史
     */
    List<TaskVO> getProcessHistory(String processInstanceId);
    
    /**
     * 驳回到指定节点后重新提交
     */
    void resubmitTask(String taskId, String comment, Map<String, Object> formData);
}
