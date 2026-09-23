package com.workflow.process.task.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTaskCandidateGroup;
import org.apache.ibatis.annotations.Mapper;

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
    default List<ProcessTaskCandidateGroup> findByTaskInstanceId(String taskInstanceId) {
        return selectList(Wrappers.<ProcessTaskCandidateGroup>lambdaQuery()
                .eq(ProcessTaskCandidateGroup::getTaskInstanceId, taskInstanceId)
                .orderByAsc(ProcessTaskCandidateGroup::getSortOrder));
    }

    /**
     * 根据任务实例ID删除其下所有候选组。
     *
     * @param taskInstanceId 任务实例ID
     */
    /** 该配置表没有逻辑删除字段，使用 BaseMapper 按条件物理删除。 */
    default void deleteByTaskInstanceId(String taskInstanceId) {
        delete(Wrappers.<ProcessTaskCandidateGroup>lambdaQuery()
                .eq(ProcessTaskCandidateGroup::getTaskInstanceId, taskInstanceId));
    }
}
