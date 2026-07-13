package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程节点待办实体
 */
@Data
@TableName("process_task")
public class ProcessTask {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 流程实例ID */
    private String processInstanceId;
    
    /** 流程定义ID */
    private String processDefinitionId;
    
    /** 流程标识 */
    private String processKey;
    
    /** 流程名称 */
    private String processName;
    
    /** 节点ID */
    private String nodeId;
    
    /** 节点名称 */
    private String nodeName;
    
    /** 节点类型 */
    private String nodeType;
    
    /** Flowable任务ID */
    private String taskId;
    
    /** 业务主键 */
    private String businessKey;
    
    /** 实体编码 */
    private String entityCode;
    
    /** 实体数据ID */
    private String entityDataId;
    
    /** 执行人ID */
    private String assigneeId;
    
    /** 执行人姓名 */
    private String assigneeName;
    
    /** 执行人类型: user/group/role */
    private String assigneeType;
    
    /** 表单标识 */
    private String formKey;
    
    /** 表单数据(JSON) */
    private String formData;
    
    /** 状态: todo-待办 done-已办 transfer-转办 skip-跳过 */
    private String status;
    
    /** 操作: approve/reject/transfer/skip */
    private String action;

    /** 操作显示文本，如"同意，需要会签" */
    private String actionLabel;

    /** 审批意见 */
    private String comment;
    
    /** 任务开始时间 */
    private LocalDateTime startTime;
    
    /** 任务结束时间 */
    private LocalDateTime endTime;
    
    /** 截止时间 */
    private LocalDateTime dueTime;
    
    /** 处理耗时(毫秒) */
    private Long duration;
    
    /** 超时时间(小时) */
    private Integer timeoutHours;
    
    /** 超时处理策略: REMIND-提醒, TRANSFER-转办, AUTO_APPROVE-自动通过, AUTO_REJECT-自动驳回 */
    private String timeoutAction;
    
    /** 是否已处理超时 */
    private Boolean timeoutHandled;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
    
    @TableLogic
    private Integer deleted;
    
    // 状态常量
    public static final String STATUS_TODO = "todo";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_TRANSFER = "transfer";
    public static final String STATUS_SKIP = "skip";
    
    // 操作常量
    public static final String ACTION_APPROVE = "approve";
    public static final String ACTION_REJECT = "reject";
    public static final String ACTION_TRANSFER = "transfer";
    public static final String ACTION_SKIP = "skip";
}
