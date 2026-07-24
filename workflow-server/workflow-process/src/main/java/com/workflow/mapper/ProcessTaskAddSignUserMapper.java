package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ProcessTaskAddSignUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 任务加签用户 Mapper
 * 提供加签生成人员的查询、完成、激活、计数操作
 */
@Mapper
public interface ProcessTaskAddSignUserMapper extends BaseMapper<ProcessTaskAddSignUser> {
    /**
     * 根据生成的任务ID查询加签用户记录。
     *
     * @param taskId Flowable任务ID
     * @return 加签用户记录，无则返回 null
     */
    @Select("SELECT * FROM process_task_add_sign_user WHERE generated_task_id = #{taskId} LIMIT 1")
    ProcessTaskAddSignUser findByGeneratedTaskId(@Param("taskId") String taskId);

    /**
     * 根据生成的任务ID加锁查询加签用户记录（FOR UPDATE）。
     *
     * @param taskId Flowable任务ID
     * @return 加签用户记录，无则返回 null
     */
    @Select("SELECT * FROM process_task_add_sign_user WHERE generated_task_id = #{taskId} LIMIT 1 FOR UPDATE")
    ProcessTaskAddSignUser findByGeneratedTaskIdForUpdate(@Param("taskId") String taskId);

    /**
     * 将加签用户任务标记为已完成（DONE）。
     * <p>
     * 仅处理状态为 TODO 的记录，避免重复完成。
     *
     * @param taskId Flowable任务ID
     * @return 受影响行数
     */
    @Update("UPDATE process_task_add_sign_user SET status = 'DONE', complete_time = NOW() " +
            "WHERE generated_task_id = #{taskId} AND status = 'TODO'")
    int completeByGeneratedTaskId(@Param("taskId") String taskId);

    /**
     * 激活加签操作下处于 HOLD 状态的用户任务。
     * <p>
     * 用于串行加签场景，前一个完成后激活下一个。
     *
     * @param addSignId 加签操作ID
     * @return 受影响行数
     */
    @Update("UPDATE process_task_add_sign_user SET status = 'TODO' WHERE add_sign_id = #{addSignId} AND status = 'HOLD'")
    int activateHeld(@Param("addSignId") String addSignId);

    /**
     * 统计加签操作下未完成（TODO/HOLD）的用户任务数。
     *
     * @param addSignId 加签操作ID
     * @return 未完成任务数
     */
    @Select("SELECT COUNT(*) FROM process_task_add_sign_user WHERE add_sign_id = #{addSignId} AND status IN ('TODO','HOLD')")
    long countPending(@Param("addSignId") String addSignId);
}
