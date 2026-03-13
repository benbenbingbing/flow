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
    
    /** 状态: 0-待办 1-已办 2-转办 3-跳过 */
    private Integer status;
    
    /** 操作: approve/reject/transfer/skip */
    private String action;
    
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
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
    
    @TableLogic
    private Integer deleted;
    
    // 状态常量
    public static final int STATUS_TODO = 0;
    public static final int STATUS_DONE = 1;
    public static final int STATUS_TRANSFER = 2;
    public static final int STATUS_SKIP = 3;
    
    // 操作常量
    public static final String ACTION_APPROVE = "approve";
    public static final String ACTION_REJECT = "reject";
    public static final String ACTION_TRANSFER = "transfer";
    public static final String ACTION_SKIP = "skip";
}
