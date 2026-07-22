package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("process_task_candidate_user")
public class ProcessTaskCandidateUser {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String taskInstanceId;
    private String userId;
    private Integer sortOrder;

    @TableField("create_time")
    private LocalDateTime createdAt;
}
