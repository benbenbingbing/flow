package com.workflow.process.task.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTaskAddSignUser;
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
    default ProcessTaskAddSignUser findByGeneratedTaskId(String taskId) {
        // 首行限制交给分页插件，避免加载全部结果或在 Mapper 内拼接数据库分页语法。
        return selectList(new Page<ProcessTaskAddSignUser>(1, 1, false), Wrappers.<ProcessTaskAddSignUser>lambdaQuery()
                .eq(ProcessTaskAddSignUser::getGeneratedTaskId, taskId)).stream().findFirst().orElse(null);
    }

    /**
     * 根据生成的任务ID加锁查询加签用户记录（FOR UPDATE）。
     *
     * @param taskId Flowable任务ID
     * @return 加签用户记录，无则返回 null
     */
    // generated_task_id 有唯一索引，不依赖 LIMIT 限制行锁范围。
    @Select("SELECT * FROM process_task_add_sign_user WHERE generated_task_id = #{taskId} FOR UPDATE")
    ProcessTaskAddSignUser findByGeneratedTaskIdForUpdate(@Param("taskId") String taskId);

    /**
     * 将加签用户任务标记为已完成（DONE）。
     * <p>
     * 仅处理状态为 TODO 的记录，避免重复完成。
     *
     * @param taskId Flowable任务ID
     * @return 受影响行数
     */
    @Update("""
            <script>
            UPDATE process_task_add_sign_user SET status = 'DONE', complete_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@currentNow(_databaseId)}
             WHERE generated_task_id = #{taskId}
             AND status = 'TODO'
            </script>
            """)
    int completeByGeneratedTaskId(@Param("taskId") String taskId);

    /**
     * 激活加签操作下处于 HOLD 状态的用户任务。
     * <p>
     * 用于串行加签场景，前一个完成后激活下一个。
     *
     * @param addSignId 加签操作ID
     * @return 受影响行数
     */
    default int activateHeld(String addSignId) {
        // HOLD 检查和状态更新仍由同一 UPDATE 完成，重复激活不会修改已处理记录。
        return update(null, Wrappers.<ProcessTaskAddSignUser>lambdaUpdate()
                .set(ProcessTaskAddSignUser::getStatus, "TODO")
                .eq(ProcessTaskAddSignUser::getAddSignId, addSignId)
                .eq(ProcessTaskAddSignUser::getStatus, "HOLD"));
    }

    /**
     * 统计加签操作下未完成（TODO/HOLD）的用户任务数。
     *
     * @param addSignId 加签操作ID
     * @return 未完成任务数
     */
    default long countPending(String addSignId) {
        return selectCount(Wrappers.<ProcessTaskAddSignUser>lambdaQuery()
                .eq(ProcessTaskAddSignUser::getAddSignId, addSignId)
                .in(ProcessTaskAddSignUser::getStatus, "TODO", "HOLD"));
    }
}
