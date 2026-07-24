package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务加签用户实体
 * 记录每次加签操作生成的人员明细，与 ProcessTaskAddSign 一对多关联
 */
@Data
@TableName("process_task_add_sign_user")
public class ProcessTaskAddSignUser {
    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /** 关联的加签操作ID */
    private String addSignId;
    /** 被加签的用户ID */
    private String userId;
    /** 加签时的用户姓名快照 */
    private String userNameSnapshot;
    /** 加签生成的Flowable任务ID */
    private String generatedTaskId;
    /** 用户任务状态：PENDING-待处理，COMPLETED-已完成 */
    private String status;
    /** 排序号（控制串行加签顺序） */
    private Integer sortOrder;
    /** 用户处理完成时间 */
    private LocalDateTime completeTime;
}
