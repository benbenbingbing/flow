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
     * 获取当前用户的任务统计（待办、已办、发起流程数及平均处理时长）。
     *
     * @return 任务统计信息
     */
    TaskStatisticsVO getStatistics();
    
    /**
     * 分页获取待办任务列表。
     *
     * @param pageNum     页码
     * @param pageSize    每页大小
     * @param processName 流程名称筛选（可选）
     * @param taskName    任务名称筛选（可选）
     * @param timeRange   时间范围筛选（week/month/year，可选）
     * @return 待办任务分页结果
     */
    PageResult<TaskVO> getTodoList(Integer pageNum, Integer pageSize, String processName, String taskName, String timeRange);
    
    /**
     * 分页获取已办任务列表。
     *
     * @param pageNum     页码
     * @param pageSize    每页大小
     * @param processName 流程名称筛选（可选）
     * @param taskName    任务名称筛选（可选）
     * @param timeRange   时间范围筛选（week/month/year，可选）
     * @return 已办任务分页结果
     */
    PageResult<TaskVO> getDoneList(Integer pageNum, Integer pageSize, String processName, String taskName, String timeRange);
    
    /**
     * 完成任务审批（通过/驳回/转办/自定义操作）。
     *
     * @param taskId      任务ID
     * @param action      操作类型（approve/reject/transfer 等）
     * @param comment     审批意见
     * @param transferTo  转办目标人（仅转办时使用）
     * @param actionLabel 操作显示文本
     */
    void completeTask(String taskId, String action, String comment, String transferTo, String actionLabel);
    
    /**
     * 获取任务详情。
     *
     * @param taskId 任务ID
     * @return 任务详情VO
     */
    TaskVO getTaskDetail(String taskId);
    
    /**
     * 撤回流程。
     *
     * <p>发起人可在第一个审批人审批前撤回流程实例。</p>
     *
     * @param processInstanceId 流程实例ID
     * @param reason            撤回原因
     */
    void withdrawProcess(String processInstanceId, String reason);
    
    /**
     * 获取流程审批历史记录。
     *
     * @param processInstanceId 流程实例ID
     * @return 历史任务列表
     */
    List<TaskVO> getProcessHistory(String processInstanceId);
    
    /**
     * 驳回到指定节点后重新提交任务。
     *
     * @param taskId   任务ID
     * @param comment  重新提交备注
     * @param formData 表单数据
     */
    void resubmitTask(String taskId, String comment, Map<String, Object> formData);
}
