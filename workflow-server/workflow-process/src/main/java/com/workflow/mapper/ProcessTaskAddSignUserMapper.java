package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ProcessTaskAddSignUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProcessTaskAddSignUserMapper extends BaseMapper<ProcessTaskAddSignUser> {
    @Select("SELECT * FROM process_task_add_sign_user WHERE generated_task_id = #{taskId} LIMIT 1")
    ProcessTaskAddSignUser findByGeneratedTaskId(@Param("taskId") String taskId);

    @Select("SELECT * FROM process_task_add_sign_user WHERE generated_task_id = #{taskId} LIMIT 1 FOR UPDATE")
    ProcessTaskAddSignUser findByGeneratedTaskIdForUpdate(@Param("taskId") String taskId);

    @Update("UPDATE process_task_add_sign_user SET status = 'DONE', complete_time = NOW() " +
            "WHERE generated_task_id = #{taskId} AND status = 'TODO'")
    int completeByGeneratedTaskId(@Param("taskId") String taskId);

    @Update("UPDATE process_task_add_sign_user SET status = 'TODO' WHERE add_sign_id = #{addSignId} AND status = 'HOLD'")
    int activateHeld(@Param("addSignId") String addSignId);

    @Select("SELECT COUNT(*) FROM process_task_add_sign_user WHERE add_sign_id = #{addSignId} AND status IN ('TODO','HOLD')")
    long countPending(@Param("addSignId") String addSignId);
}
