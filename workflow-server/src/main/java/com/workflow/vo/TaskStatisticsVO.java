package com.workflow.vo;

import lombok.Data;

/**
 * 任务统计VO
 */
@Data
public class TaskStatisticsVO {
    
    /**
     * 待办任务数
     */
    private Long todoCount;
    
    /**
     * 已办任务数
     */
    private Long doneCount;
    
    /**
     * 我发起的流程数
     */
    private Long processCount;
    
    /**
     * 平均处理时长（小时）
     */
    private Double avgProcessTime;
}
