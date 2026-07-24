package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务候选用户实体
 * 记录某个任务实例的候选审批人，用于会签/或签场景下人员匹配
 */
@Data
@TableName("process_task_candidate_user")
public class ProcessTaskCandidateUser {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 关联的流程任务实例ID */
    private String taskInstanceId;
    /** 候选用户ID */
    private String userId;
    /** 排序号，控制候选用户处理顺序 */
    private Integer sortOrder;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createdAt;
}
