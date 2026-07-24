package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ProcessTaskCandidateGroup;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 任务候选组 Mapper
 * 提供任务候选组的查询与删除操作
 */
@Mapper
public interface ProcessTaskCandidateGroupMapper
        extends BaseMapper<ProcessTaskCandidateGroup> {

    /**
     * 根据任务实例ID查询候选组列表（按排序号升序）。
     *
     * @param taskInstanceId 任务实例ID
     * @return 候选组列表
     */
    @Select("SELECT * FROM process_task_candidate_group "
            + "WHERE task_instance_id = #{taskInstanceId} ORDER BY sort_order")
    List<ProcessTaskCandidateGroup> findByTaskInstanceId(
            @Param("taskInstanceId") String taskInstanceId);

    /**
     * 根据任务实例ID删除其下所有候选组。
     *
     * @param taskInstanceId 任务实例ID
     */
    @Delete("DELETE FROM process_task_candidate_group "
            + "WHERE task_instance_id = #{taskInstanceId}")
    void deleteByTaskInstanceId(@Param("taskInstanceId") String taskInstanceId);
}
