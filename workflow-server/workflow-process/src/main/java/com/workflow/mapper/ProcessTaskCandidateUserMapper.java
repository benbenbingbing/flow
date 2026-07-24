package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ProcessTaskCandidateUser;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 任务候选用户 Mapper
 * 提供任务候选审批人的查询与删除操作
 */
@Mapper
public interface ProcessTaskCandidateUserMapper
        extends BaseMapper<ProcessTaskCandidateUser> {

    /**
     * 根据任务实例ID查询候选用户列表（按排序号升序）。
     *
     * @param taskInstanceId 任务实例ID
     * @return 候选用户列表
     */
    @Select("SELECT * FROM process_task_candidate_user "
            + "WHERE task_instance_id = #{taskInstanceId} ORDER BY sort_order")
    List<ProcessTaskCandidateUser> findByTaskInstanceId(
            @Param("taskInstanceId") String taskInstanceId);

    /**
     * 根据任务实例ID删除其下所有候选用户。
     *
     * @param taskInstanceId 任务实例ID
     */
    @Delete("DELETE FROM process_task_candidate_user "
            + "WHERE task_instance_id = #{taskInstanceId}")
    void deleteByTaskInstanceId(@Param("taskInstanceId") String taskInstanceId);
}
