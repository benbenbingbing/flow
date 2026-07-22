package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ProcessTaskCandidateUser;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProcessTaskCandidateUserMapper
        extends BaseMapper<ProcessTaskCandidateUser> {

    @Select("SELECT * FROM process_task_candidate_user "
            + "WHERE task_instance_id = #{taskInstanceId} ORDER BY sort_order")
    List<ProcessTaskCandidateUser> findByTaskInstanceId(
            @Param("taskInstanceId") String taskInstanceId);

    @Delete("DELETE FROM process_task_candidate_user "
            + "WHERE task_instance_id = #{taskInstanceId}")
    void deleteByTaskInstanceId(@Param("taskInstanceId") String taskInstanceId);
}
