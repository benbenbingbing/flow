package com.workflow.vo;

import lombok.Data;

/**
 * 我发起的流程VO
 */
@Data
public class MyStartedProcessVO {
    
    /**
     * 流程实例ID
     */
    private String processInstanceId;
    
    /**
     * 流程定义ID
     */
    private String processDefinitionId;
    
    /**
     * 流程名称
     */
    private String processName;
    
    /**
     * 流程Key
     */
    private String processKey;
    
    /**
     * 业务Key
     */
    private String businessKey;
    
    /**
     * 当前节点名称
     */
    private String currentNodeName;
    
    /**
     * 发起人
     */
    private String startUser;
    
    /**
     * 发起时间
     */
    private String startTime;
    
    /**
     * 结束时间（如果已结束）
     */
    private String endTime;
    
    /**
     * 流程状态：RUNNING-运行中，COMPLETED-已完成，TERMINATED-已终止，SUSPENDED-已挂起
     */
    private String status;
    
    /**
     * 状态文本
     */
    private String statusText;
}
