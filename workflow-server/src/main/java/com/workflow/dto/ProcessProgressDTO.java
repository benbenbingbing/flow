package com.workflow.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 流程进度DTO
 * 用于展示流程实例的执行进度，包括已完成的节点、当前活动节点等
 */
@Data
public class ProcessProgressDTO {
    
    /**
     * 流程实例ID
     */
    private String processInstanceId;
    
    /**
     * 流程定义ID
     */
    private String processDefinitionId;
    
    /**
     * 流程标识
     */
    private String processKey;
    
    /**
     * 流程名称
     */
    private String processName;
    
    /**
     * BPMN XML
     */
    private String bpmnXml;
    
    /**
     * 流程状态：RUNNING-运行中，COMPLETED-已完成，SUSPENDED-已挂起
     */
    private String status;
    
    /**
     * 已完成的节点ID列表
     */
    private List<String> completedNodes;
    
    /**
     * 当前活动节点ID列表
     */
    private List<String> activeNodes;
    
    /**
     * 已执行的连线ID列表
     */
    private List<String> executedSequenceFlows;
    
    /**
     * 节点执行历史记录
     */
    private List<NodeHistoryDTO> nodeHistory;
    
    /**
     * 任务信息
     */
    private List<TaskInfoDTO> tasks;
    
    /**
     * 节点处理人映射（key: 节点ID, value: 处理人信息）
     * 用于前端快速查询每个节点的处理人
     */
    private Map<String, AssigneeInfoDTO> nodeAssigneeMap;
    
    /**
     * 实体数据
     */
    private Map<String, Object> entityData;
    
    /**
     * 表单配置
     */
    private FormConfigDTO formConfig;
    
    /**
     * 表单配置DTO
     */
    @Data
    public static class FormConfigDTO {
        /**
         * 表单ID
         */
        private String formId;
        
        /**
         * 表单名称
         */
        private String formName;
        
        /**
         * 表单Key
         */
        private String formKey;
        
        /**
         * 布局类型
         */
        private String layoutType;
        
        /**
         * 表单字段列表
         */
        private List<Map<String, Object>> fields;
    }
    
    /**
     * 节点历史记录
     */
    @Data
    public static class NodeHistoryDTO {
        /**
         * 节点ID
         */
        private String nodeId;
        
        /**
         * 节点名称
         */
        private String nodeName;
        
        /**
         * 节点类型
         */
        private String nodeType;
        
        /**
         * 执行人ID
         */
        private String assignee;
        
        /**
         * 执行人姓名
         */
        private String assigneeName;
        
        /**
         * 开始时间
         */
        private String startTime;
        
        /**
         * 结束时间
         */
        private String endTime;
        
        /**
         * 执行时长（毫秒）
         */
        private Long duration;
        
        /**
         * 执行结果：COMPLETED-完成，ACTIVE-进行中
         */
        private String status;
        
        /**
         * 处理方式：APPROVED-同意，REJECTED-驳回，TRANSFERRED-转办
         */
        private String action;
        
        /**
         * 审批意见/备注
         */
        private String comment;
        
        /**
         * 节点执行变量快照（主要用于脚本任务等自动节点）
         */
        private Map<String, Object> variables;
    }
    
    /**
     * 任务信息
     */
    @Data
    public static class TaskInfoDTO {
        /**
         * 任务ID
         */
        private String taskId;
        
        /**
         * 任务名称
         */
        private String taskName;
        
        /**
         * 节点ID
         */
        private String nodeId;
        
        /**
         * 处理人
         */
        private String assignee;
        
        /**
         * 创建时间
         */
        private String createTime;
    }
    
    /**
     * 处理人信息
     */
    @Data
    public static class AssigneeInfoDTO {
        /**
         * 处理人ID
         */
        private String assigneeId;
        
        /**
         * 处理人姓名
         */
        private String assigneeName;
        
        /**
         * 处理时间
         */
        private String handleTime;
        
        /**
         * 处理方式：APPROVED-同意，REJECTED-驳回，TRANSFERRED-转办
         */
        private String action;
        
        /**
         * 处理意见
         */
        private String comment;
    }
}
