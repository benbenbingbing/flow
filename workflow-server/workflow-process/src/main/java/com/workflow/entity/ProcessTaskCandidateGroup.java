package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("process_task_candidate_group")
public class ProcessTaskCandidateGroup {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String taskInstanceId;
    private String groupCode;
    private Integer sortOrder;

    @TableField("create_time")
    private LocalDateTime createdAt;
}
