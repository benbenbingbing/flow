package com.workflow.process.task.infrastructure.persistence.record;

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
    
    /** 当前实际办理人，兼容 ID/用户名；未认领普通任务为空，候选名单只能放在候选表。 */
    private String assigneeId;
    
    /** 执行人姓名 */
    private String assigneeName;
    
    /** 执行人类型: user/group/role */
    private String assigneeType;
    
    /** 表单标识 */
    private String formKey;
    
    /** 表单数据(JSON) */
    private String formData;

    /** 发起人身份在创建/回填时确定；显示名称由用户目录解析，避免人员更名后显示旧值。 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String startUserId;
    /** 列表只读取这些业务摘要；投影服务用显式 SQL 更新，普通 updateById 不得回写旧摘要快照。 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String businessName;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String businessCode;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String businessDataName;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String businessCurrentTaskName;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String businessStatus;
    /** 区分尚未回填与摘要本身为空；未就绪记录不能直接进入摘要分页。 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Boolean inboxSummaryReady;
    /** 未就绪时仍用引擎候选关系，防止升级期间任务因空候选表丢失。 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Boolean inboxIdentityReady;
    /** 仅用于列表查询的用户目录展示值，不写入任务表。 */
    @TableField(exist = false)
    private String startUserName;
    
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

    /** SLA综合状态 */
    private String slaStatus;

    /** 首次响应截止时间 */
    private LocalDateTime responseDueTime;

    /** Flowable 任务优先级 */
    private Integer priority;
    
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
    public static final String STATUS_HOLD = "hold";
    public static final String STATUS_WAITING = "waiting";
    
    // 操作常量
    public static final String ACTION_APPROVE = "approve";
    public static final String ACTION_REJECT = "reject";
    public static final String ACTION_TRANSFER = "transfer";
    public static final String ACTION_SKIP = "skip";
}
