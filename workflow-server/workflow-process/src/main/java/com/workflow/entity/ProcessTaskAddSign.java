package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务加签操作实体
 * 记录一次加签（前加签/后加签/并行加签）操作的上下文与状态
 */
@Data
@TableName("process_task_add_sign")
public class ProcessTaskAddSign {
    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /** 流程实例ID */
    private String processInstanceId;
    /** 触发加签的源任务ID */
    private String sourceTaskId;
    /** 加签所在节点ID */
    private String nodeId;
    /** 加签类型（如 before前加签/after后加签/parallel并行加签） */
    private String operationType;
    /** 操作人ID */
    private String operatorId;
    /** 加签操作备注 */
    private String comment;
    /** 加签状态：PENDING-进行中，COMPLETED-已完成，CANCELED-已取消 */
    private String status;
    /** Flowable引擎执行实例ID */
    private String engineExecutionId;
    /** 源任务是否已完成 */
    private Boolean sourceCompleted;
    /** 源任务操作类型（approve/reject等） */
    private String sourceAction;
    /** 源任务操作显示文本 */
    private String sourceActionLabel;
    /** 源任务审批意见 */
    private String sourceComment;
    /** 源任务表单数据（JSON） */
    private String sourceFormData;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 加签完成时间 */
    private LocalDateTime completeTime;
}
