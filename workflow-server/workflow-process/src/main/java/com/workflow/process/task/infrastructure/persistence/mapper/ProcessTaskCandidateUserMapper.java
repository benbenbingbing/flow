package com.workflow.process.task.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTaskCandidateUser;
import org.apache.ibatis.annotations.Mapper;

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
    default List<ProcessTaskCandidateUser> findByTaskInstanceId(String taskInstanceId) {
        return selectList(Wrappers.<ProcessTaskCandidateUser>lambdaQuery()
                .eq(ProcessTaskCandidateUser::getTaskInstanceId, taskInstanceId)
                .orderByAsc(ProcessTaskCandidateUser::getSortOrder));
    }

    /**
     * 根据任务实例ID删除其下所有候选用户。
     *
     * @param taskInstanceId 任务实例ID
     */
    /** 该配置表没有逻辑删除字段，使用 BaseMapper 按条件物理删除。 */
    default void deleteByTaskInstanceId(String taskInstanceId) {
        delete(Wrappers.<ProcessTaskCandidateUser>lambdaQuery()
                .eq(ProcessTaskCandidateUser::getTaskInstanceId, taskInstanceId));
    }
}
