package com.workflow.process.task.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务候选用户实体
 * 保存平台任务的直接候选用户；不包含由组成员展开的用户，也不代表已办理人员
 */
@Data
@TableName("process_task_candidate_user")
public class ProcessTaskCandidateUser {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 关联 process_task.id；使用平台主键，不能传 Flowable task_id 或已退役的旧实例 ID。 */
    private Long processTaskId;
    /** 候选用户ID */
    private String userId;
    /** 稳定展示顺序；不决定会签先后或授予办理权限。 */
    private Integer sortOrder;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createdAt;
}
