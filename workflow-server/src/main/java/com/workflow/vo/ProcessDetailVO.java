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
     * 节点处理人映射（key: 节点ID, value: 处理人信息）
     */
    private Map<String, AssigneeVO> nodeAssigneeMap;
    
    /**
     * 审批历史
     */
    private List<HistoryVO> history;
    
    /**
     * 表单数据
     */
    private Map<String, Object> formData;
    
    /**
     * 节点处理人VO
     */
    @Data
    public static class AssigneeVO {
        /**
         * 处理人ID（用户名）
         */
        private String assigneeId;
        
        /**
         * 处理人姓名（昵称）
         */
        private String assigneeName;
        
        /**
         * 处理时间
         */
        private String handleTime;
        
        /**
         * 操作类型
         */
        private String action;
        
        /**
         * 审批意见
         */
        private String comment;
        
        /**
         * 状态：completed-已完成，processing-处理中
         */
        private String status;
    }
    
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
         * 处理人ID
         */
        private String assignee;
        
        /**
         * 处理人姓名
         */
        private String assigneeName;
        
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
        
        /**
         * 节点执行时的流程变量快照
         */
        private java.util.Map<String, Object> variables;
    }
}
