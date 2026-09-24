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
     * 根据平台任务主键 process_task.id查询候选组列表（按排序号升序）。
     *
     * @param processTaskId 平台任务主键 process_task.id
     * @return 候选组列表
     */
    default List<ProcessTaskCandidateGroup> findByProcessTaskId(Long processTaskId) {
        return selectList(Wrappers.<ProcessTaskCandidateGroup>lambdaQuery()
                .eq(ProcessTaskCandidateGroup::getProcessTaskId, processTaskId)
                .orderByAsc(ProcessTaskCandidateGroup::getSortOrder));
    }

    /**
     * 替换候选集合或结束任务时物理删除该任务的候选组；须与主表/引擎变更处于同一事务。
     * @param processTaskId 平台任务主键 process_task.id，不接受引擎 task_id
     */
    default void deleteByProcessTaskId(Long processTaskId) {
        delete(Wrappers.<ProcessTaskCandidateGroup>lambdaQuery()
                .eq(ProcessTaskCandidateGroup::getProcessTaskId, processTaskId));
    }
}
