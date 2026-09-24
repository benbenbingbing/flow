package com.workflow.process.workbench.api.response;

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
     * 数据标题（实体数据的name字段）
     */
    private String dataName;
    
    /**
     * 数据名称（实体数据的name字段，用于显示标题）
     */
    private String name;
    
    /**
     * 数据编码（实体数据的code字段）
     */
    private String code;
    
    /**
     * 当前节点名称
     */
    private String currentNodeName;
    
    /**
     * 发起人
     */
    private String startUser;

    /**
     * 发起人名称（昵称）
     */
    private String startUserName;

    /**
     * 发起时间
     */
    private String startTime;
    
    /**
     * 结束时间（如果已结束）
     */
    private String endTime;
    
    /**
     * 本轮实例生命周期：RUNNING-运行中，COMPLETED-已完成（包括终止、撤回）。
     */
    private String status;
    
    /**
     * 状态文本
     */
    private String statusText;

    /** 关联实体数据的当前业务状态，不覆盖本条历史流程实例的 status。 */
    private String entityStatus;

    /** 实体配置中的状态名称；未配置时由客户端按状态编码回退。 */
    private String entityStatusText;

    /** 当前登录发起人是否可以终止该运行中流程。 */
    private Boolean canTerminate;

    /** 当前发起人且所有活动节点允许撤回时为 true；PC 列表据此显示按钮，写接口仍重新校验。 */
    private Boolean canWithdraw;
}
