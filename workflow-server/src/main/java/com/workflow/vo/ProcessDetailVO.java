package com.workflow.vo;

import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 流程详情VO
 */
@Data
public class ProcessDetailVO {
    
    /**
     * 流程名称
     */
    private String processName;
    
    /**
     * 流程实例ID
     */
    private String instanceId;
    
    /**
     * 流程定义ID
     */
    private String processDefinitionId;
    
    /**
     * 当前节点名称
     */
    private String currentNode;
    
    /**
     * 当前节点ID
     */
    private String currentNodeId;
    
    /**
     * 发起人
     */
    private String startUser;
    
    /**
     * 发起时间
     */
    private String startTime;
    
    /**
     * 业务Key
     */
    private String businessKey;
    
    /**
     * 流程状态
     */
    private String status;
    
    /**
     * BPMN XML
     */
    private String bpmnXml;
    
    /**
     * 已完成的节点ID列表
     */
    private List<String> completedNodes;
    
    /**
     * 审批历史
     */
    private List<HistoryVO> history;
    
    /**
     * 表单数据
     */
    private Map<String, Object> formData;
    
    /**
     * 历史记录VO
     */
    @Data
    public static class HistoryVO {
        /**
         * 任务名称
         */
        private String taskName;
        
        /**
         * 处理人
         */
        private String assignee;
        
        /**
         * 操作类型（发起、通过、驳回、转办）
         */
        private String action;
        
        /**
         * 开始时间
         */
        private String startTime;
        
        /**
         * 结束时间
         */
        private String endTime;
        
        /**
         * 审批意见
         */
        private String comment;
        
        /**
         * 耗时（毫秒）
         */
        private Long duration;
    }
}
