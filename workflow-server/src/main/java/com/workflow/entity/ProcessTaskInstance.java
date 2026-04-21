package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程任务实例 - 用于流程中心统一查询
 */
@Data
@TableName("process_task_instance")
public class ProcessTaskInstance {
    
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    private String processInstanceId;
    private String taskId;
    private String taskKey;
    private String taskName;
    private String processDefinitionId;
    private String processName;
    private String entityCode;
    private String entityDataId;
    private String businessKey;
    private String assigneeId;
    private String assigneeName;
    private String ownerId;
    private String candidateUsers;
    private String candidateGroups;
    
    /**
     * 任务类型：TODO待办/DONE已办/DRAFT草稿/CC抄送
     */
    private String taskType;
    
    /**
     * 操作类型：SUBMIT/APPROVE/REJECT/TRANSFER/RETURN/DELEGATE
     */
    private String actionType;
    
    private String actionComment;
    private String formData;
    private LocalDateTime dueTime;
    private Integer priority;
    private Integer isRead;
    private LocalDateTime readTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private String parentTaskId;
    private String rootTaskId;
    private String delegationState;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 非持久化字段 - 用于前端展示
    private transient String processStatus;
    private transient String businessSummary;
    private transient String startUserName;
    private transient LocalDateTime processStartTime;
}
