package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("process_task_add_sign")
public class ProcessTaskAddSign {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String processInstanceId;
    private String sourceTaskId;
    private String nodeId;
    private String operationType;
    private String operatorId;
    private String comment;
    private String status;
    private String engineExecutionId;
    private Boolean sourceCompleted;
    private String sourceAction;
    private String sourceActionLabel;
    private String sourceComment;
    private String sourceFormData;
    private LocalDateTime createTime;
    private LocalDateTime completeTime;
}
