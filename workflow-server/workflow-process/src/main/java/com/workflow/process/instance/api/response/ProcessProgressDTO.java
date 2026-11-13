package com.workflow.process.instance.api.response;

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
    /** 当前用户可读的各轮实例，供历史选择器使用；每轮进度仍独立鉴权读取。 */
    private List<ProcessRoundDTO> rounds = List.of();
    
    /**
     * 流程定义ID
     */
    private String processDefinitionId;

    /**
     * 该实例实际使用的 Flowable 流程定义版本号，不代表当前最新版本。
     */
    private Integer processVersion;
    
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
     * 生命周期：RUNNING-运行中、COMPLETED-已完成；挂起仍运行中，不能用于判断审批是否通过。
     */
    private String status;

    /** 当前所查看实例的结束类型，供图示和历史区分正常结束、终止与撤回，不能读取实体最新一轮替代。 */
    private String endType;
    /** 本次实例的结束意见，仅用于展示，不参与类型和权限判断。 */
    private String endReason;
    /** 引擎确认被取消的活动节点，供图示标记取消，不代表审批完成。 */
    private List<String> cancelledNodes;
    
    /**
     * 已完成的节点ID列表
     */
    private List<String> completedNodes;
    
    /**
     * 当前活动节点ID列表
     */
    private List<String> activeNodes;
    
    /**
     * 被终止的节点ID列表（流程终止时正在执行的节点）
     */
    private List<String> terminatedNodes;
    
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
     * 节点处理人列表映射（key: 节点ID, value: 处理人信息列表）
     * 用于多实例会签节点展示所有子任务处理人及其状态
     */
    private Map<String, List<AssigneeInfoDTO>> nodeAssigneesMap;
    
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
         * 处理方式显示文本（如"同意，需要会签"）
         */
        private String actionLabel;
        
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
         * 处理人显示名称
         */
        private String assigneeName;
        
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
         * 处理方式显示文本（如"同意，需要会签"）
         */
        private String actionLabel;
        
        /**
         * 处理意见
         */
        private String comment;
        
        /**
         * 处理状态：COMPLETED-已完成，PROCESSING-处理中，PENDING-待处理
         */
        private String status;
    }
}
