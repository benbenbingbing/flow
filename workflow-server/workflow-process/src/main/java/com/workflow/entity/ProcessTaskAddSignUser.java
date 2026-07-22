package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("process_task_add_sign_user")
public class ProcessTaskAddSignUser {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String addSignId;
    private String userId;
    private String userNameSnapshot;
    private String generatedTaskId;
    private String status;
    private Integer sortOrder;
    private LocalDateTime completeTime;
}
