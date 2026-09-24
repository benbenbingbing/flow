package com.workflow.process.task.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务候选组实体
 * 保存平台任务的候选组或 ROLE_ 角色标识；可见性按查询时的有效成员关系判断
 */
@Data
@TableName("process_task_candidate_group")
public class ProcessTaskCandidateGroup {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 关联 process_task.id；与引擎任务 ID 分离，认领/转办时保持同一平台任务。 */
    private Long processTaskId;
    /** 候选组编码（角色/部门等） */
    private String groupCode;
    /** 仅控制展示顺序；组成员实时匹配，角色使用引擎的 ROLE_ 前缀。 */
    private Integer sortOrder;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createdAt;
}
