package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务候选组实体
 * 记录某个任务实例的候选审批组，用于会签/或签场景下分组匹配
 */
@Data
@TableName("process_task_candidate_group")
public class ProcessTaskCandidateGroup {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 关联的流程任务实例ID */
    private String taskInstanceId;
    /** 候选组编码（角色/部门等） */
    private String groupCode;
    /** 排序号，控制候选组处理顺序 */
    private Integer sortOrder;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createdAt;
}
