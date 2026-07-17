package com.workflow.vo;

import lombok.Data;

/**
 * 流程中心统计 VO
 */
@Data
public class ProcessStatisticsVO {
    
    /**
     * 待办数量
     */
    private Long todoCount;
    
    /**
     * 今日已办数量
     */
    private Long doneTodayCount;
    
    /**
     * 未读抄送数量
     */
    private Long unreadCcCount;
    
    /**
     * 草稿数量
     */
    private Long draftCount;
}
